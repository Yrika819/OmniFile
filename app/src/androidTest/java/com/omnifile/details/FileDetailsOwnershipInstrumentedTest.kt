package com.omnifile.details

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ProviderInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifile.files.FilesRepository
import com.omnifile.operations.DurableLocator
import com.omnifile.storage.EntryKind
import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.storage.SafStorageProvider
import com.omnifile.storage.SequentialReadHandle
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import com.omnifile.storage.StorageTransferProvider
import com.omnifile.storage.TestDocumentsProvider
import com.omnifile.storage.TransferFileFacts
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mandatory VS11 device cases: read ownership under cancellation, and SAF authority truthfulness.
 *
 * Both classes carry the targeted-campaign marker because both are mandatory evidence and the
 * targeted re-run is required to keep proving the full mandatory set.
 */
@com.omnifile.preview.PostVs10HardeningTarget
@RunWith(AndroidJUnit4::class)
class FileDetailsOwnershipInstrumentedTest {
    @Test
    fun cancellationNeverPublishesPartialDigestAndClosesSource() = runBlocking {
        val bytes = ByteArray(4 shl 20) { (it % 251).toByte() }
        val handle = GatedHandle(bytes, chunk = 64 * 1024, gateFromRead = 1)
        val provider = SingleHandleProvider(handle)
        val repository = FilesRepository(mapOf(provider.id to provider))
        val calculator = FileDetailsDigestCalculator(repository, Dispatchers.IO)
        val entry = provider.entry("cancellable.bin", bytes.size.toLong())
        val abort = AtomicBoolean(false)

        val pending = async {
            calculator.calculate(entry, shouldAbort = { abort.get() })
        }
        // Deterministic: the reader is parked inside a provider read that cancellation cannot
        // interrupt. Nothing has been published yet.
        assertTrue(handle.enteredRead.await(20, TimeUnit.SECONDS))
        abort.set(true)
        handle.release.countDown()

        val outcome = withTimeout(60_000) { pending.await() }

        assertEquals(DigestOutcome.Cancelled, outcome)
        assertEquals("a cancelled attempt must release its source exactly once", 1, handle.closeCount)
        assertFalse("no digest may ever be produced for a cancelled attempt", outcome is DigestOutcome.Complete)
    }

