package com.omnifile.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.MessageDigest

/**
 * Host contract for the digest core: standard vectors, streaming boundaries, progress honesty,
 * close ownership, cancellation, and the rule that no partial digest is ever representable.
 *
 * Concurrency is coordinated with explicit latches and counters, never sleeps.
 */
class Sha256CalculatorTest {
    @Test
    fun emptyInputProducesTheStandardEmptyDigest() {
        assertEquals(DigestOutcome.Complete(EMPTY_SHA256, 0L), outcomeOf(ByteArray(0)))
    }

    @Test
    fun emptyInputStillClosesItsSourceExactlyOnce() {
        val source = FakeSource(ByteArray(0))
        digest(source)
        assertEquals(1, source.closeCount)
    }

    @Test
    fun smallKnownVectorMatchesTheReferenceDigest() {
        assertEquals(DigestOutcome.Complete(ABC_SHA256, 3L), outcomeOf("abc".toByteArray()))
    }

    @Test
    fun digestIsLowercaseHexOfExactly64Characters() {
        val hex = digestOf("abc".toByteArray())
        assertEquals(64, hex.length)
        assertTrue(hex.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun digestCoversInputSpanningTwoShaBlocks() {
        val input = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".toByteArray()
        assertEquals(DigestOutcome.Complete(TWO_BLOCK_SHA256, 56L), outcomeOf(input))
    }

    @Test
    fun digestCoversInputSpanningFourShaBlocks() {
        val input = ("abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmn" +
                "hijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu").toByteArray()
        assertEquals(112, input.size.toLong())
        assertEquals(DigestOutcome.Complete(FOUR_BLOCK_SHA256, 112L), outcomeOf(input))
    }

    @Test
    fun inputLargerThanOneBufferMatchesAWholeFileDigest() {
        // Comfortably beyond the 64 KiB production buffer.
        val input = ByteArray(Sha256Calculator.BUFFER_BYTES * 3 + 1237) { (it % 251).toByte() }
        val expected = reference(input)
        assertEquals(expected, digestOf(input, bufferBytes = Sha256Calculator.BUFFER_BYTES))
        assertEquals(expected, digestOf(input, bufferBytes = 7))
    }

    @Test
    fun everyBufferBoundaryProducesTheSameDigest() {
        val input = ByteArray(600) { (it * 31).toByte() }
        val expected = reference(input)
        for (buffer in listOf(1, 2, 3, 63, 64, 65, 127, 128, 599, 600, 601, 4096)) {
            assertEquals("buffer=$buffer", expected, digestOf(input, bufferBytes = buffer))
        }
    }

    @Test
    fun aSourceThatReturnsShortChunksIsDigestedIdentically() {
        val input = ByteArray(3000) { (it % 97).toByte() }
        val expected = reference(input)
        listOf(1, 3, 17, 512, 3000).forEach { chunk ->
            val outcome = Sha256Calculator.calculate(
                source = FakeSource(input, chunk = chunk),
                bufferBytes = 128,
            )
            assertEquals(expected, (outcome as DigestOutcome.Complete).hex)
        }
    }

    @Test
    fun theDigestIsAFunctionOfStreamedBytesOnlyNotOfDeclaredMetadata() {
        val input = "same bytes".toByteArray()
        val withDeclaredSize = (Sha256Calculator.calculate(
            FakeSource(
                input,
                declaredBytes = input.size.toLong()
            )
        ) as DigestOutcome.Complete).hex
        val withUnprovenSize =
            (Sha256Calculator.calculate(FakeSource(input, declaredBytes = null)) as DigestOutcome.Complete).hex
        val withMislabelledSizeSource = FakeSource(input, declaredBytes = 10_000L)
        // A mislabelled source never completes, so it can never leak its metadata into a digest.
        assertEquals(
            DigestOutcome.SourceChanged(SourceChangeEvidence.READ_LENGTH_MISMATCH, input.size.toLong()),
            digest(withMislabelledSizeSource)
        )
        assertEquals(withDeclaredSize, withUnprovenSize)
        assertEquals(reference(input), withDeclaredSize)
    }

    @Test
    fun zeroLengthSourceCompletesWithoutDividingByZero() {
        val outcome = Sha256Calculator.calculate(FakeSource(ByteArray(0), declaredBytes = 0L))
        assertEquals(DigestOutcome.Complete(EMPTY_SHA256, 0L), outcome)
    }

    @Test
    fun progressIsMonotonicNeverNegativeAndNeverExceedsTheDeclaredSize() {
        val input = ByteArray(5000) { 1 }
        val ticks = mutableListOf<DigestProgress>()
        Sha256Calculator.calculate(
            source = FakeSource(input, chunk = 128),
            bufferBytes = 64,
            onProgress = { ticks += it },
        )
        assertTrue(ticks.isNotEmpty())
        var previous = 0L
        ticks.forEach { tick ->
            assertTrue("progress must never decrease", tick.bytesRead >= previous)
            assertTrue("progress must never be negative", tick.bytesRead >= 0)
            assertTrue("progress must never exceed declared size", tick.bytesRead <= input.size)
            assertEquals(input.size.toLong(), tick.expectedBytes)
            previous = tick.bytesRead
        }
        assertEquals(input.size.toLong(), previous)
    }

    @Test
    fun unknownExpectedSizeReportsIndeterminateProgressRatherThanAFakeExpectation() {
        val ticks = mutableListOf<DigestProgress>()
        Sha256Calculator.calculate(
            source = FakeSource(ByteArray(300) { 2 }, declaredBytes = null, chunk = 64),
            bufferBytes = 64,
            onProgress = { ticks += it },
        )
        assertTrue(ticks.isNotEmpty())
        assertTrue(ticks.all { it.expectedBytes == null })
    }

    @Test
    fun negativeDeclaredSizeIsTreatedAsUnproven() {
        val input = ByteArray(64) { 7 }
        val outcome = Sha256Calculator.calculate(FakeSource(input, declaredBytes = -1L))
        assertEquals(DigestOutcome.Complete(reference(input), 64L), outcome)
    }

    @Test
    fun readingMoreThanTheDeclaredSizeIsSourceChangedNotComplete() {
        val input = ByteArray(200) { 3 }
        val outcome = digest(FakeSource(input, declaredBytes = 100L))
        assertEquals(DigestOutcome.SourceChanged(SourceChangeEvidence.READ_LENGTH_MISMATCH, 200L), outcome)
    }

    @Test
    fun readingFewerBytesThanTheDeclaredSizeIsSourceChangedNotComplete() {
        val input = ByteArray(100) { 3 }
        // The source stops early: 100 bytes declared, only 40 actually readable.
        val truncated = FakeSource(input, declaredBytes = 100L).apply { limit = 40 }
        assertEquals(
            DigestOutcome.SourceChanged(SourceChangeEvidence.READ_LENGTH_MISMATCH, 40L),
            digest(truncated),
        )
    }

    @Test
    fun aMidReadFailureDiscardsThePartialDigest() {
        // The source fails on its fifth 64-byte read, after 256 bytes were absorbed.
        val source = FakeSource(ByteArray(1000) { 9 }, chunk = 64, failAfter = 256)
        val outcome = digest(source)
        assertEquals(DigestOutcome.Failed(DigestFailure.ReadFailed, 256L), outcome)
        assertEquals(1, source.closeCount)
    }

    @Test
    fun aVanishedSourceMidReadIsSourceUnavailableNotGenericIoFailure() {
        val source = object : DigestSource {
            override val declaredBytes: Long? = null
            var closed = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                throw java.io.FileNotFoundException("gone")

            override fun close() {
                closed++
            }
        }
        assertEquals(DigestOutcome.Failed(DigestFailure.SourceUnavailable, 0L), digest(source))
        assertEquals(1, source.closed)
    }

    @Test
    fun aRevokedAuthorityMidReadIsAccessUnavailable() {
        val source = object : DigestSource {
            override val declaredBytes: Long? = null
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw SecurityException()
            override fun close() = Unit
        }
        assertEquals(DigestOutcome.Failed(DigestFailure.AccessUnavailable, 0L), digest(source))
    }

    @Test
    fun cancellationBeforeTheFirstReadProducesNoDigest() {
        val source = FakeSource(ByteArray(500) { 4 })
        val outcome = Sha256Calculator.calculate(source, shouldAbort = { true })
        assertEquals(DigestOutcome.Cancelled, outcome)
        assertEquals("no byte may be read after cancellation wins", 0, source.offset)
        assertEquals(1, source.closeCount)
    }

    @Test
    fun cancellationMidReadDiscardsEverythingAlreadyDigested() {
        val source = FakeSource(ByteArray(4096) { 5 }, chunk = 64)
        var reads = 0
        val outcome = Sha256Calculator.calculate(
            source = source,
            bufferBytes = 64,
            onProgress = { reads++ },
            // Deterministic: abort exactly at the fourth boundary.
            shouldAbort = { reads >= 4 },
        )
        assertEquals(DigestOutcome.Cancelled, outcome)
        assertEquals(4, reads)
        assertEquals(1, source.closeCount)
    }

    @Test
    fun theSourceIsClosedExactlyOnceOnSuccess() {
        val source = FakeSource(ByteArray(2048) { 6 })
        digest(source)
        assertEquals(1, source.closeCount)
    }

    @Test
    fun theSourceIsClosedExactlyOnceOnCancellation() {
        val source = FakeSource(ByteArray(2048) { 6 })
        Sha256Calculator.calculate(source, shouldAbort = { true })
        assertEquals(1, source.closeCount)
    }

    @Test
    fun aFailedClosePreventsAnOtherwiseCompleteDigest() {
        val input = ByteArray(128) { 8 }
        val source = FakeSource(input, closeFailure = IOException("close failed"))
        val outcome = digest(source)
        // COMPLETE asserts ownership was released, so a failed close must withdraw it.
        assertEquals(DigestOutcome.Failed(DigestFailure.CloseFailed, 128L), outcome)
        assertEquals(1, source.closeCount)
    }

    @Test
    fun aFailedCloseAfterCancellationStaysCancelled() {
        val source = FakeSource(ByteArray(128) { 8 }, closeFailure = IOException("close failed"))
        assertEquals(DigestOutcome.Cancelled, Sha256Calculator.calculate(source, shouldAbort = { true }))
        assertEquals(1, source.closeCount)
    }

    @Test
    fun aFailedCloseOutranksSourceChangeBecauseReleaseIsTheStrongerDoubt() {
        // The contract orders "ownership released" before the source-change check, so an
        // unproven release is the more serious claim and wins.
        val source = FakeSource(ByteArray(500) { 8 }, declaredBytes = 10L, closeFailure = IOException())
        assertEquals(
            DigestOutcome.Failed(DigestFailure.CloseFailed, 500L),
            digest(source),
        )
        assertEquals(1, source.closeCount)
    }

    @Test
    fun sourceChangeSurvivesASuccessfulClose() {
        val source = FakeSource(ByteArray(500) { 8 }, declaredBytes = 10L)
        assertEquals(
            DigestOutcome.SourceChanged(SourceChangeEvidence.READ_LENGTH_MISMATCH, 500L),
            digest(source),
        )
        assertEquals(1, source.closeCount)
    }

    @Test
    fun aZeroLengthBufferIsRejectedRatherThanLoopingForever() {
        val failure = runCatching { Sha256Calculator.calculate(FakeSource(ByteArray(0)), bufferBytes = 0) }
        assertTrue(failure.isFailure)
    }

    @Test
    fun progressReportsOnlyBytesAlreadyAbsorbedIntoTheDigest() {
        val input = ByteArray(256) { 1 }
        val seen = mutableListOf<Long>()
        val outcome = Sha256Calculator.calculate(
            source = FakeSource(input, chunk = 32),
            bufferBytes = 32,
            onProgress = { seen += it.bytesRead },
        )
        assertEquals(DigestOutcome.Complete(reference(input), 256L), outcome)
        // Each tick follows the digest absorbing the bytes, so the digest of the prefix is
        // always reproducible from what has already been reported.
        assertEquals(listOf(32L, 64L, 96L, 128L, 160L, 192L, 224L, 256L), seen)
    }

    @Test
    fun theHexEncoderIsLocaleIndependentForHighBytes() {
        val digest = MessageDigest.getInstance("SHA-256").digest("abc".toByteArray())
        assertEquals(ABC_SHA256, Sha256Calculator.toHex(digest))
        assertEquals("", Sha256Calculator.toHex(ByteArray(0)))
    }

    private fun digest(source: DigestSource): DigestOutcome =
        Sha256Calculator.calculate(source, bufferBytes = 64)

    private fun outcomeOf(input: ByteArray): DigestOutcome =
        Sha256Calculator.calculate(FakeSource(input), bufferBytes = 64)

    private fun digestOf(input: ByteArray, bufferBytes: Int = 64): String {
        val outcome = Sha256Calculator.calculate(
            source = FakeSource(input),
            bufferBytes = bufferBytes,
        )
        return (outcome as DigestOutcome.Complete).hex
    }

    private fun reference(input: ByteArray): String =
        Sha256Calculator.toHex(MessageDigest.getInstance("SHA-256").digest(input))

    /** Deterministic byte source: no timing, no threads, no sleeps. */
    private class FakeSource(
        private val bytes: ByteArray,
        override val declaredBytes: Long? = bytes.size.toLong(),
        private val chunk: Int = Int.MAX_VALUE,
        private val failAfter: Int = -1,
        private val closeFailure: Throwable? = null,
    ) : DigestSource {
        var offset = 0
        var closeCount = 0
        var limit = bytes.size

        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            if (failAfter >= 0 && offset >= failAfter) throw IOException("synthetic read failure")
            if (offset >= limit) return -1
            val count = minOf(len, chunk, limit - offset)
            System.arraycopy(bytes, offset, buffer, off, count)
            offset += count
            return count
        }

        override fun close() {
            closeCount++
            closeFailure?.let { throw it }
        }
    }

    private companion object {
        const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        const val ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        const val TWO_BLOCK_SHA256 = "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1"
        const val FOUR_BLOCK_SHA256 = "cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1"
    }
}
