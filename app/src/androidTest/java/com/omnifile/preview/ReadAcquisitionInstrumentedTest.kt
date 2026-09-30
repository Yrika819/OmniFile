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
