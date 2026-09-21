package com.omnifile.storage

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlaybackSourceTest {
    @Test
    fun `regular readable local file resolves to a seekable file transport`() = runBlocking {
        val root = Files.createTempDirectory("omnifile-local-playback")
        try {
            val file = Files.write(root.resolve("tone.wav"), ByteArray(64) { it.toByte() })
            val provider = LocalStorageProvider(root, ProviderId("local-test"))
            val rootEntry = (provider.root() as StorageResult.Success).value
            val entry = (provider.listChildren(rootEntry.ref) as StorageResult.Success).value
                .single { it.displayName == "tone.wav" }

            val source = (provider.resolvePlaybackSource(entry) as StorageResult.Success).value

            assertEquals(SeekSupport.SEEKABLE, source.seekSupport)
            assertTrue(source.transportUri.startsWith("file://"))
            assertTrue(source.transportUri.endsWith("/tone.wav"))
            assertEquals("Local storage", source.sourceLabel)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `directory is not playable`() = runBlocking {
        val root = Files.createTempDirectory("omnifile-local-playback")
        try {
            Files.createDirectories(root.resolve("Folder"))
            val provider = LocalStorageProvider(root, ProviderId("local-test"))
            val rootEntry = (provider.root() as StorageResult.Success).value
            val directory = (provider.listChildren(rootEntry.ref) as StorageResult.Success).value
                .single { it.displayName == "Folder" }

            val result = provider.resolvePlaybackSource(directory)

            assertEquals(
                StorageResult.Failure(StorageError.Unsupported),
                result,
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `missing local file resolves to not found`() = runBlocking {
        val root = Files.createTempDirectory("omnifile-local-playback")
        try {
            val file = Files.write(root.resolve("gone.wav"), ByteArray(8))
            val provider = LocalStorageProvider(root, ProviderId("local-test"))
            val rootEntry = (provider.root() as StorageResult.Success).value
            val entry = (provider.listChildren(rootEntry.ref) as StorageResult.Success).value
                .single { it.displayName == "gone.wav" }
            Files.delete(file)

            val result = provider.resolvePlaybackSource(entry)

            assertEquals(StorageResult.Failure(StorageError.NotFound), result)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `foreign provider reference is stale not playable`() = runBlocking {
        val root = Files.createTempDirectory("omnifile-local-playback")
        try {
            Files.write(root.resolve("tone.wav"), ByteArray(8))
            val provider = LocalStorageProvider(root, ProviderId("local-test"))
            val foreign = LocalStorageProvider(root, ProviderId("local-other"))
            val foreignRoot = (foreign.root() as StorageResult.Success).value
            val foreignEntry = (foreign.listChildren(foreignRoot.ref) as StorageResult.Success).value
                .single { it.displayName == "tone.wav" }

            val result = provider.resolvePlaybackSource(foreignEntry)

            assertEquals(StorageResult.Failure(StorageError.StaleReference), result)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
