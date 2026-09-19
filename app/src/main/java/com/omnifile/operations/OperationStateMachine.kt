package com.omnifile.operations

object OperationStateMachine {
    private val legalTransitions = mapOf(
        OperationState.PLANNED to setOf(
            OperationState.TRANSFERRING,
            OperationState.INTERRUPTED,
            OperationState.CONFLICTED,
            OperationState.RETRYABLE_FAILURE,
            OperationState.FAILED,
            OperationState.CANCELLED,
        ),
        OperationState.TRANSFERRING to setOf(
            OperationState.VERIFYING,
            OperationState.INTERRUPTED,
            OperationState.RETRYABLE_FAILURE,
            OperationState.FAILED,
            OperationState.CANCELLED,
        ),
        OperationState.VERIFYING to setOf(
            OperationState.FINALIZING,
            OperationState.INTERRUPTED,
            OperationState.RETRYABLE_FAILURE,
            OperationState.FAILED,
            OperationState.CANCELLED,
        ),
        OperationState.FINALIZING to setOf(
            OperationState.DESTINATION_COMPLETE,
            OperationState.INTERRUPTED,
            OperationState.CONFLICTED,
            OperationState.RETRYABLE_FAILURE,
            OperationState.FAILED,
            OperationState.CANCELLED,
        ),
        OperationState.DESTINATION_COMPLETE to setOf(
            OperationState.SOURCE_DELETE_PENDING,
            OperationState.COMPLETE,
            OperationState.INTERRUPTED,
            OperationState.RETRYABLE_FAILURE,
            OperationState.FAILED,
        ),
        OperationState.SOURCE_DELETE_PENDING to setOf(
            OperationState.COMPLETE,
            OperationState.INTERRUPTED,
            OperationState.RETRYABLE_FAILURE,
            OperationState.FAILED,
        ),
        OperationState.INTERRUPTED to setOf(
            OperationState.TRANSFERRING,
            OperationState.VERIFYING,
            OperationState.FINALIZING,
            OperationState.DESTINATION_COMPLETE,
            OperationState.SOURCE_DELETE_PENDING,
            OperationState.CONFLICTED,
            OperationState.RETRYABLE_FAILURE,
            OperationState.FAILED,
            OperationState.CANCELLED,
        ),
        OperationState.RETRYABLE_FAILURE to setOf(
            OperationState.TRANSFERRING,
            OperationState.VERIFYING,
            OperationState.FINALIZING,
            OperationState.DESTINATION_COMPLETE,
            OperationState.SOURCE_DELETE_PENDING,
            OperationState.INTERRUPTED,
            OperationState.FAILED,
            OperationState.CANCELLED,
        ),
        OperationState.CONFLICTED to emptySet(),
        OperationState.FAILED to emptySet(),
        OperationState.CANCELLED to emptySet(),
        OperationState.COMPLETE to emptySet(),
    )

    fun requireTransition(
        type: OperationType,
        from: OperationState,
        to: OperationState,
        sourceDeleteState: SourceDeleteState = defaultSourceDeleteState(type),
        destinationCompletionEstablished: Boolean = false,
    ) {
        require(to in legalTransitions.getValue(from)) {
            "Illegal operation transition: $from -> $to"
        }
        if (to == OperationState.SOURCE_DELETE_PENDING) {
            require(type == OperationType.MOVE) {
                "Copy cannot enter source-delete pending"
            }
            require(from == OperationState.DESTINATION_COMPLETE) {
                "Source deletion requires durable destination completion"
            }
            require(sourceDeleteState == SourceDeleteState.PENDING) {
                "Source deletion must begin in pending state"
            }
        }
        if (to == OperationState.DESTINATION_COMPLETE) {
            require(destinationCompletionEstablished) {
                "Destination completion requires provider-established evidence"
            }
        }
        if (to == OperationState.COMPLETE) {
            require(
                type == OperationType.COPY && from == OperationState.DESTINATION_COMPLETE ||
                    type == OperationType.MOVE &&
                    from == OperationState.SOURCE_DELETE_PENDING &&
                    sourceDeleteState == SourceDeleteState.DELETED,
            ) {
                "Operation cannot complete before its irreversible stages are durable"
            }
        }
    }

    fun isTerminal(state: OperationState): Boolean =
        state == OperationState.COMPLETE ||
            state == OperationState.CONFLICTED ||
            state == OperationState.FAILED ||
            state == OperationState.CANCELLED

    private fun defaultSourceDeleteState(type: OperationType): SourceDeleteState =
        if (type == OperationType.MOVE) SourceDeleteState.PENDING else SourceDeleteState.NOT_REQUIRED
}
