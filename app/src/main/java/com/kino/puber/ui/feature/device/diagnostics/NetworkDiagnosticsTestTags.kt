package com.kino.puber.ui.feature.device.diagnostics

internal object NetworkDiagnosticsTestTags {
    const val Steps = "diagnostics-steps"
    const val Summary = "diagnostics-summary"
    const val Transport = "diagnostics-transport"

    fun server(name: String) = "diagnostics-server-$name"
}
