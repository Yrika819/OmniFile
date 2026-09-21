package com.omnifile.storage

import android.net.TestUri
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedRootRegistryTest {
    private val localId = ProviderId("local-test")

    @Test
    fun localAndReadableSafRootsAreAggregatedWhileUnavailableRootsRemainPartial() = runBlocking {
        val local = FakeProvider(localId, StorageResult.Success(entry(localId, "local-root")))
        val saf = FakeProvider(ProviderId("saf-a"), StorageResult.Success(entry(ProviderId("saf-a"), "tree-a")))
        val unavailable = candidate("content://provider/tree/unavailable", null, StorageError.ProviderUnavailable)
        val registry = registry(local, listOf(candidate("content://provider/tree/a", saf), unavailable))

        val snapshot = registry.snapshot()

        assertEquals(2, snapshot.roots.size)
        assertEquals(2, snapshot.aggregationRoots.size)
        assertEquals(1, snapshot.failures.size)
        assertEquals(StorageError.ProviderUnavailable, snapshot.failures.single().error)
        assertFalse(snapshot.isComplete)
    }

    @Test
    fun exactUriIsScannedOnlyOnce() = runBlocking {
        val local = FakeProvider(localId, StorageResult.Success(entry(localId, "local-root")))
        val first = FakeProvider(ProviderId("saf-first"), StorageResult.Success(entry(ProviderId("saf-first"), "first")))
        val second = FakeProvider(ProviderId("saf-second"), StorageResult.Success(entry(ProviderId("saf-second"), "second")))
        val uri = "content://provider/tree/same"
        val registry = registry(local, listOf(candidate(uri, first), candidate(uri, second)))

        val snapshot = registry.snapshot()

        assertEquals(listOf("first"), snapshot.roots.filter { it.source == SupportedRootSource.SAF }.map { it.entry.displayName })
    }

    @Test
    fun descendantSafRootIsExcludedOnlyWhenContainmentIsProven() = runBlocking {
        val local = FakeProvider(localId, StorageResult.Success(entry(localId, "local-root")))
        val ancestorUri = TestUri("content://provider/tree/ancestor")
        val descendantUri = TestUri("content://provider/tree/descendant")
        val ancestor = FakeProvider(ProviderId("saf-ancestor"), StorageResult.Success(entry(ProviderId("saf-ancestor"), "ancestor")))
        val descendant = FakeProvider(ProviderId("saf-descendant"), StorageResult.Success(entry(ProviderId("saf-descendant"), "descendant")))
        val registry = SupportedRootRegistry(
            localProvider = local,
            safCandidates = { listOf(candidate(ancestorUri.toString(), ancestor), candidate(descendantUri.toString(), descendant)) },
            provenContains = { parent, child -> parent.toString() == ancestorUri.toString() && child.toString() == descendantUri.toString() },
        )

        val snapshot = registry.snapshot()

        assertEquals(3, snapshot.roots.size)
        assertEquals(listOf("local-root", "ancestor"), snapshot.aggregationRoots.map { it.entry.displayName })
    }

    @Test
    fun candidateSupplierReflectsAnewSafGrantWithoutRecreatingRegistry() = runBlocking {
        val local = FakeProvider(localId, StorageResult.Success(entry(localId, "local-root")))
        val saf = FakeProvider(ProviderId("saf-new"), StorageResult.Success(entry(ProviderId("saf-new"), "new-tree")))
        var candidates = emptyList<SafRootCandidate>()
        val registry = SupportedRootRegistry(local, safCandidates = { candidates })

        assertEquals(1, registry.snapshot().roots.size)
        candidates = listOf(candidate("content://provider/tree/new", saf))
        assertEquals(2, registry.snapshot().roots.size)
    }

    @Test
    fun noUsableRootsIsReportedWithoutInventingStorage() = runBlocking {
        val local = FakeProvider(localId, StorageResult.Failure(StorageError.PermissionDenied))
        val revoked = candidate("content://provider/tree/revoked", null, StorageError.PermissionDenied)
        val snapshot = registry(local, listOf(revoked)).snapshot()

        assertTrue(snapshot.roots.isEmpty())
        assertTrue(snapshot.aggregationRoots.isEmpty())
        assertEquals(2, snapshot.failures.size)
    }

    private fun registry(local: StorageProvider, candidates: List<SafRootCandidate>) =
        SupportedRootRegistry(local, safCandidates = { candidates })

    private fun candidate(uri: String, provider: StorageProvider?, error: StorageError? = null) =
        SafRootCandidate(
            id = SupportedRootRegistry.safRootId(TestUri(uri)),
            label = "SAF ${uri.substringAfterLast('/')}",
            uri = TestUri(uri),
            provider = provider,
            initialError = error,
        )

    private fun entry(providerId: ProviderId, token: String) = StorageEntry(
        ref = TestEntryRef(providerId, token),
        displayName = token,
        kind = EntryKind.DIRECTORY,
        sizeBytes = null,
        modifiedAtEpochMillis = null,
        mimeType = null,
        capabilities = setOf(StorageCapability.LIST_CHILDREN),
    )

    private class FakeProvider(
        override val id: ProviderId,
        private val rootValue: StorageResult<StorageEntry>,
    ) : StorageProvider {
        override suspend fun root(): StorageResult<StorageEntry> = rootValue
        override suspend fun listChildren(directory: EntryRef): StorageResult<List<StorageEntry>> = StorageResult.Success(emptyList())
    }

    private data class TestEntryRef(
        override val providerId: ProviderId,
        val token: String,
    ) : EntryRef {
        override val identityKey: String = "${providerId.value}\u0000$token"
    }
}
