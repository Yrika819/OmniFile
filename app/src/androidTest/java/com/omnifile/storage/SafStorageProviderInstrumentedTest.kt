package com.omnifile.storage

import android.content.Context
import android.content.ContentResolver
import android.content.pm.ProviderInfo
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafStorageProviderInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().context
    private lateinit var resolver: ContentResolver

    @Before
    fun registerControlledProviderInProcess() {
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
    fun listsRootNestedFolderAndRealMetadataWithoutClaimingSeekability() = runBlocking {
        val provider = SafStorageProvider(
            contentResolver = resolver,
            treeUri = TestDocumentsProvider.ROOT_URI,
            id = ProviderId("saf-test"),
        )

        val root = provider.root().requireSuccess()
        val rootChildren = provider.listChildren(root.ref).requireSuccess()
        val folder = rootChildren.single { it.displayName == "Folder A" }
        val file = rootChildren.single { it.displayName == "alpha.txt" }
        val nestedChildren = provider.listChildren(folder.ref).requireSuccess()

        assertEquals(EntryKind.DIRECTORY, folder.kind)
        assertEquals(5L, file.sizeBytes)
        assertEquals(3000L, file.modifiedAtEpochMillis)
        assertTrue(StorageCapability.LIST_CHILDREN in folder.capabilities)
        assertTrue(StorageCapability.READ_SEQUENTIAL !in file.capabilities)
        assertFalse(StorageCapability.READ_SEEKABLE in file.capabilities)
        assertTrue(StorageCapability.RENAME in file.capabilities)
        assertEquals(listOf("nested.txt"), nestedChildren.map { it.displayName })
    }

    @Test
    fun selectedTreeUriRemainsTheNavigationBoundary() = runBlocking {
        val provider = SafStorageProvider(
            contentResolver = resolver,
            treeUri = DocumentsContract.buildTreeDocumentUri(TestDocumentsProvider.AUTHORITY, "root/folder"),
            id = ProviderId("saf-test"),
        )

        val root = provider.root().requireSuccess()
        val children = provider.listChildren(root.ref).requireSuccess()

        assertEquals(listOf("nested.txt"), children.map { it.displayName })
        assertEquals(ProviderId("saf-test"), root.ref.providerId)
    }

    private fun <T> StorageResult<T>.requireSuccess(): T = when (this) {
        is StorageResult.Success -> value
        is StorageResult.Failure -> throw AssertionError("SAF provider failure: $error")
    }
}
