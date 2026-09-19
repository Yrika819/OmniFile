package com.omnifile.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnifile.files.FilesUiState
import com.omnifile.storage.EntryKind
import com.omnifile.storage.StorageEntry
import java.text.DateFormat
import java.util.Date

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FilesScreen(
    state: FilesUiState,
    onSelectLocal: () -> Unit,
    onPickTree: () -> Unit,
    onOpenDirectory: (StorageEntry) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleFor(state)) },
                navigationIcon = {
                    if (state !is FilesUiState.SourceSelection) {
                        IconButton(onClick = onBack) { Text("‹") }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (state) {
                FilesUiState.SourceSelection -> SourceSelection(onSelectLocal, onPickTree)
                is FilesUiState.Loading -> LoadingState(state.location)
                is FilesUiState.Content -> EntryList(state.breadcrumb, state.entries, onOpenDirectory)
                is FilesUiState.Empty -> EmptyState(state.breadcrumb)
                is FilesUiState.Error -> ErrorState(state.error.toString(), state.location, state.breadcrumb, onRetry)
            }
        }
    }
}

@Composable
private fun SourceSelection(onSelectLocal: () -> Unit, onPickTree: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Browse files", style = MaterialTheme.typography.headlineSmall)
        Text("Choose a read-only storage source to begin.")
        Button(onClick = onSelectLocal) { Text("Local files") }
        Button(onClick = onPickTree) { Text("フォルダを選択") }
    }
}

@Composable
private fun LoadingState(location: StorageEntry?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.size(12.dp))
        Text(location?.displayName ?: "Loading")
    }
}

@Composable
private fun EntryList(
    breadcrumb: List<String>,
    entries: List<StorageEntry>,
    onOpenDirectory: (StorageEntry) -> Unit,
) {
    Text(
        text = breadcrumb.joinToString(" / "),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
    )
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(entries, key = { it.ref.hashCode() }) { entry ->
            EntryRow(entry, onOpenDirectory)
        }
    }
}

@Composable
private fun EmptyState(breadcrumb: List<String>) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(breadcrumb.joinToString(" / "), style = MaterialTheme.typography.labelLarge)
        Text("This folder is empty.")
    }
}

@Composable
private fun ErrorState(
    error: String,
    location: StorageEntry?,
    breadcrumb: List<String>,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (location != null) Text(breadcrumb.joinToString(" / "), style = MaterialTheme.typography.labelLarge)
        Text("Unable to browse this location.")
        Text(error, style = MaterialTheme.typography.bodySmall)
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun EntryRow(entry: StorageEntry, onOpenDirectory: (StorageEntry) -> Unit) {
    val secondary = buildList {
        if (entry.kind == EntryKind.DIRECTORY) add("Folder")
        else entry.sizeBytes?.let { add("${it} B") }
        entry.modifiedAtEpochMillis?.let {
            add(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)))
        }
    }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.kind == EntryKind.DIRECTORY) { onOpenDirectory(entry) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (entry.kind == EntryKind.DIRECTORY) "□" else "·")
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.displayName)
            if (secondary.isNotEmpty()) Text(secondary, style = MaterialTheme.typography.bodySmall)
        }
    }
    HorizontalDivider()
}

private fun titleFor(state: FilesUiState): String = when (state) {
    FilesUiState.SourceSelection -> "Files"
    is FilesUiState.Loading -> state.location?.displayName ?: "Files"
    is FilesUiState.Content -> state.location.displayName
    is FilesUiState.Empty -> state.location.displayName
    is FilesUiState.Error -> state.location?.displayName ?: "Files"
}
