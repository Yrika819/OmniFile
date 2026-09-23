package com.omnifile.preview

import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewViewModelTest {
    @Test
    fun lateACompletionCannotReplaceBAndCloseClearsPublishedContent() = runBlocking {
        val slowA = CompletableDeferred<Unit>()
        val engine = object : PreviewEngine() {
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
