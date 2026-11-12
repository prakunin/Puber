package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

/**
 * The 500ms position tick copies the whole content state, and every one of the six layers under
 * `PlayerContent` takes that object as its parameter — so a copy nothing is looking at still costs a
 * recomposition pass across the entire player. While the controls are hidden nothing on screen is
 * showing a position, and the tick's own checks read the controller directly rather than the state.
 */
internal class PlayerPositionPublishingTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    @Test
    fun positionIsNotRepublishedWhileTheControlsAreHidden() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            val vm = startedVM()
            vm.onAction(PlayerAction.HideControls)
            assertFalse(contentState(vm).controlsVisible)
            val before = contentState(vm)
            every { playbackController.currentPosition } returns 90_000L

            advanceTimeBy(2_000)

            assertEquals(before.currentPosition, contentState(vm).currentPosition)
            vm.testCancelScope()
        }

    @Test
    fun positionIsPublishedWhileTheControlsAreVisible() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            val vm = startedVM()
            val before = contentState(vm)
            every { playbackController.currentPosition } returns 90_000L

            advanceTimeBy(2_000)

            assertNotEquals(before.currentPosition, contentState(vm).currentPosition)
            assertEquals(90_000L, contentState(vm).currentPosition)
            vm.testCancelScope()
        }

    /** The settings panel is the one surface that keeps its readings live with the controls hidden. */
    @Test
    fun positionIsPublishedForTheSettingsPanelWithTheControlsHidden() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            val vm = startedVM()
            vm.onAction(PlayerAction.OpenSettingsPanel)
            assertFalse(contentState(vm).controlsVisible)
            assertEquals(ActivePanel.Settings, contentState(vm).activePanel)
            every { playbackController.currentPosition } returns 90_000L

            advanceTimeBy(2_000)

            assertEquals(90_000L, contentState(vm).currentPosition)
            vm.testCancelScope()
        }

    /**
     * Auto-mark, the early next-episode prompt and skip segments all run off this same tick and read
     * the controller directly. Silencing the publish must not silence them.
     */
    @Test
    fun theTickStillMarksWatchedWithTheControlsHidden() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            val vm = startedVM()
            vm.onAction(PlayerAction.HideControls)
            every { playbackController.currentPosition } returns 2_390_000L

            advanceTimeBy(2_000)

            assertEquals(true, contentState(vm).isCurrentMediaWatched)
            vm.testCancelScope()
        }

    @Test
    fun theEndedControlsShowTheFinalPosition() {
        every { interactor.findNextEpisode(any(), any(), any()) } returns (1 to 2)
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenSettingsPanel)
        every { playbackController.isPlaying } returns false
        every { playbackController.currentPosition } returns 2_410_000L
        every { playbackController.duration } returns 2_410_000L
        every { playbackController.bufferedPosition } returns 2_410_000L

        callbackSlot.captured.onPlaybackEnded()
        vm.onAction(PlayerAction.CancelNextEpisodeCountdown)
        vm.onAction(PlayerAction.ClosePanel)

        val content = contentState(vm)
        assertTrue(content.controlsVisible)
        assertEquals(2_410_000L, content.currentPosition)
        assertEquals(2_410_000L, content.duration)
        assertEquals(2_410_000L, content.bufferedPosition)
    }

    @Test
    fun theEndedControlsAfterAMissingNextEpisodeShowTheFinalPosition() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            val vm = startedVM()
            vm.onAction(PlayerAction.HideControls)
            vm.onAction(PlayerAction.CancelNextEpisodeCountdown)
            every { playbackController.isPlaying } returns false
            every { playbackController.currentPosition } returns 2_410_000L
            every { playbackController.duration } returns 2_410_000L
            every { playbackController.bufferedPosition } returns 2_410_000L

            callbackSlot.captured.onPlaybackEnded()
            advanceTimeBy(2)

            val content = contentState(vm)
            assertTrue(content.controlsVisible)
            assertEquals(2_410_000L, content.currentPosition)
            assertEquals(2_410_000L, content.duration)
            vm.testCancelScope()
        }

    @Test
    fun theEndedControlsOverARunningCountdownShowTheFinalPositionAndKeepTheCountdown() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            every { interactor.findNextEpisode(any(), any(), any()) } returns (1 to 2)
            every { playbackController.currentPosition } returns 2_380_000L
            val vm = startedVM()
            advanceTimeBy(600)
            assertNotNull(contentState(vm).nextEpisodeCountdown)
            vm.onAction(PlayerAction.HideControls)
            every { playbackController.isPlaying } returns false
            every { playbackController.currentPosition } returns 2_410_000L
            every { playbackController.duration } returns 2_410_000L

            callbackSlot.captured.onPlaybackEnded()
            advanceTimeBy(2)

            val content = contentState(vm)
            assertTrue(content.controlsVisible)
            assertEquals(2_410_000L, content.currentPosition)
            assertNotNull(content.nextEpisodeCountdown)
            verify(exactly = 0) { playbackController.release() }
            vm.testCancelScope()
        }
}