    @Test
    fun safDigestRequiresCurrentGrantAndReportsUnavailableSource() = runBlocking {
        val entry = safEntry()
        val calculator = FileDetailsDigestCalculator(safRepository, Dispatchers.IO)

        // Authority is not current: the provider refuses, and that is reported as an access
        // failure rather than as a generic IOException and never as a fabricated grant.
        TestDocumentsProvider.configureReadFailure("root/alpha", TestDocumentsProvider.FAILURE_SECURITY)
        assertEquals(
            DigestOutcome.Failed(DigestFailure.AccessUnavailable, 0),
            calculator.calculate(entry),
        )
        assertNoPersistedGrant()

        // The document is gone: unavailable source, still a distinct typed state.
        TestDocumentsProvider.configureReadFailure("root/alpha", TestDocumentsProvider.FAILURE_NOT_FOUND)
        assertEquals(
            DigestOutcome.Failed(DigestFailure.SourceUnavailable, 0),
            calculator.calculate(entry),
        )

        // With authority restored the same entry digests to exactly its bytes.
        TestDocumentsProvider.reset()
        val expected = MessageDigest.getInstance("SHA-256").digest("alpha".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val restored = calculator.calculate(entry)
        assertEquals(expected, (restored as DigestOutcome.Complete).hex)
        assertEquals(5L, restored.bytesRead)
    }

    private lateinit var resolver: ContentResolver
    private lateinit var safRepository: FilesRepository

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun registerControlledProvider() {
        TestDocumentsProvider.reset()
        val controlled = TestDocumentsProvider()
        controlled.attachInfo(
            InstrumentationRegistry.getInstrumentation().context,
            ProviderInfo().apply {
                authority = TestDocumentsProvider.AUTHORITY
                readPermission = "android.permission.MANAGE_DOCUMENTS"
                writePermission = "android.permission.MANAGE_DOCUMENTS"
                exported = true
                grantUriPermissions = true
            },
        )
        resolver = ContentResolver.wrap(controlled)
        val provider = SafStorageProvider(
            contentResolver = resolver,
            treeUri = TestDocumentsProvider.ROOT_URI,
            id = ProviderId("saf-vs11"),
        )
        safRepository = FilesRepository(mapOf(provider.id to provider))
    }

    @After
    fun resetControlledProvider() {
        TestDocumentsProvider.reset()
    }

    private suspend fun safEntry(): StorageEntry {
        val provider = safRepository.transferProvider(ProviderId("saf-vs11"))!!
        val root = (provider.root() as StorageResult.Success).value
        return (provider.listChildren(root.ref) as StorageResult.Success).value
            .single { it.displayName == "alpha.txt" }
    }

    /** A digest attempt must never persist or invent authority on the way to failing. */
    private fun assertNoPersistedGrant() {
        val granted = context.contentResolver.persistedUriPermissions.map { it.uri.authority }
        assertFalse(
            "File Details must never fabricate a persisted SAF grant",
            granted.contains(TestDocumentsProvider.AUTHORITY),
        )
    }

    /**
     * Reads in fixed chunks and parks inside a chosen read until released, modelling a provider
     * that does not cooperate with cancellation.
     */
    private class GatedHandle(
        private val bytes: ByteArray,
        private val chunk: Int,
        private val gateFromRead: Int,
    ) : SequentialReadHandle {
        override val expectedBytes: Long = bytes.size.toLong()
        val release = CountDownLatch(1)
        val enteredRead = CountDownLatch(1)
        var closeCount = 0
            private set
        private var offset = 0
        private var seen = 0

        override fun read(buffer: ByteArray, off: Int, length: Int): Int {
            if (seen >= gateFromRead) {
                enteredRead.countDown()
                // Not a cancellable suspension point: cancellation cannot interrupt this read.
                release.await(30, TimeUnit.SECONDS)
            }
            seen++
            if (offset >= bytes.size) return -1
            val count = minOf(length, chunk, bytes.size - offset)
            System.arraycopy(bytes, offset, buffer, off, count)
            offset += count
            return count
        }

        override fun close() {
            closeCount++
        }
    }

    private class SingleHandleProvider(private val handle: SequentialReadHandle) : StorageTransferProvider {
        override val id: ProviderId = ProviderId("details-gated")
        override val transferCapabilities: Set<com.omnifile.storage.TransferCapability> = emptySet()

        override suspend fun root(): StorageResult<StorageEntry> = StorageResult.Failure(StorageError.Unsupported)

        override suspend fun listChildren(directory: EntryRef): StorageResult<List<StorageEntry>> =
            StorageResult.Failure(StorageError.Unsupported)

        override suspend fun encodeDurableLocator(ref: EntryRef): StorageResult<DurableLocator> =
            StorageResult.Success(DurableLocator(id, "gated", ref.identityKey))

        override suspend fun resolveDurableLocator(locator: DurableLocator): StorageResult<StorageEntry> =
            StorageResult.Failure(StorageError.StaleReference)

        override suspend fun inspectTransfer(locator: DurableLocator): StorageResult<TransferFileFacts> =
            StorageResult.Failure(StorageError.Unsupported)

        override suspend fun openSequentialRead(locator: DurableLocator): StorageResult<SequentialReadHandle> =
            StorageResult.Success(handle)

        override suspend fun createOperationPartial(
            destinationParent: DurableLocator,
            intendedFinalName: String,
            operationId: String,
        ): StorageResult<DurableLocator> = StorageResult.Failure(StorageError.Unsupported)

        override suspend fun openSequentialWrite(
            partial: DurableLocator,
            append: Boolean,
            operationId: String?,
        ): StorageResult<com.omnifile.storage.SequentialWriteHandle> =
            StorageResult.Failure(StorageError.Unsupported)

        override suspend fun finalizeOperationPartial(
            partial: DurableLocator,
            destinationParent: DurableLocator,
            intendedFinalName: String,
            operationId: String?,
        ): StorageResult<com.omnifile.storage.FinalizationResult> = StorageResult.Failure(StorageError.Unsupported)

        override suspend fun deleteDurableSource(source: DurableLocator): StorageResult<Unit> =
            StorageResult.Failure(StorageError.Unsupported)

        override suspend fun deleteOperationPartial(
            partial: DurableLocator,
            operationId: String?,
        ): StorageResult<Unit> = StorageResult.Failure(StorageError.Unsupported)

        fun entry(name: String, size: Long) = StorageEntry(
            ref = object : EntryRef {
                override val providerId: ProviderId = this@SingleHandleProvider.id
                override val identityKey: String = "entry:$name"
            },
            displayName = name,
            kind = EntryKind.FILE,
            sizeBytes = size,
            modifiedAtEpochMillis = null,
            mimeType = "application/octet-stream",
            capabilities = setOf(com.omnifile.storage.StorageCapability.READ_SEQUENTIAL),
        )
    }
}
