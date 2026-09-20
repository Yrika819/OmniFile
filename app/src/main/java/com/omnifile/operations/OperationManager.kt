package com.omnifile.operations

import com.omnifile.storage.EntryKind
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageCapability
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import com.omnifile.storage.StorageTransferProvider
import java.io.IOException
import java.util.UUID

class OperationManager(
    private val repository: OperationRepository,
    providers: Map<ProviderId, StorageTransferProvider>,
    private val transferEngine: TransferEngine = TransferEngine(),
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val providers = providers.toMutableMap()

    fun registerProvider(provider: StorageTransferProvider) {
        providers[provider.id] = provider
    }

    suspend fun enqueue(
        type: OperationType,
        items: List<EnqueueItem>,
        batchId: String = UUID.randomUUID().toString(),
    ): StorageResult<List<String>> {
        require(items.isNotEmpty()) { "At least one operation item is required" }
        val prepared = mutableListOf<PreparedItem>()
        items.forEach { item ->
            val source = providers[item.source.providerId]
                ?: return StorageResult.Failure(StorageError.PermissionDenied)
            val destination = providers[item.destinationParent.providerId]
                ?: return StorageResult.Failure(StorageError.PermissionDenied)
            val requiredDestination = setOf(
                com.omnifile.storage.TransferCapability.CREATE_CHILD,
                com.omnifile.storage.TransferCapability.WRITE_SEQUENTIAL,
                com.omnifile.storage.TransferCapability.FINALIZE,
            )
            if (!source.transferCapabilities.contains(com.omnifile.storage.TransferCapability.READ_SEQUENTIAL) ||
                !destination.transferCapabilities.containsAll(requiredDestination) ||
                type == OperationType.MOVE && !source.transferCapabilities.contains(com.omnifile.storage.TransferCapability.MOVE_SOURCE)
            ) {
                return StorageResult.Failure(StorageError.Unsupported)
            }
            val sourceEntry = when (val result = source.resolveDurableLocator(item.source)) {
                is StorageResult.Success -> result.value
                is StorageResult.Failure -> return result
            }
            if (sourceEntry.kind != EntryKind.FILE || StorageCapability.READ_SEQUENTIAL !in sourceEntry.capabilities) {
                return StorageResult.Failure(StorageError.Unsupported)
            }
            if (type == OperationType.MOVE && (
                    StorageCapability.DELETE !in sourceEntry.capabilities ||
                        StorageCapability.MOVE_SOURCE !in sourceEntry.capabilities
                )
            ) {
                return StorageResult.Failure(StorageError.Unsupported)
            }
            val destinationEntry = when (val result = destination.resolveDurableLocator(item.destinationParent)) {
                is StorageResult.Success -> result.value
                is StorageResult.Failure -> return result
            }
            if (destinationEntry.kind != EntryKind.DIRECTORY ||
                StorageCapability.CREATE_CHILD !in destinationEntry.capabilities ||
                StorageCapability.WRITE !in destinationEntry.capabilities
            ) {
                return StorageResult.Failure(StorageError.Unsupported)
            }
            val facts = when (val result = source.inspectTransfer(item.source)) {
                is StorageResult.Success -> result.value
                is StorageResult.Failure -> return result
            }
            if (facts.kind != EntryKind.FILE ||
                item.expectedBytes != null && facts.sizeBytes != null && item.expectedBytes != facts.sizeBytes
            ) {
                return StorageResult.Failure(StorageError.SourceChanged)
            }
            prepared += PreparedItem(
                item = item,
                expectedBytes = item.expectedBytes ?: facts.sizeBytes,
                sourceVersion = item.sourceVersion ?: facts.versionToken,
            )
        }

        val now = nowEpochMillis()
        val ids = prepared.map { preparedItem ->
            val item = preparedItem.item
            val operationId = UUID.randomUUID().toString()
            repository.create(
                OperationSnapshot(
                    operationId = operationId,
                    batchId = batchId,
                    type = type,
                    state = OperationState.PLANNED,
                    stage = TransferStage.PLANNED,
                    source = item.source,
                    destinationParent = item.destinationParent,
                    intendedFinalName = item.intendedFinalName,
                    partialDestination = null,
                    expectedBytes = preparedItem.expectedBytes,
                    bytesCompleted = 0L,
                    sourceVersion = preparedItem.sourceVersion,
                    verificationDescription = null,
                    finalizationDescription = null,
                    sourceDeleteState = if (type == OperationType.MOVE) {
                        SourceDeleteState.PENDING
                    } else {
                        SourceDeleteState.NOT_REQUIRED
                    },
                    cancellationRequested = false,
                    errorCode = null,
                    errorMessage = null,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            operationId
        }
        return StorageResult.Success(ids)
    }

    suspend fun execute(operationId: String): OperationSnapshot {
        var operation = requireNotNull(repository.find(operationId)) { "Unknown operation: $operationId" }
        if (operation.state == OperationState.DESTINATION_COMPLETE) return finishDestination(operation)
        if (operation.state == OperationState.SOURCE_DELETE_PENDING) return attemptSourceDeletion(operation)
        if (operation.errorCode == OperationErrorCode.AMBIGUOUS_FINALIZATION &&
            operation.stage == TransferStage.FINALIZING
        ) {
            return operation
        }
        if (operation.stage == TransferStage.FINALIZING && operation.finalizationDescription != null) {
            return reconcileFinalizing(operation)
        }
        if (operation.state == OperationState.RETRYABLE_FAILURE && operation.stage == TransferStage.SOURCE_DELETING) {
            val pending = repository.transition(
                operationId,
                OperationState.SOURCE_DELETE_PENDING,
                TransferStage.SOURCE_DELETING,
                sourceDeleteState = SourceDeleteState.PENDING,
            )
            return attemptSourceDeletion(pending)
        }
        if (operation.state == OperationState.FINALIZING || operation.state == OperationState.VERIFYING) {
            operation = repository.transition(
                operationId,
                OperationState.INTERRUPTED,
                if (operation.state == OperationState.FINALIZING) TransferStage.FINALIZING else TransferStage.VERIFYING,
            )
        }
        if (operation.state == OperationState.PLANNED ||
            operation.state == OperationState.INTERRUPTED ||
            operation.state == OperationState.RETRYABLE_FAILURE
        ) {
            operation = repository.transition(operationId, OperationState.TRANSFERRING, TransferStage.TRANSFERRING)
        }
        require(operation.state == OperationState.TRANSFERRING) {
            "Operation is not executable from ${operation.state}"
        }
        val sourceProvider = providers[operation.source.providerId]
        val destinationProvider = providers[operation.destinationParent.providerId]
        if (sourceProvider == null || destinationProvider == null) {
            return fail(operation, StorageError.PermissionDenied)
        }
        val result = transferEngine.execute(
            sourceProvider = sourceProvider,
            destinationProvider = destinationProvider,
            request = TransferEngine.TransferRequest(
                operationId = operation.operationId,
                source = operation.source,
                destinationParent = operation.destinationParent,
                intendedFinalName = operation.intendedFinalName,
                expectedBytes = operation.expectedBytes,
                existingPartial = operation.partialDestination,
            ),
            isCancellationRequested = {
                repository.find(operationId)?.cancellationRequested == true
            },
            onStage = { stage ->
                operation = repository.transition(
                    operationId,
                    stage,
                    if (stage == OperationState.VERIFYING) TransferStage.VERIFYING else TransferStage.FINALIZING,
                )
            },
            onPartialCreated = { partial -> repository.recordPartial(operationId, partial) },
            onCheckpoint = { bytes -> repository.updateProgress(operationId, bytes) },
        )
        return when (result) {
            is TransferEngine.TransferExecution.Cancelled -> repository.recordFailure(
                operationId,
                OperationState.CANCELLED,
                TransferStage.TRANSFERRING,
                OperationErrorCode.CANCELLED,
                "Transfer cancelled; operation-owned partial output was retained for reconciliation.",
            )
            is TransferEngine.TransferExecution.Failed -> {
                result.finalizedDestination?.let { repository.recordFinalization(operationId, it) }
                fail(operation, result.error)
            }
            is TransferEngine.TransferExecution.DestinationComplete -> {
                repository.recordFinalization(operationId, result.finalLocator)
                operation = repository.transition(
                    operationId,
                    OperationState.DESTINATION_COMPLETE,
                    TransferStage.DESTINATION_COMPLETE,
                    destinationCompletionEstablished = true,
                )
                finishDestination(operation)
            }
        }
    }

    suspend fun reconcileNonTerminal(): List<OperationSnapshot> =
        repository.findNonTerminal().map { operation ->
            when {
                operation.state == OperationState.DESTINATION_COMPLETE -> finishDestination(operation)
                operation.state == OperationState.SOURCE_DELETE_PENDING -> attemptSourceDeletion(operation)
                operation.state == OperationState.RETRYABLE_FAILURE && operation.stage == TransferStage.SOURCE_DELETING -> {
                    val pending = repository.transition(
                        operation.operationId,
                        OperationState.SOURCE_DELETE_PENDING,
                        TransferStage.SOURCE_DELETING,
                        sourceDeleteState = SourceDeleteState.PENDING,
                    )
                    attemptSourceDeletion(pending)
                }
                operation.errorCode == OperationErrorCode.AMBIGUOUS_FINALIZATION &&
                    operation.stage == TransferStage.FINALIZING -> operation
                operation.stage == TransferStage.FINALIZING -> reconcileFinalizing(operation)
                else -> execute(operation.operationId)
            }
        }

    suspend fun requestCancellation(operationId: String): Boolean = repository.requestCancellation(operationId)

    private suspend fun reconcileFinalizing(operation: OperationSnapshot): OperationSnapshot {
        when (val verified = verifyDurableDestination(operation)) {
            is StorageResult.Failure -> {
                if (operation.state == OperationState.INTERRUPTED ||
                    operation.errorCode == OperationErrorCode.AMBIGUOUS_FINALIZATION
                ) {
                    return operation
                }
                return repository.recordFailure(
                    operationId = operation.operationId,
                    nextState = OperationState.INTERRUPTED,
                    stage = TransferStage.FINALIZING,
                    errorCode = OperationErrorCode.AMBIGUOUS_FINALIZATION,
                    errorMessage = "Finalization is ambiguous and needs reconciliation.",
                )
            }
            is StorageResult.Success -> Unit
        }
        val completed = repository.transition(
            operation.operationId,
            OperationState.DESTINATION_COMPLETE,
            TransferStage.DESTINATION_COMPLETE,
            destinationCompletionEstablished = true,
        )
        return finishDestination(completed)
    }

    private suspend fun finishDestination(operation: OperationSnapshot): OperationSnapshot {
        when (val verified = verifyDurableDestination(operation)) {
            is StorageResult.Failure -> return fail(operation, verified.error)
            is StorageResult.Success -> Unit
        }
        if (operation.type == OperationType.COPY) {
            return if (operation.state == OperationState.DESTINATION_COMPLETE) {
                repository.transition(operation.operationId, OperationState.COMPLETE, TransferStage.COMPLETE)
            } else {
                operation
            }
        }
        val deletePending = if (operation.state == OperationState.DESTINATION_COMPLETE) {
            repository.transition(
                operation.operationId,
                OperationState.SOURCE_DELETE_PENDING,
                TransferStage.SOURCE_DELETING,
                sourceDeleteState = SourceDeleteState.PENDING,
            )
        } else {
            operation
        }
        return attemptSourceDeletion(deletePending)
    }

    private suspend fun attemptSourceDeletion(operation: OperationSnapshot): OperationSnapshot {
        when (val verified = verifyDurableDestination(operation)) {
            is StorageResult.Failure -> return fail(operation, verified.error)
            is StorageResult.Success -> Unit
        }
        val sourceProvider = providers[operation.source.providerId]
            ?: return fail(operation, StorageError.PermissionDenied)
        val currentSource = when (val result = sourceProvider.inspectTransfer(operation.source)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> when (result.error) {
                StorageError.NotFound -> return markMoveComplete(operation)
                else -> return fail(operation, result.error)
            }
        }
        if (operation.sourceVersion != null && currentSource.versionToken != operation.sourceVersion) {
            return fail(operation, StorageError.SourceChanged)
        }
        return when (val deleted = sourceProvider.deleteDurableSource(operation.source)) {
            is StorageResult.Success -> markMoveComplete(operation)
            is StorageResult.Failure -> when (deleted.error) {
                StorageError.NotFound -> markMoveComplete(operation)
                StorageError.AmbiguousSourceDeletion -> {
                    when (val observed = sourceProvider.inspectTransfer(operation.source)) {
                        is StorageResult.Failure -> if (observed.error == StorageError.NotFound) {
                            markMoveComplete(operation)
                        } else {
                            fail(operation, deleted.error)
                        }
                        is StorageResult.Success -> fail(operation, deleted.error)
                    }
                }
                else -> fail(operation, deleted.error)
            }
        }
    }

    private suspend fun verifyDurableDestination(operation: OperationSnapshot): StorageResult<Unit> {
        val finalLocator = FinalizationRecord.decode(operation.finalizationDescription)
            ?: return StorageResult.Failure(StorageError.AmbiguousFinalization)
        val destinationProvider = providers[finalLocator.providerId]
            ?: return StorageResult.Failure(StorageError.PermissionDenied)
        val parent = when (val result = destinationProvider.resolveDurableLocator(operation.destinationParent)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return StorageResult.Failure(finalizationObservationError(result.error))
        }
        val finalEntry = when (val result = destinationProvider.resolveDurableLocator(finalLocator)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return StorageResult.Failure(finalizationObservationError(result.error))
        }
        if (parent.kind != EntryKind.DIRECTORY || finalEntry.kind != EntryKind.FILE) {
            return StorageResult.Failure(StorageError.AmbiguousFinalization)
        }
        val children = when (val result = destinationProvider.listChildren(parent.ref)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return StorageResult.Failure(finalizationObservationError(result.error))
        }
        if (children.none { it.ref == finalEntry.ref }) {
            return StorageResult.Failure(StorageError.AmbiguousFinalization)
        }
        val facts = when (val result = destinationProvider.inspectTransfer(finalLocator)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return StorageResult.Failure(finalizationObservationError(result.error))
        }
        val observedBytes = facts.sizeBytes ?: try {
            countBytes(destinationProvider, finalLocator)
        } catch (error: SecurityException) {
            return StorageResult.Failure(StorageError.PermissionDenied)
        } catch (error: IOException) {
            return StorageResult.Failure(StorageError.IoFailure(error.message))
        } catch (error: IllegalStateException) {
            return StorageResult.Failure(StorageError.IoFailure(error.message))
        } ?: return StorageResult.Failure(StorageError.IoFailure("Unable to verify unknown destination size"))
        val expectedDestinationBytes = operation.expectedBytes ?: operation.bytesCompleted
        if (observedBytes != expectedDestinationBytes) {
            return StorageResult.Failure(StorageError.AmbiguousFinalization)
        }
        return StorageResult.Success(Unit)
    }

    private fun finalizationObservationError(error: StorageError): StorageError = when (error) {
        StorageError.PermissionDenied -> StorageError.PermissionDenied
        StorageError.Cancelled -> StorageError.Cancelled
        is StorageError.IoFailure -> error
        else -> StorageError.AmbiguousFinalization
    }

    private suspend fun markMoveComplete(operation: OperationSnapshot): OperationSnapshot =
        repository.transition(
            operation.operationId,
            OperationState.COMPLETE,
            TransferStage.COMPLETE,
            sourceDeleteState = SourceDeleteState.DELETED,
        )

    private suspend fun countBytes(
        provider: StorageTransferProvider,
        locator: DurableLocator,
    ): Long? {
        val reader = when (val result = provider.openSequentialRead(locator)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return null
        }
        val buffer = ByteArray(256 * 1024)
        var count = 0L
        return try {
            while (true) {
                val read = reader.read(buffer, 0, buffer.size)
                if (read < 0) break
                if (read > 0) count += read.toLong()
            }
            count
        } finally {
            reader.close()
        }
    }

    private suspend fun fail(operation: OperationSnapshot, error: StorageError): OperationSnapshot {
        val nextState = when (error) {
            is StorageError.NameConflict -> OperationState.CONFLICTED
            StorageError.Cancelled -> OperationState.CANCELLED
            StorageError.AmbiguousFinalization -> OperationState.INTERRUPTED
            StorageError.AmbiguousSourceDeletion,
            StorageError.PermissionDenied,
            is StorageError.IoFailure -> OperationState.RETRYABLE_FAILURE
            else -> OperationState.FAILED
        }
        return repository.recordFailure(
            operationId = operation.operationId,
            nextState = nextState,
            stage = operation.stage,
            errorCode = error.toOperationErrorCode(),
            errorMessage = error.toUserMessage(),
        )
    }

    data class EnqueueItem(
        val source: DurableLocator,
        val destinationParent: DurableLocator,
        val intendedFinalName: String,
        val expectedBytes: Long?,
        val sourceVersion: String?,
    )

    private data class PreparedItem(
        val item: EnqueueItem,
        val expectedBytes: Long?,
        val sourceVersion: String?,
    )
}

private fun StorageError.toOperationErrorCode(): OperationErrorCode = when (this) {
    StorageError.PermissionDenied -> OperationErrorCode.PERMISSION_DENIED
    StorageError.NotFound, StorageError.StaleReference -> OperationErrorCode.NOT_FOUND
    StorageError.SourceChanged -> OperationErrorCode.SOURCE_CHANGED
    is StorageError.NameConflict -> OperationErrorCode.DESTINATION_CONFLICT
    StorageError.Unsupported -> OperationErrorCode.UNSUPPORTED_ROUTE
    is StorageError.IoFailure -> OperationErrorCode.PROVIDER_IO
    StorageError.Cancelled -> OperationErrorCode.CANCELLED
    StorageError.AmbiguousFinalization -> OperationErrorCode.AMBIGUOUS_FINALIZATION
    StorageError.AmbiguousSourceDeletion -> OperationErrorCode.AMBIGUOUS_SOURCE_DELETION
    is StorageError.InvalidName, is StorageError.PartialDelete -> OperationErrorCode.PROVIDER_IO
}

private fun StorageError.toUserMessage(): String = when (this) {
    StorageError.PermissionDenied -> "Permission denied by the storage provider."
    StorageError.NotFound, StorageError.StaleReference -> "The source or destination is no longer available."
    StorageError.SourceChanged -> "The source changed during transfer."
    is StorageError.NameConflict -> "The destination already contains ${requestedName}."
    StorageError.Unsupported -> "This provider route is not supported safely."
    is StorageError.IoFailure -> detail ?: "The storage provider reported an I/O failure."
    StorageError.Cancelled -> "The operation was cancelled."
    StorageError.AmbiguousFinalization -> "Finalization is ambiguous and needs reconciliation."
    StorageError.AmbiguousSourceDeletion -> "Source deletion is ambiguous and remains pending reconciliation."
    is StorageError.InvalidName -> message
    is StorageError.PartialDelete -> "Some source items could not be deleted."
}
