package com.omnifile.preview

import androidx.lifecycle.ViewModel
import com.omnifile.storage.StorageResult
import java.io.IOException
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main owns every document/page transition. Background work returns owned candidates only. */
class PreviewViewModel(
    private val engine: PreviewEngine = PreviewEngine(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val ownsScope = scope == null
    private val lifecycleScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // A serial dispatcher is injectable for host tests; production is always Main.immediate.
    private val main = if (scope == null || !com.omnifile.BuildConfig.DEBUG) Dispatchers.Main.immediate else {
        val supplied = scope.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher ?: Dispatchers.Main.immediate
        if (supplied == Dispatchers.Unconfined) SerialInlineTestDispatcher() else supplied.limitedParallelism(1)
    }
    private val _state = MutableStateFlow<PreviewUiState>(PreviewUiState.Idle)
    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    private var generation = 0L
    private var pageGeneration = 0L
    private var opening: Opening? = null
    private var document: Document? = null
    private var drain: Drain? = null
    private var queuedPage: Int? = null
    private var activePage: Int? = null
    private var currentItem: PreviewItem? = null
    private var currentResolver: (suspend () -> StorageResult<PreviewSource>)? = null
    private var currentPage = 0

    private class Opening(val generation: Long) { lateinit var job: Job }
    private class Document(val session: PdfDocumentSession, val item: PreviewItem, val label: String)
    private class Drain(val document: Document) { lateinit var job: Job }

    private fun onMain(action: () -> Unit): Job = lifecycleScope.launch(main) { action() }

    fun open(item: PreviewItem, resolveSource: suspend () -> StorageResult<PreviewSource>) {
        onMain { openAt(item, resolveSource, 0) }
    }

    private fun openAt(item: PreviewItem, resolveSource: suspend () -> StorageResult<PreviewSource>, startingPage: Int) {
        invalidateOwners()
        currentItem = item
        currentResolver = resolveSource
        currentPage = startingPage.coerceAtLeast(0)
        _state.value = PreviewUiState.Loading(item)
        val token = Opening(generation)
        opening = token
        token.job = lifecycleScope.launch(main, start = CoroutineStart.LAZY) {
            val delivery = OwningResponse<ResolvedPreview> { it.disposeCandidate() }
            try {
                // Unit crosses the dispatcher boundary. The holder retains resources if cancellation
                // discards the resumption; a resource-bearing withContext result would be unsafe.
                withContext(dispatcher) {
                    delivery.offer(engine.resolveAndLoad(item, resolveSource, startingPage.coerceAtLeast(0)))
                }
                val candidate = delivery.awaitAndTransfer()
                accept(token, item, candidate)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: SecurityException) {
                publishError(token, item, PreviewError.PermissionOrGrantMissing)
            } catch (error: IOException) {
                publishError(token, item, PreviewError.IoFailure(error.message))
            } catch (_: OutOfMemoryError) {
                if (token.generation == generation) {
                    document?.let { clearDocument(it) }
                    publishError(token, item, PreviewError.ResourceLimit)
                }
            } catch (_: Exception) {
                publishError(token, item, PreviewError.Unknown)
            } finally {
                delivery.dispose()
                if (opening === token) opening = null
            }
        }
        token.job.start()
    }

    /** One serialized acceptance transition; there is no suspension between comparison and publish. */
    private fun accept(token: Opening, item: PreviewItem, candidate: ResolvedPreview) {
        var transferred = false
        try {
            if (token.generation != generation) return
            when (val payload = candidate.payload) {
                PreviewPayload.Unsupported -> _state.value = PreviewUiState.Unsupported(item)
                is PreviewPayload.Failure -> _state.value = PreviewUiState.Error(item, payload.error)
                else -> {
                    if (payload is PreviewPayload.PdfPage) {
                        val installed = Document(payload.session, item, candidate.sourceLabel)
                        document?.let { clearDocument(it) }
                        document = installed
                        currentPage = payload.pageIndex
                        queuedPage = null
                        activePage = null
                        transferred = true // document now owns the session even if publication throws
                    }
                    _state.value = PreviewUiState.Ready(item, candidate.sourceLabel, payload)
                    transferred = true
                }
            }
        } finally { if (!transferred) candidate.disposeCandidate() }
    }

    fun previousPage() { onMain { requestRelativePage(-1) } }
    fun nextPage() { onMain { requestRelativePage(1) } }

    private fun requestRelativePage(delta: Int) {
        val owner = document ?: return
        val target = (queuedPage ?: activePage ?: currentPage) + delta
        if (target !in 0 until owner.session.pageCount) return
        pageGeneration++
        queuedPage = target
        _state.value = PreviewUiState.Loading(owner.item)
        if (drain == null) startDrain(owner)
    }

    private fun startDrain(owner: Document) {
        val token = Drain(owner)
        drain = token
        token.job = lifecycleScope.launch(main, start = CoroutineStart.LAZY) {
            try {
                while (document === owner && drain === token) {
                    val target = queuedPage ?: return@launch
                    queuedPage = null
                    activePage = target
                    val requestedGeneration = pageGeneration
                    val page = try {
                        withContext(dispatcher) { owner.session.renderPage(target) }
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (failure: PdfRendererFailure) {
                        pageFailed(token, requestedGeneration, failure.toPreviewError())
                        return@launch
                    } catch (_: OutOfMemoryError) {
                        pageFailed(token, requestedGeneration, PreviewError.ResourceLimit)
                        return@launch
                    } catch (_: Exception) {
                        pageFailed(token, requestedGeneration, PreviewError.RendererFailure)
                        return@launch
                    }
                    if (document !== owner || drain !== token) return@launch
                    activePage = null
                    if (requestedGeneration == pageGeneration && queuedPage == null) {
                        currentPage = target
                        _state.value = PreviewUiState.Ready(owner.item, owner.label,
                            PreviewPayload.PdfPage(page, target, owner.session.pageCount, owner.session))
                    }
                }
            } finally {
                // An old drain can never clear or restart a newer drain's bookkeeping.
                if (drain === token) {
                    drain = null
                    activePage = null
                    if (document === owner && queuedPage != null) startDrain(owner)
                }
            }
        }
        token.job.start()
    }

    private fun pageFailed(token: Drain, requestedGeneration: Long, error: PreviewError) {
        val owner = token.document
        if (document === owner && drain === token && requestedGeneration == pageGeneration) {
            clearDocument(owner)
            _state.value = PreviewUiState.Error(owner.item, error)
        }
    }

    private fun clearDocument(owner: Document) {
        if (document === owner) {
            document = null
            queuedPage = null
            activePage = null
        }
        owner.session.invalidate() // logical invalidation only; controller owns asynchronous cleanup
    }

    private fun publishError(owner: Opening, item: PreviewItem, error: PreviewError) {
        if (owner.generation == generation) _state.value = PreviewUiState.Error(item, error)
    }

    fun retry() {
        onMain {
            val item = currentItem ?: return@onMain
            val resolver = currentResolver ?: return@onMain
            openAt(item, resolver, currentPage)
        }
    }

    private fun invalidateOwners() {
        generation++
        pageGeneration++
        opening?.job?.cancel()
        opening = null
        drain?.job?.cancel()
        drain = null
        queuedPage = null
        activePage = null
        document?.let { clearDocument(it) }
    }

    fun close() { onMain { closeOwned() } }
    private fun closeOwned() {
        invalidateOwners()
        currentItem = null
        currentResolver = null
        currentPage = 0
        _state.value = PreviewUiState.Idle
    }

    override fun onCleared() {
        val clearing = onMain { closeOwned() }
        if (ownsScope) clearing.invokeOnCompletion { lifecycleScope.cancel() }
        super.onCleared()
    }
}

private fun ResolvedPreview.disposeCandidate() {
    (payload as? PreviewPayload.PdfPage)?.session?.invalidate()
    (payload as? PreviewPayload.Image)?.bitmap?.recycle()
}

/** Debug test seam: synchronous where possible, serialized even across background resumptions. */
private class SerialInlineTestDispatcher : CoroutineDispatcher() {
    private val queue = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
    private val draining = java.util.concurrent.atomic.AtomicBoolean(false)
    override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
        queue.add(block)
        do {
            if (!draining.compareAndSet(false, true)) return
            try { while (true) (queue.poll() ?: break).run() }
            finally { draining.set(false) }
        } while (queue.isNotEmpty())
    }
}
