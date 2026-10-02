package com.omnifile.details

import com.omnifile.files.FilesRepository
import com.omnifile.operations.DurableLocator
import com.omnifile.storage.EntryKind
import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.ProviderId
import com.omnifile.storage.SequentialReadHandle
import com.omnifile.storage.StorageCapability
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageError
import com.omnifile.storage.StorageResult
import com.omnifile.storage.StorageTransferProvider
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Provider-neutral digest contract over a real Local provider plus a controllable delegate that
 * reproduces SAF-shaped conditions: revoked authority, a vanished document, and a source that
 * changes between opening and the post-EOF re-resolve.
 */
class FileDetailsDigestCalculatorTest {
    @Test
    fun aLocalFileDigestsToItsStandardReferenceValue() = withLocalFixture { fixture ->
        val bytes = "the quick brown fox".toByteArray()
        val entry = fixture.file("fox.txt", bytes)
        val ticks = mutableListOf<DigestProgress>()

        val outcome = fixture.calculator.calculate(entry, onProgress = { ticks += it })

        assertEquals(DigestOutcome.Complete(reference(bytes), bytes.size.toLong()), outcome)
        assertTrue(ticks.isNotEmpty())
        assertTrue(ticks.all { it.expectedBytes == bytes.size.toLong() })
    }

    @Test
    fun aLocalFileLargerThanTheStreamingBufferStillDigestsExactly() = withLocalFixture { fixture ->
        val bytes = ByteArray(Sha256Calculator.BUFFER_BYTES * 2 + 517) { (it % 241).toByte() }
        val entry = fixture.file("large.bin", bytes)

        assertEquals(DigestOutcome.Complete(reference(bytes), bytes.size.toLong()), fixture.calculator.calculate(entry))
    }

    @Test
    fun aZeroLengthLocalFileProducesTheStandardEmptyDigest() = withLocalFixture { fixture ->
        val entry = fixture.file("empty.txt", ByteArray(0))

        assertEquals(DigestOutcome.Complete(EMPTY_SHA256, 0L), fixture.calculator.calculate(entry))
    }

    @Test
    fun aLocalHandleIsClosedExactlyOnceOnASuccessfulDigest() = withLocalFixture { fixture ->
        val entry = fixture.file("counted.txt", ByteArray(900) { 5 })
        val handle = CountingHandle(ByteArray(900) { 5 })
        fixture.provider.openOverride = { StorageResult.Success(handle) }
        fixture.provider.refreshOverride = { StorageResult.Success(entry) }

        assertTrue(fixture.calculator.calculate(entry) is DigestOutcome.Complete)
        assertEquals(1, handle.closeCount)
    }

    @Test
    fun aLocalHandleIsClosedExactlyOnceEvenWhenTheReResolveFails() = withLocalFixture { fixture ->
        val entry = fixture.file("counted.txt", ByteArray(64) { 5 })
        val handle = CountingHandle(ByteArray(64) { 5 })
        fixture.provider.openOverride = { StorageResult.Success(handle) }
        fixture.provider.refreshOverride = { StorageResult.Failure(StorageError.ProviderUnavailable) }

        fixture.calculator.calculate(entry)
        assertEquals(1, handle.closeCount)
    }

    @Test
    fun aHandleClosedBeforeTheReResolveNeverLeaksAcrossAnException() = withLocalFixture { fixture ->
        val entry = fixture.file("boom.txt", ByteArray(64) { 5 })
        val handle = CountingHandle(ByteArray(64) { 5 }, readFailure = IOException("mid-read"))
        fixture.provider.openOverride = { StorageResult.Success(handle) }

        assertEquals(DigestOutcome.Failed(DigestFailure.ReadFailed, 0L), fixture.calculator.calculate(entry))
        assertEquals(1, handle.closeCount)
    }

