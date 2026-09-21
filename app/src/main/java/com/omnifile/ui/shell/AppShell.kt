package com.omnifile.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun AppShell(
    selected: TopLevelDestination,
    showPrimaryNavigation: Boolean,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    if (!showPrimaryNavigation) {
        Box(Modifier.fillMaxSize()) { content() }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < 600.dp) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                PrimaryNavigationBar(selected, onDestinationSelected)
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                PrimaryNavigationRail(selected, onDestinationSelected)
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            }
        }
    }
}

@Composable
private fun PrimaryNavigationBar(selected: TopLevelDestination, onSelected: (TopLevelDestination) -> Unit) {
    NavigationBar(modifier = Modifier.testTag("shell.navigation-bar")) {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onSelected(destination) },
                icon = { DestinationIcon(destination) },
                label = { Text(destination.label) },
                modifier = Modifier.semantics { contentDescription = destination.label },
            )
        }
    }
}

@Composable
private fun PrimaryNavigationRail(selected: TopLevelDestination, onSelected: (TopLevelDestination) -> Unit) {
    NavigationRail(modifier = Modifier.testTag("shell.navigation-rail")) {
        TopLevelDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = selected == destination,
                onClick = { onSelected(destination) },
                icon = { DestinationIcon(destination) },
                label = { Text(destination.label) },
                alwaysShowLabel = true,
                modifier = Modifier.semantics { contentDescription = destination.label },
            )
        }
    }
}

@Composable
private fun DestinationIcon(destination: TopLevelDestination) {
    Text(
        text = destination.icon,
        modifier = Modifier.height(24.dp),
    )
}

enum class TopLevelDestination(val label: String, val icon: String) {
    HOME("Home", "⌂"),
    SEARCH("Search", "⌕"),
    MUSIC("Music", "♪"),
    SETTINGS("Settings", "⚙"),
}
