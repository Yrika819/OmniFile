package com.omnifile.operations

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ProviderInfo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifile.operations.persistence.OperationDatabase
import com.omnifile.operations.persistence.OperationStore
import com.omnifile.storage.EntryKind
import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.ProviderId
import com.omnifile.storage.SafDurableLocatorCodec
import com.omnifile.storage.SafStorageProvider
import com.omnifile.storage.StorageResult
import com.omnifile.storage.TestDocumentsProvider
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafTransferRuntimeInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var resolver: ContentResolver
    private lateinit var database: OperationDatabase
    private lateinit var localRoot: java.nio.file.Path
    private val databaseName = "vs04-saf-runtime-operations.db"

    @Before
    fun setUp() {
        TestDocumentsProvider.reset()
        val provider = TestDocumentsProvider()
        provider.attachInfo(
            InstrumentationRegistry.getInstrumentation().context,
            ProviderInfo().apply {
                authority = TestDocumentsProvider.AUTHORITY
                readPermission = "android.permission.MANAGE_DOCUMENTS"
                writePermission = "android.permission.MANAGE_DOCUMENTS"
                exported = true
                grantUriPermissions = true
            },
        )
        resolver = ContentResolver.wrap(provider)
        context.deleteDatabase(databaseName)
        localRoot = context.filesDir.toPath().resolve("vs04-saf-runtime")
        localRoot.toFile().deleteRecursively()
        Files.createDirectories(localRoot)
        database = Room.databaseBuilder(context, OperationDatabase::class.java, databaseName).build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
        localRoot.toFile().deleteRecursively()
    }

    @Test
    fun localToSafCopyAndMoveUseDurableFinalizationBeforeSourceDelete() = runBlocking {
        val local = LocalStorageProvider(localRoot, ProviderId("local-runtime"))
        val saf = SafStorageProvider(resolver, TestDocumentsProvider.ROOT_URI, ProviderId("saf-runtime"), finalizationProven = true)
        val manager = OperationManager(OperationStore(database.operationDao()), mapOf(local.id to local, saf.id to saf))
        val localCopy = localRoot.resolve("local-copy.txt")
        val localMove = localRoot.resolve("local-move.txt")
        Files.write(localCopy, "local-to-saf-copy".toByteArray())
        Files.write(localMove, "local-to-saf-move".toByteArray())
        val localRootEntry = local.root().requireSuccess()
        val safRoot = saf.root().requireSuccess()
        val copySource = local.listChildren(localRootEntry.ref).requireSuccess().single { it.displayName == "local-copy.txt" }
        val moveSource = local.listChildren(localRootEntry.ref).requireSuccess().single { it.displayName == "local-move.txt" }
        val localCopyResult = manager.execute(manager.enqueue(
            OperationType.COPY,
            listOf(item(local, copySource.ref, saf, safRoot.ref, copySource.displayName, copySource.sizeBytes)),
        ).requireSuccess().single())
        assertEquals(OperationState.COMPLETE, localCopyResult.state)
        assertArrayEquals("local-to-saf-copy".toByteArray(), safBytes(saf, safRoot.ref, "local-copy.txt"))
        assertTrue(Files.exists(localCopy))

        val moveResult = manager.execute(manager.enqueue(
            OperationType.MOVE,
            listOf(item(local, moveSource.ref, saf, safRoot.ref, moveSource.displayName, moveSource.sizeBytes)),
        ).requireSuccess().single())
        assertEquals(OperationState.COMPLETE, moveResult.state)
        assertFalse(Files.exists(localMove))
        assertArrayEquals("local-to-saf-move".toByteArray(), safBytes(saf, safRoot.ref, "local-move.txt"))
        assertNoPartials(saf, safRoot.ref)
    }

    @Test
    fun safToLocalCopyAndMoveUseDurableSourceDeleteOrdering() = runBlocking {
        val local = LocalStorageProvider(localRoot, ProviderId("local-runtime"))
        val saf = SafStorageProvider(resolver, TestDocumentsProvider.ROOT_URI, ProviderId("saf-runtime"), finalizationProven = true)
        val manager = OperationManager(OperationStore(database.operationDao()), mapOf(local.id to local, saf.id to saf))
        val localRootEntry = local.root().requireSuccess()
        val safRoot = saf.root().requireSuccess()
        val copySource = saf.listChildren(safRoot.ref).requireSuccess().single { it.displayName == "alpha.txt" }
        val copyResult = manager.execute(manager.enqueue(
            OperationType.COPY,
            listOf(item(saf, copySource.ref, local, localRootEntry.ref, "saf-copy.txt", copySource.sizeBytes)),
        ).requireSuccess().single())
        assertEquals(OperationState.COMPLETE, copyResult.state)
        assertArrayEquals("alpha".toByteArray(), Files.readAllBytes(localRoot.resolve("saf-copy.txt")))
        assertTrue(TestDocumentsProvider.exists("root/alpha"))

        val moveSource = saf.listChildren(safRoot.ref).requireSuccess().single { it.displayName == "alpha.txt" }
        val moveResult = manager.execute(manager.enqueue(
            OperationType.MOVE,
            listOf(item(saf, moveSource.ref, local, localRootEntry.ref, "saf-move.txt", moveSource.sizeBytes)),
        ).requireSuccess().single())
        assertEquals(OperationState.COMPLETE, moveResult.state)
        assertArrayEquals("alpha".toByteArray(), Files.readAllBytes(localRoot.resolve("saf-move.txt")))
        assertFalse(TestDocumentsProvider.exists("root/alpha"))
    }

    @Test
    fun safToSafCopyAndMoveDoNotAssumeNativeMove() = runBlocking {
        val saf = SafStorageProvider(resolver, TestDocumentsProvider.ROOT_URI, ProviderId("saf-runtime"), finalizationProven = true)
        val manager = OperationManager(OperationStore(database.operationDao()), mapOf(saf.id to saf))
        val root = saf.root().requireSuccess()
        val folder = saf.listChildren(root.ref).requireSuccess().single { it.kind == EntryKind.DIRECTORY && it.displayName == "Folder A" }
        val source = saf.listChildren(root.ref).requireSuccess().single { it.displayName == "alpha.txt" }
        val copyResult = manager.execute(manager.enqueue(
            OperationType.COPY,
            listOf(item(saf, source.ref, saf, folder.ref, "alpha-copy.txt", source.sizeBytes)),
        ).requireSuccess().single())
        assertEquals(OperationState.COMPLETE, copyResult.state)
        assertArrayEquals("alpha".toByteArray(), safBytes(saf, folder.ref, "alpha-copy.txt"))
        assertTrue(TestDocumentsProvider.exists("root/alpha"))

        val moveSource = saf.listChildren(root.ref).requireSuccess().single { it.displayName == "alpha.txt" }
        val moveResult = manager.execute(manager.enqueue(
            OperationType.MOVE,
            listOf(item(saf, moveSource.ref, saf, folder.ref, "alpha-moved.txt", moveSource.sizeBytes)),
        ).requireSuccess().single())
        assertEquals(OperationState.COMPLETE, moveResult.state)
        assertArrayEquals("alpha".toByteArray(), safBytes(saf, folder.ref, "alpha-moved.txt"))
        assertFalse(TestDocumentsProvider.exists("root/alpha"))
    }

    private suspend fun item(
        sourceProvider: SafStorageProvider,
        source: com.omnifile.storage.EntryRef,
        destinationProvider: SafStorageProvider,
        destination: com.omnifile.storage.EntryRef,
        name: String,
        size: Long?,
    ): OperationManager.EnqueueItem = OperationManager.EnqueueItem(
        sourceProvider.encodeDurableLocator(source).requireSuccess(),
        destinationProvider.encodeDurableLocator(destination).requireSuccess(),
        name,
        size,
        null,
    )

    private suspend fun item(
        sourceProvider: LocalStorageProvider,
        source: com.omnifile.storage.EntryRef,
        destinationProvider: SafStorageProvider,
        destination: com.omnifile.storage.EntryRef,
        name: String,
        size: Long?,
    ): OperationManager.EnqueueItem = OperationManager.EnqueueItem(
        sourceProvider.encodeDurableLocator(source).requireSuccess(),
        destinationProvider.encodeDurableLocator(destination).requireSuccess(),
        name,
        size,
        null,
    )

    private suspend fun item(
        sourceProvider: SafStorageProvider,
        source: com.omnifile.storage.EntryRef,
        destinationProvider: LocalStorageProvider,
        destination: com.omnifile.storage.EntryRef,
        name: String,
        size: Long?,
    ): OperationManager.EnqueueItem = OperationManager.EnqueueItem(
        sourceProvider.encodeDurableLocator(source).requireSuccess(),
        destinationProvider.encodeDurableLocator(destination).requireSuccess(),
        name,
        size,
        null,
    )

    private suspend fun safBytes(
        provider: SafStorageProvider,
        parent: com.omnifile.storage.EntryRef,
        name: String,
    ): ByteArray {
        val entry = provider.listChildren(parent).requireSuccess().single { it.displayName == name }
        val locator = provider.encodeDurableLocator(entry.ref).requireSuccess()
        val handle = provider.openSequentialRead(locator).requireSuccess()
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

    private suspend fun assertNoPartials(provider: SafStorageProvider, parent: com.omnifile.storage.EntryRef) {
        assertTrue(provider.listChildren(parent).requireSuccess().none { it.displayName.startsWith(".omnifile-") })
    }

    private fun <T> StorageResult<T>.requireSuccess(): T = when (this) {
        is StorageResult.Success -> value
        is StorageResult.Failure -> error("Expected success, got $error")
    }
}