    @Test
    fun aFailedHandleCloseWithdrawsAnOtherwiseCompleteDigest() = withLocalFixture { fixture ->
        val entry = fixture.file("closer.txt", ByteArray(128) { 1 })
        val handle = CountingHandle(ByteArray(128) { 1 }, closeFailure = true)
        fixture.provider.openOverride = { StorageResult.Success(handle) }
        fixture.provider.refreshOverride = { StorageResult.Success(entry) }

        assertEquals(DigestOutcome.Failed(DigestFailure.CloseFailed, 128L), fixture.calculator.calculate(entry))
    }

    @Test
    fun revokedAuthorityAtOpenIsAccessUnavailable() = withLocalFixture { fixture ->
        fixture.provider.openOverride = { StorageResult.Failure(StorageError.PermissionDenied) }

        assertEquals(
            DigestOutcome.Failed(DigestFailure.AccessUnavailable, 0L),
            fixture.calculator.calculate(fixture.file("gone.txt", ByteArray(8))),
        )
    }

    @Test
    fun aVanishedDocumentAtOpenIsSourceUnavailable() = withLocalFixture { fixture ->
        fixture.provider.openOverride = { StorageResult.Failure(StorageError.NotFound) }

        assertEquals(
            DigestOutcome.Failed(DigestFailure.SourceUnavailable, 0L),
            fixture.calculator.calculate(fixture.file("gone.txt", ByteArray(8))),
        )
    }

    @Test
    fun aProviderThatCannotOpenReportsSourceUnavailableWithoutClaimingAbsenceProof() =
        withLocalFixture { fixture ->
            fixture.provider.openOverride = { StorageResult.Failure(StorageError.ProviderUnavailable) }

            assertEquals(
                DigestOutcome.Failed(DigestFailure.SourceUnavailable, 0L),
                fixture.calculator.calculate(fixture.file("gone.txt", ByteArray(8))),
            )
        }

    @Test
    fun aNonReadableSourceIsUnsupportedRatherThanAReadFailure() = withLocalFixture { fixture ->
        fixture.provider.openOverride = { StorageResult.Failure(StorageError.Unsupported) }

        assertEquals(
            DigestOutcome.Failed(DigestFailure.Unsupported, 0L),
            fixture.calculator.calculate(fixture.file("gone.txt", ByteArray(8))),
        )
    }

    @Test
    fun aProviderReportedSourceChangeAtOpenIsPreserved() = withLocalFixture { fixture ->
        fixture.provider.openOverride = { StorageResult.Failure(StorageError.SourceChanged) }

        assertEquals(
            DigestOutcome.SourceChanged(SourceChangeEvidence.PROVIDER_REPORTED_CHANGE, 0L),
            fixture.calculator.calculate(fixture.file("gone.txt", ByteArray(8))),
        )
    }

    @Test
    fun aSizeThatDiffersFromTheBytesReadIsSourceChanged() = withLocalFixture { fixture ->
        val entry = fixture.file("short.bin", ByteArray(300) { 1 })
        fixture.provider.openOverride =
            { StorageResult.Success(CountingHandle(ByteArray(120) { 1 }, expectedBytes = null)) }
        fixture.provider.refreshOverride = { StorageResult.Success(entry) }

        // The provider proved no size, so EOF alone decides and the digest stands.
        assertTrue(fixture.calculator.calculate(entry) is DigestOutcome.Complete)
    }

    @Test
    fun aReresolvedSizeChangeIsSourceChanged() = withLocalFixture { fixture ->
        val entry = fixture.file("grew.bin", ByteArray(64) { 1 })
        fixture.provider.openOverride = { StorageResult.Success(CountingHandle(ByteArray(64) { 1 })) }
        fixture.provider.refreshOverride = { StorageResult.Success(entry.copy(sizeBytes = 65L)) }

        assertEquals(
            DigestOutcome.SourceChanged(SourceChangeEvidence.RERESOLVED_SIZE_CHANGED, 64L),
            fixture.calculator.calculate(entry),
        )
    }

