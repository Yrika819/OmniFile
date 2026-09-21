package com.omnifile.search

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ProviderInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifile.storage.ProviderId
import com.omnifile.storage.SafStorageProvider
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import com.omnifile.storage.TestDocumentsProvider
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchSafProviderInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().context
    private lateinit var resolver: ContentResolver

    @Before
    fun registerControlledProviderInProcess() {
        TestDocumentsProvider.reset()
        val provider = TestDocumentsProvider()
        provider.attachInfo(
            context,
            ProviderInfo().apply {
                authority = TestDocumentsProvider.AUTHORITY
                readPermission = "android.permission.MANAGE_DOCUMENTS"
                writePermission = "android.permission.MANAGE_DOCUMENTS"
                exported = true
                grantUriPermissions = true
            },
        )
        resolver = ContentResolver.wrap(provider)
    }

    @Test
    fun providerOutageAtSearchRootIsNotReportedAsNoMatches() = runBlocking {
        val provider = SafStorageProvider(
            contentResolver = resolver,
            treeUri = TestDocumentsProvider.ROOT_URI,
            id = ProviderId("saf-search-outage"),
        )
        val root = provider.root().requireSuccess()
        TestDocumentsProvider.setProviderUnavailable(true)
        val emissions = SearchEngine().search(
            request = SearchRequest(
                SearchScope.CurrentFolder(ProviderId("saf-search-outage"), root),
                "nested",
            ),
            roots = {
                when (val result = provider.root()) {
                    is StorageResult.Success -> StorageResult.Success(listOf(result.value))
                    is StorageResult.Failure -> StorageResult.Failure(result.error)
                }
            },
            children = { entry -> provider.listChildren(entry.ref) },
        ).toList()
        val completed = emissions.filterIsInstance<SearchEmission.Completed>().single()

        assertEquals(StorageError.ProviderUnavailable, completed.rootError)
        assertTrue(completed.hits.isEmpty())
    }

    @Test
    fun recursivelySearchesOnlyWithinSelectedSafTree() = runBlocking {
        val provider = SafStorageProvider(
            contentResolver = resolver,
            treeUri = TestDocumentsProvider.ROOT_URI,
            id = ProviderId("saf-search-test"),
        )
        val root = provider.root().requireSuccess()
        val emissions = SearchEngine().search(
            request = SearchRequest(SearchScope.CurrentFolder(provider.id, root), "nested"),
            roots = { StorageResult.Success(listOf(root)) },
            children = { entry -> provider.listChildren(entry.ref) },
        ).toList()
        val completed = emissions.filterIsInstance<SearchEmission.Completed>().single()

        assertEquals(listOf("nested.txt"), completed.hits.map { it.entry.displayName })
        assertEquals(listOf("Controlled SAF", "Folder A"), completed.hits.single().ancestors.map { it.displayName })
        assertTrue(completed.complete)
    }
}


private suspend fun <T> StorageResult<T>.requireSuccess(): T = when (this) {
    is StorageResult.Success -> value
    is StorageResult.Failure -> error("Expected success but received $error")
}
