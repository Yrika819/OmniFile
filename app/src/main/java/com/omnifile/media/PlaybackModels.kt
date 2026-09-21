package com.omnifile.media

import com.omnifile.storage.ProviderId
import com.omnifile.storage.SeekSupport
import com.omnifile.storage.StorageEntry
import java.security.MessageDigest

/**
 * Provider-neutral identity of the single current playback item.
 *
 * [mediaId] is the session-visible stable identity: provider ID plus the
 * provider-scoped [com.omnifile.storage.EntryRef.identityKey]. Raw filesystem
 * paths and raw SAF URIs are transport details and never appear here.
 * [displayName] is presentational only; renaming a file does not change identity.
 */
data class PlaybackItem(
    val mediaId: String,
    val providerId: ProviderId,
    val entryIdentityKey: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val sourceLabel: String,
) {
    companion object {
        fun of(entry: StorageEntry, sourceLabel: String): PlaybackItem {
            // EntryRef.identityKey may contain a local absolute path or a raw SAF URI.
            // Keep that transport identity inside the provider boundary; session-visible
            // media identity is an opaque, provider-scoped digest instead.
            val opaqueEntryIdentity = sha256Hex(entry.ref.identityKey)
            return PlaybackItem(
                mediaId = "${entry.ref.providerId.value}\u0000$opaqueEntryIdentity",
                providerId = entry.ref.providerId,
                entryIdentityKey = opaqueEntryIdentity,
                displayName = entry.displayName,
                mimeType = entry.mimeType,
                sizeBytes = entry.sizeBytes,
                sourceLabel = sourceLabel,
            )
        }

        private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

enum class PlaybackStatus {
    NO_MEDIA,
    BUFFERING,
    READY,
    ENDED,
    ERROR,
}

/** User-mappable playback failures; raw Media3 exceptions never reach the UI. */
sealed interface PlaybackError {
    /** The persisted SAF grant or read permission is not currently valid. */
    data object PermissionUnavailable : PlaybackError

    data object SourceNotFound : PlaybackError

    data object ProviderUnavailable : PlaybackError

    /** The container/codec is not supported by the playback stack. */
    data object UnsupportedMedia : PlaybackError

    data object DecodeFailure : PlaybackError

    /** Read/I/O failure after playback was resolved. */
    data object ReadFailure : PlaybackError

    /** The source identity no longer matches (renamed/replaced/stale). */
    data object SourceChanged : PlaybackError

    data class Unknown(val detail: String? = null) : PlaybackError
}

/**
 * The one production playback state source for the whole app.
 *
 * [durationMs] is null when the source duration is unknown; unknown duration
 * must never be rendered as a 0:00 total. [seekSupport] is the per-item probe
 * result; a functional seek control requires [SeekSupport.SEEKABLE] plus a
 * known duration.
 */
data class NowPlayingState(
    val status: PlaybackStatus,
    val item: PlaybackItem?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long?,
    val seekSupport: SeekSupport,
    val error: PlaybackError?,
) {
    val seekAllowed: Boolean
        get() = status == PlaybackStatus.READY &&
                seekSupport == SeekSupport.SEEKABLE &&
                durationMs != null && durationMs > 0

    companion object {
        val NoMedia = NowPlayingState(
            status = PlaybackStatus.NO_MEDIA,
            item = null,
            isPlaying = false,
            positionMs = 0L,
            durationMs = null,
            seekSupport = SeekSupport.UNKNOWN,
            error = null,
        )
    }
}

/** Media3-free player phases so state reduction stays host-testable. */
enum class PlayerPhase {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
}

object PlaybackStateReducer {
    fun reduce(
        phase: PlayerPhase,
        hasItem: Boolean,
        hasError: Boolean,
    ): PlaybackStatus = when {
        hasError -> PlaybackStatus.ERROR
        !hasItem -> PlaybackStatus.NO_MEDIA
        phase == PlayerPhase.BUFFERING -> PlaybackStatus.BUFFERING
        phase == PlayerPhase.READY -> PlaybackStatus.READY
        phase == PlayerPhase.ENDED -> PlaybackStatus.ENDED
        else -> PlaybackStatus.BUFFERING
    }
}

/** Maps provider/storage failures to the playback error model (host-testable). */
object PlaybackErrorMapper {
    fun fromStorageError(error: com.omnifile.storage.StorageError): PlaybackError = when (error) {
        is com.omnifile.storage.StorageError.PermissionDenied -> PlaybackError.PermissionUnavailable
        is com.omnifile.storage.StorageError.NotFound -> PlaybackError.SourceNotFound
        is com.omnifile.storage.StorageError.ProviderUnavailable -> PlaybackError.ProviderUnavailable
        is com.omnifile.storage.StorageError.StaleReference -> PlaybackError.SourceChanged
        is com.omnifile.storage.StorageError.SourceChanged -> PlaybackError.SourceChanged
        is com.omnifile.storage.StorageError.Unsupported -> PlaybackError.UnsupportedMedia
        is com.omnifile.storage.StorageError.IoFailure -> PlaybackError.ReadFailure
        is com.omnifile.storage.StorageError.Cancelled -> PlaybackError.Unknown("cancelled")
        else -> PlaybackError.Unknown(error.toString())
    }
}
