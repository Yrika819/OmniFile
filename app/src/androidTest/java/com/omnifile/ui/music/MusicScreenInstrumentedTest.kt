package com.omnifile.ui.music

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omnifile.media.NowPlayingState
import com.omnifile.media.PlaybackError
import com.omnifile.media.PlaybackItem
import com.omnifile.media.PlaybackStatus
import com.omnifile.storage.ProviderId
import com.omnifile.storage.SeekSupport
import com.omnifile.ui.theme.OmniFileTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateIsTruthfulAndHasNoTransportControls() {
        composeRule.setContent { OmniFileTheme { MusicScreen(state = NowPlayingState.NoMedia) } }

        composeRule.onNodeWithText("Nothing is playing.").assertExists()
        composeRule.onNodeWithTag("music.title").assertDoesNotExist()
        composeRule.onNodeWithTag("music.play-pause").assertDoesNotExist()
    }

    @Test
    fun activeSeekableItemShowsTitleSourceProgressAndPause() {
        composeRule.setContent {
            OmniFileTheme {
                MusicScreen(
                    state = active(
                        isPlaying = true,
                        positionMs = 65_000L,
                        durationMs = 130_000L,
                        seekSupport = SeekSupport.SEEKABLE,
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("music.title").assertExists()
        composeRule.onNodeWithText("tone.wav").assertExists()
        composeRule.onNodeWithText("Local storage").assertExists()
        composeRule.onNodeWithText("1:05").assertExists()
        composeRule.onNodeWithText("2:10").assertExists()
        composeRule.onNodeWithText("Pause").assertExists()
        composeRule.onNodeWithTag("music.seek").assertIsEnabled()
    }

    @Test
    fun pausedItemOffersPlay() {
        composeRule.setContent {
            OmniFileTheme { MusicScreen(state = active(isPlaying = false)) }
        }
        composeRule.onNodeWithText("Play").assertExists()
    }

    @Test
    fun nonSeekableSourceDisablesSeekTruthfully() {
        composeRule.setContent {
            OmniFileTheme {
                MusicScreen(
                    state = active(
                        isPlaying = true,
                        durationMs = 130_000L,
                        seekSupport = SeekSupport.NOT_SEEKABLE,
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("music.seek").assertIsNotEnabled()
        composeRule.onNodeWithText("Seeking is not supported for this source.").assertExists()
    }

    @Test
    fun unknownDurationIsNeverRenderedAsZeroTotal() {
        composeRule.setContent {
            OmniFileTheme {
                MusicScreen(
                    state = active(isPlaying = true, durationMs = null, seekSupport = SeekSupport.UNKNOWN),
                )
            }
        }

                        composeRule.onNodeWithTag("music.position").assertDoesNotExist()
        composeRule.onNodeWithText("—").assertExists()
        composeRule.onNodeWithText("0:00").assertDoesNotExist()
        composeRule.onNodeWithTag("music.seek").assertIsNotEnabled()
    }

    @Test
    fun permissionUnavailableErrorShowsMappedMessage() {
        expectErrorMessage(
            PlaybackError.PermissionUnavailable,
            "Storage permission for this item is no longer available.",
        )
    }

    @Test
    fun sourceNotFoundErrorShowsMappedMessage() {
        expectErrorMessage(PlaybackError.SourceNotFound, "This file could not be found.")
    }

    @Test
    fun providerUnavailableErrorShowsMappedMessage() {
        expectErrorMessage(PlaybackError.ProviderUnavailable, "The storage provider is unavailable.")
    }

    @Test
    fun unsupportedMediaErrorShowsMappedMessage() {
        expectErrorMessage(PlaybackError.UnsupportedMedia, "This audio format is not supported.")
    }

    @Test
    fun decodeFailureErrorShowsMappedMessage() {
        expectErrorMessage(PlaybackError.DecodeFailure, "This file could not be decoded.")
    }

    @Test
    fun readFailureErrorShowsMappedMessage() {
        expectErrorMessage(PlaybackError.ReadFailure, "The file could not be read.")
    }

    @Test
    fun sourceChangedErrorShowsMappedMessage() {
        expectErrorMessage(
            PlaybackError.SourceChanged,
            "The file changed or is no longer available.",
        )
    }

    private fun expectErrorMessage(error: PlaybackError, expected: String) {
        composeRule.setContent {
            OmniFileTheme {
                MusicScreen(
                    state = active().copy(status = PlaybackStatus.ERROR, isPlaying = false, error = error),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(expected).assertExists()
    }

    @Test
    fun playPauseAndSeekCallbacksAreWired() {
        var playPauseCalls = 0
        var soughtTo = -1L
        composeRule.setContent {
            OmniFileTheme {
                MusicScreen(
                    state = active(
                        isPlaying = true,
                        positionMs = 0L,
                        durationMs = 130_000L,
                        seekSupport = SeekSupport.SEEKABLE,
                    ),
                    onPlayPause = { playPauseCalls++ },
                    onSeek = { soughtTo = it },
                )
            }
        }

        composeRule.onNodeWithTag("music.play-pause").performClick()
        assertEquals(1, playPauseCalls)

        composeRule.onNodeWithTag("music.seek")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        composeRule.waitForIdle()
        assertTrue("seek target=$soughtTo", soughtTo in 60_000L..70_000L)
    }

    private fun active(
        isPlaying: Boolean = false,
        positionMs: Long = 0L,
        durationMs: Long? = 130_000L,
        seekSupport: SeekSupport = SeekSupport.SEEKABLE,
    ) = NowPlayingState(
        status = PlaybackStatus.READY,
        item = PlaybackItem(
            mediaId = "local-app-files\u0000tone.wav",
            providerId = ProviderId("local-app-files"),
            entryIdentityKey = "local-app-files\u0000tone.wav",
            displayName = "tone.wav",
            mimeType = "audio/x-wav",
            sizeBytes = 64_000L,
            sourceLabel = "Local storage",
        ),
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = durationMs,
        seekSupport = seekSupport,
        error = null,
    )
}
