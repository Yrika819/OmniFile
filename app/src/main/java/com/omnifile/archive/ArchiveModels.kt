package com.omnifile.archive

import com.omnifile.storage.EntryKind
import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError

/** ZIP is recognized only as a regular provider file; the source remains provider-owned. */
object ArchiveSupport {
    private val zipMimeTypes = setOf("application/zip", "application/x-zip-compressed")

    fun isZip(entry: StorageEntry): Boolean = entry.kind == EntryKind.FILE && (
        entry.displayName.substringAfterLast('.', "").equals("zip", ignoreCase = true) ||
            entry.mimeType?.lowercase() in zipMimeTypes
        )
}

@JvmInline
value class ArchivePath(private val value: List<String>) {
    fun segments(): List<String> = value
    fun isRoot(): Boolean = value.isEmpty()
    fun child(name: String): ArchivePath = ArchivePath(value + name)
    override fun toString(): String = value.joinToString("/")

    companion object {
        val ROOT = ArchivePath(emptyList())
    }
}

data class ArchiveContainer(
    val source: StorageEntry,
    val title: String = source.displayName,
) {
    val identityKey: String get() = source.ref.identityKey
}

data class ArchiveEntryRef(
    val providerIdValue: ProviderId,
    val archiveIdentity: String,
    val discriminator: String,
) : EntryRef {
    override val providerId: ProviderId = providerIdValue
    override val identityKey: String =
        "archive-entry-v1:${providerId.value}:$archiveIdentity:$discriminator"
}

enum class ArchiveEntryStatus {
    SUPPORTED,
    UNSAFE_PATH,
    UNSUPPORTED_METHOD,
}

data class ArchiveRecord(
    val ordinal: Int,
    val rawName: String,
    val safePath: ArchivePath?,
    val kind: EntryKind,
    val declaredSizeBytes: Long?,
    val actualSizeBytes: Long,
    val compressedSizeBytes: Long?,
    val modifiedAtEpochMillis: Long?,
    val method: Int,
    val status: ArchiveEntryStatus,
    val unsafeReason: String? = null,
)

data class ArchiveNode(
    val ref: ArchiveEntryRef,
    val displayName: String,
    val path: ArchivePath,
    val kind: EntryKind,
    val sizeBytes: Long?,
    val compressedSizeBytes: Long?,
    val modifiedAtEpochMillis: Long?,
    val status: ArchiveEntryStatus,
    /** One or more source records for an implicit/explicit virtual directory. */
    val sourceOrdinals: List<Int>,
) {
    val isExtractable: Boolean
        get() = status == ArchiveEntryStatus.SUPPORTED
}

data class ArchiveDocument(
    val container: ArchiveContainer,
    val records: List<ArchiveRecord>,
) {
    val entryCount: Int get() = records.size

    fun list(path: ArchivePath = ArchivePath.ROOT): List<ArchiveNode> {
        val safeRecords = records.filter { record ->
            val recordPath = record.safePath ?: return@filter false
            val prefix = path.segments()
            val candidate = recordPath.segments()
            candidate.size > prefix.size && candidate.subList(0, prefix.size) == prefix
        }
        val names = linkedSetOf<String>()
        safeRecords.forEach { record ->
            val candidate = record.safePath!!.segments()
            names += candidate[path.segments().size]
        }

        val nodes = mutableListOf<ArchiveNode>()
        for (name in names) {
            val childPath = path.child(name)
            val exact = safeRecords.filter { it.safePath == childPath }
            val descendants = safeRecords.any {
                val candidate = it.safePath!!.segments()
                candidate.size > childPath.segments().size &&
                    candidate.subList(0, childPath.segments().size) == childPath.segments()
            }
            exact.filter { it.kind == EntryKind.FILE }.forEach { record ->
                nodes += record.toNode(container, childPath)
            }
            val directoryRecords = exact.filter { it.kind == EntryKind.DIRECTORY }
            if (directoryRecords.isNotEmpty()) {
                directoryRecords.forEach { record ->
                    nodes += record.toNode(container, childPath)
                }
            } else if (descendants) {
                nodes += ArchiveNode(
                    ref = ArchiveEntryRef(
                        container.source.ref.providerId,
                        container.identityKey,
                        "virtual-dir:${childPath.segments().joinToString("/")}",
                    ),
                    displayName = name,
                    path = childPath,
                    kind = EntryKind.DIRECTORY,
                    sizeBytes = null,
                    compressedSizeBytes = null,
                    modifiedAtEpochMillis = null,
                    status = ArchiveEntryStatus.SUPPORTED,
                    sourceOrdinals = emptyList(),
                )
            }
        }

        // Unsafe entries remain visible at the root, but never become a traversable
        // virtual path. Their original name is metadata, not an output path.
        if (path.isRoot()) {
            records.filter { it.safePath == null }.forEach { record ->
                nodes += ArchiveNode(
                    ref = ArchiveEntryRef(
                        container.source.ref.providerId,
                        container.identityKey,
                        "ordinal:${record.ordinal}",
                    ),
                    displayName = record.rawName.ifEmpty { "(unnamed entry)" },
                    path = ArchivePath.ROOT,
                    kind = record.kind,
                    sizeBytes = record.actualSizeBytes,
                    compressedSizeBytes = record.compressedSizeBytes,
                    modifiedAtEpochMillis = record.modifiedAtEpochMillis,
                    status = record.status,
                    sourceOrdinals = listOf(record.ordinal),
                )
            }
        }
        return nodes.sortedWith(compareBy<ArchiveNode> { it.displayName }.thenBy { it.kind })
    }

    private fun ArchiveRecord.toNode(container: ArchiveContainer, path: ArchivePath): ArchiveNode =
        ArchiveNode(
            ref = ArchiveEntryRef(
                container.source.ref.providerId,
                container.identityKey,
                "ordinal:$ordinal",
            ),
            displayName = path.segments().last(),
            path = path,
            kind = kind,
            sizeBytes = actualSizeBytes,
            compressedSizeBytes = compressedSizeBytes,
            modifiedAtEpochMillis = modifiedAtEpochMillis,
            status = status,
            sourceOrdinals = listOf(ordinal),
        )
}

