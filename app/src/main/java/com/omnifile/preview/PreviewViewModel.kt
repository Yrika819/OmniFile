package com.omnifile.preview

import androidx.lifecycle.ViewModel
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import java.io.IOException
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

/** Owns one request at a time; generation ownership protects even non-cooperative providers. */
class PreviewViewModel(
    private val engine: PreviewEngine = PreviewEngine(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val ownsScope = scope == null
    private val lifecycleScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<PreviewUiState>(PreviewUiState.Idle)
    private var generation = 0L
    private var job: Job? = null
    private var currentItem: PreviewItem? = null
    private var currentResolver: (suspend () -> StorageResult<PreviewSource>)? = null

    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    fun open(item: PreviewItem, resolveSource: suspend () -> StorageResult<PreviewSource>) {
        generation += 1
        val owner = generation
        job?.cancel()
        currentItem = item
        currentResolver = resolveSource
        _state.value = PreviewUiState.Loading(item)
        job = lifecycleScope.launch(dispatcher) {
            val source = try {
                when (val result = resolveSource()) {
                    is StorageResult.Success -> result.value
                    is StorageResult.Failure -> {
                        publishSourceFailure(owner, item, result.error)
                        return@launch
                    }
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: SecurityException) {
                publish(owner, PreviewUiState.Error(item, PreviewError.PermissionOrGrantMissing))
                return@launch
            } catch (error: IOException) {
                publish(owner, PreviewUiState.Error(item, PreviewError.IoFailure(error.message)))
                return@launch
            } catch (_: Exception) {
                publish(owner, PreviewUiState.Error(item, PreviewError.Unknown))
                return@launch
            }
            if (owner != generation) return@launch
            val request = PreviewRequest(item, source)
            try {
                when (val result = engine.load(request)) {
                    PreviewPayload.Unsupported -> publish(owner, PreviewUiState.Unsupported(item))
                    is PreviewPayload.Failure -> publish(owner, PreviewUiState.Error(item, result.error))
                    else -> publish(owner, PreviewUiState.Ready(item, source.sourceLabel, result))
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: SecurityException) {
                publish(owner, PreviewUiState.Error(item, PreviewError.PermissionOrGrantMissing))
            } catch (error: IOException) {
                publish(owner, PreviewUiState.Error(item, PreviewError.IoFailure(error.message)))
            } catch (_: OutOfMemoryError) {
                publish(owner, PreviewUiState.Error(item, PreviewError.ResourceLimit))
            } catch (_: Exception) {
                publish(owner, PreviewUiState.Error(item, PreviewError.Unknown))
            }
        }
    }

    fun retry() {
        val item = currentItem ?: return
        val resolver = currentResolver ?: return
        open(item, resolver)
    }

    /** Called whenever navigation leaves Preview so handles, jobs, and decoded bitmaps are released. */
    fun close() {
        generation += 1
        job?.cancel()
        job = null
        currentItem = null
        currentResolver = null
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
        if (owner == generation) _state.value = state
    }

    override fun onCleared() {
        close()
        if (ownsScope) lifecycleScope.cancel()
        super.onCleared()
    }
}
