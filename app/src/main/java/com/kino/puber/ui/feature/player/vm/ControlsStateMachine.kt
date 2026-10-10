package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.FocusTarget

internal class ControlsStateMachine {

    data class State(
        val controlsVisible: Boolean = false,
        val focusTarget: FocusTarget? = null,
        val activePanel: ActivePanel = ActivePanel.None,
        /**
         * The controls are up because the media ran out, not because the viewer asked for them.
         * Back then leaves the player instead of tidying them away, since there is nothing behind
         * them left to watch.
         */
        val playbackEnded: Boolean = false,
        val episodesPanelHeld: Boolean = false,
    )

    sealed interface Effect {
        data object ScheduleHide : Effect
        data object CancelHide : Effect
        data object SaveAndExit : Effect

        /**
         * A panel came down, whichever way it was dismissed. What was put on screen for it has to
         * come down with it, so the caller gets told once instead of every dismissal path having to
         * remember.
         */
        data object PanelClosed : Effect
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

        state = state.copy(
            activePanel = panel,
            controlsVisible = false,
            focusTarget = null,
            episodesPanelHeld = false,
        )
        return if (panel == ActivePanel.Episodes) {
            listOf(Effect.ScheduleHide)
        } else {
            listOf(Effect.CancelHide)
        }
    }

    fun closePanel(): List<Effect> {
        if (state.activePanel == ActivePanel.None) return emptyList()
        state = state.copy(
            activePanel = ActivePanel.None,
            controlsVisible = true,
            focusTarget = lastPanelOpener,
            episodesPanelHeld = false,
        )
        return listOf(Effect.ScheduleHide, Effect.PanelClosed)
    }

    fun handleBack(): List<Effect> {
        return when {
            state.activePanel != ActivePanel.None -> closePanel()
            // Hiding the controls here would leave the viewer on a finished picture with nothing
            // to press, so the way out is out.
            state.playbackEnded -> listOf(Effect.SaveAndExit)
            state.controlsVisible -> hideControls()
            else -> listOf(Effect.SaveAndExit)
        }
    }

    /**
     * Controls put up by the end of the media, and left up.
     *
     * There is no picture left to reveal by hiding them, and a hide scheduled earlier would take
     * away the only thing on screen. This used to be written straight into the published content,
     * which left this class believing the controls were still down — harmless to look at, but the
     * reason back worked at all afterwards. The state says so now instead.
     */
    fun showControlsForEndedPlayback(): List<Effect> {
        state = state.copy(controlsVisible = true, focusTarget = null, playbackEnded = true)
        return listOf(Effect.CancelHide)
    }

    /** Playing again, so the controls go back to being the viewer's to summon and dismiss. */
    fun onPlaybackResumed() {
        if (state.playbackEnded) {
            state = state.copy(playbackEnded = false)
        }
    }

    /**
     * The 6 s without a key ran out. The bar hides; the Episodes row closes to the clean video,
     * or to the end-of-media controls once the media has run out; anything opened on purpose
     * (Settings, About, a held Episodes row, the ended controls) stays.
     */
    fun onHideTimeout(): List<Effect> {
        val panel = state.activePanel
        return when {
            panel == ActivePanel.Episodes && !state.episodesPanelHeld -> {
                state = if (state.playbackEnded) {
                    state.copy(
                        activePanel = ActivePanel.None,
                        controlsVisible = true,
                        focusTarget = lastPanelOpener,
                        episodesPanelHeld = false,
                    )
                } else {
                    state.copy(
                        activePanel = ActivePanel.None,
                        controlsVisible = false,
                        focusTarget = null,
                        episodesPanelHeld = false,
                    )
                }
                listOf(Effect.PanelClosed)
            }
            panel != ActivePanel.None -> emptyList()
            state.playbackEnded -> emptyList()
            else -> {
                state = state.copy(controlsVisible = false, focusTarget = null)
                emptyList()
            }
        }
    }

    /** Only the Episodes row runs on the idle timer among the panels, and not while it is held. */
    fun isEpisodesPanelAwaitingTimeout(): Boolean =
        state.activePanel == ActivePanel.Episodes && !state.episodesPanelHeld

    /**
     * The media ran out while a panel had the screen. Nothing changes on screen now; the panel
     * closes onto the end-of-media controls, and those stay until Back.
     */
    fun markPlaybackEnded() {
        state = state.copy(playbackEnded = true)
    }

    /** A dialog opened from the Episodes row keeps the row up; releasing it restarts the 6 s. */
    fun holdEpisodesPanel(held: Boolean): List<Effect> {
        if (state.activePanel != ActivePanel.Episodes) return emptyList()
        state = state.copy(episodesPanelHeld = held)
        return listOf(if (held) Effect.CancelHide else Effect.ScheduleHide)
    }
}
