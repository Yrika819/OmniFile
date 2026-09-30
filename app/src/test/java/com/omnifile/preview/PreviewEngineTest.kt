package com.omnifile.preview

import com.omnifile.files.FilesRepository
import com.omnifile.storage.EntryRef
import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.ProviderId
import com.omnifile.storage.SequentialReadHandle
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewEngineTest {
    @Test
    fun largeTextReadsOnlyTheExplicitByteBoundAndReportsTruncation() = runBlocking {
        val request = request(ByteArray(PreviewLimits.MAX_TEXT_BYTES + 8 * 1024) { 'x'.code.toByte() })
        val result = PreviewEngine().load(request) as PreviewPayload.Text

        assertEquals(PreviewLimits.MAX_TEXT_BYTES, result.bytesRead)
        assertEquals(PreviewLimits.MAX_TEXT_CHARACTERS, result.content.length)
        assertTrue(result.truncated)
    }

    @Test
    fun emptyTxtProducesAnEmptyNonTruncatedTextPreview() = runBlocking {
        val result = PreviewEngine().load(request(byteArrayOf())) as PreviewPayload.Text
        assertEquals("", result.content)
        assertFalse(result.truncated)
    }

    @Test
    fun providerUnavailableIsKeptDistinctFromUnsupported() = runBlocking {
        val item = item()
        val source = PreviewSource(item.identity, "Selected storage", null, null, PreviewCapabilities(true, true)) {
            StorageResult.Failure(StorageError.ProviderUnavailable)
        }
        val result = PreviewEngine().load(PreviewRequest(item, source)) as PreviewPayload.Failure
        assertEquals(PreviewError.ProviderUnavailable, result.error)
    }

    @Test
    fun repeatedZeroProgressReadsBecomeTypedIoFailure() = runBlocking {
        val item = item()
        var reads = 0
        val source = PreviewSource(item.identity, "Test source", "text/plain", null, PreviewCapabilities(true, true)) {
            StorageResult.Success(object : SequentialReadHandle {
                override val expectedBytes: Long? = null
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    reads++
                    return 0
                }
                override fun close() = Unit
            })
        }

        val result = PreviewEngine().load(PreviewRequest(item, source)) as PreviewPayload.Failure

        assertEquals(PreviewError.IoFailure("Provider read made no progress"), result.error)
        assertEquals(PreviewLimits.MAX_CONSECUTIVE_NO_PROGRESS_READS + 1, reads)
    }

    @Test
    fun largeTextCanBeReadFromASingleOpenSequentialSource() = runBlocking {
        val item = item()
        val bytes = ByteArray(12_000) { 'x'.code.toByte() }
        var opens = 0
        val source = PreviewSource(item.identity, "Single-open source", "text/plain", bytes.size.toLong(), PreviewCapabilities(true, false)) {
            opens++
            if (opens > 1) {
                StorageResult.Failure(StorageError.Unsupported)
            } else {
                var readOffset = 0
                StorageResult.Success(object : SequentialReadHandle {
                    override val expectedBytes: Long = bytes.size.toLong()
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        if (readOffset >= bytes.size) return -1
                        val count = minOf(length, bytes.size - readOffset)
                        bytes.copyInto(buffer, offset, readOffset, readOffset + count)
                        readOffset += count
                        return count
                    }
                    override fun close() = Unit
                })
            }
        }

        val result = PreviewEngine().load(PreviewRequest(item, source))

        assertEquals(1, opens)
        assertEquals(bytes.size, (result as PreviewPayload.Text).bytesRead)
        assertEquals(bytes.size, result.content.length)
    }

    @Test
    fun localAdapterOpensAProviderOwnedSequentialPreviewSource() = runBlocking {
        val root = Files.createTempDirectory("omnifile-preview-local")
        try {
            Files.write(root.resolve("sample.txt"), "local preview".toByteArray())
            val provider = LocalStorageProvider(root, ProviderId("local-preview-test"))
            val rootEntry = (provider.root() as StorageResult.Success).value
            val entry = (provider.listChildren(rootEntry.ref) as StorageResult.Success).value.single()
            val repository = FilesRepository(mapOf(provider.id to provider))
            val source = (repository.previewSource(entry) as StorageResult.Success).value
            assertEquals(entry.ref.identityKey, source.identity.identityKey)
            assertEquals("Local storage", source.sourceLabel)
            assertTrue(source.capabilities.canReopen)
            val result = PreviewEngine().load(PreviewRequest(item(entry), source)) as PreviewPayload.Text
            assertEquals("local preview", result.content)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun request(bytes: ByteArray): PreviewRequest {
        val item = item()
        var readOffset = 0
        val source = PreviewSource(item.identity, "Test source", "text/plain", bytes.size.toLong(), PreviewCapabilities(true, true)) {
            readOffset = 0
            StorageResult.Success(object : SequentialReadHandle {
                override val expectedBytes: Long = bytes.size.toLong()
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (readOffset >= bytes.size) return -1
                    val size = minOf(length, bytes.size - readOffset)
                    bytes.copyInto(buffer, offset, readOffset, readOffset + size)
                    readOffset += size
                    return size
                }
                override fun close() = Unit
            })
        }
        return PreviewRequest(item, source)
    }

    private fun item(entry: StorageEntry? = null): PreviewItem {
        val identity = entry?.ref ?: TestRef(ProviderId("preview-test"), "id:sample")
        return PreviewItem(identity, entry?.displayName ?: "sample.txt", "Tests", entry?.mimeType ?: "text/plain", entry?.sizeBytes)
    }

    private data class TestRef(override val providerId: ProviderId, override val identityKey: String) : EntryRef
}
