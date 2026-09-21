package com.omnifile.media

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ProviderInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifile.storage.ProviderId
import com.omnifile.storage.SafStorageProvider
import com.omnifile.storage.SeekSupport
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import com.omnifile.storage.TestDocumentsProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SAF playback-source resolution against the controlled in-process
 * DocumentsProvider: pipe descriptors are proven NOT_SEEKABLE, real regular
 * file descriptors are proven SEEKABLE, and grant/provider failures map
 * truthfully. No universal SAF seek claim is made.
 */
@RunWith(AndroidJUnit4::class)
class SafPlaybackSourceInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().context
    private lateinit var resolver: ContentResolver
    private lateinit var provider: SafStorageProvider

    @Before
    fun setUp() {
        TestDocumentsProvider.reset()
        val controlled = TestDocumentsProvider()
        controlled.attachInfo(
            context,
            ProviderInfo().apply {
                authority = TestDocumentsProvider.AUTHORITY
                readPermission = "android.permission.MANAGE_DOCUMENTS"
                writePermission = "android.permission.MANAGE_DOCUMENTS"
                exported = true
                grantUriPermissions = true
            },
        )
        resolver = ContentResolver.wrap(controlled)
        TestDocumentsProvider.addDocument(
            "root/tone.wav",
            "root",
            "tone.wav",
            "audio/x-wav",
            byteArrayOf(1, 2, 3, 4),
        )
        provider = SafStorageProvider(
            contentResolver = resolver,
            treeUri = TestDocumentsProvider.ROOT_URI,
            id = ProviderId("saf-test"),
        )
    }

    @Test
    fun pipeBackedSafAudioIsProvenNotSeekable() = runBlocking {
        val entry = audioEntry()

        val result = provider.resolvePlaybackSource(entry)

        val source = (result as StorageResult.Success).value
        assertEquals(SeekSupport.NOT_SEEKABLE, source.seekSupport)
        assertTrue(source.transportUri.startsWith("content://"))
        assertEquals("SAF folder", source.sourceLabel)
    }

    @Test
    fun regularFileBackedSafAudioIsProvenSeekable() = runBlocking {
        // App-private regular-file backing: matches the working media fixture pattern.
        // The test-only instrumentation APK context does not own a reliably
        // writable filesDir on the device.
        val backing = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "vs07-saf-backing.wav",
        )
        try {
            backing.writeBytes(byteArrayOf(9, 9, 9))
            TestDocumentsProvider.setRegularFileBacking("root/tone.wav", backing)
            val entry = audioEntry()

            val result = provider.resolvePlaybackSource(entry)

            assertEquals(SeekSupport.SEEKABLE, (result as StorageResult.Success).value.seekSupport)
        } finally {
            backing.delete()
        }
    }

    @Test
    fun lostGrantDuringProbeFailsAsPermissionDenied() = runBlocking {
        TestDocumentsProvider.configureReadFailure("root/tone.wav", TestDocumentsProvider.FAILURE_SECURITY)
        val entry = audioEntry()

        val result = provider.resolvePlaybackSource(entry)

        assertEquals(StorageResult.Failure(StorageError.PermissionDenied), result)
    }

    @Test
    fun missingDocumentDuringProbeFailsAsNotFound() = runBlocking {
        TestDocumentsProvider.configureReadFailure("root/tone.wav", TestDocumentsProvider.FAILURE_NOT_FOUND)
        val entry = audioEntry()

        val result = provider.resolvePlaybackSource(entry)

        assertEquals(StorageResult.Failure(StorageError.NotFound), result)
    }

    @Test
    fun inconclusiveProbeKeepsPlaybackPossibleButSeekUnknown() = runBlocking {
        TestDocumentsProvider.configureReadFailure("root/tone.wav", TestDocumentsProvider.FAILURE_IO)
        val entry = audioEntry()

        val result = provider.resolvePlaybackSource(entry)

        val source = (result as StorageResult.Success).value
        assertEquals(SeekSupport.UNKNOWN, source.seekSupport)
    }

    @Test
    fun providerUnavailableDuringProbeFailsAsProviderUnavailable() = runBlocking {
        val entry = audioEntry()
        TestDocumentsProvider.setProviderUnavailable(true)

        val result = provider.resolvePlaybackSource(entry)

        assertEquals(StorageResult.Failure(StorageError.ProviderUnavailable), result)
    }

    @Test
    fun staleForeignEntryIsRejected() = runBlocking {
        val foreign = SafStorageProvider(
            contentResolver = resolver,
            treeUri = TestDocumentsProvider.ROOT_URI,
            id = ProviderId("saf-other"),
        )
        val root = (foreign.root() as StorageResult.Success).value
        val entry = (foreign.listChildren(root.ref) as StorageResult.Success).value
            .single { it.displayName == "tone.wav" }

        val result = provider.resolvePlaybackSource(entry)

        assertEquals(StorageResult.Failure(StorageError.StaleReference), result)
    }

    private suspend fun audioEntry(): StorageEntry {
        val root = (provider.root() as StorageResult.Success).value
        return (provider.listChildren(root.ref) as StorageResult.Success).value
            .single { it.displayName == "tone.wav" }
    }
}
