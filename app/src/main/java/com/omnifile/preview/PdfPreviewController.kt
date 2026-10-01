package com.omnifile.preview

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
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

/** One reservation serializes controller opening and the handoff from acquisition. */
internal class PdfControllerReservation(private val mutex: Mutex) : AutoCloseable {
    private val released = AtomicBoolean(false)
    override fun close() { if (released.compareAndSet(false, true)) mutex.unlock() }
}

internal sealed interface PdfPreparedAcquisition {
    data class Ready(val staged: PdfStageResult.Ready,
        val reservation: com.omnifile.storage.ReadLease<PdfControllerReservation>,
        val scope: com.omnifile.storage.ReadAcquisitionScope) : PdfPreparedAcquisition {
        init { require(staged.acquisitionLease != null) }
    }
    data class Failure(val error: PreviewError) : PdfPreparedAcquisition
    fun dispose() { if (this is Ready) { staged.disposeCandidate(); reservation.close() } }
    fun transfer() { if (this is Ready) scope.transferTogether(listOf(staged.acquisitionLease!!, reservation)) }
}

class PdfPreviewController(
    internal val snapshots: PdfSnapshotStore,
    private val clients: PdfRendererClientFactory,
    private val renderTimeoutMillis: Long = PreviewLimits.PDF_RENDER_TIMEOUT_MILLIS,
) {
    private val pendingSnapshots = java.util.concurrent.ConcurrentHashMap<PdfSnapshot, Unit>()
    internal val openingSnapshotCount: Int get() {
        check(com.omnifile.BuildConfig.DEBUG)
        return pendingSnapshots.size
    }
    private val openMutex = Mutex()
    private val activeSession = java.util.concurrent.atomic.AtomicReference<OwnedPdfDocumentSession?>(null)
    // Opening waits for the exact prior session's cleanup; at most one cleanup can be outstanding.
    private val cleanupScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO.limitedParallelism(1))

    internal suspend fun reserve(): PdfControllerReservation {
        val reservation = PdfControllerReservation(openMutex)
        openMutex.lock()
        return reservation
    }

    suspend fun open(request: PreviewRequest, requestedPage: Int = 0): PdfOpenResult {
        val prepared = try {
            snapshots.acquisitions.acquire(dispose = { value: PdfPreparedAcquisition -> value.dispose() },
                successful = { it is PdfPreparedAcquisition.Ready }, transfer = { it.transfer() }) {
                prepareScoped(request)
            }
        } catch (error: com.omnifile.storage.ReadAcquisitionException) {
            return PdfOpenResult.Failure(acquisitionError(error))
        }
        return when (prepared) {
            is PdfPreparedAcquisition.Failure -> PdfOpenResult.Failure(prepared.error)
            is PdfPreparedAcquisition.Ready -> openReserved(prepared.staged.snapshot, prepared.reservation.value, requestedPage)
        }
    }

    /** Used by both PDF entry points inside one existing admission, never nested admission. */
    internal suspend fun prepareScoped(request: PreviewRequest): PdfPreparedAcquisition {
        val scope = com.omnifile.storage.ReadAcquisitionScope.current()!!
        val worker = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]!!
        val reservation = scope.operation(cancel = { worker.cancel() }, dispose = { it: PdfControllerReservation -> it.close() }) { reserve() }
        val lease = scope.own(reservation) { it.close() }
        return when (val staged = snapshots.stage(request)) {
            is PdfStageResult.Ready -> PdfPreparedAcquisition.Ready(staged, lease, scope)
            is PdfStageResult.Failure -> { lease.close(); PdfPreparedAcquisition.Failure(staged.error) }
        }
    }

    /** Owns snapshot and reservation on entry, including cancellation while awaiting old cleanup. */
    internal suspend fun openReserved(snapshot: PdfSnapshot, reservation: PdfControllerReservation, requestedPage: Int): PdfOpenResult {
        pendingSnapshots[snapshot] = Unit
        // Install the cleanup owner before any suspension or renderer creation can fail.
        var session: OwnedPdfDocumentSession? = null
        var delivered = false
        try {
            activeSession.get()?.let { prior ->
                prior.invalidate()
                withTimeout(renderTimeoutMillis) { prior.awaitCleanup() }
                activeSession.compareAndSet(prior, null)
            }
            val owned = OwnedPdfDocumentSession(snapshot, renderTimeoutMillis, cleanupScope) { exact ->
                activeSession.compareAndSet(exact, null)
            }
            session = owned
            activeSession.set(owned)
            pendingSnapshots.remove(snapshot)
            val openedClient = clients.create()
            owned.installClient(openedClient)
            val pageCount = withTimeout(renderTimeoutMillis) { openedClient.open(snapshot) }
            if (pageCount <= 0) return PdfOpenResult.Failure(PreviewError.CorruptOrMalformed)
            if (pageCount > PreviewLimits.MAX_PDF_PAGES) return PdfOpenResult.Failure(PreviewError.PdfPageCountLimit)
            owned.installPageCount(pageCount)
            val index = requestedPage.coerceIn(0, pageCount - 1)
            val page = owned.renderPage(index)
            val result = PdfOpenResult.Ready(PreviewPayload.PdfPage(page, index, pageCount, owned))
            delivered = true
            return result
        } catch (_: TimeoutCancellationException) {
            return PdfOpenResult.Failure(PreviewError.RendererTimeout)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: PdfRendererFailure) {
            return PdfOpenResult.Failure(failure.toPreviewError())
        } catch (_: OutOfMemoryError) {
            return PdfOpenResult.Failure(PreviewError.ResourceLimit)
        } catch (_: Exception) {
            return PdfOpenResult.Failure(PreviewError.RendererFailure)
        } finally {
            if (session == null) {
                // No client was created. This background caller still owns the candidate.
                snapshot.close()
                pendingSnapshots.remove(snapshot)
            } else if (!delivered) {
                // Cleanup survives caller cancellation; its exact owner remains in activeSession.
                session.invalidate()
            }
            reservation.close()
        }
    }
}

