package com.kino.puber.ui.feature.history.vm

import com.kino.puber.data.api.models.History
import com.kino.puber.domain.interactor.history.HistoryInteractor
import com.kino.puber.domain.interactor.history.HistoryTraversal
import kotlin.math.min

/**
 * Reads history pages until there is something to render.
 *
 * One server page can collapse to nothing once repeats of an item already on screen are dropped, so
 * both walks keep reading while the result is empty and the server still has pages. The view model
 * only ever sees a page it can publish.
 */
internal class HistoryPageLoader(private val interactor: HistoryInteractor) {

    /**
     * Re-reads the list from the top, at least as deep as it was before.
     *
     * A refresh has to restore the depth the user had already scrolled to, or the list would jump
     * back to one page under them.
     */
    suspend fun loadDepth(loadedPageDepth: Int): HistoryPageDepthResult {
        val traversal = HistoryTraversal()
        val items = mutableListOf<History>()
        val requestedDepth = loadedPageDepth.coerceAtLeast(FIRST_PAGE)
        var page = FIRST_PAGE
        var current = FIRST_PAGE
        var totalPages = FIRST_PAGE
        var boundedDepth = FIRST_PAGE

        do {
            val response = interactor.getPage(page)
            check(response.pagination.current == page) {
                "History pagination did not match the requested page"
            }
            items += traversal.filterFirstOccurrences(response.items)
            current = response.pagination.current
            totalPages = response.pagination.total
            boundedDepth = min(
                requestedDepth,
                totalPages.coerceAtLeast(FIRST_PAGE),
            )
            page++
        } while (
            page <= boundedDepth ||
                (items.isEmpty() && current < totalPages)
        )

        return HistoryPageDepthResult(
            items = items,
            currentPage = current,
            totalPages = totalPages,
        )
    }

    /**
     * @param alreadyOnScreen rows the list already holds, so repeats of them do not come back as a
     * second copy further down.
     */
    suspend fun loadNextRenderable(
        startPage: Int,
        alreadyOnScreen: List<History>,
    ): HistoryNextPageResult {
        val traversal = HistoryTraversal(alreadyOnScreen)
        var currentPage = startPage
        var totalPages = startPage + 1
        var items: List<History>
        do {
            val requestedPage = currentPage + 1
            val response = interactor.getPage(page = requestedPage)
            check(response.pagination.current == requestedPage) {
                "History pagination did not match the requested next page"
            }
            items = traversal.filterFirstOccurrences(response.items)
            currentPage = response.pagination.current
            totalPages = response.pagination.total
        } while (items.isEmpty() && currentPage < totalPages)
        return HistoryNextPageResult(
            items = items,
            currentPage = currentPage,
            totalPages = totalPages,
        )
    }
}

/**
 * Folds a freshly read page into what is already on screen, replacing rows that came back rather
 * than appending a second copy of them.
 */
internal fun mergeStableHistory(
    oldItems: List<History>,
    newItems: List<History>,
): List<History> {
    val merged = oldItems.toMutableList()
    newItems.forEach { newItem ->
        val index = merged.indexOfFirst { oldItem ->
            HistoryRowComparator.isItemTheSame(oldItem, newItem)
        }
        if (index >= 0) {
            merged[index] = newItem
        } else {
            merged += newItem
        }
    }
    return merged
}
