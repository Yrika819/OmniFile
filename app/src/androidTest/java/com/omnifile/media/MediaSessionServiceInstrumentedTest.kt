package com.omnifile.media

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.ProviderId
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageResult
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Session/service contract evidence: manifest shape, system-visible session,
 * and media-playback foreground service state during real playback.
 * Diagnostics are read-only (PackageManager + `dumpsys` via UiAutomation).
 */
@RunWith(AndroidJUnit4::class)
class MediaSessionServiceInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var localProvider: LocalStorageProvider
    private lateinit var coordinator: PlaybackCoordinator

    @Before
    fun setUp() {
        localProvider = LocalStorageProvider(context.filesDir.toPath(), ProviderId("local-app-files"))
        coordinator = PlaybackCoordinator(
            context,
            resolverFor = { id -> if (id == localProvider.id) localProvider else null },
        )
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync {
            coordinator.stop()
            coordinator.releaseController()
        }
        File(context.filesDir, WAV_NAME).delete()
    }

    @Test
    fun serviceIsDeclaredExportedForSystemMediaControlsWithMediaPlaybackForegroundType() {
        val resolved = context.packageManager.resolveService(
            Intent(androidx.media3.session.MediaSessionService.SERVICE_INTERFACE)
                .setPackage(context.packageName),
            0,
        )
        assertTrue("MediaSessionService action must resolve", resolved != null)
        val serviceInfo: ServiceInfo = resolved!!.serviceInfo
        assertTrue("service must be exported for system media controls", serviceInfo.exported)
        assertTrue(
            "foregroundServiceType must include mediaPlayback",
            serviceInfo.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK != 0,
        )
        val permissions = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        ).requestedPermissions.orEmpty().toSet()
        assertTrue(permissions.contains(android.Manifest.permission.FOREGROUND_SERVICE))
        assertTrue(permissions.contains(android.Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK))
        // No storage / microphone / internet permission may be introduced by playback.
        assertTrue(!permissions.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
        assertTrue(!permissions.contains(android.Manifest.permission.READ_EXTERNAL_STORAGE))
        assertTrue(!permissions.contains(android.Manifest.permission.RECORD_AUDIO))
        assertTrue(!permissions.contains(android.Manifest.permission.INTERNET))
    }

    @Test
    fun sameAppControllerConnectsWithRequiredReadAndTransportCommands() {
        val controller = MediaController.Builder(
            context,
            sessionToken(context),
        ).buildAsync().get(10, TimeUnit.SECONDS)
        try {
            instrumentation.runOnMainSync {
                assertTrue(controller.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM))
                assertTrue(controller.isCommandAvailable(Player.COMMAND_GET_TIMELINE))
                assertTrue(controller.isCommandAvailable(Player.COMMAND_GET_METADATA))
                assertTrue(controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
                assertTrue(controller.isCommandAvailable(Player.COMMAND_STOP))
                assertTrue(controller.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM))
            }
        } finally {
            instrumentation.runOnMainSync { controller.release() }
        }
    }

    @Test
    fun untrustedTestPackageControllerIsRejected() {
        val untrustedContext = instrumentation.context
        val future = MediaController.Builder(
            untrustedContext,
            sessionToken(untrustedContext),
        ).buildAsync()
        try {
            future.get(10, TimeUnit.SECONDS)
            fail("untrusted test package must not connect to the MediaSession")
        } catch (error: ExecutionException) {
            assertTrue("rejection must have a connection failure cause", error.cause != null)
        }
    }

    @Test
    fun sessionIsVisibleToSystemWithTruthfulTitleWhilePlaying() {
        val entry = prepareWav()
        instrumentation.runOnMainSync { coordinator.play(entry) }
        awaitPlayback()

        val sessions = shell("dumpsys media_session")
        assertTrue("MediaSession must exist for the package", sessions.contains(context.packageName))
        assertTrue("session title must be truthful", sessions.contains(WAV_NAME))

        val services = shell("dumpsys activity services ${context.packageName}")
        assertTrue("playback service must run: $services", services.contains("OmniFilePlaybackService"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            assertTrue(
                "media-playback foreground service must be foreground during playback: $services",
                services.contains("isForeground=true"),
            )
            // dumpsys prints the FGS type as a bitmask; 0x2 is FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK.
            assertTrue(
                "foreground service type must be mediaPlayback (0x2): $services",
                services.contains("types=0x00000002") || services.contains("types=0x00000012"),
            )
        }
    }

    @Test
    fun stoppedServiceIsNoLongerForeground() {
        val entry = prepareWav()
        instrumentation.runOnMainSync { coordinator.play(entry) }
        awaitPlayback()

        instrumentation.runOnMainSync { coordinator.stop() }
        Thread.sleep(1500)

        val services = shell("dumpsys activity services ${context.packageName}")
        assertTrue(
            "a stopped media service must not stay foreground: $services",
            !services.contains("isForeground=true"),
        )
    }

    private fun sessionToken(controllerContext: android.content.Context): SessionToken =
        SessionToken(
            controllerContext,
            ComponentName(context.packageName, OmniFilePlaybackService::class.java.name),
        )

    private fun prepareWav(): StorageEntry = runBlocking {
        File(context.filesDir, WAV_NAME).writeBytes(MediaTestFixtures.toneWavBytes())
        val root = (localProvider.root() as StorageResult.Success).value
        (localProvider.listChildren(root.ref) as StorageResult.Success).value
            .single { it.displayName == WAV_NAME }
    }

    private fun awaitPlayback() {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            val state = coordinator.state.value
            if (state.status == PlaybackStatus.READY && state.isPlaying) return
            Thread.sleep(50)
        }
        throw AssertionError("playback did not start: ${coordinator.state.value}")
    }

    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private companion object {
        const val WAV_NAME = "vs07-session.wav"
    }
}
