package com.omnifile.preview

import androidx.lifecycle.ViewModel
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owns Preview generations, one PDF document, and at most one in-flight page render. */
class PreviewViewModel(
    private val engine: PreviewEngine = PreviewEngine(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val ownsScope = scope == null
    private val lifecycleScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<PreviewUiState>(PreviewUiState.Idle)
    private val generation = AtomicLong()
    private val pageGeneration = AtomicLong()
    private val pageQueueLock = Any()
    private var job: Job? = null
    @Volatile private var pageJob: Job? = null
    private var queuedPageIndex: Int? = null
    private var activePageIndex: Int? = null
    @Volatile private var currentItem: PreviewItem? = null
    private var currentResolver: (suspend () -> StorageResult<PreviewSource>)? = null
    @Volatile private var currentSourceLabel: String = ""
    @Volatile private var currentPageIndex: Int = 0
    @Volatile private var pdfSession: PdfDocumentSession? = null

    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    fun open(item: PreviewItem, resolveSource: suspend () -> StorageResult<PreviewSource>) {
        openAt(item, resolveSource, 0)
    }

    private fun openAt(
        item: PreviewItem,
        resolveSource: suspend () -> StorageResult<PreviewSource>,
        startingPage: Int,
    ) {
        val owner = generation.incrementAndGet()
        pageGeneration.incrementAndGet()
        job?.cancel()
        pageJob?.cancel()
        synchronized(pageQueueLock) {
            queuedPageIndex = null
            activePageIndex = null
        }
        pdfSession?.invalidate()
        pdfSession = null
        job = null
        pageJob = null
        currentItem = item
        currentResolver = resolveSource
        val requestedStartingPage = startingPage.coerceAtLeast(0)
        currentPageIndex = requestedStartingPage
        currentSourceLabel = ""
        _state.value = PreviewUiState.Loading(item)
        job = lifecycleScope.launch(dispatcher) {
            try {
                val resolved = engine.resolveAndLoad(item, resolveSource, requestedStartingPage)
                when (val result = resolved.payload) {
                    PreviewPayload.Unsupported -> publish(owner, PreviewUiState.Unsupported(item))
                    is PreviewPayload.Failure -> publish(owner, PreviewUiState.Error(item, result.error))
                    else -> {
                        if (owner != generation.get()) {
                            (result as? PreviewPayload.PdfPage)?.session?.invalidate()
                            return@launch
                        }
                        if (result is PreviewPayload.PdfPage) {
                            pdfSession = result.session
                            currentPageIndex = result.pageIndex
                            currentSourceLabel = resolved.sourceLabel
                            if (owner != generation.get()) {
                                if (pdfSession === result.session) pdfSession = null
                                result.session.invalidate()
                                return@launch
                            }
                        }
                        publish(owner, PreviewUiState.Ready(item, resolved.sourceLabel, result))
                    }
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: SecurityException) {
                publish(owner, PreviewUiState.Error(item, PreviewError.PermissionOrGrantMissing))
            } catch (error: IOException) {
                publish(owner, PreviewUiState.Error(item, PreviewError.IoFailure(error.message)))
            } catch (_: OutOfMemoryError) {
                pdfSession?.invalidate()
                pdfSession = null
                publish(owner, PreviewUiState.Error(item, PreviewError.ResourceLimit))
            } catch (_: Exception) {
                publish(owner, PreviewUiState.Error(item, PreviewError.Unknown))
            }
        }
    }

    fun previousPage() = requestRelativePage(-1)
    fun nextPage() = requestRelativePage(1)

    private fun requestRelativePage(delta: Int) {
        val session = pdfSession ?: return
        val item = currentItem ?: return
        val owner = generation.get()
        var target = -1
        var startWorker = false
        synchronized(pageQueueLock) {
            val base = queuedPageIndex ?: activePageIndex ?: currentPageIndex
            target = base + delta
            if (target in 0 until session.pageCount) {
                pageGeneration.incrementAndGet()
                queuedPageIndex = target
                _state.value = PreviewUiState.Loading(item)
                startWorker = pageJob?.isActive != true
            }
        }
        if (target !in 0 until session.pageCount) return
        if (startWorker) pageJob = lifecycleScope.launch(dispatcher) { drainPageRequests(owner, item, session) }
    }

    private suspend fun drainPageRequests(owner: Long, item: PreviewItem, session: PdfDocumentSession) {
        try {
            while (owner == generation.get() && session === pdfSession) {
                val (target, requestedGeneration) = synchronized(pageQueueLock) {
                    val target = queuedPageIndex.also {
                        queuedPageIndex = null
                        activePageIndex = it
                    }
                    target to pageGeneration.get()
                }
                if (target == null) return
                val result = try {
                    session.renderPage(target)
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (failure: PdfRendererFailure) {
                    if (owner == generation.get() && requestedGeneration == pageGeneration.get()) {
                        session.invalidate()
                        pdfSession = null
                        publish(owner, PreviewUiState.Error(item, failure.toPreviewError()))
                    }
                    return
                } catch (_: Exception) {
                    if (owner == generation.get() && requestedGeneration == pageGeneration.get()) {
                        session.invalidate()
                        pdfSession = null
                        publish(owner, PreviewUiState.Error(item, PreviewError.RendererFailure))
                    }
                    return
                }
                val published = synchronized(pageQueueLock) {
                    val stillCurrent = owner == generation.get() && session === pdfSession &&
                        requestedGeneration == pageGeneration.get() && queuedPageIndex == null
                    if (activePageIndex == target) activePageIndex = null
                    if (stillCurrent) {
                        currentPageIndex = target
                        _state.value = PreviewUiState.Ready(
                            item,
                            currentSourceLabel,
                            PreviewPayload.PdfPage(result, target, session.pageCount, session),
                        )
                    }
                    stillCurrent
                }
                if (!published) continue
            }
        } finally {
            var restart = false
            synchronized(pageQueueLock) {
                pageJob = null
                activePageIndex = null
                restart = queuedPageIndex != null && owner == generation.get() && session === pdfSession
                if (restart) pageJob = lifecycleScope.launch(dispatcher) { drainPageRequests(owner, item, session) }
            }
        }
    }

    fun retry() {
        val item = currentItem ?: return
        val resolver = currentResolver ?: return
        openAt(item, resolver, currentPageIndex)
    }

    /** Navigation leaving Preview invalidates ownership before a worker can publish late output. */
    fun close() {
        generation.incrementAndGet()
        pageGeneration.incrementAndGet()
        job?.cancel()
        pageJob?.cancel()
        job = null
        pageJob = null
        synchronized(pageQueueLock) {
            queuedPageIndex = null
            activePageIndex = null
        }
        pdfSession?.invalidate()
        pdfSession = null
        currentItem = null
        currentResolver = null
        currentPageIndex = 0
        currentSourceLabel = ""
        _state.value = PreviewUiState.Idle
    }

    private fun publishSourceFailure(owner: Long, item: PreviewItem, error: StorageError) {
        if (error == StorageError.Unsupported) {
            publish(owner, PreviewUiState.Unsupported(item))
        } else {
            publish(owner, PreviewUiState.Error(item, previewError(error)))
        }
    }

    private fun publish(owner: Long, state: PreviewUiState) {
        if (owner == generation.get()) _state.value = state
    }

    override fun onCleared() {
        close()
        if (ownsScope) lifecycleScope.cancel()
        super.onCleared()
    }
}
