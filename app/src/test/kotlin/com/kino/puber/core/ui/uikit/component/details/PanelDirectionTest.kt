package com.kino.puber.core.ui.uikit.component.details

import androidx.compose.ui.unit.LayoutDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PanelDirectionTest {

    private fun details(id: Int, isLoading: Boolean = false) =
        VideoDetailsUIState.Loading.copy(id = id, isLoading = isLoading)

    private fun pos(index: Int, id: Int = index + 1, row: String = "r") = PanelPosition(row, index, id)

    @Test
    fun panelDirection_isEnd_whenSameRowHigherIndex() {
        assertEquals(PanelDirection.End, panelDirection(PanelPosition("r", 0, 1), PanelPosition("r", 1, 2)))
    }

    @Test
    fun panelDirection_isStart_whenSameRowLowerIndex() {
        assertEquals(PanelDirection.Start, panelDirection(PanelPosition("r", 1, 2), PanelPosition("r", 0, 1)))
    }

    @Test
    fun panelDirection_isNone_whenDifferentRow() {
        assertEquals(PanelDirection.None, panelDirection(PanelPosition("a", 0, 1), PanelPosition("b", 1, 2)))
    }

    @Test
    fun panelDirection_isNone_whenFromUnknown() {
        assertEquals(PanelDirection.None, panelDirection(null, PanelPosition("r", 1, 2)))
    }

    @Test
    fun panelDirection_isNone_whenToUnknown() {
        assertEquals(PanelDirection.None, panelDirection(PanelPosition("r", 0, 1), null))
    }

    @Test
    fun panelDirection_isNone_whenSameItemId() {
        assertEquals(PanelDirection.None, panelDirection(PanelPosition("r", 0, 7), PanelPosition("r", 3, 7)))
    }

    @Test
    fun panelDirection_isNone_whenSameIndex() {
        assertEquals(PanelDirection.None, panelDirection(PanelPosition("r", 2, 1), PanelPosition("r", 2, 9)))
    }

    @Test
    fun directionTo_usesShownAndFinalCard_whenDebouncedJumpAcrossCards() {
        val tracker = PanelDirectionTracker()
        tracker.onCardFocused(pos(0))
        tracker.markShown(details(1))
        tracker.onCardFocused(pos(1))
        tracker.onCardFocused(pos(2))
        tracker.onCardFocused(pos(3))
        assertEquals(PanelDirection.End, tracker.directionTo(details(4)))
    }

    @Test
    fun directionTo_isStart_afterReverseJump() {
        val tracker = PanelDirectionTracker()
        tracker.onCardFocused(pos(3))
        tracker.markShown(details(4))
        tracker.onCardFocused(pos(2))
        tracker.onCardFocused(pos(1))
        assertEquals(PanelDirection.Start, tracker.directionTo(details(2)))
    }

    @Test
    fun enterSign_followsDirection_inLtr() {
        assertEquals(1, enterSign(PanelDirection.End, LayoutDirection.Ltr))
        assertEquals(-1, enterSign(PanelDirection.Start, LayoutDirection.Ltr))
        assertEquals(0, enterSign(PanelDirection.None, LayoutDirection.Ltr))
    }

    @Test
    fun enterSign_isNegated_inRtl() {
        assertEquals(-1, enterSign(PanelDirection.End, LayoutDirection.Rtl))
        assertEquals(1, enterSign(PanelDirection.Start, LayoutDirection.Rtl))
        assertEquals(0, enterSign(PanelDirection.None, LayoutDirection.Rtl))
    }

    @Test
    fun directionTo_isNone_whenNothingShownYet() {
        val tracker = PanelDirectionTracker()
        tracker.onCardFocused(pos(1))
        assertEquals(PanelDirection.None, tracker.directionTo(details(2)))
    }

    @Test
    fun directionTo_isNone_whenRefreshRepublishesSameId() {
        val tracker = PanelDirectionTracker()
        tracker.onCardFocused(pos(1))
        tracker.markShown(details(2))
        assertEquals(PanelDirection.None, tracker.directionTo(details(2)))
    }

    @Test
    fun directionTo_isNone_whenDetailsLoading() {
        val tracker = PanelDirectionTracker()
        tracker.onCardFocused(pos(0))
        tracker.markShown(details(1))
        tracker.onCardFocused(pos(1))
        assertEquals(PanelDirection.None, tracker.directionTo(details(0, isLoading = true)))
    }

    @Test
    fun directionTo_isNone_whenFocusedIdDiffersFromDetails() {
        val tracker = PanelDirectionTracker()
        tracker.onCardFocused(pos(0))
        tracker.markShown(details(1))
        tracker.onCardFocused(pos(2))
        assertEquals(PanelDirection.None, tracker.directionTo(details(2)))
    }

    @Test
    fun directionTo_isNone_afterFocusMovesToAnotherRow() {
        val tracker = PanelDirectionTracker()
        tracker.onCardFocused(pos(0, id = 1, row = "a"))
        tracker.markShown(details(1))
        tracker.onCardFocused(pos(1, id = 2, row = "b"))
        assertEquals(PanelDirection.None, tracker.directionTo(details(2)))
    }

    @Test
    fun markShown_ignoresLoadingDetails() {
        val tracker = PanelDirectionTracker()
        tracker.onCardFocused(pos(0))
        tracker.markShown(details(1))
        tracker.onCardFocused(pos(1))
        tracker.markShown(VideoDetailsUIState.Loading)
        assertEquals(PanelDirection.End, tracker.directionTo(details(2)))
    }

    @Test
    fun markShown_clearsShown_whenNoPositionMatchesDetails() {
        val tracker = PanelDirectionTracker()
        tracker.onCardFocused(pos(0))
        tracker.markShown(details(1))
        tracker.onCardFocused(pos(1))
        tracker.markShown(details(99))
        tracker.onCardFocused(pos(2))
        assertEquals(PanelDirection.None, tracker.directionTo(details(3)))
    }
}
