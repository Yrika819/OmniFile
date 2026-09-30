package com.omnifile.storage

import java.util.ArrayDeque
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.runBlocking

/** Logical completion never promises termination of a platform/provider call. */
enum class ReadAcquisitionState { ACTIVE, SUCCEEDED, FAILED, EXPIRED, CANCELLED }
enum class ReadAcquisitionFailure { BUSY, TIMEOUT, CANCELLED }
class ReadAcquisitionException(val failure: ReadAcquisitionFailure) : CancellationException(failure.name)

/**
 * Exactly two app-wide admissions, two fixed work threads, and two fixed revocation threads.
 * There is no admission queue. Each admitted scope can schedule at most one cleanup drainer;
 * the two-entry executor queues therefore cannot accumulate work from hostile Retry.
 * Shutdown refuses new work; it neither interrupts nor reclaims retained admissions. Production
 * owns the singleton for the process lifetime. Tests close isolated runners after releasing fakes.
 */
class ReadAcquisitionExecutor(
    private val clockNanos: () -> Long = System::nanoTime,
    private val timeoutMillis: Long = 10_000,
) : AutoCloseable {
    init { require(timeoutMillis in 1..10_000) }
    private val admission = Semaphore(2)
    private val stopped = AtomicBoolean(false)
    private val retainedScopes = java.util.concurrent.ConcurrentHashMap.newKeySet<ReadAcquisitionScope>()
    private val lifecycleLock = Any()
    private fun lanes(name: String) = ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(2), { work -> Thread(work, name).apply { isDaemon = true } })
    private val work = lanes("omnifile-read-work")
    private val revocation = lanes("omnifile-read-revoke")
    private val deadlines = ScheduledThreadPoolExecutor(1) { work ->
        Thread(work, "omnifile-read-deadline").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }

    internal val retainedSlots: Int get() = 2 - admission.availablePermits()
    internal val workThreadCount: Int get() = work.poolSize

    suspend fun <T> acquire(dispose: (T) -> Unit = {}, successful: (T) -> Boolean = { true }, block: suspend (ReadAcquisitionScope) -> T): T {
        lateinit var scope: ReadAcquisitionScope
        val timer = synchronized(lifecycleLock) {
            if (stopped.get() || !admission.tryAcquire()) throw ReadAcquisitionException(ReadAcquisitionFailure.BUSY)
            scope = ReadAcquisitionScope(clockNanos, clockNanos() + timeoutMillis * 1_000_000,
                { task -> revocation.execute(task) }, {
                    retainedScopes.remove(scope)
                    admission.release()
                    if (stopped.get() && retainedScopes.isEmpty()) revocation.shutdown()
                })
            retainedScopes.add(scope)
            val scheduled = deadlines.schedule({ scope.expire() }, timeoutMillis, TimeUnit.MILLISECONDS)
            try {
                work.execute {
                    try {
                        val result = runBlocking(scope) { scope.checkActive(); block(scope) }
                        scope.complete(result, successful(result), dispose)
                    } catch (error: Throwable) {
                        scope.fail(error)
                    } finally { scope.workerReturned() }
                }
            } catch (error: Throwable) {
                scope.fail(error)
                scope.workerReturned()
            }
            scheduled
        }
        try {
            // Force notification resumption off the deadline/revocation caller, even for Unconfined tests.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { scope.available.await() }
            @Suppress("UNCHECKED_CAST")
            return scope.take() as T
        } finally {
            // Includes prompt cancellation after availability but before delivery.
            scope.cancel()
            timer.cancel(false)
        }
    }

    internal fun awaitIdle(timeoutMillis: Long = 5_000): Boolean {
        if (!admission.tryAcquire(2, timeoutMillis, TimeUnit.MILLISECONDS)) return false
        admission.release(2)
        return true
    }

    override fun close() = synchronized(lifecycleLock) {
        stopped.set(true)
        work.shutdown()
        deadlines.shutdown()
        if (retainedScopes.isEmpty()) revocation.shutdown()
        // Retained scopes stay strongly owned. No shutdownNow or unsafe reclamation.
    }

    companion object { val appWide = ReadAcquisitionExecutor() }
}

