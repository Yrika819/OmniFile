package com.omnifile.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.omnifile.storage.SupportedRoot
import com.omnifile.storage.StorageError

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeScreen(
    state: HomeUiState,
    onOpenRoot: (SupportedRoot) -> Unit,
    onAddFolder: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OmniFile") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Storage", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            when (state) {
                HomeUiState.Loading -> item {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp).testTag("home.loading"))
                }
                is HomeUiState.Error -> item {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(state.message)
                        Button(onClick = onAddFolder) { Text("Add a storage folder") }
                    }
                }
                is HomeUiState.Ready -> {
                    items(state.snapshot.roots, key = { it.id }) { root ->
                        RootCard(root, onOpenRoot)
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Add a folder", style = MaterialTheme.typography.titleMedium)
                                Text("Search and browse only storage that OmniFile can currently access.")
                                Button(onClick = onAddFolder, modifier = Modifier.testTag("home.add-folder")) { Text("Choose SAF folder") }
                            }
                        }
                    }
                    if (state.snapshot.failures.isNotEmpty()) item {
                        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "${state.snapshot.failures.size} storage source(s) unavailable. They are not included in This device search.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            state.snapshot.failures.forEach { failure ->
                                Text("${failure.label}: ${homeFailureMessage(failure.error)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RootCard(root: SupportedRoot, onOpenRoot: (SupportedRoot) -> Unit) {
    Card(
        onClick = { onOpenRoot(root) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("home.root.${root.id}"),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (root.source.name == "LOCAL") "▣" else "□", style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(root.label, style = MaterialTheme.typography.titleLarge)
                Text("Browse ${root.entry.displayName}")
                Text("Supported source", style = MaterialTheme.typography.bodySmall)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium)
        }
    }
}


private fun homeFailureMessage(error: StorageError): String = when (error) {
    StorageError.PermissionDenied -> "permission revoked or unavailable"
    StorageError.ProviderUnavailable -> "provider unavailable"
    StorageError.StaleReference -> "stale source"
    else -> "unavailable"
}
