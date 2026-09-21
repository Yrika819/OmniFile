package com.omnifile.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.omnifile.storage.PlaybackSource
import com.omnifile.storage.PlaybackSourceProvider
import com.omnifile.storage.ProviderId
import com.omnifile.storage.SeekSupport
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * The single production playback entry point.
 *
 * The coordinator is process-scoped (owned by AppContainer): it survives
 * Activity recreation, owns exactly one [MediaController] connected to
 * [OmniFilePlaybackService], and never creates a player itself. All public
 * methods must be called on the main thread.
 */
class PlaybackCoordinator(
    private val appContext: Context,
    private val resolverFor: (ProviderId) -> PlaybackSourceProvider?,
    private val serviceClass: Class<*> = OmniFilePlaybackService::class.java,
    private val mainScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val _state = MutableStateFlow(NowPlayingState.NoMedia)
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var listenerAttached = false
    private val items = mutableMapOf<String, PlaybackItem>()
    private var seekSupportById = mutableMapOf<String, SeekSupport>()
    private var pollingJob: Job? = null
    private var playJob: Job? = null
    private var playRequestId = 0L
    private var expectedMediaId: String? = null
    private var controllerGeneration = 0L
    private var playerError: PlaybackError? = null

    /**
     * Plays one eligible audio entry, replacing the single current item.
     * Ineligible or unresolvable entries publish a truthful error state and
     * never fabricate a new playing identity.
     */
    fun play(entry: StorageEntry) {
        playJob?.cancel()
        val requestId = ++playRequestId
        clearPlayerForReplacement()
        when (val eligibility = entry.playbackEligibility()) {
            is PlaybackEligibility.Ineligible -> {
                val errorItem = PlaybackItem.of(entry, "")
                expectedMediaId = errorItem.mediaId
                val error = when (eligibility.reason) {
                    PlaybackIneligibility.DIRECTORY,
                    PlaybackIneligibility.UNSUPPORTED_TYPE,
                        -> PlaybackError.UnsupportedMedia

                    PlaybackIneligibility.NOT_READABLE -> PlaybackError.PermissionUnavailable
                }
                publishError(errorItem, error)
                return
            }

            PlaybackEligibility.Eligible -> Unit
        }
        val pendingItem = PlaybackItem.of(entry, "")
        expectedMediaId = pendingItem.mediaId
        _state.value = NowPlayingState(
            status = PlaybackStatus.BUFFERING,
            item = pendingItem,
            isPlaying = false,
            positionMs = 0L,
            durationMs = null,
            seekSupport = SeekSupport.UNKNOWN,
            error = null,
        )
        playJob = mainScope.launch {
            val resolver = resolverFor(entry.ref.providerId)
            if (!isCurrentPlayRequest(requestId)) return@launch
            if (resolver == null) {
                publishError(pendingItem, PlaybackError.ProviderUnavailable)
                return@launch
            }
            when (val resolved = withContext(Dispatchers.IO) {
                resolver.resolvePlaybackSource(entry)
            }) {
                is StorageResult.Failure -> {
                    if (isCurrentPlayRequest(requestId)) {
                        publishError(pendingItem, PlaybackErrorMapper.fromStorageError(resolved.error))
                    }
                }

                is StorageResult.Success -> startPlayback(
                    requestId,
                    PlaybackItem.of(entry, resolved.value.sourceLabel),
                    resolved.value,
                )
            }
        }
    }

    fun togglePlayPause() {
        val current = controller ?: return
        if (current.isPlaying) current.pause() else current.play()
    }

    /** Stops playback and clears the item; the service may then shut down per Media3 lifecycle. */
    fun stop() {
        playJob?.cancel()
        playJob = null
        ++playRequestId
        expectedMediaId = null
        clearPlayerForReplacement()
        if (controller == null) {
            controllerGeneration++
            controllerFuture?.cancel(true)
            controllerFuture = null
        }
        _state.value = NowPlayingState.NoMedia
    }

    /** Seek is only issued when the per-item probe proved SEEKABLE and the player agrees. */
    fun seekTo(positionMs: Long) {
        val current = controller ?: return
        val mediaId = current.currentMediaItem?.mediaId ?: return
        if (seekSupportById[mediaId] != SeekSupport.SEEKABLE) return
        if (!current.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return
        if (current.duration == C.TIME_UNSET) return
        current.seekTo(positionMs.coerceIn(0L, current.duration))
    }

    private suspend fun startPlayback(requestId: Long, item: PlaybackItem, source: PlaybackSource) {
        if (!isCurrentPlayRequest(requestId)) return
        items[item.mediaId] = item
        seekSupportById[item.mediaId] = source.seekSupport
        playerError = null
        _state.value = NowPlayingState(
            status = PlaybackStatus.BUFFERING,
            item = item,
            isPlaying = false,
            positionMs = 0L,
            durationMs = null,
            seekSupport = source.seekSupport,
            error = null,
        )
        val target = awaitController()
        if (!isCurrentPlayRequest(requestId)) return
        if (target == null) {
            publishError(item, PlaybackError.ProviderUnavailable)
            return
        }
        val mediaItem = MediaItem.Builder()
            .setMediaId(item.mediaId)
            .setUri(source.transportUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.displayName)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build(),
            )
            .build()
        if (!isCurrentPlayRequest(requestId)) return
        target.setMediaItem(mediaItem)
        target.prepare()
        target.play()
    }

    private fun isCurrentPlayRequest(requestId: Long): Boolean = requestId == playRequestId

    private suspend fun awaitController(): MediaController? {
        controller?.let { existing ->
            if (existing.isConnected) return existing
            existing.release()
            controller = null
            listenerAttached = false
            controllerFuture = null
        }
        val future = controllerFuture ?: MediaController.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, serviceClass)),
        ).buildAsync().also { controllerFuture = it }
        val generation = controllerGeneration
        return suspendCancellableCoroutine { continuation ->
            future.addListener({
                val connected = runCatching { future.get() }.getOrNull()
                if (generation != controllerGeneration || controllerFuture !== future) {
                    connected?.release()
                    if (continuation.isActive) continuation.resume(null)
                    return@addListener
                }
                if (connected != null) {
                    controller = connected
                    if (!listenerAttached) {
                        connected.addListener(playerListener)
                        listenerAttached = true
                    }
                } else {
                    // Do not cache a failed connection: a later play request may
                    // recover after a transient service/session failure.
                    controllerFuture = null
                }
                if (continuation.isActive) continuation.resume(connected)
            }, MoreExecutors.directExecutor())
        }
    }

    /** Test-only teardown; production playback lifetime is owned by the service. */
    fun releaseController() {
        playJob?.cancel()
        playJob = null
        ++playRequestId
        expectedMediaId = null
        pollingJob?.cancel()
        controllerGeneration++
        val currentController = controller
        controller = null
        listenerAttached = false
        currentController?.release()
        controllerFuture?.let { future ->
            if (!future.isDone) {
                future.cancel(true)
            } else if (currentController == null) {
                runCatching { future.get().release() }
            }
        }
        controllerFuture = null
    }


    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = publishFromPlayer()
        override fun onIsPlayingChanged(isPlaying: Boolean) = publishFromPlayer()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = publishFromPlayer()
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) = publishFromPlayer()

        override fun onPlayerError(error: PlaybackException) {
            val mediaId = controller?.currentMediaItem?.mediaId
            if (!isExpectedMediaId(mediaId)) return
            playerError = mapPlaybackError(error)
            val item = mediaId?.let(items::get)
            publishError(item, playerError!!)
        }
    }

    private fun publishFromPlayer() {
        val current = controller ?: return
        val mediaId = current.currentMediaItem?.mediaId
        // MediaController commands are asynchronous. Ignore callbacks from the
        // previous item (including the transient empty state from clearMediaItems)
        // while a newer request owns the coordinator state.
        if (!isExpectedMediaId(mediaId)) return
        val item = mediaId?.let(items::get)
        val phase = when (current.playbackState) {
            Player.STATE_IDLE -> PlayerPhase.IDLE
            Player.STATE_BUFFERING -> PlayerPhase.BUFFERING
            Player.STATE_READY -> PlayerPhase.READY
            Player.STATE_ENDED -> PlayerPhase.ENDED
            else -> PlayerPhase.IDLE
        }
        val status = PlaybackStateReducer.reduce(
            phase,
            hasItem = item != null,
            hasError = playerError != null,
        )
        val duration = current.duration.takeIf { it != C.TIME_UNSET && it >= 0L }
        _state.value = NowPlayingState(
            status = status,
            item = item,
            isPlaying = current.isPlaying,
            positionMs = current.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            seekSupport = mediaId?.let { seekSupportById[it] } ?: SeekSupport.UNKNOWN,
            error = playerError,
        )
        updatePolling(current.isPlaying && item != null && playerError == null)
    }

    private fun isExpectedMediaId(mediaId: String?): Boolean =
        mediaId != null && mediaId == expectedMediaId

    private fun clearPlayerForReplacement() {
        playerError = null
        pollingJob?.cancel()
        pollingJob = null
        controller?.let { current ->
            current.stop()
            current.clearMediaItems()
        }
    }

    private fun publishError(item: PlaybackItem?, error: PlaybackError) {
        pollingJob?.cancel()
        _state.value = NowPlayingState(
            status = PlaybackStatus.ERROR,
            item = item,
            isPlaying = false,
            positionMs = 0L,
            durationMs = null,
            seekSupport = item?.let { seekSupportById[it.mediaId] } ?: SeekSupport.UNKNOWN,
            error = error,
        )
    }

    private fun updatePolling(playing: Boolean) {
        if (playing) {
            if (pollingJob?.isActive == true) return
            pollingJob = mainScope.launch {
                while (isActive) {
                    delay(POSITION_POLL_MS)
                    publishFromPlayer()
                }
            }
        } else {
            pollingJob?.cancel()
            pollingJob = null
        }
    }

    private fun mapPlaybackError(error: PlaybackException): PlaybackError = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> PlaybackError.PermissionUnavailable
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackError.SourceNotFound
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            -> PlaybackError.UnsupportedMedia

        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            -> PlaybackError.DecodeFailure

        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            -> PlaybackError.ReadFailure

        else -> PlaybackError.Unknown(error.errorCodeName)
    }

    private companion object {
        const val POSITION_POLL_MS = 500L
    }
}
