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
        // Focus stays on the video until the matching key-up, preventing a phantom button click.
        assertNull(contentState(vm).controlsFocusTarget)
    }

    @Test
    fun okReleased_focusesButtons_whenSettingDisabled() {
        givenOkTogglesPlayPause(enabled = false)
        val vm = startedVM()

        vm.onAction(PlayerAction.OkPressed)
        vm.onAction(PlayerAction.OkReleased)

        verify(exactly = 0) { playbackController.pause() }
        assertEquals(FocusTarget.Buttons, contentState(vm).controlsFocusTarget)
    }

    @Test
    fun okReleased_doesNotChangeFocus_whenSettingEnabled() {
        givenOkTogglesPlayPause(enabled = true)
        val vm = startedVM()
        vm.onAction(PlayerAction.HideControls)

        vm.onAction(PlayerAction.OkReleased)

        assertFalse(contentState(vm).controlsVisible)
        assertNull(contentState(vm).controlsFocusTarget)
    }

    @Test
    fun okPressed_doesNotTogglePlayback_whenSettingDisabled() {
        givenOkTogglesPlayPause(enabled = false)
        every { playbackController.isPlaying } returns true

        startedVM().onAction(PlayerAction.OkPressed)

        verify(exactly = 0) { playbackController.pause() }
    }

    // endregion

    // region Settings panel

    @Test
    fun openSettingsPanel_setsActivePanel() {
        val vm = startedVM()

        vm.onAction(PlayerAction.OpenSettingsPanel)

        assertEquals(ActivePanel.Settings, contentState(vm).activePanel)
    }

    @Test
    fun openSettingsPanel_keepsPlaying() {
        val vm = startedVM()

        vm.onAction(PlayerAction.OpenSettingsPanel)

        verify(exactly = 0) { playbackController.pause() }
    }

    @Test
    fun openSettingsPanel_readsDebugInfoImmediately() {
        every { playbackController.getDebugInfo() } returns testDebugInfo
        val vm = startedVM()

        vm.onAction(PlayerAction.OpenSettingsPanel)

        assertEquals(testDebugInfo, contentState(vm).debugInfo)
    }

    @Test
    fun closeSettingsPanel_dropsDebugInfo_whenDebugOverlayDisabled() {
        every { playbackController.getDebugInfo() } returns testDebugInfo
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenSettingsPanel)

        vm.onAction(PlayerAction.ClosePanel)

        assertNull(contentState(vm).debugInfo)
    }

    @Test
    fun closeSettingsPanel_keepsDebugInfo_whenDebugOverlayEnabled() {
        every { interactor.getBehaviourPreferences() } returns PlayerBehaviourPreferences(
            debugOverlayEnabled = true,
            okTogglesPlayPause = false,
        )
        every { playbackController.getDebugInfo() } returns testDebugInfo
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenSettingsPanel)

        vm.onAction(PlayerAction.ClosePanel)

        assertEquals(testDebugInfo, contentState(vm).debugInfo)
    }

    /** Back is the usual way out of the panel, and must drop the readings like the gear does. */
    @Test
    fun backOutOfSettingsPanel_dropsDebugInfo_whenDebugOverlayDisabled() {
        every { playbackController.getDebugInfo() } returns testDebugInfo
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenSettingsPanel)

        vm.onAction(PlayerAction.OnBackPressed)

        assertEquals(ActivePanel.None, contentState(vm).activePanel)
        assertNull(contentState(vm).debugInfo)
    }

    @Test
    fun backOutOfSettingsPanel_keepsDebugInfo_whenDebugOverlayEnabled() {
        every { interactor.getBehaviourPreferences() } returns PlayerBehaviourPreferences(
            debugOverlayEnabled = true,
            okTogglesPlayPause = false,
        )
        every { playbackController.getDebugInfo() } returns testDebugInfo
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenSettingsPanel)

        vm.onAction(PlayerAction.OnBackPressed)

        assertEquals(testDebugInfo, contentState(vm).debugInfo)
    }

    /** The description is not a diagnostics surface: opening it must not start the readings. */
    @Test
    fun openAboutPanel_doesNotReadDebugInfo() {
        every { playbackController.getDebugInfo() } returns testDebugInfo
        val vm = startedVM()

        vm.onAction(PlayerAction.OpenAboutPanel)

        assertEquals(ActivePanel.About, contentState(vm).activePanel)
        assertNull(contentState(vm).debugInfo)
        verify(exactly = 0) { playbackController.getDebugInfo() }
    }

    @Test
    fun playbackEnded_keepsSettingsPanel_withoutRevealingControls() {
        coEvery {
            contentStateFactory.build(any(), any(), any(), any(), any(), any())
        } returns testContentState.copy(hasNextEpisode = false)
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenSettingsPanel)

        callbackSlot.captured.onPlaybackEnded()

        assertEquals(ActivePanel.Settings, contentState(vm).activePanel)
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
        bufferedBytes = "38.0 / 64 MB",
        streamSource = "MSK01",
        renderRate = "24.0 / 24 fps",
        frameDrops = "0 (keyframe 0, run 0)",
        frameReleaseOffset = "+0.0 ms",
        videoSwitch = "—",
    )
}
