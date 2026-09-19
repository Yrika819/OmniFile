package com.omnifile.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.omnifile.storage.EntryKind
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageProvider
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

sealed interface FilesUiState {
    data object SourceSelection : FilesUiState

    data class Loading(val location: StorageEntry?) : FilesUiState

    data class Content(
        val location: StorageEntry,
        val entries: List<StorageEntry>,
        val breadcrumb: List<String>,
    ) : FilesUiState

    data class Empty(
        val location: StorageEntry,
        val breadcrumb: List<String>,
    ) : FilesUiState

    data class Error(
        val error: StorageError,
        val location: StorageEntry?,
        val breadcrumb: List<String>,
    ) : FilesUiState
}

class FilesViewModel(
    private val repository: FilesRepository,
    private val localProviderId: ProviderId,
    private val safProviderFor: (Uri) -> StorageProvider = { error("SAF provider factory is not configured") },
    private val restoredSafUri: () -> Uri? = { null },
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val ownsScope = scope == null
    private val lifecycleScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow<FilesUiState>(FilesUiState.SourceSelection)
    private val navigation = mutableListOf<StorageEntry>()
    private var selectedProviderId: ProviderId? = null
    private var listingJob: Job? = null
    private var requestToken = 0L

    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    fun selectLocal() {
        selectedProviderId = localProviderId
        navigation.clear()
        loadRoot(localProviderId)
    }

    fun selectSaf(uri: Uri) {
        try {
            val provider = safProviderFor(uri)
            repository.register(provider)
            selectedProviderId = provider.id
            navigation.clear()
            loadRoot(provider.id)
        } catch (_: SecurityException) {
            _uiState.value = FilesUiState.Error(StorageError.PermissionDenied, null, emptyList())
        } catch (_: IllegalArgumentException) {
            _uiState.value = FilesUiState.Error(StorageError.StaleReference, null, emptyList())
        }
    }

    fun restorePersistedSaf() {
        restoredSafUri()?.let(::selectSaf)
    }

    fun openDirectory(entry: StorageEntry) {
        if (entry.kind != EntryKind.DIRECTORY || entry.ref.providerId != selectedProviderId) return
        entry.parentRef?.let { parentRef ->
            val parentIndex = navigation.indexOfFirst { it.ref == parentRef }
            if (parentIndex >= 0) {
                while (navigation.size > parentIndex + 1) navigation.removeAt(navigation.lastIndex)
            }
        }
        navigation += entry
        loadChildren(entry)
    }

    fun goBack() {
        when {
            navigation.size > 1 -> {
                navigation.removeAt(navigation.lastIndex)
                loadChildren(navigation.last())
            }

            navigation.size == 1 -> {
                listingJob?.cancel()
                requestToken += 1
                navigation.clear()
                selectedProviderId = null
                _uiState.value = FilesUiState.SourceSelection
            }
        }
    }

    fun retry() {
        when {
            navigation.isNotEmpty() -> loadChildren(navigation.last())
            selectedProviderId != null -> loadRoot(selectedProviderId!!)
        }
    }

    override fun onCleared() {
        listingJob?.cancel()
        if (ownsScope) lifecycleScope.cancel()
        super.onCleared()
    }

    private fun loadRoot(providerId: ProviderId) {
        val token = beginLoading(null)
        listingJob = lifecycleScope.launch(Dispatchers.IO) {
            when (val result = repository.root(providerId)) {
                is StorageResult.Failure -> publish(token) {
                    FilesUiState.Error(result.error, null, emptyList())
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
                FilesUiState.Error(result.error, location, breadcrumb())
            }

            is StorageResult.Success -> publish(token) {
                val breadcrumb = breadcrumb()
                if (result.value.isEmpty()) {
                    FilesUiState.Empty(location, breadcrumb)
                } else {
                    FilesUiState.Content(location, result.value, breadcrumb)
                }
            }
        }
    }

    private fun beginLoading(location: StorageEntry?): Long {
        listingJob?.cancel()
        requestToken += 1
        _uiState.value = FilesUiState.Loading(location)
        return requestToken
    }

    private suspend fun publish(token: Long, state: () -> FilesUiState) {
        if (token == requestToken) _uiState.value = state()
    }

    private fun breadcrumb(): List<String> = navigation.map { it.displayName }
}