/** One lock serializes tickets, terminal state, lease ownership, and delivery ownership. */
class ReadAcquisitionScope internal constructor(
    private val clockNanos: () -> Long,
    val deadlineNanos: Long,
    private val enqueue: (Runnable) -> Unit,
    private val releaseSlot: () -> Unit,
) : AbstractCoroutineContextElement(Key), ThreadContextElement<ReadAcquisitionScope?> {
    companion object Key : CoroutineContext.Key<ReadAcquisitionScope> {
        private val current = ThreadLocal<ReadAcquisitionScope?>()
        fun current(): ReadAcquisitionScope? = current.get()
    }
    override fun updateThreadContext(context: CoroutineContext): ReadAcquisitionScope? =
        current.get().also { current.set(this) }
    override fun restoreThreadContext(context: CoroutineContext, oldState: ReadAcquisitionScope?) { current.set(oldState) }

    private val lock = Any()
    private var logical = ReadAcquisitionState.ACTIVE
    val state: ReadAcquisitionState get() = synchronized(lock) { logical }
    internal val available = CompletableDeferred<Unit>()
    private val tickets = mutableSetOf<OperationTicket>()
    private val leases = mutableSetOf<ReadLease<*>>()
    private val cleanup = ArrayDeque<() -> Unit>()
    private val afterWorker = ArrayDeque<() -> Unit>()
    private var draining = false
    private var workerDone = false
    private var released = false
    private var delivered = false
    private val uncertainCleanup = mutableListOf<() -> Unit>()
    private var outcomeSuccessful = true
    private var resultReady = false
    private var result: Any? = null
    private var resultDisposer: (() -> Unit)? = null
    private var error: Throwable? = null

    class OperationTicket internal constructor(internal var cancellation: (() -> Unit)?)

    private fun checkLocked() {
        if (logical == ReadAcquisitionState.ACTIVE && clockNanos() - deadlineNanos >= 0) terminateLocked(ReadAcquisitionState.EXPIRED)
        if (logical != ReadAcquisitionState.ACTIVE) throw terminalError()
    }
    private fun terminalError(): Throwable = error ?: ReadAcquisitionException(
        if (logical == ReadAcquisitionState.EXPIRED) ReadAcquisitionFailure.TIMEOUT else ReadAcquisitionFailure.CANCELLED)

    fun checkActive() = synchronized(lock) { checkLocked() }

    /** Registration precedes the call. A rejected late result is owned by the cleanup lane. */
    fun <T> blockingOperation(cancel: (() -> Unit)? = null, dispose: (T) -> Unit = {}, block: () -> T): T {
        val ticket = register(cancel)
        val value = try { block() } catch (error: Throwable) { finish(ticket); throw error }
        return synchronized(lock) {
            finishLocked(ticket)
            try { checkLocked() } catch (error: Throwable) { queueLocked { dispose(value) }; throw error }
            value // worker owns any resource until the next atomic lease/adoption decision
        }
    }

    suspend fun <T> operation(cancel: (() -> Unit)? = null, block: suspend () -> T): T {
        val ticket = register(cancel)
        return try { block().also { synchronized(lock) { finishLocked(ticket); checkLocked() } } }
        finally { finish(ticket) }
    }

    private fun register(cancel: (() -> Unit)?): OperationTicket = synchronized(lock) {
        checkLocked()
        OperationTicket(cancel).also { tickets.add(it) }
    }
    private fun finish(ticket: OperationTicket) = synchronized(lock) { finishLocked(ticket) }
    private fun finishLocked(ticket: OperationTicket) { tickets.remove(ticket); ticket.cancellation = null }

    /** The worker owns value before this call; adoption or cleanup ownership is atomic. */
    fun <T> own(value: T, afterWorkerReturns: Boolean = false, dispose: (T) -> Unit): ReadLease<T> = synchronized(lock) {
        val lease = ReadLease(this, value, afterWorkerReturns, dispose)
        try { checkLocked() } catch (error: Throwable) { if (afterWorkerReturns && !workerDone) afterWorker.add { dispose(value) } else queueLocked { dispose(value) }; throw error }
        leases.add(lease)
        lease
    }

    internal fun closeLease(lease: ReadLease<*>) {
        val owned = synchronized(lock) {
            if (lease !in leases) return
            // Claim close and its operation ticket together; expiry cannot strand the owner.
            val ticket = OperationTicket(null).also { tickets.add(it) }
            leases.remove(lease)
            ticket to lease.claim()
        }
        try { owned.second?.invoke() } catch (error: Throwable) {
            synchronized(lock) { uncertainCleanup.add(owned.second!!); released = true } // retain the resource and admission
            throw error
        } finally { finish(owned.first) }
    }

    internal fun <T, R> transfer(lease: ReadLease<T>, adopt: (T) -> R): R = synchronized(lock) {
        checkLocked()
        check(lease in leases) { "Lease no longer owns the resource" }
        val result = adopt(lease.value) // memory-only logical adoption, under the same terminal-state lock
        leases.remove(lease)
        lease.claim() // caller becomes owner at this decision
        result
    }

    internal fun <T> complete(value: T, successful: Boolean = true, disposer: (T) -> Unit) = synchronized(lock) {
        try { checkLocked() } catch (_: Throwable) { queueLocked { disposer(value) }; return@synchronized }
        outcomeSuccessful = successful
        resultReady = true
        result = value
        resultDisposer = { disposer(value) }
        disposeLeasesLocked()
        available.complete(Unit)
    }

    internal fun fail(failure: Throwable) = synchronized(lock) {
        if (logical == ReadAcquisitionState.ACTIVE) {
            error = failure
            terminateLocked(ReadAcquisitionState.FAILED)
        }
    }
    fun expire() = synchronized(lock) { if (logical == ReadAcquisitionState.ACTIVE) terminateLocked(ReadAcquisitionState.EXPIRED) }
    fun cancel() = synchronized(lock) {
        if (logical == ReadAcquisitionState.ACTIVE) terminateLocked(ReadAcquisitionState.CANCELLED)
        maybeReleaseLocked()
    }
    private fun terminateLocked(state: ReadAcquisitionState) {
        logical = state
        resultDisposer?.let { queueLocked(it) }
        resultDisposer = null
        result = null
        tickets.forEach { ticket -> ticket.cancellation?.let { queueLocked(it) }; ticket.cancellation = null }
        tickets.clear()
        disposeLeasesLocked()
        delivered = true
        available.complete(Unit)
        maybeReleaseLocked()
    }
    private fun disposeLeasesLocked() {
        leases.forEach { lease -> lease.claim()?.let { action ->
            if (lease.afterWorkerReturns && !workerDone) afterWorker.add(action) else queueLocked(action)
        } }
        leases.clear()
    }

    internal fun take(): Any? = synchronized(lock) {
        checkLocked()
        check(resultReady && !delivered)
        logical = if (outcomeSuccessful) ReadAcquisitionState.SUCCEEDED else ReadAcquisitionState.FAILED
        delivered = true
        resultDisposer = null
        result.also { result = null; maybeReleaseLocked() }
    }
    internal fun workerReturned() = synchronized(lock) { workerDone = true; while (afterWorker.isNotEmpty()) queueLocked(afterWorker.removeFirst()); maybeReleaseLocked() }

    private fun queueLocked(action: () -> Unit) {
        cleanup.add(action)
        if (!draining) {
            draining = true
            enqueue(Runnable { drain() })
        }
    }
    private fun drain() {
        while (true) {
            val action = synchronized(lock) {
                if (cleanup.isEmpty()) { draining = false; maybeReleaseLocked(); return }
                cleanup.removeFirst()
            }
            try { action() } catch (_: Throwable) {
                // Failed or interrupted cleanup cannot prove physical release. Retain the slot.
                synchronized(lock) { uncertainCleanup.add(action); released = true }
            }
        }
    }
    private fun maybeReleaseLocked() {
        if (!released && workerDone && delivered && logical != ReadAcquisitionState.ACTIVE && !draining && cleanup.isEmpty() && afterWorker.isEmpty()) {
            released = true
            releaseSlot()
        }
    }
}

class ReadLease<T> internal constructor(
    private val scope: ReadAcquisitionScope,
    internal val value: T,
    internal val afterWorkerReturns: Boolean,
    private val dispose: (T) -> Unit,
) : AutoCloseable {
    private var owned = true // accessed only under the scope lock
    internal fun claim(): (() -> Unit)? = if (owned) { owned = false; { dispose(value) } } else null
    fun transfer(): T = scope.transfer(this) { it }
    fun <R> adopt(transform: (T) -> R): R = scope.transfer(this, transform)
    override fun close() = scope.closeLease(this)
}
