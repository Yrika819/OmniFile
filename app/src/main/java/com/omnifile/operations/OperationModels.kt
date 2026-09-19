package com.omnifile.operations

import com.omnifile.storage.ProviderId

/** Durable state; runtime jobs and UI lifetimes are deliberately absent. */
enum class OperationType {
    COPY,
    MOVE,
}

enum class OperationState {
    PLANNED,
    TRANSFERRING,
    VERIFYING,
    FINALIZING,
    DESTINATION_COMPLETE,
    SOURCE_DELETE_PENDING,
    COMPLETE,
    INTERRUPTED,
    CONFLICTED,
    RETRYABLE_FAILURE,
    FAILED,
    CANCELLED,
}

enum class TransferStage {
    PLANNED,
    TRANSFERRING,
    VERIFYING,
    FINALIZING,
    DESTINATION_COMPLETE,
    SOURCE_DELETING,
    COMPLETE,
}

enum class SourceDeleteState {
    NOT_REQUIRED,
    PENDING,
    DELETED,
    AMBIGUOUS,
}

enum class OperationErrorCode {
    PERMISSION_DENIED,
    NOT_FOUND,
    SOURCE_CHANGED,
    DESTINATION_CONFLICT,
    UNSUPPORTED_ROUTE,
    PROVIDER_IO,
    STORAGE_FAILURE,
    CANCELLED,
    RETRYABLE_INTERRUPTION,
    AMBIGUOUS_FINALIZATION,
}

data class DurableLocator(
    val providerId: ProviderId,
    val encoding: String,
    val value: String,
) {
    init {
        require(encoding.isNotBlank()) { "Locator encoding must not be blank" }
        require(value.isNotBlank()) { "Locator value must not be blank" }
    }
}

data class OperationSnapshot(
    val operationId: String,
    val batchId: String,
    val type: OperationType,
    val state: OperationState,
    val stage: TransferStage,
    val source: DurableLocator,
    val destinationParent: DurableLocator,
    val intendedFinalName: String,
    val partialDestination: DurableLocator?,
    val expectedBytes: Long?,
    val bytesCompleted: Long,
    val sourceVersion: String?,
    val verificationDescription: String?,
    val finalizationDescription: String?,
    val sourceDeleteState: SourceDeleteState,
    val cancellationRequested: Boolean,
    val errorCode: OperationErrorCode?,
    val errorMessage: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(operationId.isNotBlank()) { "Operation ID must not be blank" }
        require(batchId.isNotBlank()) { "Batch ID must not be blank" }
        require(intendedFinalName.isNotBlank()) { "Final name must not be blank" }
        require(bytesCompleted >= 0L) { "Completed bytes cannot be negative" }
        require(expectedBytes == null || expectedBytes >= 0L) { "Expected bytes cannot be negative" }
        require(createdAtEpochMillis <= updatedAtEpochMillis) {
            "Updated time cannot precede creation time"
        }
        if (type == OperationType.COPY) {
            require(sourceDeleteState == SourceDeleteState.NOT_REQUIRED) {
                "Copy cannot carry source-delete state"
            }
        }
    }
}
