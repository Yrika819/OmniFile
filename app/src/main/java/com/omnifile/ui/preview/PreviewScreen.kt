package com.omnifile.ui.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import android.graphics.Bitmap
import java.nio.ByteBuffer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omnifile.preview.PreviewError
import com.omnifile.preview.PreviewPayload
import com.omnifile.preview.PreviewUiState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PreviewScreen(
    state: PreviewUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPreviousPage: () -> Unit = {},
    onNextPage: () -> Unit = {},
) {
    val item = when (state) {
        PreviewUiState.Idle -> null
        is PreviewUiState.Loading -> state.item
        is PreviewUiState.Ready -> state.item
        is PreviewUiState.Unsupported -> state.item
        is PreviewUiState.Error -> state.item
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Text("‹") } },
                title = { Text(item?.displayName ?: "Preview", maxLines = 1) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item?.locationLabel?.takeIf(String::isNotBlank)?.let {
                Text(it, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium)
            }
            when (state) {
                PreviewUiState.Idle -> Text("No file selected.", modifier = Modifier.padding(16.dp))
                is PreviewUiState.Loading -> Loading(itemName = state.item.displayName, modifier = Modifier.weight(1f).fillMaxWidth())
                is PreviewUiState.Unsupported -> Message("Preview is not supported for this file.", "preview.unsupported")
                is PreviewUiState.Error -> ErrorMessage(state.error, onRetry)
                is PreviewUiState.Ready -> when (val payload = state.payload) {
                    is PreviewPayload.Text -> {
                        Text(state.sourceLabel, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
                        TextPreview(payload.content, payload.truncated, Modifier.weight(1f).fillMaxWidth())
                    }
                    is PreviewPayload.Image -> {
                        Text(state.sourceLabel, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Image(
                                bitmap = payload.bitmap.asImageBitmap(),
                                contentDescription = "Preview of ${state.item.displayName}",
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    is PreviewPayload.PdfPage -> {
                        Text(state.sourceLabel, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
                        val pageBitmap = remember(payload.page) {
                            var bitmap: Bitmap? = null
                            try {
                                bitmap = Bitmap.createBitmap(payload.page.width, payload.page.height, Bitmap.Config.ARGB_8888)
                                bitmap.apply { copyPixelsFromBuffer(ByteBuffer.wrap(payload.page.pixels)) }
                            } catch (_: OutOfMemoryError) {
                                bitmap?.recycle()
                                null
                            }
                        }
                        DisposableEffect(pageBitmap) {
                            onDispose { pageBitmap?.recycle() }
                        }
                        if (pageBitmap != null) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Image(
                                    bitmap = pageBitmap.asImageBitmap(),
                                    contentDescription = "PDF page ${payload.pageIndex + 1} of ${payload.pageCount} for ${state.item.displayName}",
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        } else {
                            Text("This page could not be displayed.", modifier = Modifier.weight(1f).padding(24.dp).testTag("preview.pdf.memory-error"))
                        }
                        PdfPageControls(payload.pageIndex, payload.pageCount, onPreviousPage, onNextPage)
                    }
                    PreviewPayload.Unsupported -> Message("Preview is not supported for this file.", "preview.unsupported")
                    is PreviewPayload.Failure -> ErrorMessage(payload.error, onRetry)
                }
            }
        }
    }
}

@Composable
private fun Loading(itemName: String, modifier: Modifier) {
    Column(
        modifier = modifier.testTag("preview.loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text("Loading $itemName…", modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun TextPreview(content: String, truncated: Boolean, modifier: Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (truncated) {
            Text("Preview truncated at its safety limit.", modifier = Modifier.testTag("preview.truncated"), color = MaterialTheme.colorScheme.tertiary)
        }
        SelectionContainer {
            Text(content, modifier = Modifier.fillMaxWidth().testTag("preview.text"), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun PdfPageControls(pageIndex: Int, pageCount: Int, onPreviousPage: () -> Unit, onNextPage: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPreviousPage,
            enabled = pageIndex > 0,
            modifier = Modifier.testTag("preview.pdf.previous").semantics { contentDescription = "Previous page" },
        ) { Text("Previous") }
        Text("${pageIndex + 1} / $pageCount", modifier = Modifier.testTag("preview.pdf.page"))
        Button(
            onClick = onNextPage,
            enabled = pageIndex < pageCount - 1,
            modifier = Modifier.testTag("preview.pdf.next").semantics { contentDescription = "Next page" },
        ) { Text("Next") }
    }
}

@Composable
private fun Message(message: String, tag: String) {
    Text(message, modifier = Modifier.padding(24.dp).testTag(tag), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun ErrorMessage(error: PreviewError, onRetry: () -> Unit) {
    val message = when (error) {
        PreviewError.ProviderUnavailable -> "The storage provider is unavailable."
        PreviewError.SourceVanished -> "This file is no longer available."
        PreviewError.PermissionOrGrantMissing -> "Storage permission is no longer available."
        PreviewError.CorruptOrMalformed -> "This file is corrupt or malformed."
        PreviewError.ResourceLimit -> "This file exceeds the preview safety limit."
        PreviewError.PdfInputTooLarge -> "This PDF is larger than the preview limit."
        PreviewError.PdfPageCountLimit -> "This PDF has too many pages to preview."
        PreviewError.EncryptedOrUnsupported -> "Encrypted or unsupported PDFs cannot be previewed."
        PreviewError.StagingFailure -> "The PDF preview could not be staged."
        PreviewError.StagingTimeout -> "The file did not respond in time."
        PreviewError.RendererTimeout -> "PDF rendering took too long."
        PreviewError.RendererFailure -> "The PDF renderer stopped unexpectedly."
        PreviewError.Cancelled -> "Preview was cancelled."
        is PreviewError.IoFailure -> "The file could not be read."
        PreviewError.Unknown -> "This file could not be previewed."
    }
    val retryable = error == PreviewError.ProviderUnavailable || error == PreviewError.SourceVanished ||
        error == PreviewError.PermissionOrGrantMissing || error == PreviewError.StagingTimeout ||
        error == PreviewError.StagingFailure || error == PreviewError.RendererTimeout ||
        error == PreviewError.RendererFailure || error is PreviewError.IoFailure
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, modifier = Modifier.testTag("preview.error"), style = MaterialTheme.typography.bodyLarge)
        if (retryable) Button(onClick = onRetry, modifier = Modifier.testTag("preview.retry")) { Text("Retry") }
    }
}
