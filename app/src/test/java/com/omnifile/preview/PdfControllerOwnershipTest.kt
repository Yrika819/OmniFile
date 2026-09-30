package com.omnifile.preview

import com.omnifile.storage.*
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PdfControllerOwnershipTest {
    @Test fun replacementWaitsForExactPhysicalCleanupWithoutBlockingLogicalInvalidation() = runBlocking {
        fixture { controller, request, created ->
            val first = (controller.open(request) as PdfOpenResult.Ready).payload.session
            val prior = created.single(); prior.shutdown = CompletableDeferred()
            first.invalidate()
            prior.aborted.await()
            val next = async(start = CoroutineStart.UNDISPATCHED) { controller.open(request) }
            assertEquals(1, created.size); assertFalse(next.isCompleted)
            prior.shutdown!!.complete(Unit)
            val second = (next.await() as PdfOpenResult.Ready).payload.session
            first.close(); assertEquals(2, created.size)
            assertNotNull(second.renderPage(1)); second.close()
            assertEquals(1, prior.disposals.get())
        }
    }
    @Test fun cancellationWaitingForPriorCleanupDisposesNewSnapshotAndNeverOpensClient() = runBlocking {
        fixture { controller, request, created ->
            val first = (controller.open(request) as PdfOpenResult.Ready).payload.session
            val prior = created.single(); prior.shutdown = CompletableDeferred(); first.invalidate(); prior.aborted.await()
            val next = async(start = CoroutineStart.UNDISPATCHED) { controller.open(request) }
            withTimeout(5_000) { while (controller.openingSnapshotCount != 1) yield() }
            next.cancelAndJoin(); assertEquals(1, created.size)
            assertEquals(1, controller.snapshots.workspace.listFiles().orEmpty().size)
            prior.shutdown!!.complete(Unit); first.close()
            assertEquals(0, controller.snapshots.workspace.listFiles().orEmpty().size)
        }
    }
    @Test fun cancellationDuringRendererOpenLeavesIndependentExactCleanupOwner() = runBlocking {
        fixture(openGate = CompletableDeferred()) { controller, request, created ->
            val opening = async { controller.open(request) }
            withTimeout(5_000) { while (created.isEmpty()) yield(); created.single().opened.await() }
            val client = created.single(); client.shutdown = CompletableDeferred()
            opening.cancelAndJoin(); client.aborted.await()
            assertEquals(1, controller.snapshots.workspace.listFiles().orEmpty().size)
            client.openGate!!.complete(Unit); client.shutdown!!.complete(Unit)
            withTimeout(5_000) { while (controller.snapshots.workspace.listFiles().orEmpty().isNotEmpty()) yield() }
            assertEquals(1, client.disposals.get())
        }
    }
    @Test fun uncertainCleanupPreventsUnsafeControllerReuse() = runBlocking {
        fixture { controller, request, created ->
            val first = (controller.open(request) as PdfOpenResult.Ready).payload.session
            created.single().shutdownFailure = true; first.invalidate()
            try { first.close(); fail("Expected explicit cleanup failure") } catch (_: java.io.IOException) { }
            assertTrue(controller.open(request) is PdfOpenResult.Failure)
            assertEquals(1, created.size)
        }
    }
    @Test fun blockedOldCleanupTimesOutReplacementWithoutOpeningAnotherNativeOwner() = runBlocking {
        fixture(timeoutMillis = 100) { controller, request, created ->
            val first = (controller.open(request) as PdfOpenResult.Ready).payload.session
            val prior = created.single(); prior.shutdown = CompletableDeferred(); first.invalidate(); prior.aborted.await()
            val replacement = controller.open(request) as PdfOpenResult.Failure
            assertEquals(PreviewError.RendererTimeout, replacement.error)
            assertEquals(1, created.size)
            assertEquals(1, controller.snapshots.workspace.listFiles().orEmpty().size)
            prior.shutdown!!.complete(Unit); first.close()
            assertEquals(0, controller.snapshots.workspace.listFiles().orEmpty().size)
        }
    }
    @Test fun directControllerOpenCannotQueueOutsideSaturatedReadAdmission() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(2)
        val release = java.util.concurrent.CountDownLatch(1)
        ReadAcquisitionExecutor().use { runner ->
            val retained = List(2) { async(Dispatchers.Default) { runner.acquire { entered.countDown(); release.await() } } }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            try {
                fixture(runner = runner) { controller, request, created ->
                    val busy = controller.open(request) as PdfOpenResult.Failure
                    assertEquals(PreviewError.AcquisitionBusy, busy.error); assertTrue(created.isEmpty())
                }
            } finally { release.countDown(); retained.forEach { it.await() }; assertTrue(runner.awaitIdle()) }
        }
    }
    @Test fun directControllerReservationWaitingConsumesTheAcquisitionDeadline() = runBlocking {
        ReadAcquisitionExecutor(timeoutMillis = 100).use { runner ->
            fixture(runner = runner) { controller, request, created ->
                val reservation = controller.reserve()
                try {
                    val expired = controller.open(request) as PdfOpenResult.Failure
                    assertEquals(PreviewError.AcquisitionTimeout, expired.error); assertTrue(created.isEmpty())
                    assertTrue(runner.awaitIdle())
                } finally { reservation.close() }
            }
        }
    }
    private suspend fun fixture(openGate: CompletableDeferred<Unit>? = null, timeoutMillis: Long = PreviewLimits.PDF_RENDER_TIMEOUT_MILLIS, runner: ReadAcquisitionExecutor = ReadAcquisitionExecutor.appWide, block: suspend (PdfPreviewController, PreviewRequest, MutableList<Client>) -> Unit) {
        val root = Files.createTempDirectory("pdf-controller-owner")
        val created = java.util.Collections.synchronizedList(mutableListOf<Client>())
        val ref = object : EntryRef { override val providerId = ProviderId("controller-test"); override val identityKey = "pdf" }
        val source = PreviewSource(ref, "Test", "application/pdf", 9, PreviewCapabilities(true, true, true)) {
            val input = "%PDF-1.4\n".byteInputStream()
            StorageResult.Success(object : SequentialReadHandle {
                override val expectedBytes = 9L
                override fun read(buffer: ByteArray, offset: Int, length: Int) = input.read(buffer, offset, length)
                override fun close() = input.close()
            })
        }
        val controller = PdfPreviewController(PdfSnapshotStore(root.toFile(), acquisitions = runner), PdfRendererClientFactory { Client(openGate).also { created += it } }, renderTimeoutMillis = timeoutMillis)
        try { block(controller, PreviewRequest(PreviewItem(ref, "a.pdf", "Test", source.mimeType, 9), source), created) }
        finally { root.toFile().deleteRecursively() }
    }
    private class Client(val openGate: CompletableDeferred<Unit>?) : PdfRendererClient {
        val opened = CompletableDeferred<Unit>(); val aborted = CompletableDeferred<Unit>()
        val disposals = AtomicInteger(); var shutdown: CompletableDeferred<Unit>? = null; var shutdownFailure = false
        override suspend fun open(snapshot: PdfSnapshot): Int { opened.complete(Unit); openGate?.await(); return 2 }
        override suspend fun renderPage(pageIndex: Int) = PdfRenderedPage(2, 2, ByteArray(16))
        override fun abort() { disposals.incrementAndGet(); aborted.complete(Unit) }
        override suspend fun close() { abort() }
        override suspend fun awaitShutdown() { shutdown?.await(); if (shutdownFailure) throw java.io.IOException("uncertain cleanup") }
    }
}
