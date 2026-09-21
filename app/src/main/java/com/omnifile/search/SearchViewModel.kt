package com.omnifile.search

import androidx.lifecycle.ViewModel
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SearchViewModel(
    private val listChildren: suspend (StorageEntry) -> StorageResult<List<StorageEntry>>,
    private val rootsForScope: suspend (SearchScope) -> StorageResult<List<StorageEntry>> = { scope ->
        when (scope) {
            SearchScope.ThisDevice -> StorageResult.Failure(StorageError.Unsupported)
            is SearchScope.CurrentFolder -> StorageResult.Success(listOf(scope.directory))
        }
    },
    private val engine: SearchEngine = SearchEngine(),
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val ownsScope = scope == null
    private val lifecycleScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle())
    private var activeScope: SearchScope? = null
    private var currentQuery = ""
    private var generation = 0L
    private var debounceJob: Job? = null
    private var searchJob: Job? = null

    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun setScope(scope: SearchScope) {
        if (activeScope == scope) return
        cancelActive()
        activeScope = scope
        currentQuery = ""
        _state.value = SearchUiState.Idle(scope)
    }

    fun queryChanged(rawQuery: String) {
        currentQuery = rawQuery
        generation += 1
        debounceJob?.cancel()
        searchJob?.cancel()
        val scope = activeScope ?: return
        val normalized = SearchQuery.normalize(rawQuery)
        if (normalized.isEmpty()) {
            _state.value = SearchUiState.Idle(activeScope)
            return
        }
        _state.value = SearchUiState.Searching(rawQuery, scope)
        val requestGeneration = generation
        debounceJob = lifecycleScope.launch {
            delay(150)
            startSearch(requestGeneration, rawQuery, scope)
        }
    }

    fun submitQuery() {
        val scope = activeScope ?: return
        val normalized = SearchQuery.normalize(currentQuery)
        if (normalized.isEmpty()) {
            queryChanged(currentQuery)
            return
        }
        generation += 1
        debounceJob?.cancel()
        searchJob?.cancel()
        _state.value = SearchUiState.Searching(currentQuery, scope)
        startSearch(generation, currentQuery, scope)
    }

    fun clearQuery() {
        queryChanged("")
    }

    fun stop() {
        cancelActive()
        activeScope = null
        currentQuery = ""
        _state.value = SearchUiState.Idle(null)
    }

    private fun startSearch(requestGeneration: Long, rawQuery: String, scope: SearchScope) {
        if (requestGeneration != generation || scope != activeScope) return
        searchJob = lifecycleScope.launch(Dispatchers.IO) {
            engine.search(
                request = SearchRequest(scope, rawQuery),
                roots = rootsForScope,
                children = listChildren,
            ).collect { emission ->
                if (requestGeneration != generation || scope != activeScope) return@collect
                when (emission) {
                    is SearchEmission.Batch -> publishBatch(emission)
                    is SearchEmission.Completed -> publishCompleted(emission, rawQuery, scope)
                }
            }
        }
    }

    private fun publishBatch(batch: SearchEmission.Batch) {
        val current = _state.value as? SearchUiState.Searching ?: return
        _state.value = current.copy(
            hits = current.hits + batch.hits,
            failures = current.failures + batch.failures,
            entriesVisited = batch.entriesVisited,
            directoriesVisited = batch.directoriesVisited,
        )
    }

    private fun publishCompleted(
        completed: SearchEmission.Completed,
        rawQuery: String,
        scope: SearchScope,
    ) {
        if (completed.rootError != null) {
            _state.value = SearchUiState.Error(
                query = rawQuery,
                scope = scope,
                rootError = completed.rootError,
                hits = completed.hits,
                failures = completed.failures,
            )
            return
        }
        _state.value = SearchUiState.Results(
            query = rawQuery,
            scope = scope,
            hits = completed.hits,
            failures = completed.failures,
            entriesVisited = completed.entriesVisited,
            directoriesVisited = completed.directoriesVisited,
            complete = completed.complete,
            truncated = completed.truncated,
        )
    }

    private fun cancelActive() {
        generation += 1
        debounceJob?.cancel()
        searchJob?.cancel()
        debounceJob = null
        searchJob = null
    }

    override fun onCleared() {
        cancelActive()
        if (ownsScope) lifecycleScope.cancel()
        super.onCleared()
    }
}
