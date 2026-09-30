package com.omnifile.preview

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SharedMemory
import android.util.Log
import com.omnifile.BuildConfig
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

class AndroidPdfRendererClientFactory(context: Context) : PdfRendererClientFactory {
    private val appContext = context.applicationContext
    override fun create(): PdfRendererClient = AndroidPdfRendererClient(appContext)
}

private class AndroidPdfRendererClient(private val context: Context) : PdfRendererClient, PdfRendererDeathTestHook, PdfResponseArrivalTestHook {
    private val sessionId = UUID.randomUUID().toString()
    private val requestIds = AtomicLong(0)
    private val closed = AtomicBoolean(false)
    private val bindStarted = AtomicBoolean(false)
    private val unbound = AtomicBoolean(false)
    private val bindFinished = AtomicBoolean(false)
    private val connectionReady = CompletableDeferred<Messenger>()
    private val shutdownReady = CompletableDeferred<Unit>()
    private val shutdownRequest = AtomicLong(-1)
    private val transportLock = Any()
    private val workerDisconnected = CompletableDeferred<Unit>()
    private val service = AtomicReference<Messenger?>(null)
    private val pending = AtomicReference<Pending?>(null)
    private data class PageArrival(val requestId: Long, val arrived: CompletableDeferred<Unit>)
    private val pageArrivalForTest = AtomicReference<PageArrival?>(null)
    private val callbackMessenger = Messenger(CallbackHandler())
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Log.i(TAG, "service_connected")
            if (closed.get()) {
                connectionReady.completeExceptionally(PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE))
                return
            }
            val messenger = Messenger(binder)
            service.set(messenger)
            connectionReady.complete(messenger)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.w(TAG, "service_disconnected")
            service.set(null)
            failPending(PdfRendererFailure(PdfRendererFailureKind.WORKER_DIED))
            shutdownReady.complete(Unit)
            workerDisconnected.complete(Unit)
        }

        override fun onBindingDied(name: ComponentName) {
            Log.w(TAG, "service_binding_died")
            service.set(null)
            failPending(PdfRendererFailure(PdfRendererFailureKind.WORKER_DIED))
            shutdownReady.complete(Unit)
            workerDisconnected.complete(Unit)
            connectionReady.completeExceptionally(PdfRendererFailure(PdfRendererFailureKind.WORKER_DIED))
            unbind()
        }

        override fun onNullBinding(name: ComponentName) {
            Log.w(TAG, "service_null_binding")
            shutdownReady.complete(Unit)
            failPending(PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE))
            connectionReady.completeExceptionally(PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE))
            unbind()
        }
    }

    override suspend fun open(snapshot: PdfSnapshot): Int {
        val descriptor = try {
            ParcelFileDescriptor.open(snapshot.file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (_: IOException) {
            throw PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE)
        }
        val response = try {
            request(PdfRendererProtocol.OPEN, descriptor = descriptor)
        } finally {
            runCatching { descriptor.close() }
        }
        return response.use {
            it.pageCount.also { count ->
                if (count < 0) throw PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE)
            }
        }
    }

    override suspend fun renderPage(pageIndex: Int): PdfRenderedPage = request(PdfRendererProtocol.RENDER) { bundle ->
        bundle.putInt(PdfRendererProtocol.PAGE_INDEX, pageIndex)
    }.use { response ->
        val width = response.width
        val height = response.height
        if (!isValidPdfRenderSize(width, height)) throw PdfRendererFailure(PdfRendererFailureKind.RESOURCE_LIMIT)
        val memory = response.memory ?: throw PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE)
        val expected = width * height * 4
        try {
            if (memory.size != expected) throw PdfRendererFailure(PdfRendererFailureKind.RESOURCE_LIMIT)
            val mapped = memory.mapReadOnly()
            try {
                if (mapped.remaining() != expected) throw PdfRendererFailure(PdfRendererFailureKind.RESOURCE_LIMIT)
                val pixels = ByteArray(expected)
                mapped.get(pixels)
                PdfRenderedPage(width, height, pixels)
            } finally { SharedMemory.unmap(mapped) }
        } catch (failure: PdfRendererFailure) {
            throw failure
        } catch (_: RuntimeException) {
            throw PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE)
        }
    }

    override fun armAcceptedPageResponseForTest(): kotlinx.coroutines.Deferred<Unit> {
        check(BuildConfig.DEBUG) { "Response observations are only available in debug builds" }
        val arrival = PageArrival(requestIds.get() + 1, CompletableDeferred())
        check(pageArrivalForTest.compareAndSet(null, arrival))
        return arrival.arrived
    }

    override suspend fun killRendererForTest() {
        check(BuildConfig.DEBUG) { "The renderer death hook is only available in debug builds" }
        val target = connect()
        val message = Message.obtain(null, PdfRendererProtocol.KILL_WORKER_FOR_TEST)
        message.data = Bundle().apply { putString(PdfRendererProtocol.SESSION_ID, sessionId) }
        target.send(message)
        withTimeout(5_000) { workerDisconnected.await() }
    }

    override suspend fun close() { beginShutdown(); awaitShutdown() }

    override fun abort() { beginShutdown() }

    private fun beginShutdown() {
        synchronized(transportLock) {
            if (!closed.compareAndSet(false, true)) return
            val target = service.get()
            // A CLOSE is queued behind prior native work. The worker's existing deadline kills
            // non-cooperative native work; CLOSED or disconnect is the physical cleanup barrier.
            if (target == null) shutdownReady.complete(Unit) else {
                val id = requestIds.incrementAndGet()
                shutdownRequest.set(id)
                try {
                    val message = Message.obtain(null, PdfRendererProtocol.CLOSE)
                    message.replyTo = callbackMessenger
                    message.data = Bundle().apply {
                        putLong(PdfRendererProtocol.REQUEST_ID, id)
                        putString(PdfRendererProtocol.SESSION_ID, sessionId)
                    }
                    target.send(message)
                } catch (_: RemoteException) {
                    // Only observed Binder death proves physical disposal. Other transport
                    // failures retain this owner until disconnect; never reclaim optimistically.
                    if (!target.binder.isBinderAlive) shutdownReady.complete(Unit)
                }
            }
            failPending(PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE))
            connectionReady.completeExceptionally(PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE))
        }
    }

    override suspend fun awaitShutdown() {
        shutdownReady.await()
        unbind()
    }

    private suspend fun request(
        operation: Int,
        descriptor: ParcelFileDescriptor? = null,
        addPayload: (Bundle) -> Unit = {},
    ): RendererResponse {
        if (closed.get()) throw PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE)
        val target = connect()
        if (closed.get()) throw PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE)
        val id = requestIds.incrementAndGet()
        val response = OwningResponse<RendererResponse>(sessionId, id) { it.close() }
        val entry = Pending(id, response)
        val observedForTest = BuildConfig.DEBUG && operation == PdfRendererProtocol.RENDER &&
            pageArrivalForTest.get()?.requestId == id
        try {
            val message = Message.obtain(null, operation)
            message.obj = descriptor // boot-class Parcelable; Bundle contains only scalar metadata
            message.replyTo = callbackMessenger
            message.data = Bundle().apply {
                putLong(PdfRendererProtocol.REQUEST_ID, id)
                putString(PdfRendererProtocol.SESSION_ID, sessionId)
                addPayload(this)
            }
            synchronized(transportLock) {
                if (closed.get() || !pending.compareAndSet(null, entry)) throw PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE)
                target.send(message)
            }
            Log.i(TAG, "request_sent_${operationName(operation)}")
            // An armed debug observation lets the manual test dispatcher stop before transfer,
            // even if a fast callback arrives before await has suspended. Normal/release input is inert.
            if (observedForTest) kotlinx.coroutines.yield()
            val result = response.awaitAndTransfer()
            try {
                Log.i(TAG, "response_received_${operationName(operation)}")
                if (result.what == PdfRendererProtocol.ERROR) throw mapError(result.errorKind)
                return result
            } catch (error: Throwable) { result.close(); throw error }
        } catch (failure: PdfRendererFailure) {
            throw failure
        } catch (_: RemoteException) {
            throw PdfRendererFailure(PdfRendererFailureKind.WORKER_DIED)
        } finally {
            val awaiting = !response.hasResponse
            pending.compareAndSet(entry, null)
            response.dispose() // map removal never discards an untransferred response
            if (awaiting) {
                runCatching {
                    val cancel = Message.obtain(null, PdfRendererProtocol.CANCEL)
                    cancel.data = Bundle().apply {
                        putLong(PdfRendererProtocol.REQUEST_ID, id)
                        putString(PdfRendererProtocol.SESSION_ID, sessionId)
                    }
                    target.send(cancel)
                }
            }
        }
    }

    private suspend fun connect(): Messenger {
        if (bindStarted.compareAndSet(false, true)) {
            val intent = Intent(context, PdfRendererService::class.java)
            // Context's default ServiceConnection callbacks still target Main; the bind itself
            // belongs to the renderer's background operation and must not block the UI owner.
            val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE).also { bound ->
                bindFinished.set(bound)
                if (closed.get()) unbind()
            }
            Log.i(TAG, "bind_returned_$bound")
            if (!bound) {
                connectionReady.completeExceptionally(PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE))
            }
        }
        return try {
            connectionReady.await()
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            throw PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE)
        }
    }

    private fun failPending(error: PdfRendererFailure) {
        pending.getAndSet(null)?.response?.fail(error)
    }

    private fun unbind() {
        if (bindFinished.get() && unbound.compareAndSet(false, true)) {
            runCatching { context.unbindService(connection) }
        }
    }

    private inner class CallbackHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            val data = message.data
            var incoming = message.obj as? java.io.Closeable
            message.obj = null // callback now owns the decoded handle, independently of the Message
            var observedArrival: CompletableDeferred<Unit>? = null
            try {
                val requestId = data.getLong(PdfRendererProtocol.REQUEST_ID, -1L)
                val responseSessionId = data.getString(PdfRendererProtocol.SESSION_ID)
                if (message.what == PdfRendererProtocol.CLOSED &&
                    requestId == shutdownRequest.get() && responseSessionId == sessionId) {
                    shutdownReady.complete(Unit)
                    return
                }
                val active = pending.get()
                if (closed.get() || active == null || active.requestId != requestId ||
                    responseSessionId != sessionId
                ) {
                    return
                }
                if (message.what == PdfRendererProtocol.ERROR) {
                    Log.w(TAG, "error_received")
                    active.response.fail(mapError(data.getInt(PdfRendererProtocol.ERROR_KIND)), responseObserved = true)
                } else {
                    Log.i(TAG, "response_delivered")
                    val memory = incoming as? SharedMemory
                    // The callback owns memory until offer transfers it or disposes the rejection.
                    val response = RendererResponse(message.what, data.getInt(PdfRendererProtocol.PAGE_COUNT, -1),
                        data.getInt(PdfRendererProtocol.WIDTH, -1), data.getInt(PdfRendererProtocol.HEIGHT, -1),
                        data.getInt(PdfRendererProtocol.ERROR_KIND), memory)
                    if (memory != null) incoming = null // response owns SharedMemory; unexpected PFD stays cleanup-owned
                    val accepted = active.response.offer(response, responseSessionId, requestId)
                    if (BuildConfig.DEBUG && accepted && message.what == PdfRendererProtocol.PAGE) {
                        val arrival = pageArrivalForTest.get()
                        if (arrival?.requestId == requestId && pageArrivalForTest.compareAndSet(arrival, null)) {
                            observedArrival = arrival.arrived
                        }
                    }
                }
            } finally {
                incoming?.close() // stale/error/rejected headers and pre-response exceptions
                // Resource Parcelables travel in Message.obj, avoiding lazy Bundle FD copies/GC.
                data.clear()
                observedArrival?.complete(Unit) // observation includes transport FD disposal
            }
        }
    }

    private data class Pending(val requestId: Long, val response: OwningResponse<RendererResponse>)

    private class RendererResponse(val what: Int, val pageCount: Int, val width: Int, val height: Int,
        val errorKind: Int, val memory: SharedMemory?) : java.io.Closeable {
        private val closed = AtomicBoolean(false)
        override fun close() { if (closed.compareAndSet(false, true)) memory?.close() }
    }

    private fun mapError(code: Int): PdfRendererFailure = PdfRendererFailure(
        when (code) {
            PdfRendererProtocol.ERROR_MALFORMED -> PdfRendererFailureKind.MALFORMED
            PdfRendererProtocol.ERROR_ENCRYPTED -> PdfRendererFailureKind.ENCRYPTED_OR_UNSUPPORTED
            PdfRendererProtocol.ERROR_RESOURCE -> PdfRendererFailureKind.RESOURCE_LIMIT
            PdfRendererProtocol.ERROR_INVALID_PAGE -> PdfRendererFailureKind.INVALID_PAGE
            else -> PdfRendererFailureKind.UNAVAILABLE
        },
    )

    private fun operationName(operation: Int): String = when (operation) {
        PdfRendererProtocol.OPEN -> "open"
        PdfRendererProtocol.RENDER -> "render"
        PdfRendererProtocol.CLOSE -> "close"
        else -> "other"
    }

    private companion object {
        const val TAG = "OmniPdfClient"
    }
}
