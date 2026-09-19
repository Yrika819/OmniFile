package com.omnifile.operations

import com.omnifile.storage.EntryKind
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import com.omnifile.storage.StorageTransferProvider
import java.util.UUID

class OperationManager(
    private val repository: OperationRepository,
    private val providers: Map<ProviderId, StorageTransferProvider>,
    private val transferEngine: TransferEngine = TransferEngine(),
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun enqueue(
        type: OperationType,
        items: List<EnqueueItem>,
        batchId: String = UUID.randomUUID().toString(),
    ): StorageResult<List<String>> {
        require(items.isNotEmpty()) { "At least one operation item is required" }
        items.forEach { item ->
            val source = providers[item.source.providerId]
                ?: return StorageResult.Failure(StorageError.Unsupported)
            val destination = providers[item.destinationParent.providerId]
                ?: return StorageResult.Failure(StorageError.Unsupported)
            val requiredDestination = setOf(
                com.omnifile.storage.TransferCapability.CREATE_CHILD,
                com.omnifile.storage.TransferCapability.WRITE_SEQUENTIAL,
                com.omnifile.storage.TransferCapability.FINALIZE,
            )
            if (!source.transferCapabilities.contains(com.omnifile.storage.TransferCapability.READ_SEQUENTIAL) ||
                !destination.transferCapabilities.containsAll(requiredDestination) ||
                type == OperationType.MOVE &&
                !source.transferCapabilities.contains(com.omnifile.storage.TransferCapability.DELETE)
            ) {
                return StorageResult.Failure(StorageError.Unsupported)
            }
        }
        val now = nowEpochMillis()
        val ids = items.map { item ->
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
                    expectedBytes = item.expectedBytes,
                    bytesCompleted = 0L,
                    sourceVersion = item.sourceVersion,
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
        val sourceProvider = requireNotNull(providers[operation.source.providerId])
        val destinationProvider = requireNotNull(providers[operation.destinationParent.providerId])
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
            is TransferEngine.TransferExecution.Failed -> fail(operation, result.error)
            is TransferEngine.TransferExecution.DestinationComplete -> {
                operation = repository.transition(
                    operationId,
                    OperationState.DESTINATION_COMPLETE,
                    TransferStage.DESTINATION_COMPLETE,
                    destinationCompletionEstablished = true,
                )
                completeAfterDestination(operation)
            }
        }
    }

    suspend fun reconcileNonTerminal(): List<OperationSnapshot> =
        repository.findNonTerminal().map { operation ->
            when {
                operation.state == OperationState.DESTINATION_COMPLETE ||
                    operation.state == OperationState.SOURCE_DELETE_PENDING -> completeAfterDestination(operation)
                operation.stage == TransferStage.FINALIZING ->
                    reconcileFinalizing(operation) ?: execute(operation.operationId)
                else -> execute(operation.operationId)
            }
        }

    suspend fun requestCancellation(operationId: String): Boolean = repository.requestCancellation(operationId)

    private suspend fun reconcileFinalizing(operation: OperationSnapshot): OperationSnapshot? {
        val destinationProvider = providers[operation.destinationParent.providerId] ?: return null
        val parent = when (val result = destinationProvider.resolveDurableLocator(operation.destinationParent)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return null
        }
        val candidate = when (val result = destinationProvider.listChildren(parent.ref)) {
            is StorageResult.Success -> result.value.firstOrNull { it.displayName == operation.intendedFinalName }
            is StorageResult.Failure -> return null
        } ?: return null
        if (candidate.kind != EntryKind.FILE ||
            operation.expectedBytes == null ||
            candidate.sizeBytes != operation.expectedBytes
        ) return null
        val candidateLocator = when (val result = destinationProvider.encodeDurableLocator(candidate.ref)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return null
        }
        val facts = when (val result = destinationProvider.inspectTransfer(candidateLocator)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> return null
        }
        if (facts.kind != EntryKind.FILE || facts.sizeBytes != operation.expectedBytes) return null
        val completed = repository.transition(
            operation.operationId,
            OperationState.DESTINATION_COMPLETE,
            TransferStage.DESTINATION_COMPLETE,
            destinationCompletionEstablished = true,
        )
        return completeAfterDestination(completed)
    }

    private suspend fun completeAfterDestination(operation: OperationSnapshot): OperationSnapshot {
        if (operation.type == OperationType.COPY) {
            return repository.transition(operation.operationId, OperationState.COMPLETE, TransferStage.COMPLETE)
        }
        val sourceProvider = requireNotNull(providers[operation.source.providerId])
        return when (val deleted = sourceProvider.deleteDurableSource(operation.source)) {
            is StorageResult.Success -> repository.transition(
                operation.operationId,
                OperationState.COMPLETE,
                TransferStage.COMPLETE,
                sourceDeleteState = SourceDeleteState.DELETED,
            )
            is StorageResult.Failure -> fail(operation, deleted.error)
        }
    }

    private suspend fun fail(operation: OperationSnapshot, error: StorageError): OperationSnapshot {
        val nextState = when (error) {
            is StorageError.NameConflict -> OperationState.CONFLICTED
            StorageError.Cancelled -> OperationState.CANCELLED
            StorageError.AmbiguousFinalization -> OperationState.INTERRUPTED
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
    is StorageError.InvalidName -> message
    is StorageError.PartialDelete -> "Some source items could not be deleted."
}
