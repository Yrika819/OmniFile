package com.omnifile.storage

@JvmInline
value class ProviderId(val value: String)

interface EntryRef {
    val providerId: ProviderId
}

enum class EntryKind {
    FILE,
    DIRECTORY,
}

enum class StorageCapability {
    LIST_CHILDREN,
    READ_SEQUENTIAL,
    READ_SEEKABLE,
    WRITE,
    RENAME,
    DELETE,
}

data class StorageEntry(
    val ref: EntryRef,
    val displayName: String,
    val kind: EntryKind,
    val sizeBytes: Long?,
    val modifiedAtEpochMillis: Long?,
    val mimeType: String?,
    val capabilities: Set<StorageCapability>,
    val parentRef: EntryRef? = null,
)

sealed interface StorageError {
    data object NotFound : StorageError
    data object PermissionDenied : StorageError
    data object StaleReference : StorageError
    data object Unsupported : StorageError
    data class IoFailure(val detail: String?) : StorageError
    data object Cancelled : StorageError
}

sealed interface StorageResult<out T> {
    data class Success<T>(val value: T) : StorageResult<T>
    data class Failure(val error: StorageError) : StorageResult<Nothing>
}

interface StorageProvider {
    val id: ProviderId

    suspend fun root(): StorageResult<StorageEntry>

    suspend fun listChildren(directory: EntryRef): StorageResult<List<StorageEntry>>
}
