package com.kino.puber.ui.feature.player.vm

import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.every
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * What happens when the viewer does not take the offer: says no, or moves around the episode.
 */
internal class PlayerVMPromptDismissalTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val intro = SkipSegment(SkipSegmentType.INTRO, startMs = 6_000, endMs = 40_000)

    private fun insideTheIntro() {
        every { skipSegmentInteractor.findActiveSegment(any(), any(), any()) } returns intro
        every { playbackController.currentPosition } returns 10_000L
    }

    @Test
    fun aCancelledSkipPromptStaysDownWhenTheViewerRewindsInsideTheSegment() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            insideTheIntro()
            val vm = startedVM()
            advanceTimeBy(1_200)
            assertNotNull(contentState(vm).activeSkipSegment)

            vm.onAction(PlayerAction.CancelSkipSegment)
            assertNull(contentState(vm).activeSkipSegment)

            every { playbackController.currentPosition } returns 7_000L
            advanceTimeBy(2_000)

            assertNull(contentState(vm).activeSkipSegment)
            vm.testCancelScope()
        }

    @Test
    fun aCancelledSkipPromptComesBackOnceItsWindowHasPassed() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            insideTheIntro()
            val vm = startedVM()
            advanceTimeBy(1_200)
            vm.onAction(PlayerAction.CancelSkipSegment)

            advanceTimeBy(59_000)
            assertNull(contentState(vm).activeSkipSegment)

            advanceTimeBy(2_000)
            assertNotNull(contentState(vm).activeSkipSegment)
            vm.testCancelScope()
        }

    @Test
    fun aCancelledNextEpisodeIsNotOfferedAgainWhenTheEpisodeRunsOut() {
        val vm = startedVM()
        callbackSlot.captured.onPlaybackEnded()
        assertNotNull(contentState(vm).nextEpisodeCountdown)

        vm.onAction(PlayerAction.CancelNextEpisodeCountdown)
        callbackSlot.captured.onPlaybackEnded()

        assertNull(contentState(vm).nextEpisodeCountdown)
    }

    @Test
    fun seekingAfterCancellingPutsTheNextEpisodeQuestionBack() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            every { playbackController.currentPosition } returns 100_000L
            val vm = startedVM()
            callbackSlot.captured.onPlaybackEnded()
            vm.onAction(PlayerAction.CancelNextEpisodeCountdown)

            // Rewound: the earlier "not this time" no longer speaks for where they are now.
            every { playbackController.currentPosition } returns 20_000L
            advanceTimeBy(1_200)
            callbackSlot.captured.onPlaybackEnded()

            assertNotNull(contentState(vm).nextEpisodeCountdown)
            vm.testCancelScope()
        }

    @Test
    fun seekingWhileTheNextEpisodePromptIsUpTakesItDown() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            every { playbackController.currentPosition } returns 100_000L
            val vm = startedVM()
            callbackSlot.captured.onPlaybackEnded()
            assertNotNull(contentState(vm).nextEpisodeCountdown)

            every { playbackController.currentPosition } returns 20_000L
            advanceTimeBy(1_200)

            assertNull(contentState(vm).nextEpisodeCountdown)
            vm.testCancelScope()
        }
}
