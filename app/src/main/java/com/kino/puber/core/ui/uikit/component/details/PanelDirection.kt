package com.kino.puber.core.ui.uikit.component.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.LayoutDirection

enum class PanelDirection { None, Start, End }

@Immutable
data class PanelPosition(val rowKey: String, val index: Int, val itemId: Int)

internal fun panelDirection(from: PanelPosition?, to: PanelPosition?): PanelDirection = when {
    from == null || to == null -> PanelDirection.None
    from.itemId == to.itemId -> PanelDirection.None
    from.rowKey != to.rowKey -> PanelDirection.None
    to.index > from.index -> PanelDirection.End
    to.index < from.index -> PanelDirection.Start
    else -> PanelDirection.None
}

internal fun enterSign(direction: PanelDirection, layoutDirection: LayoutDirection): Int {
    val sign = when (direction) {
        PanelDirection.End -> 1
        PanelDirection.Start -> -1
        PanelDirection.None -> 0
    }
    return if (layoutDirection == LayoutDirection.Rtl) -sign else sign
}

/**
 * Remembers where the card shown in the panel sits and where focus is now, so a card change
 * can be given a direction. Plain fields, not snapshot state: they are written from focus
 * callbacks and `SideEffect`, and read while composing the next change.
 */
internal class PanelDirectionTracker {
    private var shown: PanelPosition? = null
    private var focused: PanelPosition? = null

    fun onCardFocused(position: PanelPosition) {
        focused = position
    }

    fun directionTo(details: VideoDetailsUIState): PanelDirection {
        if (details.isLoading) return PanelDirection.None
        return panelDirection(shown, focused?.takeIf { it.itemId == details.id })
    }

    fun markShown(details: VideoDetailsUIState) {
        if (details.isLoading) return
        shown = focused?.takeIf { it.itemId == details.id }
            ?: shown?.takeIf { it.itemId == details.id }
    }
}

@Composable
internal fun rememberPanelDirectionTracker(): PanelDirectionTracker = remember { PanelDirectionTracker() }
