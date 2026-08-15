package com.kino.puber.domain.interactor.favorites

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.domain.interactor.watchstate.RecentlyPlayedOrder

internal class FavoritesInteractor(
    private val api: KinoPubApiClient,
    private val itemDetailsRepository: ItemDetailsRepository,
    private val recentlyPlayedOrder: RecentlyPlayedOrder,
) {

    suspend fun getWatchlist(): List<Item> {
        val result = api.getWatchingList(onlySubscribed = true)
        return recentlyPlayedOrder.sort(result.getOrThrow().items.orEmpty())
    }

    suspend fun getItemDetails(id: Int): Item {
        return itemDetailsRepository.getItemDetails(id)
    }
}
