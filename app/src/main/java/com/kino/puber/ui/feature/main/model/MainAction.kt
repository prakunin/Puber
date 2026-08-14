package com.kino.puber.ui.feature.main.model

import com.kino.puber.core.ui.uikit.model.UIAction

internal sealed interface MainAction : UIAction {
    data class RefreshTab(val tab: MainTab) : MainAction

    /** The main screen came back to the foreground. */
    data object Resumed : MainAction
}
