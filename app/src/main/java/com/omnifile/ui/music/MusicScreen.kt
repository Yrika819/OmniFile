package com.omnifile.ui.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.omnifile.media.NowPlayingState
import com.omnifile.media.PlaybackError
import com.omnifile.media.PlaybackStatus
import com.omnifile.storage.SeekSupport

/**
 * Now Playing surface. Truthful empty state when no item exists; otherwise the
 * current item's real title, source context, transport controls, and progress.
 * Seek is only enabled when the source probe proved SEEKABLE and the duration
 * is known. No library, queue, or fabricated metadata is shown.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MusicScreen(
    state: NowPlayingState = NowPlayingState.NoMedia,
    onPlayPause: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Music") },
            )
        },
    ) { padding ->
        if (state.status == PlaybackStatus.NO_MEDIA || state.item == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text("Music", style = MaterialTheme.typography.headlineMedium)
                Text("Nothing is playing.", style = MaterialTheme.typography.titleMedium)
                Text("Play an audio file from Files or Search to see it here.")
            }
            return@Scaffold
        }
        val item = state.item
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                item.displayName,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag("music.title"),
            )
            Text(
                item.sourceLabel,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("music.source"),
            )
            when (state.status) {
                PlaybackStatus.BUFFERING -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    Text("Preparing…", modifier = Modifier.testTag("music.status"))
                }
                PlaybackStatus.ENDED ->
                    Text("Playback ended", modifier = Modifier.testTag("music.status"))
                PlaybackStatus.ERROR -> Text(
                    playbackErrorMessage(state.error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("music.status"),
                )
                else -> Unit
            }
            NowPlayingControls(state, onPlayPause, onSeek)
        }
    }
}

@Composable
private fun NowPlayingControls(
    state: NowPlayingState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    if (state.seekAllowed) {
        // seekAllowed guarantees a known positive duration; the player also
        // gates individual seek calls before issuing them.
        val duration = requireNotNull(state.durationMs)
        Slider(
            value = (state.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
            onValueChange = { fraction -> onSeek((fraction * duration).toLong()) },
            modifier = Modifier.fillMaxWidth().testTag("music.seek"),
        )
    } else {
        Slider(
            value = 0f,
            onValueChange = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().testTag("music.seek"),
        )
        if (state.seekSupport == SeekSupport.NOT_SEEKABLE) {
            Text("Seeking is not supported for this source.")
        }
    }
        Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Elapsed position is only meaningful against a known total: never
        // render a 0:00 position when the duration is unknown.
        if (state.durationMs != null) {
            Text(
                formatPosition(state.positionMs),
                modifier = Modifier.testTag("music.position"),
            )
        }
        // Unknown duration is never rendered as a 0:00 total; show an em dash.
        Text(
            state.durationMs?.let(::formatPosition) ?: "—",
            modifier = Modifier.testTag("music.duration"),
        )
    }
    Button(
        onClick = onPlayPause,
        enabled = state.status == PlaybackStatus.READY || state.status == PlaybackStatus.ENDED,
        modifier = Modifier.testTag("music.play-pause"),
    ) {
        Text(if (state.isPlaying) "Pause" else "Play")
    }
}

internal fun playbackErrorMessage(error: PlaybackError?): String = when (error) {
    PlaybackError.PermissionUnavailable -> "Storage permission for this item is no longer available."
    PlaybackError.SourceNotFound -> "This file could not be found."
    PlaybackError.ProviderUnavailable -> "The storage provider is unavailable."
    PlaybackError.UnsupportedMedia -> "This audio format is not supported."
    PlaybackError.DecodeFailure -> "This file could not be decoded."
    PlaybackError.ReadFailure -> "The file could not be read."
    PlaybackError.SourceChanged -> "The file changed or is no longer available."
    is PlaybackError.Unknown, null -> "Playback failed."
}

internal fun formatPosition(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
