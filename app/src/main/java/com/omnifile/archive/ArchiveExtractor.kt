package com.omnifile.archive

import com.omnifile.files.FilesRepository
import com.omnifile.storage.EntryKind
import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.SequentialReadHandle
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.Normalizer
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Conservative ZIP extraction coordinator. It is intentionally restart-required;
 * provider partial/finalization primitives protect each file, while this coordinator
 * rolls back output created by the current foreground attempt on failure/cancel.
 */
class ArchiveExtractor(
    private val filesRepository: FilesRepository,
    private val limits: Limits = Limits(),
) {
    data class Limits(
        val maxEntryBytes: Long = ArchiveLimits.MAX_ENTRY_BYTES,
        val maxTotalBytes: Long = ArchiveLimits.MAX_TOTAL_EXTRACT_BYTES,
        val bufferSize: Int = ArchiveLimits.BUFFER_SIZE,
    )

    suspend fun extract(
        document: ArchiveDocument,
        selected: List<ArchiveNode>,
        destination: LocalStorageProvider,
        destinationParent: StorageEntry,
        onProgress: (ArchiveExtractionProgress) -> Unit = {},
    ): ArchiveExtractionResult {
        if (destinationParent.kind != EntryKind.DIRECTORY || destinationParent.ref.providerId != destination.id) {
            return ArchiveExtractionResult.Failure(
                ArchiveExtractionError.Io("The selected Local destination is not available."),
                cleanupComplete = true,
            )
        }
        val planResult = plan(document, selected, destination, destinationParent)
        val plan = when (planResult) {
            is PlanResult.Success -> planResult.plan
            is PlanResult.Failure -> return ArchiveExtractionResult.Failure(planResult.error, true)
        }
        val createdDirectories = mutableListOf<StorageEntry>()
        val finalizedFiles = mutableListOf<StorageEntry>()
        val partials = mutableListOf<PartialState>()
        val operationId = "archive-${UUID.randomUUID()}"
        var activeWriter: com.omnifile.storage.SequentialWriteHandle? = null
        var activePartial: PartialState? = null

        suspend fun cleanup(): Boolean = withContext(NonCancellable) {
            var complete = true
            try {
                activeWriter?.close()
            } catch (_: Exception) {
                complete = false
            }
            activeWriter = null
            activePartial?.let { if (it !in partials) partials += it }
            partials.asReversed().forEach { partial ->
                if (destination.deleteOperationPartial(partial.locator, partial.operationId) is StorageResult.Failure) {
                    complete = false
                }
            }
            finalizedFiles.asReversed().forEach { file ->
                if (destination.delete(file) is StorageResult.Failure) complete = false
            }
            createdDirectories.asReversed().forEach { directory ->
                if (destination.delete(directory) is StorageResult.Failure) complete = false
            }
            complete
        }

        return try {
            val directoryMap = linkedMapOf<String, StorageEntry>("" to destinationParent)
            val directoryPaths = buildSet {
                plan.records.filter { it.record.kind == EntryKind.DIRECTORY }
                    .forEach { addAll(pathWithParents(it.target)) }
                plan.records.filter { it.record.kind == EntryKind.FILE }
                    .forEach { addAll(pathWithParents(it.target.dropLast(1))) }
            }.sortedBy { it.size }
            for (path in directoryPaths) {
                currentCoroutineContext().ensureActive()
                if (path.isEmpty()) continue
                val parent = directoryMap[pathKey(path.dropLast(1))]
                    ?: return ArchiveExtractionResult.Failure(
                        ArchiveExtractionError.TypeConflict(pathKey(path)),
                        cleanup(),
                    )
                val name = path.last()
                when (val existing = childAt(destination, parent, name)) {
                    is StorageResult.Success -> {
                        if (existing.value.kind != EntryKind.DIRECTORY) {
                            return ArchiveExtractionResult.Failure(
                                ArchiveExtractionError.TypeConflict(pathKey(path)),
                                cleanup(),
                            )
                        }
                        directoryMap[pathKey(path)] = existing.value
                    }
                    is StorageResult.Failure -> if (existing.error == StorageError.NotFound) {
                        when (val created = destination.createDirectory(parent.ref, name)) {
                            is StorageResult.Success -> {
                                directoryMap[pathKey(path)] = created.value
                                createdDirectories += created.value
                            }
                            is StorageResult.Failure -> {
                                return ArchiveExtractionResult.Failure(
                                    ArchiveExtractionError.Io(created.error.toString()),
                                    cleanup(),
                                )
                            }
                        }
                    } else {
                        return ArchiveExtractionResult.Failure(
                            ArchiveExtractionError.Io(existing.error.toString()),
                            cleanup(),
                        )
                    }
                }
            }

            val files = plan.records.filter { it.record.kind == EntryKind.FILE }
            var totalBytes: Long? = 0L
            for (item in files) {
                val declared = item.record.declaredSizeBytes
                if (declared == null) {
                    totalBytes = null
                } else if (totalBytes != null) {
                    totalBytes = checkedAdd(totalBytes!!, declared)
                }
            }
            onProgress(ArchiveExtractionProgress(0, files.size, 0L, totalBytes))
            val source = when (val opened = filesRepository.openSequentialRead(document.container.source)) {
                is StorageResult.Success -> opened.value
                is StorageResult.Failure -> {
                    return ArchiveExtractionResult.Failure(
                        ArchiveExtractionError.Source(ArchiveError.Provider(opened.error)),
                        cleanup(),
                    )
                }
            }
            HandleInputStream(source).use { rawInput ->
                val buffered = BufferedInputStream(rawInput, limits.bufferSize)
                val zip = ZipInputStream(buffered)
                try {
                    val buffer = ByteArray(limits.bufferSize)
                    var ordinal = 0
                    var scannedBytes = 0L
                    var extractedBytes = 0L
                    var completedFiles = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val entry = try {
                            zip.nextEntry
                        } catch (error: ZipException) {
                            return ArchiveExtractionResult.Failure(
                                ArchiveExtractionError.Source(classify(error)),
                                cleanup(),
                            )
                        }
                        if (entry == null) break
                        val planned = plan.byOrdinal[ordinal]
                        val record = document.records.getOrNull(ordinal)
                            ?: return ArchiveExtractionResult.Failure(
                                ArchiveExtractionError.Source(ArchiveError.Corrupt),
                                cleanup(),
                            )
                        if (entry.name != record.rawName) {
                            return ArchiveExtractionResult.Failure(
                                ArchiveExtractionError.Source(ArchiveError.Io("Archive changed while extracting")),
                                cleanup(),
                            )
                        }
                        var actual = 0L
                        activeWriter = null
                        activePartial = null
                        try {
                            if (planned != null && record.kind == EntryKind.FILE) {
                                val parent = directoryMap[pathKey(planned.target.dropLast(1))]
                                    ?: return ArchiveExtractionResult.Failure(
                                        ArchiveExtractionError.TypeConflict(pathKey(planned.target)),
                                        cleanup(),
                                    )
                                val itemOperationId = "$operationId-$ordinal"
                                val parentLocator = destination.encodeDurableLocator(parent.ref).requireValue()
                                val partialLocator = destination.createOperationPartial(
                                    parentLocator,
                                    planned.target.last(),
                                    itemOperationId,
                                ).requireValue()
                                activePartial = PartialState(partialLocator, itemOperationId)
                                partials += activePartial!!
                                activeWriter = destination.openSequentialWrite(partialLocator, false, itemOperationId).requireValue()
                            }
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = try {
                                    zip.read(buffer)
                                } catch (error: java.io.IOException) {
                                    return ArchiveExtractionResult.Failure(
                                        ArchiveExtractionError.Source(ArchiveError.Provider(StorageError.ProviderUnavailable)),
                                        cleanup(),
                                    )
                                }
                                if (read < 0) break
                                if (read == 0) continue
                                actual = checkedAdd(actual, read.toLong())
                                if (actual > limits.maxEntryBytes) {
                                    return ArchiveExtractionResult.Failure(
                                        ArchiveExtractionError.ResourceLimit("Entry exceeds ${limits.maxEntryBytes} bytes"),
                                        cleanup(),
                                    )
                                }
                                if (planned == null) {
                                    scannedBytes = checkedAdd(scannedBytes, read.toLong())
                                    if (scannedBytes > ArchiveLimits.MAX_METADATA_BYTES) {
                                        return ArchiveExtractionResult.Failure(
                                            ArchiveExtractionError.ResourceLimit("Archive scan exceeds ${ArchiveLimits.MAX_METADATA_BYTES} bytes"),
                                            cleanup(),
                                        )
                                    }
                                } else {
                                    extractedBytes = checkedAdd(extractedBytes, read.toLong())
                                    if (extractedBytes > limits.maxTotalBytes) {
                                        return ArchiveExtractionResult.Failure(
                                            ArchiveExtractionError.ResourceLimit("Extraction exceeds ${limits.maxTotalBytes} bytes"),
                                            cleanup(),
                                        )
                                    }
                                    try {
                                        activeWriter?.write(buffer, 0, read)
                                    } catch (error: java.io.IOException) {
                                        return ArchiveExtractionResult.Failure(
                                            ArchiveExtractionError.Io(error.message),
                                            cleanup(),
                                        )
                                    }
                                }
                            }
                            val declared = entry.size.takeIf { it >= 0 }
                            if (declared != null && declared != actual) {
                                return ArchiveExtractionResult.Failure(
                                    ArchiveExtractionError.Source(ArchiveError.Corrupt),
                                    cleanup(),
                                )
                            }
                            if (planned != null && actual != record.actualSizeBytes) {
                                return ArchiveExtractionResult.Failure(
                                    ArchiveExtractionError.Source(ArchiveError.Io("Archive changed while extracting")),
                                    cleanup(),
                                )
                            }
                            try {
                                activeWriter?.flush()
                                activeWriter?.close()
                            } catch (error: java.io.IOException) {
                                return ArchiveExtractionResult.Failure(
                                    ArchiveExtractionError.Io(error.message),
                                    cleanup(),
                                )
                            }
                            activeWriter = null
                            if (activePartial != null) {
                                val currentPartial = activePartial!!
                                val parent = directoryMap[pathKey(planned!!.target.dropLast(1))]!!
                                val parentLocator = destination.encodeDurableLocator(parent.ref).requireValue()
                                when (val finalized = destination.finalizeOperationPartial(
                                    currentPartial.locator,
                                    parentLocator,
                                    planned.target.last(),
                                    currentPartial.operationId,
                                ).requireValue()) {
                                    is com.omnifile.storage.FinalizationResult.Finalized -> {
                                        partials.remove(currentPartial)
                                        activePartial = null
                                        val finalEntry = childAt(destination, parent, planned.target.last()).requireValue()
                                        finalizedFiles += finalEntry
                                        completedFiles += 1
                                        onProgress(ArchiveExtractionProgress(completedFiles, files.size, extractedBytes, totalBytes))
                                    }
                                    com.omnifile.storage.FinalizationResult.Ambiguous -> {
                                        return ArchiveExtractionResult.Failure(
                                            ArchiveExtractionError.Io("Finalization is ambiguous; retry is required."),
                                            cleanup(),
                                        )
                                    }
                                    com.omnifile.storage.FinalizationResult.Unsupported -> {
                                        return ArchiveExtractionResult.Failure(
                                            ArchiveExtractionError.Io("The destination cannot finalize extraction output."),
                                            cleanup(),
                                        )
                                    }
                                }
                            }
                        } catch (error: kotlinx.coroutines.CancellationException) {
                            throw error
                        } catch (error: java.io.IOException) {
                            return ArchiveExtractionResult.Failure(
                                ArchiveExtractionError.Source(ArchiveError.Provider(StorageError.ProviderUnavailable)),
                                cleanup(),
                            )
                        } finally {
                            try {
                                activeWriter?.close()
                            } finally {
                                activeWriter = null
                            }
                        }
                        try {
                            zip.closeEntry()
                        } catch (error: ZipException) {
                            return ArchiveExtractionResult.Failure(
                                ArchiveExtractionError.Source(classify(error)),
                                cleanup(),
                            )
                        }
                        ordinal += 1
                    }
                    val drainBuffer = ByteArray(limits.bufferSize)
                    while (buffered.read(drainBuffer) > 0) { }
                    if (!rawInput.hasZipEndRecord()) {
                        return ArchiveExtractionResult.Failure(
                            ArchiveExtractionError.Source(ArchiveError.Corrupt),
                            cleanup(),
                        )
                    }
                    if (ordinal != document.records.size) {
                        return ArchiveExtractionResult.Failure(
                            ArchiveExtractionError.Source(ArchiveError.Io("Archive changed while extracting")),
                            cleanup(),
                        )
                    }
                    return ArchiveExtractionResult.Success(files.size, extractedBytes)
                } finally {
                    zip.close()
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            ArchiveExtractionResult.Cancelled(cleanup())
        } catch (error: ArithmeticException) {
            ArchiveExtractionResult.Failure(
                ArchiveExtractionError.ResourceLimit("Extraction byte counter overflow"),
                cleanup(),
            )
        } catch (error: ZipException) {
            ArchiveExtractionResult.Failure(ArchiveExtractionError.Source(classify(error)), cleanup())
        } catch (error: java.io.IOException) {
            ArchiveExtractionResult.Failure(
                ArchiveExtractionError.Source(ArchiveError.Provider(StorageError.ProviderUnavailable)),
                cleanup(),
            )
        } catch (error: ExtractionProviderException) {
            ArchiveExtractionResult.Failure(ArchiveExtractionError.Io(error.error.toString()), cleanup())
        } catch (error: IllegalStateException) {
            ArchiveExtractionResult.Failure(ArchiveExtractionError.Io(error.message), cleanup())
        }
    }

    private suspend fun plan(
        document: ArchiveDocument,
        selected: List<ArchiveNode>,
        destination: LocalStorageProvider,
        destinationParent: StorageEntry,
    ): PlanResult {
        if (selected.isEmpty()) return PlanResult.Failure(ArchiveExtractionError.Io("Select at least one archive entry."))
        selected.firstOrNull { !it.isExtractable }?.let {
            return PlanResult.Failure(ArchiveExtractionError.UnsafePath(it.displayName, "Entry is not safe to extract"))
        }
        val selectedPaths = selected.map { it.path.segments() }
        val selectedOrdinals = selected.flatMap { it.sourceOrdinals }.toSet()
        val records = document.records.mapNotNull { record ->
            val path = record.safePath?.segments() ?: return@mapNotNull if (record.ordinal in selectedOrdinals) {
                PlanResult.Failure(ArchiveExtractionError.UnsafePath(record.rawName, record.unsafeReason))
            } else null
            val include = if (record.kind == EntryKind.FILE && record.ordinal in selectedOrdinals) {
                true
            } else {
                selectedPaths.any { prefix -> path.size >= prefix.size && path.subList(0, prefix.size) == prefix }
            }
            if (!include) null else PlannedRecord(record, path)
        }
        val failures = records.filterIsInstance<PlanResult.Failure>()
        if (failures.isNotEmpty()) return failures.first()
        @Suppress("UNCHECKED_CAST")
        val planned = records.filterIsInstance<PlannedRecord>()
        if (planned.isEmpty()) return PlanResult.Failure(ArchiveExtractionError.Io("No extractable entries were selected."))

        val byPath = planned.groupBy { normalizedPathKey(it.target) }
        byPath.forEach { (path, sameTarget) ->
            if (sameTarget.size > 1) return PlanResult.Failure(ArchiveExtractionError.DuplicateTarget(path))
        }
        val filePaths = planned.filter { it.record.kind == EntryKind.FILE }.map { it.target }.map(::normalizedPathKey).toSet()
        val directoryPaths = planned.filter { it.record.kind == EntryKind.DIRECTORY }.map { it.target }.map(::normalizedPathKey).toSet()
        if (filePaths.any { file -> directoryPaths.any { dir -> file == dir || dir.startsWith("$file/") } }) {
            return PlanResult.Failure(ArchiveExtractionError.TypeConflict("file/directory collision"))
        }
        val allDirectories = buildSet {
            planned.filter { it.record.kind == EntryKind.DIRECTORY }.forEach { addAll(pathWithParents(it.target)) }
            planned.filter { it.record.kind == EntryKind.FILE }.forEach { addAll(pathWithParents(it.target.dropLast(1))) }
        }
        // Preflight the destination namespace without creating anything. A missing
        // parent is safe; the executor creates it later and rolls it back on failure.
        for (path in (allDirectories + planned.map { it.target }).sortedBy { it.size }) {
            if (path.isEmpty()) continue
            var current = destinationParent
            var missing = false
            path.forEachIndexed { index, name ->
                if (missing) return@forEachIndexed
                when (val child = childAt(destination, current, name)) {
                    is StorageResult.Success -> {
                        val final = index == path.lastIndex
                        val wantsFile = final && planned.any { it.target == path && it.record.kind == EntryKind.FILE }
                        val wantsDirectory = final && planned.any { it.target == path && it.record.kind == EntryKind.DIRECTORY }
                        if (wantsFile) return PlanResult.Failure(ArchiveExtractionError.DestinationConflict(pathKey(path)))
                        if ((wantsDirectory || !final) && child.value.kind != EntryKind.DIRECTORY) {
                            return PlanResult.Failure(ArchiveExtractionError.TypeConflict(pathKey(path)))
                        }
                        current = child.value
                    }
                    is StorageResult.Failure -> if (child.error == StorageError.NotFound) {
                        missing = true
                    } else {
                        return PlanResult.Failure(ArchiveExtractionError.Io(child.error.toString()))
                    }
                }
            }
        }
        val byOrdinal = planned.associateBy { it.record.ordinal }
        return PlanResult.Success(Plan(byOrdinal, planned))
    }

    private sealed interface PlanResult {
        data class Success(val plan: Plan) : PlanResult
        data class Failure(val error: ArchiveExtractionError) : PlanResult
    }

    private data class PlannedRecord(val record: ArchiveRecord, val target: List<String>)
    private data class Plan(val byOrdinal: Map<Int, PlannedRecord>, val records: List<PlannedRecord>)
    private data class PartialState(
        val locator: com.omnifile.operations.DurableLocator,
        val operationId: String,
    )

    private fun pathWithParents(path: List<String>): List<List<String>> = buildList {
        for (index in 1..path.size) add(path.take(index))
    }

    private fun pathKey(path: List<String>): String = path.joinToString("/")

    private fun normalizedPathKey(path: List<String>): String =
        path.joinToString("/") { Normalizer.normalize(it, Normalizer.Form.NFC) }

    private fun normalizedComponent(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFC)

    private suspend fun childAt(
        destination: LocalStorageProvider,
        parent: StorageEntry,
        name: String,
    ): StorageResult<StorageEntry> = when (val result = destination.listChildren(parent.ref)) {
        is StorageResult.Success -> result.value.firstOrNull {
            normalizedComponent(it.displayName) == normalizedComponent(name)
        }?.let { StorageResult.Success(it) }
            ?: StorageResult.Failure(StorageError.NotFound)
        is StorageResult.Failure -> result
    }

    private fun checkedAdd(current: Long, increment: Long): Long = Math.addExact(current, increment)

    private fun classify(error: ZipException): ArchiveError {
        val message = error.message.orEmpty().lowercase()
        return when {
            "encrypt" in message || "password" in message -> ArchiveError.Encrypted
            "unsupported" in message || "method" in message -> ArchiveError.Unsupported
            else -> ArchiveError.Corrupt
        }
    }

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

    private fun <T> StorageResult<T>.requireValue(): T = when (this) {
        is StorageResult.Success -> value
        is StorageResult.Failure -> throw ExtractionProviderException(error)
    }

    private class ExtractionProviderException(val error: StorageError) : RuntimeException()
}
