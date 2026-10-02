package com.omnifile.details

import com.omnifile.files.FilesRepository
import com.omnifile.operations.DurableLocator
import com.omnifile.storage.EntryKind
import com.omnifile.storage.EntryRef
import com.omnifile.storage.ProviderId
import com.omnifile.storage.SequentialReadHandle
import com.omnifile.storage.StorageCapability
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import com.omnifile.storage.StorageTransferProvider
import com.omnifile.storage.TransferFileFacts
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * State-machine contract for one File Details surface.
 *
 * Deterministic coordination only: explicit deferred gates, never sleeps. Recreated surfaces
 * model configuration change (same owner) and process death (no owner) separately.
 */
class FileDetailsViewModelTest {
    @Test
    fun openPublishesTruthfulMetadataAndAnIdleDigest() = withFixture { fixture ->
        fixture.vm.open(
            fixture.entry(name = "report.txt", size = 2048, modified = 1_700_000_000_000, mime = "text/plain"),
            "Local storage",
        )

        val content = fixture.metadata()
        assertEquals("report.txt", content.metadata.displayName)
        assertEquals("Local storage", content.metadata.sourceLabel)
        assertEquals(2048L, content.metadata.sizeBytes)
        assertEquals(1_700_000_000_000L, content.metadata.modifiedAtEpochMillis)
        assertEquals("text/plain", content.metadata.mimeType)
        assertEquals(DigestUiState.Idle, content.digest)
        assertTrue(content.hashingSupported)
    }

    @Test
    fun unprovenMetadataStaysNullRatherThanBeingGuessedFromTheName() = withFixture { fixture ->
        fixture.vm.open(fixture.entry(name = "notes.txt", size = null, modified = null, mime = null), "SAF folder")

        val content = fixture.metadata()
        assertNull(content.metadata.sizeBytes)
        assertNull(content.metadata.modifiedAtEpochMillis)
        assertNull(content.metadata.mimeType)
    }

    @Test
    fun aDirectoryCanNeverBecomeAFileDetailsSource() = withFixture { fixture ->
        val failure = runCatching {
            fixture.vm.open(fixture.entry(name = "folder", kind = EntryKind.DIRECTORY), "Local storage")
        }
        assertTrue(failure.isFailure)
        assertEquals(FileDetailsUiState.Unavailable, fixture.vm.uiState.value)
    }

    @Test
    fun metadataStaysVisibleButHashingIsGatedWhenTheSourceCannotBeRead() = withFixture { fixture ->
        fixture.vm.open(fixture.entry(readable = false), "SAF folder")

        val content = fixture.metadata()
        assertFalse(content.hashingSupported)
        assertEquals("SAF folder", content.metadata.sourceLabel)

        fixture.vm.calculate()
        assertEquals(DigestUiState.Idle, fixture.metadata().digest)
    }

