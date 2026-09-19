package com.omnifile.operations

import com.omnifile.storage.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationStateMachineTest {
    @Test
    fun copyLifecycleRequiresVerifyAndFinalizeBeforeComplete() {
        OperationStateMachine.requireTransition(OperationType.COPY, OperationState.PLANNED, OperationState.TRANSFERRING)
        OperationStateMachine.requireTransition(OperationType.COPY, OperationState.TRANSFERRING, OperationState.VERIFYING)
        OperationStateMachine.requireTransition(OperationType.COPY, OperationState.VERIFYING, OperationState.FINALIZING)
        OperationStateMachine.requireTransition(
            OperationType.COPY,
            OperationState.FINALIZING,
            OperationState.DESTINATION_COMPLETE,
            destinationCompletionEstablished = true,
        )
        OperationStateMachine.requireTransition(OperationType.COPY, OperationState.DESTINATION_COMPLETE, OperationState.COMPLETE)
    }

    @Test
    fun moveCannotDeleteSourceBeforeDurableDestinationCompletion() {
        assertThrows(IllegalArgumentException::class.java) {
            OperationStateMachine.requireTransition(
                OperationType.MOVE,
                OperationState.FINALIZING,
                OperationState.SOURCE_DELETE_PENDING,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationStateMachine.requireTransition(
                OperationType.MOVE,
                OperationState.FINALIZING,
                OperationState.DESTINATION_COMPLETE,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperationStateMachine.requireTransition(
                OperationType.MOVE,
                OperationState.DESTINATION_COMPLETE,
                OperationState.COMPLETE,
                SourceDeleteState.PENDING,
            )
        }
    }

    @Test
    fun moveCompletesOnlyAfterSourceDeletionIsDurable() {
        OperationStateMachine.requireTransition(
            OperationType.MOVE,
            OperationState.FINALIZING,
            OperationState.DESTINATION_COMPLETE,
            destinationCompletionEstablished = true,
        )
        OperationStateMachine.requireTransition(
            OperationType.MOVE,
            OperationState.DESTINATION_COMPLETE,
            OperationState.SOURCE_DELETE_PENDING,
        )
        OperationStateMachine.requireTransition(
            OperationType.MOVE,
            OperationState.SOURCE_DELETE_PENDING,
            OperationState.COMPLETE,
            SourceDeleteState.DELETED,
        )
    }

    @Test
    fun bytesAtExpectedSizeDoNotMakeTransferComplete() {
        assertThrows(IllegalArgumentException::class.java) {
            OperationStateMachine.requireTransition(
                OperationType.COPY,
                OperationState.TRANSFERRING,
                OperationState.COMPLETE,
            )
        }
    }

    @Test
    fun interruptedOperationCanBeReconciledWithoutBlindReplay() {
        OperationStateMachine.requireTransition(OperationType.COPY, OperationState.TRANSFERRING, OperationState.INTERRUPTED)
        assertThrows(IllegalArgumentException::class.java) {
            OperationStateMachine.requireTransition(
                OperationType.COPY,
                OperationState.INTERRUPTED,
                OperationState.DESTINATION_COMPLETE,
            )
        }
        OperationStateMachine.requireTransition(
            OperationType.COPY,
            OperationState.INTERRUPTED,
            OperationState.DESTINATION_COMPLETE,
            destinationCompletionEstablished = true,
        )
    }

    @Test
    fun terminalStatesCannotBeReopened() {
        assertTrue(OperationStateMachine.isTerminal(OperationState.COMPLETE))
        assertTrue(OperationStateMachine.isTerminal(OperationState.CONFLICTED))
        assertFalse(OperationStateMachine.isTerminal(OperationState.INTERRUPTED))
        assertThrows(IllegalArgumentException::class.java) {
            OperationStateMachine.requireTransition(OperationType.COPY, OperationState.COMPLETE, OperationState.TRANSFERRING)
        }
    }

    @Test
    fun durableLocatorRejectsEmptyParts() {
        assertThrows(IllegalArgumentException::class.java) {
            DurableLocator(ProviderId("local"), "relative-path", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DurableLocator(ProviderId("local"), "", "root/file")
        }
    }

    @Test
    fun operationSnapshotUsesLongCountersAndValidMoveDeleteState() {
        val snapshot = OperationSnapshot(
            operationId = "op-1",
            batchId = "batch-1",
            type = OperationType.MOVE,
            state = OperationState.TRANSFERRING,
            stage = TransferStage.TRANSFERRING,
            source = DurableLocator(ProviderId("local"), "root-relative", "source.bin"),
            destinationParent = DurableLocator(ProviderId("local"), "root-relative", "destination"),
            intendedFinalName = "source.bin",
            partialDestination = null,
            expectedBytes = 5_000_000_000L,
            bytesCompleted = 4_000_000_000L,
            sourceVersion = "size:5000000000",
            verificationDescription = null,
            finalizationDescription = null,
            sourceDeleteState = SourceDeleteState.PENDING,
            cancellationRequested = false,
            errorCode = null,
            errorMessage = null,
            createdAtEpochMillis = 10L,
            updatedAtEpochMillis = 20L,
        )

        assertEquals(5_000_000_000L, snapshot.expectedBytes)
        assertEquals(SourceDeleteState.PENDING, snapshot.sourceDeleteState)
    }
}
