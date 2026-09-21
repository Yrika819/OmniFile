package com.omnifile.media

import com.omnifile.storage.EntryKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackEligibilityTest {
    @Test
    fun `readable audio MIME file is eligible`() {
        val entry = testEntry("local-app-files", "a/song.flac", "song.flac", mimeType = "audio/flac")
        assertEquals(PlaybackEligibility.Eligible, entry.playbackEligibility())
    }

    @Test
    fun `unreadable audio file is not eligible`() {
        val entry = testEntry(
            "local-app-files",
            "a/song.flac",
            "song.flac",
            mimeType = "audio/flac",
            capabilities = emptySet(),
        )
        assertEquals(
            PlaybackEligibility.Ineligible(PlaybackIneligibility.NOT_READABLE),
            entry.playbackEligibility(),
        )
    }

    @Test
    fun `non-audio MIME is rejected truthfully`() {
        val entry = testEntry("local-app-files", "a/notes.txt", "notes.txt", mimeType = "text/plain")
        assertEquals(
            PlaybackEligibility.Ineligible(PlaybackIneligibility.UNSUPPORTED_TYPE),
            entry.playbackEligibility(),
        )
    }

    @Test
    fun `documented extension fallback applies when MIME is absent`() {
        listOf("song.flac", "song.mp3", "song.m4a", "song.aac", "song.wav", "song.ogg", "song.opus")
            .forEach { name ->
                val entry = testEntry("saf-tree-abc", "root/$name", name, mimeType = null)
                assertEquals(name, PlaybackEligibility.Eligible, entry.playbackEligibility())
            }
    }

    @Test
    fun `unknown extension without MIME is not claimed playable`() {
        val entry = testEntry("saf-tree-abc", "root/archive.zip", "archive.zip", mimeType = null)
        assertEquals(
            PlaybackEligibility.Ineligible(PlaybackIneligibility.UNSUPPORTED_TYPE),
            entry.playbackEligibility(),
        )
    }

    @Test
    fun `directory with audio-looking name is still a directory`() {
        val entry = testEntry(
            "local-app-files",
            "a/music",
            "music.flac",
            kind = EntryKind.DIRECTORY,
            capabilities = setOf(
                com.omnifile.storage.StorageCapability.LIST_CHILDREN,
                com.omnifile.storage.StorageCapability.READ_SEQUENTIAL,
            ),
        )
        assertEquals(
            PlaybackEligibility.Ineligible(PlaybackIneligibility.DIRECTORY),
            entry.playbackEligibility(),
        )
    }
}

