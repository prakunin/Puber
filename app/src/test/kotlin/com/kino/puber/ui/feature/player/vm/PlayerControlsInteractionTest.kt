package com.kino.puber.ui.feature.player.vm

import com.kino.puber.domain.interactor.player.PlayerBehaviourPreferences
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * How the player reacts to the remote: what OK does, and the info panel it can open.
 * See [PlayerVMTest] for why these tests avoid `runTest`.
 */
internal class PlayerControlsInteractionTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    // region OK button

    @Test
    fun okPressed_togglesPlayPause_whenSettingEnabled() {
        givenOkTogglesPlayPause(enabled = true)
        every { playbackController.isPlaying } returns true

        startedVM().onAction(PlayerAction.OkPressed)

        verify { playbackController.pause() }
    }

    @Test
    fun okPressed_showsControls_whenSettingDisabled() {
        givenOkTogglesPlayPause(enabled = false)
        val vm = startedVM()
        vm.onAction(PlayerAction.HideControls)

        vm.onAction(PlayerAction.OkPressed)

        assertTrue(contentState(vm).controlsVisible)
        // Not the button row: its first button is play/pause, and holding OK would click it.
        assertEquals(FocusTarget.SeekBar, contentState(vm).controlsFocusTarget)
    }

    @Test
    fun okPressed_onVisibleControls_neverPauses_whenSettingDisabled() {
        givenOkTogglesPlayPause(enabled = false)
        val vm = startedVM()

        repeat(3) { vm.onAction(PlayerAction.OkPressed) }

        verify(exactly = 0) { playbackController.pause() }
        assertEquals(FocusTarget.SeekBar, contentState(vm).controlsFocusTarget)
    }

    @Test
    fun okPressed_doesNotTogglePlayback_whenSettingDisabled() {
        givenOkTogglesPlayPause(enabled = false)
        every { playbackController.isPlaying } returns true

        startedVM().onAction(PlayerAction.OkPressed)

        verify(exactly = 0) { playbackController.pause() }
    }

    // endregion

    // region Info panel

    @Test
    fun openInfoPanel_setsActivePanel() {
        val vm = startedVM()

        vm.onAction(PlayerAction.OpenInfoPanel)

        assertEquals(ActivePanel.Info, contentState(vm).activePanel)
    }

    @Test
    fun openInfoPanel_keepsPlaying() {
        val vm = startedVM()

        vm.onAction(PlayerAction.OpenInfoPanel)

        verify(exactly = 0) { playbackController.pause() }
    }

    @Test
    fun openInfoPanel_readsDebugInfoImmediately() {
        every { playbackController.getDebugInfo() } returns testDebugInfo
        val vm = startedVM()

        vm.onAction(PlayerAction.OpenInfoPanel)

        assertEquals(testDebugInfo, contentState(vm).debugInfo)
    }

    @Test
    fun closeInfoPanel_dropsDebugInfo_whenDebugOverlayDisabled() {
        every { playbackController.getDebugInfo() } returns testDebugInfo
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenInfoPanel)

        vm.onAction(PlayerAction.ClosePanel)

        assertNull(contentState(vm).debugInfo)
    }

    @Test
    fun closeInfoPanel_keepsDebugInfo_whenDebugOverlayEnabled() {
        every { interactor.getBehaviourPreferences() } returns PlayerBehaviourPreferences(
            debugOverlayEnabled = true,
            okTogglesPlayPause = false,
        )
        every { playbackController.getDebugInfo() } returns testDebugInfo
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenInfoPanel)

        vm.onAction(PlayerAction.ClosePanel)

        assertEquals(testDebugInfo, contentState(vm).debugInfo)
    }

    @Test
    fun playbackEnded_keepsInfoPanel_withoutRevealingControls() {
        coEvery {
            contentStateFactory.build(any(), any(), any(), any(), any(), any())
        } returns testContentState.copy(hasNextEpisode = false)
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenInfoPanel)

        callbackSlot.captured.onPlaybackEnded()

        assertEquals(ActivePanel.Info, contentState(vm).activePanel)
        assertFalse(contentState(vm).controlsVisible)
    }

    // endregion

    private fun givenOkTogglesPlayPause(enabled: Boolean) {
        every { interactor.getBehaviourPreferences() } returns PlayerBehaviourPreferences(
            debugOverlayEnabled = false,
            okTogglesPlayPause = enabled,
        )
    }

    private val testDebugInfo = PlaybackControl.DebugInfo(
        videoResolution = "1920x1080",
        videoCodec = "avc1",
        videoBitrate = "8.0 Mbps",
        videoFrameRate = "24 fps",
        audioCodec = "mp4a",
        audioChannels = "stereo",
        droppedFrames = "0",
        bufferedDuration = "12.0s",
    )
}
