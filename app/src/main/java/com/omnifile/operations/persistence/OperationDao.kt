package com.omnifile.operations.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OperationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(operation: OperationEntity)

    @Query("SELECT * FROM operations WHERE operationId = :operationId")
    suspend fun find(operationId: String): OperationEntity?

    @Query("SELECT * FROM operations ORDER BY updatedAtEpochMillis DESC LIMIT :limit")
    suspend fun findRecent(limit: Int = 50): List<OperationEntity>

    @Query("SELECT * FROM operations WHERE state NOT IN (:terminalStates) ORDER BY createdAtEpochMillis")
    suspend fun findNonTerminal(terminalStates: Set<String> = TERMINAL_STATES): List<OperationEntity>

    @Query("""
        UPDATE operations
        SET state = :nextState,
            stage = :nextStage,
            sourceDeleteState = :sourceDeleteState,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE operationId = :operationId AND state = :expectedState
    """)
    suspend fun compareAndSetState(
        operationId: String,
        expectedState: String,
        nextState: String,
        nextStage: String,
        sourceDeleteState: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("""
        UPDATE operations
        SET partialProviderId = :providerId,
            partialLocatorEncoding = :locatorEncoding,
            partialLocatorValue = :locatorValue,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE operationId = :operationId
    """)
    suspend fun updatePartial(
        operationId: String,
        providerId: String,
        locatorEncoding: String,
        locatorValue: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("""
        UPDATE operations
        SET state = :nextState,
            stage = :nextStage,
            sourceDeleteState = :sourceDeleteState,
            errorCode = :errorCode,
            errorMessage = :errorMessage,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE operationId = :operationId AND state = :expectedState
    """)
    suspend fun recordFailure(
        operationId: String,
        expectedState: String,
        nextState: String,
        nextStage: String,
        sourceDeleteState: String,
        errorCode: String,
        errorMessage: String?,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("""
        UPDATE operations
        SET finalizationDescription = :description,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE operationId = :operationId
    """)
    suspend fun recordFinalization(
        operationId: String,
        description: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("""
        UPDATE operations
        SET bytesCompleted = :bytesCompleted,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE operationId = :operationId
    """)
    suspend fun updateProgress(
        operationId: String,
        bytesCompleted: Long,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("""
        UPDATE operations
        SET cancellationRequested = 1,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE operationId = :operationId AND state NOT IN (:terminalStates)
    """)
    suspend fun requestCancellation(
        operationId: String,
        updatedAtEpochMillis: Long,
        terminalStates: Set<String> = TERMINAL_STATES,
    ): Int

    companion object {
        val TERMINAL_STATES = setOf("COMPLETE", "CONFLICTED", "FAILED", "CANCELLED")
    }
}
