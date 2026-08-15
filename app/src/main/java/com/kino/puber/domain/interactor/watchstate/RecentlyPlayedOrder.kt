package com.kino.puber.domain.interactor.watchstate

import com.kino.puber.core.logger.log
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.repository.WatchStateRepository

/**
 * Puts the title the account watched most recently at the front of a continue-watching list.
 *
 * Two screens show the same `/watching/serials` list — the home row and the "I'm watching" tab — and
 * the server's order for it says nothing about when anything was played, so the order is decided
 * here for both.
 */
class RecentlyPlayedOrder(
    private val api: KinoPubApiClient,
    private val watchState: WatchStateRepository,
) {

    /**
     * The order comes from the two sources that know when something was played. The newest history
     * page comes first: it is the only one that already knows about an episode finished a minute
     * ago, which is exactly the title the user expects to find at the front when they come back to
     * the screen. The local index covers everything further back than that page — it is built from a
     * walk over the whole history, but only as far as the walk has got and never fresher than the
     * last sync, which is why it cannot answer this on its own.
     *
     * A title neither source can date keeps the server's order, behind everything that could be
     * dated: the sort is stable, so the list degrades to what it is today rather than to a shuffle.
     */
    suspend fun sort(items: List<Item>): List<Item> {
        // A list of one is already in order, and asking the server for a history page to find that
        // out is a request spent on nothing.
        if (items.size < 2) return items
        val indexed = watchState.lastWatchedAt()
        val recent = recentlyPlayed()
        return items.sortedByDescending { item -> maxOf(recent[item.id] ?: 0L, indexed[item.id] ?: 0L) }
    }

    /**
     * When each item on the newest history page was last played.
     *
     * Best-effort: this decides an order, not what the list contains, so a history request that
     * fails costs the list its freshest ordering source and nothing else.
     */
    private suspend fun recentlyPlayed(): Map<Int, Long> {
        val page = api.getHistoryData(FIRST_PAGE).getOrElse { error ->
            log(error, "Continue watching ordered from the index alone: history page unavailable")
            return emptyMap()
        }
        val lastSeen = mutableMapOf<Int, Long>()
        page.items.forEach { entry ->
            // One entry per episode played, so a series appears many times over; the newest of them
            // is when the series was last watched.
            val seenAt = entry.updated?.toLongOrNull() ?: return@forEach
            if (seenAt > (lastSeen[entry.item.id] ?: 0L)) lastSeen[entry.item.id] = seenAt
        }
        return lastSeen
    }

    private companion object {
        const val FIRST_PAGE = 1
    }
}
