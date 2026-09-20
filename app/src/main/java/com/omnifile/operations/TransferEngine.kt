package com.omnifile.operations

import com.omnifile.storage.EntryKind
import com.omnifile.storage.FinalizationResult
import com.omnifile.storage.SequentialReadHandle
import com.omnifile.storage.SequentialWriteHandle
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import com.omnifile.storage.StorageTransferProvider
import java.io.IOException

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
            when (val deleted = destinationProvider.deleteOperationPartial(existing, request.operationId)) {
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
        if (sourceFacts.kind != EntryKind.FILE) {
            return TransferExecution.Failed(StorageError.Unsupported, null)
        }
        if (request.expectedBytes != null && sourceFacts.sizeBytes != null && request.expectedBytes != sourceFacts.sizeBytes) {
            return TransferExecution.Failed(StorageError.SourceChanged, null)
        }

        val destinationParent = when (val result = destinationProvider.resolveDurableLocator(request.destinationParent)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return TransferExecution.Failed(result.error, null)
        }
        if (destinationParent.kind != EntryKind.DIRECTORY) {
            return TransferExecution.Failed(StorageError.Unsupported, null)
        }
        val destinationChildren = when (val result = destinationProvider.listChildren(destinationParent.ref)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return TransferExecution.Failed(result.error, null)
        }
        if (destinationChildren.any { it.displayName == request.intendedFinalName }) {
            return TransferExecution.Failed(StorageError.NameConflict(request.intendedFinalName), null)
        }

        val discoveredPartial = if (request.existingPartial == null) {
            destinationChildren
                .firstOrNull { it.displayName == partialName(request.operationId) }
                ?.let { entry ->
                    when (val encoded = destinationProvider.encodeDurableLocator(entry.ref)) {
                        is StorageResult.Success -> encoded.value
                        is StorageResult.Failure -> return TransferExecution.Failed(encoded.error, null)
                    }
                }
        } else {
            null
        }
        val partial = discoveredPartial ?: when (val result = destinationProvider.createOperationPartial(
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
                operationId = request.operationId,
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
        } catch (error: SecurityException) {
            return TransferExecution.Failed(StorageError.PermissionDenied, partial)
        } catch (error: UnsupportedOperationException) {
            return TransferExecution.Failed(StorageError.Unsupported, partial)
        } catch (error: IllegalArgumentException) {
            return TransferExecution.Failed(StorageError.StaleReference, partial)
        } catch (error: IOException) {
            return TransferExecution.Failed(StorageError.IoFailure(error.message), partial)
        } catch (error: IllegalStateException) {
            return TransferExecution.Failed(StorageError.IoFailure(error.message), partial)
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
        val partialBytes = try {
            when {
                partialFacts.kind != EntryKind.FILE -> return TransferExecution.Failed(StorageError.Unsupported, partial)
                partialFacts.sizeBytes != null -> partialFacts.sizeBytes
                else -> countBytes(destinationProvider, partial)
            }
        } catch (error: SecurityException) {
            return TransferExecution.Failed(StorageError.PermissionDenied, partial)
        } catch (error: IOException) {
            return TransferExecution.Failed(StorageError.IoFailure(error.message), partial)
        } catch (error: IllegalStateException) {
            return TransferExecution.Failed(StorageError.IoFailure(error.message), partial)
        }
        if (partialBytes != bytesCompleted) {
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
            operationId = request.operationId,
        )) {
            is StorageResult.Success -> when (val finalization = result.value) {
                is FinalizationResult.Finalized -> finalization.finalLocator
                FinalizationResult.Ambiguous -> return TransferExecution.Failed(StorageError.AmbiguousFinalization, partial)
                FinalizationResult.Unsupported -> return TransferExecution.Failed(StorageError.Unsupported, partial)
            }
            is StorageResult.Failure -> return TransferExecution.Failed(result.error, partial)
        }
        faultInjector.failureAt(TransferFaultBoundary.AFTER_FINALIZE, bytesCompleted)?.let {
            return TransferExecution.Failed(it, null, finalLocator)
        }

        val finalFacts = when (val result = destinationProvider.inspectTransfer(finalLocator)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return TransferExecution.Failed(result.error, null, finalLocator)
        }
        val finalBytes = try {
            when {
                finalFacts.kind != EntryKind.FILE -> return TransferExecution.Failed(StorageError.Unsupported, null, finalLocator)
                finalFacts.sizeBytes != null -> finalFacts.sizeBytes
                else -> countBytes(destinationProvider, finalLocator)
            }
        } catch (error: SecurityException) {
            return TransferExecution.Failed(StorageError.PermissionDenied, null, finalLocator)
        } catch (error: IOException) {
            return TransferExecution.Failed(StorageError.IoFailure(error.message), null, finalLocator)
        } catch (error: IllegalStateException) {
            return TransferExecution.Failed(StorageError.IoFailure(error.message), null, finalLocator)
        }
        if (finalBytes != bytesCompleted) {
            return TransferExecution.Failed(
                StorageError.IoFailure("Final verification size mismatch"),
                null,
                finalLocator,
            )
        }
        return TransferExecution.DestinationComplete(finalLocator, bytesCompleted)
    }

    private suspend fun copy(
        sourceProvider: StorageTransferProvider,
        destinationProvider: StorageTransferProvider,
        operationId: String,
        source: DurableLocator,
        partial: DurableLocator,
        expectedBytes: Long?,
        isCancellationRequested: suspend () -> Boolean,
        onCheckpoint: suspend (Long) -> Unit,
    ): Long {
        val reader = when (val result = sourceProvider.openSequentialRead(source)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> throw TransferFailure(result.error)
        }
        val writer = when (val result = destinationProvider.openSequentialWrite(partial, append = false, operationId = operationId)) {
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
            if (expectedBytes != null && completed != expectedBytes) {
                throw TransferFailure(StorageError.IoFailure("Transferred byte count mismatch"))
            }
            writer.flush()
            onCheckpoint(completed)
            return completed
        } finally {
            writer.close()
            reader.close()
        }
    }

    private suspend fun countBytes(
        provider: StorageTransferProvider,
        locator: DurableLocator,
    ): Long {
        val reader = when (val result = provider.openSequentialRead(locator)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> throw TransferFailure(result.error)
        }
        val buffer = ByteArray(bufferSize)
        var count = 0L
        try {
            while (true) {
                val read = reader.read(buffer, 0, buffer.size)
                if (read < 0) return count
                if (read > 0) count += read.toLong()
            }
        } finally {
            reader.close()
        }
    }

    private fun partialName(operationId: String): String =
        ".omnifile-${operationId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.partial"

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
        data class Failed(
            val error: StorageError,
            val partial: DurableLocator?,
            val finalizedDestination: DurableLocator? = null,
        ) : TransferExecution
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
