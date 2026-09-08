package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.PlayerCountdowns
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * The next-episode prompt is written to the state once and left alone until its action runs.
 *
 * It used to be rewritten every second with the seconds left. Each write is a fresh screen state,
 * and on the television the recomposition that follows takes longer than a frame — so the bar,
 * which is animated frame by frame in the overlay, skipped a frame on every tick. The overlay runs
 * the clock for the bar and the digit together; the view model only needs to know when to act.
 */
internal class PlayerVMCountdownClockTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val wholeWait =
        (PlayerCountdowns.NEXT_EPISODE_SEC + PlayerCountdowns.ZERO_TICK_SEC) * PlayerCountdowns.TICK_MS

    @Test
    fun nextEpisodeCountdown_isWrittenOnceAndNotTickedDown() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            every { interactor.findNextEpisode(any(), any(), any()) } returns (1 to 2)
            val vm = startedVM()
            callbackSlot.captured.onPlaybackEnded()
            val prompt = contentState(vm)

            advanceTimeBy(3 * PlayerCountdowns.TICK_MS)
            runCurrent()

            assertEquals(PlayerCountdowns.NEXT_EPISODE_SEC, contentState(vm).nextEpisodeCountdown)
            assertEquals(prompt, contentState(vm))
            vm.testCancelScope()
        }

    @Test
    fun nextEpisodeCountdown_switchesOnlyWhenTheWholeWaitHasRun() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            every { interactor.findNextEpisode(any(), any(), any()) } returns (1 to 2)
            val vm = startedVM()
            callbackSlot.captured.onPlaybackEnded()

            // Zero is shown for a second of its own, so the switch is a tick later than the count.
            advanceTimeBy(wholeWait - 1)
            runCurrent()
            verify(exactly = 0) { playbackController.release() }

            advanceTimeBy(1)
            runCurrent()
            verify { playbackController.release() }
            vm.testCancelScope()
        }
}