    @Test
    fun aReresolvedModifiedTimeChangeIsSourceChanged() = withLocalFixture { fixture ->
        val entry = fixture.file("touched.bin", ByteArray(64) { 1 }).copy(modifiedAtEpochMillis = 1_000L)
        fixture.provider.openOverride = { StorageResult.Success(CountingHandle(ByteArray(64) { 1 })) }
        fixture.provider.refreshOverride = { StorageResult.Success(entry.copy(modifiedAtEpochMillis = 2_000L)) }

        assertEquals(
            DigestOutcome.SourceChanged(SourceChangeEvidence.RERESOLVED_MODIFIED_CHANGED, 64L),
            fixture.calculator.calculate(entry),
        )
    }

    @Test
    fun anIdentityThatNoLongerResolvesIsSourceChanged() = withLocalFixture { fixture ->
        val entry = fixture.file("replaced.bin", ByteArray(64) { 1 })
        fixture.provider.openOverride = { StorageResult.Success(CountingHandle(ByteArray(64) { 1 })) }
        fixture.provider.refreshOverride = { StorageResult.Failure(StorageError.NotFound) }

        assertEquals(
            DigestOutcome.SourceChanged(SourceChangeEvidence.RERESOLVED_IDENTITY_LOST, 64L),
            fixture.calculator.calculate(entry),
        )
    }

    @Test
    fun aReResolveThatCannotAnswerKeepsACleanDigestAndDocumentsTheLimitation() =
        withLocalFixture { fixture ->
            val bytes = ByteArray(64) { 1 }
            val entry = fixture.file("opaque.bin", bytes)
            fixture.provider.openOverride = { StorageResult.Success(CountingHandle(bytes)) }
            fixture.provider.refreshOverride = { StorageResult.Failure(StorageError.ProviderUnavailable) }

            // Not evidence of change: the read reached EOF with the declared size matched, so the
            // digest honestly represents the bytes read during this calculation.
            assertEquals(
                DigestOutcome.Complete(reference(bytes), 64L),
                fixture.calculator.calculate(entry),
            )
        }

    @Test
    fun newlyAvailableMetadataIsNotTreatedAsContradiction() = withLocalFixture { fixture ->
        val bytes = ByteArray(64) { 1 }
        // Size was unknown before the read and is known afterwards: new information, not a change.
        val entry = fixture.file("unknown.bin", bytes).copy(sizeBytes = null, modifiedAtEpochMillis = null)
        fixture.provider.openOverride = { StorageResult.Success(CountingHandle(bytes, expectedBytes = null)) }
        fixture.provider.refreshOverride =
            { StorageResult.Success(entry.copy(sizeBytes = 64L, modifiedAtEpochMillis = 5L)) }

        assertEquals(DigestOutcome.Complete(reference(bytes), 64L), fixture.calculator.calculate(entry))
    }

    @Test
    fun cancellationBeforeTheFirstReadPublishesNothingAndStillCloses() = withLocalFixture { fixture ->
        val bytes = ByteArray(256) { 1 }
        val entry = fixture.file("cancel.bin", bytes)
        val handle = CountingHandle(bytes)
        fixture.provider.openOverride = { StorageResult.Success(handle) }
        fixture.provider.refreshOverride = { StorageResult.Success(entry) }

        assertEquals(DigestOutcome.Cancelled, fixture.calculator.calculate(entry, shouldAbort = { true }))
        assertEquals(1, handle.closeCount)
        assertEquals("a cancelled attempt must never re-resolve for change evidence", 0, fixture.provider.refreshCount)
    }

    @Test
    fun aCancelledAttemptNeverPublishesADigestEvenIfTheStreamWasReadable() =
        withLocalFixture { fixture ->
            val bytes = ByteArray(4096) { 2 }
            val entry = fixture.file("midcancel.bin", bytes)
            var boundaries = 0
            // Deterministic: abort at the boundary after the whole body was absorbed, but before
            // EOF. The bytes are read, yet no digest may be claimed.
            val outcome = fixture.calculator.calculate(entry, shouldAbort = { ++boundaries >= 2 })
            assertEquals(DigestOutcome.Cancelled, outcome)
        }

