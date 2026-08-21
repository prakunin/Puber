package com.kino.puber.ui.feature.device.diagnostics.model

import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.diagnostics.SpeedTestServer

internal sealed interface NetworkDiagnosticsActions : UIAction {
    data class Start(val server: SpeedTestServer) : NetworkDiagnosticsActions
}
