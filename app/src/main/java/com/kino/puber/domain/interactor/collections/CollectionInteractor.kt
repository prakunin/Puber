package com.kino.puber.domain.interactor.collections

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.KCollection
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.cache.CacheKeys
import com.kino.puber.data.cache.CacheTtl
import com.kino.puber.data.cache.ContentCacheRepository

class CollectionInteractor(
    private val api: KinoPubApiClient,
    private val contentCache: ContentCacheRepository,
) {

    suspend fun getCollections(page: Int): PaginatedResponse<KCollection> {
        if (page != FIRST_PAGE) return api.getCollections(page = page).getOrThrow()
        return contentCache.getPayload(
            key = CacheKeys.collectionsPage(page),
            serializer = PaginatedResponse.serializer(KCollection.serializer()),
            ttl = CacheTtl.Collections,
        ) { api.getCollections(page = page).getOrThrow() }
    }

    suspend fun getCollectionItems(id: Int): List<Item> {
        val generation = contentCache.generation
        val items = api.getCollectionItems(id).getOrThrow().items
        return contentCache.mergeItems(items, expectedGeneration = generation)
    }

    companion object {
        private const val FIRST_PAGE = 1
    }
}
