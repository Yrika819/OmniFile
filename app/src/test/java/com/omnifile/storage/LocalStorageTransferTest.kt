package com.omnifile.storage

import com.omnifile.operations.DurableLocator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime

class LocalStorageTransferTest {
    @Test
    fun localTransferUsesOwnedPartialAndAtomicFinalization() = runBlocking {
        val root = Files.createTempDirectory("omnifile-transfer")
        try {
            val sourcePath = root.resolve("source.txt")
            val bytes = "bounded transfer".toByteArray()
            Files.write(sourcePath, bytes)
            val provider = LocalStorageProvider(root, ProviderId("local-transfer"))
            val transfer = provider as StorageTransferProvider
            val source = provider.root().requireSuccess().let { rootEntry ->
                provider.listChildren(rootEntry.ref).requireSuccess().single()
            }
            val sourceLocator = transfer.encodeDurableLocator(source.ref).requireSuccess()
            val destinationParent = transfer.encodeDurableLocator(provider.root().requireSuccess().ref).requireSuccess()

            val partial = transfer.createOperationPartial(destinationParent, source.displayName, "op-1")
                .requireSuccess()
            val writer = transfer.openSequentialWrite(partial, append = false, operationId = "op-1").requireSuccess()
            writer.write(bytes, 0, bytes.size)
            writer.flush()
            writer.close()

            val facts = transfer.inspectTransfer(partial).requireSuccess()
            assertEquals(EntryKind.FILE, facts.kind)
            assertEquals(bytes.size.toLong(), facts.sizeBytes)
            val finalized =
                transfer.finalizeOperationPartial(partial, destinationParent, "copy.txt", operationId = "op-1")
                    .requireSuccess()
            val finalLocator = (finalized as FinalizationResult.Finalized).finalLocator
            assertEquals("copy.txt", transfer.resolveDurableLocator(finalLocator).requireSuccess().displayName)
            assertArrayEquals(bytes, Files.readAllBytes(root.resolve("copy.txt")))
            assertTrue(Files.notExists(root.resolve(".omnifile-op-1.partial"), LinkOption.NOFOLLOW_LINKS))
            assertEquals(bytes.size.toLong(), transfer.inspectTransfer(sourceLocator).requireSuccess().sizeBytes)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun localFinalizationClassifiesExistingFinalNameAsConflict() = runBlocking {
        val root = Files.createTempDirectory("omnifile-conflict")
        try {
            Files.write(root.resolve("source.txt"), byteArrayOf(1))
            Files.write(root.resolve("copy.txt"), byteArrayOf(2))
            val provider = LocalStorageProvider(root, ProviderId("local-conflict"))
            val transfer = provider as StorageTransferProvider
            val parent = transfer.encodeDurableLocator(provider.root().requireSuccess().ref).requireSuccess()
            val partial = transfer.createOperationPartial(parent, "source.txt", "op-2").requireSuccess()
            val writer = transfer.openSequentialWrite(partial, append = false, operationId = "op-2").requireSuccess()
            writer.write(byteArrayOf(3), 0, 1)
            writer.close()

            val result = transfer.finalizeOperationPartial(partial, parent, "copy.txt", operationId = "op-2")
            assertEquals(StorageError.NameConflict("copy.txt"), result.failure().error)
            assertTrue(Files.exists(root.resolve(".omnifile-op-2.partial"), LinkOption.NOFOLLOW_LINKS))
            assertArrayEquals(byteArrayOf(2), Files.readAllBytes(root.resolve("copy.txt")))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun localDurableLocatorCannotEscapeConfiguredRoot() = runBlocking {
        val root = Files.createTempDirectory("omnifile-containment")
        try {
            val provider = LocalStorageProvider(root, ProviderId("local-containment"))
            val escaped = DurableLocator(ProviderId("local-containment"), "root-relative-v1", "../outside")
            assertEquals(StorageError.StaleReference, provider.inspectTransfer(escaped).failure().error)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun localVersionTokenIncludesFileKeySoAReplacementIsDetectable() = runBlocking {
        val root = Files.createTempDirectory("omnifile-version")
        try {
            val path = root.resolve("target.bin")
            val bytes = ByteArray(64) { 1 }
            Files.write(path, bytes)
            val fixedTime = FileTime.fromMillis(1_700_000_000_000L)
            Files.setLastModifiedTime(path, fixedTime)
            val provider = LocalStorageProvider(root, ProviderId("local-version"))
            val transfer = provider as StorageTransferProvider
            val entry = provider.listChildren(provider.root().requireSuccess().ref).requireSuccess().single()
            val locator = transfer.encodeDurableLocator(entry.ref).requireSuccess()

            val before = transfer.inspectTransfer(locator).requireSuccess().versionToken
            assertTrue("Local must always offer version evidence", before != null)

            // Replace the file at the same path with different content, then restore identical
            // size and modified time. This is exactly the case that size and mtime alone cannot
            // see, and that only the fileKey distinguishes.
            val replacement = root.resolve("replacement.bin")
            Files.write(replacement, ByteArray(64) { 2 })
            Files.setLastModifiedTime(replacement, fixedTime)
            Files.move(replacement, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            Files.setLastModifiedTime(path, fixedTime)

            val after = transfer.inspectTransfer(locator).requireSuccess().versionToken
            assertTrue(
                "a same-path, same-size, same-mtime replacement must change the version token",
                before != after,
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun localVersionTokenIsStableWhenNothingChanged() = runBlocking {
        val root = Files.createTempDirectory("omnifile-version-stable")
        try {
            Files.write(root.resolve("stable.bin"), ByteArray(32) { 3 })
            val provider = LocalStorageProvider(root, ProviderId("local-version-stable"))
            val transfer = provider as StorageTransferProvider
            val entry = provider.listChildren(provider.root().requireSuccess().ref).requireSuccess().single()
            val locator = transfer.encodeDurableLocator(entry.ref).requireSuccess()

            assertEquals(
                transfer.inspectTransfer(locator).requireSuccess().versionToken,
                transfer.inspectTransfer(locator).requireSuccess().versionToken,
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun <T> StorageResult<T>.requireSuccess(): T = when (this) {
        is StorageResult.Success -> value
        is StorageResult.Failure -> error("Expected success, got $error")
    }

    private fun <T> StorageResult<T>.failure(): StorageResult.Failure = when (this) {
        is StorageResult.Failure -> this
        is StorageResult.Success -> error("Expected failure, got $value")
    }
}
