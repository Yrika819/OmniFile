package com.omnifile.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifile.storage.EntryKind
import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageResult
import java.nio.file.Files
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchLocalProviderInstrumentedTest {
    private val root = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        .toPath()
        .resolve("vs05-search-local-runtime")
    private lateinit var provider: LocalStorageProvider

    @Before
    fun createDisposableTree() {
        root.toFile().deleteRecursively()
        Files.createDirectories(root.resolve("first"))
        Files.createDirectories(root.resolve("second"))
        Files.createFile(root.resolve("root-needle.txt"))
        Files.createFile(root.resolve("first").resolve("report.txt"))
        Files.createFile(root.resolve("second").resolve("report.txt"))
        provider = LocalStorageProvider(root, ProviderId("local-search-runtime"))
    }

    @After
    fun deleteDisposableTree() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun searchesNestedAndDuplicateNamesWithinAppPrivateLocalRoot() = runBlocking {
        val rootEntry = (provider.root() as StorageResult.Success).value
        val emissions = SearchEngine().search(
            request = SearchRequest(SearchScope.CurrentFolder(provider.id, rootEntry), "report"),
            roots = { StorageResult.Success(listOf(rootEntry)) },
            children = { entry -> provider.listChildren(entry.ref) },
        ).toList()
        val completed = emissions.filterIsInstance<SearchEmission.Completed>().single()

        assertEquals(listOf("first", "second"), completed.hits.map { it.ancestors.last().displayName }.distinct())
        assertEquals(listOf("report.txt", "report.txt"), completed.hits.map { it.entry.displayName })
        assertTrue(completed.hits.map { it.entry.ref.identityKey }.toSet().size == 2)
        assertTrue(completed.complete)
    }
}
