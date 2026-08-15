package com.kino.puber.ui.feature.contentlist.vm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** What the sections on one content list are being asked to do. */
internal sealed interface SectionRefresh {

    /** Re-page everything. For changes whose extent is not known — a return from another screen. */
    data object All : SectionRefresh

    /**
     * One title changed; only rows showing it have anything to redraw.
     *
     * A row that never held the item cannot be affected by it, and there is no cheaper redraw
     * available for the rows that did: the saved flag rides on the item payload rather than in a
     * repository the mapper could consult, so a correct badge means re-reading the row.
     */
    data class ForItem(val itemId: Int) : SectionRefresh
}

internal class ContentListRefreshCoordinator {
    private val mutableRefreshes = MutableStateFlow(Request(generation = 0L, refresh = SectionRefresh.All))

    private data class Request(val generation: Long, val refresh: SectionRefresh)

    fun refreshRequests(): Flow<SectionRefresh> {
        // Capture before collection starts so a refresh cannot disappear in the coroutine launch gap.
        val observedGeneration = mutableRefreshes.value.generation
        return mutableRefreshes
            .filter { it.generation != observedGeneration }
            .map { it.refresh }
    }

    fun requestRefresh() = publish(SectionRefresh.All)

    fun requestRefreshForItem(itemId: Int) = publish(SectionRefresh.ForItem(itemId))

    private fun publish(refresh: SectionRefresh) {
        mutableRefreshes.update { Request(generation = it.generation + 1, refresh = refresh) }
    }
}
