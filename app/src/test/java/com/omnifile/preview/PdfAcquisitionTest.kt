package com.omnifile.preview

import com.omnifile.storage.*
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class PdfAcquisitionTest {
    private enum class Phase { RESOLUTION, CLASSIFICATION_OPEN, CLASSIFICATION_READ, STAGING_OPEN, STAGING_READ }
    private data class Ref(override val identityKey: String) : EntryRef { override val providerId = ProviderId("acquisition-test") }
    private val item = PreviewItem(Ref("file"), "fixture.pdf", "Tests", "application/pdf", null)

    private fun stalled(phase: Phase, cancel: Boolean = false) = runBlocking {
        val root = Files.createTempDirectory("pdf-acquisition-test")
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val closes = AtomicInteger(); val rendererOpens = AtomicInteger()
        var scope: ReadAcquisitionScope? = null
        ReadAcquisitionExecutor(timeoutMillis = 10_000).use { runner ->
            val store = PdfSnapshotStore(root.toFile(), acquisitions = runner)
            val controller = PdfPreviewController(store, PdfRendererClientFactory { rendererOpens.incrementAndGet(); error("expired acquisition reached renderer") })
            val engine = PreviewEngine(controller, runner)
            fun pause(at: Phase) {
                if (at == phase) { scope = ReadAcquisitionScope.current()!!; entered.countDown(); release.await() }
            }
            var opens = 0
            val source = PreviewSource(item.identity, "Test source", item.mimeType, null, PreviewCapabilities(true, true, true)) {
                val pass = ++opens
                pause(if (pass == 1) Phase.CLASSIFICATION_OPEN else Phase.STAGING_OPEN)
                var read = false
                StorageResult.Success(object : SequentialReadHandle {
                    override val expectedBytes: Long? = null
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        pause(if (pass == 1) Phase.CLASSIFICATION_READ else Phase.STAGING_READ)
                        if (read) return -1
                        read = true
                        val bytes = "%PDF-1.4\n".toByteArray(); bytes.copyInto(buffer, offset); return bytes.size
                    }
                    override fun close() { closes.incrementAndGet() }
                })
            }
            val result = async(start = CoroutineStart.UNDISPATCHED) {
                engine.resolveAndLoad(item, { pause(Phase.RESOLUTION); StorageResult.Success(source) })
            }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                if (cancel) { result.cancelAndJoin() }
                else {
                    scope!!.expire()
                    assertEquals(PreviewError.AcquisitionTimeout, (result.await().payload as PreviewPayload.Failure).error)
                }
                assertEquals(1, runner.retainedSlots)
                assertEquals(0, rendererOpens.get())
            } finally { release.countDown() }
            assertTrue(runner.awaitIdle())
            assertEquals(0, store.workspace.listFiles().orEmpty().size)
            assertEquals(opens, closes.get())
        }
        root.toFile().deleteRecursively()
        Unit
    }
    @Test fun resolutionTimeoutIncludesInitialProviderCall() = stalled(Phase.RESOLUTION)
    @Test fun classificationOpenTimeoutIsNotStagingTimeout() = stalled(Phase.CLASSIFICATION_OPEN)
    @Test fun classificationReadTimeoutIsNotStagingTimeout() = stalled(Phase.CLASSIFICATION_READ)
    @Test fun stagingReopenUsesSameAcquisition() = stalled(Phase.STAGING_OPEN)
    @Test fun stagingReadExpiryNeverHandsSnapshotToRenderer() = stalled(Phase.STAGING_READ)
    @Test fun cancelDuringResolutionRetainsWorker() = stalled(Phase.RESOLUTION, true)
    @Test fun cancelDuringClassificationOpenDisposesLateHandle() = stalled(Phase.CLASSIFICATION_OPEN, true)
    @Test fun cancelDuringClassificationReadClosesHandle() = stalled(Phase.CLASSIFICATION_READ, true)
    @Test fun cancelDuringStagingOpenDisposesLateHandle() = stalled(Phase.STAGING_OPEN, true)
    @Test fun cancelDuringStagingReadOwnsPartialCandidate() = stalled(Phase.STAGING_READ, true)

    @Test fun classificationProgressConsumesBudgetBeforeStagingOpen() = runBlocking {
        val time = AtomicLong(0); val root = Files.createTempDirectory("pdf-trickle")
        ReadAcquisitionExecutor(time::get, timeoutMillis = 10_000).use { runner ->
            val store = PdfSnapshotStore(root.toFile(), acquisitions = runner)
            var opens = 0
            val source = PreviewSource(item.identity, "Test", null, null, PreviewCapabilities(true, true, true)) {
                opens++
                var offset = 0
                StorageResult.Success(object : SequentialReadHandle {
                    override val expectedBytes: Long? = null
                    override fun read(buffer: ByteArray, start: Int, length: Int): Int {
                        time.addAndGet(2_000_000_000)
                        val bytes = "%PDF-1.4".toByteArray()
                        if (offset == bytes.size) return -1
                        buffer[start] = bytes[offset++]; return 1
                    }
                    override fun close() = Unit
                })
            }
            val result = PreviewEngine(PdfPreviewController(store, PdfRendererClientFactory { error("must not render") }), runner)
                .resolveAndLoad(item, { StorageResult.Success(source) })
            assertEquals(PreviewError.AcquisitionTimeout, (result.payload as PreviewPayload.Failure).error)
            assertTrue(runner.awaitIdle()); assertEquals(1, opens)
            assertTrue(store.workspace.listFiles().orEmpty().isEmpty())
        }
        root.toFile().deleteRecursively()
        Unit
    }
    @Test fun twoPreviewViewModelsShareAdmissionAndThirdGetsBusy() = runBlocking {
        ReadAcquisitionExecutor().use { runner ->
            val entered = CountDownLatch(2); val release = CountDownLatch(1)
            val lifecycle = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
            val engine = PreviewEngine(acquisitions = runner)
            val models = List(3) { PreviewViewModel(engine, kotlinx.coroutines.Dispatchers.Default, lifecycle) }
            val resolver: suspend () -> StorageResult<PreviewSource> = { entered.countDown(); release.await(); StorageResult.Failure(StorageError.NotFound) }
            try {
                models[0].open(item, resolver); models[1].open(item, resolver)
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                models[2].open(item, resolver)
                val state = kotlinx.coroutines.withTimeout(5_000) {
                    models[2].state.first { it is PreviewUiState.Error }
                } as PreviewUiState.Error
                assertEquals(PreviewError.AcquisitionBusy, state.error)
                models.forEach { it.close() }
                assertEquals(2, runner.retainedSlots)
            } finally { release.countDown() }
            assertTrue(runner.awaitIdle())
            lifecycle.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
        }
    }

    @Test fun exceptionAfterSnapshotAdoptionKeepsCandidateCleanupOwned() = runBlocking {
        val root = Files.createTempDirectory("pdf-adoption-exception")
        ReadAcquisitionExecutor().use { runner ->
            val store = PdfSnapshotStore(root.toFile(), acquisitions = runner)
            val source = PreviewSource(item.identity, "Test", null, null, PreviewCapabilities(true, true, true)) {
                var done = false
                StorageResult.Success(object : SequentialReadHandle {
                    override val expectedBytes: Long? = null
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        if (done) return -1
                        done = true; val bytes = "%PDF-1.4".toByteArray(); bytes.copyInto(buffer, offset); return bytes.size
                    }
                    override fun close() = Unit
                })
            }
            try {
                runner.acquire<Unit> { store.stage(PreviewRequest(item, source)); throw java.io.IOException("after adoption") }
                fail("Expected post-adoption failure")
            } catch (_: java.io.IOException) { }
            assertTrue(runner.awaitIdle()); assertTrue(store.workspace.listFiles().orEmpty().isEmpty())
        }
        root.toFile().deleteRecursively(); Unit
    }

    @Test fun grantLossDuringAcquisitionKeepsPermissionFailure() = runBlocking {
        ReadAcquisitionExecutor().use { runner ->
            val source = PreviewSource(item.identity, "Test", null, null, PreviewCapabilities(true, true, true)) {
                StorageResult.Failure(StorageError.PermissionDenied)
            }
            val result = PreviewEngine(acquisitions = runner).resolveAndLoad(item, { StorageResult.Success(source) })
            assertEquals(PreviewError.PermissionOrGrantMissing, (result.payload as PreviewPayload.Failure).error)
            assertTrue(runner.awaitIdle())
        }
    }
    @Test fun lateResolutionExceptionWithUndispatchedTimerMapsToAcquisitionTimeoutAndRetry() = runBlocking {
        val time = AtomicLong(0)
        ReadAcquisitionExecutor(time::get, timeoutMillis = 10_000).use { runner ->
            val lifecycle = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
            val model = PreviewViewModel(PreviewEngine(acquisitions = runner), kotlinx.coroutines.Dispatchers.Default, lifecycle)
            val attempts = AtomicInteger()
            try {
                model.open(item) {
                    if (attempts.incrementAndGet() == 1) {
                        time.set(10_000_000_000)
                        throw SecurityException("provider permission failure after absolute deadline")
                    }
                    StorageResult.Failure(StorageError.PermissionDenied)
                }
                val expired = kotlinx.coroutines.withTimeout(5_000) {
                    model.state.first { it is PreviewUiState.Error }
                } as PreviewUiState.Error
                assertEquals(PreviewError.AcquisitionTimeout, expired.error)
                assertTrue(runner.awaitIdle())
                model.retry()
                val retried = kotlinx.coroutines.withTimeout(5_000) {
                    model.state.first { it is PreviewUiState.Error && it.error == PreviewError.PermissionOrGrantMissing }
                } as PreviewUiState.Error
                assertEquals(PreviewError.PermissionOrGrantMissing, retried.error)
                assertEquals(2, attempts.get())
                assertTrue(runner.awaitIdle())
            } finally {
                model.close()
                lifecycle.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            }
        }
    }

}
