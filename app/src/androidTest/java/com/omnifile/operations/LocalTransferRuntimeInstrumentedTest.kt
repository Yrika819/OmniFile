package com.omnifile.operations

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omnifile.operations.persistence.OperationDatabase
import com.omnifile.operations.persistence.OperationStore
import com.omnifile.storage.EntryKind
import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageResult
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalTransferRuntimeInstrumentedTest {
    @Test
    fun localCopyAndMoveCompleteWithDurableOrdering() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = context.filesDir.toPath().resolve("vs03-runtime")
        val databaseName = "vs03-runtime-operations.db"
        Files.createDirectories(root.resolve("copy-destination"))
        Files.createDirectories(root.resolve("move-destination"))
        val copyBytes = "pixel copy fixture".toByteArray()
        val moveBytes = "pixel move fixture".toByteArray()
        Files.write(root.resolve("copy-source.txt"), copyBytes)
        Files.write(root.resolve("move-source.txt"), moveBytes)
        val provider = LocalStorageProvider(root, ProviderId("local-runtime"))
        val database = Room.databaseBuilder(context, OperationDatabase::class.java, databaseName).build()
        try {
            val store = OperationStore(database.operationDao())
            val manager = OperationManager(store, mapOf(provider.id to provider))
            val rootEntry = provider.root().requireSuccess()
            val entries = provider.listChildren(rootEntry.ref).requireSuccess()
            val copySource = entries.single { it.displayName == "copy-source.txt" }
            val moveSource = entries.single { it.displayName == "move-source.txt" }
            val copyDestination = entries.single { it.displayName == "copy-destination" }
            val moveDestination = entries.single { it.displayName == "move-destination" }

            val copyId = manager.enqueue(
                OperationType.COPY,
                listOf(OperationManager.EnqueueItem(
                    provider.encodeDurableLocator(copySource.ref).requireSuccess(),
                    provider.encodeDurableLocator(copyDestination.ref).requireSuccess(),
                    "copy-source.txt",
                    copyBytes.size.toLong(),
                    null,
                )),
            ).requireSuccess().single()
            val copyResult = try { manager.execute(copyId) } catch (error: Throwable) { throw AssertionError("copy execution failed", error) }
            assertEquals(OperationState.COMPLETE, copyResult.state)
            assertTrue(Files.exists(root.resolve("copy-source.txt")))
            assertArrayEquals(copyBytes, Files.readAllBytes(root.resolve("copy-destination/copy-source.txt")))

            val moveId = manager.enqueue(
                OperationType.MOVE,
                listOf(OperationManager.EnqueueItem(
                    provider.encodeDurableLocator(moveSource.ref).requireSuccess(),
                    provider.encodeDurableLocator(moveDestination.ref).requireSuccess(),
                    "move-source.txt",
                    moveBytes.size.toLong(),
                    null,
                )),
            ).requireSuccess().single()
            val moveResult = try { manager.execute(moveId) } catch (error: Throwable) {
                val persisted = store.find(moveId)
                throw AssertionError("move execution failed state=${persisted?.state} stage=${persisted?.stage} delete=${persisted?.sourceDeleteState}", error)
            }
            assertEquals(OperationState.COMPLETE, moveResult.state)
            assertFalse(Files.exists(root.resolve("move-source.txt")))
            assertArrayEquals(moveBytes, Files.readAllBytes(root.resolve("move-destination/move-source.txt")))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
            root.toFile().deleteRecursively()
        }
    }

    private fun <T> StorageResult<T>.requireSuccess(): T = when (this) {
        is StorageResult.Success -> value
        is StorageResult.Failure -> error("Expected success, got $error")
    }
}
