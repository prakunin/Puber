package com.kino.puber.domain.interactor.diagnostics

/** The highest quality a measured rate can carry, by the thresholds below. */
internal enum class QualityCeiling {
    TooSlow,
    Hd720,
    Hd1080,
    Uhd4k,
}

/**
 * What the run adds up to.
 *
 * API health and media speed are separate fields because they fail separately: a blocked mirror
 * with a fast CDN and a healthy API with a crawling CDN are both ordinary, and a single verdict
 * would have to pick one of them to be wrong about.
 */
internal data class DiagnosticsAdvice(
    val apiReachable: Boolean,
    val mediaBitsPerSecond: Double?,
    val ceiling: QualityCeiling?,
    val mirrorProposal: String?,
)

internal fun advise(run: NetworkDiagnosticsRun): DiagnosticsAdvice {
    val apiReachable = run.state(DiagnosticStep.ApiReachability) is StepState.Success
    val rate = (run.state(DiagnosticStep.MediaThroughput) as? StepState.Success)
        ?.sample
        ?.bitsPerSecond

    return DiagnosticsAdvice(
        apiReachable = apiReachable,
        mediaBitsPerSecond = rate,
        ceiling = rate?.let(::qualityCeiling),
        // Only worth proposing when the mirror in use stopped working: moving between two live
        // mirrors changes which host answers and nothing the user can perceive.
        mirrorProposal = run.workingMirrorDomain.takeUnless { apiReachable },
    )
}

internal fun qualityCeiling(bitsPerSecond: Double): QualityCeiling = when {
    bitsPerSecond >= UHD_4K_BITS_PER_SECOND -> QualityCeiling.Uhd4k
    bitsPerSecond >= HD_1080_BITS_PER_SECOND -> QualityCeiling.Hd1080
    bitsPerSecond >= HD_720_BITS_PER_SECOND -> QualityCeiling.Hd720
    else -> QualityCeiling.TooSlow
}

private const val HD_720_BITS_PER_SECOND = 5_000_000.0
private const val HD_1080_BITS_PER_SECOND = 10_000_000.0
private const val UHD_4K_BITS_PER_SECOND = 25_000_000.0
