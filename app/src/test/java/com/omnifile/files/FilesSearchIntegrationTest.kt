package com.omnifile.files

import com.omnifile.search.SearchHit
import com.omnifile.storage.EntryKind
import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageCapability
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageProvider
import com.omnifile.storage.StorageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesSearchIntegrationTest {
    @Test
    fun searchResultFromAnotherProviderOpensTheMatchingFilesSurface() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val local = FakeProvider(ProviderId("local"), "Local")
            val saf = FakeProvider(ProviderId("saf"), "SAF")
            val repository = FilesRepository(mapOf(local.id to local, saf.id to saf))
            val viewModel = FilesViewModel(repository, local.id, scope = scope)
            val hit = SearchHit(
                entry = saf.file,
                ancestors = listOf(saf.root),
            )

            assertTrue(viewModel.openSearchResult(hit))
            val state = viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()

            assertEquals(saf.id, state.location.ref.providerId)
            assertEquals(saf.root.ref, state.location.ref)
            assertEquals(listOf(saf.file), state.entries)
        } finally {
            scope.cancel()
        }
    }

    private inner class FakeProvider(
        override val id: ProviderId,
        label: String,
    ) : StorageProvider {
        val root = entry(id, label, EntryKind.DIRECTORY)
        val file = entry(id, "same-name.txt", EntryKind.FILE, root)

        override suspend fun root(): StorageResult<StorageEntry> = StorageResult.Success(root)

        override suspend fun listChildren(directory: EntryRef): StorageResult<List<StorageEntry>> =
            if (directory == root.ref) StorageResult.Success(listOf(file)) else StorageResult.Success(emptyList())
    }

    private fun entry(
        providerId: ProviderId,
        name: String,
        kind: EntryKind,
        parent: StorageEntry? = null,
    ) = StorageEntry(
        ref = TestEntryRef(providerId, name + (parent?.ref?.identityKey ?: "")),
        displayName = name,
        kind = kind,
        sizeBytes = null,
        modifiedAtEpochMillis = null,
        mimeType = if (kind == EntryKind.FILE) "text/plain" else null,
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
