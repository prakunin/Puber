package com.kino.puber.ui.feature.device.diagnostics

internal object NetworkDiagnosticsTestTags {
    const val Steps = "diagnostics-steps"
    const val Summary = "diagnostics-summary"

    fun server(name: String) = "diagnostics-server-$name"
}
