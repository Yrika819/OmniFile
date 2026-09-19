package com.omnifile.operations

import com.omnifile.storage.EntryKind
import com.omnifile.storage.FinalizationResult
import com.omnifile.storage.SequentialReadHandle
import com.omnifile.storage.SequentialWriteHandle
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import com.omnifile.storage.StorageTransferProvider

/**
 * Provider-neutral regular-file transfer. It never owns durable operation truth;
 * the coordinator persists stage and state around these provider effects.
 */
class TransferEngine(
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    private val checkpointBytes: Long = DEFAULT_CHECKPOINT_BYTES,
    private val faultInjector: TransferFaultInjector = TransferFaultInjector.None,
) {
    init {
        require(bufferSize > 0) { "Transfer buffer must be positive" }
        require(checkpointBytes > 0L) { "Checkpoint cadence must be positive" }
    }

    suspend fun execute(
        sourceProvider: StorageTransferProvider,
        destinationProvider: StorageTransferProvider,
        request: TransferRequest,
        isCancellationRequested: suspend () -> Boolean,
        onStage: suspend (OperationState) -> Unit,
        onPartialCreated: suspend (DurableLocator) -> Unit,
        onCheckpoint: suspend (Long) -> Unit,
    ): TransferExecution {
        request.existingPartial?.let { existing ->
            when (val deleted = destinationProvider.deleteOperationPartial(existing)) {
                is StorageResult.Success -> Unit
                is StorageResult.Failure -> if (deleted.error != StorageError.NotFound) {
                    return TransferExecution.Failed(deleted.error, existing)
                }
            }
        }
        val sourceFacts = when (val result = sourceProvider.inspectTransfer(request.source)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return TransferExecution.Failed(result.error, null)
        }
        if (sourceFacts.kind != EntryKind.FILE || sourceFacts.sizeBytes == null) {
            return TransferExecution.Failed(StorageError.Unsupported, null)
        }
        if (request.expectedBytes != null && request.expectedBytes != sourceFacts.sizeBytes) {
            return TransferExecution.Failed(StorageError.SourceChanged, null)
        }

        val destinationParent = when (val result = destinationProvider.resolveDurableLocator(request.destinationParent)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return TransferExecution.Failed(result.error, null)
        }
        val existing = when (val result = destinationProvider.listChildren(destinationParent.ref)) {
            is StorageResult.Success -> result.value.firstOrNull { it.displayName == request.intendedFinalName }
            is StorageResult.Failure -> return TransferExecution.Failed(result.error, null)
        }
        if (existing != null) {
            return TransferExecution.Failed(StorageError.NameConflict(request.intendedFinalName), null)
        }

        val partial = when (val result = destinationProvider.createOperationPartial(
            request.destinationParent,
            request.intendedFinalName,
            request.operationId,
        )) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return TransferExecution.Failed(result.error, null)
        }
        onPartialCreated(partial)
        faultInjector.failureAt(TransferFaultBoundary.AFTER_PARTIAL_CREATE, 0L)?.let {
            return TransferExecution.Failed(it, partial)
        }

        val bytesCompleted = try {
            copy(
                sourceProvider = sourceProvider,
                destinationProvider = destinationProvider,
                source = request.source,
                partial = partial,
                expectedBytes = sourceFacts.sizeBytes,
                isCancellationRequested = isCancellationRequested,
                onCheckpoint = onCheckpoint,
            )
        } catch (cancelled: TransferCancelled) {
            return TransferExecution.Cancelled(partial, cancelled.bytesCompleted)
        } catch (failure: TransferFailure) {
            return TransferExecution.Failed(failure.error, partial)
        }
        faultInjector.failureAt(TransferFaultBoundary.AFTER_TRANSFER, bytesCompleted)?.let {
            return TransferExecution.Failed(it, partial)
        }

        val changedSource = when (val result = sourceProvider.inspectTransfer(request.source)) {
            is StorageResult.Success -> result.value.versionToken != sourceFacts.versionToken
            is StorageResult.Failure -> return TransferExecution.Failed(result.error, partial)
        }
        if (changedSource) return TransferExecution.Failed(StorageError.SourceChanged, partial)

        onStage(OperationState.VERIFYING)
        val partialFacts = when (val result = destinationProvider.inspectTransfer(partial)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return TransferExecution.Failed(result.error, partial)
        }
        if (partialFacts.kind != EntryKind.FILE || partialFacts.sizeBytes != sourceFacts.sizeBytes) {
            return TransferExecution.Failed(StorageError.IoFailure("Partial verification size mismatch"), partial)
        }
        faultInjector.failureAt(TransferFaultBoundary.AFTER_VERIFY, bytesCompleted)?.let {
            return TransferExecution.Failed(it, partial)
        }

        onStage(OperationState.FINALIZING)
        val finalLocator = when (val result = destinationProvider.finalizeOperationPartial(
            partial,
            request.destinationParent,
            request.intendedFinalName,
        )) {
            is StorageResult.Success -> when (val finalization = result.value) {
                is FinalizationResult.Finalized -> finalization.finalLocator
                FinalizationResult.Ambiguous -> return TransferExecution.Failed(StorageError.AmbiguousFinalization, partial)
                FinalizationResult.Unsupported -> return TransferExecution.Failed(StorageError.Unsupported, partial)
            }
            is StorageResult.Failure -> return TransferExecution.Failed(result.error, partial)
        }
        faultInjector.failureAt(TransferFaultBoundary.AFTER_FINALIZE, bytesCompleted)?.let {
            return TransferExecution.Failed(it, finalLocator)
        }

        val finalFacts = when (val result = destinationProvider.inspectTransfer(finalLocator)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return TransferExecution.Failed(result.error, finalLocator)
        }
        if (finalFacts.kind != EntryKind.FILE || finalFacts.sizeBytes != sourceFacts.sizeBytes) {
            return TransferExecution.Failed(StorageError.IoFailure("Final verification size mismatch"), finalLocator)
        }
        return TransferExecution.DestinationComplete(finalLocator, bytesCompleted)
    }

    private suspend fun copy(
        sourceProvider: StorageTransferProvider,
        destinationProvider: StorageTransferProvider,
        source: DurableLocator,
        partial: DurableLocator,
        expectedBytes: Long,
        isCancellationRequested: suspend () -> Boolean,
        onCheckpoint: suspend (Long) -> Unit,
    ): Long {
        val reader = when (val result = sourceProvider.openSequentialRead(source)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> throw TransferFailure(result.error)
        }
        val writer = when (val result = destinationProvider.openSequentialWrite(partial, append = false)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> {
                reader.close()
                throw TransferFailure(result.error)
            }
        }
        val buffer = ByteArray(bufferSize)
        var completed = 0L
        var nextCheckpoint = checkpointBytes
        try {
            while (true) {
                if (isCancellationRequested()) throw TransferCancelled(completed)
                val read = reader.read(buffer, 0, buffer.size)
                if (read < 0) break
                if (read == 0) continue
                writer.write(buffer, 0, read)
                completed += read.toLong()
                faultInjector.failureAt(TransferFaultBoundary.MID_TRANSFER, completed)?.let {
                    throw TransferFailure(it)
                }
                if (completed >= nextCheckpoint) {
                    onCheckpoint(completed)
                    while (nextCheckpoint <= completed) nextCheckpoint += checkpointBytes
                }
            }
            if (completed != expectedBytes) throw TransferFailure(StorageError.IoFailure("Transferred byte count mismatch"))
            writer.flush()
            onCheckpoint(completed)
            return completed
        } finally {
            writer.close()
            reader.close()
        }
    }

    data class TransferRequest(
        val operationId: String,
        val source: DurableLocator,
        val destinationParent: DurableLocator,
        val intendedFinalName: String,
        val expectedBytes: Long?,
        val existingPartial: DurableLocator? = null,
    )

    sealed interface TransferExecution {
        data class DestinationComplete(val finalLocator: DurableLocator, val bytesCompleted: Long) : TransferExecution
        data class Cancelled(val partial: DurableLocator, val bytesCompleted: Long) : TransferExecution
        data class Failed(val error: StorageError, val partial: DurableLocator?) : TransferExecution
    }

    enum class TransferFaultBoundary {
        AFTER_PARTIAL_CREATE,
        MID_TRANSFER,
        AFTER_TRANSFER,
        AFTER_VERIFY,
        AFTER_FINALIZE,
    }

    fun interface TransferFaultInjector {
        fun failureAt(boundary: TransferFaultBoundary, bytesCompleted: Long): StorageError?

        object None : TransferFaultInjector {
            override fun failureAt(boundary: TransferFaultBoundary, bytesCompleted: Long): StorageError? = null
        }
    }

    private class TransferFailure(val error: StorageError) : RuntimeException()
    private class TransferCancelled(val bytesCompleted: Long) : RuntimeException()

    companion object {
        const val DEFAULT_BUFFER_SIZE = 256 * 1024
        const val DEFAULT_CHECKPOINT_BYTES = 8L * 1024L * 1024L
    }
}

