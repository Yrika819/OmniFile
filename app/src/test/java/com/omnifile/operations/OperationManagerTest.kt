package com.omnifile.operations

import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import com.omnifile.storage.StorageTransferProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class OperationManagerTest {
    @Test
    fun copyReachesCompleteOnlyAfterFinalization() = runBlocking {
        withLocalFixture { root, provider, sourceLocator, destinationLocator ->
            val repository = InMemoryRepository()
            val manager = OperationManager(repository, mapOf(provider.id to provider))
            val ids = manager.enqueue(
                OperationType.COPY,
                listOf(OperationManager.EnqueueItem(sourceLocator, destinationLocator, "copy.txt", 4L, null)),
            ).requireSuccess()

            val completed = manager.execute(ids.single())

            assertEquals(OperationState.COMPLETE, completed.state)
            assertTrue(Files.exists(root.resolve("copy.txt")))
            assertTrue(Files.exists(root.resolve("source.txt")))
        }
    }

    @Test
    fun destinationConflictBecomesDurableConflictWithoutOverwritingExistingFile() = runBlocking {
        withLocalFixture { root, provider, sourceLocator, destinationLocator ->
            Files.write(root.resolve("copy.txt"), byteArrayOf(9))
            val repository = InMemoryRepository()
            val manager = OperationManager(repository, mapOf(provider.id to provider))
            val id = manager.enqueue(
                OperationType.COPY,
                listOf(OperationManager.EnqueueItem(sourceLocator, destinationLocator, "copy.txt", 4L, null)),
            ).requireSuccess().single()

            val conflicted = manager.execute(id)

            assertEquals(OperationState.CONFLICTED, conflicted.state)
            assertTrue(Files.exists(root.resolve("source.txt")))
            assertTrue(Files.exists(root.resolve(".omnifile-$id.partial").normalize()).not())
            assertEquals(listOf(9.toByte()), Files.readAllBytes(root.resolve("copy.txt")).toList())
        }
    }

    @Test
    fun moveNeverDeletesSourceWhenTransferFaultsAfterPartialCreation() = runBlocking {
        withLocalFixture { root, provider, sourceLocator, destinationLocator ->
            val repository = InMemoryRepository()
            val engine = TransferEngine(
                faultInjector = TransferEngine.TransferFaultInjector { boundary, _ ->
                    if (boundary == TransferEngine.TransferFaultBoundary.AFTER_PARTIAL_CREATE) {
                        StorageError.IoFailure("injected")
                    } else null
                },
            )
            val manager = OperationManager(repository, mapOf(provider.id to provider), engine)
            val id = manager.enqueue(
                OperationType.MOVE,
                listOf(OperationManager.EnqueueItem(sourceLocator, destinationLocator, "moved.txt", 4L, null)),
            ).requireSuccess().single()

            val failed = manager.execute(id)

            assertEquals(OperationState.RETRYABLE_FAILURE, failed.state)
            assertTrue(Files.exists(root.resolve("source.txt")))
            assertFalse(Files.exists(root.resolve("moved.txt")))
        }
    }

    @Test
    fun providerUnavailableBeforeExecutionIsRetryableAndRecoveryDoesNotDuplicateDestination() = runBlocking {
        withLocalFixture { root, provider, sourceLocator, destinationLocator ->
            val repository = InMemoryRepository()
            val injected = InjectingProvider(provider)
            val manager = OperationManager(repository, mapOf(injected.id to injected))
            val id = manager.enqueue(
                OperationType.COPY,
                listOf(OperationManager.EnqueueItem(sourceLocator, destinationLocator, "recovered.txt", 4L, null)),
            ).requireSuccess().single()

            injected.unavailable = true
            val unavailable = manager.execute(id)

            assertEquals(OperationState.RETRYABLE_FAILURE, unavailable.state)
            assertEquals(OperationErrorCode.PROVIDER_UNAVAILABLE, unavailable.errorCode)
            assertTrue(Files.exists(root.resolve("source.txt")))
            assertFalse(Files.exists(root.resolve("recovered.txt")))

            injected.unavailable = false
            val recovered = manager.execute(id)

            assertEquals(OperationState.COMPLETE, recovered.state)
            assertEquals(1, injected.partialCreates)
            assertEquals(byteArrayOf(1, 2, 3, 4).toList(), Files.readAllBytes(root.resolve("recovered.txt")).toList())
        }
    }

    @Test
    fun providerUnavailableDuringSourceDeleteLeavesPendingStateAndRecoveryDoesNotRecopy() = runBlocking {
        withLocalFixture { root, provider, sourceLocator, destinationLocator ->
            val repository = InMemoryRepository()
            val injected = InjectingProvider(provider, failNextDelete = true)
            val manager = OperationManager(repository, mapOf(injected.id to injected))
            val id = manager.enqueue(
                OperationType.MOVE,
                listOf(OperationManager.EnqueueItem(sourceLocator, destinationLocator, "pending.txt", 4L, null)),
            ).requireSuccess().single()

            val pending = manager.execute(id)

            assertEquals(OperationState.RETRYABLE_FAILURE, pending.state)
            assertEquals(TransferStage.SOURCE_DELETING, pending.stage)
            assertEquals(SourceDeleteState.PENDING, pending.sourceDeleteState)
            assertEquals(OperationErrorCode.PROVIDER_UNAVAILABLE, pending.errorCode)
            assertTrue(Files.exists(root.resolve("source.txt")))
            assertEquals(byteArrayOf(1, 2, 3, 4).toList(), Files.readAllBytes(root.resolve("pending.txt")).toList())

            val recovered = manager.execute(id)

            assertEquals(OperationState.COMPLETE, recovered.state)
            assertEquals(SourceDeleteState.DELETED, recovered.sourceDeleteState)
            assertFalse(Files.exists(root.resolve("source.txt")))
            assertEquals(1, injected.partialCreates)
            assertEquals(byteArrayOf(1, 2, 3, 4).toList(), Files.readAllBytes(root.resolve("pending.txt")).toList())
        }
    }

    @Test
    fun missingProviderIsReportedAsPermissionFailureBeforeDurableRecord() = runBlocking {
        val repository = InMemoryRepository()
        val sourceRoot = Files.createTempDirectory("omnifile-route")
        try {
            val provider = LocalStorageProvider(sourceRoot, ProviderId("local"))
            val locator = provider.encodeDurableLocator(provider.root().requireSuccess().ref).requireSuccess()
            val result = OperationManager(repository, emptyMap()).enqueue(
                OperationType.COPY,
                listOf(OperationManager.EnqueueItem(locator, locator, "copy.txt", 0L, null)),
            )
            assertEquals(StorageError.PermissionDenied, result.failure().error)
            assertTrue(repository.records.isEmpty())
        } finally {
            sourceRoot.toFile().deleteRecursively()
        }
    }

    private suspend fun withLocalFixture(
        block: suspend (java.nio.file.Path, StorageTransferProvider, DurableLocator, DurableLocator) -> Unit,
    ) {
        val root = Files.createTempDirectory("omnifile-manager")
        try {
            Files.write(root.resolve("source.txt"), byteArrayOf(1, 2, 3, 4))
            val provider = LocalStorageProvider(root, ProviderId("local"))
            val rootEntry = provider.root().requireSuccess()
            val source = provider.listChildren(rootEntry.ref).requireSuccess().single()
            val sourceLocator = provider.encodeDurableLocator(source.ref).requireSuccess()
            val destinationLocator = provider.encodeDurableLocator(rootEntry.ref).requireSuccess()
            block(root, provider, sourceLocator, destinationLocator)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private class InjectingProvider(
        private val delegate: StorageTransferProvider,
        var unavailable: Boolean = false,
        private var failNextDelete: Boolean = false,
    ) : StorageTransferProvider by delegate {
        var partialCreates: Int = 0

        override suspend fun inspectTransfer(locator: DurableLocator): StorageResult<com.omnifile.storage.TransferFileFacts> =
            if (unavailable) StorageResult.Failure(StorageError.ProviderUnavailable)
            else delegate.inspectTransfer(locator)

        override suspend fun createOperationPartial(
            destinationParent: DurableLocator,
            intendedFinalName: String,
            operationId: String,
        ): StorageResult<DurableLocator> = delegate.createOperationPartial(
            destinationParent,
            intendedFinalName,
            operationId,
        ).also { result ->
            if (result is StorageResult.Success) partialCreates += 1
        }

        override suspend fun deleteDurableSource(source: DurableLocator): StorageResult<Unit> {
            if (failNextDelete) {
                failNextDelete = false
                return StorageResult.Failure(StorageError.ProviderUnavailable)
            }
            return delegate.deleteDurableSource(source)
        }
    }

    private class InMemoryRepository : OperationRepository {
        val records = linkedMapOf<String, OperationSnapshot>()
        override suspend fun create(snapshot: OperationSnapshot) { records[snapshot.operationId] = snapshot }
        override suspend fun find(operationId: String) = records[operationId]
        override suspend fun findNonTerminal() = records.values.filterNot { OperationStateMachine.isTerminal(it.state) }
        override suspend fun findRecent(limit: Int) = records.values.toList().takeLast(limit).reversed()
        override suspend fun transition(
            operationId: String,
            nextState: OperationState,
            nextStage: TransferStage,
            sourceDeleteState: SourceDeleteState?,
            destinationCompletionEstablished: Boolean,
        ): OperationSnapshot {
            val current = requireNotNull(records[operationId])
            val nextDelete = sourceDeleteState ?: current.sourceDeleteState
            OperationStateMachine.requireTransition(current.type, current.state, nextState, nextDelete, destinationCompletionEstablished)
            return current.copy(
                state = nextState,
                stage = nextStage,
                sourceDeleteState = nextDelete,
                updatedAtEpochMillis = current.updatedAtEpochMillis + 1,
            ).also { records[operationId] = it }
        }
        override suspend fun recordPartial(operationId: String, partial: DurableLocator) {
            records[operationId] = requireNotNull(records[operationId]).copy(partialDestination = partial)
        }
        override suspend fun recordFinalization(operationId: String, finalLocator: DurableLocator) {
            records[operationId] = requireNotNull(records[operationId]).copy(
                finalizationDescription = FinalizationRecord.encode(finalLocator),
            )
        }
        override suspend fun recordFailure(
            operationId: String,
            nextState: OperationState,
            stage: TransferStage,
            errorCode: OperationErrorCode,
            errorMessage: String?,
        ): OperationSnapshot = transition(operationId, nextState, stage).copy(
            errorCode = errorCode,
            errorMessage = errorMessage,
        ).also { records[operationId] = it }
        override suspend fun updateProgress(operationId: String, bytesCompleted: Long) {
            records[operationId] = requireNotNull(records[operationId]).copy(bytesCompleted = bytesCompleted)
        }
        override suspend fun requestCancellation(operationId: String): Boolean {
            records[operationId] = requireNotNull(records[operationId]).copy(cancellationRequested = true)
            return true
        }
    }

    private fun <T> StorageResult<T>.requireSuccess(): T = when (this) {
        is StorageResult.Success -> value
        is StorageResult.Failure -> error("Expected success: $error")
    }

    private fun <T> StorageResult<T>.failure(): StorageResult.Failure = when (this) {
        is StorageResult.Failure -> this
        is StorageResult.Success -> error("Expected failure: $value")
    }
}
