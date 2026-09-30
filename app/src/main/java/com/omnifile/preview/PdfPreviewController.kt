package com.omnifile.preview

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

sealed interface PdfOpenResult {
    data class Ready(val payload: PreviewPayload.PdfPage) : PdfOpenResult
    data class Failure(val error: PreviewError) : PdfOpenResult
}

internal fun PdfRendererFailure.toPreviewError(): PreviewError = when (kind) {
    PdfRendererFailureKind.TIMEOUT -> PreviewError.RendererTimeout
    PdfRendererFailureKind.WORKER_DIED, PdfRendererFailureKind.UNAVAILABLE -> PreviewError.RendererFailure
    PdfRendererFailureKind.MALFORMED -> PreviewError.CorruptOrMalformed
    PdfRendererFailureKind.ENCRYPTED_OR_UNSUPPORTED -> PreviewError.EncryptedOrUnsupported
    PdfRendererFailureKind.INVALID_PAGE -> PreviewError.CorruptOrMalformed
    PdfRendererFailureKind.RESOURCE_LIMIT -> PreviewError.ResourceLimit
}

class PdfPreviewController(
    internal val snapshots: PdfSnapshotStore,
    private val clients: PdfRendererClientFactory,
    private val renderTimeoutMillis: Long = PreviewLimits.PDF_RENDER_TIMEOUT_MILLIS,
) {
    private val openMutex = Mutex()
    @Volatile private var activeSession: OwnedPdfDocumentSession? = null

    suspend fun open(request: PreviewRequest, requestedPage: Int = 0): PdfOpenResult = openMutex.withLock {
        activeSession?.invalidate()
        activeSession = null
        openLocked(request, requestedPage)
    }

    private suspend fun openLocked(request: PreviewRequest, requestedPage: Int): PdfOpenResult {
        val snapshot = when (val result = snapshots.stage(request)) {
            is PdfStageResult.Ready -> result.snapshot
            is PdfStageResult.Failure -> return PdfOpenResult.Failure(result.error)
        }
        val client = try {
            clients.create()
        } catch (_: OutOfMemoryError) {
            snapshot.close()
            return PdfOpenResult.Failure(PreviewError.ResourceLimit)
        } catch (_: Exception) {
            snapshot.close()
            return PdfOpenResult.Failure(PreviewError.RendererFailure)
        }
        val pageCount = try {
            withTimeout(renderTimeoutMillis) { client.open(snapshot) }
        } catch (timeout: TimeoutCancellationException) {
            client.abort()
            snapshot.close()
            return PdfOpenResult.Failure(PreviewError.RendererTimeout)
        } catch (cancel: CancellationException) {
            client.abort()
            snapshot.close()
            throw cancel
        } catch (failure: PdfRendererFailure) {
            client.abort()
            snapshot.close()
            return PdfOpenResult.Failure(failure.toPreviewError())
        } catch (_: OutOfMemoryError) {
            client.abort()
            snapshot.close()
            return PdfOpenResult.Failure(PreviewError.ResourceLimit)
        } catch (_: Exception) {
            client.abort()
            snapshot.close()
            return PdfOpenResult.Failure(PreviewError.RendererFailure)
        }
        if (pageCount <= 0) {
            client.abort()
            snapshot.close()
            return PdfOpenResult.Failure(PreviewError.CorruptOrMalformed)
        }
        if (pageCount > PreviewLimits.MAX_PDF_PAGES) {
            client.abort()
            snapshot.close()
            return PdfOpenResult.Failure(PreviewError.PdfPageCountLimit)
        }

        var createdSession: OwnedPdfDocumentSession? = null
        val session = OwnedPdfDocumentSession(snapshot, client, pageCount, renderTimeoutMillis) {
            if (activeSession === createdSession) activeSession = null
        }
        createdSession = session
        activeSession = session
        val pageIndex = requestedPage.coerceIn(0, pageCount - 1)
        return try {
            val page = session.renderPage(pageIndex)
            PdfOpenResult.Ready(PreviewPayload.PdfPage(page, pageIndex, pageCount, session))
        } catch (cancel: CancellationException) {
            session.close()
            throw cancel
        } catch (failure: PdfRendererFailure) {
            session.close()
            PdfOpenResult.Failure(failure.toPreviewError())
        } catch (_: OutOfMemoryError) {
            session.invalidate()
            PdfOpenResult.Failure(PreviewError.ResourceLimit)
        } catch (_: Exception) {
            session.close()
            PdfOpenResult.Failure(PreviewError.RendererFailure)
        }
    }
}

private class OwnedPdfDocumentSession(
    private val snapshot: PdfSnapshot,
    private val client: PdfRendererClient,
    override val pageCount: Int,
    private val renderTimeoutMillis: Long,
    private val onClosed: () -> Unit,
) : PdfDocumentSession, PdfRendererDeathTestHook {
    private val closed = AtomicBoolean(false)
    private val pageMutex = Mutex()

    override suspend fun killRendererForTest() {
        val hook = client as? PdfRendererDeathTestHook
            ?: throw UnsupportedOperationException("Renderer does not expose the instrumentation death hook")
        hook.killRendererForTest()
    }

    override fun invalidate() {
        if (closed.compareAndSet(false, true)) {
            client.abort()
            snapshot.close()
            onClosed()
        }
    }

    override suspend fun renderPage(pageIndex: Int): PdfRenderedPage = pageMutex.withLock {
        if (closed.get()) throw PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE)
        if (pageIndex !in 0 until pageCount) throw PdfRendererFailure(PdfRendererFailureKind.INVALID_PAGE)
        try {
            val page = withTimeout(renderTimeoutMillis) { client.renderPage(pageIndex) }
            if (closed.get()) throw PdfRendererFailure(PdfRendererFailureKind.WORKER_DIED)
            if (!isValidPdfRenderSize(page.width, page.height)) {
                throw PdfRendererFailure(PdfRendererFailureKind.RESOURCE_LIMIT)
            }
            page
        } catch (timeout: TimeoutCancellationException) {
            invalidate()
            throw PdfRendererFailure(PdfRendererFailureKind.TIMEOUT)
        } catch (_: OutOfMemoryError) {
            invalidate()
            throw PdfRendererFailure(PdfRendererFailureKind.RESOURCE_LIMIT)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: PdfRendererFailure) {
            throw failure
        } catch (_: Exception) {
            throw PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE)
        }
    }

    override suspend fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            client.close()
        } catch (_: Exception) {
            client.abort()
        } finally {
            snapshot.close()
            onClosed()
        }
    }
}
