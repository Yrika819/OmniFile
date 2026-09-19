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

    @Test
    fun renameUsesProviderReturnedIdentityAndSanitizedName() = runBlocking {
        TestDocumentsProvider.configureRename(
            "root/alpha",
            "root/renamed-alpha",
            "provider-name.txt",
        )
        val provider = testProvider()
        val root = provider.root().requireSuccess()
        val original = provider.listChildren(root.ref).requireSuccess().single { it.displayName == "alpha.txt" }

        val renamed = provider.rename(original, "requested-name.txt").requireSuccess()

        assertTrue(renamed.ref != original.ref)
        assertEquals("provider-name.txt", renamed.displayName)
        assertEquals(
            listOf("Folder A", "empty", "provider-name.txt"),
            provider.listChildren(root.ref).requireSuccess().map { it.displayName },
        )
    }

    @Test
    fun missingRenameFlagIsUnsupportedAndDoesNotCallProvider() = runBlocking {
        TestDocumentsProvider.setRenameSupported("root/alpha", false)
        val provider = testProvider()
        val root = provider.root().requireSuccess()
        val file = provider.listChildren(root.ref).requireSuccess().single { it.displayName == "alpha.txt" }

        assertFalse(StorageCapability.RENAME in file.capabilities)
        assertEquals(StorageError.Unsupported, provider.rename(file, "new.txt").failure().error)
        assertEquals(0, TestDocumentsProvider.renameCalls())
    }

    @Test
    fun rootNeverExposesMutationCapabilitiesEvenIfProviderReportsThem() = runBlocking {
        TestDocumentsProvider.enableRootMutationFlags()
        val root = testProvider().root().requireSuccess()

        assertFalse(StorageCapability.RENAME in root.capabilities)
        assertFalse(StorageCapability.DELETE in root.capabilities)
    }

    @Test
    fun deleteUsesProviderDeleteAndRefreshesTruth() = runBlocking {
        val provider = testProvider()
        val root = provider.root().requireSuccess()
        val file = provider.listChildren(root.ref).requireSuccess().single { it.displayName == "alpha.txt" }

        assertEquals(StorageResult.Success(Unit), provider.delete(file))
        assertEquals(1, TestDocumentsProvider.deleteCalls())
        assertEquals(listOf("Folder A", "empty"), provider.listChildren(root.ref).requireSuccess().map { it.displayName })
    }

    @Test
    fun missingDeleteFlagIsUnsupportedAndDoesNotCallProvider() = runBlocking {
        TestDocumentsProvider.setDeleteSupported("root/alpha", false)
        val provider = testProvider()
        val root = provider.root().requireSuccess()
        val file = provider.listChildren(root.ref).requireSuccess().single { it.displayName == "alpha.txt" }

        assertFalse(StorageCapability.DELETE in file.capabilities)
        assertEquals(StorageError.Unsupported, provider.delete(file).failure().error)
        assertEquals(0, TestDocumentsProvider.deleteCalls())
    }

    @Test
    fun providerDeleteFailureIsMappedWithoutDeletingEntry() = runBlocking {
        TestDocumentsProvider.configureDeleteFailure("root/alpha")
        val provider = testProvider()
        val root = provider.root().requireSuccess()
        val file = provider.listChildren(root.ref).requireSuccess().single { it.displayName == "alpha.txt" }

        assertEquals(StorageError.IoFailure("controlled delete failure"), provider.delete(file).failure().error)
        assertEquals(listOf("Folder A", "alpha.txt", "empty"), provider.listChildren(root.ref).requireSuccess().map { it.displayName })
    }

    @Test
    fun staleTreeReferenceIsRejectedForRenameAndDelete() = runBlocking {
        val sourceProvider = testProvider(id = ProviderId("source"))
        val root = sourceProvider.root().requireSuccess()
        val file = sourceProvider.listChildren(root.ref).requireSuccess().single { it.displayName == "alpha.txt" }
        val otherProvider = testProvider(id = ProviderId("other"))

        assertEquals(StorageError.StaleReference, otherProvider.rename(file, "new.txt").failure().error)
        assertEquals(StorageError.StaleReference, otherProvider.delete(file).failure().error)
    }

    private fun testProvider(id: ProviderId = ProviderId("saf-test")) = SafStorageProvider(
        contentResolver = resolver,
        treeUri = TestDocumentsProvider.ROOT_URI,
        id = id,
    )

    private fun <T> StorageResult<T>.requireSuccess(): T = when (this) {
        is StorageResult.Success -> value
        is StorageResult.Failure -> throw AssertionError("SAF provider failure: $error")
    }

    private fun <T> StorageResult<T>.failure(): StorageResult.Failure = this as StorageResult.Failure
}
