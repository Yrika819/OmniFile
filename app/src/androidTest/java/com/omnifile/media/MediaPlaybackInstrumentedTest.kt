package com.omnifile.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnifile.storage.LocalStorageProvider
import com.omnifile.storage.ProviderId
import com.omnifile.storage.SeekSupport
import com.omnifile.storage.StorageEntry
import com.omnifile.storage.StorageResult
import java.io.File
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end playback evidence: real OmniFilePlaybackService, real ExoPlayer,
 * app-private Local fixtures. Player state/progress is the primary signal;
 * audible output is never asserted.
 */
@RunWith(AndroidJUnit4::class)
class MediaPlaybackInstrumentedTest {
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
        File(context.filesDir, FLAC_NAME).delete()
        File(context.filesDir, "vs07-missing-replacement.wav").delete()
    }

    @Test
    @RequiresAudioClock
    fun serviceConnectsAndLocalWavPlaysWithTruthfulState() {
        val entry = prepareWav()

        instrumentation.runOnMainSync { coordinator.play(entry) }

        val playing = awaitState { it.status == PlaybackStatus.READY && it.isPlaying }
        assertEquals(PlaybackStatus.READY, playing.status)
        assertTrue(playing.isPlaying)
        assertEquals(WAV_NAME, playing.item?.displayName)
        assertEquals("Local storage", playing.item?.sourceLabel)
        assertEquals(SeekSupport.SEEKABLE, playing.seekSupport)
        val duration = playing.durationMs
        assertNotNull("WAV duration must be known", duration)
        assertTrue("duration=$duration", duration!! in 1500L..2500L)

        val firstPosition = playing.positionMs
        val advanced = awaitState { it.isPlaying && it.positionMs > firstPosition + 200 }
        assertTrue("position must advance", advanced.positionMs > firstPosition + 200)

        instrumentation.runOnMainSync { coordinator.togglePlayPause() }
        val paused = awaitState { it.status == PlaybackStatus.READY && !it.isPlaying }
        assertTrue(!paused.isPlaying)

        instrumentation.runOnMainSync { coordinator.togglePlayPause() }
        val resumed = awaitState { it.status == PlaybackStatus.READY && it.isPlaying }
        assertTrue(resumed.isPlaying)
    }

    @Test
    fun localFlacFixturePlays() {
        val target = File(context.filesDir, FLAC_NAME)
        instrumentation.context.assets.open(MediaTestFixtures.FLAC_ASSET).use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        val entry = entryFor(FLAC_NAME)

        instrumentation.runOnMainSync { coordinator.play(entry) }

        val playing = awaitState { it.status == PlaybackStatus.READY && it.isPlaying }
        assertEquals(PlaybackStatus.READY, playing.status)
        assertEquals(FLAC_NAME, playing.item?.displayName)
        val duration = playing.durationMs
        assertNotNull("FLAC duration must be known", duration)
        assertTrue("duration=$duration", duration!! in 500L..2000L)
    }

    @Test
    fun seekIsIssuedOnlyForProvenSeekableSource() {
        val entry = prepareWav()
        instrumentation.runOnMainSync { coordinator.play(entry) }
        awaitState { it.status == PlaybackStatus.READY && it.isPlaying }

        instrumentation.runOnMainSync { coordinator.seekTo(1000L) }
        val sought = awaitState { it.positionMs >= 900L }
        assertTrue("position=${sought.positionMs}", sought.positionMs >= 900L)
    }

    @Test
    fun unprovenSeekSourceRejectsSeekCommand() {
        val entry = prepareWav()
        // Same file, but the resolver truthfully reports a non-seekable transport.
        val nonSeekable = PlaybackCoordinator(
            context,
            resolverFor = { id ->
                if (id != localProvider.id) {
                    null
                } else {
                    object : com.omnifile.storage.PlaybackSourceProvider by localProvider {
                        override suspend fun resolvePlaybackSource(
                            entry: StorageEntry,
                        ): StorageResult<com.omnifile.storage.PlaybackSource> =
                            when (val base = localProvider.resolvePlaybackSource(entry)) {
                                is StorageResult.Success -> StorageResult.Success(
                                    base.value.copy(seekSupport = SeekSupport.NOT_SEEKABLE),
                                )

                                is StorageResult.Failure -> base
                            }
                    }
                }
            },
        )
        try {
            instrumentation.runOnMainSync { nonSeekable.play(entry) }
            awaitState(nonSeekable.state) { it.status == PlaybackStatus.READY && it.isPlaying }

            instrumentation.runOnMainSync { nonSeekable.seekTo(1900L) }
            Thread.sleep(300)
            val state = nonSeekable.state.value
            assertTrue(
                "seek must be suppressed for NOT_SEEKABLE, position=${state.positionMs}",
                state.positionMs < 1500L,
            )
            assertEquals(SeekSupport.NOT_SEEKABLE, state.seekSupport)
            assertTrue(!state.seekAllowed)
        } finally {
            instrumentation.runOnMainSync {
                nonSeekable.stop()
                nonSeekable.releaseController()
            }
        }
    }

    @Test
    fun missingLocalSourceFailsTruthfully() {
        val entry = prepareWav()
        File(context.filesDir, WAV_NAME).delete()

        instrumentation.runOnMainSync { coordinator.play(entry) }

        val failed = awaitState { it.status == PlaybackStatus.ERROR }
        assertEquals(PlaybackStatus.ERROR, failed.status)
        assertEquals(PlaybackError.SourceNotFound, failed.error)
    }

    @Test
    fun failedReplacementKeepsStateOnTheFailedCurrentRequest() {
        val first = prepareWav()
        val replacementName = "vs07-missing-replacement.wav"
        File(context.filesDir, replacementName).writeBytes(MediaTestFixtures.toneWavBytes())
        val replacement = entryFor(replacementName)

        instrumentation.runOnMainSync { coordinator.play(first) }
        awaitState { it.status == PlaybackStatus.READY && it.isPlaying }
        File(context.filesDir, replacementName).delete()

        instrumentation.runOnMainSync { coordinator.play(replacement) }

        val failed = awaitState { it.status == PlaybackStatus.ERROR }
        assertEquals(replacementName, failed.item?.displayName)
        assertEquals(PlaybackError.SourceNotFound, failed.error)
        Thread.sleep(500)
        val stable = coordinator.state.value
        assertEquals("replacement error must remain current", replacementName, stable.item?.displayName)
        assertEquals(PlaybackStatus.ERROR, stable.status)
        assertTrue(!stable.isPlaying)
    }

    @Test
    @RequiresAudioClock
    fun wavPlaysToEndedState() {
        val entry = prepareWav()
        instrumentation.runOnMainSync { coordinator.play(entry) }
        awaitState { it.status == PlaybackStatus.READY && it.isPlaying }

        val ended = awaitState(timeoutMs = 8000) { it.status == PlaybackStatus.ENDED }
        assertEquals(PlaybackStatus.ENDED, ended.status)
        assertEquals(WAV_NAME, ended.item?.displayName)
    }

    private fun prepareWav(): StorageEntry {
        MediaTestFixtures.writeToneWav(File(context.filesDir, WAV_NAME))
        return entryFor(WAV_NAME)
    }

    private fun entryFor(fileName: String): StorageEntry = runBlocking {
        val root = (localProvider.root() as StorageResult.Success).value
        (localProvider.listChildren(root.ref) as StorageResult.Success).value
            .single { it.displayName == fileName }
    }

    private fun awaitState(
        flow: StateFlow<NowPlayingState> = coordinator.state,
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

    private companion object {
        const val WAV_NAME = "vs07-tone.wav"
        const val FLAC_NAME = "vs07-primary.flac"
    }
}
