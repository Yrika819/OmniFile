package com.omnifile.files

import com.omnifile.storage.EntryKind
import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageCapability
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageProvider
import com.omnifile.storage.StorageResult
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
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

class FilesViewModelTest {
    @Test
    fun backStopsAtSourceSelectionInsteadOfLeavingSelectedRoot() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = NavigationProvider()
            val viewModel = FilesViewModel(FilesRepository(mapOf(provider.id to provider)), provider.id, scope = scope)

            viewModel.selectLocal()
            viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()
            viewModel.openDirectory(provider.folder)
            viewModel.uiState.filterIsInstance<FilesUiState.Empty>().first { it.location.displayName == "folder" }

            viewModel.goBack()
            assertTrue(viewModel.uiState.filterIsInstance<FilesUiState.Content>().first().location.displayName == "root")
            viewModel.goBack()
            assertEquals(FilesUiState.SourceSelection, viewModel.uiState.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun delayedFolderResultCannotOverwriteNewerFolder() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = RaceProvider()
            val viewModel = FilesViewModel(FilesRepository(mapOf(provider.id to provider)), provider.id, scope = scope)

            viewModel.selectLocal()
            viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()
            viewModel.openDirectory(provider.folderA)
            provider.folderAStarted.await()
            viewModel.openDirectory(provider.folderB)
            val newer = viewModel.uiState.filterIsInstance<FilesUiState.Empty>().first { it.location.displayName == "folder-b" }
            provider.folderAResult.complete(StorageResult.Success(emptyList()))

            assertEquals("folder-b", newer.location.displayName)
            assertEquals("folder-b", (viewModel.uiState.value as FilesUiState.Empty).location.displayName)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun providerFailureIsRecoverableAndRetainsLocation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = FailureProvider()
            val viewModel = FilesViewModel(FilesRepository(mapOf(provider.id to provider)), provider.id, scope = scope)

            viewModel.selectLocal()
            val error = viewModel.uiState.filterIsInstance<FilesUiState.Error>().first()

            assertEquals(StorageError.PermissionDenied, error.error)
            assertEquals("root", error.location?.displayName)
        } finally {
            scope.cancel()
        }
    }

    private inner class NavigationProvider : StorageProvider {
        override val id = ProviderId("navigation")
        val root = entry(id, "root", EntryKind.DIRECTORY)
        val folder = entry(id, "folder", EntryKind.DIRECTORY, root)

        override suspend fun root() = StorageResult.Success(root)

        override suspend fun listChildren(directory: EntryRef) = when (directory) {
            root.ref -> StorageResult.Success(listOf(folder))
            else -> StorageResult.Success(emptyList())
        }
    }

    private inner class RaceProvider : StorageProvider {
        override val id = ProviderId("race")
        val root = entry(id, "root", EntryKind.DIRECTORY)
        val folderA = entry(id, "folder-a", EntryKind.DIRECTORY, root)
        val folderB = entry(id, "folder-b", EntryKind.DIRECTORY, root)
        val folderAStarted = CompletableDeferred<Unit>()
        val folderAResult = CompletableDeferred<StorageResult<List<StorageEntry>>>()

        override suspend fun root() = StorageResult.Success(root)

        override suspend fun listChildren(directory: EntryRef) = when (directory) {
            root.ref -> StorageResult.Success(listOf(folderA, folderB))
            folderA.ref -> {
                folderAStarted.complete(Unit)
                folderAResult.await()
            }
            folderB.ref -> StorageResult.Success(emptyList())
            else -> StorageResult.Failure(StorageError.NotFound)
        }
    }

    private inner class FailureProvider : StorageProvider {
        override val id = ProviderId("failure")
        private val root = entry(id, "root", EntryKind.DIRECTORY)

        override suspend fun root() = StorageResult.Success(root)

        override suspend fun listChildren(directory: EntryRef) =
            StorageResult.Failure(StorageError.PermissionDenied)
    }

    private fun entry(providerId: ProviderId, name: String, kind: EntryKind, parent: StorageEntry? = null) = StorageEntry(
        ref = TestEntryRef(providerId, name),
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
    ) : EntryRef
}
