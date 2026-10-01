package com.omnifile.preview

import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifile.storage.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadAcquisitionInstrumentedTest {
    @Test fun nonCooperativeSafQueryExpiresAtAbsoluteDeadlineAndLateCursorCloses() = runBlocking {
        scenario(query = true, realDeadline = true)
    }
    @Test fun nonCooperativeSafOpenExpiresAndLateDescriptorCloses() = runBlocking {
        scenario(query = false, realDeadline = false)
    }
    @Test fun replacementDuringBlockedSafResolutionCannotPublishOrRetainLateCursor() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        TestDocumentsProvider.reset()
        TestDocumentsProvider.addDocument("root/blocked-pdf", "root", "A.pdf", "application/pdf", ownershipTestPdf(1))
        val testProvider = TestDocumentsProvider().apply { attachInfo(instrumentation.context, ProviderInfo().apply {
            authority = TestDocumentsProvider.AUTHORITY; exported = true; grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"; writePermission = "android.permission.MANAGE_DOCUMENTS"
        }) }
        val saf = SafStorageProvider(ContentResolver.wrap(testProvider), TestDocumentsProvider.ROOT_URI,
            ProviderId("replacement-saf-test"), grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val safRoot = (saf.root() as StorageResult.Success).value
        val a = (saf.listChildren(safRoot.ref) as StorageResult.Success).value.single { it.displayName == "A.pdf" }
        val localRoot = java.io.File(context.cacheDir, "vs10-blocked-resolution-replacement").apply { mkdirs() }
        val localFile = java.io.File(localRoot, "B.pdf").apply { writeBytes(ownershipTestPdf(2)) }
        val local = LocalStorageProvider(localRoot.toPath(), ProviderId("replacement-local-test"))
        val b = (local.listChildren((local.root() as StorageResult.Success).value.ref) as StorageResult.Success).value.single()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val scopeA = AtomicReference<ReadAcquisitionScope>(); val lateCursor = AtomicReference<Cursor>()
        TestDocumentsProvider.beforeQueryForAcquisitionTest = Runnable {
            scopeA.set(ReadAcquisitionScope.current()!!); entered.countDown(); check(release.await(30, TimeUnit.SECONDS))
        }
        TestDocumentsProvider.queryResultForAcquisitionTest = java.util.function.Consumer { lateCursor.set(it) }
        ReadAcquisitionExecutor().use { runner ->
            val store = PdfSnapshotStore(context.noBackupFilesDir, acquisitions = runner)
            val vm = PreviewViewModel(PreviewEngine(PdfPreviewController(store, AndroidPdfRendererClientFactory(context)), runner))
            var acceptedB: PdfDocumentSession? = null
            try {
                fun item(entry: StorageEntry) = PreviewItem(entry.ref, entry.displayName, "Test", entry.mimeType, entry.sizeBytes)
                vm.open(item(a)) { saf.openPreviewSource(a) }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                vm.open(item(b)) { local.openPreviewSource(b) }
                val state = withTimeout(20_000) { vm.state.first { it is PreviewUiState.Ready || it is PreviewUiState.Error } }
                assertTrue("B must render while A remains physically blocked: $state", state is PreviewUiState.Ready)
                val ready = state as PreviewUiState.Ready
                assertEquals(b.ref, ready.item.identity)
                acceptedB = (ready.payload as PreviewPayload.PdfPage).session
                assertEquals(1, runner.retainedSlots)
                assertEquals(ReadAcquisitionState.CANCELLED, scopeA.get().state)
                release.countDown(); assertTrue(runner.awaitIdle())
                assertTrue(lateCursor.get().isClosed)
                assertSame(acceptedB, ((vm.state.value as PreviewUiState.Ready).payload as PreviewPayload.PdfPage).session)
                vm.nextPage()
                val navigated = withTimeout(10_000) { vm.state.first {
                    (it is PreviewUiState.Ready && (it.payload as? PreviewPayload.PdfPage)?.pageIndex == 1) || it is PreviewUiState.Error
                } }
                assertTrue("B must remain navigable: $navigated", navigated is PreviewUiState.Ready)
            } finally {
                release.countDown(); vm.close()
                withTimeout(5_000) { vm.state.first { it == PreviewUiState.Idle } }
                acceptedB?.close()
                assertTrue(runner.awaitIdle())
                TestDocumentsProvider.reset(); localFile.delete(); localRoot.delete()
            }
            assertTrue(store.workspace.listFiles().orEmpty().none { it.name.endsWith(".candidate") })
        }
    }

    private suspend fun scenario(query: Boolean, realDeadline: Boolean) = coroutineScope {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        TestDocumentsProvider.reset()
        TestDocumentsProvider.addDocument("root/acquisition-pdf", "root", "fixture.pdf", "application/pdf", ownershipTestPdf(1))
        val testProvider = TestDocumentsProvider().apply { attachInfo(instrumentation.context, ProviderInfo().apply {
            authority = TestDocumentsProvider.AUTHORITY; exported = true; grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"; writePermission = "android.permission.MANAGE_DOCUMENTS"
        }) }
        val provider = SafStorageProvider(ContentResolver.wrap(testProvider), TestDocumentsProvider.ROOT_URI,
            ProviderId("acquisition-instrumented"), grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val root = (provider.root() as StorageResult.Success).value
        val entry = (provider.listChildren(root.ref) as StorageResult.Success).value.single { it.displayName == "fixture.pdf" }
        val item = PreviewItem(entry.ref, entry.displayName, "Test", entry.mimeType, entry.sizeBytes)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val scope = AtomicReference<ReadAcquisitionScope>()
        val lateCursor = AtomicReference<Cursor>(); val lateDescriptor = AtomicReference<ParcelFileDescriptor>()
        val gate = Runnable { scope.set(ReadAcquisitionScope.current()!!); entered.countDown(); check(release.await(30, TimeUnit.SECONDS)) }
        if (query) {
            TestDocumentsProvider.beforeQueryForAcquisitionTest = gate
            TestDocumentsProvider.queryResultForAcquisitionTest = java.util.function.Consumer { lateCursor.set(it) }
        } else {
            TestDocumentsProvider.beforeReadOpenForAcquisitionTest = gate
            TestDocumentsProvider.openResultForAcquisitionTest = java.util.function.Consumer { lateDescriptor.set(it) }
        }
        ReadAcquisitionExecutor().use { runner ->
            val store = PdfSnapshotStore(context.noBackupFilesDir, acquisitions = runner)
            val engine = PreviewEngine(PdfPreviewController(store, AndroidPdfRendererClientFactory(context)), runner)
            val began = System.nanoTime()
            val result = async(Dispatchers.Default) { engine.resolveAndLoad(item, { provider.openPreviewSource(entry) }) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                if (!realDeadline) scope.get().expire()
                val payload = withTimeout(15_000) { result.await().payload } as PreviewPayload.Failure
                assertEquals(PreviewError.AcquisitionTimeout, payload.error)
                if (realDeadline) assertTrue("Logical deadline exceeded observation tolerance", System.nanoTime() - began < 13_000_000_000L)
                assertEquals(1, runner.retainedSlots)
            } finally {
                release.countDown()
                assertTrue(runner.awaitIdle())
                TestDocumentsProvider.reset()
            }
            if (query) assertTrue(lateCursor.get().isClosed)
            else assertFalse(lateDescriptor.get().fileDescriptor.valid())
            assertTrue(store.workspace.listFiles().orEmpty().none { it.name.endsWith(".candidate") })
        }
    }
}
