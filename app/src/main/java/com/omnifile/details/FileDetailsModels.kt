package com.omnifile.details

/**
 * Metadata the current provider model already knows.
 *
 * A null field is a truthful "Not available". Nothing here is inferred from a filename
 * extension, a MIME guess, or a filesystem probe, and no field carries a path, a URI,
 * a document id, or an internal locator.
 */
data class FileDetailsMetadata(
    val displayName: String,
    /** Human-facing origin such as "Local storage". Never the provider id or the tree URI. */
    val sourceLabel: String,
    val sizeBytes: Long?,
    val modifiedAtEpochMillis: Long?,
    val mimeType: String?,
)

/** User-facing reason a digest is absent. Distinct causes are never collapsed into IOException text. */
enum class FileDetailsFailure {
    /** Persisted authority is missing or revoked. */
    ACCESS_UNAVAILABLE,

    /** The provider or the entry disappeared. */
    SOURCE_UNAVAILABLE,

    /**
     * The storage provider could not answer right now. The file may well still exist, so this is
     * kept separate from [SOURCE_UNAVAILABLE] and offers Retry.
     */
    PROVIDER_UNAVAILABLE,

    /** The source may not be the bytes that were read, so no digest is claimed. */
    SOURCE_CHANGED,

    /** The stream failed. */
    READ_FAILURE,

    /** This provider cannot sequentially read the entry. */
    UNSUPPORTED,
}

/** Digest state for one File Details surface. At most one attempt is live at a time. */
sealed interface DigestUiState {
    data object Idle : DigestUiState

    /** [bytesRead] only grows. [expectedBytes] is null when the provider proved no size. */
    data class Calculating(val bytesRead: Long, val expectedBytes: Long?) : DigestUiState

    /**
     * Stop was requested. A provider that is non-cooperative inside one read may still be
     * holding its handle, so this state never claims cancellation physically finished.
     */
    data object Cancelling : DigestUiState

    /** Only reachable after the whole stream was digested and ownership was released. */
    data class Complete(val hex: String) : DigestUiState

    data object Cancelled : DigestUiState

    data class Failed(val failure: FileDetailsFailure) : DigestUiState
}

/**
 * Retry is offered only where it meaningfully re-opens through the existing authorized
 * provider route. Re-deriving access or relocating a vanished file is not that route.
 *
 * A temporarily unavailable provider qualifies: re-attempting the same route is exactly the
 * action that can succeed once the provider recovers.
 */
val DigestUiState.Failed.isRetryable: Boolean
    get() = failure == FileDetailsFailure.READ_FAILURE ||
            failure == FileDetailsFailure.PROVIDER_UNAVAILABLE

/** Fraction complete in 0f..1f, or null for indeterminate progress. */
val DigestUiState.Calculating.fraction: Float?
    get() {
        val expected = expectedBytes ?: return null
        // A zero-length source is fully read the moment it opens, and must not divide by zero.
        if (expected == 0L) return 1f
        if (bytesRead <= 0L) return 0f
        // Clamped so a source that grew mid-read can never render above 100%.
        return (bytesRead.toDouble() / expected.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }

sealed interface FileDetailsUiState {
    /**
     * Source ownership is gone, so the surface must return to Files instead of guessing a
     * source from a restored route or a stale serialized locator.
     */
    data object Unavailable : FileDetailsUiState

    data class Metadata(
        val metadata: FileDetailsMetadata,
        /**
         * Whether this provider can sequentially read the entry. Metadata stays visible and
         * truthful when it cannot; only the digest action is capability-gated.
         */
        val hashingSupported: Boolean,
        val digest: DigestUiState = DigestUiState.Idle,
    ) : FileDetailsUiState
}
