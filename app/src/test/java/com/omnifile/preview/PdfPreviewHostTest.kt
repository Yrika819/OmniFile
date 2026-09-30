package com.omnifile.preview

import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.storage.SequentialReadHandle
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PdfPreviewHostTest {
    @Test
    fun contentSignatureWinsAndPdfNameAloneDoesNot() {
        assertEquals(
            PreviewContentType.PDF,
            PreviewContentClassifier.classify("%PDF-1.7".toByteArray(), "text/plain", "payload.txt"),
        )
        assertEquals(
            PreviewContentType.PDF,
            PreviewContentClassifier.classify(ByteArray(120) { ' '.code.toByte() } + "%PDF-1.4".toByteArray(), null, "file.bin"),
        )
        assertEquals(
            PreviewContentType.TEXT,
            PreviewContentClassifier.classify("ordinary text".toByteArray(), "application/pdf", "foo.pdf"),
        )
    }

    @Test
    fun exactSixteenMiBSnapshotBecomesReadyAndIsRemovedWhenReleased() = runBlocking {
        withWorkspace { root ->
            val bytes = pdfBytes(PreviewLimits.MAX_PDF_BYTES)
            val (request, stats) = request(bytes, advertisedSize = bytes.size.toLong())
            val store = PdfSnapshotStore(root.toFile(), Dispatchers.Unconfined)
            val staged = store.stage(request) as PdfStageResult.Ready

            assertEquals(PreviewLimits.MAX_PDF_BYTES.toLong(), staged.snapshot.sizeBytes)
            assertTrue(staged.snapshot.file.name.endsWith(".candidate"))
            assertTrue(staged.snapshot.file.name.matches(Regex("[0-9a-fA-F-]{36}\\.candidate")))
            assertEquals(1, stats.opens.get())
            staged.snapshot.close()
            assertEquals(0, store.workspace.listFiles().orEmpty().size)
        }
    }

    @Test
    fun knownOversizeSourceIsRejectedBeforeOpeningAndUnknownSizeReadsOnlyOneExtraByte() = runBlocking {
        withWorkspace { root ->
            val knownStore = PdfSnapshotStore(root.toFile(), Dispatchers.Unconfined)
            val tooLargeKnown = pdfBytes(PreviewLimits.MAX_PDF_BYTES + 1)
            val (knownRequest, knownStats) = request(tooLargeKnown, advertisedSize = tooLargeKnown.size.toLong())
            val knownResult = knownStore.stage(knownRequest) as PdfStageResult.Failure
            assertEquals(PreviewError.PdfInputTooLarge, knownResult.error)
            assertEquals(0, knownStats.opens.get())

            val unknownStore = PdfSnapshotStore(root.toFile(), Dispatchers.Unconfined)
            val (unknownRequest, unknownStats) = request(
                tooLargeKnown,
                advertisedSize = null,
                handleExpectedBytes = null,
            )
            val unknownResult = unknownStore.stage(unknownRequest) as PdfStageResult.Failure
            assertEquals(PreviewError.PdfInputTooLarge, unknownResult.error)
            assertEquals(PreviewLimits.MAX_PDF_BYTES + 1, unknownStats.bytesRead.get())
            assertEquals(0, unknownStore.workspace.listFiles().orEmpty().size)
        }
    }

    @Test
    fun noProgressAndInterruptedReadsNeverPromotePartialSnapshots() = runBlocking {
        withWorkspace { root ->
            val store = PdfSnapshotStore(root.toFile(), Dispatchers.Unconfined)
            val item = item("no-progress")
            val readCount = AtomicInteger()
            val stalledSource = source(item, null, null) {
                StorageResult.Success(object : SequentialReadHandle {
                    override val expectedBytes: Long? = null
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        readCount.incrementAndGet()
                        return 0
                    }
                    override fun close() = Unit
                })
            }
            val stalled = store.stage(PreviewRequest(item, stalledSource)) as PdfStageResult.Failure
            assertEquals(PreviewError.ProviderUnavailable, stalled.error)
            assertEquals(PreviewLimits.MAX_CONSECUTIVE_NO_PROGRESS_READS + 1, readCount.get())

            val interrupted = item("interrupted")
            val partialReads = AtomicInteger()
            val interruptedSource = source(interrupted, null, null) {
                StorageResult.Success(object : SequentialReadHandle {
                    override val expectedBytes: Long? = null
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        if (partialReads.incrementAndGet() == 1) {
                            buffer[offset] = '%'.code.toByte()
                            return 1
                        }
                        throw IOException("private provider detail")
                    }
                    override fun close() = Unit
                })
            }
            val failed = store.stage(PreviewRequest(interrupted, interruptedSource)) as PdfStageResult.Failure
            assertEquals(PreviewError.ProviderUnavailable, failed.error)
            assertEquals(0, store.workspace.listFiles().orEmpty().size)
        }
    }

    @Test
    fun absoluteDeadlineClosesBlockedSourceAndLeavesNoAdoptedArtifact() = runBlocking {
        withWorkspace { root ->
            val acquisitions = com.omnifile.storage.ReadAcquisitionExecutor(timeoutMillis = 40)
            val store = PdfSnapshotStore(root.toFile(), Dispatchers.IO, acquisitions = acquisitions)
            val item = item("stalled-provider")
            val readStarted = CountDownLatch(1)
            val closed = CountDownLatch(1)
            val source = source(item, null, null) {
                StorageResult.Success(object : SequentialReadHandle {
                    override val expectedBytes: Long? = null
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        readStarted.countDown()
                        closed.await(5, TimeUnit.SECONDS)
                        throw IOException("closed")
                    }
                    override fun close() { closed.countDown() }
                })
            }
            val result = store.stage(PreviewRequest(item, source)) as PdfStageResult.Failure
            assertEquals(PreviewError.AcquisitionTimeout, result.error)
            assertTrue(readStarted.await(1, TimeUnit.SECONDS))
            assertTrue(acquisitions.awaitIdle())
            assertEquals(0L, closed.count)
            assertEquals(0, store.workspace.listFiles().orEmpty().size)
        }
    }

    @Test
    fun abandonedArtifactsAreReconciledOnlyInsideOwnedWorkspace() = runBlocking {
        withWorkspace { root ->
            val store = PdfSnapshotStore(root.toFile(), Dispatchers.Unconfined)
            val id = UUID.randomUUID().toString()
            val partial = store.workspace.resolve("$id.partial")
            val ready = store.workspace.resolve("${UUID.randomUUID()}.ready")
            Files.write(partial.toPath(), byteArrayOf(1))
            Files.write(ready.toPath(), byteArrayOf(2))
            val outside = root.resolve("outside.ready")
            Files.write(outside, byteArrayOf(3))
            val childDirectory = store.workspace.resolve("${UUID.randomUUID()}.ready")
            Files.createDirectory(childDirectory.toPath())

            assertEquals(2, store.reconcileAbandoned())
            assertFalse(Files.exists(partial.toPath()))
            assertFalse(Files.exists(ready.toPath()))
            assertTrue(Files.exists(outside))
            assertTrue(Files.isDirectory(childDirectory.toPath()))
        }
    }

    @Test
    fun workspaceCleanupDoesNotFollowSymlinksOutsideTheOwnedTree() = runBlocking {
        withWorkspace { root ->
            val store = PdfSnapshotStore(root.toFile(), Dispatchers.Unconfined)
            val outside = root.resolve("untouched")
            Files.write(outside, byteArrayOf(8))
            val link = store.workspace.resolve("${UUID.randomUUID()}.ready")
            runCatching { Files.createSymbolicLink(link.toPath(), outside) }.getOrElse { return@withWorkspace }

            assertEquals(0, store.reconcileAbandoned())
            assertTrue(Files.exists(outside))
        }
    }

    @Test
    fun reconciliationRefusesSymlinkedWorkspaceInsteadOfTraversingItsTarget() = runBlocking {
        val noBackup = Files.createTempDirectory("omnifile-pdf-no-backup")
        val outside = Files.createTempDirectory("omnifile-pdf-outside")
        try {
            val outsideArtifact = outside.resolve("${UUID.randomUUID()}.ready")
            Files.write(outsideArtifact, byteArrayOf(1))
            Files.createSymbolicLink(noBackup.resolve("preview"), outside)
            val store = PdfSnapshotStore(noBackup.toFile(), Dispatchers.Unconfined)
            val (request, _) = request(pdfBytes(32))
            val failure = store.stage(request) as PdfStageResult.Failure
            assertEquals(PreviewError.StagingFailure, failure.error)
            assertTrue(Files.exists(outsideArtifact))
        } finally {
            noBackup.toFile().deleteRecursively()
            outside.toFile().deleteRecursively()
        }
    }

    @Test
    fun stagingRejectsSourceIdentityMismatchWithoutOpeningIt() = runBlocking {
        withWorkspace { root ->
            val item = item("expected")
            val stats = SourceStats()
            val mismatched = source(item, pdfBytes(20), 20, identity = TestRef("another"), stats = stats)
            val result = PdfSnapshotStore(root.toFile(), Dispatchers.Unconfined).stage(item, mismatched) as PdfStageResult.Failure
            assertEquals(PreviewError.SourceVanished, result.error)
            assertEquals(0, stats.opens.get())
        }
    }

    @Test
    fun pageCountAndRenderBoundsAreEnforcedAtTheRendererSeam() = runBlocking {
        withWorkspace { root ->
            val tooMany = fakeController(root, pages = PreviewLimits.MAX_PDF_PAGES + 1)
            val tooManyResult = tooMany.first.open(tooMany.second) as PdfOpenResult.Failure
            assertEquals(PreviewError.PdfPageCountLimit, tooManyResult.error)
            assertEquals(1, tooMany.third.created.single().aborted.get())
            assertEquals(0, tooMany.first.snapshots.workspace.listFiles().orEmpty().size)

            val bounds = PdfPageGeometry.bounded(10_000, 10_000)!!
            assertTrue(bounds.width <= PreviewLimits.MAX_PDF_PAGE_SIDE)
            assertTrue(bounds.height <= PreviewLimits.MAX_PDF_PAGE_SIDE)
            assertTrue(bounds.width.toLong() * bounds.height <= PreviewLimits.MAX_PDF_PAGE_PIXELS)
            assertEquals(null, PdfPageGeometry.bounded(0, 20))
            try {
                PdfRenderedPage(1600, 1600, ByteArray(1600 * 1600 * 4))
                fail("Expected unsafe pixel area to be rejected")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun invalidPageIndexAndRendererTimeoutAreTypedAndReleaseSnapshots() = runBlocking {
        withWorkspace { root ->
            val normal = fakeController(root, pages = 2)
            val ready = normal.first.open(normal.second) as PdfOpenResult.Ready
            try {
                ready.payload.session.renderPage(-1)
                fail("Expected invalid page rejection")
            } catch (failure: PdfRendererFailure) {
                assertEquals(PdfRendererFailureKind.INVALID_PAGE, failure.kind)
            }
            ready.payload.session.close()

            val timeoutRoot = Files.createDirectory(root.resolve("timeout"))
            val timeoutClient = FakeRendererClient(openWait = CompletableDeferred())
            val timeoutController = PdfPreviewController(
                PdfSnapshotStore(timeoutRoot.toFile(), Dispatchers.Unconfined),
                PdfRendererClientFactory { timeoutClient },
                renderTimeoutMillis = 30,
            )
            val (request, _) = request(pdfBytes(80))
            val timedOut = timeoutController.open(request) as PdfOpenResult.Failure
            assertEquals(PreviewError.RendererTimeout, timedOut.error)
            assertEquals(1, timeoutClient.aborted.get())
            assertEquals(0, timeoutController.snapshots.workspace.listFiles().orEmpty().size)

            val pageTimeoutRoot = Files.createDirectory(root.resolve("page-timeout"))
            val pageTimeoutClient = FakeRendererClient(renderWait = CompletableDeferred())
            val pageTimeoutController = PdfPreviewController(
                PdfSnapshotStore(pageTimeoutRoot.toFile(), Dispatchers.Unconfined),
                PdfRendererClientFactory { pageTimeoutClient },
                renderTimeoutMillis = 30,
            )
            val pageTimedOut = pageTimeoutController.open(request) as PdfOpenResult.Failure
            assertEquals(PreviewError.RendererTimeout, pageTimedOut.error)
            assertEquals(1, pageTimeoutClient.aborted.get())
            assertEquals(0, pageTimeoutController.snapshots.workspace.listFiles().orEmpty().size)
        }
    }

    @Test
    fun workerDeathAndFailureMappingRemainSanitized() = runBlocking {
        withWorkspace { root ->
            val worker = FakeRendererClient(openFailure = PdfRendererFailure(PdfRendererFailureKind.WORKER_DIED))
            val controller = PdfPreviewController(PdfSnapshotStore(root.toFile(), Dispatchers.Unconfined), PdfRendererClientFactory { worker })
            val (request, _) = request(pdfBytes(100))
            val result = controller.open(request) as PdfOpenResult.Failure
            assertEquals(PreviewError.RendererFailure, result.error)
            assertEquals(1, worker.aborted.get())
            assertEquals(0, controller.snapshots.workspace.listFiles().orEmpty().size)
            assertEquals(PreviewError.EncryptedOrUnsupported, PdfRendererFailure(PdfRendererFailureKind.ENCRYPTED_OR_UNSUPPORTED).toPreviewError())
            assertEquals(PreviewError.CorruptOrMalformed, PdfRendererFailure(PdfRendererFailureKind.MALFORMED).toPreviewError())
        }
    }

    @Test
    fun previewRetryStagesANewSnapshotAndPageNavigationKeepsTruthfulIndex() = runBlocking {
        withWorkspace { root ->
            val created = mutableListOf<FakeRendererClient>()
            val store = PdfSnapshotStore(root.toFile(), Dispatchers.Unconfined)
            val controller = PdfPreviewController(store, PdfRendererClientFactory {
                FakeRendererClient(pages = 2).also { created += it }
            })
            val engine = PreviewEngine(controller)
            val item = item("reader.pdf")
            val opens = AtomicInteger()
            val source = PreviewSource(
                item.identity, "Local storage", null, 32, PreviewCapabilities(true, true, canStagePdf = true),
            ) {
                opens.incrementAndGet()
                handle(pdfBytes(32), 32)
            }
            val lifecycle = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val viewModel = PreviewViewModel(engine, Dispatchers.Unconfined, lifecycle)
            val resolver: suspend () -> StorageResult<PreviewSource> = { StorageResult.Success(source) }

            viewModel.open(item, resolver)
            var state = withTimeout(5_000) { viewModel.state.first { it is PreviewUiState.Ready } } as PreviewUiState.Ready
            assertEquals(0, (state.payload as PreviewPayload.PdfPage).pageIndex)
            viewModel.nextPage()
            state = withTimeout(5_000) {
                viewModel.state.first { value ->
                    value is PreviewUiState.Ready && (value.payload as? PreviewPayload.PdfPage)?.pageIndex == 1
                }
            } as PreviewUiState.Ready
            assertEquals(1, (state.payload as PreviewPayload.PdfPage).pageIndex)
            assertEquals(2, opens.get()) // classification and fresh staging pass

            val previousSnapshotId = created.first().snapshotId
            viewModel.retry()
            state = withTimeout(5_000) {
                viewModel.state.first { value ->
                    value is PreviewUiState.Ready && (value.payload as? PreviewPayload.PdfPage)?.pageIndex == 1
                }
            } as PreviewUiState.Ready
            assertEquals(1, (state.payload as PreviewPayload.PdfPage).pageIndex)
            assertNotEquals(previousSnapshotId, created.last().snapshotId)
            assertEquals(4, opens.get())
            assertEquals(1, created.first().aborted.get())
            viewModel.close()
            assertEquals(0, store.workspace.listFiles().orEmpty().size)
            lifecycle.cancel()
        }
    }

    @Test
    fun staleDocumentAndPageResultsCannotPublishAfterReplacement() = runBlocking {
        val releaseDocumentA = CompletableDeferred<Unit>()
        val documentAStarted = CompletableDeferred<Unit>()
        val sessionA = FakeDocumentSession(1)
        val engine = object : ResolvingPdfFakePreviewEngine() {
            override suspend fun load(request: PreviewRequest): PreviewPayload {
                if (request.item.displayName == "A.pdf") {
                    documentAStarted.complete(Unit)
                    withContext(NonCancellable) { releaseDocumentA.await() }
                    return PreviewPayload.PdfPage(page(), 0, 1, sessionA)
                }
                return PreviewPayload.Text("B", false, 1)
            }
        }
        val lifecycle = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = PreviewViewModel(engine, Dispatchers.Default, lifecycle)
        val itemA = item("A.pdf")
        val itemB = item("B.txt")
        viewModel.open(itemA) { StorageResult.Success(source(itemA, pdfBytes(24), 24)) }
        withTimeout(5_000) { documentAStarted.await() }
        viewModel.open(itemB) { StorageResult.Success(source(itemB, "B".toByteArray(), 1, pdf = false)) }
        withTimeout(5_000) { viewModel.state.first { it is PreviewUiState.Ready && it.item == itemB } }
        releaseDocumentA.complete(Unit)
        withTimeout(5_000) { sessionA.invalidatedSignal.await() }
        assertEquals("B", ((viewModel.state.value as PreviewUiState.Ready).payload as PreviewPayload.Text).content)
        lifecycle.cancel()

        val renderStarted = CompletableDeferred<Unit>()
        val releaseRender = CompletableDeferred<Unit>()
        val stalePageSession = FakeDocumentSession(
            2,
            render = {
                renderStarted.complete(Unit)
                withContext(NonCancellable) { releaseRender.await() }
                page()
            },
        )
        val pageEngine = object : ResolvingPdfFakePreviewEngine() {
            override suspend fun load(request: PreviewRequest): PreviewPayload =
                PreviewPayload.PdfPage(page(), 0, 2, stalePageSession)
        }
        val secondLifecycle = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val secondViewModel = PreviewViewModel(pageEngine, Dispatchers.Default, secondLifecycle)
        secondViewModel.open(itemA) { StorageResult.Success(source(itemA, pdfBytes(24), 24)) }
        withTimeout(5_000) { secondViewModel.state.first { it is PreviewUiState.Ready } }
        secondViewModel.nextPage()
        withTimeout(5_000) { renderStarted.await() }
        secondViewModel.open(itemB) { StorageResult.Success(source(itemB, "B".toByteArray(), 1, pdf = false)) }
        withTimeout(5_000) { secondViewModel.state.first { it is PreviewUiState.Ready && it.item == itemB } }
        releaseRender.complete(Unit)
        withTimeout(5_000) { stalePageSession.invalidatedSignal.await() }
        assertEquals(itemB, (secondViewModel.state.value as PreviewUiState.Ready).item)
        secondLifecycle.cancel()
    }

    @Test
    fun rapidPageRequestsRenderSeriallyAndOnlyPublishTheLatestPage() = runBlocking {
        val firstPageStarted = CompletableDeferred<Unit>()
        val releaseFirstPage = CompletableDeferred<Unit>()
        val renderIndices = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val session = FakeDocumentSession(3) { pageIndex ->
            renderIndices += pageIndex
            if (pageIndex == 1) {
                firstPageStarted.complete(Unit)
                withContext(NonCancellable) { releaseFirstPage.await() }
            }
            page()
        }
        val engine = object : ResolvingPdfFakePreviewEngine() {
            override suspend fun load(request: PreviewRequest): PreviewPayload =
                PreviewPayload.PdfPage(page(), 0, 3, session)
        }
        val lifecycle = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = PreviewViewModel(engine, Dispatchers.Default, lifecycle)
        val item = item("rapid.pdf")
        viewModel.open(item) { StorageResult.Success(source(item, pdfBytes(24), 24)) }
        withTimeout(5_000) { viewModel.state.first { it is PreviewUiState.Ready } }

        viewModel.nextPage()
        withTimeout(5_000) { firstPageStarted.await() }
        viewModel.nextPage()
        releaseFirstPage.complete(Unit)
        val ready = withTimeout(5_000) {
            viewModel.state.first { value ->
                value is PreviewUiState.Ready && (value.payload as? PreviewPayload.PdfPage)?.pageIndex == 2
            }
        } as PreviewUiState.Ready

        assertEquals(2, (ready.payload as PreviewPayload.PdfPage).pageIndex)
        assertEquals(listOf(1, 2), renderIndices.toList())
        viewModel.close()
        lifecycle.cancel()
    }

    private suspend fun fakeController(root: java.nio.file.Path, pages: Int): Triple<PdfPreviewController, PreviewRequest, FakeFactory> {
        val item = item("fake.pdf")
        val (request, _) = request(pdfBytes(40), item = item)
        val factory = FakeFactory(pages)
        return Triple(
            PdfPreviewController(PdfSnapshotStore(root.toFile(), Dispatchers.Unconfined), factory),
            request.copy(item = item, source = request.source),
            factory,
        )
    }

    private fun request(
        bytes: ByteArray,
        advertisedSize: Long? = bytes.size.toLong(),
        handleExpectedBytes: Long? = advertisedSize,
        item: PreviewItem = item("fixture.pdf"),
        sourceIdentity: EntryRef = item.identity,
    ): Pair<PreviewRequest, SourceStats> {
        val stats = SourceStats()
        val source = source(item, bytes, advertisedSize, sourceIdentity, stats, handleExpectedBytes)
        return PreviewRequest(item, source) to stats
    }

    private fun source(
        item: PreviewItem,
        bytes: ByteArray?,
        advertisedSize: Long?,
        identity: EntryRef = item.identity,
        stats: SourceStats = SourceStats(),
        handleExpectedBytes: Long? = advertisedSize,
        pdf: Boolean = true,
        openHandle: (() -> StorageResult<SequentialReadHandle>)? = null,
    ): PreviewSource = PreviewSource(
        identity = identity,
        sourceLabel = "Fixture",
        mimeType = null,
        sizeBytes = advertisedSize,
        capabilities = PreviewCapabilities(true, true, canStagePdf = pdf),
    ) {
        stats.opens.incrementAndGet()
        if (openHandle != null) {
            openHandle()
        } else if (bytes == null) {
            StorageResult.Success(object : SequentialReadHandle {
                override val expectedBytes: Long? = handleExpectedBytes
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int = -1
                override fun close() = Unit
            })
        } else {
            handle(bytes, handleExpectedBytes, stats)
        }
    }

    private fun handle(bytes: ByteArray, expected: Long?, stats: SourceStats = SourceStats()): StorageResult<SequentialReadHandle> {
        var offset = 0
        return StorageResult.Success(object : SequentialReadHandle {
            override val expectedBytes: Long? = expected
            override fun read(buffer: ByteArray, start: Int, length: Int): Int {
                if (offset >= bytes.size) return -1
                val count = minOf(length, bytes.size - offset)
                bytes.copyInto(buffer, start, offset, offset + count)
                offset += count
                stats.bytesRead.addAndGet(count)
                return count
            }
            override fun close() = Unit
        })
    }

    private fun pdfBytes(size: Int): ByteArray = ByteArray(size) { 'x'.code.toByte() }.apply {
        "%PDF-1.4\n".toByteArray().copyInto(this, 0, 0, minOf(size, 9))
    }

    private fun item(name: String) = PreviewItem(TestRef(name), name, "Tests", null, null)
    private suspend fun withWorkspace(block: suspend (java.nio.file.Path) -> Unit) {
        val root = Files.createTempDirectory("omnifile-pdf-host")
        try {
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private data class TestRef(override val identityKey: String) : EntryRef {
        override val providerId = ProviderId("pdf-test")
    }

    private data class SourceStats(val opens: AtomicInteger = AtomicInteger(), val bytesRead: AtomicInteger = AtomicInteger())

    private class FakeFactory(private val pages: Int) : PdfRendererClientFactory {
        val created = mutableListOf<FakeRendererClient>()
        override fun create(): PdfRendererClient = FakeRendererClient(pages).also { created += it }
    }

    private class FakeRendererClient(
        private val pages: Int = 1,
        private val openWait: CompletableDeferred<Unit>? = null,
        private val openFailure: PdfRendererFailure? = null,
        private val renderWait: CompletableDeferred<Unit>? = null,
    ) : PdfRendererClient {
        val closed = AtomicInteger()
        val aborted = AtomicInteger()
        var snapshotId: String? = null
        override suspend fun open(snapshot: PdfSnapshot): Int {
            snapshotId = snapshot.id
            openFailure?.let { throw it }
            openWait?.await()
            return pages
        }
        override suspend fun renderPage(pageIndex: Int): PdfRenderedPage {
            if (pageIndex !in 0 until pages) throw PdfRendererFailure(PdfRendererFailureKind.INVALID_PAGE)
            renderWait?.await()
            return page()
        }
        override suspend fun close() { closed.incrementAndGet() }
        override fun abort() { aborted.incrementAndGet() }
    }

    private class FakeDocumentSession(
        override val pageCount: Int,
        private val render: suspend (Int) -> PdfRenderedPage = { page() },
    ) : PdfDocumentSession {
        val invalidatedSignal = CompletableDeferred<Unit>()
        override suspend fun renderPage(pageIndex: Int): PdfRenderedPage {
            if (pageIndex !in 0 until pageCount) throw PdfRendererFailure(PdfRendererFailureKind.INVALID_PAGE)
            return render(pageIndex)
        }
        override suspend fun close() = Unit
        override fun invalidate() { invalidatedSignal.complete(Unit) }
    }

    private companion object {
        fun page() = PdfRenderedPage(2, 2, ByteArray(16))
    }
}


private abstract class ResolvingPdfFakePreviewEngine : PreviewEngine() {
    override suspend fun resolveAndLoad(item: PreviewItem, resolveSource: suspend () -> StorageResult<PreviewSource>, startingPage: Int): ResolvedPreview {
        val source = (resolveSource() as StorageResult.Success).value
        return ResolvedPreview(source.sourceLabel, load(PreviewRequest(item, source, startingPage)))
    }
}
