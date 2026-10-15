package com.kino.puber.ui.feature.device.settings.mappers

import com.kino.puber.R
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.telemetry.SessionLogState
import com.kino.puber.ui.feature.device.settings.model.SessionLogUi

internal class SessionLogUiMapper(private val resources: ResourceProvider) {

    fun map(state: SessionLogState, sharing: Boolean): SessionLogUi {
        val hasFiles = when (state) {
            is SessionLogState.Off -> state.hasFiles
            is SessionLogState.Recording -> state.hasFiles
            SessionLogState.Unavailable -> false
        }
        return SessionLogUi(
            enabled = state is SessionLogState.Recording,
            description = describe(state),
            shareEnabled = hasFiles,
            sharing = sharing,
            shareSubtitle = resources.getString(
                when {
                    sharing -> R.string.settings_share_log_preparing
                    hasFiles -> R.string.settings_share_log_subtitle
                    else -> R.string.settings_share_log_empty
                },
            ),
        )
    }

    private fun describe(state: SessionLogState): String = when (state) {
        is SessionLogState.Off -> resources.getString(R.string.settings_session_log_off)
        SessionLogState.Unavailable -> resources.getString(R.string.settings_session_log_unavailable)
        is SessionLogState.Recording -> if (state.remainingMs < HOUR_MS) {
            resources.getString(R.string.settings_session_log_recording_under_hour)
        } else {
            resources.getString(
                R.string.settings_session_log_recording,
                (state.remainingMs / DAY_MS).toInt(),
                (state.remainingMs % DAY_MS / HOUR_MS).toInt(),
            )
        }
    }

    private companion object {
        const val HOUR_MS = 60L * 60 * 1000
        const val DAY_MS = 24 * HOUR_MS
    }
}
