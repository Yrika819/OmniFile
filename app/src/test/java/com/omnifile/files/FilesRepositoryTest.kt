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

    private class FakeProvider(override val id: ProviderId) : StorageProvider {
        val calls = mutableListOf<ProviderId>()
        private val root = entry("root", EntryKind.DIRECTORY, setOf(StorageCapability.LIST_CHILDREN))

        override suspend fun root(): StorageResult<StorageEntry> {
            calls += id
            return StorageResult.Success(root)
        }

        override suspend fun listChildren(directory: EntryRef): StorageResult<List<StorageEntry>> {
            calls += id
            return StorageResult.Success(listOf(entry("folder", EntryKind.DIRECTORY, setOf(StorageCapability.LIST_CHILDREN))))
        }

        private fun entry(name: String, kind: EntryKind, capabilities: Set<StorageCapability>) =
            StorageEntry(TestEntryRef(id, name), name, kind, null, null, null, capabilities)
    }

    private data class TestEntryRef(
        override val providerId: ProviderId,
        val token: String,
    ) : EntryRef
}
