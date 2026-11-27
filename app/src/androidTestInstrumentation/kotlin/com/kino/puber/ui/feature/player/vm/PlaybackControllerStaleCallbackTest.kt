package com.kino.puber.ui.feature.player.vm

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kino.puber.playertestfixtures.server.HermeticTestServer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Probe for a stream-failure callback that outlives its stream.
 *
 * Stream A answers its playlist request with a malformed body that is released by a gate, so the
 * parse failure (not retried, mapped to `NEXT_URL`) is reported only after the gate opens. A
 * failure or error event after `switchStream(B)` is the "reproduced" branch: the controller
 * reported A's failure against the stream the viewer already left; the case guards the
 * `PlaybackCallbackGate` fix. Each case fixes a different ordering:
 *
 * - [delayedFailure_reportsStreamFailure_whenStreamIsNotSwitched]: control; without the switch the
 *   same route must fail, otherwise the probes pass for nothing.
 * - [switchStreamBeforeDelayedFailure_reportsNoFailureForPreviousStream]: the switch comes first
 *   and A's loader is cancelled, so A's error normally never exists (cancellation ordering).
 * - [switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream]: A's error is parsed and
 *   queued on the main looper before the switch runs in the same main-thread turn (queued-error
 *   window); an attempt only counts when the player did deliver that error, retried up to 5 times with a fresh
 *   stream A, so the case cannot pass unexercised.
 * - [switchStreamToFailingStream_reportsOneFailureWithoutRePrepare]: B itself fails after the
 *   switch; the controller must feed B's load start into the gate, otherwise the first real error
 *   is answered by one extra prepare (a second request to B) before it is reported.
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

    @Test
    fun switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream() {
        // The queue-turns-non-idle wait below can be satisfied by an unrelated message, so the switch
        // sometimes runs before A's error is queued. An attempt that did not deliver A's error did not
        // exercise the window; it is repeated with a fresh stream A, and only an attempt that did is judged.
        val gates = QUEUED_ATTEMPT_PATHS.map { harness.newGate() }
        harness.useMediaRoutes(
            extra = QUEUED_ATTEMPT_PATHS.mapIndexed { index, path -> delayedMalformedPlaylist(gates[index], path) },
        )
        val unmet = mutableListOf<String>()
        for ((index, path) in QUEUED_ATTEMPT_PATHS.withIndex()) {
            val probe = harness.prepare(path)
            harness.awaitCondition("stream A playlist request is held at the gate") { requested(path) }
            val mark = harness.events.mark()

            // One main-thread turn: A's body is released and parsed while main is blocked, so its
            // error is queued on the main looper before switchStream(B) changes the current stream.
            harness.onMain {
                gates[index].countDown()
                awaitOnMain("stream A playlist is answered") { outcome(path) != null }
                // The loader closes the response once the parse failed; the error then hops through the
                // playback thread to the main looper, whose queue turns non-idle while this turn is blocked.
                check(harness.closedResponses.awaitClosed(path, PARSE_WAIT_MS)) {
                    "stream A response was not closed in $PARSE_WAIT_MS ms; ${harness.diagnostics()}"
                }
                awaitOnMain("stream A error is queued on the main looper", PARSE_WAIT_MS) { !Looper.myQueue().isIdle }
                harness.controller.switchStream(harness.stream(STREAM_B_PATH), null)
            }

            harness.awaitCondition("stream B is ready") {
                harness.events.since(mark).any { it is PlaybackEvent.StreamReady }
            }
            Thread.sleep(SETTLE_MS)
            if (probe.error.get() != null) {
                assertNoFailureAfter(mark)
                return
            }
            unmet += "attempt ${index + 1}: ${harness.diagnostics()}"
        }
        throw AssertionError(
            "A's error never reached the player in ${QUEUED_ATTEMPT_PATHS.size} attempts, so the queued-failure " +
                "window was not exercised; ${unmet.joinToString("; ")}",
        )
    }

    @Test
    fun switchStreamToFailingStream_reportsOneFailureWithoutRePrepare() {
        harness.useMediaRoutes(
            extra = listOf(
                harness.server.route(
                    id = "stream-b-malformed",
                    path = FAILING_B_PATH,
                    response = HermeticTestServer.text(
                        body = "not a playlist",
                        contentType = PlaybackControllerHarness.HLS_CONTENT_TYPE,
                    ),
                ),
            ),
        )
        val probe = harness.prepare(HEALTHY_A_PATH)
        harness.awaitReady(probe)
        val mark = harness.events.mark()

        harness.onMain { harness.controller.switchStream(harness.stream(FAILING_B_PATH), null) }

        harness.awaitCondition("stream B failure is reported") {
            harness.events.since(mark).any { it is PlaybackEvent.StreamFailure }
        }
        Thread.sleep(SETTLE_MS)
        val failures = harness.events.since(mark).filterIsInstance<PlaybackEvent.StreamFailure>()
        assertEquals("Expected exactly one failure; events=${harness.events.since(mark)}", 1, failures.size)
        assertEquals(harness.url(FAILING_B_PATH), failures.single().url)
        assertEquals(
            "B's real error must be reported without a re-prepare; ${harness.diagnostics()}",
            1,
            harness.server.requestJournal.entries.count { it.path == FAILING_B_PATH },
        )
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

    private fun awaitOnMain(description: String, timeoutMs: Long = AWAIT_MS, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "$description: not reached in $timeoutMs ms" }
            Thread.sleep(POLL_MS)
        }
    }

    private fun aRequested(): Boolean = requested(STREAM_A_PATH)

    private fun aOutcome() = outcome(STREAM_A_PATH)

    private fun requested(path: String): Boolean =
        harness.server.requestJournal.entries.any { it.path == path }

    private fun outcome(path: String) =
        harness.server.requestJournal.entries.firstOrNull { it.path == path }?.outcome

    private fun delayedMalformedPlaylist(gate: CountDownLatch, path: String = STREAM_A_PATH) = harness.server.route(
        id = "stream-a-$path",
        path = path,
        response = HermeticTestServer.delayed(
            gate = gate,
            body = "not a playlist".toByteArray(),
            status = 200,
            contentType = PlaybackControllerHarness.HLS_CONTENT_TYPE,
        ),
    )

    private companion object {
        const val STREAM_A_PATH = "/a/master.m3u8"
        val QUEUED_ATTEMPT_PATHS = (1..5).map { "/a$it/master.m3u8" }
        const val STREAM_B_PATH = "/media/progressive.mp4"
        const val HEALTHY_A_PATH = "/media/progressive.mp4"
        const val FAILING_B_PATH = "/b/malformed.m3u8"
        const val SETTLE_MS = 1_000L
        const val PARSE_WAIT_MS = 500L
        const val AWAIT_MS = 5_000L
        const val POLL_MS = 10L
    }
}
