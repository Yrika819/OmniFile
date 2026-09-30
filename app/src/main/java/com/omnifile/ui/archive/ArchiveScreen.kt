package com.omnifile.ui.archive

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omnifile.archive.ArchiveEntryStatus
import com.omnifile.archive.ArchiveError
import com.omnifile.archive.ArchiveExtractionUiState
import com.omnifile.archive.ArchiveNode
import com.omnifile.archive.ArchiveUiState
import com.omnifile.storage.EntryKind

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
fun ArchiveScreen(
    state: ArchiveUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenDirectory: (ArchiveNode) -> Unit,
    onPreview: (ArchiveNode) -> Unit = {},
    onEnterSelection: (ArchiveNode) -> Unit,
    onToggleSelection: (ArchiveNode) -> Unit,
    onExtract: () -> Unit,
    onCancelExtraction: () -> Unit,
    onRetry: () -> Unit,
) {
    val selection = when (state) {
        is ArchiveUiState.Content -> state.selection
        is ArchiveUiState.Empty -> state.selection
        else -> emptySet()
    }
    val extraction = when (state) {
        is ArchiveUiState.Content -> state.extraction
        is ArchiveUiState.Empty -> state.extraction
        else -> null
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Text("‹") } },
                title = {
                    Text(
                        when (state) {
                            is ArchiveUiState.Loading -> state.container.displayName
                            is ArchiveUiState.Error -> state.container.displayName
                            is ArchiveUiState.Content -> state.document.container.title
                            is ArchiveUiState.Empty -> state.document.container.title
                            ArchiveUiState.Idle -> "Archive"
                        },
                    )
                },
                actions = {
                    if (extraction is ArchiveExtractionUiState.Running) {
                        TextButton(onClick = onCancelExtraction, modifier = Modifier.testTag("archive.extract.cancel")) {
                            Text("Cancel")
                        }
                    } else if (selection.isNotEmpty()) {
                        TextButton(
                            enabled = selection.isNotEmpty(),
                            onClick = onExtract,
                            modifier = Modifier.testTag("archive.extract"),
                        ) { Text("Extract to Local") }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues),
        ) {
            when (state) {
                ArchiveUiState.Idle -> Unit
                is ArchiveUiState.Loading -> Loading(state.container.displayName)
                is ArchiveUiState.Error -> ErrorState(state.error, onRetry)
                is ArchiveUiState.Content -> ArchiveContent(
                    state,
                    Modifier.weight(1f),
                    onOpenDirectory,
                    onPreview,
                    onEnterSelection,
                    onToggleSelection,
                )
                is ArchiveUiState.Empty -> {
                    Breadcrumb(state.path.toString())
                    Text("This archive folder is empty.", modifier = Modifier.padding(16.dp))
                }
            }
            extraction?.let { ExtractionStatus(it) }
        }
    }
}

@Composable
private fun ArchiveContent(
    state: ArchiveUiState.Content,
    modifier: Modifier = Modifier,
    onOpenDirectory: (ArchiveNode) -> Unit,
    onPreview: (ArchiveNode) -> Unit,
    onEnterSelection: (ArchiveNode) -> Unit,
    onToggleSelection: (ArchiveNode) -> Unit,
) {
    Breadcrumb(state.path.toString())
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
    ) {
        items(state.entries, key = { it.ref.identityKey }) { entry ->
            val selected = entry.ref in state.selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("archive.entry.${entry.ref.identityKey}")
                    .semantics(mergeDescendants = true) {
                        this.selected = selected
                        onLongClick {
                            onEnterSelection(entry)
                            true
                        }
                    }
                    .combinedClickable(
                        onClick = {
                            if (state.selection.isNotEmpty()) onToggleSelection(entry)
                            else if (entry.kind == EntryKind.DIRECTORY) onOpenDirectory(entry)
                            else if (entry.status == ArchiveEntryStatus.SUPPORTED) onPreview(entry)
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
                    Text(archiveMetadata(entry), style = MaterialTheme.typography.bodySmall)
                }
                if (entry.status != ArchiveEntryStatus.SUPPORTED) {
                    Text("Unsafe", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                } else if (entry.kind == EntryKind.FILE) {
                    TextButton(onClick = { if (state.selection.isEmpty()) onPreview(entry) }, enabled = state.selection.isEmpty()) {
                        Text("Preview")
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Breadcrumb(path: String) {
    Text(
        text = if (path.isEmpty()) "/" else "/$path",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun Loading(name: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.size(12.dp))
        Text("Reading $name")
    }
}

@Composable
private fun ErrorState(error: ArchiveError, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(archiveErrorMessage(error), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry, modifier = Modifier.testTag("archive.retry")) { Text("Retry") }
    }
}

@Composable
private fun ExtractionStatus(state: ArchiveExtractionUiState) {
    when (state) {
        is ArchiveExtractionUiState.Running -> Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Text("Extracting ${state.progress.filesCompleted}/${state.progress.filesTotal} files")
        }
        is ArchiveExtractionUiState.Failed -> Text(
            "Extraction failed; no completed result was claimed. ${extractionErrorMessage(state.result.error)}",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error,
        )
        is ArchiveExtractionUiState.Cancelled -> Text(
            if (state.cleanupComplete) "Extraction cancelled; partial output was removed."
            else "Extraction cancelled; cleanup needs attention.",
            modifier = Modifier.padding(16.dp),
        )
        is ArchiveExtractionUiState.Completed -> Text(
            "Extracted ${state.files} file(s) to Local files.",
            modifier = Modifier.padding(16.dp),
        )
    }
}

private fun archiveMetadata(entry: ArchiveNode): String = buildList {
    add(if (entry.kind == EntryKind.DIRECTORY) "Folder" else "File")
    entry.sizeBytes?.let { add("$it B") }
    entry.compressedSizeBytes?.let { add("compressed $it B") }
}.joinToString(" · ")

private fun archiveErrorMessage(error: ArchiveError): String = when (error) {
    ArchiveError.NotAnArchive -> "This file is not a supported ZIP archive."
    ArchiveError.Corrupt -> "The ZIP archive is corrupt or truncated."
    ArchiveError.Encrypted -> "Encrypted ZIP entries are not supported in VS08."
    ArchiveError.Unsupported -> "This ZIP feature or compression method is not supported."
    is ArchiveError.ResourceLimit -> "The archive exceeds a safety limit: ${error.detail}"
    is ArchiveError.Provider -> when (error.error) {
        com.omnifile.storage.StorageError.ProviderUnavailable -> "The storage provider is unavailable."
        com.omnifile.storage.StorageError.PermissionDenied -> "Archive read permission was denied."
        else -> "The archive source could not be read."
    }
    is ArchiveError.Io -> "The archive could not be read."
}

private fun extractionErrorMessage(error: com.omnifile.archive.ArchiveExtractionError): String = when (error) {
    is com.omnifile.archive.ArchiveExtractionError.UnsafePath -> "Unsafe archive path: ${error.name}."
    is com.omnifile.archive.ArchiveExtractionError.DuplicateTarget -> "Duplicate output target: ${error.path}."
    is com.omnifile.archive.ArchiveExtractionError.TypeConflict -> "File/directory conflict: ${error.path}."
    is com.omnifile.archive.ArchiveExtractionError.DestinationConflict -> "Destination already contains: ${error.path}."
    is com.omnifile.archive.ArchiveExtractionError.Source -> archiveErrorMessage(error.error)
    is com.omnifile.archive.ArchiveExtractionError.Io -> error.detail ?: "The destination could not be written."
    is com.omnifile.archive.ArchiveExtractionError.ResourceLimit -> error.detail
}
