package com.kino.puber.ui.feature.player.vm

import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerCountdowns
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.delay
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
 * A prompt that appears under a held Left/Right key takes the key's target away, so the VM holds
 * both prompts down while a seek burst runs and offers them once the viewer stops seeking.
 */
internal class PlayerVMSeekBurstTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()

        private const val BURST_WINDOW_MS = 1_500L
    }

    private var position = 0L

    private fun playingAt(from: Long, segments: List<SkipSegment> = emptyList()) {
        position = from
        every { playbackController.currentPosition } answers { position }
        every { playbackController.seekTo(any()) } answers { position = firstArg() }
        every { skipSegmentInteractor.findActiveSegment(any(), any(), any()) } answers {
            segments.firstOrNull {
                position >= it.startMs - PlayerCountdowns.PROMPT_LEAD_IN_MS && position < it.endMs!!
            }
        }
    }

    private fun TestScope.seek(vm: PlayerVM, action: PlayerAction = PlayerAction.SeekForward) {
        vm.onAction(action)
        callbackSlot.captured.onSeekPerformed()
        runCurrent()
    }

    private fun TestScope.wait(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun aSeekBurstIntoTheIntroRaisesNoSkipPromptUntilItEnds() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            playingAt(
                from = 20_000,
                segments = listOf(SkipSegment(SkipSegmentType.INTRO, startMs = 30_000, endMs = 60_000)),
            )
            val vm = startedVM()

            seek(vm)
            wait(1_000)
            assertNull(contentState(vm).activeSkipSegment)

            wait(BURST_WINDOW_MS - 1_000 + PlayerCountdowns.TICK_MS)
            val prompt = contentState(vm).activeSkipSegment
            assertNotNull(prompt)
            wait(PlayerCountdowns.TICK_MS * 2)
            assertEquals(prompt, contentState(vm).activeSkipSegment)
            vm.testCancelScope()
        }

    @Test
    fun aSeekBurstIntoTheCreditsLeadInRaisesNoNextEpisodePromptUntilItEnds() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            playingAt(from = 100_000)
            every { playbackController.seekTo(any()) } answers { position = 2_380_000L }
            val vm = startedVM()

            seek(vm)
            wait(1_000)
            assertNull(contentState(vm).nextEpisodeCountdown)

            wait(BURST_WINDOW_MS - 1_000 + PlayerCountdowns.TICK_MS)
            assertNotNull(contentState(vm).nextEpisodeCountdown)
            vm.testCancelScope()
        }

    @Test
    fun aSeekBackwardBurstIntoTheIntroRaisesNoSkipPromptUntilItEnds() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            playingAt(
                from = 100_000,
                segments = listOf(SkipSegment(SkipSegmentType.INTRO, startMs = 30_000, endMs = 60_000)),
            )
            every { playbackController.seekTo(any()) } answers { position = 40_000L }
            val vm = startedVM()

            seek(vm, PlayerAction.SeekBackward)
            wait(1_000)
            assertNull(contentState(vm).activeSkipSegment)

            wait(BURST_WINDOW_MS - 1_000 + PlayerCountdowns.TICK_MS)
            assertNotNull(contentState(vm).activeSkipSegment)
            vm.testCancelScope()
        }

    @Test
    fun creditsLoadedMidBurstRaiseNoNextEpisodePromptUntilTheBurstEnds() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            val credits = SkipSegment(SkipSegmentType.CREDITS, startMs = 2_300_000, endMs = 2_400_000)
            playingAt(from = 100_000)
            every { playbackController.seekTo(any()) } answers { position = 2_380_000L }
            coEvery { skipSegmentInteractor.loadSegments(any(), any(), any()) } coAnswers {
                delay(500)
                listOf(credits)
            }
            every { skipSegmentInteractor.findCreditsSegment(any()) } answers {
                firstArg<List<SkipSegment>>().firstOrNull()
            }
            val vm = startedVM()

            seek(vm)
            wait(1_000)
            assertNull(contentState(vm).nextEpisodeCountdown)

            wait(BURST_WINDOW_MS - 1_000 + PlayerCountdowns.TICK_MS)
            assertNotNull(contentState(vm).nextEpisodeCountdown)
            vm.testCancelScope()
        }

    @Test
    fun everySeekInTheBurstRestartsTheWindow() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            playingAt(
                from = 20_000,
                segments = listOf(SkipSegment(SkipSegmentType.INTRO, startMs = 30_000, endMs = 600_000)),
            )
            val vm = startedVM()

            seek(vm)
            wait(1_000)
            assertNull(contentState(vm).activeSkipSegment)
            seek(vm)
            wait(1_000)
            assertNull(contentState(vm).activeSkipSegment)
            seek(vm)
            wait(1_000)
            assertNull(contentState(vm).activeSkipSegment)

            wait(BURST_WINDOW_MS - 1_000 + PlayerCountdowns.TICK_MS)
            assertNotNull(contentState(vm).activeSkipSegment)
            vm.testCancelScope()
        }

    @Test
    fun aSkipLandingOnTheNextSegmentOffersItWithoutWaiting() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            playingAt(
                from = 7_500,
                segments = listOf(
                    SkipSegment(SkipSegmentType.INTRO, startMs = 10_000, endMs = 40_000),
                    SkipSegment(SkipSegmentType.RECAP, startMs = 41_000, endMs = 80_000),
                ),
            )
            val vm = startedVM()
            repeat(2) {
                position += PlayerCountdowns.TICK_MS / 2
                wait(PlayerCountdowns.TICK_MS / 2)
            }
            assertEquals(SkipSegmentType.INTRO, contentState(vm).activeSkipSegment?.type)

            vm.onAction(PlayerAction.SkipSegmentClicked)
            callbackSlot.captured.onSeekPerformed()
            runCurrent()
            wait(PlayerCountdowns.TICK_MS)

            assertEquals(SkipSegmentType.RECAP, contentState(vm).activeSkipSegment?.type)
            vm.testCancelScope()
        }
}
