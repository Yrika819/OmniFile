package com.omnifile.preview

import android.app.Service
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SharedMemory
import android.system.OsConstants
import android.util.Log
import java.io.IOException
import java.util.concurrent.Executors

/** Runs PDFium-backed platform rendering only in the manifest-declared isolated process. */
class PdfRendererService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val renderExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omnifile-pdf-renderer").apply { isDaemon = true }
    }
    private val messenger by lazy { Messenger(IncomingHandler()) }

    // Accessed only by renderExecutor.
    private var renderer: PdfRenderer? = null
    private var sourceDescriptor: ParcelFileDescriptor? = null
    private var activeSessionId: String? = null
    private var activeRequestId: Long = -1L
    private var activeRequestSessionId: String? = null
    private var deadline: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        if (Process.isIsolated()) {
            Log.i(TAG, "worker_created")
        } else {
            Log.e(TAG, "worker_not_isolated")
        }
    }

    override fun onBind(intent: android.content.Intent?): IBinder? {
        if (!Process.isIsolated()) return null
        Log.i(TAG, "worker_bound")
        return messenger.binder
    }

    override fun onDestroy() {
        deadline?.let(mainHandler::removeCallbacks)
        val cleanupDeadline = Runnable { Process.killProcess(Process.myPid()) }
        mainHandler.postDelayed(cleanupDeadline, PreviewLimits.PDF_RENDER_TIMEOUT_MILLIS)
        runCatching {
            renderExecutor.execute {
                closeDocument()
                mainHandler.post { mainHandler.removeCallbacks(cleanupDeadline) }
            }
            renderExecutor.shutdown()
        }.onFailure { renderExecutor.shutdownNow() }
        super.onDestroy()
    }

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            val replyTo = message.replyTo ?: run {
                Log.w(TAG, "request_without_reply_channel")
                return
            }
            val data = message.data.apply { classLoader = ParcelFileDescriptor::class.java.classLoader }
            val requestId = data.getLong(PdfRendererProtocol.REQUEST_ID, -1L)
            val sessionId = data.getString(PdfRendererProtocol.SESSION_ID) ?: return
            when (message.what) {
                PdfRendererProtocol.OPEN -> {
                    Log.i(TAG, "open_received")
                    val descriptor = data.getParcelable<ParcelFileDescriptor>(PdfRendererProtocol.DESCRIPTOR) ?: run {
                        sendError(replyTo, requestId, PdfRendererProtocol.ERROR_UNAVAILABLE)
                        return
                    }
                    startDeadline(requestId, sessionId)
                    renderExecutor.execute { openDocument(sessionId, requestId, descriptor, replyTo) }
                }
                PdfRendererProtocol.RENDER -> {
                    val pageIndex = data.getInt(PdfRendererProtocol.PAGE_INDEX, -1)
                    startDeadline(requestId, sessionId)
                    renderExecutor.execute { renderPage(sessionId, requestId, pageIndex, replyTo) }
                }
                PdfRendererProtocol.CLOSE -> {
                    startDeadline(requestId, sessionId)
                    renderExecutor.execute {
                        val ownedSession = activeSessionId == sessionId
                        if (ownedSession) closeDocument()
                        sendSimple(replyTo, PdfRendererProtocol.CLOSED, requestId)
                        finishDeadline(requestId, sessionId)
                        if (ownedSession) stopSelf()
                    }
                }
                PdfRendererProtocol.CANCEL -> {
                    if (requestId == activeRequestId && sessionId == activeRequestSessionId) {
                        Process.killProcess(Process.myPid())
                    }
                }
                else -> super.handleMessage(message)
            }
        }
    }

    private fun openDocument(
        sessionId: String,
        requestId: Long,
        descriptor: ParcelFileDescriptor,
        replyTo: Messenger,
    ) {
        try {
            Log.i(TAG, "open_started")
            closeDocument()
            val opened = PdfRenderer(descriptor)
            renderer = opened
            sourceDescriptor = descriptor
            activeSessionId = sessionId
            sendSimple(replyTo, PdfRendererProtocol.OPENED, requestId) { putInt(PdfRendererProtocol.PAGE_COUNT, opened.pageCount) }
            Log.i(TAG, "open_succeeded")
        } catch (failure: Throwable) {
            Log.w(TAG, "open_failed_${failure.javaClass.simpleName}")
            runCatching { descriptor.close() }
            sendError(replyTo, requestId, classifyOpenFailure(failure))
        } finally {
            finishDeadline(requestId, sessionId)
        }
    }

    private fun renderPage(sessionId: String, requestId: Long, pageIndex: Int, replyTo: Messenger) {
        var page: PdfRenderer.Page? = null
        var bitmap: Bitmap? = null
        var shared: SharedMemory? = null
        try {
            val current = renderer
            if (current == null || activeSessionId != sessionId) {
                sendError(replyTo, requestId, PdfRendererProtocol.ERROR_UNAVAILABLE)
                return
            }
            if (pageIndex !in 0 until current.pageCount) {
                sendError(replyTo, requestId, PdfRendererProtocol.ERROR_INVALID_PAGE)
                return
            }
            page = current.openPage(pageIndex)
            val dimensions = PdfPageGeometry.bounded(page.width, page.height)
                ?: run {
                    sendError(replyTo, requestId, PdfRendererProtocol.ERROR_RESOURCE)
                    return
                }
            bitmap = Bitmap.createBitmap(dimensions.width, dimensions.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            val byteCount = dimensions.width * dimensions.height * 4
            val outputMemory = SharedMemory.create("omnifile-pdf-page", byteCount)
            shared = outputMemory
            val mapped = outputMemory.mapReadWrite()
            try {
                bitmap.copyPixelsToBuffer(mapped)
            } finally {
                SharedMemory.unmap(mapped)
            }
            if (!outputMemory.setProtect(OsConstants.PROT_READ)) {
                sendError(replyTo, requestId, PdfRendererProtocol.ERROR_UNAVAILABLE)
                return
            }
            val result = Message.obtain(null, PdfRendererProtocol.PAGE)
            result.data = android.os.Bundle().apply {
                putLong(PdfRendererProtocol.REQUEST_ID, requestId)
                putInt(PdfRendererProtocol.WIDTH, dimensions.width)
                putInt(PdfRendererProtocol.HEIGHT, dimensions.height)
                putParcelable(PdfRendererProtocol.SHARED_MEMORY, outputMemory)
            }
            replyTo.send(result)
            // Messenger has parcelled a duplicate descriptor; release the worker's copy now.
            runCatching { outputMemory.close() }
            shared = null
        } catch (failure: Throwable) {
            sendError(replyTo, requestId, classifyRenderFailure(failure))
        } finally {
            runCatching { page?.close() }
            runCatching { bitmap?.recycle() }
            runCatching { shared?.close() }
            finishDeadline(requestId, sessionId)
        }
    }

    private fun closeDocument() {
        runCatching { renderer?.close() }
        renderer = null
        runCatching { sourceDescriptor?.close() }
        sourceDescriptor = null
        activeSessionId = null
    }

    private fun startDeadline(requestId: Long, sessionId: String) {
        deadline?.let(mainHandler::removeCallbacks)
        activeRequestId = requestId
        activeRequestSessionId = sessionId
        deadline = Runnable {
            if (activeRequestId == requestId && activeRequestSessionId == sessionId) {
                Process.killProcess(Process.myPid())
            }
        }.also { mainHandler.postDelayed(it, PreviewLimits.PDF_RENDER_TIMEOUT_MILLIS) }
    }

    private fun finishDeadline(requestId: Long, sessionId: String) {
        val clear = Runnable {
            if (activeRequestId == requestId && activeRequestSessionId == sessionId) {
                deadline?.let(mainHandler::removeCallbacks)
                deadline = null
                activeRequestId = -1L
                activeRequestSessionId = null
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) clear.run() else mainHandler.post(clear)
    }

    private fun sendSimple(
        target: Messenger,
        what: Int,
        requestId: Long,
        addData: android.os.Bundle.() -> Unit = {},
    ) {
        runCatching {
            target.send(Message.obtain(null, what).apply {
                data = android.os.Bundle().apply {
                    putLong(PdfRendererProtocol.REQUEST_ID, requestId)
                    addData()
                }
            })
        }
    }

    private fun sendError(target: Messenger, requestId: Long, errorKind: Int) {
        sendSimple(target, PdfRendererProtocol.ERROR, requestId) {
            putInt(PdfRendererProtocol.ERROR_KIND, errorKind)
        }
    }

    private fun classifyOpenFailure(failure: Throwable): Int = when {
        isEncrypted(failure) -> PdfRendererProtocol.ERROR_ENCRYPTED
        failure is IOException || failure is IllegalArgumentException || failure is IllegalStateException ->
            PdfRendererProtocol.ERROR_MALFORMED
        else -> PdfRendererProtocol.ERROR_UNAVAILABLE
    }

    private fun classifyRenderFailure(failure: Throwable): Int = when {
        isEncrypted(failure) -> PdfRendererProtocol.ERROR_ENCRYPTED
        failure is OutOfMemoryError -> PdfRendererProtocol.ERROR_RESOURCE
        failure is IOException || failure is IllegalArgumentException || failure is IllegalStateException ->
            PdfRendererProtocol.ERROR_MALFORMED
        else -> PdfRendererProtocol.ERROR_UNAVAILABLE
    }

    private fun isEncrypted(failure: Throwable): Boolean {
        var current: Throwable? = failure
        while (current != null) {
            if (current is SecurityException) return true
            val message = current.message?.lowercase().orEmpty()
            if ("password" in message || "encrypt" in message) return true
            current = current.cause
        }
        return false
    }

    private companion object {
        const val TAG = "OmniPdfWorker"
    }
}
