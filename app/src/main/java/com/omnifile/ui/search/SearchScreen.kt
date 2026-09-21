package com.omnifile.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.omnifile.search.SearchHit
import com.omnifile.search.SearchScope
import com.omnifile.search.SearchSubtreeFailure
import com.omnifile.search.SearchRootFailure
import com.omnifile.search.SearchUiState
import com.omnifile.storage.EntryKind
import com.omnifile.storage.StorageError
import java.text.DateFormat
import java.util.Date

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SearchScreen(
    state: SearchUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSubmitQuery: () -> Unit,
    onClearQuery: () -> Unit,
    onOpenResult: (SearchHit) -> Unit,
) {
    val query = when (state) {
        is SearchUiState.Idle -> ""
        is SearchUiState.Searching -> state.query
        is SearchUiState.Results -> state.query
        is SearchUiState.Error -> state.query
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(modifier = Modifier.testTag("search.back"), onClick = onBack) { Text("‹") }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = onQueryChanged,
                        modifier = Modifier.fillMaxWidth().testTag("search.query"),
                        singleLine = true,
                        placeholder = { Text("Search") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmitQuery() }),
                    )
                },
                actions = {
                    if (query.isNotEmpty()) {
                        TextButton(modifier = Modifier.testTag("search.clear"), onClick = onClearQuery) {
                            Text("Clear")
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScopeHeader(state)
            when (state) {
                is SearchUiState.Idle -> IdleState()
                is SearchUiState.Searching -> SearchResults(
                    hits = state.hits,
                    failures = state.failures,
                    rootFailures = state.rootFailures,
                    searching = true,
                    onOpenResult = onOpenResult,
                )
                is SearchUiState.Results -> SearchResults(
                    hits = state.hits,
                    failures = state.failures,
                    rootFailures = state.rootFailures,
                    searching = false,
                    truncated = state.truncated,
                    onOpenResult = onOpenResult,
                )
                is SearchUiState.Error -> RootErrorState(state.rootError, state.hits, state.rootFailures, onOpenResult)
            }
        }
    }
}

@Composable
private fun ScopeHeader(state: SearchUiState) {
    val scope = when (state) {
        is SearchUiState.Idle -> state.scope
        is SearchUiState.Searching -> state.scope
        is SearchUiState.Results -> state.scope
        is SearchUiState.Error -> state.scope
    } ?: return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = true,
            enabled = false,
            onClick = {},
            modifier = Modifier.testTag("search.scope.current"),
            label = { Text(scopeLabel(scope)) },
        )
        if (scope is SearchScope.CurrentFolder) {
            Text("Recursive", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun IdleState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Search files and folders", style = MaterialTheme.typography.titleLarge)
        Text("Search matches names only within the selected scope.")
    }
}

@Composable
private fun SearchResults(
    hits: List<SearchHit>,
    failures: List<SearchSubtreeFailure>,
    rootFailures: List<SearchRootFailure>,
    searching: Boolean,
    truncated: Boolean = false,
    onOpenResult: (SearchHit) -> Unit,
) {
    if (rootFailures.isNotEmpty()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).testTag("search.partial.roots")) {
            Text("Some storage sources could not be searched; results are incomplete.")
            rootFailures.forEach { failure ->
                Text("${failure.label}: ${searchErrorMessage(failure.error)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (searching) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp).testTag("search.progress"))
            Text("Searching…")
        }
    }
    if (!searching && hits.isEmpty() && failures.isEmpty()) {
        Text("No matches found.", modifier = Modifier.padding(24.dp).testTag("search.empty"))
        return
    }
    if (hits.isEmpty() && failures.isNotEmpty()) {
        Text(
            "No complete result: some folders could not be searched.",
            modifier = Modifier.padding(horizontal = 16.dp).testTag("search.partial.empty"),
        )
    } else if (failures.isNotEmpty()) {
        Text(
            "Some folders could not be searched (${failures.size}).",
            modifier = Modifier.padding(horizontal = 16.dp).testTag("search.partial"),
        )
    }
    if (truncated) {
        Text(
            "Search stopped at its safety limit; results may be incomplete.",
            modifier = Modifier.padding(horizontal = 16.dp).testTag("search.truncated"),
        )
    }
    if (hits.isNotEmpty()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(hits, key = { _, hit -> hit.entry.ref.identityKey }) { index, hit ->
                SearchResultRow(index, hit, onOpenResult)
            }
        }
    }
}

@Composable
private fun RootErrorState(
    error: StorageError,
    hits: List<SearchHit>,
    rootFailures: List<SearchRootFailure>,
    onOpenResult: (SearchHit) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(searchErrorMessage(error), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("search.error"))
        if (rootFailures.isNotEmpty()) {
            Text("${rootFailures.size} supported source(s) need attention before results are complete.")
            rootFailures.forEach { failure ->
                Text("${failure.label}: ${searchErrorMessage(failure.error)}", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (hits.isNotEmpty()) {
            Text("Previously found results remain available.")
            hits.forEachIndexed { index, hit -> SearchResultRow(index, hit, onOpenResult) }
        } else {
            Text("No results are available for this scope.")
        }
    }
}

@Composable
private fun SearchResultRow(index: Int, hit: SearchHit, onOpenResult: (SearchHit) -> Unit) {
    val location = buildList {
        hit.rootLabel?.let(::add)
        addAll(hit.ancestors.map { it.displayName })
    }.joinToString(" / ")
    val metadata = buildList {
        add(if (hit.entry.kind == EntryKind.DIRECTORY) "Folder" else hit.entry.mimeType ?: "File")
        hit.entry.sizeBytes?.let { add("${it} B") }
        hit.entry.modifiedAtEpochMillis?.let {
            add(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)))
        }
    }.joinToString(" · ")
    TextButton(
        onClick = { onOpenResult(hit) },
        modifier = Modifier.fillMaxWidth().testTag("search.result.$index"),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (hit.entry.kind == EntryKind.DIRECTORY) "□" else "·")
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(hit.entry.displayName, style = MaterialTheme.typography.titleMedium)
                if (location.isNotEmpty()) Text(location, style = MaterialTheme.typography.bodySmall)
                Text(metadata, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun scopeLabel(scope: SearchScope): String = when (scope) {
    SearchScope.ThisDevice -> "This device"
    is SearchScope.CurrentFolder -> "Current folder · ${scope.directory.displayName}"
}

private fun searchErrorMessage(error: StorageError): String = when (error) {
    StorageError.ProviderUnavailable -> "The storage provider is unavailable."
    StorageError.PermissionDenied -> "Search permission was denied for this scope."
    StorageError.NotFound -> "The search scope is no longer available."
    StorageError.StaleReference -> "The search scope is stale. Return to Files and try again."
    StorageError.Unsupported -> "This search scope is not available yet."
    StorageError.Cancelled -> "Search was cancelled."
    is StorageError.IoFailure -> "The provider could not complete the search."
    else -> "Search could not be completed."
}
