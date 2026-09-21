package com.omnifile.search

import com.omnifile.storage.EntryKind
import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageCapability
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {
    private val providerId = ProviderId("search-test")

    @Test
    fun recursivelyFindsFilesAndDirectoriesWithDistinctAncestors() = runBlocking {
        val root = entry("root", EntryKind.DIRECTORY)
        val alpha = entry("Alpha", EntryKind.DIRECTORY, root)
        val beta = entry("Beta", EntryKind.DIRECTORY, root)
        val alphaHit = entry("same.txt", EntryKind.FILE, alpha)
        val betaHit = entry("same.txt", EntryKind.FILE, beta)
        val tree = mapOf(
            root to listOf(beta, alpha),
            alpha to listOf(alphaHit),
            beta to listOf(betaHit),
        )

        val emissions = SearchEngine(batchSize = 1).search(
            request = SearchRequest(SearchScope.CurrentFolder(providerId, root), "same"),
            roots = { StorageResult.Success(listOf(root)) },
            children = { StorageResult.Success(tree[it] ?: emptyList()) },
        ).toList()
        val completed = emissions.filterIsInstance<SearchEmission.Completed>().single()

        assertEquals(listOf("same.txt", "same.txt"), completed.hits.map { it.entry.displayName })
        assertEquals(
            listOf("root / Alpha", "root / Beta"),
            completed.hits.map { it.ancestors.joinToString(" / ") { ancestor -> ancestor.displayName } },
        )
        assertTrue(completed.hits[0].entry.ref.identityKey != completed.hits[1].entry.ref.identityKey)
        assertTrue(completed.complete)
    }

    @Test
    fun preservesMatchesWhenOneSubtreeFails() = runBlocking {
        val root = entry("root", EntryKind.DIRECTORY)
        val readable = entry("readable", EntryKind.DIRECTORY, root)
        val unavailable = entry("unavailable", EntryKind.DIRECTORY, root)
        val hit = entry("needle.txt", EntryKind.FILE, readable)
        val emissions = SearchEngine().search(
            request = SearchRequest(SearchScope.CurrentFolder(providerId, root), "needle"),
            roots = { StorageResult.Success(listOf(root)) },
            children = { entry ->
                when (entry) {
                    root -> StorageResult.Success(listOf(unavailable, readable))
                    readable -> StorageResult.Success(listOf(hit))
                    unavailable -> StorageResult.Failure(StorageError.ProviderUnavailable)
                    else -> StorageResult.Success(emptyList())
                }
            },
        ).toList()
        val completed = emissions.filterIsInstance<SearchEmission.Completed>().single()

        assertEquals(listOf("needle.txt"), completed.hits.map { it.entry.displayName })
        assertEquals(listOf("unavailable"), completed.failures.map { it.directory.displayName })
        assertFalse(completed.complete)
        assertFalse(completed.truncated)
    }

    @Test
    fun classifiesThrownRootProviderFailure() = runBlocking {
        val root = entry("root", EntryKind.DIRECTORY)
        val emissions = SearchEngine().search(
            request = SearchRequest(SearchScope.CurrentFolder(providerId, root), "needle"),
            roots = { throw SecurityException("revoked") },
            children = { error("children must not be called") },
        ).toList()

        val completed = emissions.single() as SearchEmission.Completed
        assertEquals(StorageError.PermissionDenied, completed.rootError)
        assertTrue(completed.hits.isEmpty())
    }

    @Test
    fun currentFolderRootFailureIsReportedAsProviderError() = runBlocking {
        val root = entry("root", EntryKind.DIRECTORY)
        val emissions = SearchEngine().search(
            request = SearchRequest(SearchScope.CurrentFolder(providerId, root), "needle"),
            roots = { StorageResult.Success(listOf(root)) },
            children = { StorageResult.Failure(StorageError.ProviderUnavailable) },
        ).toList()

        val completed = emissions.filterIsInstance<SearchEmission.Completed>().single()
        assertEquals(StorageError.ProviderUnavailable, completed.rootError)
        assertTrue(completed.hits.isEmpty())
        assertTrue(completed.failures.isEmpty())
    }

    @Test
    fun cancellationStopsAnInFlightProviderListing() = runBlocking {
        val root = entry("root", EntryKind.DIRECTORY)
        var childCalls = 0
        val job = launch {
            SearchEngine().search(
                request = SearchRequest(SearchScope.CurrentFolder(providerId, root), "needle"),
                roots = { StorageResult.Success(listOf(root)) },
                children = {
                    childCalls += 1
                    awaitCancellation()
                },
            ).collect { error("search should not complete after cancellation") }
        }

        while (childCalls == 0) kotlinx.coroutines.yield()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test
    fun whitespaceQueryDoesNotTraverse() = runBlocking {
        var childCalls = 0
        val root = entry("root", EntryKind.DIRECTORY)
        val emissions = SearchEngine().search(
            request = SearchRequest(SearchScope.CurrentFolder(providerId, root), "  "),
            roots = { StorageResult.Success(listOf(root)) },
            children = {
                childCalls += 1
                StorageResult.Success(emptyList())
            },
        ).toList()

        val completed = emissions.single() as SearchEmission.Completed
        assertEquals(0, childCalls)
        assertTrue(completed.complete)
        assertTrue(completed.hits.isEmpty())
    }

    private fun entry(
        name: String,
        kind: EntryKind,
        parent: StorageEntry? = null,
    ): StorageEntry = StorageEntry(
        ref = TestEntryRef(providerId, "${parent?.ref?.identityKey ?: "root"}/$name"),
        displayName = name,
        kind = kind,
        sizeBytes = null,
        modifiedAtEpochMillis = null,
        mimeType = null,
        capabilities = if (kind == EntryKind.DIRECTORY) setOf(StorageCapability.LIST_CHILDREN) else emptySet(),
        parentRef = parent?.ref,
    )

    private data class TestEntryRef(
        override val providerId: ProviderId,
        val token: String,
    ) : EntryRef {
        override val identityKey: String = "${providerId.value}\u0000$token"
    }
}