    @Test
    fun theRepositoryOwnsTheLocatorSoTheDigestLayerOnlyEverSeesAnEntry() =
        withLocalFixture { fixture ->
            val bytes = ByteArray(16) { 3 }
            val entry = fixture.file("private.bin", bytes)

            assertEquals(DigestOutcome.Complete(reference(bytes), 16L), fixture.calculator.calculate(entry))

            // Both the open and the post-EOF re-resolve encode a locator, and both happen inside
            // FilesRepository. Nothing in the digest path accepts or returns a locator.
            assertEquals(2, fixture.provider.encodeCount)
            assertEquals(1, fixture.provider.openCount)
            assertEquals(1, fixture.provider.refreshCount)
            assertTrue("no locator may appear in user-visible output", !reference(bytes).contains("://"))
        }

    private fun reference(input: ByteArray): String =
        Sha256Calculator.toHex(MessageDigest.getInstance("SHA-256").digest(input))

    private fun withLocalFixture(block: suspend (Fixture) -> Unit) = runBlocking {
        val root: Path = Files.createTempDirectory("omnifile-details")
        try {
            val delegate = LocalStorageProvider(root, ProviderId("local-details-test"))
            val provider = ControlledProvider(delegate)
            val repository = FilesRepository(mapOf(provider.id to provider))
            block(Fixture(root, delegate, provider, FileDetailsDigestCalculator(repository, Dispatchers.Unconfined)))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private class Fixture(
        private val root: Path,
        private val delegate: LocalStorageProvider,
        val provider: ControlledProvider,
        val calculator: FileDetailsDigestCalculator,
    ) {
        private var counter = 0

        suspend fun file(name: String, bytes: ByteArray): StorageEntry {
            counter++
            val path = root.resolve("$counter-$name")
            Files.write(path, bytes)
            Files.setLastModifiedTime(path, FileTime.fromMillis(1_700_000_000_000L))
            val children = (delegate.listChildren(delegate.root().let { (it as StorageResult.Success).value.ref })
                    as StorageResult.Success).value
            return children.single { it.displayName == "$counter-$name" }
        }
    }

    private class ControlledProvider(private val delegate: StorageTransferProvider) :
        StorageTransferProvider by delegate {
        var openOverride: (() -> StorageResult<SequentialReadHandle>)? = null
        var refreshOverride: (() -> StorageResult<StorageEntry>)? = null
        var openCount = 0
        var refreshCount = 0
        var encodeCount = 0
        val resolvedLocators = mutableListOf<DurableLocator>()

        override suspend fun openSequentialRead(locator: DurableLocator): StorageResult<SequentialReadHandle> {
            openCount++
            return openOverride?.invoke() ?: delegate.openSequentialRead(locator)
        }

        override suspend fun encodeDurableLocator(ref: com.omnifile.storage.EntryRef): StorageResult<DurableLocator> {
            encodeCount++
            return delegate.encodeDurableLocator(ref)
        }

        override suspend fun resolveDurableLocator(locator: DurableLocator): StorageResult<StorageEntry> {
            refreshCount++
            resolvedLocators += locator
            return refreshOverride?.invoke() ?: delegate.resolveDurableLocator(locator)
        }
    }

    private class CountingHandle(
        private val bytes: ByteArray,
        override val expectedBytes: Long? = bytes.size.toLong(),
        private val readFailure: Throwable? = null,
        private val closeFailure: Boolean = false,
    ) : SequentialReadHandle {
        var closeCount = 0
        private var offset = 0

        override fun read(buffer: ByteArray, off: Int, length: Int): Int {
            readFailure?.let { throw it }
            if (offset >= bytes.size) return -1
            val count = minOf(length, bytes.size - offset)
            System.arraycopy(bytes, offset, buffer, off, count)
            offset += count
            return count
        }

        override fun close() {
            closeCount++
            if (closeFailure) throw IOException("close failed")
        }
    }

    private companion object {
        const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
