package com.omnifile.files

import android.net.TestUri
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesViewModelTest {
    @Test
    fun longPressEntersSingleSelectionAndToggleDeselectsTheLastEntry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = SelectionProvider()
            val viewModel = FilesViewModel(FilesRepository(mapOf(provider.id to provider)), provider.id, scope = scope)
            viewModel.selectLocal()
            val content = viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()

            viewModel.enterSelection(content.entries.first())
            assertTrue(viewModel.isSelectionMode)
            assertEquals(setOf(content.entries.first().ref), viewModel.selectedEntries)

            viewModel.toggleSelection(content.entries.first())
            assertFalse(viewModel.isSelectionMode)
            assertTrue(viewModel.selectedEntries.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun selectionToggleSupportsMultipleEntriesAndExplicitClear() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = SelectionProvider()
            val viewModel = FilesViewModel(FilesRepository(mapOf(provider.id to provider)), provider.id, scope = scope)
            viewModel.selectLocal()
            val entries = viewModel.uiState.filterIsInstance<FilesUiState.Content>().first().entries

            viewModel.enterSelection(entries[0])
            viewModel.toggleSelection(entries[1])
            assertEquals(setOf(entries[0].ref, entries[1].ref), viewModel.selectedEntries)

            viewModel.clearSelection()
            assertFalse(viewModel.isSelectionMode)
            assertTrue(viewModel.selectedEntries.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun directoryNavigationClearsSelection() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = SelectionProvider()
            val viewModel = FilesViewModel(FilesRepository(mapOf(provider.id to provider)), provider.id, scope = scope)
            viewModel.selectLocal()
            val content = viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()

            viewModel.enterSelection(content.entries.first())
            viewModel.openDirectory(provider.folder)
            viewModel.uiState.filterIsInstance<FilesUiState.Empty>().first { it.location.ref == provider.folder.ref }

            assertFalse(viewModel.isSelectionMode)
            assertTrue(viewModel.selectedEntries.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun providerBoundaryClearsSelection() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val local = SelectionProvider(ProviderId("local"))
            val saf = SelectionProvider(ProviderId("saf"))
            val uri = TestUri("content://selection-tree")
            val viewModel = FilesViewModel(
                FilesRepository(mapOf(local.id to local, saf.id to saf)),
                local.id,
                safProviderFor = { saf },
                scope = scope,
            )
            viewModel.selectLocal()
            val localContent = viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()
            viewModel.enterSelection(localContent.entries.first())

            viewModel.selectSaf(uri)
            viewModel.uiState.filterIsInstance<FilesUiState.Content>().first { it.location.ref == saf.root.ref }

            assertFalse(viewModel.isSelectionMode)
            assertTrue(viewModel.selectedEntries.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun backClearsSelectionBeforeChangingDirectory() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = SelectionProvider()
            val viewModel = FilesViewModel(FilesRepository(mapOf(provider.id to provider)), provider.id, scope = scope)
            viewModel.selectLocal()
            viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()
            viewModel.enterSelection(provider.child)

            viewModel.handleBack()
            assertFalse(viewModel.isSelectionMode)
            assertEquals(provider.root.ref, (viewModel.uiState.value as FilesUiState.Content).location.ref)

            viewModel.openDirectory(provider.folder)
            viewModel.uiState.filterIsInstance<FilesUiState.Empty>().first { it.location.ref == provider.folder.ref }
            viewModel.goBack()
            assertEquals(provider.root.ref, viewModel.uiState.filterIsInstance<FilesUiState.Content>().first().location.ref)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun refreshDropsTheOldRefWhenProviderRenamesAnEntry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = SelectionProvider()
            val viewModel = FilesViewModel(FilesRepository(mapOf(provider.id to provider)), provider.id, scope = scope)
            viewModel.selectLocal()
            viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()
            viewModel.enterSelection(provider.child)
            provider.replaceChild(provider.renamedChild)

            viewModel.retry()
            viewModel.uiState.filterIsInstance<FilesUiState.Content>().first { state ->
                state.entries.any { it.ref == provider.renamedChild.ref }
            }

            assertFalse(viewModel.selectedEntries.contains(provider.child.ref))
            assertTrue(viewModel.selectedEntries.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun refreshDropsTheRefWhenProviderDeletesAnEntry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = SelectionProvider()
            val viewModel = FilesViewModel(FilesRepository(mapOf(provider.id to provider)), provider.id, scope = scope)
            viewModel.selectLocal()
            viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()
            viewModel.enterSelection(provider.child)
            provider.removeChild()

            viewModel.retry()
            val refreshed = viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()

            assertFalse(viewModel.isSelectionMode)
            assertTrue(viewModel.selectedEntries.isEmpty())
            assertTrue(refreshed.entries.none { it.ref == provider.child.ref })
        } finally {
            scope.cancel()
        }
    }

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
    fun destinationPickerCanBackToParentAndEnterSiblingDirectory() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = DestinationPickerProvider()
            val viewModel = FilesViewModel(FilesRepository(mapOf(provider.id to provider)), provider.id, scope = scope)

            viewModel.selectLocal()
            viewModel.uiState.filterIsInstance<FilesUiState.Content>().first { it.location.ref == provider.root.ref }
            viewModel.openDirectory(provider.container)
            viewModel.uiState.filterIsInstance<FilesUiState.Content>().first { it.location.ref == provider.container.ref }
            viewModel.openDirectory(provider.source)
            val source = viewModel.uiState.filterIsInstance<FilesUiState.Content>().first { it.location.ref == provider.source.ref }
            viewModel.enterSelection(source.entries.single { it.ref == provider.sourceFile.ref })

            viewModel.copySelectedToCurrentDirectory()
            val sourcePicker = viewModel.uiState.filterIsInstance<FilesUiState.DestinationPicker>().first()
            assertEquals(provider.source.ref, sourcePicker.location.ref)
            assertEquals(listOf("root", "container", "source"), sourcePicker.breadcrumb)

            viewModel.handleBack()
            val parentPicker = viewModel.uiState.filterIsInstance<FilesUiState.DestinationPicker>().first { it.location.ref == provider.container.ref }
            assertEquals(listOf("root", "container"), parentPicker.breadcrumb)
            assertTrue(parentPicker.entries.any { it.ref == provider.destination.ref })

            viewModel.openDestinationDirectory(provider.destination)
            val destinationPicker = viewModel.uiState.filterIsInstance<FilesUiState.DestinationPicker>().first { it.location.ref == provider.destination.ref }
            assertEquals(listOf("root", "container", "destination"), destinationPicker.breadcrumb)
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

    @Test
    fun renameSelectedRefreshesProviderTruthAndClearsSelection() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = MutationProvider()
            val viewModel = FilesViewModel(FilesRepository(mapOf(provider.id to provider)), provider.id, scope = scope)
            viewModel.selectLocal()
            val content = viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()
            viewModel.enterSelection(content.entries.single { it.ref == provider.old.ref })

            viewModel.renameSelected("requested.txt")
            val refreshed = viewModel.uiState.filterIsInstance<FilesUiState.Content>().first { state ->
                state.entries.any { it.ref == provider.renamed.ref }
            }

            assertTrue(refreshed.entries.none { it.ref == provider.old.ref })
            assertFalse(viewModel.isSelectionMode)
            assertEquals(1, provider.renameCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun duplicateRenameSubmissionIsSuppressed() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = MutationProvider(blockRename = true)
            val viewModel = FilesViewModel(FilesRepository(mapOf(provider.id to provider)), provider.id, scope = scope)
            viewModel.selectLocal()
            val content = viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()
            viewModel.enterSelection(content.entries.single { it.ref == provider.old.ref })

            viewModel.renameSelected("requested.txt")
            provider.renameStarted.await()
            viewModel.renameSelected("second.txt")
            provider.releaseRename.complete(Unit)
            viewModel.uiState.filterIsInstance<FilesUiState.Content>().first { it.entries.any { entry -> entry.ref == provider.renamed.ref } }

            assertEquals(1, provider.renameCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun partialDeleteRetainsOnlyFailedSurvivingEntries() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = MutationProvider(failDelete = true)
            val viewModel = FilesViewModel(FilesRepository(mapOf(provider.id to provider)), provider.id, scope = scope)
            viewModel.selectLocal()
            val content = viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()
            viewModel.enterSelection(content.entries.single { it.ref == provider.old.ref })
            viewModel.toggleSelection(content.entries.single { it.ref == provider.failed.ref })

            viewModel.deleteSelected()
            val error = viewModel.uiState.filterIsInstance<FilesUiState.Error>().first { it.error is StorageError.PartialDelete }

            assertEquals(setOf(provider.failed.ref), viewModel.selectedEntries)
            assertTrue(error.location?.ref == provider.root.ref)
            assertEquals(2, provider.deleteCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun fullDeleteClearsSelectionAfterProviderRefresh() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = MutationProvider()
            val viewModel = FilesViewModel(FilesRepository(mapOf(provider.id to provider)), provider.id, scope = scope)
            viewModel.selectLocal()
            val content = viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()
            viewModel.enterSelection(content.entries.single { it.ref == provider.old.ref })
            viewModel.toggleSelection(content.entries.single { it.ref == provider.failed.ref })

            viewModel.deleteSelected()
            viewModel.uiState.filterIsInstance<FilesUiState.Content>().first { state ->
                state.entries.singleOrNull()?.ref == provider.folder.ref
            }

            assertFalse(viewModel.isSelectionMode)
            assertTrue(viewModel.selectedEntries.isEmpty())
            assertEquals(2, provider.deleteCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun staleMutationCompletionCannotOverwriteNewerNavigation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = MutationProvider(blockRename = true)
            val viewModel = FilesViewModel(FilesRepository(mapOf(provider.id to provider)), provider.id, scope = scope)
            viewModel.selectLocal()
            val content = viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()
            viewModel.enterSelection(content.entries.single { it.ref == provider.old.ref })
            viewModel.renameSelected("requested.txt")
            provider.renameStarted.await()

            viewModel.openDirectory(provider.folder)
            viewModel.uiState.filterIsInstance<FilesUiState.Empty>().first { it.location.ref == provider.folder.ref }
            provider.releaseRename.complete(Unit)
            provider.renameCompleted.await()
            provider.staleRefreshCompleted.await()
            withTimeout(2_000) {
                while (viewModel.isMutationInFlight) yield()
            }

            assertEquals(provider.folder.ref, (viewModel.uiState.value as FilesUiState.Empty).location.ref)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun staleMutationCompletionCannotClearNewerMutationInFlight() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = MutationOwnershipProvider()
            val viewModel = FilesViewModel(FilesRepository(mapOf(provider.id to provider)), provider.id, scope = scope)
            provider.inFlightProbe = { viewModel.isMutationInFlight }
            viewModel.selectLocal()
            val rootContent = viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()

            viewModel.enterSelection(rootContent.entries.single { it.ref == provider.first.ref })
            viewModel.renameSelected("first-renamed.txt")
            withTimeout(2_000) { provider.firstRenameStarted.await() }

            viewModel.openDirectory(provider.folder)
            viewModel.uiState.filterIsInstance<FilesUiState.Content>().first { it.location.ref == provider.folder.ref }
            val folderContent = viewModel.uiState.value as FilesUiState.Content
            viewModel.enterSelection(folderContent.entries.single { it.ref == provider.second.ref })
            viewModel.renameSelected("second-renamed.txt")
            withTimeout(2_000) { provider.secondRenameStarted.await() }

            provider.releaseFirstRename.complete(Unit)
            withTimeout(2_000) { provider.firstRefreshStarted.await() }
            provider.releaseFirstRefresh.complete(Unit)
            withTimeout(2_000) { provider.firstRefreshReturned.await() }
            val staleOwnerCleared = withTimeout(2_000) { provider.secondMutationObservation.await() }
            assertFalse(staleOwnerCleared)

            provider.releaseSecondRename.complete(Unit)
            viewModel.uiState.filterIsInstance<FilesUiState.Content>().first { state ->
                state.entries.any { it.ref == provider.secondRenamed.ref }
            }
            assertFalse(viewModel.isMutationInFlight)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun duplicateDeleteSubmissionIsSuppressed() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = MutationProvider(blockDelete = true)
            val viewModel = FilesViewModel(FilesRepository(mapOf(provider.id to provider)), provider.id, scope = scope)
            viewModel.selectLocal()
            val content = viewModel.uiState.filterIsInstance<FilesUiState.Content>().first()
            viewModel.enterSelection(content.entries.single { it.ref == provider.old.ref })

            viewModel.deleteSelected()
            provider.deleteStarted.await()
            viewModel.deleteSelected()
            provider.releaseDelete.complete(Unit)
            viewModel.uiState.filterIsInstance<FilesUiState.Content>().first { state ->
                state.entries.none { it.ref == provider.old.ref }
            }

            assertEquals(1, provider.deleteCalls)
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

    private inner class DestinationPickerProvider : StorageProvider {
        override val id = ProviderId("destination-picker")
        val root = entry(id, "root", EntryKind.DIRECTORY)
        val container = entry(id, "container", EntryKind.DIRECTORY, root)
        val source = entry(id, "source", EntryKind.DIRECTORY, container)
        val destination = entry(id, "destination", EntryKind.DIRECTORY, container)
        val sourceFile = entry(id, "source.bin", EntryKind.FILE, source, setOf(StorageCapability.READ_SEQUENTIAL))

        override suspend fun root() = StorageResult.Success(root)

        override suspend fun listChildren(directory: EntryRef) = when (directory) {
            root.ref -> StorageResult.Success(listOf(container))
            container.ref -> StorageResult.Success(listOf(source, destination))
            source.ref -> StorageResult.Success(listOf(sourceFile))
            destination.ref -> StorageResult.Success(emptyList())
            else -> StorageResult.Failure(StorageError.NotFound)
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

    private inner class MutationProvider(
        private val blockRename: Boolean = false,
        private val failDelete: Boolean = false,
        private val blockDelete: Boolean = false,
    ) : StorageProvider {
        override val id = ProviderId("mutation")
        val root = entry(id, "root", EntryKind.DIRECTORY)
        val folder = entry(id, "folder", EntryKind.DIRECTORY, root)
        val old = entry(id, "old", EntryKind.FILE, root)
        val renamed = entry(id, "renamed", EntryKind.FILE, root)
        val failed = entry(id, "failed", EntryKind.FILE, root)
        val renameStarted = CompletableDeferred<Unit>()
        val releaseRename = CompletableDeferred<Unit>()
        val renameCompleted = CompletableDeferred<Unit>()
        val staleRefreshCompleted = CompletableDeferred<Unit>()
        val deleteStarted = CompletableDeferred<Unit>()
        val releaseDelete = CompletableDeferred<Unit>()
        var renameCalls = 0
        var deleteCalls = 0
        private var children = listOf(folder, old, failed)

        override suspend fun root() = StorageResult.Success(root)

        override suspend fun listChildren(directory: EntryRef) = when (directory) {
            root.ref -> {
                if (renameCompleted.isCompleted) staleRefreshCompleted.complete(Unit)
                StorageResult.Success(children)
            }
            folder.ref -> StorageResult.Success(emptyList())
            else -> StorageResult.Failure(StorageError.NotFound)
        }

        override suspend fun rename(entry: StorageEntry, requestedName: String): StorageResult<StorageEntry> {
            renameCalls++
            if (blockRename) {
                renameStarted.complete(Unit)
                withContext(NonCancellable) {
                    releaseRename.await()
                }
            }
            children = listOf(folder, renamed, failed)
            renameCompleted.complete(Unit)
            return StorageResult.Success(renamed)
        }

        override suspend fun delete(entry: StorageEntry): StorageResult<Unit> {
            deleteCalls++
            if (blockDelete) {
                deleteStarted.complete(Unit)
                withContext(NonCancellable) {
                    releaseDelete.await()
                }
            }
            return if (failDelete && entry.ref == failed.ref) {
                StorageResult.Failure(StorageError.PermissionDenied)
            } else {
                children = children.filterNot { it.ref == entry.ref }
                StorageResult.Success(Unit)
            }
        }
    }

    private inner class MutationOwnershipProvider : StorageProvider {
        override val id = ProviderId("mutation-ownership")
        val root = entry(id, "root", EntryKind.DIRECTORY)
        val folder = entry(id, "folder", EntryKind.DIRECTORY, root)
        val first = entry(id, "first", EntryKind.FILE, root, capabilities = setOf(StorageCapability.RENAME))
        val firstRenamed = entry(id, "first-renamed", EntryKind.FILE, root, capabilities = setOf(StorageCapability.RENAME))
        val second = entry(id, "second", EntryKind.FILE, folder, capabilities = setOf(StorageCapability.RENAME))
        val secondRenamed = entry(id, "second-renamed", EntryKind.FILE, folder, capabilities = setOf(StorageCapability.RENAME))
        val firstRenameStarted = CompletableDeferred<Unit>()
        val releaseFirstRename = CompletableDeferred<Unit>()
        val firstRefreshStarted = CompletableDeferred<Unit>()
        val releaseFirstRefresh = CompletableDeferred<Unit>()
        val firstRefreshReturned = CompletableDeferred<Unit>()
        val secondRenameStarted = CompletableDeferred<Unit>()
        val secondMutationObservation = CompletableDeferred<Boolean>()
        val releaseSecondRename = CompletableDeferred<Unit>()
        var inFlightProbe: () -> Boolean = { true }
        private var children = listOf(folder, first)
        private var folderChildren = listOf(second)
        private var renameCalls = 0

        override suspend fun root() = StorageResult.Success(root)

        override suspend fun listChildren(directory: EntryRef): StorageResult<List<StorageEntry>> = when (directory) {
            root.ref -> {
                if (renameCalls >= 1 && !firstRefreshStarted.isCompleted) {
                    firstRefreshStarted.complete(Unit)
                    withContext(NonCancellable) {
                        releaseFirstRefresh.await()
                        firstRefreshReturned.complete(Unit)
                    }
                }
                StorageResult.Success(children)
            }
            folder.ref -> StorageResult.Success(folderChildren)
            else -> StorageResult.Failure(StorageError.NotFound)
        }

        override suspend fun rename(entry: StorageEntry, requestedName: String): StorageResult<StorageEntry> {
            renameCalls += 1
            if (entry.ref == first.ref) {
                firstRenameStarted.complete(Unit)
                withContext(NonCancellable) { releaseFirstRename.await() }
                children = listOf(folder, firstRenamed)
                return StorageResult.Success(firstRenamed)
            }
            secondRenameStarted.complete(Unit)
            val staleOwnerCleared = try {
                withTimeout(500) {
                    while (inFlightProbe()) yield()
                }
                true
            } catch (_: TimeoutCancellationException) {
                false
            }
            secondMutationObservation.complete(staleOwnerCleared)
            withContext(NonCancellable) { releaseSecondRename.await() }
            folderChildren = listOf(secondRenamed)
            return StorageResult.Success(secondRenamed)
        }
    }

    private inner class SelectionProvider(
        override val id: ProviderId = ProviderId("selection"),
    ) : StorageProvider {
        val root = entry(id, "root", EntryKind.DIRECTORY)
        val folder = entry(id, "folder", EntryKind.DIRECTORY, root)
        val child = entry(id, "old-name", EntryKind.FILE, root)
        val renamedChild = entry(id, "new-name", EntryKind.FILE, root)
        private var children = listOf(folder, child)

        override suspend fun root() = StorageResult.Success(root)

        override suspend fun listChildren(directory: EntryRef) = when (directory) {
            root.ref -> StorageResult.Success(children)
            folder.ref -> StorageResult.Success(emptyList())
            else -> StorageResult.Failure(StorageError.NotFound)
        }

        fun replaceChild(entry: StorageEntry) {
            children = listOf(folder, entry)
        }

        fun removeChild() {
            children = listOf(folder)
        }
    }

    private fun entry(
        providerId: ProviderId,
        name: String,
        kind: EntryKind,
        parent: StorageEntry? = null,
        capabilities: Set<StorageCapability> = if (kind == EntryKind.DIRECTORY) {
            setOf(StorageCapability.LIST_CHILDREN)
        } else {
            emptySet()
        },
    ) = StorageEntry(
        ref = TestEntryRef(providerId, name),
        displayName = name,
        kind = kind,
        sizeBytes = null,
        modifiedAtEpochMillis = null,
        mimeType = null,
        capabilities = capabilities,
        parentRef = parent?.ref,
    )

    private data class TestEntryRef(
        override val providerId: ProviderId,
        val token: String,
    ) : EntryRef {
        override val identityKey: String = "${providerId.value}\u0000$token"
    }
}
