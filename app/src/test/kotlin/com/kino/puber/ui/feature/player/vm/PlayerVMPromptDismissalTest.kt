package com.kino.puber.ui.feature.player.vm

import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.util.MainDispatcherExtension
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Season
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
            callbackSlot.captured.onSeekPerformed()
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

    private fun nextEpisodeIs1x2() {
        every { interactor.findNextEpisode(any(), any(), any()) } returns (1 to 2)
    }

    private fun verifySwitchedTo1x2() {
        verify { playbackController.release() }
        verify { interactor.resolveMedia(item = any(), seasonNumber = 1, episodeNumber = 2, videoNumber = null) }
    }

    private fun verifyNeverSwitched() {
        verify(exactly = 0) { playbackController.release() }
    }

    /** The tail of the episode is reached, so the tick raises the prompt. */
    private suspend fun TestScope.vmWithThePromptUp(): PlayerVM {
        every { playbackController.currentPosition } returns 2_380_000L
        val vm = startedVM()
        advanceTimeBy(600)
        assertNotNull(contentState(vm).nextEpisodeCountdown)
        return vm
    }

    /** The position loop never ends, so a failed assertion must still cancel the scope. */
    private fun promptUpTest(block: suspend TestScope.(PlayerVM) -> Unit) =
        runTest(mainDispatcher.dispatcher.scheduler) {
            nextEpisodeIs1x2()
            val vm = vmWithThePromptUp()
            try {
                block(vm)
            } finally {
                vm.testCancelScope()
            }
        }

    @Test
    fun aCancelledNextEpisodeStillStartsWhenTheEpisodeRunsOut() =
        promptUpTest { vm ->
            vm.onAction(PlayerAction.CancelNextEpisodeCountdown)
            assertNull(contentState(vm).nextEpisodeCountdown)
            verifyNeverSwitched()

            callbackSlot.captured.onPlaybackEnded()
            advanceTimeBy(2)

            verifySwitchedTo1x2()
        }

    @Test
    fun theSwitchOfAnEndedDismissedEpisodeLeavesThePlayersEndedCallbackFirst() =
        promptUpTest { vm ->
            vm.onAction(PlayerAction.CancelNextEpisodeCountdown)

            callbackSlot.captured.onPlaybackEnded()
            verifyNeverSwitched()

            advanceTimeBy(2)
            verifySwitchedTo1x2()
        }

    @Test
    fun backOnTheNextEpisodePromptStillStartsItWhenTheEpisodeRunsOut() =
        promptUpTest { vm ->
            vm.onAction(PlayerAction.OnBackPressed)
            callbackSlot.captured.onPlaybackEnded()
            advanceTimeBy(2)

            verifySwitchedTo1x2()
            verify(exactly = 0) { router.back(any(), any()) }
        }

    @Test
    fun theFinishedEpisodeIsMarkedWatchedBeforeTheNextOneLoads() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            nextEpisodeIs1x2()
            val vm = startedVM()
            vm.onAction(PlayerAction.CancelNextEpisodeCountdown)

            callbackSlot.captured.onPlaybackEnded()
            advanceTimeBy(2)

            coVerifyOrder {
                interactor.markCurrentAsWatched(42, 1, 1)
                playbackController.release()
            }
            vm.testCancelScope()
        }

    @Test
    fun anEndedDismissedEpisodeWithNoNextToFindKeepsTheEndedControls() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            val vm = startedVM()
            vm.onAction(PlayerAction.HideControls)
            assertFalse(contentState(vm).controlsVisible)
            vm.onAction(PlayerAction.CancelNextEpisodeCountdown)

            callbackSlot.captured.onPlaybackEnded()
            advanceTimeBy(2)

            assertTrue(contentState(vm).controlsVisible)
            assertNull(contentState(vm).nextEpisodeCountdown)
            verifyNeverSwitched()
            verify(exactly = 0) { router.back(any(), any()) }
            vm.testCancelScope()
        }

    @Test
    fun closingAPanelOverAnEndedDismissedEpisodeStartsTheNextOne() =
        promptUpTest { vm ->
            vm.onAction(PlayerAction.CancelNextEpisodeCountdown)
            vm.onAction(PlayerAction.OpenSettingsPanel)
            callbackSlot.captured.onPlaybackEnded()
            verifyNeverSwitched()

            vm.onAction(PlayerAction.ClosePanel)

            verifySwitchedTo1x2()
        }

    @Test
    fun aPanelOpenedInsideTheDeferralOfAnEndedDismissedEpisodeStartsTheNextOneOnClose() =
        promptUpTest { vm ->
            vm.onAction(PlayerAction.CancelNextEpisodeCountdown)
            callbackSlot.captured.onPlaybackEnded()
            vm.onAction(PlayerAction.OpenSettingsPanel)

            advanceTimeBy(2)
            verifyNeverSwitched()

            vm.onAction(PlayerAction.ClosePanel)
            verifySwitchedTo1x2()
        }

    @Test
    fun leavingInsideTheDeferralOfAnEndedDismissedEpisodeDoesNotSwitch() =
        promptUpTest { vm ->
            vm.onAction(PlayerAction.CancelNextEpisodeCountdown)
            callbackSlot.captured.onPlaybackEnded()
            vm.onAction(PlayerAction.OnBackPressed)
            vm.onAction(PlayerAction.OnBackPressed)
            verify { router.back(any(), any()) }

            advanceTimeBy(2)

            verifyNeverSwitched()
            verify(exactly = 0) { playbackController.release() }
        }

    @Test
    fun pickingAnEpisodeOverAnEndedDismissedEpisodePlaysThePickedOne() {
        nextEpisodeIs1x2()
        val item = testItem.copy(
            seasons = listOf(
                Season(
                    id = 1,
                    number = 1,
                    episodes = listOf(
                        Episode(id = 101, number = 1, title = "One"),
                        Episode(id = 102, number = 2, title = "Two"),
                        Episode(id = 103, number = 3, title = "Three"),
                    ),
                ),
            ),
        )
        coEvery { interactor.getItemDetails(any()) } returns item
        coEvery { interactor.markCurrentAsWatched(any(), any(), any()) } returns item
        val vm = startedVM()
        vm.onAction(PlayerAction.CancelNextEpisodeCountdown)
        vm.onAction(PlayerAction.OpenEpisodesPanel)
        callbackSlot.captured.onPlaybackEnded()

        vm.onAction(PlayerAction.SelectEpisodeById(103))

        verify { interactor.resolveMedia(item = any(), seasonNumber = 1, episodeNumber = 3, videoNumber = null) }
        verify(exactly = 0) {
            interactor.resolveMedia(item = any(), seasonNumber = 1, episodeNumber = 2, videoNumber = null)
        }
    }

    @Test
    fun aRestartUnderThePanelCancelsTheAdvanceOnClose() {
        nextEpisodeIs1x2()
        val vm = startedVM()
        vm.onAction(PlayerAction.CancelNextEpisodeCountdown)
        vm.onAction(PlayerAction.OpenSettingsPanel)
        callbackSlot.captured.onPlaybackEnded()
        callbackSlot.captured.onPlaybackStateChanged(
            isPlaying = true,
            isBuffering = false,
            position = 0L,
            duration = 2_400_000L,
            buffered = 0L,
        )

        vm.onAction(PlayerAction.ClosePanel)

        verifyNeverSwitched()
    }

    @Test
    fun aCancelAfterTheEndUnderAPanelDoesNotStartTheNextOneOnClose() {
        nextEpisodeIs1x2()
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenSettingsPanel)
        callbackSlot.captured.onPlaybackEnded()
        vm.onAction(PlayerAction.CancelNextEpisodeCountdown)

        vm.onAction(PlayerAction.ClosePanel)

        verifyNeverSwitched()
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
            callbackSlot.captured.onSeekPerformed()
            advanceTimeBy(1_200)
            callbackSlot.captured.onPlaybackEnded()

            assertNotNull(contentState(vm).nextEpisodeCountdown)
            vm.testCancelScope()
        }

    /**
     * The sampler runs every half second, but nothing guarantees it is on time: a warm-up, a
     * recomposition or a stall can hold it back, and the media moves on meanwhile. That is not the
     * viewer taking the wheel, and the prompt they are watching count down must survive it.
     */
    @Test
    fun aLateTickDoesNotTakeTheNextEpisodePromptDown() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            // Inside the tail the prompt is armed from: duration minus its lead-in.
            every { playbackController.currentPosition } returns 2_380_000L
            val vm = startedVM()
            advanceTimeBy(600)
            assertNotNull(contentState(vm).nextEpisodeCountdown)

            // One sample later the media has moved on by four seconds, no one having pressed
            // anything: the tick simply arrived late.
            every { playbackController.currentPosition } returns 2_384_000L
            advanceTimeBy(600)

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
            callbackSlot.captured.onSeekPerformed()
            advanceTimeBy(1_200)

            assertNull(contentState(vm).nextEpisodeCountdown)
            vm.testCancelScope()
        }
}
