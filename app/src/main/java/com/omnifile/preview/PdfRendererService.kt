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
    private var deadline: Runnable? = null

    override fun onBind(intent: android.content.Intent?): IBinder = messenger.binder

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
            val replyTo = message.replyTo ?: return
            val data = message.data.apply { classLoader = ParcelFileDescriptor::class.java.classLoader }
            val requestId = data.getLong(PdfRendererProtocol.REQUEST_ID, -1L)
            val sessionId = data.getString(PdfRendererProtocol.SESSION_ID) ?: return
            when (message.what) {
                PdfRendererProtocol.OPEN -> {
                    val descriptor = data.getParcelable<ParcelFileDescriptor>(PdfRendererProtocol.DESCRIPTOR) ?: run {
                        sendError(replyTo, requestId, PdfRendererProtocol.ERROR_UNAVAILABLE)
                        return
                    }
                    startDeadline(requestId)
                    renderExecutor.execute { openDocument(sessionId, requestId, descriptor, replyTo) }
                }
                PdfRendererProtocol.RENDER -> {
                    val pageIndex = data.getInt(PdfRendererProtocol.PAGE_INDEX, -1)
                    startDeadline(requestId)
                    renderExecutor.execute { renderPage(sessionId, requestId, pageIndex, replyTo) }
                }
                PdfRendererProtocol.CLOSE -> {
                    startDeadline(requestId)
                    renderExecutor.execute {
                        val ownedSession = activeSessionId == sessionId
                        if (ownedSession) closeDocument()
                        sendSimple(replyTo, PdfRendererProtocol.CLOSED, requestId)
                        finishDeadline(requestId)
                        if (ownedSession) stopSelf()
                    }
                }
                PdfRendererProtocol.CANCEL -> {
                    if (requestId == activeRequestId) Process.killProcess(Process.myPid())
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
            closeDocument()
            val opened = PdfRenderer(descriptor)
            renderer = opened
            sourceDescriptor = descriptor
            activeSessionId = sessionId
            sendSimple(replyTo, PdfRendererProtocol.OPENED, requestId) { putInt(PdfRendererProtocol.PAGE_COUNT, opened.pageCount) }
        } catch (failure: Throwable) {
            runCatching { descriptor.close() }
            sendError(replyTo, requestId, classifyOpenFailure(failure))
        } finally {
            finishDeadline(requestId)
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
            finishDeadline(requestId)
        }
    }

    private fun closeDocument() {
        runCatching { renderer?.close() }
        renderer = null
        runCatching { sourceDescriptor?.close() }
        sourceDescriptor = null
        activeSessionId = null
    }

    private fun startDeadline(requestId: Long) {
        deadline?.let(mainHandler::removeCallbacks)
        activeRequestId = requestId
        deadline = Runnable {
            if (activeRequestId == requestId) Process.killProcess(Process.myPid())
        }.also { mainHandler.postDelayed(it, PreviewLimits.PDF_RENDER_TIMEOUT_MILLIS) }
    }

    private fun finishDeadline(requestId: Long) {
        val clear = Runnable {
            if (activeRequestId == requestId) {
                deadline?.let(mainHandler::removeCallbacks)
                deadline = null
                activeRequestId = -1L
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
}
