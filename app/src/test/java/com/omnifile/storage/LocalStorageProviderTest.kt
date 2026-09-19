package com.omnifile.storage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStorageProviderTest {
    @Test
    fun listsOnlyDirectChildrenInDeterministicNameOrder() = runBlocking {
        val root = Files.createTempDirectory("omnifile-local")
        try {
            Files.createDirectories(root.resolve("Folder"))
            Files.createFile(root.resolve("alpha.txt"))
            Files.createFile(root.resolve("日本語.txt"))
            Files.createFile(root.resolve("zeta.txt"))

            val provider = LocalStorageProvider(root, ProviderId("local-test"))
            val rootEntry = (provider.root() as StorageResult.Success).value
            val children = (provider.listChildren(rootEntry.ref) as StorageResult.Success).value

            assertEquals(listOf("Folder", "alpha.txt", "zeta.txt", "日本語.txt"), children.map { it.displayName })
            assertEquals(4, children.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun exposesKnownFileMetadataAndDirectoryCapabilityWithoutRecursing() = runBlocking {
        val root = Files.createTempDirectory("omnifile-local")
        try {
            val file = Files.createFile(root.resolve("one.txt"))
            Files.setLastModifiedTime(file, FileTime.fromMillis(1234))
            Files.createDirectories(root.resolve("empty"))

            val provider = LocalStorageProvider(root, ProviderId("local-test"))
            val rootEntry = (provider.root() as StorageResult.Success).value
            val children = (provider.listChildren(rootEntry.ref) as StorageResult.Success).value
            val fileEntry = children.single { it.displayName == "one.txt" }
            val emptyEntry = children.single { it.displayName == "empty" }

            assertEquals(0L, fileEntry.sizeBytes)
            assertEquals(1234L, fileEntry.modifiedAtEpochMillis)
            assertTrue(StorageCapability.READ_SEQUENTIAL in fileEntry.capabilities)
            assertTrue(StorageCapability.READ_SEEKABLE in fileEntry.capabilities)
            assertEquals(EntryKind.DIRECTORY, emptyEntry.kind)
            assertTrue(StorageCapability.LIST_CHILDREN in emptyEntry.capabilities)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deletedReferenceBecomesNotFound() = runBlocking {
        val root = Files.createTempDirectory("omnifile-local")
        try {
            val file = Files.createFile(root.resolve("gone.txt"))
            val provider = LocalStorageProvider(root, ProviderId("local-test"))
            val rootEntry = (provider.root() as StorageResult.Success).value
            val entry = (provider.listChildren(rootEntry.ref) as StorageResult.Success).value.single()
            Files.delete(file)

            val result = provider.listChildren(entry.ref)

            assertEquals(StorageError.NotFound, (result as StorageResult.Failure).error)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun samePathInDifferentProvidersHasDifferentScopedIdentity() = runBlocking {
        val root = Files.createTempDirectory("omnifile-local")
        try {
            val first = LocalStorageProvider(root, ProviderId("local-first"))
            val second = LocalStorageProvider(root, ProviderId("local-second"))

            val firstRoot = (first.root() as StorageResult.Success).value.ref
            val secondRoot = (second.root() as StorageResult.Success).value.ref

            assertTrue(firstRoot != secondRoot)
            assertEquals(ProviderId("local-first"), firstRoot.providerId)
            assertEquals(ProviderId("local-second"), secondRoot.providerId)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun Path.deleteRecursively() {
        if (Files.notExists(this)) return
        Files.walk(this).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
