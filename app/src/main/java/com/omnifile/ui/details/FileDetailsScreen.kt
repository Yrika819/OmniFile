package com.omnifile.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.omnifile.details.DigestUiState
import com.omnifile.details.FileDetailsFailure
import com.omnifile.details.FileDetailsMetadata
import com.omnifile.details.FileDetailsUiState
import com.omnifile.details.fraction
import com.omnifile.details.isRetryable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/** Shown for every field the provider cannot prove. Never a blank, a zero, or a guess. */
private const val NOT_AVAILABLE = "Not available"

/**
 * File Details for exactly one supported regular file.
 *
 * Metadata stays visible even when the provider cannot sequentially read the source; only the
 * digest action is capability-gated. Every state is carried by text and semantics as well as by
 * colour, and the content scrolls so large font scales cannot strand the primary actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDetailsScreen(
    state: FileDetailsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onCalculate: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("File details") },
                navigationIcon = {
                    TextButton(
                        modifier = Modifier.testTag("fileDetails.back"),
                        onClick = onBack,
                    ) { Text("Back") }
                },
            )
        },
    ) { insets ->
        val content = state as? FileDetailsUiState.Metadata
        if (content == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(insets).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { Text("This file is no longer available.") }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MetadataSection(content.metadata)
            HorizontalDivider()
            DigestSection(
                hashingSupported = content.hashingSupported,
                digest = content.digest,
                onCalculate = onCalculate,
                onCancel = onCancel,
                onRetry = onRetry,
                onCopy = onCopy,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MetadataSection(metadata: FileDetailsMetadata) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Name", style = MaterialTheme.typography.labelLarge)
        Text(
            text = metadata.displayName,
            modifier = Modifier.testTag("fileDetails.name"),
            style = MaterialTheme.typography.bodyLarge,
        )
        MetadataRow("fileDetails.size", "Size", formatSize(metadata.sizeBytes))
        MetadataRow("fileDetails.modified", "Modified", formatModified(metadata.modifiedAtEpochMillis))
        MetadataRow("fileDetails.type", "Type", metadata.mimeType ?: NOT_AVAILABLE)
        MetadataRow("fileDetails.source", "Source", metadata.sourceLabel)
    }
}

@Composable
private fun MetadataRow(tag: String, label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            text = value,
            modifier = Modifier.testTag(tag),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun DigestSection(
    hashingSupported: Boolean,
    digest: DigestUiState,
    onCalculate: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SHA-256", style = MaterialTheme.typography.titleMedium)

        when (digest) {
            is DigestUiState.Calculating -> CalculatingState(digest, onCancel)
            is DigestUiState.Cancelling -> StatusState("fileDetails.cancelling", "Cancelling…")
            is DigestUiState.Complete -> CompleteState(digest, onCopy)
            DigestUiState.Cancelled -> StatusState("fileDetails.cancelled", "Cancelled")
            is DigestUiState.Failed -> FailureState(digest, onRetry)
            DigestUiState.Idle -> Unit
        }

        // Stacked rather than in a row: a large font scale must never push an action off-screen.
        Button(
            modifier = Modifier.testTag("fileDetails.calculate"),
            enabled = hashingSupported &&
                    digest !is DigestUiState.Calculating &&
                    digest !is DigestUiState.Cancelling,
            onClick = onCalculate,
        ) { Text("Calculate SHA-256") }

        if (!hashingSupported) {
            Text(
                text = "This source cannot be read for hashing.",
                modifier = Modifier.testTag("fileDetails.calculateUnsupported"),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CalculatingState(digest: DigestUiState.Calculating, onCancel: () -> Unit) {
    val fraction = digest.fraction
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (fraction == null) {
                "Calculating…"
            } else {
                "Calculating… ${(fraction * 100).toInt()}%"
            },
            modifier = Modifier.testTag("fileDetails.progressLabel"),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (fraction == null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fileDetails.progress")
                    .semantics { contentDescription = "Calculating SHA-256, progress unknown" },
            )
        } else {
            val percent = (fraction * 100).toInt()
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fileDetails.progress")
                    .semantics {
                        contentDescription = "Calculating SHA-256, $percent percent"
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    },
            )
        }
        TextButton(
            modifier = Modifier.testTag("fileDetails.cancel"),
            onClick = onCancel,
        ) { Text("Cancel") }
    }
}

@Composable
private fun CompleteState(digest: DigestUiState.Complete, onCopy: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusState("fileDetails.completeStatus", "SHA-256 complete")
        Text(
            text = digest.hex,
            modifier = Modifier.testTag("fileDetails.digest"),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
        TextButton(
            modifier = Modifier.testTag("fileDetails.copy"),
            onClick = { onCopy(digest.hex) },
        ) { Text("Copy SHA-256") }
    }
}

@Composable
private fun FailureState(digest: DigestUiState.Failed, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusState("fileDetails.errorStatus", failureText(digest.failure))
        if (digest.isRetryable) {
            TextButton(
                modifier = Modifier.testTag("fileDetails.retry"),
                onClick = onRetry,
            ) { Text("Retry") }
        }
    }
}

/** State is never carried by colour alone: every state has its own text and semantics. */
@Composable
private fun StatusState(tag: String, text: String) {
    Text(
        text = text,
        modifier = Modifier.testTag(tag).semantics { contentDescription = text },
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Sanitized, closed-vocabulary diagnostics. No exception text, provider message, or path. */
private fun failureText(failure: FileDetailsFailure): String = when (failure) {
    FileDetailsFailure.ACCESS_UNAVAILABLE -> "Access to this file is no longer granted."
    FileDetailsFailure.SOURCE_UNAVAILABLE -> "This file is no longer available."
    FileDetailsFailure.SOURCE_CHANGED -> "This file changed or moved while it was being read."
    FileDetailsFailure.READ_FAILURE -> "The file could not be read."
    FileDetailsFailure.UNSUPPORTED -> "This file cannot be hashed."
}

private val SIZE_UNITS = arrayOf("B", "KiB", "MiB", "GiB", "TiB")

private fun formatSize(bytes: Long?): String {
    if (bytes == null) return NOT_AVAILABLE
    if (bytes == 0L) return "0 B"
    if (abs(bytes) < 1024L) return "$bytes B"
    val exponent = (ln(abs(bytes).toDouble()) / ln(1024.0)).toInt().coerceIn(1, SIZE_UNITS.lastIndex)
    val scaled = bytes.toDouble() / 1024.0.pow(exponent)
    // The exact byte count stays visible so the summary can never overstate what was hashed.
    return String.format(Locale.getDefault(), "%.1f %s (%d bytes)", scaled, SIZE_UNITS[exponent], bytes)
}

private fun formatModified(epochMillis: Long?): String {
    if (epochMillis == null) return NOT_AVAILABLE
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}
