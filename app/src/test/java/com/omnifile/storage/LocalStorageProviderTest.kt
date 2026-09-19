package com.omnifile.storage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun localRenameIsUnsupportedAndNeverExposesRenameCapability() = runBlocking {
        withFixture { root, provider, rootEntry ->
            Files.createFile(root.resolve("before.txt"))
            val oldEntry = child(provider, rootEntry, "before.txt")

            assertFalse(StorageCapability.RENAME in oldEntry.capabilities)
            val result = provider.rename(oldEntry, "after.txt")

            assertEquals(StorageError.Unsupported, result.failure().error)
            assertTrue(Files.exists(root.resolve("before.txt")))
            assertTrue(Files.notExists(root.resolve("after.txt")))
        }
    }

    @Test
    fun rootCannotBeRenamedOrDeletedAndDoesNotExposeMutationCapabilities() = runBlocking {
        withFixture { root, provider, rootEntry ->
            assertFalse(StorageCapability.RENAME in rootEntry.capabilities)
            assertFalse(StorageCapability.DELETE in rootEntry.capabilities)
            Files.createFile(root.resolve("child.txt"))
            val child = child(provider, rootEntry, "child.txt")
            assertTrue(StorageCapability.DELETE in child.capabilities)
            assertFalse(StorageCapability.RENAME in child.capabilities)
            assertEquals(StorageError.Unsupported, (provider.rename(rootEntry, "renamed").failure()).error)
            assertEquals(StorageError.Unsupported, (provider.delete(rootEntry).failure()).error)
        }
    }

    @Test
    fun deletingFileRemovesItAndMakesItsReferenceNotFound() = runBlocking {
        withFixture { root, provider, rootEntry ->
            val file = Files.createFile(root.resolve("gone.txt"))
            val entry = child(provider, rootEntry, file.fileName.toString())

            assertEquals(StorageResult.Success(Unit), provider.delete(entry))
            assertTrue(Files.notExists(file))
            assertEquals(StorageError.NotFound, provider.listChildren(entry.ref).failure().error)
        }
    }

    @Test
    fun deletingEmptyDirectoryIsAllowed() = runBlocking {
        withFixture { root, provider, rootEntry ->
            val directory = Files.createDirectory(root.resolve("empty"))
            val entry = child(provider, rootEntry, directory.fileName.toString())

            assertEquals(StorageResult.Success(Unit), provider.delete(entry))
            assertTrue(Files.notExists(directory))
        }
    }

    @Test
    fun deletingNonEmptyDirectoryIsUnsupportedAndPreservesContents() = runBlocking {
        withFixture { root, provider, rootEntry ->
            val directory = Files.createDirectory(root.resolve("nested"))
            val file = Files.createFile(directory.resolve("child.txt"))
            val entry = child(provider, rootEntry, directory.fileName.toString())

            assertFalse(StorageCapability.DELETE in entry.capabilities)
            assertFalse(StorageCapability.RENAME in entry.capabilities)
            assertEquals(StorageError.Unsupported, provider.delete(entry).failure().error)
            assertTrue(Files.exists(directory))
            assertTrue(Files.exists(file))
        }
    }

    @Test
    fun deletingUnicodeFileRemovesTheUnicodeEntry() = runBlocking {
        withFixture { root, provider, rootEntry ->
            val file = Files.createFile(root.resolve("削除対象.txt"))
            val entry = child(provider, rootEntry, file.fileName.toString())

            assertEquals(StorageResult.Success(Unit), provider.delete(entry))
            assertTrue(Files.notExists(root.resolve("削除対象.txt")))
        }
    }

    @Test
    fun staleProviderReferenceIsRejected() = runBlocking {
        withFixture { root, _, rootEntry ->
            val otherProvider: StorageProvider = LocalStorageProvider(root, ProviderId("other"))

            assertEquals(StorageError.StaleReference, otherProvider.listChildren(rootEntry.ref).failure().error)
            assertEquals(StorageError.Unsupported, otherProvider.rename(rootEntry, "renamed").failure().error)
            assertEquals(StorageError.StaleReference, otherProvider.delete(rootEntry).failure().error)
        }
    }

    @Test
    fun externallyRemovedEntryIsNotFoundForRenameAndDelete() = runBlocking {
        withFixture { root, provider, rootEntry ->
            val file = Files.createFile(root.resolve("stale.txt"))
            val entry = child(provider, rootEntry, file.fileName.toString())
            Files.delete(file)

            assertEquals(StorageError.Unsupported, provider.rename(entry, "new.txt").failure().error)
            assertEquals(StorageError.NotFound, provider.delete(entry).failure().error)
        }
    }

    @Test
    fun deletingSymlinksDoesNotFollowThemOrDeleteOutsideContainment() = runBlocking {
        withFixture { root, provider, rootEntry ->
            val outside = Files.createTempDirectory("omnifile-local-outside")
            try {
                val outsideFile = Files.createFile(outside.resolve("keep.txt"))
                val outsideDirectory = Files.createDirectory(outside.resolve("keep-directory"))
                Files.createFile(outsideDirectory.resolve("child.txt"))
                Files.createSymbolicLink(root.resolve("file-link"), outsideFile)
                Files.createSymbolicLink(root.resolve("directory-link"), outsideDirectory)

                val fileLink = child(provider, rootEntry, "file-link")
                val directoryLink = child(provider, rootEntry, "directory-link")

                assertEquals(StorageResult.Success(Unit), provider.delete(fileLink))
                assertEquals(StorageResult.Success(Unit), provider.delete(directoryLink))
                assertTrue(Files.exists(outsideFile))
                assertTrue(Files.exists(outsideDirectory.resolve("child.txt")))
                assertTrue(Files.notExists(root.resolve("file-link")))
                assertTrue(Files.notExists(root.resolve("directory-link")))
            } finally {
                outside.deleteRecursively()
            }
        }
    }

    @Test
    fun replacingConfiguredRootWithOutsideSymlinkBlocksCapturedNestedDelete() = runBlocking {
        val root = Files.createTempDirectory("omnifile-local-root-")
        val outside = Files.createTempDirectory("omnifile-local-outside-")
        val relocatedRoot = root.resolveSibling("${root.fileName}-relocated")
        try {
            val nested = Files.createDirectories(root.resolve("nested"))
            val capturedFile = Files.createFile(nested.resolve("captured.txt"))
            val outsideNested = Files.createDirectories(outside.resolve("nested"))
            val outsideFile = Files.createFile(outsideNested.resolve("captured.txt"))
            val provider: StorageProvider = LocalStorageProvider(root, ProviderId("local-test"))
            val rootEntry = (provider.root() as StorageResult.Success).value
            val capturedEntry = child(provider, child(provider, rootEntry, "nested"), "captured.txt")

            Files.move(root, relocatedRoot)
            Files.createSymbolicLink(root, outside)

            val rootResult = provider.root()
            assertTrue(rootResult is StorageResult.Failure)
            if (rootResult is StorageResult.Failure) {
                assertEquals(StorageError.Unsupported, rootResult.error)
            }
            val deleteResult = provider.delete(capturedEntry)
            assertTrue(deleteResult is StorageResult.Failure)
            assertTrue(Files.exists(outsideFile))
            assertTrue(Files.exists(relocatedRoot.resolve("nested/captured.txt")))
        } finally {
            Files.deleteIfExists(root)
            relocatedRoot.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun replacingNestedAncestorWithOutsideSymlinkCannotRedirectCapturedDelete() = runBlocking {
        withFixture { root, provider, rootEntry ->
            val nested = Files.createDirectory(root.resolve("nested"))
            val capturedFile = Files.createFile(nested.resolve("captured.txt"))
            val outside = Files.createTempDirectory("omnifile-local-outside-")
            val displacedNested = root.resolveSibling("${root.fileName}-nested-displaced")
            try {
                val outsideFile = Files.createFile(outside.resolve("captured.txt"))
                val capturedEntry = child(provider, child(provider, rootEntry, "nested"), "captured.txt")

                Files.move(nested, displacedNested)
                Files.createSymbolicLink(root.resolve("nested"), outside)

                assertTrue(provider.delete(capturedEntry) is StorageResult.Failure)
                assertTrue(Files.exists(outsideFile))
                assertTrue(Files.exists(displacedNested.resolve("captured.txt")))
            } finally {
                Files.deleteIfExists(root.resolve("nested"))
                displacedNested.deleteRecursively()
                outside.deleteRecursively()
            }
        }
    }

    @Test
    fun mutationTargetRemainsContainedInTheEntryParent() = runBlocking {
        withFixture { root, provider, rootEntry ->
            val directory = Files.createDirectory(root.resolve("folder"))
            val file = Files.createFile(directory.resolve("entry.txt"))
            val entry = child(provider, child(provider, rootEntry, "folder"), file.fileName.toString())

            assertEquals(StorageError.Unsupported, provider.rename(entry, "../outside.txt").failure().error)
            assertTrue(Files.exists(file))
            assertTrue(Files.notExists(root.resolve("outside.txt")))
        }
    }

    private suspend fun withFixture(block: suspend (Path, StorageProvider, StorageEntry) -> Unit) {
        val root = Files.createTempDirectory("omnifile-local-test-")
        try {
            val provider: StorageProvider = LocalStorageProvider(root, ProviderId("local-test"))
            val rootEntry = (provider.root() as StorageResult.Success).value
            block(root, provider, rootEntry)
        } finally {
            root.deleteRecursively()
        }
    }

    private suspend fun child(provider: StorageProvider, parent: StorageEntry, name: String): StorageEntry =
        (provider.listChildren(parent.ref) as StorageResult.Success).value.single { it.displayName == name }

    private suspend fun names(provider: StorageProvider, parent: EntryRef): List<String> =
        (provider.listChildren(parent) as StorageResult.Success).value.map { it.displayName }

    private fun StorageResult<*>.failure(): StorageResult.Failure = this as StorageResult.Failure

    private fun Path.deleteRecursively() {
        if (Files.notExists(this)) return
        Files.walk(this).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
