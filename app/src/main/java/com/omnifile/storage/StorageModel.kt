package com.omnifile.storage

@JvmInline
value class ProviderId(val value: String)

interface EntryRef {
    val providerId: ProviderId

    /**
     * Stable, provider-scoped identity containing every field that distinguishes this reference.
     * UI code may use it as a Bundle-saveable list key without reducing identity to a hash.
     */
    val identityKey: String
}

enum class EntryKind {
    FILE,
    DIRECTORY,
}

enum class StorageCapability {
    LIST_CHILDREN,
    CREATE_CHILD,
    READ_SEQUENTIAL,
    READ_SEEKABLE,
    WRITE,
    RENAME,
    DELETE,
    /** Source mutation/version proof is strong enough for destructive Move. */
    MOVE_SOURCE,
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
    data object SourceChanged : StorageError
    data object StaleReference : StorageError
    data object Unsupported : StorageError
    data class InvalidName(
        val requestedName: String,
        val message: String,
    ) : StorageError
    data class NameConflict(val requestedName: String) : StorageError {
        val message: String = "An entry named $requestedName already exists"
    }
    data class PartialDelete(val outcomes: List<DeleteItemResult>) : StorageError {
        val hasFailures: Boolean = outcomes.any { it.outcome is DeleteItemOutcome.Failed }
    }
    data class IoFailure(val detail: String?) : StorageError
    data object Cancelled : StorageError
    data object AmbiguousFinalization : StorageError
    data object AmbiguousSourceDeletion : StorageError
}

data class DeleteItemResult(
    val entry: StorageEntry,
    val outcome: DeleteItemOutcome,
)

sealed interface DeleteItemOutcome {
    data object Deleted : DeleteItemOutcome
    data class Failed(val error: StorageError) : DeleteItemOutcome
}

sealed interface StorageResult<out T> {
    data class Success<T>(val value: T) : StorageResult<T>
    data class Failure(val error: StorageError) : StorageResult<Nothing>
}

enum class TransferCapability {
    READ_SEQUENTIAL,
    CREATE_CHILD,
    WRITE_SEQUENTIAL,
    RESUME_WRITE,
    FINALIZE,
    DELETE,
    MOVE_SOURCE,
}

data class TransferFileFacts(
    val locator: com.omnifile.operations.DurableLocator,
    val kind: EntryKind,
    val sizeBytes: Long?,
    val versionToken: String?,
)

sealed interface FinalizationResult {
    data class Finalized(val finalLocator: com.omnifile.operations.DurableLocator) : FinalizationResult
    data object Ambiguous : FinalizationResult
    data object Unsupported : FinalizationResult
}

interface SequentialReadHandle : java.io.Closeable {
    val expectedBytes: Long?
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
}

interface SequentialWriteHandle : java.io.Closeable {
    fun write(buffer: ByteArray, offset: Int, length: Int)
    fun flush()
}

/** Optional transfer surface; StorageProvider remains browse/mutation compatible. */
interface StorageTransferProvider : StorageProvider {
    val transferCapabilities: Set<TransferCapability>

    suspend fun encodeDurableLocator(ref: EntryRef): StorageResult<com.omnifile.operations.DurableLocator>

    suspend fun resolveDurableLocator(locator: com.omnifile.operations.DurableLocator): StorageResult<StorageEntry>

    suspend fun inspectTransfer(locator: com.omnifile.operations.DurableLocator): StorageResult<TransferFileFacts>

    suspend fun openSequentialRead(locator: com.omnifile.operations.DurableLocator): StorageResult<SequentialReadHandle>

    suspend fun createOperationPartial(
        destinationParent: com.omnifile.operations.DurableLocator,
        intendedFinalName: String,
        operationId: String,
    ): StorageResult<com.omnifile.operations.DurableLocator>

    suspend fun openSequentialWrite(
        partial: com.omnifile.operations.DurableLocator,
        append: Boolean,
        operationId: String? = null,
    ): StorageResult<SequentialWriteHandle>

    suspend fun finalizeOperationPartial(
        partial: com.omnifile.operations.DurableLocator,
        destinationParent: com.omnifile.operations.DurableLocator,
        intendedFinalName: String,
        operationId: String? = null,
    ): StorageResult<FinalizationResult>

    suspend fun deleteDurableSource(source: com.omnifile.operations.DurableLocator): StorageResult<Unit>

    suspend fun deleteOperationPartial(
        partial: com.omnifile.operations.DurableLocator,
        operationId: String? = null,
    ): StorageResult<Unit>
}

interface StorageProvider {
    val id: ProviderId

    suspend fun root(): StorageResult<StorageEntry>

    suspend fun listChildren(directory: EntryRef): StorageResult<List<StorageEntry>>

    suspend fun rename(
        entry: StorageEntry,
        requestedName: String,
    ): StorageResult<StorageEntry> = StorageResult.Failure(StorageError.Unsupported)

    suspend fun delete(entry: StorageEntry): StorageResult<Unit> =
        StorageResult.Failure(StorageError.Unsupported)
}
