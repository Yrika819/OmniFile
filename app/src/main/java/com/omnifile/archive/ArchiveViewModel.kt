package com.omnifile.archive

import androidx.lifecycle.ViewModel
import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ArchiveUiState {
    data object Idle : ArchiveUiState
    data class Loading(val container: StorageEntry) : ArchiveUiState
    data class Content(
        val document: ArchiveDocument,
        val path: ArchivePath,
        val entries: List<ArchiveNode>,
        val selection: Set<ArchiveEntryRef> = emptySet(),
        val extraction: ArchiveExtractionUiState? = null,
    ) : ArchiveUiState
    data class Empty(
        val document: ArchiveDocument,
        val path: ArchivePath,
        val selection: Set<ArchiveEntryRef> = emptySet(),
        val extraction: ArchiveExtractionUiState? = null,
    ) : ArchiveUiState
    data class Error(
        val container: StorageEntry,
        val path: ArchivePath,
        val error: ArchiveError,
    ) : ArchiveUiState
}

sealed interface ArchiveExtractionUiState {
    data class Running(val progress: ArchiveExtractionProgress) : ArchiveExtractionUiState
    data class Failed(val result: ArchiveExtractionResult.Failure) : ArchiveExtractionUiState
    data class Cancelled(val cleanupComplete: Boolean) : ArchiveExtractionUiState
    data class Completed(val files: Int, val bytes: Long) : ArchiveExtractionUiState
}

class ArchiveViewModel(
    private val repository: ArchiveRepository,
    private val extractor: ArchiveExtractor,
    private val localProvider: LocalStorageProvider,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val ownsScope = scope == null
    private val lifecycleScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow<ArchiveUiState>(ArchiveUiState.Idle)
    private var document: ArchiveDocument? = null
    private var path = ArchivePath.ROOT
    private var selection = linkedSetOf<ArchiveEntryRef>()
    private var loadJob: Job? = null
    private var extractionJob: Job? = null

    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()
    val isExtracting: Boolean get() = extractionJob?.isActive == true
    val isAtRoot: Boolean get() = path.isRoot()
    val isSelectionMode: Boolean get() = selection.isNotEmpty()

    fun open(container: StorageEntry) {
        extractionJob?.cancel()
        loadJob?.cancel()
        document = null
        path = ArchivePath.ROOT
        selection.clear()
        _uiState.value = ArchiveUiState.Loading(container)
        loadJob = lifecycleScope.launch(Dispatchers.IO) {
            when (val result = repository.open(container)) {
                is ArchiveOpenResult.Success -> publish(result.document, ArchivePath.ROOT)
                is ArchiveOpenResult.Failure -> _uiState.value = ArchiveUiState.Error(container, ArchivePath.ROOT, result.error)
            }
        }
    }

    fun openDirectory(entry: ArchiveNode) {
        if (entry.kind != com.omnifile.storage.EntryKind.DIRECTORY || entry.status != ArchiveEntryStatus.SUPPORTED) return
        val current = document ?: return
        if (entry !in current.list(path)) return
        publish(current, entry.path)
    }

    fun toggleSelection(entry: ArchiveNode) {
        val current = _uiState.value as? ArchiveUiState.Content ?: return
        if (current.entries.none { it.ref == entry.ref }) return
        if (!entry.isExtractable) return
        if (!selection.add(entry.ref)) selection.remove(entry.ref)
        publishSelection(current)
    }

    fun enterSelection(entry: ArchiveNode) {
        val current = _uiState.value as? ArchiveUiState.Content ?: return
        if (current.entries.none { it.ref == entry.ref } || !entry.isExtractable) return
        selection.clear()
        selection += entry.ref
        publishSelection(current)
    }

    fun clearSelection() {
        if (selection.isEmpty()) return
        selection.clear()
        val current = _uiState.value as? ArchiveUiState.Content ?: return
        publishSelection(current)
    }

    /** Returns true when the back gesture was consumed inside the archive detail. */
    fun handleBack(): Boolean {
        if (isExtracting) {
            cancelExtraction()
            return true
        }
        if (selection.isNotEmpty()) {
            clearSelection()
            return true
        }
        if (!path.isRoot()) {
            val parent = path.segments().dropLast(1)
            document?.let { publish(it, ArchivePath(parent)) }
            return true
        }
        return false
    }

    fun extractSelected() {
        val current = _uiState.value as? ArchiveUiState.Content ?: return
        if (selection.isEmpty() || isExtracting) return
        val selectedEntries = current.entries.filter { it.ref in selection }
        if (selectedEntries.isEmpty()) return
        extractionJob = lifecycleScope.launch(Dispatchers.IO) {
            val destination = when (val result = localProvider.root()) {
                is com.omnifile.storage.StorageResult.Success -> result.value
                is com.omnifile.storage.StorageResult.Failure -> {
                    publishExtraction(ArchiveExtractionUiState.Failed(
                        ArchiveExtractionResult.Failure(
                            ArchiveExtractionError.Io(result.error.toString()),
                            cleanupComplete = true,
                        ),
                    ))
                    return@launch
                }
            }
            val result = extractor.extract(
                document = current.document,
                selected = selectedEntries,
                destination = localProvider,
                destinationParent = destination,
                onProgress = { progress -> publishExtraction(ArchiveExtractionUiState.Running(progress)) },
            )
            when (result) {
                is ArchiveExtractionResult.Success -> {
                    selection.clear()
                    val completed = ArchiveExtractionUiState.Completed(result.files, result.bytes)
                    _uiState.value = when (val state = _uiState.value) {
                        is ArchiveUiState.Content -> state.copy(selection = emptySet(), extraction = completed)
                        is ArchiveUiState.Empty -> state.copy(selection = emptySet(), extraction = completed)
                        else -> state
                    }
                }
                is ArchiveExtractionResult.Failure -> publishExtraction(ArchiveExtractionUiState.Failed(result))
                is ArchiveExtractionResult.Cancelled -> publishExtraction(ArchiveExtractionUiState.Cancelled(result.cleanupComplete))
            }
        }
    }

    fun cancelExtraction() {
        extractionJob?.cancel()
    }

    fun retry() {
        val state = _uiState.value
        if (state is ArchiveUiState.Error) open(state.container)
    }

    private fun publish(nextDocument: ArchiveDocument, nextPath: ArchivePath) {
        document = nextDocument
        path = nextPath
        selection.clear()
        val entries = nextDocument.list(nextPath)
        _uiState.value = if (entries.isEmpty()) {
            ArchiveUiState.Empty(nextDocument, nextPath)
        } else {
            ArchiveUiState.Content(nextDocument, nextPath, entries)
        }
    }

    private fun publishSelection(current: ArchiveUiState.Content) {
        _uiState.value = current.copy(selection = selection.toSet())
    }

    private fun publishExtraction(extraction: ArchiveExtractionUiState) {
        _uiState.value = when (val current = _uiState.value) {
            is ArchiveUiState.Content -> current.copy(extraction = extraction)
            is ArchiveUiState.Empty -> current.copy(extraction = extraction)
            else -> current
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        extractionJob?.cancel()
        if (ownsScope) lifecycleScope.cancel()
        super.onCleared()
    }
}
