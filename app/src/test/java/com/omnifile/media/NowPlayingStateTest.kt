package com.omnifile.media

import com.omnifile.storage.SeekSupport
import com.omnifile.storage.StorageError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingStateTest {
    @Test
    fun `state reducer covers no media preparing playing paused ended and error`() {
        assertEquals(
            PlaybackStatus.NO_MEDIA,
            PlaybackStateReducer.reduce(PlayerPhase.IDLE, hasItem = false, hasError = false),
        )
        assertEquals(
            PlaybackStatus.BUFFERING,
            PlaybackStateReducer.reduce(PlayerPhase.BUFFERING, hasItem = true, hasError = false),
        )
        assertEquals(
            PlaybackStatus.READY,
            PlaybackStateReducer.reduce(PlayerPhase.READY, hasItem = true, hasError = false),
        )
        assertEquals(
            PlaybackStatus.ENDED,
            PlaybackStateReducer.reduce(PlayerPhase.ENDED, hasItem = true, hasError = false),
        )
        assertEquals(
            PlaybackStatus.ERROR,
            PlaybackStateReducer.reduce(PlayerPhase.READY, hasItem = true, hasError = true),
        )
        // An idle player with a loaded item is still preparing, not "no media".
        assertEquals(
            PlaybackStatus.BUFFERING,
            PlaybackStateReducer.reduce(PlayerPhase.IDLE, hasItem = true, hasError = false),
        )
    }

    @Test
    fun `seek is allowed only with proven seekable source and known duration`() {
        val base = NowPlayingState.NoMedia.copy(
            status = PlaybackStatus.READY,
            item = null,
        )
        assertFalse(base.copy(seekSupport = SeekSupport.SEEKABLE, durationMs = null).seekAllowed)
        assertFalse(base.copy(seekSupport = SeekSupport.SEEKABLE, durationMs = 0L).seekAllowed)
        assertFalse(base.copy(seekSupport = SeekSupport.NOT_SEEKABLE, durationMs = 120_000L).seekAllowed)
        assertFalse(base.copy(seekSupport = SeekSupport.UNKNOWN, durationMs = 120_000L).seekAllowed)
        assertTrue(base.copy(seekSupport = SeekSupport.SEEKABLE, durationMs = 120_000L).seekAllowed)
        // No functional seek while an error is showing.
        assertFalse(
            base.copy(
                status = PlaybackStatus.ERROR,
                seekSupport = SeekSupport.SEEKABLE,
                durationMs = 120_000L,
            ).seekAllowed,
        )
    }

    @Test
    fun `unknown duration stays null and is never a zero total`() {
        assertNull(NowPlayingState.NoMedia.durationMs)
        val streaming = NowPlayingState.NoMedia.copy(
            status = PlaybackStatus.READY,
            durationMs = null,
            seekSupport = SeekSupport.NOT_SEEKABLE,
        )
        assertNull(streaming.durationMs)
        assertFalse(streaming.seekAllowed)
    }

    @Test
    fun `storage failures map to truthful playback errors`() {
        assertEquals(
            PlaybackError.PermissionUnavailable,
            PlaybackErrorMapper.fromStorageError(StorageError.PermissionDenied),
        )
        assertEquals(
            PlaybackError.SourceNotFound,
            PlaybackErrorMapper.fromStorageError(StorageError.NotFound),
        )
        assertEquals(
            PlaybackError.ProviderUnavailable,
            PlaybackErrorMapper.fromStorageError(StorageError.ProviderUnavailable),
        )
        assertEquals(
            PlaybackError.SourceChanged,
            PlaybackErrorMapper.fromStorageError(StorageError.StaleReference),
        )
        assertEquals(
            PlaybackError.UnsupportedMedia,
            PlaybackErrorMapper.fromStorageError(StorageError.Unsupported),
        )
        assertEquals(
            PlaybackError.ReadFailure,
            PlaybackErrorMapper.fromStorageError(StorageError.IoFailure("read failed")),
        )
    }
}
