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
    private val resolveRoots: suspend (SearchScope) -> SearchRootResolution = { scope ->
        when (scope) {
            SearchScope.ThisDevice -> SearchRootResolution(emptyList(), listOf(SearchRootFailure("unsupported", "This device", StorageError.Unsupported)))
            is SearchScope.CurrentFolder -> SearchRootResolution(listOf(scope.directory))
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
    val query: String get() = currentQuery
    val scope: SearchScope? get() = activeScope

    fun setScope(scope: SearchScope) {
        if (activeScope == scope) return
        cancelActive()
        activeScope = scope
        currentQuery = ""
        _state.value = SearchUiState.Idle(scope)
    }

    fun restoreRequest(scope: SearchScope, query: String) {
        if (activeScope != scope) setScope(scope)
        if (currentQuery == query && query.isNotBlank() && _state.value !is SearchUiState.Idle) return
        if (query.isNotBlank()) queryChanged(query)
    }

    fun queryChanged(rawQuery: String) {
        currentQuery = rawQuery
        generation += 1
        debounceJob?.cancel()
        searchJob?.cancel()
        val scope = activeScope ?: return
        if (SearchQuery.normalize(rawQuery).isEmpty()) {
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
        if (SearchQuery.normalize(currentQuery).isEmpty()) {
            queryChanged(currentQuery)
            return
        }
        generation += 1
        debounceJob?.cancel()
        searchJob?.cancel()
        _state.value = SearchUiState.Searching(currentQuery, scope)
        startSearch(generation, currentQuery, scope)
    }

    fun clearQuery() = queryChanged("")

    fun stop() {
        cancelActive()
        activeScope = null
        currentQuery = ""
        _state.value = SearchUiState.Idle(null)
    }

    private fun startSearch(requestGeneration: Long, rawQuery: String, scope: SearchScope) {
        if (requestGeneration != generation || scope != activeScope) return
        searchJob = lifecycleScope.launch(Dispatchers.IO) {
            var rootFailures = emptyList<SearchRootFailure>()
            var resolvedRootCount = 0
            var rootLabels = emptyMap<String, String>()
            engine.search(
                request = SearchRequest(scope, rawQuery),
                roots = { requestedScope ->
                    val resolution = resolveRoots(requestedScope)
                    rootFailures = resolution.failures
                    resolvedRootCount = resolution.roots.size
                    rootLabels = resolution.rootLabels
                    StorageResult.Success(resolution.roots)
                },
                children = listChildren,
            ).collect { emission ->
                if (requestGeneration != generation || scope != activeScope) return@collect
                when (emission) {
                    is SearchEmission.Batch -> publishBatch(emission, rootFailures, rootLabels)
                    is SearchEmission.Completed -> publishCompleted(emission, rawQuery, scope, rootFailures, resolvedRootCount, rootLabels)
                }
            }
        }
    }

    private fun publishBatch(
        batch: SearchEmission.Batch,
        rootFailures: List<SearchRootFailure>,
        rootLabels: Map<String, String>,
    ) {
        val current = _state.value as? SearchUiState.Searching ?: return
        _state.value = current.copy(
            hits = current.hits + labelHits(batch.hits, rootLabels),
            failures = current.failures + batch.failures,
            rootFailures = rootFailures,
            entriesVisited = batch.entriesVisited,
            directoriesVisited = batch.directoriesVisited,
        )
    }

    private fun publishCompleted(
        completed: SearchEmission.Completed,
        rawQuery: String,
        scope: SearchScope,
        rootFailures: List<SearchRootFailure>,
        resolvedRootCount: Int,
        rootLabels: Map<String, String>,
    ) {
        if (completed.rootError != null || (scope is SearchScope.ThisDevice && completed.hits.isEmpty() && rootFailures.isNotEmpty() && resolvedRootCount == 0)) {
            _state.value = SearchUiState.Error(
                query = rawQuery,
                scope = scope,
                rootError = completed.rootError ?: rootFailures.first().error,
                hits = labelHits(completed.hits, rootLabels),
                failures = completed.failures,
                rootFailures = rootFailures,
            )
            return
        }
        _state.value = SearchUiState.Results(
            query = rawQuery,
            scope = scope,
            hits = labelHits(completed.hits, rootLabels),
            failures = completed.failures,
            entriesVisited = completed.entriesVisited,
            directoriesVisited = completed.directoriesVisited,
            complete = completed.complete && rootFailures.isEmpty(),
            truncated = completed.truncated,
            rootFailures = rootFailures,
        )
    }


    private fun labelHits(hits: List<SearchHit>, rootLabels: Map<String, String>): List<SearchHit> =
        hits.map { hit ->
            hit.copy(rootLabel = hit.rootLabel ?: hit.ancestors.firstOrNull()?.ref?.identityKey?.let(rootLabels::get))
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
