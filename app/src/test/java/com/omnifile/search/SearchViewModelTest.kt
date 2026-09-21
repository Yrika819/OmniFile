package com.omnifile.search

import com.omnifile.storage.EntryKind
import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchViewModelTest {
    private val providerId = ProviderId("view-model-test")

    @Test
    fun replacingQueryPreventsLateOldResultsFromBecomingVisible() = runBlocking {
        val root = entry("root", EntryKind.DIRECTORY)
        val oldResult = entry("old-result.txt", EntryKind.FILE)
        val newResult = entry("new-result.txt", EntryKind.FILE)
        val oldRelease = CompletableDeferred<Unit>()
        var childCalls = 0
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = SearchViewModel(
            listChildren = {
                childCalls += 1
                when (childCalls) {
                    1 -> {
                        withContext(NonCancellable) { oldRelease.await() }
                        StorageResult.Success(listOf(oldResult))
                    }
                    else -> StorageResult.Success(listOf(newResult))
                }
            },
            scope = testScope,
        )
        viewModel.setScope(SearchScope.CurrentFolder(providerId, root))

        viewModel.queryChanged("old")
        viewModel.submitQuery()
        eventually { childCalls >= 1 }

        viewModel.queryChanged("new")
        viewModel.submitQuery()
        eventually { childCalls >= 2 && viewModel.state.value is SearchUiState.Results }
        oldRelease.complete(Unit)
        delay(100)

        val state = viewModel.state.value as SearchUiState.Results
        assertEquals("new", state.query)
        assertEquals(listOf("new-result.txt"), state.hits.map { it.entry.displayName })
        assertTrue(state.hits.none { it.entry.displayName == "old-result.txt" })

        viewModel.stop()
        testScope.cancel()
    }

    @Test
    fun whitespaceQueryDoesNotStartProviderTraversal() = runBlocking {
        var childCalls = 0
        val root = entry("root", EntryKind.DIRECTORY)
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = SearchViewModel(
            listChildren = {
                childCalls += 1
                StorageResult.Success(emptyList())
            },
            scope = testScope,
        )
        viewModel.setScope(SearchScope.CurrentFolder(providerId, root))

        viewModel.queryChanged("   ")
        delay(250)

        assertEquals(SearchUiState.Idle(scope = SearchScope.CurrentFolder(providerId, root)), viewModel.state.value)
        assertEquals(0, childCalls)
        viewModel.stop()
        testScope.cancel()
    }

    private suspend fun eventually(predicate: () -> Boolean) {
        withTimeout(5_000) {
            while (!predicate()) delay(10)
        }
    }

    private fun entry(name: String, kind: EntryKind): StorageEntry = StorageEntry(
        ref = TestEntryRef(providerId, name),
        displayName = name,
        kind = kind,
        sizeBytes = null,
        modifiedAtEpochMillis = null,
        mimeType = null,
        capabilities = emptySet(),
    )

    private data class TestEntryRef(
        override val providerId: ProviderId,
        val token: String,
    ) : EntryRef {
        override val identityKey: String = "${providerId.value}\u0000$token"
    }
}
