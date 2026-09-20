package com.omnifile.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.omnifile.storage.EntryKind
import com.omnifile.storage.EntryRef
import com.omnifile.storage.DeleteItemOutcome
import com.omnifile.storage.DeleteItemResult
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageProvider
import com.omnifile.storage.StorageTransferProvider
import com.omnifile.operations.OperationManager
import com.omnifile.operations.OperationType
import com.omnifile.storage.StorageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class FilesSelectionState(
    val providerId: ProviderId,
    val locationRef: EntryRef,
    val selectedEntries: Set<EntryRef>,
)

sealed interface FilesUiState {
    data object SourceSelection : FilesUiState

    data class Loading(val location: StorageEntry?) : FilesUiState

    data class DestinationPicker(
        val location: StorageEntry,
        val entries: List<StorageEntry>,
        val breadcrumb: List<String>,
        val operationType: OperationType,
        val sourceCount: Int,
    ) : FilesUiState

    data class Content(
        val location: StorageEntry,
        val entries: List<StorageEntry>,
        val breadcrumb: List<String>,
        val selection: FilesSelectionState? = null,
    ) : FilesUiState

    data class Empty(
        val location: StorageEntry,
        val breadcrumb: List<String>,
        val selection: FilesSelectionState? = null,
    ) : FilesUiState

    data class Error(
        val error: StorageError,
        val location: StorageEntry?,
        val breadcrumb: List<String>,
        val selection: FilesSelectionState? = null,
    ) : FilesUiState
}

