package com.kino.puber.core.ui.uikit.component.moviesList

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FocusNeighbourhoodTest {

    private val neighbourhood = FocusNeighbourhood()

    @Test
    fun candidatesAreOrderedRightLeftDownUp() {
        neighbourhood.register(rowOrder = 0, rowKey = "top", itemIds = listOf(10, 11, 12))
        neighbourhood.register(rowOrder = 1, rowKey = "middle", itemIds = listOf(20, 21, 22))
        neighbourhood.register(rowOrder = 2, rowKey = "bottom", itemIds = listOf(30, 31, 32))

        val candidates = neighbourhood.neighboursOf(rowKey = "middle", itemId = 21)

        assertEquals(listOf(22, 20, 31, 11), candidates)
    }

    @Test
    fun theFirstItemOfARowHasNoLeftCandidate() {
        neighbourhood.register(rowOrder = 0, rowKey = "only", itemIds = listOf(10, 11))

        assertEquals(listOf(11), neighbourhood.neighboursOf(rowKey = "only", itemId = 10))
    }

    @Test
    fun aRegisteredItemWithNoNeighbourAtAllYieldsAnEmptyList() {
        // Distinct from "not registered": the surface knows this row, it simply has nowhere to go.
        neighbourhood.register(rowOrder = 0, rowKey = "only", itemIds = listOf(10))

        assertEquals(emptyList<Int>(), neighbourhood.neighboursOf(rowKey = "only", itemId = 10))
    }

    @Test
    fun theIndexClampsToTheLastItemOfAShorterAdjacentRow() {
        neighbourhood.register(rowOrder = 0, rowKey = "long", itemIds = listOf(10, 11, 12, 13))
        neighbourhood.register(rowOrder = 1, rowKey = "short", itemIds = listOf(20, 21))

        val candidates = neighbourhood.neighboursOf(rowKey = "long", itemId = 13)

        assertEquals(listOf(12, 21), candidates)
    }

    @Test
    fun anUnregisteredRowContributesNoCandidate() {
        neighbourhood.register(rowOrder = 0, rowKey = "top", itemIds = listOf(10, 11))

        assertNull(neighbourhood.neighboursOf(rowKey = "unknown", itemId = 10))
    }

    @Test
    fun anItemThatIsNotInTheRowIsNotRegistered() {
        neighbourhood.register(rowOrder = 0, rowKey = "top", itemIds = listOf(10, 11))

        assertNull(neighbourhood.neighboursOf(rowKey = "top", itemId = 99))
    }

    @Test
    fun aGapInAbsoluteRowOrderIsNotSkipped() {
        // Row 1 is a Collections row, an error row, or one the LazyColumn has not composed. Whatever
        // it is, it is one D-pad press below row 0 — row 2 is two, and must not be offered as a
        // downward candidate.
        neighbourhood.register(rowOrder = 0, rowKey = "top", itemIds = listOf(10, 11))
        neighbourhood.register(rowOrder = 2, rowKey = "bottom", itemIds = listOf(30, 31))

        assertEquals(listOf(11), neighbourhood.neighboursOf(rowKey = "top", itemId = 10))
        assertEquals(listOf(31), neighbourhood.neighboursOf(rowKey = "bottom", itemId = 30))
    }

    @Test
    fun unregisterRemovesARow() {
        neighbourhood.register(rowOrder = 0, rowKey = "top", itemIds = listOf(10, 11))
        neighbourhood.register(rowOrder = 1, rowKey = "bottom", itemIds = listOf(20, 21))

        neighbourhood.unregister("bottom")

        assertNull(neighbourhood.neighboursOf(rowKey = "bottom", itemId = 20))
        assertEquals(listOf(11), neighbourhood.neighboursOf(rowKey = "top", itemId = 10))
    }

    @Test
    fun reRegisteringARowUnderANewOrderReplacesTheOldEntry() {
        // Rows re-register whenever their position or items change; the previous registration must
        // not survive as a second row at the old order.
        neighbourhood.register(rowOrder = 0, rowKey = "row", itemIds = listOf(10, 11))
        neighbourhood.register(rowOrder = 1, rowKey = "neighbour", itemIds = listOf(20, 21))

        neighbourhood.register(rowOrder = 5, rowKey = "row", itemIds = listOf(10, 11))

        // Nothing adjacent to row 5. A surviving registration at row 0 would still offer 20 below.
        assertEquals(listOf(11), neighbourhood.neighboursOf(rowKey = "row", itemId = 10))
    }
}
