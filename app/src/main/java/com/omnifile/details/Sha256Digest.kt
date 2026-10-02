package com.omnifile.details

import java.security.MessageDigest

/**
 * Checked byte accounting for the digest core.
 *
 * Factored out so the boundary is directly testable without reading `Long.MAX_VALUE` bytes, and
 * so the overflow rule is stated once. A stream cannot realistically reach this, but a wrapped
 * total would publish a negative byte count and a digest of an unknown byte sequence, so the
 * addition is rejected instead: null means "this count cannot be represented" and the caller
 * fails the attempt rather than emitting a decreasing or wrapped total.
 */
internal fun checkedAddBytes(current: Long, count: Int): Long? {
    if (count < 0) return null
    val delta = count.toLong()
    // Written as a subtraction guard rather than a sum so it cannot itself overflow.
    if (current < 0L || delta > Long.MAX_VALUE - current) return null
    return current + delta
}

/** Reliable evidence that the source moved under a completed read, so the digest is discarded. */
enum class SourceChangeEvidence {
    /** Bytes actually read differ from the size the handle declared before the read. */
    READ_LENGTH_MISMATCH,

    /**
     * Bytes actually read differ from a size that was already known for the selected entry
     * before the read. This is reliable pre-read evidence, so it holds even when the provider
     * cannot re-answer the entry afterwards.
     */
    SELECTED_SIZE_MISMATCH,

    /**
     * Optional provider version evidence differed across the read, which detects a same-path
     * replacement that preserved size and modified time. Only meaningful when the provider
     * could prove a token on both sides.
     */
    VERSION_TOKEN_CHANGED,

    /** The same provider entry reported a different size after EOF. */
    RERESOLVED_SIZE_CHANGED,

    /** The same provider reported a different modified time after EOF. */
    RERESOLVED_MODIFIED_CHANGED,

    /** The selected provider-scoped identity no longer resolves to the same logical item. */
    RERESOLVED_IDENTITY_LOST,

    /** The provider itself reported a source change while the read was being opened. */
    PROVIDER_REPORTED_CHANGE,
}

/**
 * Why a digest is absent. Every value is a closed taxonomy so the UI never has to render
 * exception text, a provider message, or a path.
 */
sealed interface DigestFailure {
    /** Persisted authority for this source is missing or revoked. */
    data object AccessUnavailable : DigestFailure

    /** The provider or the entry itself disappeared. */
    data object SourceUnavailable : DigestFailure

    /** This provider cannot sequentially read the entry, so no digest can be produced. */
    data object Unsupported : DigestFailure

    /** The stream failed part way through; whatever was digested is discarded. */
    data object ReadFailed : DigestFailure

    /** Ownership could not be released, so claiming COMPLETE would be untruthful. */
    data object CloseFailed : DigestFailure
}

/** Terminal result of one digest attempt. A partial digest is never representable. */
sealed interface DigestOutcome {
    /** The whole stream was digested and released, and no reliable change evidence was found. */
    data class Complete(val hex: String, val bytesRead: Long) : DigestOutcome

    /** Cancellation won before any complete digest existed. */
    data object Cancelled : DigestOutcome

    /** The digest is withheld because the source may not be the bytes that were read. */
    data class SourceChanged(val evidence: SourceChangeEvidence, val bytesRead: Long) : DigestOutcome

    /** The digest is withheld because the attempt failed. */
    data class Failed(val failure: DigestFailure, val bytesRead: Long) : DigestOutcome
}

/**
 * One progress tick. [expectedBytes] is null when the provider could not prove a size before
 * the read, which is reported as indeterminate progress rather than a fabricated percentage.
 */
data class DigestProgress(val bytesRead: Long, val expectedBytes: Long?)

/**
 * A byte source owned by exactly one caller for the duration of one digest attempt.
 *
 * Deliberately narrower than [com.omnifile.storage.SequentialReadHandle] so the digest core is
 * host-testable with no provider, no Android runtime, and no filesystem.
 */
interface DigestSource : AutoCloseable {
    /** Bytes the provider declared before reading, or null when it could not prove them. */
    val declaredBytes: Long?

    fun read(buffer: ByteArray, offset: Int, length: Int): Int
}

/**
 * Bounded-memory streaming SHA-256 over the exact bytes a source yields.
 *
 * The core never sees a filename, a locator, metadata, or a staged representation: it digests
 * only [DigestSource.read] output. Ownership rules:
 *
 * - This function is the single closer. It closes the source exactly once on every exit path,
 *   including a throwing [DigestSource.read].
 * - A close failure is never silently absorbed. It downgrades a would-be [DigestOutcome.Complete]
 *   to [DigestFailure.CloseFailed], because COMPLETE asserts that ownership was released.
 * - [shouldAbort] is polled at every read boundary. It reports cancellation or a stale
 *   generation. A non-cooperative provider can still block inside one [DigestSource.read], so
 *   abort is honoured at the next boundary and never reported as instant.
 */
