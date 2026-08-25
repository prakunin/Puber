package com.kino.puber.ui.feature.player.vm

import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerCountdowns
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * The prompt has to fit inside the segment it offers to skip.
 *
 * Leaving the segment takes the prompt down and the skip never runs, so a countdown longer than
 * what the segment has left is a promise the player cannot keep: the bar stops part-way and the
 * plate disappears as the segment ends on its own.
 */
internal class PlayerVMSkipCountdownFitTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private var position = 0L

    private fun playingInto(segment: SkipSegment, from: Long) {
        position = from
        every { playbackController.currentPosition } answers { position }
        every { skipSegmentInteractor.findActiveSegment(any(), any(), any()) } answers {
            segment.takeIf {
                position >= it.startMs - PlayerCountdowns.PROMPT_LEAD_IN_MS && position <= it.endMs!!
            }
        }
    }

    /**
     * Playback the way the position tick sees it: half a second of media per half second.
     *
     * [runCurrent] after each step because the ticks land exactly on the boundary [advanceTimeBy]
     * stops short of, and every one of them has to run.
     */
    private fun TestScope.playFor(seconds: Double) {
        repeat((seconds * 2).toInt()) {
            position += PlayerCountdowns.TICK_MS / 2
            advanceTimeBy(PlayerCountdowns.TICK_MS / 2)
            runCurrent()
        }
    }

    @Test
    fun aSegmentWithRoomToSpareGetsTheWholeCountdown() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            playingInto(SkipSegment(SkipSegmentType.INTRO, startMs = 10_000, endMs = 40_000), from = 7_500)
            val vm = startedVM()

            playFor(1.0)

            assertEquals(
                PlayerCountdowns.SKIP_SEGMENT_SEC,
                contentState(vm).activeSkipSegment?.countdown,
            )
            vm.testCancelScope()
        }

    @Test
    fun aShorterSegmentGetsAShorterCountdownAndTheSkipStillRuns() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            val intro = SkipSegment(SkipSegmentType.INTRO, startMs = 10_000, endMs = 18_000)
            playingInto(intro, from = 7_500)
            val vm = startedVM()

            playFor(1.0)
            val prompt = contentState(vm).activeSkipSegment
            assertNotNull(prompt)
            // 9.5s of segment left, minus the second zero gets and the three the skip has to save.
            assertEquals(5, prompt?.countdown)
            assertEquals(5, prompt?.totalSeconds)

            // The countdown runs out while the segment is still on screen, so the skip happens.
            playFor(6.5)
            verify { playbackController.seekTo(18_000) }
            assertNull(contentState(vm).activeSkipSegment)
            vm.testCancelScope()
        }

    @Test
    fun countdownUsesActualPlayerSpeedWhenUiStateHasResetForANewEpisode() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            every { playbackController.playbackSpeed } returns 2f
            playingInto(SkipSegment(SkipSegmentType.INTRO, startMs = 10_000, endMs = 30_000), from = 7_500)
            val vm = startedVM()

            playFor(1.0)

            // At the UI state's default 1x this would be capped at seven seconds. The player is
            // actually still running at 2x, so only six seconds fit before the segment ends.
            assertEquals(6, contentState(vm).activeSkipSegment?.countdown)
            vm.testCancelScope()
        }

    @Test
    fun aSkipWhosePointHasPassedDoesNotRewind() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            val intro = SkipSegment(SkipSegmentType.INTRO, startMs = 10_000, endMs = 18_000)
            playingInto(intro, from = 7_500)
            val vm = startedVM()
            playFor(1.0)
            assertNotNull(contentState(vm).activeSkipSegment)

            // Playback ran past the end of the segment while the countdown was still up — which is
            // what an open panel or a speed change can do to a countdown that fitted when it began.
            position = 25_000
            vm.onAction(PlayerAction.SkipSegmentClicked)

            verify(exactly = 0) { playbackController.seekTo(any()) }
            assertNull(contentState(vm).activeSkipSegment)
            vm.testCancelScope()
        }

    @Test
    fun aSegmentTooShortToSaveAnythingIsNeverOffered() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            playingInto(SkipSegment(SkipSegmentType.INTRO, startMs = 10_000, endMs = 15_000), from = 7_500)
            val vm = startedVM()

            repeat(16) {
                playFor(0.5)
                assertNull(contentState(vm).activeSkipSegment)
            }
            verify(exactly = 0) { playbackController.seekTo(any()) }
            vm.testCancelScope()
        }
}
