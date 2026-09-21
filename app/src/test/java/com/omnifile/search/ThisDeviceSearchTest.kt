package com.omnifile.search

import com.omnifile.storage.EntryKind
import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThisDeviceSearchTest {
    private val localId = ProviderId("local-root")
    private val safId = ProviderId("saf-root")

    @Test
    fun aggregatesDistinctProviderRootsAndReportsPartialResults() = runBlocking {
        val localRoot = entry(localId, "Local storage", EntryKind.DIRECTORY)
        val safRoot = entry(safId, "SAF folder", EntryKind.DIRECTORY)
        val localHit = entry(localId, "same-name.txt", EntryKind.FILE, localRoot)
        val safHit = entry(safId, "same-name.txt", EntryKind.FILE, safRoot)
        val children = mapOf(localRoot.ref.identityKey to listOf(localHit), safRoot.ref.identityKey to listOf(safHit))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val viewModel = SearchViewModel(
                listChildren = { directory -> StorageResult.Success(children[directory.ref.identityKey].orEmpty()) },
                resolveRoots = {
                    SearchRootResolution(
                        roots = listOf(localRoot, safRoot),
                        failures = listOf(SearchRootFailure("revoked", "Revoked SAF", StorageError.PermissionDenied)),
                        rootLabels = mapOf(
                            localRoot.ref.identityKey to "Local storage",
                            safRoot.ref.identityKey to "SAF source A",
                        ),
                    )
                },
                scope = scope,
            )

            viewModel.setScope(SearchScope.ThisDevice)
            viewModel.queryChanged("same-name")
            viewModel.submitQuery()
            eventually { viewModel.state.value is SearchUiState.Results }

            val state = viewModel.state.value as SearchUiState.Results
            assertEquals(setOf(localId, safId), state.hits.map { it.providerId }.toSet())
            assertEquals(setOf("Local storage", "SAF source A"), state.hits.map { it.rootLabel }.toSet())
            assertEquals(1, state.rootFailures.size)
            assertFalse(state.complete)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun localSuccessWithUnavailableSafIsResultsNotAFalseTotalFailure() = runBlocking {
        val root = entry(localId, "Local storage", EntryKind.DIRECTORY)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val viewModel = SearchViewModel(
                listChildren = { StorageResult.Success(emptyList()) },
                resolveRoots = {
                    SearchRootResolution(
                        roots = listOf(root),
                        failures = listOf(SearchRootFailure("unavailable", "SAF folder", StorageError.ProviderUnavailable)),
                    )
                },
                scope = scope,
            )
            viewModel.setScope(SearchScope.ThisDevice)
            viewModel.queryChanged("missing")
            viewModel.submitQuery()
            eventually { viewModel.state.value is SearchUiState.Results }

            val state = viewModel.state.value as SearchUiState.Results
            assertTrue(state.hits.isEmpty())
            assertFalse(state.complete)
            assertEquals(1, state.rootFailures.size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun noResolvedRootProducesExplicitError() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val viewModel = SearchViewModel(
                listChildren = { StorageResult.Success(emptyList()) },
                resolveRoots = {
                    SearchRootResolution(
                        roots = emptyList(),
                        failures = listOf(SearchRootFailure("revoked", "Revoked SAF", StorageError.PermissionDenied)),
                    )
                },
                scope = scope,
            )
            viewModel.setScope(SearchScope.ThisDevice)
            viewModel.queryChanged("anything")
            viewModel.submitQuery()
            eventually { viewModel.state.value is SearchUiState.Error }

            val state = viewModel.state.value as SearchUiState.Error
            assertEquals(StorageError.PermissionDenied, state.rootError)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun eventually(predicate: () -> Boolean) {
        withTimeout(5_000) {
            while (!predicate()) delay(10)
        }
    }

    private fun entry(providerId: ProviderId, name: String, kind: EntryKind, parent: StorageEntry? = null) = StorageEntry(
        ref = TestEntryRef(providerId, name + (parent?.ref?.identityKey ?: "")),
        displayName = name,
        kind = kind,
        sizeBytes = null,
        modifiedAtEpochMillis = null,
        mimeType = if (kind == EntryKind.FILE) "text/plain" else null,
        capabilities = emptySet(),
        parentRef = parent?.ref,
    )

    private data class TestEntryRef(
        override val providerId: ProviderId,
        val token: String,
    ) : EntryRef {
        override val identityKey: String = "${providerId.value}\u0000$token"
    }
}
