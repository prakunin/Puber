package com.kino.puber.data.repository

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.cache.Cached
import com.kino.puber.data.cache.ContentCacheRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

class ItemDetailsRepository(
    private val api: KinoPubApiClient,
    private val watchStateRepository: WatchStateRepository,
    private val contentCache: ContentCacheRepository,
) {

    fun observeItemDetails(id: Int, force: Boolean = false): Flow<Cached<Item>> {
        return loadItemDetails(id, force = force).onEach { emission ->
            // Details do carry watch fields, unlike the catalogue, so every title someone actually
            // reads is a free chance to sharpen the local index.
            //
            // Recorded here rather than inside the loader because a read need not have run the
            // loader at all: it may be serving the store, or joining a prefetch already in flight.
            // Either way it received the value and the index should hear about it. A stale value is
            // skipped — it is about to be replaced, and it can be days old.
            if (emission !is Cached.Value || emission.isStale) return@onEach
            watchStateRepository.recordFromServer(listOf(emission.value), observedAt = emission.updatedAt)
        }
    }

    /**
     * Fills or revalidates the details cache without any of the effects reading has.
     *
     * For work the user did not ask for: a prefetch must not touch the watch-state index, because
     * that would tick `settledChanges`, redraw watched badges and re-page a filtered list behind a
     * card nobody has opened. It shares [observeItemDetails]'s load, so the value it leaves behind
     * is exactly what the details screen later reads, and a press that lands mid-warm joins the
     * request rather than issuing a second one.
     *
     * Throws when there was nothing to serve and the load failed. A failed revalidation over a
     * stored value is not a failure here: that value stands and remains usable.
     */
    suspend fun warmItemDetails(id: Int) {
        getItemDetailsCacheOnly(id)
    }

    /**
     * Reads through the shared details cache without publishing the item's watch fields.
     *
     * Used by focus-driven previews as well as prefetching: both are speculative reads that must
     * share the request Details will later join, but neither may redraw or re-page the list merely
     * because focus rested on a card.
     */
    suspend fun getItemDetailsCacheOnly(id: Int): Item {
        return loadItemDetails(id).lastValue()
    }

    private fun loadItemDetails(id: Int, force: Boolean = false): Flow<Cached<Item>> {
        return contentCache.observeItemDetails(id, force = force) { fetchItem(id) }
    }

    fun observeSimilarItems(id: Int, force: Boolean = false): Flow<Cached<List<Item>>> {
        return contentCache.observeSimilarItems(id, force = force) {
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
        contentCache.markItemDetailsStale(itemId)
    }

    suspend fun invalidate(itemId: Int) {
        contentCache.invalidateItemDetails(itemId)
    }

    suspend fun clear() {
        contentCache.clear()
    }

    private suspend fun fetchItem(id: Int): Item {
        val response = api.getItemDetails(id).getOrThrow()
        return checkNotNull(response.item) { "Details response for item $id carried no item" }
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
