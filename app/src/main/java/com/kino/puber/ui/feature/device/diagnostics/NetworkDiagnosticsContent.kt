package com.kino.puber.ui.feature.device.diagnostics

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.diagnostics.DiagnosticStep
import com.kino.puber.domain.interactor.diagnostics.DiagnosticsAdvice
import com.kino.puber.domain.interactor.diagnostics.FailureReason
import com.kino.puber.domain.interactor.diagnostics.QualityCeiling
import com.kino.puber.domain.interactor.diagnostics.SkipReason
import com.kino.puber.domain.interactor.diagnostics.StepState
import com.kino.puber.ui.feature.device.diagnostics.model.DiagnosticStepUi
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsActions
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsViewState
import java.util.Locale

private val ScreenHorizontalPadding = 48.dp
private val ScreenVerticalPadding = 28.dp
private const val BITS_PER_MEGABIT = 1_000_000.0

@Composable
internal fun NetworkDiagnosticsContent(
    state: NetworkDiagnosticsViewState,
    onAction: (UIAction) -> Unit = {},
) {
    val primaryFocusRequester = rememberFocusRequesterOnLaunch()
    // The proposal is not on screen when the screen opens — it appears when the run ends — so a
    // requester that fired at first composition has already spent itself on the button below.
    // The proposal is a change to the user's settings and has to be what their thumb is on, so it
    // takes focus at the moment it appears instead.
    val mirrorFocusRequester = remember { FocusRequester() }
    val proposal = state.advice?.mirrorProposal
    // Whether the proposal was on screen the last time this ran, so its disappearance — the switch
    // resolved and the button left composition — can be told apart from a screen that never had a
    // proposal to begin with. Only the former has to hand focus back to the primary action.
    var proposalWasShown by remember { mutableStateOf(false) }
    LaunchedEffect(proposal) {
        if (proposal != null) {
            proposalWasShown = true
            mirrorFocusRequester.requestFocus()
        } else if (proposalWasShown) {
            proposalWasShown = false
            primaryFocusRequester.requestFocus()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ScreenHorizontalPadding, vertical = ScreenVerticalPadding),
        ) {
            Text(
                text = stringResource(R.string.diagnostics_screen_title),
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(modifier = Modifier.height(20.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(NetworkDiagnosticsTestTags.Steps),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.steps, key = { it.step.name }) { row ->
                    StepRow(
                        row = row,
                        // The one host each row is allowed to name, and only on the row it is the
                        // subject of: the current mirror beside the reachability check that is
                        // about it, the mirror the sweep found beside the sweep that found it.
                        detail = when (row.step) {
                            DiagnosticStep.ApiReachability -> state.apiDomain
                            DiagnosticStep.MirrorSweep -> state.workingMirrorDomain
                            else -> null
                        },
                    )
                }
            }

            state.advice?.let { advice ->
                Text(
                    text = summaryText(advice),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(NetworkDiagnosticsTestTags.Summary),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // A confirmed settings change earns standing confirmation rather than a message that
            // disappears: this is a television, and the person who pressed the button may well
            // have looked away by the time it lands.
            state.appliedMirror?.let { mirror ->
                Text(
                    text = stringResource(R.string.diagnostics_mirror_switched, mirror),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(NetworkDiagnosticsTestTags.AppliedMirror),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (proposal != null) {
                Text(
                    text = stringResource(R.string.diagnostics_mirror_proposal, state.apiDomain, proposal),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(NetworkDiagnosticsTestTags.MirrorProposal),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (proposal != null) {
                    Button(
                        onClick = { onAction(NetworkDiagnosticsActions.ConfirmMirrorSwitch) },
                        // Disabling this button while the switch is in flight would drop it out of
                        // the focus chain the user's thumb is already on. The view model already
                        // ignores a repeat press while applying, so staying enabled is safe — the
                        // button's own label is what tells the user a press landed.
                        modifier = Modifier
                            .focusRequester(mirrorFocusRequester)
                            .testTag(NetworkDiagnosticsTestTags.MirrorSwitch),
                    ) {
                        Text(
                            if (state.applyingMirror) {
                                stringResource(R.string.diagnostics_mirror_switching)
                            } else {
                                stringResource(R.string.diagnostics_mirror_switch, proposal)
                            }
                        )
                    }
                }
                Button(
                    onClick = {
                        onAction(
                            if (state.running) {
                                NetworkDiagnosticsActions.Cancel
                            } else {
                                NetworkDiagnosticsActions.Restart
                            }
                        )
                    },
                    modifier = Modifier
                        .focusRequester(primaryFocusRequester)
                        .testTag(NetworkDiagnosticsTestTags.PrimaryAction),
                ) {
                    Text(
                        stringResource(
                            if (state.running) R.string.diagnostics_cancel else R.string.diagnostics_restart
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(row: DiagnosticStepUi, detail: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(NetworkDiagnosticsTestTags.step(row.step.name))
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(row.step.titleRes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (!detail.isNullOrBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )
        }
        Text(
            text = stateText(row.state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val DiagnosticStep.titleRes: Int
    get() = when (this) {
        DiagnosticStep.ApiReachability -> R.string.diagnostics_step_api_reachability
        DiagnosticStep.NameResolution -> R.string.diagnostics_step_name_resolution
        DiagnosticStep.ApiResponsiveness -> R.string.diagnostics_step_api_responsiveness
        DiagnosticStep.MediaThroughput -> R.string.diagnostics_step_media_throughput
        DiagnosticStep.MirrorSweep -> R.string.diagnostics_step_mirror_sweep
    }

@Composable
private fun stateText(state: StepState): String = when (state) {
    StepState.Pending -> stringResource(R.string.diagnostics_state_pending)
    StepState.Running -> stringResource(R.string.diagnostics_state_running)
    is StepState.Success -> successText(state)
    is StepState.Failure -> stringResource(
        when (state.reason) {
            FailureReason.Unreachable -> R.string.diagnostics_failure_unreachable
            FailureReason.ResolutionFailed -> R.string.diagnostics_failure_resolution
            FailureReason.RequestFailed -> R.string.diagnostics_failure_request
        }
    )
    is StepState.Skipped -> stringResource(
        when (state.reason) {
            SkipReason.NoNetwork -> R.string.diagnostics_skipped_no_network
            SkipReason.NoMediaLink -> R.string.diagnostics_skipped_no_media_link
            SkipReason.CurrentMirrorAnswers -> R.string.diagnostics_skipped_mirror_ok
        }
    )
}

@Composable
private fun successText(state: StepState.Success): String = when {
    state.sample != null -> stringResource(
        R.string.diagnostics_rate_mbits,
        formatMegabits(state.sample.bitsPerSecond),
    )
    state.latencyMillis != null -> stringResource(
        R.string.diagnostics_latency_millis,
        state.latencyMillis,
    )
    else -> ""
}

@Composable
private fun summaryText(advice: DiagnosticsAdvice): String {
    val api = stringResource(
        if (advice.apiReachable) {
            R.string.diagnostics_summary_api_ok
        } else {
            R.string.diagnostics_summary_api_down
        }
    )
    val rate = advice.mediaBitsPerSecond
    val media = if (rate == null || advice.ceiling == null) {
        stringResource(R.string.diagnostics_summary_media_unknown)
    } else {
        stringResource(
            when (advice.ceiling) {
                QualityCeiling.TooSlow -> R.string.diagnostics_summary_media_too_slow
                QualityCeiling.Hd720 -> R.string.diagnostics_summary_media_720
                QualityCeiling.Hd1080 -> R.string.diagnostics_summary_media_1080
                QualityCeiling.Uhd4k -> R.string.diagnostics_summary_media_4k
            },
            formatMegabits(rate),
        )
    }
    // Location and streaming type are the two settings a slow link is worth revisiting, and neither
    // can be measured from here — so they are named as something to try, never offered as a button.
    val hint = when (advice.ceiling) {
        QualityCeiling.TooSlow, QualityCeiling.Hd720 ->
            " " + stringResource(R.string.diagnostics_summary_slow_hint)
        else -> ""
    }
    return "$api $media$hint"
}

private fun formatMegabits(bitsPerSecond: Double): String =
    String.format(Locale.getDefault(), "%.1f", bitsPerSecond / BITS_PER_MEGABIT)