    @Test
    fun calculatingPublishesACompleteLowercaseDigestOfTheStreamedBytes() = withFixture { fixture ->
        val bytes = ByteArray(4096) { 7 }
        fixture.provider.nextHandle = CountingHandle(bytes, chunk = 256)
        fixture.vm.open(fixture.entry(size = bytes.size.toLong()), "Local storage")

        fixture.vm.calculate()

        val complete = fixture.digest<DigestUiState.Complete>()
        assertEquals(64, complete.hex.length)
        assertTrue(complete.hex.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(reference(bytes), complete.hex)
    }

    @Test
    fun determinateProgressIsPublishedWhileTheStreamIsStillOpen() = withFixture { fixture ->
        val gate = CompletableDeferred<Unit>()
        val handle = GatedHandle(ByteArray(4096) { 3 }, gate)
        fixture.provider.nextHandle = handle
        fixture.vm.open(fixture.entry(size = 4096L), "Local storage")

        fixture.vm.calculate()
        handle.enteredRead.await()

        // The first read already reported real progress while the stream remains open.
        val calculating = withTimeout(10_000) {
            fixture.vm.uiState.filterIsInstance<FileDetailsUiState.Metadata>()
                .first { it.digest is DigestUiState.Calculating && it.digest.bytesRead > 0 }
                .digest as DigestUiState.Calculating
        }
        assertEquals(4096L, calculating.expectedBytes)
        assertTrue(calculating.bytesRead > 0)
        assertTrue(calculating.bytesRead <= 4096)
        assertTrue("progress must never exceed 100%", (calculating.fraction ?: 1f) <= 1f)

        gate.complete(Unit)
        fixture.digest<DigestUiState.Complete>()
    }

    @Test
    fun cancellingNeverPublishesADigestAndStillReleasesTheSourceOnce() = withFixture { fixture ->
        val bytes = ByteArray(1 shl 20) { 4 }
        val handle = CountingHandle(bytes, chunk = 64)
        fixture.provider.nextHandle = handle
        fixture.vm.open(fixture.entry(size = bytes.size.toLong()), "Local storage")

        fixture.vm.calculate()
        handle.firstRead.await()
        fixture.vm.cancel()

        assertEquals(DigestUiState.Cancelled, fixture.digest<DigestUiState.Cancelled>())
        assertEquals("a cancelled attempt releases its source exactly once", 1, handle.closeCount)
    }

    @Test
    fun cancellingStaysCancellingUntilTheReaderActuallyReturned() = withFixture { fixture ->
        val gate = CompletableDeferred<Unit>()
        val handle = GatedHandle(ByteArray(1024) { 5 }, gate)
        fixture.provider.nextHandle = handle
        fixture.vm.open(fixture.entry(size = 1024L), "Local storage")

        fixture.vm.calculate()
        handle.enteredRead.await()
        fixture.vm.cancel()

        // A provider mid-read may still hold its handle, so the surface must not claim that
        // cancellation physically finished.
        assertEquals(DigestUiState.Cancelling, fixture.metadata().digest)

        gate.complete(Unit)
        assertEquals(DigestUiState.Cancelled, fixture.digest<DigestUiState.Cancelled>())
    }

    @Test
    fun aReplacementCalculationRevokesTheOldGenerationBeforeItCanPublish() = withFixture { fixture ->
        val gate = CompletableDeferred<Unit>()
        val blocked = GatedHandle(ByteArray(4096) { 1 }, gate)
        fixture.provider.nextHandle = blocked
        fixture.vm.open(fixture.entry(name = "a.bin", size = 4096), "Local storage")
        fixture.vm.calculate()
        blocked.enteredRead.await()

        val replacement = ByteArray(4096) { 2 }
        fixture.provider.nextHandle = CountingHandle(replacement, chunk = 4096)
        fixture.vm.open(fixture.entry(name = "b.bin", size = 4096), "Local storage")
        fixture.vm.calculate()
        val complete = fixture.digest<DigestUiState.Complete>()

        gate.complete(Unit)
        assertEquals(reference(replacement), complete.hex)
        assertEquals("b.bin", fixture.metadata().metadata.displayName)
        assertEquals(DigestUiState.Complete(complete.hex), fixture.metadata().digest)
    }

    @Test
    fun changingTheSelectedSourceInvalidatesTheInFlightCalculation() = withFixture { fixture ->
        val gate = CompletableDeferred<Unit>()
        val blocked = GatedHandle(ByteArray(2048) { 1 }, gate)
        fixture.provider.nextHandle = blocked
        fixture.vm.open(fixture.entry(name = "a.bin", size = 2048), "Local storage")
        fixture.vm.calculate()
        blocked.enteredRead.await()

        fixture.vm.open(fixture.entry(name = "b.bin", size = 2048), "Local storage")
        gate.complete(Unit)

        assertEquals(DigestUiState.Idle, fixture.metadata().digest)
    }

    @Test
    fun aVanishedSourceIsTypedRatherThanCollapsedIntoGenericIo() = withFixture { fixture ->
        fixture.provider.openResult = { StorageResult.Failure(StorageError.NotFound) }
        fixture.vm.open(fixture.entry(), "SAF folder")

        fixture.vm.calculate()

        assertEquals(
            FileDetailsFailure.SOURCE_UNAVAILABLE,
            fixture.digest<DigestUiState.Failed>().failure,
        )
    }

    @Test
    fun revokedAuthorityIsTypedAsAccessUnavailable() = withFixture { fixture ->
        fixture.provider.openResult = { StorageResult.Failure(StorageError.PermissionDenied) }
        fixture.vm.open(fixture.entry(), "SAF folder")

        fixture.vm.calculate()

        assertEquals(
            FileDetailsFailure.ACCESS_UNAVAILABLE,
            fixture.digest<DigestUiState.Failed>().failure,
        )
    }

    @Test
    fun anUnsupportedSourceIsTypedRatherThanCollapsedIntoGenericIo() = withFixture { fixture ->
        fixture.provider.openResult = { StorageResult.Failure(StorageError.Unsupported) }
        fixture.vm.open(fixture.entry(), "SAF folder")

        fixture.vm.calculate()

        assertEquals(
            FileDetailsFailure.UNSUPPORTED,
            fixture.digest<DigestUiState.Failed>().failure,
        )
    }

    @Test
    fun aSourceThatChangedDuringTheReadNeverPublishesItsDigest() = withFixture { fixture ->
        val bytes = ByteArray(512) { 9 }
        fixture.provider.nextHandle = CountingHandle(bytes, chunk = 512)
        fixture.provider.refreshResult = { StorageResult.Failure(StorageError.NotFound) }
        fixture.vm.open(fixture.entry(size = 512L), "Local storage")

        fixture.vm.calculate()

        assertEquals(
            FileDetailsFailure.SOURCE_CHANGED,
            fixture.digest<DigestUiState.Failed>().failure,
        )
    }

    @Test
    fun onlyAReadFailureOffersRetry() = withFixture { fixture ->
        fixture.provider.openResult = { StorageResult.Failure(StorageError.PermissionDenied) }
        fixture.vm.open(fixture.entry(), "SAF folder")
        fixture.vm.calculate()
        assertFalse(fixture.failure(FileDetailsFailure.ACCESS_UNAVAILABLE).isRetryable)

        fixture.provider.openResult = { StorageResult.Failure(StorageError.Unsupported) }
        fixture.vm.calculate()
        assertFalse(fixture.failure(FileDetailsFailure.UNSUPPORTED).isRetryable)

        fixture.provider.openResult = { StorageResult.Failure(StorageError.NotFound) }
        fixture.vm.calculate()
        assertFalse(fixture.failure(FileDetailsFailure.SOURCE_UNAVAILABLE).isRetryable)
    }

    @Test
    fun retryReopensTheSameAuthorizedSourceAndCanComplete() = withFixture { fixture ->
        fixture.provider.nextHandle = CountingHandle(ByteArray(256) { 2 }, readFailure = IOException("transient"))
        fixture.vm.open(fixture.entry(size = 256L), "Local storage")
        fixture.vm.calculate()
        assertTrue(fixture.digest<DigestUiState.Failed>().isRetryable)

        val bytes = ByteArray(256) { 2 }
        fixture.provider.nextHandle = CountingHandle(bytes, chunk = 256)
        fixture.vm.retry()

        assertEquals(reference(bytes), fixture.digest<DigestUiState.Complete>().hex)
    }

    @Test
    fun retryIsIgnoredForAStateThatCannotMeaningfullyReopen() = withFixture { fixture ->
        fixture.vm.open(fixture.entry(), "Local storage")

        fixture.vm.retry()

        assertEquals(DigestUiState.Idle, fixture.metadata().digest)
    }

    @Test
    fun closingRevokesAPendingCalculationAndReportsUnavailable() = withFixture { fixture ->
        val gate = CompletableDeferred<Unit>()
        val handle = GatedHandle(ByteArray(1024) { 1 }, gate)
        fixture.provider.nextHandle = handle
        fixture.vm.open(fixture.entry(size = 1024L), "Local storage")
        fixture.vm.calculate()
        handle.enteredRead.await()

        fixture.vm.close()
        gate.complete(Unit)

        assertEquals(FileDetailsUiState.Unavailable, fixture.vm.uiState.value)
    }

    @Test
    fun processDeathLeavesNoSurfaceToRestore() = withFixture { fixture ->
        // A fresh ViewModel is exactly what Android builds after process death: no source
        // ownership, so the restored route is dismissed rather than reconstructed.
        val recreated = FileDetailsViewModel(fixture.repository, Dispatchers.Default, fixture.scope)

        assertEquals(FileDetailsUiState.Unavailable, recreated.uiState.value)
        assertNull((recreated.uiState.value as? FileDetailsUiState.Metadata)?.digest)
    }

    @Test
    fun configurationRecreationKeepsTheSameLogicalOwnerAndItsResult() = withFixture { fixture ->
        val bytes = ByteArray(4096) { 6 }
        fixture.provider.nextHandle = CountingHandle(bytes, chunk = 64)
        fixture.vm.open(fixture.entry(name = "keep.bin", size = bytes.size.toLong()), "Local storage")

        fixture.vm.calculate()

        // The Activity-scoped owner survives configuration change, so it finishes and publishes
        // exactly once; nothing is restarted and nothing is re-read.
        assertEquals(reference(bytes), fixture.digest<DigestUiState.Complete>().hex)
        assertEquals("keep.bin", fixture.metadata().metadata.displayName)
        assertEquals(1, fixture.provider.openCount)
    }

    @Test
    fun calculatingWithoutAnOpenSourceIsANoOp() = withFixture { fixture ->
        fixture.vm.calculate()

        assertEquals(FileDetailsUiState.Unavailable, fixture.vm.uiState.value)
    }

    @Test
    fun onlyACompletedDigestIsEverOfferedForCopy() = withFixture { fixture ->
        val bytes = ByteArray(128) { 1 }
        fixture.provider.nextHandle = CountingHandle(bytes, chunk = 128)
        fixture.vm.open(fixture.entry(size = 128L), "Local storage")

        fixture.vm.calculate()
        assertTrue(fixture.digest<DigestUiState.Complete>().hex.isNotEmpty())

        // Every non-complete terminal state carries no digest text at all.
        fixture.provider.openResult = { StorageResult.Failure(StorageError.PermissionDenied) }
        fixture.vm.calculate()
        val failed = fixture.failure(FileDetailsFailure.ACCESS_UNAVAILABLE)
        assertEquals(FileDetailsFailure.ACCESS_UNAVAILABLE, failed.failure)
        assertFalse(failed is DigestUiState.Complete)
    }

    @Test
    fun aZeroLengthSourceCompletesWithTheStandardEmptyDigest() = withFixture { fixture ->
        fixture.provider.nextHandle = CountingHandle(ByteArray(0), expectedBytes = 0L)
        fixture.vm.open(fixture.entry(size = 0L), "Local storage")

        fixture.vm.calculate()

        assertEquals(EMPTY_SHA256, fixture.digest<DigestUiState.Complete>().hex)
    }

    @Test
    fun bytesReadBeyondTheDeclaredSizeInvalidatesCompletionAsSourceChange() = withFixture { fixture ->
        fixture.provider.nextHandle = CountingHandle(ByteArray(1000) { 1 }, chunk = 500, expectedBytes = 100L)
        fixture.vm.open(fixture.entry(size = 100L), "Local storage")

        fixture.vm.calculate()

        assertEquals(FileDetailsFailure.SOURCE_CHANGED, fixture.digest<DigestUiState.Failed>().failure)
    }

    @Test
    fun indeterminateProgressIsReportedWhenTheProviderProvesNoSize() {
        assertNull(DigestUiState.Calculating(bytesRead = 4096, expectedBytes = null).fraction)
    }

    @Test
    fun determinateProgressIsClampedAndNeverExceedsOneHundredPercent() {
        assertEquals(1f, DigestUiState.Calculating(500L, 100L).fraction)
        assertEquals(0f, DigestUiState.Calculating(0L, 100L).fraction)
        assertEquals(0.5f, DigestUiState.Calculating(50L, 100L).fraction)
        assertEquals(
            "zero length is complete, never a division by zero",
            1f,
            DigestUiState.Calculating(0L, 0L).fraction
        )
    }

    private fun reference(input: ByteArray): String =
        Sha256Calculator.toHex(MessageDigest.getInstance("SHA-256").digest(input))

    private suspend fun Fixture.metadata(): FileDetailsUiState.Metadata =
        vm.uiState.filterIsInstance<FileDetailsUiState.Metadata>().first()

    private suspend inline fun <reified T : DigestUiState> Fixture.digest(): T =
        withTimeout(10_000) {
            vm.uiState.filterIsInstance<FileDetailsUiState.Metadata>().first { it.digest is T }.digest as T
        }

    private suspend fun Fixture.failure(expected: FileDetailsFailure): DigestUiState.Failed =
        withTimeout(10_000) {
            vm.uiState.filterIsInstance<FileDetailsUiState.Metadata>()
                .first { (it.digest as? DigestUiState.Failed)?.failure == expected }
                .digest as DigestUiState.Failed
        }

    private fun withFixture(block: suspend (Fixture) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val provider = DetailsProvider()
            val repository = FilesRepository(mapOf(provider.id to provider))
            block(Fixture(scope, provider, repository, FileDetailsViewModel(repository, Dispatchers.Default, scope)))
        } finally {
            scope.cancel()
        }
    }

