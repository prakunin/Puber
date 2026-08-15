package com.kino.puber.core.ui.uikit.component.moviesList

/**
 * Where focus can go from a card, for one list surface.
 *
 * A row knows its own items and nothing else, so the vertical half of the answer has to be kept
 * somewhere both rows can see. Rows put themselves in here while they are composed and take
 * themselves out when they are not, which also means the registry never offers a card that no
 * longer exists.
 *
 * The order it holds is the *absolute* one, gaps and all. A row that does not participate — a
 * Collections row, an error row, a row the `LazyColumn` has not composed — leaves its position
 * empty rather than closing the gap, so the row after it is never mistaken for one press away.
 */
internal class FocusNeighbourhood {

    private class Row(val order: Int, val itemIds: List<Int>)

    private val rows = LinkedHashMap<String, Row>()

    fun register(rowOrder: Int, rowKey: String, itemIds: List<Int>) {
        rows[rowKey] = Row(order = rowOrder, itemIds = itemIds)
    }

    fun unregister(rowKey: String) {
        rows.remove(rowKey)
    }

    /**
     * The cards the D-pad can reach from [itemId], likeliest first: right, left, down, up.
     *
     * This is a prediction rather than a reimplementation of Compose's focus search — rows scroll
     * independently, so the spatially nearest card in an adjacent row may not be the one at the same
     * index. Nothing here depends on the prediction being exact; a wrong guess costs one request
     * that is never read.
     *
     * Null when the row or the card is not registered, which is how a surface says "not mine".
     * An empty list is a registered card with nowhere to go.
     */
    fun neighboursOf(rowKey: String, itemId: Int): List<Int>? {
        val row = rows[rowKey] ?: return null
        val index = row.itemIds.indexOf(itemId)
        if (index < 0) return null
        return buildList {
            row.itemIds.getOrNull(index + 1)?.let(::add)
            row.itemIds.getOrNull(index - 1)?.let(::add)
            rowAt(row.order + 1)?.itemNear(index)?.let(::add)
            rowAt(row.order - 1)?.itemNear(index)?.let(::add)
        }
    }

    private fun rowAt(order: Int): Row? = rows.values.firstOrNull { it.order == order }

    /** The card focus would most likely land on, clamped when the adjacent row is shorter. */
    private fun Row.itemNear(index: Int): Int? = itemIds.getOrNull(index.coerceAtMost(itemIds.lastIndex))
}
