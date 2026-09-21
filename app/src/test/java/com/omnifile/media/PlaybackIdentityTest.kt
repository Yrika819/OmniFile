package com.omnifile.media

import com.omnifile.storage.EntryKind
import com.omnifile.storage.StorageCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlaybackIdentityTest {
    @Test
    fun `same display name from different providers has distinct playback identity`() {
        val local = PlaybackItem.of(testEntry("local-app-files", "a/song.flac", "song.flac"), "Local storage")
        val saf = PlaybackItem.of(testEntry("saf-tree-abc", "root/song.flac", "song.flac"), "SAF folder")

        assertNotEquals(local.mediaId, saf.mediaId)
        assertNotEquals(local.providerId, saf.providerId)
        assertEquals("song.flac", local.displayName)
        assertEquals("song.flac", saf.displayName)
    }

    @Test
    fun `renamed display text does not become identity`() {
        val original = testEntry("local-app-files", "a/song.flac", "song.flac")
        val renamed = original.copy(displayName = "renamed.flac")

        assertEquals(
            PlaybackItem.of(original, "Local storage").mediaId,
            PlaybackItem.of(renamed, "Local storage").mediaId,
        )
        assertNotEquals(
            PlaybackItem.of(original, "Local storage").displayName,
            PlaybackItem.of(renamed, "Local storage").displayName,
        )
    }

    @Test
    fun `media id is opaque and provider scoped`() {
        val source = testEntry("saf-tree-abc", "content://provider/tree/raw/document/song.flac", "song.flac")
        val item = PlaybackItem.of(source, "SAF folder")

        assertEquals("saf-tree-abc\u0000${item.entryIdentityKey}", item.mediaId)
        assertNotEquals(source.ref.identityKey, item.mediaId)
        assertFalse(item.mediaId.contains("content://"))
        assertFalse(item.mediaId.contains("/"))
        assertEquals("song.flac", item.displayName)
        assertEquals("SAF folder", item.sourceLabel)
    }

    @Test
    fun `directory is never eligible for playback`() {
        val directory = testEntry(
            "local-app-files",
            "a/folder",
            "folder",
            kind = EntryKind.DIRECTORY,
            mimeType = null,
            capabilities = setOf(StorageCapability.LIST_CHILDREN),
        )
        assertEquals(
            PlaybackEligibility.Ineligible(PlaybackIneligibility.DIRECTORY),
            directory.playbackEligibility(),
        )
    }
}