private class OwnedPdfDocumentSession(
    private val snapshot: PdfSnapshot,
    private val renderTimeoutMillis: Long,
    private val cleanupScope: kotlinx.coroutines.CoroutineScope,
    private val onClosed: (OwnedPdfDocumentSession) -> Unit,
) : PdfDocumentSession, PdfRendererDeathTestHook {
    private var client: PdfRendererClient? = null
    override var pageCount: Int = 0
        private set
    fun installClient(value: PdfRendererClient) { check(client == null); client = value }
    fun installPageCount(value: Int) { pageCount = value }
    private val closed = AtomicBoolean(false)
    private val cleanup = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val pageMutex = Mutex()
    val isClosed: Boolean get() = closed.get()

    override suspend fun killRendererForTest() {
        (client as? PdfRendererDeathTestHook
            ?: throw UnsupportedOperationException("Renderer does not expose the instrumentation death hook"))
            .killRendererForTest()
    }

    override fun invalidate() { startCleanup(abort = true) }

    private fun startCleanup(abort: Boolean) {
        if (!closed.compareAndSet(false, true)) return
        cleanupScope.launch {
            try {
                try {
                    client?.let {
                        if (abort) it.abort() else it.close()
                        it.awaitShutdown()
                    }
                } finally { snapshot.close() }
                onClosed(this@OwnedPdfDocumentSession)
                cleanup.complete(Unit)
            } catch (error: Throwable) {
                // Keep the controller's exact session owner on uncertain cleanup; no unsafe reuse.
                cleanup.completeExceptionally(error)
            }
        }
    }

    suspend fun awaitCleanup() { cleanup.await() }

    override suspend fun renderPage(pageIndex: Int): PdfRenderedPage = pageMutex.withLock {
        if (closed.get()) throw PdfRendererFailure(PdfRendererFailureKind.UNAVAILABLE)
        if (pageIndex !in 0 until pageCount) throw PdfRendererFailure(PdfRendererFailureKind.INVALID_PAGE)
        try {
            val page = withTimeout(renderTimeoutMillis) { requireNotNull(client).renderPage(pageIndex) }
            if (closed.get()) throw PdfRendererFailure(PdfRendererFailureKind.WORKER_DIED)
            if (!isValidPdfRenderSize(page.width, page.height)) throw PdfRendererFailure(PdfRendererFailureKind.RESOURCE_LIMIT)
            page
        } catch (_: TimeoutCancellationException) {
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

    override suspend fun close() { startCleanup(abort = false); awaitCleanup() }
}
