package com.omnifile.storage

import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifile.preview.PreviewEngine
import com.omnifile.preview.PreviewItem
import com.omnifile.preview.PreviewPayload
import com.omnifile.preview.PreviewRequest
import com.omnifile.preview.PreviewSourceProvider
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewStorageInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var resolver: ContentResolver
    private lateinit var localRoot: java.nio.file.Path

    @Before
    fun setUp() {
        TestDocumentsProvider.reset()
        val provider = TestDocumentsProvider()
        provider.attachInfo(instrumentation.context, ProviderInfo().apply {
            authority = TestDocumentsProvider.AUTHORITY
            readPermission = "android.permission.MANAGE_DOCUMENTS"
            writePermission = "android.permission.MANAGE_DOCUMENTS"
            exported = true
            grantUriPermissions = true
        })
        resolver = ContentResolver.wrap(provider)
        localRoot = context.filesDir.toPath().resolve("vs09-preview-fixtures")
        localRoot.toFile().deleteRecursively()
        Files.createDirectories(localRoot)
    }

    @After
    fun tearDown() {
        localRoot.toFile().deleteRecursively()
        TestDocumentsProvider.reset()
    }

    @Test
    fun localProviderOpensRealTextAndSamplesAProviderOwnedImage() = runBlocking {
        Files.write(localRoot.resolve("notes.txt"), "Local preview ✓".toByteArray())
        Files.write(localRoot.resolve("misleading.bin"), pngFixture())
        val provider = LocalStorageProvider(localRoot, ProviderId("preview-local-device"))
        val root = provider.root().requireSuccess()
        val entries = provider.listChildren(root.ref).requireSuccess()
        val text = preview(provider, entries.single { it.displayName == "notes.txt" })
        assertEquals("Local preview ✓", (text as PreviewPayload.Text).content)
        val image = preview(provider, entries.single { it.displayName == "misleading.bin" }) as PreviewPayload.Image
        assertEquals(4, image.width)
        assertEquals(3, image.height)
        image.bitmap.recycle()
    }

    @Test
    fun safSequentialProviderPreviewsTextAndImageOnlyWithReadCapability() = runBlocking {
        TestDocumentsProvider.addDocument("root/photo", "root", "photo.bin", "text/plain", pngFixture())
        val saf = SafStorageProvider(
            contentResolver = resolver,
            treeUri = TestDocumentsProvider.ROOT_URI,
            id = ProviderId("preview-saf-device"),
            grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val root = saf.root().requireSuccess()
        val entries = saf.listChildren(root.ref).requireSuccess()
        val text = preview(saf, entries.single { it.displayName == "alpha.txt" }) as PreviewPayload.Text
        assertEquals("alpha", text.content)
        val image = preview(saf, entries.single { it.displayName == "photo.bin" }) as PreviewPayload.Image
        assertEquals(4, image.width)
        assertEquals(3, image.height)
        image.bitmap.recycle()

        val noReadGrant = SafStorageProvider(resolver, TestDocumentsProvider.ROOT_URI, ProviderId("preview-saf-denied"), grantFlags = 0)
        val deniedRoot = noReadGrant.root().requireSuccess()
        val deniedText = noReadGrant.listChildren(deniedRoot.ref).requireSuccess().single { it.displayName == "alpha.txt" }
        assertTrue(StorageCapability.READ_SEQUENTIAL !in deniedText.capabilities)
        assertTrue(noReadGrant.openPreviewSource(deniedText) is StorageResult.Failure)
    }

    private suspend fun preview(provider: PreviewSourceProvider, entry: StorageEntry): PreviewPayload {
        val source = (provider.openPreviewSource(entry) as StorageResult.Success).value
        return PreviewEngine().load(
            PreviewRequest(
                PreviewItem(entry.ref, entry.displayName, "Fixture source", entry.mimeType, entry.sizeBytes),
                source,
            ),
        )
    }

    private fun pngFixture(): ByteArray = ByteArrayOutputStream().use { bytes ->
        val bitmap = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        bitmap.recycle()
        bytes.toByteArray()
    }

    private fun <T> StorageResult<T>.requireSuccess(): T = when (this) {
        is StorageResult.Success -> value
        is StorageResult.Failure -> error("Expected success, got $error")
    }
}
