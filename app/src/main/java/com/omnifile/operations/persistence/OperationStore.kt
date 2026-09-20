package com.omnifile.operations.persistence

import com.omnifile.operations.OperationSnapshot
import com.omnifile.operations.OperationState
import com.omnifile.operations.OperationStateMachine
import com.omnifile.operations.SourceDeleteState

class OperationStore(
    private val dao: OperationDao,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) : com.omnifile.operations.OperationRepository {
    override suspend fun create(snapshot: OperationSnapshot) {
        require(snapshot.state == OperationState.PLANNED) {
            "New operations must start in PLANNED"
        }
        dao.insert(OperationEntity.from(snapshot))
    }

    override suspend fun find(operationId: String): OperationSnapshot? =
        dao.find(operationId)?.toSnapshot()

    override suspend fun findNonTerminal(): List<OperationSnapshot> =
        dao.findNonTerminal().map(OperationEntity::toSnapshot)

    override suspend fun findRecent(limit: Int): List<OperationSnapshot> =
        dao.findRecent(limit).map(OperationEntity::toSnapshot)

    override suspend fun transition(
        operationId: String,
        nextState: OperationState,
        nextStage: com.omnifile.operations.TransferStage,
        sourceDeleteState: SourceDeleteState?,
        destinationCompletionEstablished: Boolean,
    ): OperationSnapshot {
        val current = requireNotNull(find(operationId)) { "Unknown operation: $operationId" }
        val nextDeleteState = sourceDeleteState ?: current.sourceDeleteState
        OperationStateMachine.requireTransition(
            type = current.type,
            from = current.state,
            to = nextState,
            sourceDeleteState = nextDeleteState,
            destinationCompletionEstablished = destinationCompletionEstablished,
        )
        check(
            dao.compareAndSetState(
                operationId = operationId,
                expectedState = current.state.name,
                nextState = nextState.name,
                nextStage = nextStage.name,
                sourceDeleteState = nextDeleteState.name,
                updatedAtEpochMillis = nowEpochMillis(),
            ) == 1,
        ) {
            "Operation changed while transitioning: $operationId"
        }
        return requireNotNull(find(operationId))
    }

    override suspend fun recordPartial(operationId: String, partial: com.omnifile.operations.DurableLocator) {
        check(dao.updatePartial(
            operationId = operationId,
            providerId = partial.providerId.value,
            locatorEncoding = partial.encoding,
            locatorValue = partial.value,
            updatedAtEpochMillis = nowEpochMillis(),
        ) == 1) { "Unknown operation: $operationId" }
    }

    override suspend fun recordFailure(
        operationId: String,
        nextState: OperationState,
        stage: com.omnifile.operations.TransferStage,
        errorCode: com.omnifile.operations.OperationErrorCode,
        errorMessage: String?,
    ): OperationSnapshot {
        val current = requireNotNull(find(operationId)) { "Unknown operation: $operationId" }
        OperationStateMachine.requireTransition(
            type = current.type,
            from = current.state,
            to = nextState,
            sourceDeleteState = current.sourceDeleteState,
        )
        check(dao.recordFailure(
            operationId = operationId,
            expectedState = current.state.name,
            nextState = nextState.name,
            nextStage = stage.name,
            sourceDeleteState = current.sourceDeleteState.name,
            errorCode = errorCode.name,
            errorMessage = errorMessage,
            updatedAtEpochMillis = nowEpochMillis(),
        ) == 1) { "Operation changed while recording failure: $operationId" }
        return requireNotNull(find(operationId))
    }

    override suspend fun recordFinalization(
        operationId: String,
        finalLocator: com.omnifile.operations.DurableLocator,
    ) {
        check(dao.recordFinalization(
            operationId = operationId,
            description = com.omnifile.operations.FinalizationRecord.encode(finalLocator),
            updatedAtEpochMillis = nowEpochMillis(),
        ) == 1) { "Unknown operation: $operationId" }
    }

    override suspend fun updateProgress(operationId: String, bytesCompleted: Long) {
        require(bytesCompleted >= 0L) { "Completed bytes cannot be negative" }
        check(dao.updateProgress(operationId, bytesCompleted, nowEpochMillis()) == 1) {
            "Unknown operation: $operationId"
        }
    }

    override suspend fun requestCancellation(operationId: String): Boolean =
        dao.requestCancellation(operationId, nowEpochMillis()) == 1
}
