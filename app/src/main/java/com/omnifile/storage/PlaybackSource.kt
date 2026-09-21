package com.omnifile.storage

/**
 * Truthful per-item seek capability of a resolved playback source.
 *
 * Readable never implies seekable: a SAF provider may hand out a pipe-backed
 * descriptor that only supports sequential reads.
 */
enum class SeekSupport {
    /** Random access is proven for this item (e.g. regular file descriptor). */
    SEEKABLE,

    /** Only sequential reads are proven; seeking must not be offered. */
    NOT_SEEKABLE,

    /** The capability probe could not decide; playback may still work, seeking is not offered. */
    UNKNOWN,
}

/**
 * A resolved, provider-specific playback transport for one entry.
 *
 * [transportUri] is a transient transport detail (`file://` or `content://`).
 * It is never part of durable playback identity; identity remains the
 * provider-scoped [EntryRef] of the entry this source was resolved from.
 */
data class PlaybackSource(
    val transportUri: String,
    val seekSupport: SeekSupport,
    /** Human-readable origin context for the Now Playing surface (never a raw URI/path). */
    val sourceLabel: String,
)

/**
 * Optional playback surface; [StorageProvider] remains browse/mutation compatible.
 *
 * Implementations resolve an entry to a provider-specific transport without
 * leaking raw paths/URIs into the generic media identity model and without
 * claiming codec support they cannot prove.
 */
interface PlaybackSourceProvider : StorageProvider {
    suspend fun resolvePlaybackSource(entry: StorageEntry): StorageResult<PlaybackSource>
}
