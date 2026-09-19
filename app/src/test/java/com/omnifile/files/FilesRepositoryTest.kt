package com.omnifile.files

import com.omnifile.storage.EntryKind
import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageCapability
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageProvider
import com.omnifile.storage.StorageResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FilesRepositoryTest {
    @Test
    fun dispatchesRootAndChildrenToTheEntryProvider() = runBlocking {
        val provider = FakeProvider(ProviderId("fake"))
        val repository = FilesRepository(mapOf(provider.id to provider))

        val root = (repository.root(provider.id) as StorageResult.Success).value
        val children = (repository.children(root) as StorageResult.Success).value

        assertEquals("root", root.displayName)
        assertEquals(listOf("folder"), children.map { it.displayName })
        assertEquals(listOf(provider.id, provider.id), provider.calls)
    }

    @Test
    fun renameDelegatesOnlyToTheEntryProvider() = runBlocking {
        val selected = FakeProvider(ProviderId("selected"))
        val other = FakeProvider(ProviderId("other"))
        val repository = FilesRepository(mapOf(other.id to other, selected.id to selected))
        val entry = selected.child

        val result = repository.rename(entry, "renamed.txt")

        assertEquals(StorageResult.Success(selected.renamed), result)
        assertEquals(listOf("rename:renamed.txt"), selected.mutationCalls)
        assertEquals(emptyList<String>(), other.mutationCalls)
    }

    @Test
    fun deleteDelegatesOnlyToTheEntryProvider() = runBlocking {
        val selected = FakeProvider(ProviderId("selected"))
        val other = FakeProvider(ProviderId("other"))
        val repository = FilesRepository(mapOf(other.id to other, selected.id to selected))

        val result = repository.delete(selected.child)

        assertEquals(StorageResult.Success(Unit), result)
        assertEquals(listOf("delete:child"), selected.mutationCalls)
        assertEquals(emptyList<String>(), other.mutationCalls)
    }

    private class FakeProvider(override val id: ProviderId) : StorageProvider {
        val calls = mutableListOf<ProviderId>()
        val mutationCalls = mutableListOf<String>()
        private val root = entry("root", EntryKind.DIRECTORY, setOf(StorageCapability.LIST_CHILDREN))
        private val folder = entry("folder", EntryKind.DIRECTORY, setOf(StorageCapability.LIST_CHILDREN))
        val child = entry(
            "child",
            EntryKind.FILE,
            setOf(StorageCapability.RENAME, StorageCapability.DELETE),
        )
        val renamed = entry(
            "renamed.txt",
            EntryKind.FILE,
            setOf(StorageCapability.RENAME, StorageCapability.DELETE),
        )

        override suspend fun root(): StorageResult<StorageEntry> {
            calls += id
            return StorageResult.Success(root)
        }

        override suspend fun listChildren(directory: EntryRef): StorageResult<List<StorageEntry>> {
            calls += id
            return StorageResult.Success(listOf(folder))
        }

        override suspend fun rename(
            entry: StorageEntry,
            requestedName: String,
        ): StorageResult<StorageEntry> {
            mutationCalls += "rename:$requestedName"
            return StorageResult.Success(renamed)
        }

        override suspend fun delete(entry: StorageEntry): StorageResult<Unit> {
            mutationCalls += "delete:${entry.displayName}"
            return StorageResult.Success(Unit)
        }

        private fun entry(name: String, kind: EntryKind, capabilities: Set<StorageCapability>) =
            StorageEntry(TestEntryRef(id, name), name, kind, null, null, null, capabilities)
    }

    private data class TestEntryRef(
        override val providerId: ProviderId,
        val token: String,
    ) : EntryRef {
        override val identityKey: String = "${providerId.value}\u0000$token"
    }
}
