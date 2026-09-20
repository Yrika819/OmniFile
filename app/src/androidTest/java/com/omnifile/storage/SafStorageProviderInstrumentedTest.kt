package com.omnifile.storage

import android.content.Context
import android.content.Intent
import android.content.ContentResolver
import android.content.pm.ProviderInfo
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
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
        assertTrue(StorageCapability.READ_SEQUENTIAL in file.capabilities)
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
        val provider = testProvider()
        val root = provider.root().requireSuccess()

        assertFalse(StorageCapability.RENAME in root.capabilities)
        assertFalse(StorageCapability.DELETE in root.capabilities)
        assertEquals(StorageError.Unsupported, provider.rename(root, "renamed").failure().error)
        assertEquals(StorageError.Unsupported, provider.delete(root).failure().error)
        assertEquals(0, TestDocumentsProvider.renameCalls())
        assertEquals(0, TestDocumentsProvider.deleteCalls())
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
    fun providerRenameFailureIsMappedWithoutDeletingEntry() = runBlocking {
        TestDocumentsProvider.configureRenameFailure("root/alpha")
        val provider = testProvider()
        val root = provider.root().requireSuccess()
        val file = provider.listChildren(root.ref).requireSuccess().single { it.displayName == "alpha.txt" }

        assertEquals(StorageError.IoFailure("controlled rename failure"), provider.rename(file, "new.txt").failure().error)
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

        val otherTree = SafStorageProvider(
            contentResolver = resolver,
            treeUri = DocumentsContract.buildTreeDocumentUri(TestDocumentsProvider.AUTHORITY, "root/folder"),
            id = ProviderId("source"),
        )
        assertEquals(StorageError.StaleReference, otherTree.rename(file, "new.txt").failure().error)
        assertEquals(StorageError.StaleReference, otherTree.delete(file).failure().error)
    }

    @Test
    fun sequentialReadWorksWithPipeBackedDescriptor() = runBlocking {
        val provider = testProvider()
        val root = provider.root().requireSuccess()
        val file = provider.listChildren(root.ref).requireSuccess().single { it.displayName == "alpha.txt" }
        val locator = provider.encodeDurableLocator(file.ref).requireSuccess()

        val handle = provider.openSequentialRead(locator).requireSuccess()
        assertEquals(5L, handle.expectedBytes)
        assertArrayEquals("alpha".toByteArray(), readAll(handle))
        assertEquals(1, TestDocumentsProvider.readOpenCalls())
    }

    @Test
    fun operationOwnedPartialWritesAndFinalizationPersistReturnedIdentity() = runBlocking {
        val provider = testProvider()
        val root = provider.root().requireSuccess()
        val parentLocator = provider.encodeDurableLocator(root.ref).requireSuccess()
        val partial = provider.createOperationPartial(parentLocator, "final.txt", "op-1").requireSuccess()
        val bytes = "controlled saf".toByteArray()

        val writer = provider.openSequentialWrite(partial, append = false, operationId = "op-1").requireSuccess()
        writer.write(bytes, 0, bytes.size)
        writer.flush()
        writer.close()
        assertTrue(TestDocumentsProvider.awaitPendingIo())

        assertArrayEquals(bytes, readAll(provider.openSequentialRead(partial).requireSuccess()))
        assertEquals(bytes.size.toLong(), provider.inspectTransfer(partial).requireSuccess().sizeBytes)

        val oldDocumentId = SafDurableLocatorCodec.decode(partial.value)!!.documentId
        TestDocumentsProvider.configureRename(oldDocumentId, "root/returned-final", "provider-final.txt")
        val finalized = provider.finalizeOperationPartial(partial, parentLocator, "final.txt", "op-1").requireSuccess()
        val finalLocator = (finalized as FinalizationResult.Finalized).finalLocator
        val finalEntry = provider.resolveDurableLocator(finalLocator).requireSuccess()

        assertEquals("provider-final.txt", finalEntry.displayName)
        assertArrayEquals(bytes, readAll(provider.openSequentialRead(finalLocator).requireSuccess()))
        assertFalse(TestDocumentsProvider.exists(oldDocumentId))
        assertTrue(TestDocumentsProvider.exists("root/returned-final"))
    }

    @Test
    fun unknownSizeRemainsUnknownButSequentialReadStillWorks() = runBlocking {
        TestDocumentsProvider.setUnknownSize("root/alpha", true)
        val provider = testProvider()
        val root = provider.root().requireSuccess()
        val file = provider.listChildren(root.ref).requireSuccess().single { it.displayName == "alpha.txt" }
        val locator = provider.encodeDurableLocator(file.ref).requireSuccess()

        assertEquals(null, file.sizeBytes)
        assertEquals(null, provider.inspectTransfer(locator).requireSuccess().sizeBytes)
        val handle = provider.openSequentialRead(locator).requireSuccess()
        assertEquals(null, handle.expectedBytes)
        assertArrayEquals("alpha".toByteArray(), readAll(handle))
    }

    @Test
    fun readOnlyGrantCannotCreateOrWriteDestination() = runBlocking {
        val provider = SafStorageProvider(
            contentResolver = resolver,
            treeUri = TestDocumentsProvider.ROOT_URI,
            id = ProviderId("saf-read-only"),
            grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val root = provider.root().requireSuccess()
        assertTrue(StorageCapability.READ_SEQUENTIAL in root.ref.let { provider.listChildren(it).requireSuccess().single { entry -> entry.displayName == "alpha.txt" }.capabilities })
        assertTrue(StorageCapability.CREATE_CHILD !in root.capabilities)
        assertTrue(StorageCapability.WRITE !in root.capabilities)
        val parentLocator = provider.encodeDurableLocator(root.ref).requireSuccess()
        assertEquals(
            StorageError.Unsupported,
            provider.createOperationPartial(parentLocator, "read-only.txt", "op-read-only").failure().error,
        )
    }

    @Test
    fun destinationConflictDoesNotOverwriteAndOwnedPartialRemainsAddressable() = runBlocking {
        val provider = testProvider()
        val root = provider.root().requireSuccess()
        val parentLocator = provider.encodeDurableLocator(root.ref).requireSuccess()
        val partial = provider.createOperationPartial(parentLocator, "alpha.txt", "op-conflict").requireSuccess()
        val conflict = provider.finalizeOperationPartial(partial, parentLocator, "alpha.txt", "op-conflict")

        assertEquals(StorageError.NameConflict("alpha.txt"), conflict.failure().error)
        assertTrue(provider.resolveDurableLocator(partial).isSuccess())
        assertEquals(StorageResult.Success(Unit), provider.deleteOperationPartial(partial, "op-conflict"))
        assertTrue(TestDocumentsProvider.exists("root/alpha"))
    }

    private fun testProvider(id: ProviderId = ProviderId("saf-test")) = SafStorageProvider(
        contentResolver = resolver,
        treeUri = TestDocumentsProvider.ROOT_URI,
        id = id,
        finalizationProven = true,
        sourceVersionProven = true,
    )

    private fun readAll(handle: SequentialReadHandle): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(32)
        try {
            while (true) {
                val read = handle.read(buffer, 0, buffer.size)
                if (read < 0) break
                if (read > 0) output.write(buffer, 0, read)
            }
        } finally {
            handle.close()
        }
        return output.toByteArray()
    }

    private fun <T> StorageResult<T>.isSuccess(): Boolean = this is StorageResult.Success

    private fun <T> StorageResult<T>.requireSuccess(): T = when (this) {
        is StorageResult.Success -> value
        is StorageResult.Failure -> throw AssertionError("SAF provider failure: $error")
    }

    private fun <T> StorageResult<T>.failure(): StorageResult.Failure = this as StorageResult.Failure
}
