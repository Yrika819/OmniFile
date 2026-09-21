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
    fun contextualResultPreservesTheExistingFilesPathForBack() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = FakeProvider(ProviderId("local"), "Local")
            val repository = FilesRepository(mapOf(provider.id to provider))
            val viewModel = FilesViewModel(repository, provider.id, scope = scope)
            viewModel.selectLocal()
            viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()
            viewModel.openDirectory(provider.folder)
            viewModel.uiState.filterIsInstance<FilesUiState.Content>().first { it.location.ref == provider.folder.ref }

            assertTrue(viewModel.openSearchResult(SearchHit(provider.nestedFile, listOf(provider.folder))))
            assertEquals(provider.folder.ref, (viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()).location.ref)
            assertTrue(!viewModel.isAtProviderRoot())

            viewModel.handleBack()
            assertEquals(provider.root.ref, viewModel.uiState.filterIsInstance<FilesUiState.Content>().first().location.ref)
        } finally {
            scope.cancel()
        }
    }

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
            assertEquals(listOf(saf.file, saf.folder), state.entries)
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
        val folder = entry(id, "nested", EntryKind.DIRECTORY, root)
        val nestedFile = entry(id, "nested-result.txt", EntryKind.FILE, folder)

        override suspend fun root(): StorageResult<StorageEntry> = StorageResult.Success(root)

        override suspend fun listChildren(directory: EntryRef): StorageResult<List<StorageEntry>> = when (directory) {
            root.ref -> StorageResult.Success(listOf(file, folder))
            folder.ref -> StorageResult.Success(listOf(nestedFile))
            else -> StorageResult.Success(emptyList())
        }
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
