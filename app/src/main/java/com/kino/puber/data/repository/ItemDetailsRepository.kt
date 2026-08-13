package com.kino.puber.data.repository

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.cache.Cached
import com.kino.puber.data.cache.CachedFeed
import com.kino.puber.data.cache.CacheKeys
import com.kino.puber.data.cache.CacheTtl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.builtins.ListSerializer

class ItemDetailsRepository(
    private val api: KinoPubApiClient,
    private val watchStateRepository: WatchStateRepository,
    store: PersistentPayloadStore,
) {

    private val details = CachedFeed(
        store = store,
        serializer = Item.serializer(),
        ttl = CacheTtl.ItemDetails,
        keyPrefix = CacheKeys.ItemPrefix,
    )

    private val similar = CachedFeed(
        store = store,
        serializer = ListSerializer(Item.serializer()),
        ttl = CacheTtl.SimilarItems,
        keyPrefix = CacheKeys.SimilarPrefix,
    )

    fun observeItemDetails(id: Int, force: Boolean = false): Flow<Cached<Item>> {
        return details.load(CacheKeys.item(id), force = force) { fetchItem(id) }
    }

    fun observeSimilarItems(id: Int, force: Boolean = false): Flow<Cached<List<Item>>> {
        return similar.load(CacheKeys.similar(id), force = force) {
            api.getSimilarItems(id).getOrThrow().items.orEmpty()
        }
    }

    /** The one-shot read, for callers that need a value rather than a screen that repaints. */
    suspend fun getItemDetails(id: Int): Item {
        return observeItemDetails(id).lastValue()
    }

    /**
     * A hard refresh, unlike [getItemDetails]: it must not fall back to whatever was cached
     * before the mutation that prompted this call. Callers refresh after mutating server state —
     * a watched mark, a bookmark toggle — specifically to redraw with the server's current truth,
     * so a failed revalidation here has to be reported rather than quietly answered with
     * pre-mutation data.
     */
    suspend fun refresh(id: Int): Item {
        return observeItemDetails(id, force = true).latestValueOrFailure()
    }

    suspend fun markStale(itemId: Int) {
        details.markStale(CacheKeys.item(itemId))
    }

    suspend fun invalidate(itemId: Int) {
        details.invalidate(CacheKeys.item(itemId))
    }

    suspend fun clear() {
        // Both namespaces belong to this repository, and both describe the same catalogue.
        details.invalidateNamespace()
        similar.invalidateNamespace()
    }

    private suspend fun fetchItem(id: Int): Item {
        return api.getItemDetails(id).getOrThrow().item!!.also { item ->
            // Details do carry watch fields, unlike the catalogue. Every opened title is a free
            // chance to sharpen the local index. observedAt is passed explicitly (rather than
            // relying on WatchStateRepository's own clock default) because it is genuinely "now"
            // from this fetch's point of view.
            watchStateRepository.recordFromServer(listOf(item), observedAt = System.currentTimeMillis())
        }
    }

    /**
     * Collapses a [Cached] stream to the value callers actually want: the most recent usable
     * [Item]. A background revalidation that fails after a stored value was already served must
     * not turn into an exception here — that would replace content already on screen with an
     * error for no better reason than a network hiccup. Only when no value was ever emitted does
     * the failure (or, if the flow completed with nothing at all, an [IllegalStateException])
     * propagate.
     */
    private suspend fun Flow<Cached<Item>>.lastValue(): Item {
        var lastValue: Item? = null
        var failure: Throwable? = null
        collect { emission ->
            when (emission) {
                is Cached.Value -> lastValue = emission.value
                is Cached.RefreshFailed -> failure = emission.error
            }
        }
        return lastValue ?: throw (failure ?: IllegalStateException("No item details were emitted for this key"))
    }

    /**
     * The strict counterpart to [lastValue]: a [Cached.RefreshFailed] is never absorbed, even when
     * a value was already emitted. Used by [refresh], where returning the value that existed
     * before this call is exactly the wrong fallback.
     */
    private suspend fun Flow<Cached<Item>>.latestValueOrFailure(): Item {
        var lastValue: Item? = null
        collect { emission ->
            when (emission) {
                is Cached.Value -> lastValue = emission.value
                is Cached.RefreshFailed -> throw emission.error
            }
        }
        return lastValue ?: throw IllegalStateException("No item details were emitted for this key")
    }
}
