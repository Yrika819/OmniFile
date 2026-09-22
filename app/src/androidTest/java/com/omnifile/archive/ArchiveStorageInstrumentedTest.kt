package com.omnifile.archive

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ProviderInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifile.files.FilesRepository
import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.ProviderId
import com.omnifile.storage.SafStorageProvider
import com.omnifile.storage.StorageResult
import com.omnifile.storage.TestDocumentsProvider
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
class ArchiveStorageInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var resolver: ContentResolver
    private lateinit var localRoot: java.nio.file.Path

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
        localRoot = context.filesDir.toPath().resolve("vs08-archive-local")
        localRoot.toFile().deleteRecursively()
        Files.createDirectories(localRoot)
    }

    @After
    fun tearDown() {
        localRoot.toFile().deleteRecursively()
        TestDocumentsProvider.reset()
    }

    @Test
    fun controlledSafZipBrowsesSequentiallyAndExtractsToLocal() = runBlocking {
        TestDocumentsProvider.addDocument(
            "root/archive",
            "root",
            "fixture.zip",
            "application/zip",
            zipOf("folder/file.txt" to "device".toByteArray()),
        )
        val saf = SafStorageProvider(resolver, TestDocumentsProvider.ROOT_URI, ProviderId("saf-vs08"))
        val safRoot = saf.root().requireSuccess()
        val archive = saf.listChildren(safRoot.ref).requireSuccess().single { it.displayName == "fixture.zip" }
        assertFalse(com.omnifile.storage.StorageCapability.READ_SEEKABLE in archive.capabilities)

        val repository = FilesRepository(mapOf(saf.id to saf))
        val document = (ArchiveRepository(repository).open(archive) as ArchiveOpenResult.Success).document
        val folder = document.list().single { it.displayName == "folder" }
        assertEquals("file.txt", document.list(folder.path).single().displayName)

        val local = LocalStorageProvider(localRoot, ProviderId("local-vs08"))
        val destination = local.root().requireSuccess()
        val result = ArchiveExtractor(repository).extract(document, listOf(folder), local, destination)
        assertTrue(result is ArchiveExtractionResult.Success)
        assertArrayEquals("device".toByteArray(), Files.readAllBytes(localRoot.resolve("folder/file.txt")))
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        output.toByteArray()
    }

    private fun <T> StorageResult<T>.requireSuccess(): T = when (this) {
        is StorageResult.Success -> value
        is StorageResult.Failure -> error("Expected success, got $error")
    }
}
