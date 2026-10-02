package com.omnifile.details

import com.omnifile.files.FilesRepository
import com.omnifile.storage.SequentialReadHandle
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Adapts an owned provider handle to the digest core without widening it. */
private class HandleDigestSource(private val handle: SequentialReadHandle) : DigestSource {
    override val declaredBytes: Long? = handle.expectedBytes?.takeIf { it >= 0 }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        handle.read(buffer, offset, length)

    override fun close() = handle.close()
}

/**
 * Provider-neutral digest of one Local or SAF regular file.
 *
 * Reads are always owned, always off the main thread, and always closed exactly once by
 * [Sha256Calculator]. No whole-file staging, no locator escapes this class, and nothing here
 * touches the PDF renderer, its SharedMemory transport, its snapshot staging, the durable
 * transfer executor, or the media service.
 *
 * Source-change honesty: a digest is published only when the read reached EOF with a clean
 * close and no reliable change evidence. When a provider proves no size and offers no version
 * token, the digest honestly represents the bytes read during this calculation; that is a
 * stated limitation, not an immutable-snapshot claim.
 */
class FileDetailsDigestCalculator(
    private val repository: FilesRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val bufferBytes: Int = Sha256Calculator.BUFFER_BYTES,
) {
    suspend fun calculate(
        entry: StorageEntry,
        onProgress: (DigestProgress) -> Unit = {},
        shouldAbort: () -> Boolean = { false },
    ): DigestOutcome = withContext(ioDispatcher) {
        val handle = try {
            when (val opened = repository.openSequentialRead(entry)) {
                is StorageResult.Success -> opened.value
                is StorageResult.Failure -> return@withContext openFailure(opened.error)
            }
        } catch (error: Throwable) {
            // A provider is allowed to throw out of an open. That must become a typed digest
            // failure, never an uncaught coroutine failure with a lost UI state.
            return@withContext openThrownFailure(error)
        }
        // The core is the single closer, including when shouldAbort already won.
        val outcome = Sha256Calculator.calculate(
            source = HandleDigestSource(handle),
            bufferBytes = bufferBytes,
            onProgress = onProgress,
            shouldAbort = shouldAbort,
        )
        if (outcome is DigestOutcome.Complete) verifyUnchanged(entry, outcome) else outcome
    }

    /**
     * Re-resolves the same provider-scoped entry after EOF. The provider's locator is used
     * inside the repository and never reaches this layer.
     */
    private suspend fun verifyUnchanged(
        entry: StorageEntry,
        complete: DigestOutcome.Complete,
    ): DigestOutcome = when (val refreshed = repository.refreshEntry(entry)) {
        is StorageResult.Failure -> when (refreshed.error) {
            // Definitive absence: the selected identity no longer resolves to the same item.
            StorageError.NotFound, StorageError.StaleReference -> changed(
                SourceChangeEvidence.RERESOLVED_IDENTITY_LOST,
                complete,
            )
            // A provider that merely cannot answer is not evidence of change. The read already
            // reached EOF with the declared size matched, so the digest stands.
            else -> complete
        }

        is StorageResult.Success -> {
            val current = refreshed.value
            when {
                current.ref != entry.ref -> changed(SourceChangeEvidence.RERESOLVED_IDENTITY_LOST, complete)
                // Compared only when the value was already known before the read. A newly
                // available value is new information, not a contradiction.
                entry.sizeBytes != null && current.sizeBytes != entry.sizeBytes ->
                    changed(SourceChangeEvidence.RERESOLVED_SIZE_CHANGED, complete)

                entry.modifiedAtEpochMillis != null &&
                        current.modifiedAtEpochMillis != null &&
                        current.modifiedAtEpochMillis != entry.modifiedAtEpochMillis ->
                    changed(SourceChangeEvidence.RERESOLVED_MODIFIED_CHANGED, complete)

                else -> complete
            }
        }
    }

    private fun changed(
        evidence: SourceChangeEvidence,
        complete: DigestOutcome.Complete,
    ) = DigestOutcome.SourceChanged(evidence, complete.bytesRead)

    private fun openFailure(error: StorageError): DigestOutcome = when (error) {
        StorageError.PermissionDenied -> DigestOutcome.Failed(DigestFailure.AccessUnavailable, 0)
        StorageError.NotFound, StorageError.StaleReference, StorageError.ProviderUnavailable ->
            DigestOutcome.Failed(DigestFailure.SourceUnavailable, 0)

        StorageError.SourceChanged -> DigestOutcome.SourceChanged(SourceChangeEvidence.PROVIDER_REPORTED_CHANGE, 0)
        StorageError.Unsupported -> DigestOutcome.Failed(DigestFailure.Unsupported, 0)
        // A provider that reports its own cancellation is still a read that produced no digest,
        // and Retry is the honest affordance. It is not collapsed into a generic IOException.
        StorageError.Cancelled -> DigestOutcome.Failed(DigestFailure.ReadFailed, 0)
        // Deliberately ignores error.detail: it can carry provider text, a document id, or a path.
        else -> DigestOutcome.Failed(DigestFailure.ReadFailed, 0)
    }

    private fun openThrownFailure(error: Throwable): DigestOutcome = when (error) {
        is SecurityException -> DigestOutcome.Failed(DigestFailure.AccessUnavailable, 0)
        is java.io.FileNotFoundException -> DigestOutcome.Failed(DigestFailure.SourceUnavailable, 0)
        else -> DigestOutcome.Failed(DigestFailure.ReadFailed, 0)
    }
}
