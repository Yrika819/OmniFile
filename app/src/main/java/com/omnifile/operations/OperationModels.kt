package com.omnifile.operations

import com.omnifile.storage.ProviderId
import java.util.Base64

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
    PROVIDER_UNAVAILABLE,
    STORAGE_FAILURE,
    CANCELLED,
    RETRYABLE_INTERRUPTION,
    AMBIGUOUS_FINALIZATION,
    AMBIGUOUS_SOURCE_DELETION,
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

object FinalizationRecord {
    private const val LEGACY_PREFIX = "finalization-v1"
    private const val PREFIX = "finalization-v2"

    fun encode(locator: DurableLocator): String = listOf(
        PREFIX,
        encodePart(locator.providerId.value),
        encodePart(locator.encoding),
        encodePart(locator.value),
    ).joinToString(".")

    fun decode(description: String?): DurableLocator? {
        val parts = description?.split("\u001f") ?: return null
        if (parts.size == 4 && parts[0] == LEGACY_PREFIX) {
            return durableLocator(parts[1], parts[2], parts[3])
        }
        val encoded = description.split(".")
        if (encoded.size != 4 || encoded[0] != PREFIX) return null
        return try {
            durableLocator(
                decodePart(encoded[1]),
                decodePart(encoded[2]),
                decodePart(encoded[3]),
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun durableLocator(providerId: String, encoding: String, value: String): DurableLocator =
        DurableLocator(ProviderId(providerId), encoding, value)

    private fun encodePart(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodePart(value: String): String =
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
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
