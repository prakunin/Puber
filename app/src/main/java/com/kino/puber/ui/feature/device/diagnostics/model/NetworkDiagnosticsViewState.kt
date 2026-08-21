package com.kino.puber.ui.feature.device.diagnostics.model

import androidx.compose.runtime.Immutable
import com.kino.puber.domain.interactor.diagnostics.DiagnosticStep
import com.kino.puber.domain.interactor.diagnostics.DiagnosticsAdvice
import com.kino.puber.domain.interactor.diagnostics.StepState

/** One row: which measurement, and where it got to. */
@Immutable
internal data class DiagnosticStepUi(
    val step: DiagnosticStep,
    val state: StepState,
)

/**
 * The run's `Map` becomes an ordered `List` here rather than being handed to Compose as it is: a
 * map is not a stable key source for a list, and the order the steps are drawn in is a decision the
 * screen owns.
 */
@Immutable
internal data class NetworkDiagnosticsViewState(
    val steps: List<DiagnosticStepUi> = DiagnosticStep.entries.map {
        DiagnosticStepUi(it, StepState.Pending)
    },
    val apiDomain: String = "",
    val running: Boolean = false,
    val finished: Boolean = false,
    val advice: DiagnosticsAdvice? = null,
    val applyingMirror: Boolean = false,
    val appliedMirror: String? = null,
    val workingMirrorDomain: String? = null,
)
