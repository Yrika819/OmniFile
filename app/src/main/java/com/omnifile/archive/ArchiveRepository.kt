package com.omnifile.archive

import com.omnifile.files.FilesRepository
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.SequentialReadHandle
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import com.omnifile.preview.PreviewCapabilities
import com.omnifile.preview.PreviewSource
import kotlinx.coroutines.ensureActive

class ArchiveRepository(
    private val filesRepository: FilesRepository,
) {
    suspend fun open(container: StorageEntry): ArchiveOpenResult {
        if (!ArchiveSupport.isZip(container)) return ArchiveOpenResult.Failure(ArchiveError.NotAnArchive)
        val archive = ArchiveContainer(container)
        return ZipArchiveReader.read(
            open = { filesRepository.openSequentialRead(container) },
            container = archive,
        )
    }

    /** Reopens a validated record by ordinal and streams it without extracting to a destination. */
    fun previewSource(document: ArchiveDocument, node: ArchiveNode): StorageResult<PreviewSource> {
        if (node.kind != com.omnifile.storage.EntryKind.FILE || node.status != ArchiveEntryStatus.SUPPORTED ||
            node.ref.providerId != document.container.source.ref.providerId ||
            node.ref.archiveIdentity != document.container.identityKey
        ) return StorageResult.Failure(StorageError.Unsupported)
        val parent = ArchivePath(node.path.segments().dropLast(1))
        if (document.list(parent).none { it.ref == node.ref }) return StorageResult.Failure(StorageError.StaleReference)
        val ordinal = node.sourceOrdinals.singleOrNull() ?: return StorageResult.Failure(StorageError.Unsupported)
        val record = document.records.singleOrNull { it.ordinal == ordinal }
            ?: return StorageResult.Failure(StorageError.StaleReference)
        if (record.status != ArchiveEntryStatus.SUPPORTED || record.kind != com.omnifile.storage.EntryKind.FILE ||
            record.safePath != node.path || record.rawName.isEmpty() || record.method !in setOf(0, 8)
        ) return StorageResult.Failure(StorageError.Unsupported)

        return StorageResult.Success(
            PreviewSource(
                identity = node.ref,
                sourceLabel = "ZIP archive · ${node.path}",
                mimeType = null,
                sizeBytes = record.actualSizeBytes,
                capabilities = PreviewCapabilities(sequentialReadable = true, canReopen = true),
            ) {
                openEntry(document.container.source, record)
            },
        )
    }

    private suspend fun openEntry(container: StorageEntry, target: ArchiveRecord): StorageResult<SequentialReadHandle> {
        val source = when (val result = filesRepository.openSequentialRead(container)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return result
        }
        val zip = java.util.zip.ZipInputStream(java.io.BufferedInputStream(ArchiveHandleInputStream(source), 16 * 1024))
        try {
            var skippedBytes = 0L
            for (ordinal in 0..target.ordinal) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val entry = try {
                    zip.nextEntry
                } catch (error: java.util.zip.ZipException) {
                    zip.close()
                    return StorageResult.Failure(StorageError.IoFailure("Malformed ZIP entry"))
                } ?: run {
                    zip.close()
                    return StorageResult.Failure(StorageError.IoFailure("ZIP entry disappeared"))
                }
                if (ordinal == target.ordinal) {
                    if (entry.name != target.rawName || entry.method != target.method) {
                        zip.close()
                        return StorageResult.Failure(StorageError.StaleReference)
                    }
                    return StorageResult.Success(ArchiveEntryReadHandle(zip, target.actualSizeBytes))
                }
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val count = try {
                        zip.read(buffer)
                    } catch (error: java.util.zip.ZipException) {
                        zip.close()
                        return StorageResult.Failure(StorageError.IoFailure("Malformed ZIP entry"))
                    }
                    if (count < 0) break
                    if (count == 0) continue
                    skippedBytes = try {
                        Math.addExact(skippedBytes, count.toLong())
                    } catch (_: ArithmeticException) {
                        zip.close()
                        return StorageResult.Failure(StorageError.Unsupported)
                    }
                    if (skippedBytes > ArchiveLimits.MAX_METADATA_BYTES) {
                        zip.close()
                        return StorageResult.Failure(StorageError.Unsupported)
                    }
                }
            }
            zip.close()
            return StorageResult.Failure(StorageError.StaleReference)
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            zip.close()
            throw cancel
        } catch (error: java.io.IOException) {
            zip.close()
            return StorageResult.Failure(StorageError.IoFailure("ZIP preview source could not be read"))
        }
    }

    private class ArchiveHandleInputStream(private val handle: SequentialReadHandle) : java.io.InputStream() {
        override fun read(): Int {
            val byte = ByteArray(1)
            val count = handle.read(byte, 0, 1)
            if (count == 0) throw java.io.IOException("ZIP source read made no progress")
            return if (count < 0) -1 else byte[0].toInt() and 0xff
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            return handle.read(buffer, offset, length).also {
                if (it == 0) throw java.io.IOException("ZIP source read made no progress")
            }
        }
        override fun close() = handle.close()
    }

    private class ArchiveEntryReadHandle(
        private val zip: java.util.zip.ZipInputStream,
        override val expectedBytes: Long?,
    ) : SequentialReadHandle {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = zip.read(buffer, offset, length)
        override fun close() = zip.close()
    }
}
