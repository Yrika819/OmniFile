package com.omnifile.archive

import com.omnifile.files.FilesRepository
import com.omnifile.storage.EntryKind
import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageResult
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveRepositoryTest {
    @Test
    fun browsePreservesNestedEmptyUnicodeAndDuplicateIdentity() = runBlocking {
        withArchive(
            zipOf(
                ZipSpec("empty/", null),
                ZipSpec("folder/one.txt", "one".toByteArray()),
                ZipSpec("folder/two.txt", "two".toByteArray()),
                ZipSpec("same.txt", byteArrayOf(1)),
                ZipSpec("other.tx", byteArrayOf(2)),
                ZipSpec("日本語.txt", "unicode".toByteArray()),
            ).let(::renameZipEntry),
        ) { repository, archive ->
            val opened = repository.open(archive)
            val document = assertSuccess(opened).document
            assertEquals(6, document.entryCount)
            val root = document.list()
            assertEquals(listOf("empty", "folder", "same.txt", "same.txt", "日本語.txt"), root.map { it.displayName })
            assertEquals(2, root.filter { it.displayName == "same.txt" }.map { it.ref.identityKey }.toSet().size)
            assertEquals(EntryKind.DIRECTORY, root.first { it.displayName == "folder" }.kind)
            assertEquals(listOf("one.txt", "two.txt"), document.list(root.first { it.displayName == "folder" }.path).map { it.displayName })
            assertEquals(EntryKind.DIRECTORY, root.first { it.displayName == "empty" }.kind)
        }
    }

    @Test
    fun unsafeNamesRemainVisibleButAreNotExtractable() = runBlocking {
        withArchive(
            zipOf(
                ZipSpec("../escape.txt", byteArrayOf(1)),
                ZipSpec("/absolute.txt", byteArrayOf(2)),
                ZipSpec("C:/drive.txt", byteArrayOf(3)),
                ZipSpec("mixed\\separator.txt", byteArrayOf(4)),
            ),
        ) { repository, archive ->
            val document = assertSuccess(repository.open(archive)).document
            val unsafe = document.list()
            assertEquals(4, unsafe.size)
            assertTrue(unsafe.all { it.status == ArchiveEntryStatus.UNSAFE_PATH })
            assertTrue(unsafe.all { !it.isExtractable })
            assertTrue(unsafe.all { it.ref.identityKey.contains("ordinal:") })
        }
    }

    @Test
    fun truncatedZipIsReportedAsCorrupt() = runBlocking {
        val bytes = zipOf(ZipSpec("file.txt", "payload".toByteArray()))
        withArchive(bytes.copyOf(bytes.size / 2)) { repository, archive ->
            val result = repository.open(archive)
            assertEquals(ArchiveError.Corrupt, (result as ArchiveOpenResult.Failure).error)
        }
    }

    @Test
    fun nonZipFileIsNotRecognized() = runBlocking {
        withArchive("not zip".toByteArray(), name = "notes.txt") { repository, archive ->
            assertEquals(ArchiveError.NotAnArchive, (repository.open(archive) as ArchiveOpenResult.Failure).error)
        }
    }

    private suspend fun withArchive(
        bytes: ByteArray,
        name: String = "fixture.zip",
        block: suspend (ArchiveRepository, StorageEntry) -> Unit,
    ) {
        val root = Files.createTempDirectory("omnifile-archive-open")
        try {
            Files.write(root.resolve(name), bytes)
            val provider = LocalStorageProvider(root, ProviderId("archive-open"))
            val rootEntry = requireSuccess(provider.root())
            val archive = requireSuccess(provider.listChildren(rootEntry.ref)).single()
            block(ArchiveRepository(FilesRepository(mapOf(provider.id to provider))), archive)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private data class ZipSpec(val name: String, val bytes: ByteArray?)

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

    private fun zipOf(vararg specs: ZipSpec): ByteArray = ByteArrayOutputStream().use { output ->
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

    private fun assertSuccess(result: ArchiveOpenResult): ArchiveOpenResult.Success =
        result as? ArchiveOpenResult.Success ?: error("Expected archive success, got $result")
}