    private class Fixture(
        val scope: CoroutineScope,
        val provider: DetailsProvider,
        val repository: FilesRepository,
        val vm: FileDetailsViewModel,
    ) {
        fun entry(
            name: String = "file.bin",
            size: Long? = 1024,
            modified: Long? = 1_700_000_000_000,
            mime: String? = "application/octet-stream",
            kind: EntryKind = EntryKind.FILE,
            readable: Boolean = true,
        ) = provider.entry(name, size, modified, mime, kind, readable)
    }

    private class DetailsProvider(override val id: ProviderId = ProviderId("saf-tree-details")) :
        StorageTransferProvider {
        var nextHandle: SequentialReadHandle? = null
        var openResult: (() -> StorageResult<SequentialReadHandle>)? = null
        var refreshResult: (() -> StorageResult<StorageEntry>)? = null
        var openCount = 0
            private set

        override val transferCapabilities: Set<com.omnifile.storage.TransferCapability> = emptySet()

        /** Set by the fixture so the provider can answer a re-resolve with the same item. */
        var lastEncodedEntry: StorageEntry? = null

        override suspend fun root(): StorageResult<StorageEntry> = StorageResult.Failure(StorageError.Unsupported)

        override suspend fun listChildren(directory: EntryRef): StorageResult<List<StorageEntry>> =
            StorageResult.Failure(StorageError.Unsupported)

        var lastEntry: StorageEntry? = null

        override suspend fun encodeDurableLocator(ref: EntryRef): StorageResult<DurableLocator> {
            lastEntry = lastEncodedEntry
            return StorageResult.Success(DurableLocator(id, "details-test", ref.identityKey))
        }

        override suspend fun resolveDurableLocator(locator: DurableLocator): StorageResult<StorageEntry> =
            // A provider that has not changed answers with the same logical item.
            refreshResult?.invoke()
                ?: lastEntry?.let { StorageResult.Success(it) }
                ?: StorageResult.Failure(StorageError.StaleReference)

        override suspend fun inspectTransfer(locator: DurableLocator): StorageResult<TransferFileFacts> =
            StorageResult.Failure(StorageError.Unsupported)

        override suspend fun openSequentialRead(locator: DurableLocator): StorageResult<SequentialReadHandle> {
            openCount++
            return openResult?.invoke()
                ?: nextHandle?.let { StorageResult.Success(it) }
                ?: StorageResult.Failure(StorageError.Unsupported)
        }

        override suspend fun createOperationPartial(
            destinationParent: DurableLocator,
            intendedFinalName: String,
            operationId: String,
        ): StorageResult<DurableLocator> = StorageResult.Failure(StorageError.Unsupported)

        override suspend fun openSequentialWrite(
            partial: DurableLocator,
            append: Boolean,
            operationId: String?,
        ): StorageResult<com.omnifile.storage.SequentialWriteHandle> = StorageResult.Failure(StorageError.Unsupported)

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

        fun entry(
            name: String = "file.bin",
            size: Long? = 1024,
            modified: Long? = 1_700_000_000_000,
            mime: String? = "application/octet-stream",
            kind: EntryKind = EntryKind.FILE,
            readable: Boolean = true,
        ): StorageEntry {
            val entry = StorageEntry(
                ref = object : EntryRef {
                    override val providerId: ProviderId = this@DetailsProvider.id
                    override val identityKey: String = "entry:$name"
                },
                displayName = name,
                kind = kind,
                sizeBytes = size,
                modifiedAtEpochMillis = modified,
                mimeType = mime,
                capabilities = buildSet {
                    if (readable && kind == EntryKind.FILE) add(StorageCapability.READ_SEQUENTIAL)
                },
            )
            lastEncodedEntry = entry
            return entry
        }
    }

