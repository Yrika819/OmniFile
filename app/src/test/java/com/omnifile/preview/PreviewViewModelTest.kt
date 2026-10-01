package com.omnifile.preview

import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewViewModelTest {
    @Test
    fun lateACompletionCannotReplaceBAndCloseClearsPublishedContent() = runBlocking {
        val slowA = CompletableDeferred<Unit>()
        val engine = object : ResolvingFakePreviewEngine() {
            override suspend fun load(request: PreviewRequest): PreviewPayload {
                if (request.item.displayName == "A.txt") {
                    withContext(NonCancellable) { slowA.await() }
                    return PreviewPayload.Text("A", truncated = false, bytesRead = 1)
                }
                return PreviewPayload.Text("B", truncated = false, bytesRead = 1)
            }
        }
        val lifecycle = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = PreviewViewModel(engine = engine, dispatcher = Dispatchers.Unconfined, scope = lifecycle)
        val itemA = item("A.txt", "a")
        val itemB = item("B.txt", "b")
        val sourceA = source(itemA)
        val sourceB = source(itemB)

        viewModel.open(itemA) { StorageResult.Success(sourceA) }
        assertTrue(viewModel.state.value is PreviewUiState.Loading)
        viewModel.open(itemB) { StorageResult.Success(sourceB) }
        assertEquals("B.txt", (viewModel.state.value as PreviewUiState.Ready).item.displayName)

        slowA.complete(Unit)
        assertEquals("B", ((viewModel.state.value as PreviewUiState.Ready).payload as PreviewPayload.Text).content)

        viewModel.close()
        assertEquals(PreviewUiState.Idle, viewModel.state.value)
        lifecycle.cancel()
    }

    @Test
    fun lateCompletionOnAnotherDispatcherCannotReplaceNewerRequest() = runBlocking {
        val slowAStarted = CompletableDeferred<Unit>()
        val releaseSlowA = CompletableDeferred<Unit>()
        val engine = object : ResolvingFakePreviewEngine() {
            override suspend fun load(request: PreviewRequest): PreviewPayload {
                if (request.item.displayName == "A.txt") {
                    slowAStarted.complete(Unit)
                    withContext(NonCancellable) { releaseSlowA.await() }
                    return PreviewPayload.Text("A", truncated = false, bytesRead = 1)
                }
                return PreviewPayload.Text("B", truncated = false, bytesRead = 1)
            }
        }
        val lifecycle = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = PreviewViewModel(engine = engine, dispatcher = Dispatchers.Default, scope = lifecycle)
        val itemA = item("A.txt", "a")
        val itemB = item("B.txt", "b")

        viewModel.open(itemA) { StorageResult.Success(source(itemA)) }
        withTimeout(5_000) { slowAStarted.await() }
        val jobA = lifecycle.coroutineContext[Job]!!.children.first()
        viewModel.open(itemB) { StorageResult.Success(source(itemB)) }
        withTimeout(5_000) {
            viewModel.state.first { state -> state is PreviewUiState.Ready && state.item == itemB }
        }

        releaseSlowA.complete(Unit)
        withTimeout(5_000) { jobA.join() }
        assertEquals("B", (((viewModel.state.value as PreviewUiState.Ready).payload) as PreviewPayload.Text).content)
        lifecycle.cancel()
    }

    @Test
    fun mismatchedResolvedSourceBecomesTypedFailureInsteadOfEscapingCoroutine() = runBlocking {
        val uncaught = CompletableDeferred<Throwable>()
        val handler = CoroutineExceptionHandler { _, error -> uncaught.complete(error) }
        val lifecycle = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + handler)
        val viewModel = PreviewViewModel(engine = PreviewEngine(), dispatcher = Dispatchers.Unconfined, scope = lifecycle)
        val item = item("sample.txt", "expected")
        val source = PreviewSource(
            identity = TestEntryRef("different"),
            sourceLabel = "Test source",
            mimeType = "text/plain",
            sizeBytes = 1,
            capabilities = PreviewCapabilities(sequentialReadable = true, canReopen = true),
        ) { StorageResult.Failure(com.omnifile.storage.StorageError.Unsupported) }

        viewModel.open(item) { StorageResult.Success(source) }

        val state = withTimeout(5_000) { viewModel.state.first { it is PreviewUiState.Error } }
        assertEquals(PreviewUiState.Error(item, PreviewError.ProviderUnavailable), state)
        assertTrue(!uncaught.isCompleted)
        lifecycle.cancel()
    }

    private fun item(name: String, identityKey: String) = PreviewItem(
        identity = TestEntryRef(identityKey),
        displayName = name,
        locationLabel = "Folder",
        mimeType = "text/plain",
        sizeBytes = 1,
    )

    private fun source(item: PreviewItem) = PreviewSource(
        identity = item.identity,
        sourceLabel = "Local storage",
        mimeType = item.mimeType,
        sizeBytes = item.sizeBytes,
        capabilities = PreviewCapabilities(sequentialReadable = true, canReopen = true),
    ) { StorageResult.Failure(com.omnifile.storage.StorageError.Unsupported) }

    private data class TestEntryRef(override val identityKey: String) : EntryRef {
        override val providerId = ProviderId("preview-test")
    }
}


private abstract class ResolvingFakePreviewEngine : PreviewEngine() {
    override suspend fun resolveAndLoad(item: PreviewItem, resolveSource: suspend () -> StorageResult<PreviewSource>, startingPage: Int): ResolvedPreview {
        val source = (resolveSource() as StorageResult.Success).value
        return ResolvedPreview(source.sourceLabel, load(PreviewRequest(item, source, startingPage)))
    }
}
