package com.omnifile.ui.files

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omnifile.files.FilesSelectionState
import com.omnifile.files.FilesUiState
import com.omnifile.storage.EntryKind
import com.omnifile.storage.StorageCapability
import com.omnifile.storage.StorageEntry
import java.text.DateFormat
import java.util.Date

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun FilesScreen(
    state: FilesUiState,
    onSelectLocal: () -> Unit,
    onPickTree: () -> Unit,
    onOpenDirectory: (StorageEntry) -> Unit,
    onEnterSelection: (StorageEntry) -> Unit,
    onToggleSelection: (StorageEntry) -> Unit,
    onClearSelection: () -> Unit,
    onRenameSelected: (String) -> Unit,
    onDeleteSelected: () -> Unit,
    mutationInFlight: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    var renameDialogVisible by remember { mutableStateOf(false) }
    var deleteDialogVisible by remember { mutableStateOf(false) }
    var requestedName by remember { mutableStateOf("") }
    val selection = selectionFor(state)
    val selectedEntries = entriesForSelection(state, selection)
    val canRename = selectedEntries.size == 1 && selectedEntries.single().supports(StorageCapability.RENAME)
    val canDelete = selectedEntries.isNotEmpty() && selectedEntries.all { it.supports(StorageCapability.DELETE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selection != null) "${selection.selectedEntries.size} selected" else titleFor(state)) },
                navigationIcon = {
                    if (state !is FilesUiState.SourceSelection) {
                        IconButton(onClick = if (selection != null) onClearSelection else onBack) { Text("‹") }
                    }
                },
                actions = {
                    if (selection != null && mutationInFlight) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else if (selection != null) {
                        if (canRename) {
                            TextButton(onClick = {
                                requestedName = selectedEntries.single().displayName
                                renameDialogVisible = true
                            }) { Text("Rename") }
                        }
                        TextButton(enabled = canDelete, onClick = { deleteDialogVisible = true }) { Text("Delete") }
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
                is FilesUiState.Content -> EntryList(
                    breadcrumb = state.breadcrumb,
                    entries = state.entries,
                    selection = state.selection,
                    onOpenDirectory = onOpenDirectory,
                    onEnterSelection = onEnterSelection,
                    onToggleSelection = onToggleSelection,
                )
                is FilesUiState.Empty -> EmptyState(state.breadcrumb)
                is FilesUiState.Error -> ErrorState(state.error.toString(), state.location, state.breadcrumb, onRetry)
            }
        }
    }

    if (renameDialogVisible) {
        AlertDialog(
            onDismissRequest = { renameDialogVisible = false },
            title = { Text("Rename") },
            text = {
                TextField(
                    value = requestedName,
                    onValueChange = { requestedName = it },
                    singleLine = true,
                    label = { Text("New name") },
                )
            },
            confirmButton = {
                TextButton(enabled = requestedName.isNotBlank(), onClick = {
                    renameDialogVisible = false
                    onRenameSelected(requestedName)
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameDialogVisible = false }) { Text("Cancel") } },
        )
    }
    if (deleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            title = { Text("Delete selected items?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteDialogVisible = false
                    onDeleteSelected()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteDialogVisible = false }) { Text("Cancel") } },
        )
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
    selection: FilesSelectionState?,
    onOpenDirectory: (StorageEntry) -> Unit,
    onEnterSelection: (StorageEntry) -> Unit,
    onToggleSelection: (StorageEntry) -> Unit,
) {
    Text(
        text = breadcrumb.joinToString(" / "),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
    )
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(entries, key = { it.ref.hashCode() }) { entry ->
            EntryRow(
                entry = entry,
                selected = entry.ref in selection?.selectedEntries.orEmpty(),
                selectionMode = selection != null,
                onOpenDirectory = onOpenDirectory,
                onEnterSelection = onEnterSelection,
                onToggleSelection = onToggleSelection,
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: StorageEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onOpenDirectory: (StorageEntry) -> Unit,
    onEnterSelection: (StorageEntry) -> Unit,
    onToggleSelection: (StorageEntry) -> Unit,
) {
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
            .semantics { this.selected = selected }
            .combinedClickable(
                onClick = {
                    if (selectionMode) onToggleSelection(entry)
                    else if (entry.kind == EntryKind.DIRECTORY) onOpenDirectory(entry)
                },
                onLongClick = { onEnterSelection(entry) },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (selected) "✓" else if (entry.kind == EntryKind.DIRECTORY) "□" else "·")
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

private fun selectionFor(state: FilesUiState): FilesSelectionState? = when (state) {
    is FilesUiState.Content -> state.selection
    is FilesUiState.Empty -> state.selection
    is FilesUiState.Error -> state.selection
    else -> null
}

private fun entriesForSelection(
    state: FilesUiState,
    selection: FilesSelectionState?,
): List<StorageEntry> = if (state is FilesUiState.Content && selection != null) {
    state.entries.filter { it.ref in selection.selectedEntries }
} else {
    emptyList()
}

private fun StorageEntry.supports(capability: StorageCapability): Boolean = capability in capabilities
