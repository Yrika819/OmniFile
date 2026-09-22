package com.omnifile.archive

import com.omnifile.storage.SequentialReadHandle
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

internal object ZipArchiveReader {
    suspend fun read(
        open: suspend () -> StorageResult<SequentialReadHandle>,
        container: ArchiveContainer,
    ): ArchiveOpenResult {
        val handle = when (val result = open()) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return ArchiveOpenResult.Failure(ArchiveError.Provider(result.error))
        }
        return try {
            val records = mutableListOf<ArchiveRecord>()
            var totalBytes = 0L
            HandleInputStream(handle).use { rawInput ->
                val buffered = BufferedInputStream(rawInput, ArchiveLimits.BUFFER_SIZE)
                val zip = ZipInputStream(buffered)
                try {
                    val buffer = ByteArray(ArchiveLimits.BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val entry = try {
                            zip.nextEntry
                        } catch (error: ZipException) {
                            return classifyZipException(error)
                        }
                        if (entry == null) break
                        if (records.size >= ArchiveLimits.MAX_ENTRIES) {
                            return ArchiveOpenResult.Failure(
                                ArchiveError.ResourceLimit("ZIP entry count exceeds ${ArchiveLimits.MAX_ENTRIES}"),
                            )
                        }
                        val name = entry.name
                        if (name.length > ArchiveLimits.MAX_NAME_CHARS * ArchiveLimits.MAX_DEPTH) {
                            return ArchiveOpenResult.Failure(ArchiveError.ResourceLimit("ZIP entry name is too long"))
                        }
                        val (safePath, unsafeReason) = validateArchivePath(name)
                        var actualBytes = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = try {
                                zip.read(buffer)
                            } catch (error: ZipException) {
                                return classifyZipException(error)
                            }
                            if (read < 0) break
                            if (read == 0) continue
                            actualBytes = checkedAdd(actualBytes, read.toLong())
                            totalBytes = checkedAdd(totalBytes, read.toLong())
                            if (actualBytes > ArchiveLimits.MAX_METADATA_BYTES ||
                                totalBytes > ArchiveLimits.MAX_METADATA_BYTES
                            ) {
                                return ArchiveOpenResult.Failure(
                                    ArchiveError.ResourceLimit("ZIP metadata scan exceeds ${ArchiveLimits.MAX_METADATA_BYTES} bytes"),
                                )
                            }
                        }
                        val declaredSize = entry.size.takeIf { it >= 0 }
                        if (declaredSize != null && declaredSize != actualBytes) {
                            return ArchiveOpenResult.Failure(ArchiveError.Corrupt)
                        }
                        val path = safePath
                        records += ArchiveRecord(
                            ordinal = records.size,
                            rawName = name,
                            safePath = path,
                            kind = if (entry.isDirectory || name.endsWith('/')) {
                                com.omnifile.storage.EntryKind.DIRECTORY
                            } else {
                                com.omnifile.storage.EntryKind.FILE
                            },
                            declaredSizeBytes = declaredSize,
                            actualSizeBytes = actualBytes,
                            compressedSizeBytes = entry.compressedSize.takeIf { it >= 0 },
                            modifiedAtEpochMillis = entry.time.takeIf { it >= 0 },
                            method = entry.method,
                            status = if (unsafeReason == null) ArchiveEntryStatus.SUPPORTED else ArchiveEntryStatus.UNSAFE_PATH,
                            unsafeReason = unsafeReason,
                        )
                        try {
                            zip.closeEntry()
                        } catch (error: ZipException) {
                            return classifyZipException(error)
                        }
                    }
                    // ZipInputStream stops when it reaches the central directory. Drain
                    // the remaining sequential bytes so the end record is actually proven.
                    val drainBuffer = ByteArray(ArchiveLimits.BUFFER_SIZE)
                    while (buffered.read(drainBuffer) > 0) { }
                    if (!rawInput.hasZipEndRecord()) {
                        return ArchiveOpenResult.Failure(ArchiveError.Corrupt)
                    }
                } finally {
                    zip.close()
                }
            }
            if (records.isEmpty()) {
                ArchiveOpenResult.Success(ArchiveDocument(container, emptyList()))
            } else {
                ArchiveOpenResult.Success(ArchiveDocument(container, records))
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: ZipException) {
            classifyZipException(error)
        } catch (error: IOException) {
            ArchiveOpenResult.Failure(ArchiveError.Provider(StorageError.ProviderUnavailable))
        } catch (error: IllegalStateException) {
            ArchiveOpenResult.Failure(ArchiveError.Provider(StorageError.ProviderUnavailable))
        } catch (error: ArithmeticException) {
            ArchiveOpenResult.Failure(ArchiveError.ResourceLimit("ZIP byte counter overflow"))
        }
    }

    private fun classifyZipException(error: ZipException): ArchiveOpenResult.Failure {
        val message = error.message.orEmpty().lowercase()
        return when {
            "encrypt" in message || "password" in message -> ArchiveOpenResult.Failure(ArchiveError.Encrypted)
            "unsupported" in message || "method" in message -> ArchiveOpenResult.Failure(ArchiveError.Unsupported)
            else -> ArchiveOpenResult.Failure(ArchiveError.Corrupt)
        }
    }

    private fun checkedAdd(current: Long, increment: Long): Long =
        Math.addExact(current, increment)

    private class HandleInputStream(
        private val handle: SequentialReadHandle,
    ) : InputStream() {
        private var closed = false
        private val tail = java.io.ByteArrayOutputStream(65_536)

        override fun read(): Int {
            val buffer = ByteArray(1)
            val count = read(buffer, 0, 1)
            return if (count < 0) -1 else buffer[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            check(!closed) { "stream is closed" }
            if (length == 0) return 0
            val count = handle.read(buffer, offset, length)
            if (count > 0) {
                val start = maxOf(offset, offset + count - 65_536)
                tail.write(buffer, start, offset + count - start)
                if (tail.size() > 65_536) {
                    val bytes = tail.toByteArray()
                    tail.reset()
                    tail.write(bytes, bytes.size - 65_536, 65_536)
                }
            }
            return count
        }

        fun hasZipEndRecord(): Boolean {
            val bytes = tail.toByteArray()
            for (index in 0 until bytes.size - 3) {
                val signature = (bytes[index].toInt() and 0xff) or
                    ((bytes[index + 1].toInt() and 0xff) shl 8) or
                    ((bytes[index + 2].toInt() and 0xff) shl 16) or
                    ((bytes[index + 3].toInt() and 0xff) shl 24)
                if (signature == 0x06054b50 || signature == 0x06064b50) return true
            }
            return false
        }

        override fun close() {
            if (!closed) {
                closed = true
                handle.close()
            }
        }
    }
}
