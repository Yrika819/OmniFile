package com.omnifile.preview

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OwningResponseTest {
    private class Resource { val closes = AtomicInteger(); fun close() { check(closes.incrementAndGet() == 1) } }
    private class ManualDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.add(block) }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
    }
    private class Fixture {
        val dispatcher = ManualDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val resource = Resource()
        val holder = OwningResponse<Resource>("session", 1) { it.close() }
        var transfers = 0
        var error: Throwable? = null
        fun consumer(throwAfter: Boolean = false): Job = scope.launch {
            try {
                val owned = holder.awaitAndTransfer()
                transfers++
                try { if (throwAfter) throw IllegalStateException("consumer failure") } finally { owned.close() }
            } catch (failure: Throwable) { error = failure }
        }.also { dispatcher.drain() }
        fun accepted() { assertTrue(holder.offer(resource)); assertEquals(0, resource.closes.get()) }
        fun closed() { dispatcher.drain(); assertEquals(1, resource.closes.get()) }
    }
    @Test fun pageAcceptedThenConsumerCancelledBeforeResumption() {
        val f = Fixture(); val consumer = f.consumer(); f.accepted(); consumer.cancel(); f.closed(); assertEquals(0, f.transfers)
    }
    @Test fun pageAfterExceptionalCompletionBeforePendingRemoval() {
        val f = Fixture(); f.holder.fail(IllegalStateException()); assertFalse(f.holder.offer(f.resource)); f.closed()
    }
    @Test fun duplicatePageClosesDuplicateAndRetainsFirst() {
        val f = Fixture(); f.accepted(); val duplicate = Resource(); assertFalse(f.holder.offer(duplicate)); assertEquals(1, duplicate.closes.get())
        f.holder.dispose(); f.closed()
    }
    @Test fun staleSessionPageClosesImmediately() {
        val f = Fixture(); assertFalse(f.holder.offer(f.resource, "old-session", 1)); f.closed(); assertEquals(OwningResponse.State.WAITING, f.holder.state)
    }
    @Test fun staleRequestPageClosesImmediately() {
        val f = Fixture(); assertFalse(f.holder.offer(f.resource, "session", 0)); f.closed(); assertEquals(OwningResponse.State.WAITING, f.holder.state)
    }
    @Test fun cancelBeforeCallbackClosesLateResource() {
        val f = Fixture(); val consumer = f.consumer(); consumer.cancel(); f.dispatcher.drain(); assertFalse(f.holder.offer(f.resource)); f.closed()
    }
    @Test fun cancelAfterTransferLeavesConsumerOwner() {
        val f = Fixture(); val gate = CompletableDeferred<Unit>()
        val consumer = f.scope.launch {
            val owned = f.holder.awaitAndTransfer()
            f.transfers++
            try { gate.await() } finally { owned.close() }
        }
        f.dispatcher.drain(); f.accepted(); f.dispatcher.drain()
        assertEquals(1, f.transfers); assertEquals(0, f.resource.closes.get())
        f.holder.fail(IllegalStateException("disconnect after transfer"))
        assertEquals(0, f.resource.closes.get()) // holder has relinquished; consumer still owns
        consumer.cancel(); f.closed(); f.holder.dispose(); assertEquals(1, f.resource.closes.get())
    }
    @Test fun normalSuccessTransfersOnce() {
        val f = Fixture(); f.consumer(); f.accepted(); f.closed(); assertEquals(1, f.transfers); assertEquals(OwningResponse.State.TRANSFERRED, f.holder.state)
    }
    @Test fun disconnectClosesUntransferredResponse() {
        val f = Fixture(); f.consumer(); f.accepted(); f.holder.fail(PdfRendererFailure(PdfRendererFailureKind.WORKER_DIED)); f.closed()
        assertEquals(0, f.transfers); assertTrue(f.error is PdfRendererFailure)
    }
    @Test fun timeoutClosesUntransferredResponse() {
        val f = Fixture(); f.consumer(); f.accepted(); f.holder.fail(PdfRendererFailure(PdfRendererFailureKind.TIMEOUT)); f.closed(); assertEquals(0, f.transfers)
    }
    @Test fun responseExactlyAsTimeoutWinsHasOneClose() {
        val f = Fixture(); f.consumer(); f.holder.fail(PdfRendererFailure(PdfRendererFailureKind.TIMEOUT)); assertFalse(f.holder.offer(f.resource)); f.closed()
    }
    @Test fun pendingRemovalVsCallbackCannotOrphanResponse() {
        val f = Fixture(); f.accepted(); f.holder.dispose(); f.closed(); f.holder.dispose(); assertEquals(1, f.resource.closes.get())
    }
    @Test fun consumerThrowsAfterTransferClosesInConsumerFinally() {
        val f = Fixture(); f.consumer(true); f.accepted(); f.closed(); assertEquals(1, f.transfers); assertTrue(f.error is IllegalStateException)
    }
    @Test fun disposedOwnerRejectsCallback() {
        val f = Fixture(); f.holder.dispose(); assertFalse(f.holder.offer(f.resource)); f.closed()
    }
    @Test fun fiveHundredIterationsAcrossFiveCallbackCancellationTemplates() {
        repeat(500) { index ->
            val f = Fixture(); val consumer = f.consumer()
            when (index % 5) {
                0 -> { f.accepted(); consumer.cancel() }
                1 -> { consumer.cancel(); f.dispatcher.drain(); assertFalse(f.holder.offer(f.resource)) }
                2 -> { f.accepted(); f.holder.fail(IllegalStateException()) }
                3 -> { f.accepted(); f.dispatcher.drain(); f.holder.dispose() }
                4 -> { f.holder.dispose(); assertFalse(f.holder.offer(f.resource)) }
            }
            f.closed(); assertEquals(if (index % 5 == 3) 1 else 0, f.transfers)
        }
    }
    @Test fun transferAndOwnerFailureInBothOrdersKeepOneOwner() {
        repeat(2) { order ->
            val f = Fixture(); f.consumer(); f.accepted()
            if (order == 0) { f.dispatcher.drain(); f.holder.fail(IllegalStateException()) }
            else f.holder.fail(IllegalStateException())
            f.closed(); assertEquals(if (order == 0) 1 else 0, f.transfers)
        }
    }
    // 500 concurrent callback/failure race attempts; schedule diversity unmeasured.
    @Test fun fiveHundredConcurrentCallbackFailureRaceAttemptsCloseExactlyOnce() {
        val lanes = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            repeat(500) {
                val holder = OwningResponse<Resource> { it.close() }; val resource = Resource()
                val barrier = java.util.concurrent.CyclicBarrier(3)
                val callback = lanes.submit { barrier.await(); holder.offer(resource) }
                val disconnect = lanes.submit { barrier.await(); holder.fail(IllegalStateException()) }
                barrier.await()
                callback.get(5, java.util.concurrent.TimeUnit.SECONDS)
                disconnect.get(5, java.util.concurrent.TimeUnit.SECONDS)
                assertEquals(1, resource.closes.get())
                assertEquals(OwningResponse.State.DISPOSED, holder.state)
            }
        } finally { lanes.shutdownNow() }
    }

}
