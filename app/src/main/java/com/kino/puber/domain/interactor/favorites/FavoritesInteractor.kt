package com.kino.puber.domain.interactor.favorites

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.cache.Cached
import com.kino.puber.data.cache.ContentCacheRepository
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.domain.interactor.watchstate.RecentlyPlayedOrder
import kotlinx.coroutines.flow.Flow

internal class FavoritesInteractor(
    private val api: KinoPubApiClient,
    private val itemDetailsRepository: ItemDetailsRepository,
    private val recentlyPlayedOrder: RecentlyPlayedOrder,
    private val contentCache: ContentCacheRepository,
) {

    /**
     * The watching list, stored unsorted: [RecentlyPlayedOrder] runs on each emission instead, so a
     * cached list is ordered by what the index knows now rather than by what it knew at write time.
     */
    fun observeWatchlist(force: Boolean = false): Flow<Cached<List<Item>>> =
        contentCache.watchlist(force = force) {
            api.getWatchingList(onlySubscribed = true).getOrThrow().items.orEmpty()
        }

    suspend fun sortByRecentlyPlayed(items: List<Item>): List<Item> = recentlyPlayedOrder.sort(items)

    suspend fun getItemDetails(id: Int): Item {
        return itemDetailsRepository.getItemDetails(id)
    }
}
