package com.omnifile.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnifile.storage.SupportedRootSnapshot

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(snapshot: SupportedRootSnapshot?) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
            )
        },
    ) { padding ->
        val rows = listOf(
            "OmniFile" to "Version ${com.omnifile.BuildConfig.VERSION_NAME}",
            "Storage access" to "${snapshot?.roots?.size ?: 0} supported source(s)",
            "This device search" to if (snapshot?.isComplete == true) "All supported sources available" else "Results may be partial when a source is unavailable",
            "Platform" to "Android ${Build.VERSION.SDK_INT}",
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("Current app information", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp)) }
            items(rows) { (title, subtitle) ->
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { Text("No inactive toggles are shown. More settings will be added with the capabilities they control.", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
