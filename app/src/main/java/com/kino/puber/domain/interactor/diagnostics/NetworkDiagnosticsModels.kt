package com.kino.puber.domain.interactor.diagnostics

import com.kino.puber.data.api.network.diagnostics.ThroughputSample

/** The five things a run measures, in the order it measures them. */
internal enum class DiagnosticStep {
    ApiReachability,
    NameResolution,
    ApiResponsiveness,
    MediaThroughput,
    MirrorSweep,
}

/**
 * Why a step had nothing to do.
 *
 * Kept apart from failure on purpose: an item that offers no progressive URL is a fact about the
 * catalogue, and a mirror sweep with a healthy mirror already in hand has no question to ask.
 * Drawn as failures, both would send a user looking for a network problem that is not there.
 */
internal enum class SkipReason {
    NoNetwork,
    NoMediaLink,

    /**
     * The catalogue answered, and none of the files it offered carries a progressive URL — which
     * is what an account set to an HLS streaming type looks like from here. Kept apart from
     * [NoMediaLink] because it is the one skip the user can do something about, and the row says
     * what.
     */
    NoProgressiveStream,
    CurrentMirrorAnswers,
}

internal enum class FailureReason {
    Unreachable,
    ResolutionFailed,
    RequestFailed,
}

internal sealed interface StepState {
    data object Pending : StepState
    data object Running : StepState

    data class Success(
        val latencyMillis: Long? = null,
        val sample: ThroughputSample? = null,
    ) : StepState

    data class Failure(val reason: FailureReason) : StepState
    data class Skipped(val reason: SkipReason) : StepState
}

/**
 * Everything a run knows so far.
 *
 * The whole snapshot is re-emitted on every transition rather than the step that changed: a screen
 * that draws five rows needs all five states at once, and a partial update would make the view
 * model responsible for reassembling a run it did not perform.
 */
internal data class NetworkDiagnosticsRun(
    val apiDomain: String,
    val steps: Map<DiagnosticStep, StepState> =
        DiagnosticStep.entries.associateWith { StepState.Pending },
    val workingMirrorDomain: String? = null,
    val finished: Boolean = false,
) {
    fun state(step: DiagnosticStep): StepState = steps.getValue(step)

    fun with(step: DiagnosticStep, state: StepState): NetworkDiagnosticsRun =
        copy(steps = steps + (step to state))
}
