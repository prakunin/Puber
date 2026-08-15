package com.kino.puber.ui.feature.main.model

import com.kino.puber.core.ui.uikit.model.UIAction

internal sealed interface MainAction : UIAction {
    data class RefreshTab(val tab: MainTab) : MainAction

    /**
     * The main screen came back to the foreground, so the watch-state index is worth catching up.
     *
     * Only a sync trigger — whether the app is on screen at all is reported from the root of the
     * composition, which is the only place that hears it while a fullscreen screen is on top.
     */
    data object Resumed : MainAction
}
