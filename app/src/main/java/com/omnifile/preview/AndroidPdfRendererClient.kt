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
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidPdfRendererClientFactory(context: Context) : PdfRendererClientFactory {
    private val appContext = context.applicationContext
    override fun create(): PdfRendererClient = AndroidPdfRendererClient(appContext)
}

private class AndroidPdfRendererClient(private val context: Context) : PdfRendererClient {
    private val sessionId = UUID.randomUUID().toString()
    private val requestIds = AtomicLong(0)
    private val closed = AtomicBoolean(false)
    private val bindStarted = AtomicBoolean(false)
    private val unbound = AtomicBoolean(false)
    private val connectionReady = CompletableDeferred<Messenger>()
    private val service = AtomicReference<Messenger?>(null)
    private val pending = AtomicReference<Pending?>(null)
    private val callbackMessenger = Messenger(CallbackHandler())
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Log.i(TAG, "service_connected")
            val messenger = Messenger(binder)
            service.set(messenger)
            connectionReady.complete(messenger)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.w(TAG, "service_disconnected")
            service.set(null)
            failPending(PdfRendererFailure(PdfRendererFailureKind.WORKER_DIED))
        }

        override fun onBindingDied(name: ComponentName) {
            Log.w(TAG, "service_binding_died")
            service.set(null)
            failPending(PdfRendererFailure(PdfRendererFailureKind.WORKER_DIED))
            connectionReady.completeExceptionally(PdfRendererFailure(PdfRendererFailureKind.WORKER_DIED))
            unbind()
        }

        override fun onNullBinding(name: ComponentName) {
            Log.w(TAG, "service_null_binding")
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
            request(PdfRendererProtocol.OPEN) { bundle ->
                bundle.putParcelable(PdfRendererProtocol.DESCRIPTOR, descriptor)
            }
        } finally {
            runCatching { descriptor.close() }
        }
        return response.data.getInt(PdfRendererProtocol.PAGE_COUNT, -1).also {
            if (it < 0) throw PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE)
        }
    }

    override suspend fun renderPage(pageIndex: Int): PdfRenderedPage {
        val response = request(PdfRendererProtocol.RENDER) { bundle ->
            bundle.putInt(PdfRendererProtocol.PAGE_INDEX, pageIndex)
        }
        val bundle = response.data.apply { classLoader = SharedMemory::class.java.classLoader }
        val width = bundle.getInt(PdfRendererProtocol.WIDTH, -1)
        val height = bundle.getInt(PdfRendererProtocol.HEIGHT, -1)
        if (!isValidPdfRenderSize(width, height)) {
            closeResponseMemory(bundle)
            throw PdfRendererFailure(PdfRendererFailureKind.RESOURCE_LIMIT)
        }
        val memory = bundle.getParcelable<SharedMemory>(PdfRendererProtocol.SHARED_MEMORY)
            ?: throw PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE)
        val expected = width * height * 4
        try {
            if (memory.size != expected) throw PdfRendererFailure(PdfRendererFailureKind.RESOURCE_LIMIT)
            val mapped = memory.mapReadOnly()
            try {
                if (mapped.remaining() != expected) throw PdfRendererFailure(PdfRendererFailureKind.RESOURCE_LIMIT)
                val pixels = ByteArray(expected)
                mapped.get(pixels)
                return PdfRenderedPage(width, height, pixels)
            } finally {
                SharedMemory.unmap(mapped)
            }
        } catch (failure: PdfRendererFailure) {
            throw failure
        } catch (_: RuntimeException) {
            throw PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE)
        } finally {
            runCatching { memory.close() }
        }
    }

    override suspend fun close() {
        if (!closed.compareAndSet(false, true)) return
        service.get()?.let { target ->
            runCatching {
                val message = Message.obtain(null, PdfRendererProtocol.CLOSE)
                message.replyTo = callbackMessenger
                message.data = Bundle().apply {
                    putLong(PdfRendererProtocol.REQUEST_ID, requestIds.incrementAndGet())
                    putString(PdfRendererProtocol.SESSION_ID, sessionId)
                }
                target.send(message)
            }
        }
        unbind()
    }

    override fun abort() {
        if (!closed.compareAndSet(false, true)) return
        val active = pending.get()
        service.get()?.let { target ->
            runCatching {
                if (active != null) {
                    val message = Message.obtain(null, PdfRendererProtocol.CANCEL)
                    message.data = Bundle().apply {
                        putLong(PdfRendererProtocol.REQUEST_ID, active.requestId)
                        putString(PdfRendererProtocol.SESSION_ID, sessionId)
                    }
                    target.send(message)
                } else {
                    val message = Message.obtain(null, PdfRendererProtocol.CLOSE)
                    message.data = Bundle().apply {
                        putLong(PdfRendererProtocol.REQUEST_ID, requestIds.incrementAndGet())
                        putString(PdfRendererProtocol.SESSION_ID, sessionId)
                    }
                    target.send(message)
                }
            }
        }
        if (active != null) {
            active.response.completeExceptionally(PdfRendererFailure(PdfRendererFailureKind.TIMEOUT))
        }
        unbind()
    }

    private suspend fun request(
        operation: Int,
        addPayload: (Bundle) -> Unit = {},
    ): Message {
        if (closed.get()) throw PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE)
        val target = connect()
        if (closed.get()) throw PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE)
        val id = requestIds.incrementAndGet()
        val response = CompletableDeferred<Message>()
        val entry = Pending(id, response)
        if (!pending.compareAndSet(null, entry)) throw PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE)
        try {
            val message = Message.obtain(null, operation)
            message.replyTo = callbackMessenger
            message.data = Bundle().apply {
                putLong(PdfRendererProtocol.REQUEST_ID, id)
                putString(PdfRendererProtocol.SESSION_ID, sessionId)
                addPayload(this)
            }
            target.send(message)
            Log.i(TAG, "request_sent_${operationName(operation)}")
            val result = response.await()
            Log.i(TAG, "response_received_${operationName(operation)}")
            if (result.what == PdfRendererProtocol.ERROR) throw mapError(result.data.getInt(PdfRendererProtocol.ERROR_KIND))
            return result
        } catch (failure: PdfRendererFailure) {
            throw failure
        } catch (_: RemoteException) {
            throw PdfRendererFailure(PdfRendererFailureKind.WORKER_DIED)
        } finally {
            if (pending.compareAndSet(entry, null) && !response.isCompleted) {
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
            val bound = withContext(Dispatchers.Main.immediate) {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
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
        pending.getAndSet(null)?.response?.completeExceptionally(error)
    }

    private fun unbind() {
        if (bindStarted.get() && unbound.compareAndSet(false, true)) {
            runCatching { context.unbindService(connection) }
        }
    }

    private inner class CallbackHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            val data = message.data.apply { classLoader = SharedMemory::class.java.classLoader }
            val requestId = data.getLong(PdfRendererProtocol.REQUEST_ID, -1L)
            val active = pending.get()
            if (active == null || active.requestId != requestId) {
                closeResponseMemory(data)
                return
            }
            if (message.what == PdfRendererProtocol.ERROR) {
                Log.w(TAG, "error_received")
                active.response.completeExceptionally(mapError(data.getInt(PdfRendererProtocol.ERROR_KIND)))
            } else {
                Log.i(TAG, "response_delivered")
                active.response.complete(Message.obtain(message).apply { this.data = Bundle(data) })
            }
        }
    }

    private data class Pending(val requestId: Long, val response: CompletableDeferred<Message>)

    private fun mapError(code: Int): PdfRendererFailure = PdfRendererFailure(
        when (code) {
            PdfRendererProtocol.ERROR_MALFORMED -> PdfRendererFailureKind.MALFORMED
            PdfRendererProtocol.ERROR_ENCRYPTED -> PdfRendererFailureKind.ENCRYPTED_OR_UNSUPPORTED
            PdfRendererProtocol.ERROR_RESOURCE -> PdfRendererFailureKind.RESOURCE_LIMIT
            PdfRendererProtocol.ERROR_INVALID_PAGE -> PdfRendererFailureKind.INVALID_PAGE
            else -> PdfRendererFailureKind.UNAVAILABLE
        },
    )

    private fun closeResponseMemory(bundle: Bundle) {
        runCatching { bundle.getParcelable<SharedMemory>(PdfRendererProtocol.SHARED_MEMORY)?.close() }
    }

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