sealed interface ArchiveError {
    data object NotAnArchive : ArchiveError
    data object Corrupt : ArchiveError
    data object Encrypted : ArchiveError
    data object Unsupported : ArchiveError
    data class ResourceLimit(val detail: String) : ArchiveError
    data class Provider(val error: StorageError) : ArchiveError
    data class Io(val detail: String?) : ArchiveError
}

sealed interface ArchiveOpenResult {
    data class Success(val document: ArchiveDocument) : ArchiveOpenResult
    data class Failure(val error: ArchiveError) : ArchiveOpenResult
}

sealed interface ArchiveExtractionResult {
    data class Success(val files: Int, val bytes: Long) : ArchiveExtractionResult
    data class Failure(
        val error: ArchiveExtractionError,
        val cleanupComplete: Boolean,
    ) : ArchiveExtractionResult
    data class Cancelled(val cleanupComplete: Boolean) : ArchiveExtractionResult
}

sealed interface ArchiveExtractionError {
    data class UnsafePath(val name: String, val reason: String?) : ArchiveExtractionError
    data class DuplicateTarget(val path: String) : ArchiveExtractionError
    data class TypeConflict(val path: String) : ArchiveExtractionError
    data class DestinationConflict(val path: String) : ArchiveExtractionError
    data class Source(val error: ArchiveError) : ArchiveExtractionError
    data class Io(val detail: String?) : ArchiveExtractionError
    data class ResourceLimit(val detail: String) : ArchiveExtractionError
}

data class ArchiveExtractionProgress(
    val filesCompleted: Int,
    val filesTotal: Int,
    val bytesCompleted: Long,
    val totalBytes: Long?,
)

internal object ArchiveLimits {
    const val MAX_ENTRIES = 10_000
    const val MAX_NAME_CHARS = 4_096
    const val MAX_DEPTH = 128
    const val MAX_METADATA_BYTES = 256L * 1024L * 1024L
    const val MAX_ENTRY_BYTES = 512L * 1024L * 1024L
    const val MAX_TOTAL_EXTRACT_BYTES = 1L * 1024L * 1024L * 1024L
    const val BUFFER_SIZE = 32 * 1024
}

internal fun validateArchivePath(rawName: String): Pair<ArchivePath?, String?> {
    if (rawName.isEmpty()) return null to "empty entry name"
    if ('\u0000' in rawName) return null to "NUL in entry name"
    if ('\\' in rawName) return null to "backslash or mixed separator in entry name"
    if (rawName.startsWith('/') || rawName.startsWith("//")) return null to "absolute or UNC-like path"
    if (rawName.length >= 2 && rawName[1] == ':' && rawName[0].isLetter()) {
        return null to "drive-letter path"
    }
    val rawSegments = rawName.split('/')
    val segments = if (rawSegments.lastOrNull().orEmpty().isEmpty()) rawSegments.dropLast(1) else rawSegments
    if (segments.isEmpty()) return null to "empty entry path"
    if (segments.size > ArchiveLimits.MAX_DEPTH) return null to "path depth exceeds limit"
    if (segments.any { it.isEmpty() }) return null to "empty path component"
    if (segments.any { it == "." || it == ".." }) return null to "relative traversal component"
    if (segments.any { it.length > ArchiveLimits.MAX_NAME_CHARS }) return null to "path component exceeds limit"
    return ArchivePath(segments) to null
}
