package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kino.puber.playertestfixtures.server.ResponseOutcome
import java.util.concurrent.TimeUnit
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The real [PlaybackController] and ExoPlayer against the loopback fixture server. */
@RunWith(AndroidJUnit4::class)
internal class PlaybackControllerDeviceTest {

    private lateinit var harness: PlaybackControllerHarness

    @Before
    fun setUp() {
        harness = PlaybackControllerHarness()
    }

    @After
    fun tearDown() {
        if (::harness.isInitialized) harness.close()
    }

    @Test
    fun progressiveMp4_reachesReadyAdvancesAndEnds() {
        harness.useMediaRoutes(required = setOf("progressive"))

        val probe = harness.prepare("/media/progressive.mp4")

        harness.awaitReady(probe)
        harness.awaitCondition("progressive position advance") { currentPosition > 0L }
        harness.awaitCondition("progressive ENDED") { PlaybackEvent.Ended in harness.events.all }
        assertTrue(probe.error.get() == null)
        assertTrue(
            "Expected a progressive MP4 request; journal=${harness.server.requestJournal.entries}",
            harness.server.requestJournal.entries.any {
                it.path == "/media/progressive.mp4" && it.outcome is ResponseOutcome.Completed
            },
        )
        assertEquals(0, harness.server.requestJournal.unknownRequests.size)
    }

    @Test
    fun hlsVod_reachesReadyAdvancesAndEnds() {
        harness.useMediaRoutes(required = setOf("master"))

        val probe = harness.prepare("/media/hls/master.m3u8")

        harness.awaitReady(probe)
        harness.awaitCondition("HLS position advance") { currentPosition > 0L }
        harness.awaitCondition("HLS ENDED") { PlaybackEvent.Ended in harness.events.all }
        assertTrue(probe.error.get() == null)
        val requests = harness.server.requestJournal.entries
        assertTrue(
            "Expected HLS master and media playlist requests, journal=$requests",
            requests.any { it.path == "/media/hls/master.m3u8" } &&
                requests.any { it.path.startsWith("/media/hls/video_") && it.path.endsWith(".m3u8") },
        )
        assertTrue(
            "Expected HLS segment request, journal=$requests",
            requests.any { it.path.startsWith("/media/hls/video_") && it.path.endsWith(".ts") },
        )
        harness.server.assertRequiredRoutesRequested()
        assertEquals(0, harness.server.requestJournal.unknownRequests.size)
    }

    @Test
    fun progressivePlayback_controlsAndStartPositionUseRealPlayerEvents() {
        val probe = harness.prepare("/media/progressive.mp4", startPosition = 1_000L)
        harness.awaitReady(probe)
        harness.awaitCondition("non-zero start position") { currentPosition >= 900L }

        harness.onMain { harness.controller.pause() }
        harness.awaitCondition("pause") { !isPlaying }

        harness.onMain {
            harness.controller.setSpeed(2f)
            harness.controller.seekTo(2_000L)
        }
        harness.awaitCondition("forward seek") { currentPosition in 1_800L..2_400L }

        harness.onMain { harness.controller.play() }
        harness.awaitCondition("play after seek") { isPlaying }

        harness.onMain { harness.controller.seekTo(250L) }
        harness.awaitCondition("backward seek") { currentPosition in 0L..700L }

        harness.onMain { harness.controller.seekTo(3_700L) }
        harness.awaitCondition("near-end seek ENDED") { player?.playbackState == Player.STATE_ENDED }
        assertTrue(probe.error.get() == null)

        harness.onMain { harness.controller.play() }
        harness.awaitCondition("replay after ended") { currentPosition < 1_000L && isPlaying }
        assertEquals(2f, harness.playerRead { playbackSpeed }, 0f)
        assertTrue("Seeks were not reported", harness.events.all.count { it == PlaybackEvent.Seek } >= 3)
    }

    @Test
    fun awaitCondition_failsClosedWhenConditionRemainsFalse() {
        val error = assertThrows(AssertionError::class.java) {
            harness.awaitCondition("deliberately false condition", timeoutMs = FAIL_CLOSED_TIMEOUT_MS) { false }
        }

        assertTrue(error.message.orEmpty().contains("deliberately false condition"))
    }

    @Test
    fun unexpectedLoopbackRequest_failsClosedWithBoundedDiagnostics() {
        try {
            harness.server.reset(emptyList())
            harness.server.newClient().newCall(
                Request.Builder().url(harness.server.url("/unexpected-player-request")).build(),
            ).execute().close()
            harness.server.awaitQuiescence(timeout = 10, unit = TimeUnit.SECONDS)

            val error = assertThrows(AssertionError::class.java) {
                harness.server.assertNoUnknownRequests(MAX_DIAGNOSTIC_REQUESTS)
            }

            assertTrue(error.message.orEmpty().contains("/unexpected-player-request"))
        } finally {
            harness.server.reset(emptyList())
        }
    }

    private companion object {
        const val FAIL_CLOSED_TIMEOUT_MS = 100L
        const val MAX_DIAGNOSTIC_REQUESTS = 8
    }
}
