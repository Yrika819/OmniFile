package com.omnifile.media

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.omnifile.MainActivity

/**
 * Owns the single ExoPlayer and MediaSession for the whole app.
 *
 * The service exists only for playback/session lifecycle: it is created when a
 * controller connects, promotes itself through Media3's media-playback
 * foreground contract while playing, and tears down per Media3 lifecycle when
 * playback stops. The UI never owns player lifetime.
 *
 * Controller policy: own-package controllers and external controllers holding
 * `android.media.permission.MEDIA_CONTENT_CONTROL` (system media controls,
 * Bluetooth/headset key events via the system) connect with standard commands;
 * any other external controller is rejected. No custom session commands exist.
 */
@AndroidXOptIn(markerClass = [UnstableApi::class])
class OmniFilePlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus= */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.let { session ->
            session.release()
            session.player.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val packageMatchesUid = packageManager.getPackagesForUid(controller.uid)
                ?.contains(controller.packageName) == true
            // Require a verified same-app UID/package identity; external
            // controllers must pass the same binding before permission checks.
            val ownController = controller.uid == Process.myUid() &&
                    controller.packageName == packageName &&
                    packageMatchesUid
            val systemController = packageMatchesUid && packageManager.checkPermission(
                android.Manifest.permission.MEDIA_CONTENT_CONTROL,
                controller.packageName,
            ) == PackageManager.PERMISSION_GRANTED
            if (!ownController && !systemController) {
                return MediaSession.ConnectionResult.reject()
            }

            // App-owned control needs source selection and proven seek support;
            // external system controls receive transport-only commands and can
            // never inject a new URI/item or seek an unproven source.
            val playerCommands = Player.Commands.Builder()
                .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_GET_TIMELINE)
                .add(Player.COMMAND_GET_METADATA)
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_STOP)
                .apply {
                    if (ownController) {
                        add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                        add(Player.COMMAND_SET_MEDIA_ITEM)
                        add(Player.COMMAND_PREPARE)
                        add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                    }
                }
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }
    }
}
