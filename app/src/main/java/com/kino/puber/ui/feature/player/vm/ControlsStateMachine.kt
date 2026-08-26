package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.FocusTarget

internal class ControlsStateMachine {

    data class State(
        val controlsVisible: Boolean = false,
        val focusTarget: FocusTarget? = null,
        val activePanel: ActivePanel = ActivePanel.None,
    )

    sealed interface Effect {
        data object ScheduleHide : Effect
        data object CancelHide : Effect
        data object SaveAndExit : Effect
    }

    var state = State()
        private set

    private var lastPanelOpener: FocusTarget = FocusTarget.Buttons
    fun showControls(focusTarget: FocusTarget?): List<Effect> {
        state = state.copy(controlsVisible = true, focusTarget = focusTarget)
        return listOf(Effect.ScheduleHide)
    }

    fun hideControls(): List<Effect> {
        state = state.copy(controlsVisible = false, focusTarget = null)
        return listOf(Effect.CancelHide)
    }

    fun openPanel(panel: ActivePanel): List<Effect> {
        lastPanelOpener = when (panel) {
            ActivePanel.Episodes -> FocusTarget.EpisodesButton
            ActivePanel.About -> FocusTarget.AboutButton
            ActivePanel.Settings -> FocusTarget.SettingsButton
            ActivePanel.None -> FocusTarget.Buttons
        }

        state = state.copy(activePanel = panel, controlsVisible = false, focusTarget = null)
        return listOf(Effect.CancelHide)
    }

    fun closePanel(): List<Effect> {
        state = state.copy(
            activePanel = ActivePanel.None,
            controlsVisible = true,
            focusTarget = lastPanelOpener,
        )
        return listOf(Effect.ScheduleHide)
    }

    fun handleBack(): List<Effect> {
        return when {
            state.activePanel != ActivePanel.None -> closePanel()
            state.controlsVisible -> hideControls()
            else -> listOf(Effect.SaveAndExit)
        }
    }

    fun applyControlsVisibility(visible: Boolean) {
        if (!visible && state.activePanel == ActivePanel.None) {
            state = state.copy(controlsVisible = false, focusTarget = null)
        }
    }
}