class FilesViewModel(
    private val repository: FilesRepository,
    private val localProviderId: ProviderId,
    private val safProviderFor: (Uri) -> StorageProvider = { error("SAF provider factory is not configured") },
    private val restoredSafUri: () -> Uri? = { null },
    private val operationManager: OperationManager? = null,
    private val onOperationsCreated: (List<String>) -> Unit = {},
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val ownsScope = scope == null
    private val lifecycleScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow<FilesUiState>(FilesUiState.SourceSelection)
    private val navigation = mutableListOf<StorageEntry>()
    private var selectedProviderId: ProviderId? = null
    private var selection: FilesSelectionState? = null
    private var listingJob: Job? = null
    private var mutationJob: Job? = null
    private var operationJob: Job? = null
    private var destinationJob: Job? = null
    private var destinationNavigation = mutableListOf<StorageEntry>()
    private var destinationProviderId: ProviderId? = null
    private var pendingTransfer: PendingTransfer? = null
    private var requestToken = 0L
    private val nextMutationOwner = AtomicLong(0L)
    private val activeMutationOwner = AtomicLong(0L)
    private val _mutationInFlight = MutableStateFlow(false)

    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()
    val mutationInFlight: StateFlow<Boolean> = _mutationInFlight.asStateFlow()
    val selectedEntries: Set<EntryRef>
        get() = selection?.selectedEntries.orEmpty()
    val isSelectionMode: Boolean
        get() = selectedEntries.isNotEmpty()
    val isMutationInFlight: Boolean
        get() = _mutationInFlight.value
    val isDestinationPicker: Boolean
        get() = pendingTransfer != null

    fun selectLocal() {
        clearSelection()
        selectedProviderId = localProviderId
        navigation.clear()
        loadRoot(localProviderId)
    }

    fun selectSaf(uri: Uri) {
        clearSelection()
        try {
            val provider = safProviderFor(uri)
            repository.register(provider)
            (provider as? StorageTransferProvider)?.let { operationManager?.registerProvider(it) }
            selectedProviderId = provider.id
            navigation.clear()
            loadRoot(provider.id)
        } catch (_: SecurityException) {
            invalidateActiveWork()
            selectedProviderId = null
            selection = null
            navigation.clear()
            _uiState.value = FilesUiState.Error(StorageError.PermissionDenied, null, emptyList())
        } catch (_: IllegalArgumentException) {
            invalidateActiveWork()
            selectedProviderId = null
            selection = null
            navigation.clear()
            _uiState.value = FilesUiState.Error(StorageError.StaleReference, null, emptyList())
        }
    }

    fun restorePersistedSaf() {
        restoredSafUri()?.let(::selectSaf)
    }

    fun selectDestinationLocal() {
        if (pendingTransfer == null) return
        destinationProviderId = localProviderId
        destinationNavigation.clear()
        loadDestinationRoot(localProviderId)
    }

    fun selectDestinationSaf(uri: Uri) {
        if (pendingTransfer == null) return
        try {
            val provider = safProviderFor(uri)
            repository.register(provider)
            (provider as? StorageTransferProvider)?.let { operationManager?.registerProvider(it) }
            destinationProviderId = provider.id
            destinationNavigation.clear()
            loadDestinationRoot(provider.id)
        } catch (_: SecurityException) {
            _uiState.value = FilesUiState.Error(StorageError.PermissionDenied, null, emptyList())
        } catch (_: IllegalArgumentException) {
            _uiState.value = FilesUiState.Error(StorageError.StaleReference, null, emptyList())
        }
    }

    fun openDirectory(entry: StorageEntry) {
        if (entry.kind != EntryKind.DIRECTORY || entry.ref.providerId != selectedProviderId) return
        clearSelection()
        entry.parentRef?.let { parentRef ->
            val parentIndex = navigation.indexOfFirst { it.ref == parentRef }
            if (parentIndex >= 0) {
                while (navigation.size > parentIndex + 1) navigation.removeAt(navigation.lastIndex)
            }
        }
        navigation += entry
        loadChildren(entry)
    }

    fun enterSelection(entry: StorageEntry) {
        val content = _uiState.value as? FilesUiState.Content ?: return
        if (content.entries.none { it.ref == entry.ref }) return
        val locationRef = content.location.ref
        if (entry.ref.providerId != locationRef.providerId || entry.ref.providerId != selectedProviderId) return
        selection = FilesSelectionState(entry.ref.providerId, locationRef, setOf(entry.ref))
        publishSelection()
    }

    fun toggleSelection(entry: StorageEntry) {
        val content = _uiState.value as? FilesUiState.Content ?: return
        val current = selection ?: return
        if (current.providerId != selectedProviderId || current.locationRef != content.location.ref) return
        if (entry.ref.providerId != current.providerId || content.entries.none { it.ref == entry.ref }) return

        val selected = if (entry.ref in current.selectedEntries) {
            current.selectedEntries - entry.ref
        } else {
            current.selectedEntries + entry.ref
        }
        selection = current.copy(selectedEntries = selected).takeIf { selected.isNotEmpty() }
        publishSelection()
    }

    fun clearSelection() {
        if (selection == null) return
        selection = null
        publishSelection()
    }

    fun handleBack() {
        if (pendingTransfer != null) {
            destinationBack()
        } else if (isSelectionMode) {
            clearSelection()
        } else {
            goBack()
        }
    }

    fun goBack() {
        when {
            navigation.size > 1 -> {
                clearSelection()
                navigation.removeAt(navigation.lastIndex)
                loadChildren(navigation.last())
            }

            navigation.size == 1 -> {
                clearSelection()
                listingJob?.cancel()
                invalidateMutation()
                requestToken += 1
                navigation.clear()
                selectedProviderId = null
                _uiState.value = FilesUiState.SourceSelection
            }
        }
    }

    fun retry() {
        if (pendingTransfer != null) {
            if (destinationNavigation.isNotEmpty()) {
                loadDestinationDirectory(destinationNavigation.last())
            } else {
                destinationProviderId?.let(::loadDestinationRoot)
            }
            return
        }
        when {
            navigation.isNotEmpty() -> loadChildren(navigation.last())
            selectedProviderId != null -> loadRoot(selectedProviderId!!)
        }
    }

    fun renameSelected(requestedName: String) {
        val content = _uiState.value as? FilesUiState.Content ?: return
        val current = selectionFor(content.location) ?: return
        if (current.selectedEntries.size != 1 || mutationJob?.isActive == true) return
        val entry = content.entries.singleOrNull { it.ref in current.selectedEntries } ?: return
        val mutation = beginMutation()
        mutationJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = repository.rename(entry, requestedName)
                refreshAfterMutation(
                    token = mutation.requestToken,
                    location = content.location,
                    retainedRefs = current.selectedEntries,
                    mutationError = (result as? StorageResult.Failure)?.error,
                    clearOnSuccess = true,
                )
            } finally {
                finishMutation(mutation.ownerToken)
            }
        }
    }

    fun copySelectedToCurrentDirectory() = beginDestinationPicker(OperationType.COPY)

    fun moveSelectedToCurrentDirectory() = beginDestinationPicker(OperationType.MOVE)

    private fun beginDestinationPicker(type: OperationType) {
        val content = _uiState.value as? FilesUiState.Content ?: return
        val current = selectionFor(content.location) ?: return
        val entries = content.entries.filter { it.ref in current.selectedEntries }
        if (entries.isEmpty() || entries.any { it.kind != EntryKind.FILE }) return
        pendingTransfer = PendingTransfer(type, content.location, entries)
        destinationNavigation = navigation.toMutableList()
        _uiState.value = FilesUiState.DestinationPicker(
            location = content.location,
            entries = content.entries,
            breadcrumb = content.breadcrumb,
            operationType = type,
            sourceCount = entries.size,
        )
    }

    fun openDestinationDirectory(entry: StorageEntry) {
        val pending = pendingTransfer ?: return
        if (entry.kind != EntryKind.DIRECTORY) return
        destinationNavigation += entry
        loadDestinationDirectory(entry)
    }

    private fun loadDestinationRoot(providerId: ProviderId) {
        val pending = pendingTransfer ?: return
        destinationProviderId = providerId
        val token = ++requestToken
        destinationJob?.cancel()
        destinationJob = lifecycleScope.launch(Dispatchers.IO) {
            when (val rootResult = repository.root(providerId)) {
                is StorageResult.Success -> {
                    if (token != requestToken || pendingTransfer == null) return@launch
                    destinationNavigation.clear()
                    destinationNavigation += rootResult.value
                    when (val childrenResult = repository.children(rootResult.value)) {
                        is StorageResult.Success -> if (token == requestToken) {
                            _uiState.value = FilesUiState.DestinationPicker(
                                rootResult.value,
                                childrenResult.value,
                                destinationNavigation.map { it.displayName },
                                pending.type,
                                pending.entries.size,
                            )
                        }
                        is StorageResult.Failure -> if (token == requestToken) {
                            _uiState.value = FilesUiState.Error(
                                childrenResult.error,
                                rootResult.value,
                                destinationNavigation.map { it.displayName },
                            )
                        }
                    }
                }
                is StorageResult.Failure -> if (token == requestToken) {
                    _uiState.value = FilesUiState.Error(rootResult.error, null, emptyList())
                }
            }
        }
    }

    private fun loadDestinationDirectory(entry: StorageEntry) {
        val pending = pendingTransfer ?: return
        val token = ++requestToken
        destinationJob?.cancel()
        destinationJob = lifecycleScope.launch(Dispatchers.IO) {
            when (val result = repository.children(entry)) {
                is StorageResult.Success -> if (token == requestToken) {
                    _uiState.value = FilesUiState.DestinationPicker(
                        entry, result.value, destinationNavigation.map { it.displayName },
                        pending.type, pending.entries.size,
                    )
                }
                is StorageResult.Failure -> if (token == requestToken) {
                    _uiState.value = FilesUiState.Error(result.error, entry, destinationNavigation.map { it.displayName })
                }
            }
        }
    }

    fun confirmDestination() {
        val pending = pendingTransfer ?: return
        val destination = destinationNavigation.lastOrNull() ?: return
        val destinationProvider = repository.transferProvider(destination.ref.providerId) ?: return
        operationJob?.cancel()
        operationJob = lifecycleScope.launch(Dispatchers.IO) {
            val destinationLocator = when (val result = destinationProvider.encodeDurableLocator(destination.ref)) {
                is StorageResult.Success -> result.value
                is StorageResult.Failure -> {
                    _uiState.value = FilesUiState.Error(result.error, destination, destinationNavigation.map { it.displayName })
                    return@launch
                }
            }
            val items = mutableListOf<OperationManager.EnqueueItem>()
            for (entry in pending.entries) {
                val sourceProvider = repository.transferProvider(entry.ref.providerId)
                val source = sourceProvider?.let { provider ->
                    when (val result = provider.encodeDurableLocator(entry.ref)) {
                        is StorageResult.Success -> result.value
                        is StorageResult.Failure -> null
                    }
                }
                if (source == null) {
                    _uiState.value = FilesUiState.Error(StorageError.Unsupported, destination, destinationNavigation.map { it.displayName })
                    return@launch
                }
                items += OperationManager.EnqueueItem(source, destinationLocator, entry.displayName, entry.sizeBytes, null)
            }
            when (val result = operationManager?.enqueue(pending.type, items)) {
                is StorageResult.Success -> {
                    pendingTransfer = null
                    destinationNavigation.clear()
                    clearSelection()
                    result.value.forEach { operationManager?.execute(it) }
                    onOperationsCreated(result.value)
                    loadChildren(pending.sourceLocation)
                }
                is StorageResult.Failure -> {
                    _uiState.value = FilesUiState.Error(result.error, destination, destinationNavigation.map { it.displayName })
                }
                null -> Unit
            }
        }
    }

    fun cancelDestinationPicker() {
        val pending = pendingTransfer ?: return
        pendingTransfer = null
        destinationProviderId = null
        destinationNavigation.clear()
        loadChildren(pending.sourceLocation)
    }

    fun destinationBack() {
        if (destinationNavigation.size > 1) {
            destinationNavigation.removeAt(destinationNavigation.lastIndex)
            loadDestinationDirectory(destinationNavigation.last())
        } else {
            cancelDestinationPicker()
        }
    }

    fun deleteSelected() {
        val content = _uiState.value as? FilesUiState.Content ?: return
        val current = selectionFor(content.location) ?: return
        if (current.selectedEntries.isEmpty() || mutationJob?.isActive == true) return
        val entries = content.entries.filter { it.ref in current.selectedEntries }
        if (entries.isEmpty()) return
        val mutation = beginMutation()
        mutationJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outcomes = entries.map { entry ->
                    when (val result = repository.delete(entry)) {
                        is StorageResult.Success -> DeleteItemResult(entry, DeleteItemOutcome.Deleted)
                        is StorageResult.Failure -> DeleteItemResult(entry, DeleteItemOutcome.Failed(result.error))
                    }
                }
                val failures = outcomes.filter { it.outcome is DeleteItemOutcome.Failed }
                refreshAfterMutation(
                    token = mutation.requestToken,
                    location = content.location,
                    retainedRefs = failures.mapTo(linkedSetOf()) { it.entry.ref },
                    mutationError = failures.takeIf { it.isNotEmpty() }?.let { StorageError.PartialDelete(outcomes) },
                    clearOnSuccess = failures.isEmpty(),
                )
            } finally {
                finishMutation(mutation.ownerToken)
            }
        }
    }

    private fun invalidateActiveWork() {
        listingJob?.cancel()
        invalidateMutation()
        requestToken += 1
    }

    override fun onCleared() {
        listingJob?.cancel()
        mutationJob?.cancel()
        operationJob?.cancel()
        if (ownsScope) lifecycleScope.cancel()
        super.onCleared()
    }

    private fun loadRoot(providerId: ProviderId) {
        val token = beginLoading(null)
        listingJob = lifecycleScope.launch(Dispatchers.IO) {
            when (val result = repository.root(providerId)) {
                is StorageResult.Failure -> publish(token) {
                    selection = null
                    FilesUiState.Error(result.error, null, emptyList(), selection)
                }

                is StorageResult.Success -> {
                    navigation.clear()
                    navigation += result.value
                    publishChildren(token, result.value)
                }
            }
        }
    }

    private fun loadChildren(location: StorageEntry) {
        val token = beginLoading(location)
        listingJob = lifecycleScope.launch(Dispatchers.IO) {
            publishChildren(token, location)
        }
    }

    private suspend fun publishChildren(token: Long, location: StorageEntry) {
        when (val result = repository.children(location)) {
            is StorageResult.Failure -> publish(token) {
                FilesUiState.Error(result.error, location, breadcrumb(), selectionFor(location))
            }

            is StorageResult.Success -> publish(token) {
                selection = reconciledSelection(location, result.value)
                val breadcrumb = breadcrumb()
                if (result.value.isEmpty()) {
                    FilesUiState.Empty(location, breadcrumb, selection)
                } else {
                    FilesUiState.Content(location, result.value, breadcrumb, selection)
                }
            }
        }
    }

    private fun beginLoading(location: StorageEntry?): Long {
        listingJob?.cancel()
        invalidateMutation()
        requestToken += 1
        _uiState.value = FilesUiState.Loading(location)
        return requestToken
    }

    private fun beginMutation(): MutationHandle {
        listingJob?.cancel()
        requestToken += 1
        val ownerToken = nextMutationOwner.incrementAndGet()
        activeMutationOwner.set(ownerToken)
        _mutationInFlight.value = true
        return MutationHandle(requestToken, ownerToken)
    }

    private fun invalidateMutation() {
        mutationJob?.cancel()
        mutationJob = null
        activeMutationOwner.set(0L)
        _mutationInFlight.value = false
    }

    private fun finishMutation(ownerToken: Long) {
        if (activeMutationOwner.compareAndSet(ownerToken, 0L)) {
            _mutationInFlight.value = false
        }
    }

    private suspend fun refreshAfterMutation(
        token: Long,
        location: StorageEntry,
        retainedRefs: Set<EntryRef>,
        mutationError: StorageError?,
        clearOnSuccess: Boolean,
    ) {
        when (val result = repository.children(location)) {
            is StorageResult.Failure -> publish(token) {
                FilesUiState.Error(result.error, location, breadcrumb(), selectionFor(location))
            }

            is StorageResult.Success -> publish(token) {
                val listedRefs = result.value.mapTo(mutableSetOf()) { it.ref }
                val retained = if (clearOnSuccess && mutationError == null) {
                    emptySet()
                } else {
                    retainedRefs.filterTo(linkedSetOf()) { it in listedRefs }
                }
                selection = retained.takeIf { it.isNotEmpty() }?.let {
                    FilesSelectionState(location.ref.providerId, location.ref, it)
                }
                val refreshed = contentState(location, result.value)
                if (mutationError != null) {
                    FilesUiState.Error(mutationError, location, breadcrumb(), selectionFor(location))
                } else {
                    refreshed
                }
            }
        }
    }

    private fun contentState(location: StorageEntry, entries: List<StorageEntry>): FilesUiState =
        if (entries.isEmpty()) {
            FilesUiState.Empty(location, breadcrumb(), selectionFor(location))
        } else {
            FilesUiState.Content(location, entries, breadcrumb(), selectionFor(location))
        }

    private suspend fun publish(token: Long, state: () -> FilesUiState) {
        if (token == requestToken) _uiState.value = state()
    }

    private fun publishSelection() {
        _uiState.value = when (val state = _uiState.value) {
            is FilesUiState.Content -> state.copy(selection = selectionFor(state.location))
            is FilesUiState.Empty -> state.copy(selection = selectionFor(state.location))
            is FilesUiState.Error -> state.copy(selection = state.location?.let(::selectionFor))
            else -> state
        }
    }

    private fun selectionFor(location: StorageEntry): FilesSelectionState? =
        selection?.takeIf {
            it.providerId == location.ref.providerId &&
                it.locationRef == location.ref &&
                it.selectedEntries.isNotEmpty()
        }

    private fun reconciledSelection(
        location: StorageEntry,
        entries: List<StorageEntry>,
    ): FilesSelectionState? {
        val current = selectionFor(location) ?: return null
        val listedRefs = entries.mapTo(mutableSetOf()) { it.ref }
        val retained = current.selectedEntries.filterTo(linkedSetOf()) { it in listedRefs }
        return current.copy(selectedEntries = retained).takeIf { retained.isNotEmpty() }
    }

    private fun breadcrumb(): List<String> = navigation.map { it.displayName }

    private data class PendingTransfer(
        val type: OperationType,
        val sourceLocation: StorageEntry,
        val entries: List<StorageEntry>,
    )

    private data class MutationHandle(
        val requestToken: Long,
        val ownerToken: Long,
    )
}
