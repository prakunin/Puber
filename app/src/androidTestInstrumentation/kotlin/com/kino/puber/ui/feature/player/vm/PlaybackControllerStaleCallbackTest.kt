package com.kino.puber.ui.feature.player.vm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kino.puber.playertestfixtures.server.HermeticTestServer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Probe for a stream-failure callback that outlives its stream.
 *
 * Stream A answers its playlist request with a malformed body that is released by a gate, so the
 * parse failure (not retried, mapped to `NEXT_URL`) is reported only after the gate opens. A
 * failure or error event after `switchStream(B)` is the "reproduced" branch: the controller
 * reported A's failure against the stream the viewer already left, and the finding goes to the
 * `player-callback-gate` run. Each case fixes a different ordering:
 *
 * - [delayedFailure_reportsStreamFailure_whenStreamIsNotSwitched]: control; without the switch the
 *   same route must fail, otherwise the probes pass for nothing.
 * - [switchStreamBeforeDelayedFailure_reportsNoFailureForPreviousStream]: the switch comes first
 *   and A's loader is cancelled, so A's error normally never exists (cancellation ordering).
 * - [switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream]: A's error is parsed and
 *   queued on the main looper before the switch runs in the same main-thread turn (queued-error
 *   window); the case asserts the player did deliver that error, so it cannot pass unexercised.
 */
@RunWith(AndroidJUnit4::class)
internal class PlaybackControllerStaleCallbackTest {

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
    fun delayedFailure_reportsStreamFailure_whenStreamIsNotSwitched() {
        val gate = harness.newGate()
        harness.useMediaRoutes(extra = listOf(delayedMalformedPlaylist(gate)))
        harness.prepare(STREAM_A_PATH)
        harness.awaitCondition("stream A playlist request is held at the gate") { aRequested() }

        gate.countDown()

        harness.awaitCondition("stream A failure is reported") {
            harness.events.all.any { it is PlaybackEvent.StreamFailure }
        }
        val failure = harness.events.all.filterIsInstance<PlaybackEvent.StreamFailure>().first()
        assertEquals(harness.url(STREAM_A_PATH), failure.url)
        assertEquals(PlaybackControl.StreamRecovery.NEXT_URL, failure.recovery)
    }

    @Test
    fun switchStreamBeforeDelayedFailure_reportsNoFailureForPreviousStream() {
        val gate = harness.newGate()
        harness.useMediaRoutes(extra = listOf(delayedMalformedPlaylist(gate)))
        harness.prepare(STREAM_A_PATH)
        harness.awaitCondition("stream A playlist request is held at the gate") { aRequested() }
        val mark = harness.events.mark()

        harness.onMain { harness.controller.switchStream(harness.stream(STREAM_B_PATH), null) }
        gate.countDown()

        harness.awaitCondition("stream B is ready") {
            harness.events.since(mark).any { it is PlaybackEvent.StreamReady }
        }
        harness.awaitCondition("stream A request is settled") { aOutcome() != null }
        Thread.sleep(SETTLE_MS)
        assertNoFailureAfter(mark)
    }

    @Ignore("player-callback-gate: stale failure queued before switchStream is attributed to the new stream; see docs/testing/player-playback.md")
    @Test
    fun switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream() {
        val gate = harness.newGate()
        harness.useMediaRoutes(extra = listOf(delayedMalformedPlaylist(gate)))
        val probe = harness.prepare(STREAM_A_PATH)
        harness.awaitCondition("stream A playlist request is held at the gate") { aRequested() }
        val mark = harness.events.mark()

        // One main-thread turn: A's body is released and parsed while main is blocked, so its
        // error is queued on the main looper before switchStream(B) changes the current stream.
        harness.onMain {
            gate.countDown()
            awaitOnMain("stream A playlist is answered") { aOutcome() != null }
            Thread.sleep(PARSE_WAIT_MS)
            harness.controller.switchStream(harness.stream(STREAM_B_PATH), null)
        }

        harness.awaitCondition("stream B is ready") {
            harness.events.since(mark).any { it is PlaybackEvent.StreamReady }
        }
        Thread.sleep(SETTLE_MS)
        assertNotNull(
            "A's error never reached the player, so the queued-failure window was not exercised; " +
                harness.diagnostics(),
            probe.error.get(),
        )
        assertNoFailureAfter(mark)
    }

    private fun assertNoFailureAfter(mark: Int) {
        val after = harness.events.since(mark)
        assertTrue(
            "A failure was reported after the switch; events=$after",
            after.none { it is PlaybackEvent.StreamFailure || it is PlaybackEvent.Error },
        )
        assertEquals(
            harness.url(STREAM_B_PATH),
            harness.playerRead { player?.currentMediaItem?.localConfiguration?.uri?.toString() },
        )
    }

    private fun awaitOnMain(description: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MS)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "$description: not reached in $AWAIT_MS ms" }
            Thread.sleep(POLL_MS)
        }
    }

    private fun aRequested(): Boolean =
        harness.server.requestJournal.entries.any { it.path == STREAM_A_PATH }

    private fun aOutcome() =
        harness.server.requestJournal.entries.firstOrNull { it.path == STREAM_A_PATH }?.outcome

    private fun delayedMalformedPlaylist(gate: CountDownLatch) = harness.server.route(
        id = "stream-a",
        path = STREAM_A_PATH,
        response = HermeticTestServer.delayed(
            gate = gate,
            body = "not a playlist".toByteArray(),
            status = 200,
            contentType = PlaybackControllerHarness.HLS_CONTENT_TYPE,
        ),
    )

    private companion object {
        const val STREAM_A_PATH = "/a/master.m3u8"
        const val STREAM_B_PATH = "/media/progressive.mp4"
        const val SETTLE_MS = 1_000L
        const val PARSE_WAIT_MS = 500L
        const val AWAIT_MS = 5_000L
        const val POLL_MS = 10L
    }
}
