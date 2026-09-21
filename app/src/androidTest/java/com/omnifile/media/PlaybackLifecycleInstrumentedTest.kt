package com.omnifile.media

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifile.MainActivity
import com.omnifile.OmniFileApplication
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lifecycle evidence: the AppContainer-scoped coordinator and the
 * service-owned player survive Activity recreation and backgrounding without
 * duplicating the player or losing the current item.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackLifecycleInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        val coordinator = container().playbackCoordinator
        instrumentation.runOnMainSync {
            coordinator.stop()
            coordinator.releaseController()
        }
        scenario?.close()
        File(context.filesDir, WAV_NAME).delete()
    }

    @Test
    fun playbackSurvivesActivityRecreationWithoutDuplicatePlayer() {
        val entry = prepareLocalWavEntry()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val coordinator = container().playbackCoordinator

        instrumentation.runOnMainSync { coordinator.play(entry) }
        val playing = awaitState(coordinator) { it.status == PlaybackStatus.READY && it.isPlaying }
        assertTrue(playing.isPlaying)
        val mediaId = playing.item!!.mediaId

        scenario!!.recreate()

        val afterRecreate = awaitState(coordinator) { it.status == PlaybackStatus.READY }
        assertEquals(mediaId, afterRecreate.item?.mediaId)
        assertTrue("playback must survive recreation", afterRecreate.isPlaying)
        // The coordinator (and thus the single controller) is container-owned.
        assertSame(coordinator, container().playbackCoordinator)

        instrumentation.runOnMainSync { coordinator.togglePlayPause() }
        val paused = awaitState(coordinator) { !it.isPlaying && it.status == PlaybackStatus.READY }
        assertTrue(!paused.isPlaying)
    }

    @Test
    fun playbackContinuesWhileAppIsBackgrounded() {
        val entry = prepareLocalWavEntry()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val coordinator = container().playbackCoordinator

        instrumentation.runOnMainSync { coordinator.play(entry) }
        awaitState(coordinator) { it.status == PlaybackStatus.READY && it.isPlaying }

        scenario!!.moveToState(Lifecycle.State.CREATED)
        Thread.sleep(700)

        val backgrounded = coordinator.state.value
        assertTrue(
            "playback must continue in background, status=${backgrounded.status}",
            backgrounded.isPlaying && backgrounded.status == PlaybackStatus.READY,
        )

        scenario!!.moveToState(Lifecycle.State.RESUMED)
        val returned = awaitState(coordinator) { it.status == PlaybackStatus.READY && it.isPlaying }
        assertTrue(returned.isPlaying)
        assertEquals(PlaybackItem.of(entry, "Local storage").mediaId, returned.item?.mediaId)
    }

    private fun container() = (context.applicationContext as OmniFileApplication).container

    private fun prepareLocalWavEntry(): StorageEntry = runBlocking {
        File(context.filesDir, WAV_NAME).writeBytes(MediaTestFixtures.toneWavBytes())
        val provider = container().localProvider
        val root = (provider.root() as StorageResult.Success).value
        (provider.listChildren(root.ref) as StorageResult.Success).value
            .single { it.displayName == WAV_NAME }
    }

    private fun awaitState(
        coordinator: PlaybackCoordinator,
        timeoutMs: Long = 15_000,
        predicate: (NowPlayingState) -> Boolean,
    ): NowPlayingState {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val current = coordinator.state.value
            if (predicate(current)) return current
            Thread.sleep(50)
        }
        return coordinator.state.value
    }

    private companion object {
        const val WAV_NAME = "vs07-lifecycle.wav"
    }
}
