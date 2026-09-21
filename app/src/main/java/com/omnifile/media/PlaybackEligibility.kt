package com.omnifile.media

import com.omnifile.storage.EntryKind
import com.omnifile.storage.StorageCapability
import com.omnifile.storage.StorageEntry

/** Why a single entry cannot be offered a Play action. */
enum class PlaybackIneligibility {
    DIRECTORY,
    NOT_READABLE,
    UNSUPPORTED_TYPE,
}

sealed interface PlaybackEligibility {
    data object Eligible : PlaybackEligibility
    data class Ineligible(val reason: PlaybackIneligibility) : PlaybackEligibility
}

/**
 * Conservative single-item playback eligibility.
 *
 * Requires a readable regular file and audio confidence: a trustworthy provider
 * MIME type with the audio prefix, or — because some providers omit MIME metadata — a small
 * documented filename-extension fallback. This is not a codec guarantee; decode
 * support remains Media3's runtime decision and failures map to
 * [PlaybackError.UnsupportedMedia]/[PlaybackError.DecodeFailure].
 */
object PlaybackEligibilityEvaluator {
    /** Extension fallback used only when the provider reports no audio MIME type. */
    private val audioExtensions = setOf(
        "flac", "mp3", "m4a", "aac", "wav", "ogg", "oga", "opus",
    )

    fun evaluate(entry: StorageEntry): PlaybackEligibility {
        if (entry.kind != EntryKind.FILE) {
            return PlaybackEligibility.Ineligible(PlaybackIneligibility.DIRECTORY)
        }
        if (StorageCapability.READ_SEQUENTIAL !in entry.capabilities) {
            return PlaybackEligibility.Ineligible(PlaybackIneligibility.NOT_READABLE)
        }
        val mime = entry.mimeType
        if (mime != null) {
            return if (mime.startsWith("audio/")) {
                PlaybackEligibility.Eligible
            } else {
                PlaybackEligibility.Ineligible(PlaybackIneligibility.UNSUPPORTED_TYPE)
            }
        }
        val extension = entry.displayName.substringAfterLast('.', "").lowercase()
        return if (extension in audioExtensions) {
            PlaybackEligibility.Eligible
        } else {
            PlaybackEligibility.Ineligible(PlaybackIneligibility.UNSUPPORTED_TYPE)
        }
    }
}

fun StorageEntry.playbackEligibility(): PlaybackEligibility = PlaybackEligibilityEvaluator.evaluate(this)

val StorageEntry.isPlaybackEligible: Boolean
    get() = PlaybackEligibilityEvaluator.evaluate(this) is PlaybackEligibility.Eligible
