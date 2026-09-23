package com.omnifile.archive

import com.omnifile.files.FilesRepository
import com.omnifile.storage.EntryKind
import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import com.omnifile.preview.PreviewEngine
import com.omnifile.preview.PreviewItem
import com.omnifile.preview.PreviewPayload
import com.omnifile.preview.PreviewRequest
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
        ) { repository, archive, _ ->
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
        ) { repository, archive, _ ->
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
        withArchive(bytes.copyOf(bytes.size / 2)) { repository, archive, _ ->
            val result = repository.open(archive)
            assertEquals(ArchiveError.Corrupt, (result as ArchiveOpenResult.Failure).error)
        }
    }

    @Test
    fun nonZipFileIsNotRecognized() = runBlocking {
        withArchive("not zip".toByteArray(), name = "notes.txt") { repository, archive, _ ->
            assertEquals(ArchiveError.NotAnArchive, (repository.open(archive) as ArchiveOpenResult.Failure).error)
        }
    }

    @Test
    fun duplicateArchiveNamesPreviewTheirDistinctValidatedOrdinals() = runBlocking {
        val bytes = renameZipEntry(zipOf(
            ZipSpec("same.txt", "first record".toByteArray()),
            ZipSpec("other.tx", "second record".toByteArray()),
        ))
        withArchive(bytes) { repository, archive, _ ->
            val document = assertSuccess(repository.open(archive)).document
            val duplicates = document.list().filter { it.displayName == "same.txt" }
            assertEquals(2, duplicates.size)
            val payloads = duplicates.map { node ->
                val source = (repository.previewSource(document, node) as StorageResult.Success).value
                val item = PreviewItem(node.ref, node.displayName, node.path.toString(), null, node.sizeBytes)
                PreviewEngine().load(PreviewRequest(item, source)) as PreviewPayload.Text
            }
            assertEquals(listOf("first record", "second record"), payloads.map { it.content })
        }
    }

    @Test
    fun archiveTextPreviewUsesTheSharedByteBoundAndReportsTruncation() = runBlocking {
        val text = ByteArray(300 * 1024) { 'x'.code.toByte() }
        withArchive(zipOf(ZipSpec("large.txt", text))) { repository, archive, _ ->
            val document = assertSuccess(repository.open(archive)).document
            val node = document.list().single()
            val source = (repository.previewSource(document, node) as StorageResult.Success).value
            val item = PreviewItem(node.ref, node.displayName, node.path.toString(), null, node.sizeBytes)
            val payload = PreviewEngine().load(PreviewRequest(item, source)) as PreviewPayload.Text
            assertTrue(payload.truncated)
            assertEquals(com.omnifile.preview.PreviewLimits.MAX_TEXT_BYTES, payload.bytesRead)
        }
    }

    @Test
    fun changedCompressionMethodMakesIndexedArchiveEntryStale() = runBlocking {
        val payload = "same archive entry".toByteArray()
        withArchive(storedZip("entry.txt", payload)) { repository, archive, root ->
            val document = assertSuccess(repository.open(archive)).document
            val node = document.list().single()
            Files.write(root.resolve(archive.displayName), zipOf(ZipSpec("entry.txt", payload)))

            val source = (repository.previewSource(document, node) as StorageResult.Success).value
            val result = source.open()

            assertEquals(StorageError.StaleReference, (result as StorageResult.Failure).error)
        }
    }

    private suspend fun withArchive(
        bytes: ByteArray,
        name: String = "fixture.zip",
        block: suspend (ArchiveRepository, StorageEntry, java.nio.file.Path) -> Unit,
    ) {
        val root = Files.createTempDirectory("omnifile-archive-open")
        try {
            Files.write(root.resolve(name), bytes)
            val provider = LocalStorageProvider(root, ProviderId("archive-open"))
            val rootEntry = requireSuccess(provider.root())
            val archive = requireSuccess(provider.listChildren(rootEntry.ref)).single()
            block(ArchiveRepository(FilesRepository(mapOf(provider.id to provider))), archive, root)
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

    private fun storedZip(name: String, content: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            val crc = java.util.zip.CRC32().apply { update(content) }
            zip.putNextEntry(ZipEntry(name).apply {
                method = ZipEntry.STORED
                size = content.size.toLong()
                compressedSize = content.size.toLong()
                this.crc = crc.value
            })
            zip.write(content)
            zip.closeEntry()
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
