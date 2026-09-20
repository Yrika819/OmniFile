package com.omnifile.operations

interface OperationRepository {
    suspend fun create(snapshot: OperationSnapshot)
    suspend fun find(operationId: String): OperationSnapshot?
    suspend fun findNonTerminal(): List<OperationSnapshot>
    suspend fun findRecent(limit: Int = 50): List<OperationSnapshot>
    suspend fun transition(
        operationId: String,
        nextState: OperationState,
        nextStage: TransferStage,
        sourceDeleteState: SourceDeleteState? = null,
        destinationCompletionEstablished: Boolean = false,
    ): OperationSnapshot
    suspend fun recordPartial(operationId: String, partial: DurableLocator)
    suspend fun recordFailure(
        operationId: String,
        nextState: OperationState,
        stage: TransferStage,
        errorCode: OperationErrorCode,
        errorMessage: String?,
    ): OperationSnapshot
    suspend fun recordFinalization(operationId: String, finalLocator: DurableLocator)
    suspend fun updateProgress(operationId: String, bytesCompleted: Long)
    suspend fun requestCancellation(operationId: String): Boolean
}
