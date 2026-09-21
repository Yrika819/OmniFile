package com.omnifile.media

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifile.storage.EntryKind
import com.omnifile.storage.SafStorageProvider
import com.omnifile.storage.SeekSupport
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageResult
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device SAF playback: uses only the app's current persisted SAF grant
 * through the real system ContentResolver and the real ExoPlayer. If no
 * persisted readable tree exists the test is skipped (a DocumentsUI selection
 * is a physical user action and is never fabricated).
 */
@RunWith(AndroidJUnit4::class)
class SafDevicePlaybackInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var coordinator: PlaybackCoordinator? = null

    @After
    fun tearDown() {
        coordinator?.let { active ->
            instrumentation.runOnMainSync {
                active.stop()
                active.releaseController()
            }
        }
    }

    @Test
    fun persistedDeviceSafAudioPlaysThroughTheSession() {
        val grants = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }
        assumeTrue(
            "no persisted readable SAF tree on this device: ${grants.map { it.uri }}",
            grants.isNotEmpty(),
        )
        val grant = grants.first()
        val provider = SafStorageProvider(
            contentResolver = context.contentResolver,
            treeUri = grant.uri,
            id = SafStorageProvider.providerIdFor(grant.uri),
            grantFlags = (if (grant.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                (if (grant.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0),
        )
        val entry = findDeviceAudio(provider)
        assumeTrue("no vs07 SAF audio fixture in the persisted tree", entry != null)
        val audioEntry: StorageEntry = entry!!

        val playback = PlaybackCoordinator(
            context,
            resolverFor = { id -> if (id == provider.id) provider else null },
        )
        coordinator = playback
        instrumentation.runOnMainSync { playback.play(audioEntry) }

        val playing = awaitState(playback.state) { it.status == PlaybackStatus.READY && it.isPlaying }
        assertEquals(PlaybackStatus.READY, playing.status)
        assertTrue(playing.isPlaying)
        assertEquals(audioEntry.displayName, playing.item?.displayName)
        assertEquals("SAF folder", playing.item?.sourceLabel)

        val first = playing.positionMs
        val advanced = awaitState(playback.state) { it.isPlaying && it.positionMs > first + 200 }
        assertTrue("SAF playback must progress", advanced.positionMs > first + 200)

        // Seek is issued only when the per-item probe proved SEEKABLE.
        if (advanced.seekSupport == SeekSupport.SEEKABLE && advanced.durationMs != null) {
            instrumentation.runOnMainSync { playback.seekTo(1000L) }
            val sought = awaitState(playback.state) { it.positionMs >= 900L }
            assertTrue("position=${sought.positionMs}", sought.positionMs >= 900L)
        } else {
            assertTrue(!advanced.seekAllowed)
        }
    }

    private fun findDeviceAudio(provider: SafStorageProvider): StorageEntry? = runBlocking {
        val root = when (val result = provider.root()) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> throw AssertionError("SAF root failed: ${result.error}")
        }
        val direct = when (val result = provider.listChildren(root.ref)) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> throw AssertionError("SAF listing failed: ${result.error}")
        }
        direct.firstOrNull { it.kind == EntryKind.FILE && it.displayName.startsWith("vs07-saf") }
    }

    private fun awaitState(
        flow: StateFlow<NowPlayingState>,
        timeoutMs: Long = 15_000,
        predicate: (NowPlayingState) -> Boolean,
    ): NowPlayingState {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val current = flow.value
            if (predicate(current)) return current
            Thread.sleep(50)
        }
        return flow.value
    }
}
