package com.kino.puber.domain.interactor.diagnostics

import com.kino.puber.data.api.network.diagnostics.ThroughputSample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class NetworkDiagnosticsAdviceTest {

    @Test
    fun qualityCeiling_isTooSlow_belowThe720pThreshold() {
        assertEquals(QualityCeiling.TooSlow, qualityCeiling(4_999_999.0))
    }

    @Test
    fun qualityCeiling_is720p_atItsThreshold() {
        assertEquals(QualityCeiling.Hd720, qualityCeiling(5_000_000.0))
    }

    @Test
    fun qualityCeiling_is1080p_atItsThreshold() {
        assertEquals(QualityCeiling.Hd1080, qualityCeiling(10_000_000.0))
    }

    @Test
    fun qualityCeiling_is4k_atItsThreshold() {
        assertEquals(QualityCeiling.Uhd4k, qualityCeiling(25_000_000.0))
    }

    @Test
    fun qualityCeiling_is1080p_justBelowThe4kThreshold() {
        assertEquals(QualityCeiling.Hd1080, qualityCeiling(24_999_999.0))
    }

    /** The two halves fail independently, so the verdict has to carry both separately. */
    @Test
    fun advise_reportsFastMedia_whenTheApiIsUnreachable() {
        val run = runWith(
            DiagnosticStep.ApiReachability to StepState.Failure(FailureReason.Unreachable),
            DiagnosticStep.MediaThroughput to success(bytes = 4_194_304, millis = 1_000),
        )

        val advice = advise(run)

        assertFalse(advice.apiReachable)
        assertEquals(QualityCeiling.Uhd4k, advice.ceiling)
    }

    @Test
    fun advise_reportsNoRate_whenTheMediaStepWasSkipped() {
        val run = runWith(
            DiagnosticStep.ApiReachability to StepState.Success(latencyMillis = 120),
            DiagnosticStep.MediaThroughput to StepState.Skipped(SkipReason.NoMediaLink),
        )

        val advice = advise(run)

        assertTrue(advice.apiReachable)
        assertNull(advice.mediaBitsPerSecond)
        assertNull(advice.ceiling)
    }

    /**
     * A working mirror is only worth proposing when the current one stopped working. Switching
     * between two live mirrors changes which host serves the API and nothing a user can feel.
     */
    @Test
    fun advise_proposesNoMirror_whenTheCurrentOneAnswers() {
        val run = runWith(
            DiagnosticStep.ApiReachability to StepState.Success(latencyMillis = 120),
        ).copy(workingMirrorDomain = "api.alador.test")

        assertNull(advise(run).mirrorProposal)
    }

    @Test
    fun advise_proposesTheWorkingMirror_whenTheCurrentOneFailed() {
        val run = runWith(
            DiagnosticStep.ApiReachability to StepState.Failure(FailureReason.Unreachable),
        ).copy(workingMirrorDomain = "api.alador.test")

        assertEquals("api.alador.test", advise(run).mirrorProposal)
    }

    @Test
    fun advise_proposesNothing_whenNoMirrorAnswered() {
        val run = runWith(
            DiagnosticStep.ApiReachability to StepState.Failure(FailureReason.Unreachable),
        )

        assertNull(advise(run).mirrorProposal)
    }

    private fun success(bytes: Long, millis: Long) =
        StepState.Success(sample = ThroughputSample(bytes = bytes, elapsedMillis = millis))

    private fun runWith(vararg states: Pair<DiagnosticStep, StepState>): NetworkDiagnosticsRun {
        return states.fold(NetworkDiagnosticsRun(apiDomain = "service-kp.test")) { run, (step, state) ->
            run.with(step, state)
        }
    }
}
