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
    fun renamingFileReturnsNewIdentityAndListingTruth() = runBlocking {
        withFixture { root, provider, rootEntry ->
            Files.createFile(root.resolve("before.txt"))
            val oldEntry = child(provider, rootEntry, "before.txt")

            val result = provider.rename(oldEntry, "after.txt")

            val renamed = (result as StorageResult.Success).value
            assertTrue(renamed.ref != oldEntry.ref)
            assertEquals("after.txt", renamed.displayName)
            assertTrue(Files.notExists(root.resolve("before.txt")))
            assertTrue(Files.exists(root.resolve("after.txt")))
            assertEquals(listOf("after.txt"), names(provider, rootEntry.ref))
        }
    }

    @Test
    fun renamingDirectoryPreservesChildrenAndReturnsDirectoryEntry() = runBlocking {
        withFixture { root, provider, rootEntry ->
            val before = Files.createDirectories(root.resolve("before"))
            Files.createFile(before.resolve("child.txt"))
            val oldEntry = child(provider, rootEntry, "before")

            val result = provider.rename(oldEntry, "after")

            val renamed = (result as StorageResult.Success).value
            assertEquals(EntryKind.DIRECTORY, renamed.kind)
            assertEquals(listOf("child.txt"), names(provider, renamed.ref))
            assertTrue(Files.notExists(root.resolve("before")))
            assertTrue(Files.exists(root.resolve("after/child.txt")))
        }
    }

    @Test
    fun renamingUnicodeEntryPreservesUnicodeName() = runBlocking {
        withFixture { root, provider, rootEntry ->
            Files.createFile(root.resolve("古い名前.txt"))
            val oldEntry = child(provider, rootEntry, "古い名前.txt")

            val result = provider.rename(oldEntry, "新しい名前.txt")

            assertEquals("新しい名前.txt", (result as StorageResult.Success).value.displayName)
            assertTrue(Files.exists(root.resolve("新しい名前.txt")))
        }
    }

    @Test
    fun renamingRejectsEmptySeparatorNulDotAndDotDotNames() = runBlocking {
        withFixture { root, provider, rootEntry ->
            listOf("", "nested/name", "nul\u0000name", ".", "..").forEachIndexed { index, invalidName ->
                val file = Files.createFile(root.resolve("source-$index.txt"))
                val entry = child(provider, rootEntry, file.fileName.toString())

                val result = provider.rename(entry, invalidName)

                assertEquals(
                    StorageError.InvalidName(invalidName, ""),
                    (result as StorageResult.Failure).error,
                )
            }
        }
    }

    @Test
    fun renamingRejectsExistingTargetWithoutChangingEitherEntry() = runBlocking {
        withFixture { root, provider, rootEntry ->
            Files.createFile(root.resolve("source.txt"))
            Files.createFile(root.resolve("target.txt"))
            val source = child(provider, rootEntry, "source.txt")

            val result = provider.rename(source, "target.txt")

            assertEquals(StorageError.NameConflict("target.txt"), (result as StorageResult.Failure).error)
            assertTrue(Files.exists(root.resolve("source.txt")))
            assertTrue(Files.exists(root.resolve("target.txt")))
            assertEquals(listOf("source.txt", "target.txt"), names(provider, rootEntry.ref))
        }
    }

    @Test
    fun rootCannotBeRenamedOrDeletedAndDoesNotExposeMutationCapabilities() = runBlocking {
        withFixture { root, provider, rootEntry ->
            assertFalse(StorageCapability.RENAME in rootEntry.capabilities)
            assertFalse(StorageCapability.DELETE in rootEntry.capabilities)
            Files.createFile(root.resolve("child.txt"))
            val child = child(provider, rootEntry, "child.txt")
            assertTrue(StorageCapability.RENAME in child.capabilities)
            assertTrue(StorageCapability.DELETE in child.capabilities)
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

            assertTrue(StorageCapability.RENAME in entry.capabilities)
            assertFalse(StorageCapability.DELETE in entry.capabilities)
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
            assertEquals(StorageError.StaleReference, otherProvider.rename(rootEntry, "renamed").failure().error)
            assertEquals(StorageError.StaleReference, otherProvider.delete(rootEntry).failure().error)
        }
    }

    @Test
    fun externallyRemovedEntryIsNotFoundForRenameAndDelete() = runBlocking {
        withFixture { root, provider, rootEntry ->
            val file = Files.createFile(root.resolve("stale.txt"))
            val entry = child(provider, rootEntry, file.fileName.toString())
            Files.delete(file)

            assertEquals(StorageError.NotFound, provider.rename(entry, "new.txt").failure().error)
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
    fun mutationTargetRemainsContainedInTheEntryParent() = runBlocking {
        withFixture { root, provider, rootEntry ->
            val directory = Files.createDirectory(root.resolve("folder"))
            val file = Files.createFile(directory.resolve("entry.txt"))
            val entry = child(provider, child(provider, rootEntry, "folder"), file.fileName.toString())

            assertEquals(StorageError.InvalidName("../outside.txt", ""), provider.rename(entry, "../outside.txt").failure().error)
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
