package com.omnifile.preview

import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifile.OmniFileApplication
import com.omnifile.storage.EntryRef
import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.ProviderId
import com.omnifile.storage.SafStorageProvider
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageResult
import com.omnifile.storage.TestDocumentsProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@com.omnifile.preview.PostVs10HardeningTarget
@RunWith(AndroidJUnit4::class)
class PdfRendererInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val engine by lazy { (context.applicationContext as OmniFileApplication).container.previewEngine }
    private lateinit var localRoot: java.nio.file.Path

    @Before
    fun setUp() {
        TestDocumentsProvider.reset()
        val testProvider = TestDocumentsProvider()
        testProvider.attachInfo(instrumentation.context, ProviderInfo().apply {
            authority = TestDocumentsProvider.AUTHORITY
            readPermission = "android.permission.MANAGE_DOCUMENTS"
            writePermission = "android.permission.MANAGE_DOCUMENTS"
            exported = true
            grantUriPermissions = true
        })
        localRoot = context.cacheDir.toPath().resolve("vs10-pdf-fixtures")
        localRoot.toFile().deleteRecursively()
        Files.createDirectories(localRoot)
    }

    @After
    fun tearDown() {
        localRoot.toFile().deleteRecursively()
        TestDocumentsProvider.reset()
    }

    @Test
    fun validPngWithEmbeddedPdfEvidenceUsesImagePreviewWithoutPdfStaging() = runBlocking {
        val fixtures = embeddedPdfSupportedImages()
        fixtures.forEach { (name, bytes) -> Files.write(localRoot.resolve(name), bytes) }
        val provider = LocalStorageProvider(localRoot, ProviderId("hardening-image"))
        val entries = provider.listChildren(provider.root().success().ref).success()
        entries.forEach { entry ->
            val payload = render(provider, entry)
            assertTrue("Expected image rather than malformed PDF for ${entry.displayName}: $payload", payload is PreviewPayload.Image)
            val image = payload as PreviewPayload.Image
            try { assertEquals(2, image.width); assertEquals(3, image.height) }
            finally { image.bitmap.recycle() }
            assertNoStagedSnapshots()
        }
        assertEquals(6, entries.size)
    }

    @Test
    fun localPdfUsesPlatformRendererAndSupportsPageNavigation() = runBlocking {
        Files.write(localRoot.resolve("misleading.txt"), syntheticPdf(pageCount = 2))
        val provider = LocalStorageProvider(localRoot, ProviderId("vs10-local"))
        val entry = provider.listChildren(provider.root().success().ref).success().single()
        val result = render(provider, entry)
        val payload = result.requirePdfPage()

        assertEquals(2, payload.pageCount)
        assertEquals(0, payload.pageIndex)
        assertTrue(payload.page.width <= PreviewLimits.MAX_PDF_PAGE_SIDE)
        assertTrue(payload.page.height <= PreviewLimits.MAX_PDF_PAGE_SIDE)
        assertTrue(payload.page.width.toLong() * payload.page.height <= PreviewLimits.MAX_PDF_PAGE_PIXELS)
        assertEquals(payload.page.width * payload.page.height * 4, payload.page.pixels.size)
        assertContainsRenderedColor(payload.page)
        val secondPage = payload.session.renderPage(1)
        assertContainsRenderedColor(secondPage)
        payload.session.close()
        assertNoStagedSnapshots()
    }

    @Test
    fun pipeBackedSafSourceStagesAfterFreshGrantValidationAndRenders() = runBlocking {
        TestDocumentsProvider.addDocument("root/pipe-pdf", "root", "scan.data", "text/plain", syntheticPdf(1))
        val resolver = providerResolver()
        val provider = SafStorageProvider(
            resolver,
            TestDocumentsProvider.ROOT_URI,
            ProviderId("vs10-saf"),
            grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val root = provider.root().success()
        val entry = provider.listChildren(root.ref).success().single { it.displayName == "scan.data" }
        val result = render(provider, entry).requirePdfPage()

        assertEquals(1, result.pageCount)
        assertContainsRenderedColor(result.page)
        assertTrue(TestDocumentsProvider.readOpenCalls() >= 2)
        result.session.close()
        assertTrue(TestDocumentsProvider.awaitPendingIo())
        assertNoStagedSnapshots()
    }

    @Test
    fun malformedAndTruncatedPdfStayTypedAndOversizeIsRejectedBeforeRendererOpen() = runBlocking {
        Files.write(localRoot.resolve("truncated.pdf"), "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>".toByteArray())
        val provider = LocalStorageProvider(localRoot, ProviderId("vs10-errors"))
        val root = provider.root().success()
        val truncated = provider.listChildren(root.ref).success().single()
        val failure = render(provider, truncated) as PreviewPayload.Failure
        assertTrue(
            "Unexpected malformed-PDF error ${failure.error}",
            failure.error == PreviewError.CorruptOrMalformed || failure.error == PreviewError.RendererFailure,
        )

        Files.write(localRoot.resolve("large.pdf"), ByteArray(PreviewLimits.MAX_PDF_BYTES + 1).apply {
            "%PDF-1.7\n".toByteArray().copyInto(this)
        })
        val large = provider.listChildren(root.ref).success().single { it.displayName == "large.pdf" }
        val tooLarge = render(provider, large) as PreviewPayload.Failure
        assertEquals(PreviewError.PdfInputTooLarge, tooLarge.error)
        // Error delivery is logical; a subsequent native open waits for the exact failed owner's
        // physical cleanup. Close that accepted owner before asserting filesystem release.
        Files.write(localRoot.resolve("retry.pdf"), syntheticPdf(pageCount = 1))
        val retry = provider.listChildren(root.ref).success().single { it.displayName == "retry.pdf" }
        val recovered = render(provider, retry).requirePdfPage()
        assertContainsRenderedColor(recovered.page)
        recovered.session.close()
        assertNoStagedSnapshots()
    }

    @Test
    fun rendererServiceIsPrivateAndIsolated() {
        val info = context.packageManager.getServiceInfo(
            android.content.ComponentName(context, PdfRendererService::class.java),
            PackageManager.GET_META_DATA,
        )
        assertFalse(info.exported)
        assertTrue(info.flags and ServiceInfo.FLAG_ISOLATED_PROCESS != 0)
        assertTrue(info.processName.endsWith(":pdf_renderer"))
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.INTERNET))
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE),
        )
    }

    @Test
    fun repeatedOpenRenderCloseCyclesLeaveNoSnapshotArtifacts() = runBlocking {
        Files.write(localRoot.resolve("cycles.pdf"), syntheticPdf(3, pageWidth = 3000, pageHeight = 3000))
        val provider = LocalStorageProvider(localRoot, ProviderId("vs10-cycles"))
        val entry = provider.listChildren(provider.root().success().ref).success().single()
        repeat(32) { cycle ->
            val result = render(provider, entry).requirePdfPage()
            assertEquals(3, result.pageCount)
            assertBoundedRender(result.page)
            val rendered = result.session.renderPage(cycle % 3)
            assertBoundedRender(rendered)
            assertContainsRenderedColor(rendered)
            result.session.close()
            assertNoStagedSnapshots()
        }
    }

    @Test
    fun concurrentPlatformPageRequestsAreSerializedAndReplacementClosesOldDocument() = runBlocking {
        Files.write(localRoot.resolve("first.pdf"), syntheticPdf(3))
        Files.write(localRoot.resolve("replacement.pdf"), syntheticPdf(1))
        val provider = LocalStorageProvider(localRoot, ProviderId("vs10-replacement"))
        val entries = provider.listChildren(provider.root().success().ref).success().associateBy { it.displayName }
        val first = render(provider, entries.getValue("first.pdf")).requirePdfPage()

        val pageOne = async { first.session.renderPage(1) }
        val pageTwo = async { first.session.renderPage(2) }
        assertTrue(pageOne.await().pixels.isNotEmpty())
        assertTrue(pageTwo.await().pixels.isNotEmpty())

        val replacement = render(provider, entries.getValue("replacement.pdf")).requirePdfPage()
        assertEquals(1, replacement.pageCount)
        try {
            first.session.renderPage(0)
            throw AssertionError("A replaced PDF session must not remain usable")
        } catch (failure: PdfRendererFailure) {
            assertTrue(failure.kind == PdfRendererFailureKind.UNAVAILABLE || failure.kind == PdfRendererFailureKind.WORKER_DIED)
        } finally {
            replacement.session.close()
        }
        assertNoStagedSnapshots()
    }

    @Test
    fun rendererProcessDeathReturnsTypedFailureAndFreshOpenCanRetry() = runBlocking {
        Files.write(localRoot.resolve("worker-death.pdf"), syntheticPdf(2))
        val provider = LocalStorageProvider(localRoot, ProviderId("vs10-worker-death"))
        val entry = provider.listChildren(provider.root().success().ref).success().single()
        val source = (provider.openPreviewSource(entry) as StorageResult.Success).value
        val item = PreviewItem(entry.ref, entry.displayName, "Test source", entry.mimeType, entry.sizeBytes)
        val first = engine.load(PreviewRequest(item, source)).requirePdfPage()

        (first.session as PdfRendererDeathTestHook).killRendererForTest()

        try {
            withTimeout(10_000) { first.session.renderPage(1) }
            throw AssertionError("A dead renderer must fail the active session")
        } catch (failure: PdfRendererFailure) {
            assertTrue(failure.kind == PdfRendererFailureKind.WORKER_DIED || failure.kind == PdfRendererFailureKind.UNAVAILABLE)
        } finally {
            first.session.invalidate()
        }

        val retry = withTimeout(20_000) { engine.load(PreviewRequest(item, source)) }.requirePdfPage()
        assertEquals(2, retry.pageCount)
        retry.session.close()
        assertNoStagedSnapshots()
    }

    @Test
    fun tenWorkerDeathRetryCyclesRemainUsableAndReleaseSnapshots() = runBlocking {
        Files.write(localRoot.resolve("death-cycles.pdf"), syntheticPdf(2))
        val provider = LocalStorageProvider(localRoot, ProviderId("vs10-death-cycles"))
        val entry = provider.listChildren(provider.root().success().ref).success().single()
        val fdSamples = mutableListOf<Int>()
        repeat(10) { cycle ->
            val first = render(provider, entry).requirePdfPage()
            (first.session as PdfRendererDeathTestHook).killRendererForTest()
            first.session.invalidate()
            val retry = render(provider, entry).requirePdfPage()
            assertEquals(2, retry.pageCount)
            assertContainsRenderedColor(retry.session.renderPage(1))
            retry.session.close()
            assertNoStagedSnapshots()
            fdSamples.add(File("/proc/self/fd").list().orEmpty().size)
        }
        android.util.Log.i("VS10Ownership", "death_retry_10_cycles_fd_samples=$fdSamples")
        assertTrue("Worker death FD growth: $fdSamples", fdSamples.last() <= fdSamples.first() + 4)
    }

    @Test
    fun twentyFiveViewModelDocumentAndPageReplacementsKeepExactOwner() = runBlocking {
        Files.write(localRoot.resolve("A.pdf"), syntheticPdf(3))
        Files.write(localRoot.resolve("B.pdf"), syntheticPdf(3))
        val provider = LocalStorageProvider(localRoot, ProviderId("vs10-vm-replacements"))
        val entries = provider.listChildren(provider.root().success().ref).success().associateBy { it.displayName }
        val vm = PreviewViewModel(engine)
        val fdSamples = mutableListOf<Int>()
        try {
            repeat(25) { cycle ->
                val a = entries.getValue("A.pdf")
                val b = entries.getValue("B.pdf")
                fun open(entry: StorageEntry) = vm.open(PreviewItem(entry.ref, entry.displayName, "Test", entry.mimeType, entry.sizeBytes)) {
                    provider.openPreviewSource(entry)
                }
                open(a)
                val first = withTimeout(20_000) { vm.state.first {
                    (it is PreviewUiState.Ready && it.item.identity == a.ref) || it is PreviewUiState.Error
                } }
                assertTrue("Expected A Ready, got $first", first is PreviewUiState.Ready)
                vm.nextPage()
                open(b)
                val second = withTimeout(20_000) { vm.state.first {
                    (it is PreviewUiState.Ready && it.item.identity == b.ref) || it is PreviewUiState.Error
                } }
                assertTrue("Expected B Ready, got $second", second is PreviewUiState.Ready)
                val ready = second as PreviewUiState.Ready
                vm.nextPage(); vm.nextPage()
                val navigated = withTimeout(20_000) { vm.state.first {
                    (it is PreviewUiState.Ready && (it.payload as? PreviewPayload.PdfPage)?.pageIndex == 2) || it is PreviewUiState.Error
                } }
                assertTrue("B must remain navigable: $navigated", navigated is PreviewUiState.Ready)
                vm.close()
                withTimeout(5_000) { vm.state.first { it == PreviewUiState.Idle } }
                (ready.payload as PreviewPayload.PdfPage).session.close()
                assertNoStagedSnapshots()
                if (cycle % 5 == 4) fdSamples.add(File("/proc/self/fd").list().orEmpty().size)
            }
        } finally { vm.close() }
        android.util.Log.i("VS10Ownership", "vm_replacement_25_cycles_fd_samples=$fdSamples")
        assertTrue("Replacement FD growth: $fdSamples", fdSamples.last() <= fdSamples.first() + 4)
    }

    private suspend fun render(provider: com.omnifile.preview.PreviewSourceProvider, entry: StorageEntry): PreviewPayload {
        val source = when (val result = provider.openPreviewSource(entry)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> error("Preview source resolution failed: ${result.error}")
        }
        val item = PreviewItem(entry.ref, entry.displayName, "Test source", entry.mimeType, entry.sizeBytes)
        return withTimeout(20_000) { engine.load(PreviewRequest(item, source)) }
    }

    private fun providerResolver(): ContentResolver {
        val provider = TestDocumentsProvider()
        provider.attachInfo(instrumentation.context, ProviderInfo().apply {
            authority = TestDocumentsProvider.AUTHORITY
            readPermission = "android.permission.MANAGE_DOCUMENTS"
            writePermission = "android.permission.MANAGE_DOCUMENTS"
            exported = true
            grantUriPermissions = true
        })
        return ContentResolver.wrap(provider)
    }

    private fun PreviewPayload.requirePdfPage(): PreviewPayload.PdfPage = when (this) {
        is PreviewPayload.PdfPage -> this
        is PreviewPayload.Failure -> throw AssertionError("Expected a PDF page; preview error was $error")
        else -> throw AssertionError("Expected a PDF page; payload was ${this::class.simpleName}")
    }

    private fun assertNoStagedSnapshots() {
        val workspace = File(File(context.noBackupFilesDir, "preview"), "pdf")
        assertTrue(workspace.listFiles().orEmpty().none { it.name.endsWith(".ready") || it.name.endsWith(".partial") || it.name.endsWith(".candidate") })
    }

    private fun assertContainsRenderedColor(page: PdfRenderedPage) {
        assertTrue(page.pixels.indices.step(4).any { offset ->
            val first = page.pixels[offset].toInt() and 0xff
            val second = page.pixels[offset + 1].toInt() and 0xff
            val third = page.pixels[offset + 2].toInt() and 0xff
            first != second || second != third
        })
    }

    private fun assertBoundedRender(page: PdfRenderedPage) {
        assertTrue(page.width <= PreviewLimits.MAX_PDF_PAGE_SIDE)
        assertTrue(page.height <= PreviewLimits.MAX_PDF_PAGE_SIDE)
        assertTrue(page.width.toLong() * page.height <= PreviewLimits.MAX_PDF_PAGE_PIXELS)
        assertEquals(page.width * page.height * 4, page.pixels.size)
    }

    private fun syntheticPdf(pageCount: Int, pageWidth: Int = 120, pageHeight: Int = 160): ByteArray {
        val bodies = mutableListOf<String>()
        val kids = (0 until pageCount).joinToString(" ") { index -> "${3 + index * 2} 0 R" }
        bodies += "<< /Type /Catalog /Pages 2 0 R >>"
        bodies += "<< /Type /Pages /Kids [$kids] /Count $pageCount >>"
        repeat(pageCount) { index ->
            val pageObject = 3 + index * 2
            val streamObject = pageObject + 1
            val content = "q 0.15 0.35 0.75 rg 0 0 $pageWidth $pageHeight re f Q"
            bodies += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pageWidth $pageHeight] /Resources << >> /Contents $streamObject 0 R >>"
            bodies += "<< /Length ${content.toByteArray().size} >>\nstream\n$content\nendstream"
        }
        val output = ByteArrayOutputStream()
        output.write("%PDF-1.4\n".toByteArray())
        val offsets = mutableListOf(0)
        bodies.forEachIndexed { index, body ->
            offsets += output.size()
            output.write("${index + 1} 0 obj\n$body\nendobj\n".toByteArray())
        }
        val xref = output.size()
        output.write("xref\n0 ${bodies.size + 1}\n0000000000 65535 f \n".toByteArray())
        offsets.drop(1).forEach { offset ->
            output.write("%010d 00000 n \n".format(offset).toByteArray())
        }
        output.write("trailer\n<< /Size ${bodies.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n".toByteArray())
        return output.toByteArray()
    }

    private fun <T> StorageResult<T>.success(): T = when (this) {
        is StorageResult.Success -> value
        is StorageResult.Failure -> error("Expected success, got $error")
    }
}
