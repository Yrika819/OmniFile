package com.omnifile.operations.persistence

import androidx.room.Entity
import com.omnifile.operations.DurableLocator
import com.omnifile.operations.OperationErrorCode
import com.omnifile.operations.OperationSnapshot
import com.omnifile.operations.OperationState
import com.omnifile.operations.OperationType
import com.omnifile.operations.SourceDeleteState
import com.omnifile.operations.TransferStage
import com.omnifile.storage.ProviderId

@Entity(tableName = "operations")
data class OperationEntity(
    @androidx.room.PrimaryKey val operationId: String,
    val batchId: String,
    val type: String,
    val state: String,
    val stage: String,
    val sourceProviderId: String,
    val sourceLocatorEncoding: String,
    val sourceLocatorValue: String,
    val destinationProviderId: String,
    val destinationLocatorEncoding: String,
    val destinationLocatorValue: String,
    val intendedFinalName: String,
    val partialProviderId: String?,
    val partialLocatorEncoding: String?,
    val partialLocatorValue: String?,
    val expectedBytes: Long?,
    val bytesCompleted: Long,
    val sourceVersion: String?,
    val verificationDescription: String?,
    val finalizationDescription: String?,
    val sourceDeleteState: String,
    val cancellationRequested: Boolean,
    val errorCode: String?,
    val errorMessage: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    fun toSnapshot(): OperationSnapshot = OperationSnapshot(
        operationId = operationId,
        batchId = batchId,
        type = OperationType.valueOf(type),
        state = OperationState.valueOf(state),
        stage = TransferStage.valueOf(stage),
        source = DurableLocator(
            providerId = ProviderId(sourceProviderId),
            encoding = sourceLocatorEncoding,
            value = sourceLocatorValue,
        ),
        destinationParent = DurableLocator(
            providerId = ProviderId(destinationProviderId),
            encoding = destinationLocatorEncoding,
            value = destinationLocatorValue,
        ),
        intendedFinalName = intendedFinalName,
        partialDestination = partialLocatorValue?.let {
            DurableLocator(
                providerId = ProviderId(requireNotNull(partialProviderId)),
                encoding = requireNotNull(partialLocatorEncoding),
                value = it,
            )
        },
        expectedBytes = expectedBytes,
        bytesCompleted = bytesCompleted,
        sourceVersion = sourceVersion,
        verificationDescription = verificationDescription,
        finalizationDescription = finalizationDescription,
        sourceDeleteState = SourceDeleteState.valueOf(sourceDeleteState),
        cancellationRequested = cancellationRequested,
        errorCode = errorCode?.let(OperationErrorCode::valueOf),
        errorMessage = errorMessage,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    companion object {
        fun from(snapshot: OperationSnapshot): OperationEntity = OperationEntity(
            operationId = snapshot.operationId,
            batchId = snapshot.batchId,
            type = snapshot.type.name,
            state = snapshot.state.name,
            stage = snapshot.stage.name,
            sourceProviderId = snapshot.source.providerId.value,
            sourceLocatorEncoding = snapshot.source.encoding,
            sourceLocatorValue = snapshot.source.value,
            destinationProviderId = snapshot.destinationParent.providerId.value,
            destinationLocatorEncoding = snapshot.destinationParent.encoding,
            destinationLocatorValue = snapshot.destinationParent.value,
            intendedFinalName = snapshot.intendedFinalName,
            partialProviderId = snapshot.partialDestination?.providerId?.value,
            partialLocatorEncoding = snapshot.partialDestination?.encoding,
            partialLocatorValue = snapshot.partialDestination?.value,
            expectedBytes = snapshot.expectedBytes,
            bytesCompleted = snapshot.bytesCompleted,
            sourceVersion = snapshot.sourceVersion,
            verificationDescription = snapshot.verificationDescription,
            finalizationDescription = snapshot.finalizationDescription,
            sourceDeleteState = snapshot.sourceDeleteState.name,
            cancellationRequested = snapshot.cancellationRequested,
            errorCode = snapshot.errorCode?.name,
            errorMessage = snapshot.errorMessage,
            createdAtEpochMillis = snapshot.createdAtEpochMillis,
            updatedAtEpochMillis = snapshot.updatedAtEpochMillis,
        )
    }
}
