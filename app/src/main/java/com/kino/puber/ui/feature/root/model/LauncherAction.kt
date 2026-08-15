package com.kino.puber.ui.feature.root.model

import com.kino.puber.core.ui.uikit.model.UIAction

internal sealed interface LauncherAction : UIAction {
    data object SplashShown : LauncherAction
}
