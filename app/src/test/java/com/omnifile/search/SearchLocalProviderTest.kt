package com.omnifile.search

import com.omnifile.storage.EntryKind
import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageResult
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files

class SearchLocalProviderTest {
    @Test
    fun searchesRootNestedAndDuplicateNamesWithoutFollowingSymlinkDirectories() = runBlocking {
        val root = Files.createTempDirectory("omnifile-search-local")
        val outside = Files.createTempDirectory("omnifile-search-outside")
        try {
            Files.createFile(root.resolve("needle-root.txt"))
            val first = Files.createDirectories(root.resolve("first"))
            val second = Files.createDirectories(root.resolve("second"))
            Files.createFile(first.resolve("duplicate.txt"))
            Files.createFile(second.resolve("duplicate.txt"))
            Files.createFile(first.resolve("needle-nested.txt"))
            Files.createFile(outside.resolve("needle-outside.txt"))
            val link = root.resolve("needle-link")
            try {
                Files.createSymbolicLink(link, outside)
            } catch (_: UnsupportedOperationException) {
                assumeTrue("symbolic links are supported", false)
            } catch (_: SecurityException) {
                assumeTrue("symbolic links are permitted", false)
            }

            val provider = LocalStorageProvider(root, ProviderId("local-search-test"))
            val rootEntry = (provider.root() as StorageResult.Success).value
            val emissions = SearchEngine().search(
                request = SearchRequest(SearchScope.CurrentFolder(provider.id, rootEntry), "needle"),
                roots = { StorageResult.Success(listOf(rootEntry)) },
                children = { entry -> provider.listChildren(entry.ref) },
            ).toList()
            val completed = emissions.filterIsInstance<SearchEmission.Completed>().single()

            assertTrue(completed.hits.any { it.entry.displayName == "needle-root.txt" })
            assertTrue(completed.hits.any { it.entry.displayName == "needle-nested.txt" })
            assertTrue(completed.hits.any { it.entry.displayName == "needle-link" })
            assertTrue(completed.hits.none { it.entry.displayName == "needle-outside.txt" })

            val duplicateEmissions = SearchEngine().search(
                request = SearchRequest(SearchScope.CurrentFolder(provider.id, rootEntry), "duplicate"),
                roots = { StorageResult.Success(listOf(rootEntry)) },
                children = { entry -> provider.listChildren(entry.ref) },
            ).toList()
            val duplicates = duplicateEmissions.filterIsInstance<SearchEmission.Completed>().single().hits
            assertEquals(2, duplicates.size)
            assertTrue(duplicates.map { it.entry.ref.identityKey }.toSet().size == 2)
        } finally {
            root.toFile().deleteRecursively()
            outside.toFile().deleteRecursively()
        }
    }
}
