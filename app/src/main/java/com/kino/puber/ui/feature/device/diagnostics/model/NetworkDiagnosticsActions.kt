package com.kino.puber.ui.feature.device.diagnostics.model

import com.kino.puber.core.ui.uikit.model.UIAction

internal sealed interface NetworkDiagnosticsActions : UIAction {
    data object Cancel : NetworkDiagnosticsActions
    data object Restart : NetworkDiagnosticsActions
    data object ConfirmMirrorSwitch : NetworkDiagnosticsActions
}
