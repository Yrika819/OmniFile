package com.omnifile.preview

import android.os.SharedMemory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifile.storage.*
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedMemoryOwnershipInstrumentedTest {
    private class ManualDispatcher : CoroutineDispatcher() {
        private val queue = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.add(block) }
        fun drain() { while (true) (queue.poll() ?: return).run() }
    }
    private class MemoryOwner(val memory: SharedMemory) {
        val closes = AtomicInteger()
        fun close() { check(closes.incrementAndGet() == 1); memory.close() }
    }
    private fun fdCount(): Int = File("/proc/self/fd").list().orEmpty().size

    @Test fun fiftyActualSharedMemoryCallbackCancellationCyclesHaveOneClose() = runBlocking {
        val counts = mutableListOf<Int>()
        repeat(55) { cycle ->
            val dispatcher = ManualDispatcher()
            val lifecycle = CoroutineScope(SupervisorJob() + dispatcher)
            val owner = MemoryOwner(SharedMemory.create("vs10-response-race", 4096))
            val holder = OwningResponse<MemoryOwner>("session", 1) { it.close() }
            var transfers = 0
            val consumer = lifecycle.launch {
                try {
                    val response = holder.awaitAndTransfer()
                    transfers++
                    try {
                        val mapped = response.memory.mapReadOnly()
                        try { assertEquals(4096, mapped.remaining()) } finally { SharedMemory.unmap(mapped) }
                    } finally { response.close() }
                } catch (_: CancellationException) { }
            }
            dispatcher.drain()
            when (cycle % 5) {
                0 -> { holder.offer(owner); consumer.cancel() }
                1 -> { consumer.cancel(); dispatcher.drain(); holder.offer(owner) }
                2 -> { holder.offer(owner); holder.dispose() }
                3 -> { holder.offer(owner); dispatcher.drain(); holder.dispose() }
                4 -> { holder.dispose(); holder.offer(owner) }
            }
            dispatcher.drain()
            assertEquals(1, owner.closes.get())
            assertEquals(if (cycle % 5 == 3) 1 else 0, transfers)
            lifecycle.cancel()
            if (cycle >= 5 && cycle % 5 == 4) counts.add(fdCount())
        }
        Log.i("VS10Ownership", "shared_memory_50_cycles_fd_samples=$counts")
        assertTrue("SharedMemory FD growth: $counts", counts.last() <= counts.first() + 2)
    }

    @Test fun twentyFiveMessengerPagesAcceptedThenCancelledBeforeTransferDoNotLeak() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "vs10-response-cancel").apply { mkdirs() }
        val document = File(root, "fixture.pdf").apply { writeBytes(ownershipTestPdf(1)) }
        val provider = LocalStorageProvider(root.toPath(), ProviderId("response-cancel-test"))
        val entry = (provider.listChildren((provider.root() as StorageResult.Success).value.ref) as StorageResult.Success).value.single()
        val source = (provider.openPreviewSource(entry) as StorageResult.Success).value
        val request = PreviewRequest(PreviewItem(entry.ref, entry.displayName, "Test", entry.mimeType, entry.sizeBytes), source)
        val store = PdfSnapshotStore(context.noBackupFilesDir)
        val snapshot = (store.stage(request) as PdfStageResult.Ready).snapshot
        val client = AndroidPdfRendererClientFactory(context).create()
        val dispatcher = ManualDispatcher()
        val lifecycle = CoroutineScope(SupervisorJob() + dispatcher)
        val counts = mutableListOf<Int>()
        try {
            assertEquals(1, withTimeout(10_000) { client.open(snapshot) })
            // Warm the native renderer and transport before counting.
            client.renderPage(0)
            counts.add(fdCount())
            repeat(25) { cycle ->
                val accepted = (client as PdfResponseArrivalTestHook).armAcceptedPageResponseForTest()
                var transferred = false
                val consumer = lifecycle.launch {
                    client.renderPage(0)
                    transferred = true
                }
                dispatcher.drain()
                withTimeout(10_000) { accepted.await() }
                consumer.cancel()
                dispatcher.drain()
                assertTrue(consumer.isCompleted)
                assertFalse("Consumer transferred cycle $cycle", transferred)
                if (cycle % 5 == 4) counts.add(fdCount())
            }
            Log.i("VS10Ownership", "messenger_25_cancel_cycles_fd_samples=$counts")
            assertTrue("Messenger PAGE FD growth: $counts", counts.last() <= counts.first() + 3)
        } finally {
            lifecycle.cancel(); dispatcher.drain(); client.close(); snapshot.close(); document.delete(); root.delete()
        }
    }
    @Test fun fiveWorkerDeathsWithAcceptedUntransferredPagesCloseRequestOwnersAndRetry() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "vs10-response-death").apply { mkdirs() }
        val document = File(root, "fixture.pdf").apply { writeBytes(ownershipTestPdf(1)) }
        val provider = LocalStorageProvider(root.toPath(), ProviderId("response-death-test"))
        val entry = (provider.listChildren((provider.root() as StorageResult.Success).value.ref) as StorageResult.Success).value.single()
        val source = (provider.openPreviewSource(entry) as StorageResult.Success).value
        val store = PdfSnapshotStore(context.noBackupFilesDir)
        val request = PreviewRequest(PreviewItem(entry.ref, entry.displayName, "Test", entry.mimeType, entry.sizeBytes), source)
        val counts = mutableListOf<Int>()
        try {
            repeat(5) {
                val snapshot = (store.stage(request) as PdfStageResult.Ready).snapshot
                try {
                    val client = AndroidPdfRendererClientFactory(context).create()
                    val dispatcher = ManualDispatcher()
                    val lifecycle = CoroutineScope(SupervisorJob() + dispatcher)
                    try {
                        withTimeout(10_000) { client.open(snapshot) }
                        val accepted = (client as PdfResponseArrivalTestHook).armAcceptedPageResponseForTest()
                        var failure: PdfRendererFailure? = null
                        val consumer = lifecycle.launch {
                            try { client.renderPage(0); fail("Death must reject the untransferred PAGE") }
                            catch (error: PdfRendererFailure) { failure = error }
                        }
                        dispatcher.drain(); withTimeout(10_000) { accepted.await() }
                        (client as PdfRendererDeathTestHook).killRendererForTest()
                        dispatcher.drain()
                        assertTrue(consumer.isCompleted)
                        assertEquals(PdfRendererFailureKind.WORKER_DIED, failure?.kind)
                    } finally { lifecycle.cancel(); dispatcher.drain(); client.close() }
                    val retry = AndroidPdfRendererClientFactory(context).create()
                    try {
                        assertEquals(1, withTimeout(10_000) { retry.open(snapshot) })
                        assertTrue(retry.renderPage(0).pixels.isNotEmpty())
                    } finally { retry.close() }
                } finally { snapshot.close() }
                counts.add(fdCount())
            }
            Log.i("VS10Ownership", "accepted_page_death_retry_5_cycles_fd_samples=$counts")
            assertTrue("Accepted PAGE death FD growth: $counts", counts.last() <= counts.first() + 3)
        } finally { document.delete(); root.delete() }
    }

}
