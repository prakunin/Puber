package com.kino.puber.ui.feature.device.diagnostics

internal object NetworkDiagnosticsTestTags {
    const val Steps = "diagnostics-steps"
    const val Summary = "diagnostics-summary"
    const val PrimaryAction = "diagnostics-primary-action"
    const val MirrorProposal = "diagnostics-mirror-proposal"
    const val MirrorSwitch = "diagnostics-mirror-switch"
    const val AppliedMirror = "diagnostics-applied-mirror"

    fun step(name: String) = "diagnostics-step-$name"
}
