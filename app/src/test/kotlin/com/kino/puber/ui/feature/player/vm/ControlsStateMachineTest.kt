package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.FocusTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ControlsStateMachineTest {

    private lateinit var machine: ControlsStateMachine

    @BeforeEach
    fun setUp() {
        machine = ControlsStateMachine()
    }

    // ------------------------------------------------------------------
    // showControls
    // ------------------------------------------------------------------

    @Test
    fun `showControls_setsVisible_returnsScheduleHide`() {
        val effects = machine.showControls(FocusTarget.Buttons)

        assertTrue(machine.state.controlsVisible)
        assertTrue(effects.contains(ControlsStateMachine.Effect.ScheduleHide))
    }

    @Test
    fun `showControls_setsFocusTarget`() {
        machine.showControls(FocusTarget.SeekBar)

        assertEquals(FocusTarget.SeekBar, machine.state.focusTarget)
    }

    @Test
    fun `showControls_canKeepExistingVideoFocus`() {
        machine.showControls(focusTarget = null)

        assertTrue(machine.state.controlsVisible)
        assertEquals(null, machine.state.focusTarget)
    }

    // ------------------------------------------------------------------
    // hideControls
    // ------------------------------------------------------------------

    @Test
    fun `hideControls_setsInvisible_returnsCancelHide`() {
        machine.showControls(FocusTarget.Buttons)

        val effects = machine.hideControls()

        assertFalse(machine.state.controlsVisible)
        assertTrue(effects.contains(ControlsStateMachine.Effect.CancelHide))
    }

    // ------------------------------------------------------------------
    // openPanel
    // ------------------------------------------------------------------

    @Test
    fun `openPanel_setsActivePanel`() {
        machine.openPanel(ActivePanel.Settings)

        assertEquals(ActivePanel.Settings, machine.state.activePanel)
    }

    @Test
    fun `openPanel_episodes_keepsPlaybackRunning`() {
        val effects = machine.openPanel(ActivePanel.Episodes)

        assertEquals(listOf(ControlsStateMachine.Effect.CancelHide), effects)
    }

    // ------------------------------------------------------------------
    // closePanel
    // ------------------------------------------------------------------

    @Test
    fun `closePanel_afterEpisodes_schedulesControlsHideAndReportsThePanelClosed`() {
        machine.openPanel(ActivePanel.Episodes)

        val effects = machine.closePanel()

        // The second effect is how the caller learns to drop what it put on screen for the panel.
        // Every way out of a panel comes through here, so no dismissal path can forget.
        assertEquals(
            listOf(ControlsStateMachine.Effect.ScheduleHide, ControlsStateMachine.Effect.PanelClosed),
            effects,
        )
    }

    @Test
    fun `handleBack_afterPlaybackEnded_leavesThePlayer`() {
        machine.showControlsForEndedPlayback()

        val effects = machine.handleBack()

        // The controls are up because the media ran out. Hiding them would strand the viewer on a
        // finished picture with nothing left to press.
        assertEquals(listOf(ControlsStateMachine.Effect.SaveAndExit), effects)
    }

    @Test
    fun `handleBack_afterPlaybackResumed_hidesTheControlsAgain`() {
        machine.showControlsForEndedPlayback()
        machine.onPlaybackResumed()

        val effects = machine.handleBack()

        assertEquals(listOf(ControlsStateMachine.Effect.CancelHide), effects)
        assertFalse(machine.state.controlsVisible)
    }

    @Test
    fun `showControlsForEndedPlayback_cancelsAPendingHide`() {
        machine.showControls(FocusTarget.Buttons)

        val effects = machine.showControlsForEndedPlayback()

        // Without this the hide scheduled while the viewer had the controls up fires a few seconds
        // later and takes away the only thing on the screen.
        assertEquals(listOf(ControlsStateMachine.Effect.CancelHide), effects)
        assertTrue(machine.state.controlsVisible)
    }

    @Test
    fun `openPanel_info_setsActivePanel`() {
        machine.openPanel(ActivePanel.About)

        assertEquals(ActivePanel.About, machine.state.activePanel)
    }

    @Test
    fun `closePanel_afterInfo_restoresInfoButtonFocus`() {
        machine.openPanel(ActivePanel.About)

        machine.closePanel()

        assertEquals(FocusTarget.AboutButton, machine.state.focusTarget)
    }

    @Test
    fun `closePanel_restoresFocusTarget`() {
        machine.openPanel(ActivePanel.Episodes)

        machine.closePanel()

        assertEquals(FocusTarget.EpisodesButton, machine.state.focusTarget)
    }

    // ------------------------------------------------------------------
    // handleBack
    // ------------------------------------------------------------------

    @Test
    fun `handleBack_withOpenPanel_closesPanel`() {
        machine.openPanel(ActivePanel.Settings)

        machine.handleBack()

        assertEquals(ActivePanel.None, machine.state.activePanel)
    }

    @Test
    fun `handleBack_withControlsVisible_noPanel_hidesControls`() {
        machine.showControls(FocusTarget.Buttons)

        machine.handleBack()

        assertFalse(machine.state.controlsVisible)
    }

    @Test
    fun `handleBack_nothingActive_returnsSaveAndExit`() {
        // Initial state: controls not visible, no panel open.
        val effects = machine.handleBack()

        assertTrue(effects.contains(ControlsStateMachine.Effect.SaveAndExit))
    }

    // ------------------------------------------------------------------
    // applyControlsVisibility
    // ------------------------------------------------------------------

    @Test
    fun `applyControlsVisibility_false_withOpenPanel_doesNotHide`() {
        machine.showControls(FocusTarget.Buttons)
        machine.openPanel(ActivePanel.Settings)
        // After openPanel, controlsVisible is already false; set it manually via
        // a fresh showControls call is not possible while panel is open, so we
        // verify the guard: calling applyControlsVisibility(false) when panel is
        // open must leave activePanel intact and not crash.
        machine.applyControlsVisibility(false)

        assertEquals(ActivePanel.Settings, machine.state.activePanel)
    }

    @Test
    fun `applyControlsVisibility_false_noPanel_hides`() {
        machine.showControls(FocusTarget.Buttons)

        machine.applyControlsVisibility(false)

        assertFalse(machine.state.controlsVisible)
    }
}