object Sha256Calculator {
    /** Fixed streaming buffer. 64 KiB keeps syscall overhead low without unbounded memory. */
    const val BUFFER_BYTES: Int = 64 * 1024

    /**
     * Consecutive zero-byte reads tolerated before a source is declared non-progressing. A pipe
     * backed SAF document can legitimately return 0, so a few are allowed; an unbounded run of
     * them would otherwise spin a core with no I/O, exactly as the Preview read loops guard.
     */
    const val MAX_CONSECUTIVE_NO_PROGRESS_READS: Int = 8

    private const val HEX = "0123456789abcdef"

    fun calculate(
        source: DigestSource,
        bufferBytes: Int = BUFFER_BYTES,
        onProgress: (DigestProgress) -> Unit = {},
        shouldAbort: () -> Boolean = { false },
    ): DigestOutcome {
        require(bufferBytes > 0) { "bufferBytes must be positive" }
        val digest = MessageDigest.getInstance("SHA-256")
        // One fixed buffer for the whole attempt. The file is never materialized.
        val buffer = ByteArray(bufferBytes)
        var expected: Long? = null
        var bytesRead = 0L
        var noProgressReads = 0
        var aborted: DigestOutcome? = null
        var closeFailed = false

        try {
            // Read inside the try so even a misbehaving provider property cannot leak the source.
            // A negative declared size is meaningless, so it counts as unproven rather than trusted.
            expected = source.declaredBytes?.takeIf { it >= 0 }
            read@ while (true) {
                if (shouldAbort()) {
                    aborted = DigestOutcome.Cancelled
                    break@read
                }
                val count = try {
                    source.read(buffer, 0, buffer.size)
                } catch (error: Throwable) {
                    aborted = DigestOutcome.Failed(classifyReadFailure(error), bytesRead)
                    break@read
                }
                if (count < 0) break@read
                if (count == 0) {
                    if (++noProgressReads > MAX_CONSECUTIVE_NO_PROGRESS_READS) {
                        aborted = DigestOutcome.Failed(DigestFailure.ReadFailed, bytesRead)
                        break@read
                    }
                    continue@read
                }
                noProgressReads = 0
                // Checked before the digest absorbs the bytes, so a count that cannot be
                // represented is never folded in and never published as progress.
                val advanced = checkedAddBytes(bytesRead, count)
                if (advanced == null) {
                    // Impossible accounting, so it is an impossible stream: refuse to describe
                    // it as a digest rather than wrap into a negative total.
                    aborted = DigestOutcome.Failed(DigestFailure.ReadFailed, bytesRead)
                    break@read
                }
                digest.update(buffer, 0, count)
                bytesRead = advanced
                // Reported after the digest absorbs the bytes, so a shown progress value
                // always corresponds to bytes already incorporated.
                onProgress(DigestProgress(bytesRead, expected))
            }
        } finally {
            try {
                source.close()
            } catch (_: Throwable) {
                closeFailed = true
            }
        }

        // An aborted attempt stays aborted: the user-facing truth is that no digest exists, and
        // a close problem does not change that. It is only COMPLETE that must not over-claim.
        aborted?.let { return it }
        if (closeFailed) return DigestOutcome.Failed(DigestFailure.CloseFailed, bytesRead)
        if (expected != null && bytesRead != expected) {
            return DigestOutcome.SourceChanged(SourceChangeEvidence.READ_LENGTH_MISMATCH, bytesRead)
        }
        return DigestOutcome.Complete(toHex(digest.digest()), bytesRead)
    }

    /** Lowercase hexadecimal, exactly 64 characters. Locale independent by construction. */
    fun toHex(digest: ByteArray): String {
        val out = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xff
            out.append(HEX[value ushr 4]).append(HEX[value and 0x0f])
        }
        return out.toString()
    }

    private fun classifyReadFailure(error: Throwable): DigestFailure = when (error) {
        // FileNotFoundException is an IOException, so absence must be tested first.
        is java.io.FileNotFoundException -> DigestFailure.SourceUnavailable
        is SecurityException -> DigestFailure.AccessUnavailable
        is java.io.IOException -> DigestFailure.ReadFailed
        else -> DigestFailure.ReadFailed
    }
}
