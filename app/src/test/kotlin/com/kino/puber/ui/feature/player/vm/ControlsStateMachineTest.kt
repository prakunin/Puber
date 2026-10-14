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
    // initialize
    // ------------------------------------------------------------------

    @Test
    fun `initialize_withoutResumeDialog_showsControlsOnButtons`() {
        machine.initialize(resumeDialogVisible = false)

        assertTrue(machine.state.controlsVisible)
        assertEquals(FocusTarget.Buttons, machine.state.focusTarget)
        assertEquals(ActivePanel.None, machine.state.activePanel)
    }

    @Test
    fun `initialize_withResumeDialog_keepsControlsHidden`() {
        machine.initialize(resumeDialogVisible = true)

        assertFalse(machine.state.controlsVisible)
        assertEquals(null, machine.state.focusTarget)
    }

    @Test
    fun `initialize_dropsWhatThePreviousMediaLeft`() {
        machine.openPanel(ActivePanel.Episodes)
        machine.holdEpisodesPanel(true)
        assertTrue(machine.state.episodesPanelHeld)
        machine.showControlsForEndedPlayback()

        machine.initialize(resumeDialogVisible = false)

        assertFalse(machine.state.episodesPanelHeld)
        assertEquals(ActivePanel.None, machine.state.activePanel)
        assertFalse(machine.state.playbackEnded)
    }

    @Test
    fun `initialize_thenBack_hidesControlsInsteadOfExiting`() {
        machine.initialize(resumeDialogVisible = false)

        val effects = machine.handleBack()

        assertFalse(machine.state.controlsVisible)
        assertFalse(effects.contains(ControlsStateMachine.Effect.SaveAndExit))
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

        assertEquals(listOf(ControlsStateMachine.Effect.ScheduleHide), effects)
        assertFalse(machine.state.controlsVisible)
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
    // onHideTimeout
    // ------------------------------------------------------------------

    @Test
    fun `onHideTimeout_withSettingsOpen_doesNotHide`() {
        machine.showControls(FocusTarget.Buttons)
        machine.openPanel(ActivePanel.Settings)
        val before = machine.state

        val effects = machine.onHideTimeout()

        assertEquals(before, machine.state)
        assertEquals(emptyList<ControlsStateMachine.Effect>(), effects)
    }

    @Test
    fun `onHideTimeout_noPanel_hidesControls`() {
        machine.showControls(FocusTarget.Buttons)

        val effects = machine.onHideTimeout()

        assertFalse(machine.state.controlsVisible)
        assertEquals(null, machine.state.focusTarget)
        assertEquals(emptyList<ControlsStateMachine.Effect>(), effects)
    }

    @Test
    fun `onHideTimeout_episodesOpen_closesToCleanVideoAndReportsPanelClosed`() {
        machine.openPanel(ActivePanel.Episodes)

        val effects = machine.onHideTimeout()

        assertEquals(ActivePanel.None, machine.state.activePanel)
        assertFalse(machine.state.controlsVisible)
        assertEquals(null, machine.state.focusTarget)
        assertEquals(listOf(ControlsStateMachine.Effect.PanelClosed), effects)
    }

    @Test
    fun `onHideTimeout_withAboutOpen_returnsNoEffects`() {
        machine.openPanel(ActivePanel.About)

        val effects = machine.onHideTimeout()

        assertEquals(ActivePanel.About, machine.state.activePanel)
        assertEquals(emptyList<ControlsStateMachine.Effect>(), effects)
    }

    @Test
    fun `onHideTimeout_afterPlaybackEnded_keepsControlsUp`() {
        machine.showControlsForEndedPlayback()
        val before = machine.state

        val effects = machine.onHideTimeout()

        assertEquals(before, machine.state)
        assertEquals(emptyList<ControlsStateMachine.Effect>(), effects)
    }

    @Test
    fun `closePanel_withNoPanel_returnsNoEffectsAndLeavesStateAlone`() {
        val before = machine.state

        val effects = machine.closePanel()

        assertEquals(before, machine.state)
        assertEquals(emptyList<ControlsStateMachine.Effect>(), effects)
    }

    @Test
    fun `onHideTimeout_episodesOpenAfterPlaybackEnded_landsOnEndedControls`() {
        machine.openPanel(ActivePanel.Episodes)
        machine.markPlaybackEnded()

        val effects = machine.onHideTimeout()

        assertEquals(listOf(ControlsStateMachine.Effect.PanelClosed), effects)
        assertTrue(machine.state.controlsVisible)
        assertEquals(FocusTarget.EpisodesButton, machine.state.focusTarget)
        assertTrue(machine.state.playbackEnded)

        val again = machine.onHideTimeout()

        assertEquals(emptyList<ControlsStateMachine.Effect>(), again)
        assertTrue(machine.state.controlsVisible)
    }

    @Test
    fun `isEpisodesPanelAwaitingTimeout_onlyForAnOpenEpisodesPanel`() {
        assertFalse(machine.isEpisodesPanelAwaitingTimeout())
        machine.openPanel(ActivePanel.Settings)
        assertFalse(machine.isEpisodesPanelAwaitingTimeout())
        machine.openPanel(ActivePanel.About)
        assertFalse(machine.isEpisodesPanelAwaitingTimeout())
        machine.openPanel(ActivePanel.Episodes)
        assertTrue(machine.isEpisodesPanelAwaitingTimeout())
    }

    @Test
    fun `holdEpisodesPanel_true_cancelsHideAndTimeoutKeepsPanel`() {
        machine.openPanel(ActivePanel.Episodes)

        val held = machine.holdEpisodesPanel(true)
        val timeout = machine.onHideTimeout()

        assertEquals(listOf(ControlsStateMachine.Effect.CancelHide), held)
        assertEquals(emptyList<ControlsStateMachine.Effect>(), timeout)
        assertEquals(ActivePanel.Episodes, machine.state.activePanel)
    }

    @Test
    fun `holdEpisodesPanel_false_schedulesHide`() {
        machine.openPanel(ActivePanel.Episodes)
        machine.holdEpisodesPanel(true)

        val effects = machine.holdEpisodesPanel(false)

        assertEquals(listOf(ControlsStateMachine.Effect.ScheduleHide), effects)
        assertFalse(machine.state.episodesPanelHeld)
    }

    @Test
    fun `holdEpisodesPanel_withOtherPanelOrNone_isNoOp`() {
        val none = machine.state
        assertEquals(emptyList<ControlsStateMachine.Effect>(), machine.holdEpisodesPanel(true))
        assertEquals(none, machine.state)

        machine.openPanel(ActivePanel.Settings)
        val settings = machine.state
        assertEquals(emptyList<ControlsStateMachine.Effect>(), machine.holdEpisodesPanel(true))
        assertEquals(settings, machine.state)
    }

    @Test
    fun `openPanel_and_closePanel_clearTheHeldFlag`() {
        machine.openPanel(ActivePanel.Episodes)
        machine.holdEpisodesPanel(true)
        machine.openPanel(ActivePanel.Episodes)
        assertFalse(machine.state.episodesPanelHeld)

        machine.holdEpisodesPanel(true)
        machine.closePanel()
        assertFalse(machine.state.episodesPanelHeld)
    }

    @Test
    fun `isEpisodesPanelAwaitingTimeout_isFalseWhileHeld`() {
        machine.openPanel(ActivePanel.Episodes)
        machine.holdEpisodesPanel(true)

        assertFalse(machine.isEpisodesPanelAwaitingTimeout())
    }
}
