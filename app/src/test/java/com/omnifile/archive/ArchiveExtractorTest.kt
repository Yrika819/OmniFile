package com.omnifile.archive

import com.omnifile.files.FilesRepository
import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageResult
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveExtractorTest {
    @Test
    fun extractsNestedSelectionWithPartialFinalization() = runBlocking {
        withFixture(
            zipOf(
                Spec("folder/", null),
                Spec("folder/one.txt", "one".toByteArray()),
                Spec("folder/empty/", null),
            ),
        ) { provider, archive, repository ->
            val document = (repository.open(archive) as ArchiveOpenResult.Success).document
            val folder = document.list().single { it.displayName == "folder" }
            val destination = requireSuccess(provider.root())
            val result = ArchiveExtractor(FilesRepository(mapOf(provider.id to provider))).extract(
                document,
                listOf(folder),
                provider,
                destination,
            )
            assertEquals(ArchiveExtractionResult.Success(files = 1, bytes = 3), result)
            val root = providerRoot(provider)
            assertArrayEquals("one".toByteArray(), Files.readAllBytes(root.resolve("folder/one.txt")))
            assertTrue(Files.isDirectory(root.resolve("folder/empty")))
        }
    }

    @Test
    fun duplicateArchiveTargetsFailBeforeWriting() = runBlocking {
        withFixture(renameZipEntry(zipOf(Spec("same.txt", byteArrayOf(1)), Spec("other.tx", byteArrayOf(2))))) { provider, archive, repository ->
            val document = (repository.open(archive) as ArchiveOpenResult.Success).document
            val selections = document.list().filter { it.displayName == "same.txt" }
            val result = ArchiveExtractor(FilesRepository(mapOf(provider.id to provider))).extract(
                document,
                selections,
                provider,
                requireSuccess(provider.root()),
            )
            assertEquals(
                ArchiveExtractionError.DuplicateTarget("same.txt"),
                (result as ArchiveExtractionResult.Failure).error,
            )
            assertFalse(Files.exists(providerRoot(provider).resolve("same.txt")))
        }
    }

    @Test
    fun canonicallyEquivalentNamesFailAsOneNormalizedTarget() = runBlocking {
        withFixture(zipOf(Spec("e\u0301.txt", byteArrayOf(1)), Spec("é.txt", byteArrayOf(2)))) { provider, archive, repository ->
            val document = (repository.open(archive) as ArchiveOpenResult.Success).document
            val result = ArchiveExtractor(FilesRepository(mapOf(provider.id to provider))).extract(
                document,
                document.list(),
                provider,
                requireSuccess(provider.root()),
            )
            assertEquals(
                ArchiveExtractionError.DuplicateTarget("é.txt"),
                (result as ArchiveExtractionResult.Failure).error,
            )
            assertFalse(Files.exists(providerRoot(provider).resolve("é.txt")))
        }
    }

    @Test
    fun existingDestinationIsAnExplicitConflictAndNeverOverwritten() = runBlocking {
        withFixture(zipOf(Spec("same.txt", byteArrayOf(1)))) { provider, archive, repository ->
            val root = providerRoot(provider)
            Files.write(root.resolve("same.txt"), byteArrayOf(9))
            val document = (repository.open(archive) as ArchiveOpenResult.Success).document
            val result = ArchiveExtractor(FilesRepository(mapOf(provider.id to provider))).extract(
                document,
                listOf(document.list().single()),
                provider,
                requireSuccess(provider.root()),
            )
            assertEquals(ArchiveExtractionError.DestinationConflict("same.txt"), (result as ArchiveExtractionResult.Failure).error)
            assertArrayEquals(byteArrayOf(9), Files.readAllBytes(root.resolve("same.txt")))
        }
    }

    @Test
    fun unsafeSelectionFailsAndCannotEscapeDestination() = runBlocking {
        withFixture(zipOf(Spec("../outside.txt", byteArrayOf(1)))) { provider, archive, repository ->
            val document = (repository.open(archive) as ArchiveOpenResult.Success).document
            val result = ArchiveExtractor(FilesRepository(mapOf(provider.id to provider))).extract(
                document,
                listOf(document.list().single()),
                provider,
                requireSuccess(provider.root()),
            )
            assertTrue((result as ArchiveExtractionResult.Failure).error is ArchiveExtractionError.UnsafePath)
            assertFalse(Files.exists(providerRoot(provider).parent.resolve("outside.txt")))
        }
    }

    @Test
    fun sourceTruncationAfterIndexIsRejectedAndOutputIsRolledBack() = runBlocking {
        val full = zipOf(Spec("file.txt", "payload".toByteArray()))
        withFixture(full) { provider, archive, repository ->
            val document = (repository.open(archive) as ArchiveOpenResult.Success).document
            val root = providerRoot(provider)
            val eocd = full.indexOfSignature(byteArrayOf(0x50, 0x4b, 0x05, 0x06))
            Files.write(root.resolve("fixture.zip"), full.copyOf(eocd))
            val result = ArchiveExtractor(FilesRepository(mapOf(provider.id to provider))).extract(
                document,
                listOf(document.list().single()),
                provider,
                requireSuccess(provider.root()),
            )
            assertTrue((result as ArchiveExtractionResult.Failure).error is ArchiveExtractionError.Source)
            assertTrue(result.cleanupComplete)
            assertFalse(Files.exists(root.resolve("file.txt")))
        }
    }

    @Test
    fun resourceLimitRollsBackOperationPartials() = runBlocking {
        withFixture(zipOf(Spec("large.txt", ByteArray(128) { 7 }))) { provider, archive, repository ->
            val document = (repository.open(archive) as ArchiveOpenResult.Success).document
            val result = ArchiveExtractor(
                FilesRepository(mapOf(provider.id to provider)),
                ArchiveExtractor.Limits(maxEntryBytes = 16, maxTotalBytes = 16),
            ).extract(document, listOf(document.list().single()), provider, requireSuccess(provider.root()))
            assertTrue((result as ArchiveExtractionResult.Failure).error is ArchiveExtractionError.ResourceLimit)
            assertTrue(result.cleanupComplete)
            assertTrue(Files.list(providerRoot(provider)).use { it.noneMatch { path -> path.fileName.toString().contains(".partial") } })
        }
    }

    private suspend fun withFixture(
        bytes: ByteArray,
        block: suspend (LocalStorageProvider, StorageEntry, ArchiveRepository) -> Unit,
    ) {
        val root = Files.createTempDirectory("omnifile-archive-extract")
        try {
            Files.write(root.resolve("fixture.zip"), bytes)
            val provider = LocalStorageProvider(root, ProviderId("archive-extract"))
            val rootEntry = requireSuccess(provider.root())
            val archive = requireSuccess(provider.listChildren(rootEntry.ref)).single()
            val repository = ArchiveRepository(FilesRepository(mapOf(provider.id to provider)))
            block(provider, archive, repository)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun providerRoot(provider: LocalStorageProvider): java.nio.file.Path {
        val entry = runBlocking { requireSuccess(provider.root()) }
        return java.nio.file.Paths.get(entry.ref.identityKey.substringAfter('\u0000'))
    }

    private data class Spec(val name: String, val bytes: ByteArray?)

    private fun ByteArray.indexOfSignature(signature: ByteArray): Int {
        for (index in 0..size - signature.size) {
            if (signature.indices.all { this[index + it] == signature[it] }) return index
        }
        error("signature not found")
    }

    private fun renameZipEntry(bytes: ByteArray): ByteArray {
        val oldName = "other.tx".toByteArray()
        val newName = "same.txt".toByteArray()
        val result = bytes.copyOf()
        for (index in 0..result.size - oldName.size) {
            if (oldName.indices.all { result[index + it] == oldName[it] }) {
                newName.copyInto(result, index)
            }
        }
        return result
    }

    private fun zipOf(vararg specs: Spec): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            specs.forEach { spec ->
                zip.putNextEntry(ZipEntry(spec.name))
                spec.bytes?.let(zip::write)
                zip.closeEntry()
            }
        }
        output.toByteArray()
    }

    private fun <T> requireSuccess(result: StorageResult<T>): T = when (result) {
        is StorageResult.Success -> result.value
        is StorageResult.Failure -> error("Expected success, got ${result.error}")
    }
}