    /** Deterministic byte handle. No threads, no timing, no sleeps. */
    private open class CountingHandle(
        protected val bytes: ByteArray,
        override val expectedBytes: Long? = bytes.size.toLong(),
        protected val chunk: Int = Int.MAX_VALUE,
        protected val readFailure: Throwable? = null,
    ) : SequentialReadHandle {
        val reads = AtomicInteger(0)
        val firstRead = CompletableDeferred<Unit>()
        var closeCount = 0
            protected set
        protected var offset = 0

        override fun read(buffer: ByteArray, off: Int, length: Int): Int {
            readFailure?.let { throw it }
            val count = consume(buffer, off, length)
            if (count < 0) return -1
            offset += count
            reads.incrementAndGet()
            firstRead.complete(Unit)
            return count
        }

        /** Byte count for this read, or a negative value at end of stream. */
        protected open fun consume(buffer: ByteArray, off: Int, length: Int): Int {
            if (offset >= bytes.size) return -1
            val count = minOf(length, chunk, bytes.size - offset)
            System.arraycopy(bytes, offset, buffer, off, count)
            return count
        }

        override fun close() {
            closeCount++
        }
    }

    /**
     * Blocks inside its reads, starting from the second one, so a caller can observe real
     * progress before the stream opens up again. Models a non-cooperative provider read.
     */
    private class GatedHandle(bytes: ByteArray, private val gate: CompletableDeferred<Unit>) :
        CountingHandle(bytes, chunk = 32) {
        val enteredRead = CompletableDeferred<Unit>()
        private var seen = 0

        override fun consume(buffer: ByteArray, off: Int, length: Int): Int {
            if (seen >= 1) {
                enteredRead.complete(Unit)
                // Deliberately outside any cancellable suspension point: this is the read that
                // cancellation cannot interrupt, only outlast.
                runBlocking { gate.await() }
            }
            seen++
            return super.consume(buffer, off, length)
        }
    }

    private companion object {
        const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
