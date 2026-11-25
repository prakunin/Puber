package com.kino.puber.data.cache

import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.api.models.Season
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.repository.PersistentPayloadStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * The single cache boundary for content returned by KinoPub.
 *
 * Lists store only ordered item IDs; every card payload lives once under its item ID. A sparse list
 * response is merged into that payload without erasing richer fields previously learned from
 * Details. Screen-specific interactors choose keys and TTLs, but never own cache instances.
 */
class ContentCacheRepository(
    private val store: PersistentPayloadStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val json: Json = CachedFeed.DefaultJson,
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    // One feed per record type, so the memory tier holds the decoded record and a hit costs no decode.
    private val itemFeed = newFeed(ItemRecord.serializer(), persistFallback = ItemRecord::cardOnly)
    private val listFeed = newFeed(ItemListRecord.serializer())
    private val pageFeed = newFeed(ItemPageRecord.serializer())
    private val historyFeed = newFeed(HistoryPageRecord.serializer())

    /** One feed per payload store key, created on first use because the payload type is the caller's. */
    private val payloadFeeds = ConcurrentHashMap<String, CachedFeed<*>>()
    private val seenWatchStateVersions = ConcurrentHashMap<String, Long>()

    val generation: Long
        get() = store.generation

    fun observeItems(
        key: String,
        ttl: Duration,
        force: Boolean = false,
        loader: suspend () -> List<Item>,
    ): Flow<Cached<List<Item>>> = flow {
        val storeKey = queryKey(key)
        val incomplete = queryNeedsRefetch(listFeed, storeKey, ItemListRecord::itemIds)
        listFeed.load(
            key = storeKey,
            force = force || incomplete,
            ttl = ttl,
        ) {
            val generation = store.generation
            val items = loader()
            mergeItems(items, details = false, expectedGeneration = generation)
            ItemListRecord(items.map(Item::id))
        }.collect { cached ->
            when (cached) {
                is Cached.Value -> emit(
                    Cached.Value(
                        value = readItems(cached.value.itemIds),
                        isStale = cached.isStale,
                        updatedAt = cached.updatedAt,
                    )
                )
                is Cached.RefreshFailed -> emit(cached)
            }
        }
    }.flowOn(decodeDispatcher)

    fun observeItemPage(
        key: String,
        ttl: Duration,
        force: Boolean = false,
        loader: suspend () -> PaginatedResponse<Item>,
    ): Flow<Cached<PaginatedResponse<Item>>> = flow {
        val storeKey = queryKey(key)
        val incomplete = queryNeedsRefetch(pageFeed, storeKey, ItemPageRecord::itemIds)
        pageFeed.load(
            key = storeKey,
            force = force || incomplete,
            ttl = ttl,
        ) {
            val generation = store.generation
            val page = loader()
            mergeItems(page.items, details = false, expectedGeneration = generation)
            ItemPageRecord(page.items.map(Item::id), page.pagination)
        }.collect { cached ->
            when (cached) {
                is Cached.Value -> emit(
                    Cached.Value(
                        value = PaginatedResponse(
                            items = readItems(cached.value.itemIds),
                            pagination = cached.value.pagination,
                        ),
                        isStale = cached.isStale,
                        updatedAt = cached.updatedAt,
                    )
                )
                is Cached.RefreshFailed -> emit(cached)
            }
        }
    }.flowOn(decodeDispatcher)

    fun sectionPage(
        key: String,
        watchStateVersion: Long,
        force: Boolean = false,
        loader: suspend () -> PaginatedResponse<Item>,
    ): Flow<Cached<PaginatedResponse<Item>>> = flow {
        val indexMoved = seenWatchStateVersions.put(key, watchStateVersion) != watchStateVersion
        emitAll(
            observeItemPage(
                key = key,
                ttl = CacheTtl.CatalogueSection,
                force = force || indexMoved,
                loader = loader,
            )
        )
    }

    fun watchlist(
        force: Boolean = false,
        loader: suspend () -> List<Item>,
    ): Flow<Cached<List<Item>>> = observeItems(
        key = CacheKeys.watchlist(),
        ttl = CacheTtl.Watchlist,
        force = force,
        loader = loader,
    )

    fun historyFirstPage(
        force: Boolean = false,
        loader: suspend () -> PaginatedResponse<History>,
    ): Flow<Cached<PaginatedResponse<History>>> = flow {
        val storeKey = queryKey(CacheKeys.historyPage(FIRST_PAGE))
        val incomplete = queryNeedsRefetch(historyFeed, storeKey) { record ->
            record.items.map(HistoryEntryRecord::itemId)
        }
        historyFeed.load(
            key = storeKey,
            force = force || incomplete,
            ttl = CacheTtl.HistoryPage,
        ) {
            val generation = store.generation
            val page = loader()
            mergeItems(
                items = page.items.map(History::item),
                details = false,
                expectedGeneration = generation,
            )
            HistoryPageRecord(
                items = page.items.map(HistoryEntryRecord::from),
                pagination = page.pagination,
            )
        }.collect { cached ->
            when (cached) {
                is Cached.Value -> {
                    val record = cached.value
                    // Zipping would silently re-pair entries with the wrong items once a card
                    // row is missing, so each entry looks its own item up and drops out if it
                    // is gone.
                    val items = readItemRecords(record.items.map(HistoryEntryRecord::itemId))
                    emit(
                        Cached.Value(
                            value = PaginatedResponse(
                                items = record.items.mapNotNull { entry ->
                                    items[entry.itemId]?.let { entry.toHistory(it.item) }
                                },
                                pagination = record.pagination,
                            ),
                            isStale = cached.isStale,
                            updatedAt = cached.updatedAt,
                        )
                    )
                }
                is Cached.RefreshFailed -> emit(cached)
            }
        }
    }.flowOn(decodeDispatcher)

    fun observeItemDetails(
        itemId: Int,
        force: Boolean = false,
        loader: suspend () -> Item,
    ): Flow<Cached<Item>> = flow {
        val existing = readItemRecord(itemId)
        val cachedRecordRequiresRefresh = when (val updatedAt = existing?.detailsUpdatedAt) {
            null -> existing != null
            else -> clock() - updatedAt > CacheTtl.ItemDetails.inWholeMilliseconds
        }
        var served = false
        itemFeed.load(
            key = itemKey(itemId),
            force = force || cachedRecordRequiresRefresh,
            ttl = CacheTtl.ItemDetails,
        ) {
            mergeItemRecord(
                existing = readItemRecord(itemId),
                incoming = loader(),
                details = true,
            )
        }.collect { cached ->
            when (cached) {
                is Cached.Value -> {
                    val record = cached.value
                    // Not `cached.updatedAt`: the row is shared with the card, and every list merge
                    // restamps it. Callers that order observations by this — the watch-state index —
                    // would credit details fetched long ago to the moment a card was last seen.
                    val detailsUpdatedAt = record.detailsUpdatedAt
                    if (detailsUpdatedAt != null) {
                        served = true
                        emit(Cached.Value(record.item, cached.isStale, detailsUpdatedAt))
                    }
                }
                // A record holding only a card emits nothing above, so a quiet RefreshFailed would
                // leave the caller waiting on a value that is never coming — a details screen opened
                // offline over a browsed catalogue spins forever. Only a caller that already has
                // something on screen can be told about a failed refresh instead of being thrown at.
                is Cached.RefreshFailed -> if (served) emit(cached) else throw cached.error
            }
        }
    }.flowOn(decodeDispatcher)

    fun observeSimilarItems(
        itemId: Int,
        force: Boolean = false,
        loader: suspend () -> List<Item>,
    ): Flow<Cached<List<Item>>> = observeItems(
        key = CacheKeys.similar(itemId),
        ttl = CacheTtl.SimilarItems,
        force = force,
        loader = loader,
    )

    fun <T : Any> observePayload(
        key: String,
        serializer: KSerializer<T>,
        ttl: Duration,
        force: Boolean = false,
        loader: suspend () -> T,
    ): Flow<Cached<T>> = flow {
        val storeKey = payloadKey(key)
        emitAll(payloadFeed(storeKey, serializer).load(key = storeKey, force = force, ttl = ttl, loader = loader))
    }.flowOn(decodeDispatcher)

    suspend fun <T : Any> getPayload(
        key: String,
        serializer: KSerializer<T>,
        ttl: Duration,
        force: Boolean = false,
        loader: suspend () -> T,
    ): T {
        var value: T? = null
        var failure: Throwable? = null
        observePayload(key, serializer, ttl, force, loader).collect { cached ->
            when (cached) {
                is Cached.Value -> value = cached.value
                is Cached.RefreshFailed -> failure = cached.error
            }
        }
        return value ?: throw (failure ?: IllegalStateException("No content was emitted for $key"))
    }

    suspend fun mergeItems(
        items: List<Item>,
        expectedGeneration: Long = generation,
    ): List<Item> = withContext(decodeDispatcher) {
        mergeItems(items, details = false, expectedGeneration = expectedGeneration)
        val records = readItemRecords(items.map(Item::id))
        // The response itself is the fallback here, unlike the cached read paths: the caller just
        // fetched these, so a row that went missing between the merge and the read-back costs
        // nothing, whereas dropping it would quietly hand the screen fewer items than the server
        // sent with nothing to notice or repair it.
        items.map { item -> records[item.id]?.item ?: item }
    }

    /**
     * Both of these rewrite the record to mean "these details are no longer to be trusted", which is
     * an invalidation wearing a write's clothes — the row survives because the card half of it is
     * still worth drawing. Marked as superseding so a details load already out on the network cannot
     * land afterwards and quietly restore the freshness this just took away.
     */
    suspend fun markItemDetailsStale(itemId: Int) = withContext(decodeDispatcher) {
        val current = readItemRecord(itemId) ?: return@withContext
        itemFeed.put(
            key = itemKey(itemId),
            // A card-only record has no details to distrust: stamping it would serve the bare card as details.
            value = current.copy(detailsUpdatedAt = current.detailsUpdatedAt?.let { STALE_TIMESTAMP }),
            supersede = true,
        )
    }

    suspend fun invalidateItemDetails(itemId: Int) = withContext(decodeDispatcher) {
        val current = readItemRecord(itemId) ?: return@withContext
        itemFeed.put(
            key = itemKey(itemId),
            value = current.copy(detailsUpdatedAt = null),
            supersede = true,
        )
    }

    suspend fun invalidateQuery(key: String) {
        // A query key names a list, a page or a history page depending on who wrote it, so each of
        // those feeds is asked. The payload feed may not exist yet on a fresh process, in which case
        // the row is still there for the next read to find, so it is removed from the store itself.
        val query = queryKey(key)
        listFeed.invalidate(query)
        pageFeed.invalidate(query)
        historyFeed.invalidate(query)
        val payload = payloadKey(key)
        payloadFeeds[payload]?.invalidate(payload) ?: store.remove(payload)
    }

    suspend fun clear() {
        store.clear()
    }

    private suspend fun mergeItems(
        items: List<Item>,
        details: Boolean,
        expectedGeneration: Long,
    ) {
        checkGeneration(expectedGeneration)
        if (items.isEmpty()) return
        val itemsById = items.associateBy(Item::id)
        val existing = readItemRecords(itemsById.keys.toList())
        val updatedAt = clock()
        itemFeed.putAll(
            values = itemsById.map { (itemId, item) ->
                itemKey(itemId) to mergeItemRecord(
                    existing = existing[itemId],
                    incoming = item,
                    details = details,
                    updatedAt = updatedAt,
                )
            }.toMap(),
            updatedAt = updatedAt,
        )
        checkGeneration(expectedGeneration)
    }

    private fun checkGeneration(expected: Long) {
        check(store.generation == expected) { "Content source changed while a request was in flight" }
    }

    /**
     * Resolves a list's IDs to card payloads, dropping any that are gone.
     *
     * Card rows are shared and can disappear on their own — a decode that failed after a model
     * change, the hard ceiling, a wipe crossing a write — while the list naming them survives.
     * Failing here would take down the whole list inside a collector, which the screens only log,
     * so the row would vanish on every launch instead. [queryNeedsRefetch] has already asked the
     * load to refetch such a list; showing the cards that remain is the better wait.
     */
    private suspend fun readItems(ids: List<Int>): List<Item> {
        val records = readItemRecords(ids)
        return ids.mapNotNull { itemId -> records[itemId]?.item }
    }

    /**
     * Reads a stored query row before a load consults it, and reports whether the load must refetch.
     *
     * A row that references card payloads that are gone is left in place — its remaining cards are
     * still worth drawing — with a refetch demanded, because a row that is not stale would otherwise
     * never be reloaded. The feed drops a row that no longer decodes, so that case finds nothing here.
     *
     * Read with [CachedFeed.peekAll] rather than `peek`: only the former promotes a row read from
     * Room into the memory tier, so the load that follows finds it there instead of reading and
     * decoding the same row a second time.
     */
    private suspend fun <R : Any> queryNeedsRefetch(
        feed: CachedFeed<R>,
        key: String,
        itemIds: (R) -> List<Int>,
    ): Boolean {
        val ids = feed.peekAll(listOf(key))[key]?.value?.let(itemIds)?.distinct().orEmpty()
        return ids.isNotEmpty() && readItemRecords(ids).size != ids.size
    }

    private fun <V : Any> newFeed(
        serializer: KSerializer<V>,
        persistFallback: ((V) -> V)? = null,
    ): CachedFeed<V> = CachedFeed(
        store = store,
        serializer = serializer,
        ttl = CachedFeed.HardCeiling,
        keyPrefix = CONTENT_PREFIX,
        json = json,
        clock = clock,
        // A details record carries the whole seasons/episodes/files tree of a series, a megabyte of
        // JSON for a long one, and the hard ceiling keeps it pinned for a week. Count alone let a
        // browsing session pile tens of megabytes into this tier; weighing each entry by the length of
        // the JSON it was stored as keeps the cap proportional without walking the decoded object.
        // Room still has them all.
        maximumMemoryWeight = MEMORY_TIER_ENCODED_CHARS,
        memoryWeigher = { encodedLength -> encodedLength },
        persistFallback = persistFallback,
    )

    /** A payload's type is the caller's, so its feed is made on first use and kept under its key. */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> payloadFeed(storeKey: String, serializer: KSerializer<T>): CachedFeed<T> =
        payloadFeeds.computeIfAbsent(storeKey) { newFeed(serializer) } as CachedFeed<T>

    private suspend fun readItemRecord(itemId: Int): ItemRecord? {
        return readItemRecords(listOf(itemId))[itemId]
    }

    private suspend fun readItemRecords(ids: List<Int>): Map<Int, ItemRecord> {
        val keysById = ids.distinct().associateWith(::itemKey)
        val cachedByKey = itemFeed.peekAll(keysById.values.toList())
        return keysById.mapNotNull { (itemId, key) -> cachedByKey[key]?.let { itemId to it.value } }.toMap()
    }

    private fun mergeItemRecord(
        existing: ItemRecord?,
        incoming: Item,
        details: Boolean,
        updatedAt: Long = clock(),
    ): ItemRecord {
        if (existing == null) {
            return ItemRecord(
                item = incoming,
                cardUpdatedAt = updatedAt,
                detailsUpdatedAt = updatedAt.takeIf { details },
            )
        }
        return existing.copy(
            item = existing.item.mergeSparse(incoming),
            cardUpdatedAt = updatedAt,
            detailsUpdatedAt = if (details) updatedAt else existing.detailsUpdatedAt,
        )
    }

    // Explicit field-by-field policy is intentional: nullable fields from a sparse endpoint must
    // not erase richer data, while non-null values remain authoritative. Keeping it visible here
    // makes model additions a compile-time review point instead of a reflection-based surprise.
    @Suppress("CyclomaticComplexMethod")
    private fun Item.mergeSparse(incoming: Item): Item = copy(
        title = incoming.title,
        type = incoming.type,
        year = incoming.year ?: year,
        rating = incoming.rating ?: rating,
        genres = incoming.genres ?: genres,
        countries = incoming.countries ?: countries,
        director = incoming.director ?: director,
        cast = incoming.cast ?: cast,
        plot = incoming.plot ?: plot,
        duration = incoming.duration ?: duration,
        posters = incoming.posters ?: posters,
        trailer = incoming.trailer ?: trailer,
        quality = incoming.quality ?: quality,
        ac3 = incoming.ac3 ?: ac3,
        advert = incoming.advert ?: advert,
        subscribed = incoming.subscribed ?: subscribed,
        inWatchlist = incoming.inWatchlist ?: inWatchlist,
        imdb = incoming.imdb ?: imdb,
        imdbRating = incoming.imdbRating ?: imdbRating,
        imdbVotes = incoming.imdbVotes ?: imdbVotes,
        kinopoisk = incoming.kinopoisk ?: kinopoisk,
        kinopoiskRating = incoming.kinopoiskRating ?: kinopoiskRating,
        kinopoiskVotes = incoming.kinopoiskVotes ?: kinopoiskVotes,
        langs = incoming.langs ?: langs,
        poorQuality = incoming.poorQuality ?: poorQuality,
        ratingPercentage = incoming.ratingPercentage ?: ratingPercentage,
        ratingVotes = incoming.ratingVotes ?: ratingVotes,
        subtype = incoming.subtype ?: subtype,
        tracklist = incoming.tracklist ?: tracklist,
        updatedAt = incoming.updatedAt ?: updatedAt,
        createdAt = incoming.createdAt ?: createdAt,
        views = incoming.views ?: views,
        voice = incoming.voice ?: voice,
        finished = incoming.finished ?: finished,
        comments = incoming.comments ?: comments,
        seasons = incoming.seasons ?: seasons,
        videos = incoming.videos ?: videos,
        bookmarks = incoming.bookmarks ?: bookmarks,
        total = incoming.total ?: total,
        watched = incoming.watched ?: watched,
        new = incoming.new ?: new,
        watching = incoming.watching ?: watching,
        fps = incoming.fps ?: fps,
        ageRating = incoming.ageRating ?: ageRating,
    )

    @Serializable
    private data class ItemRecord(
        val item: Item,
        val cardUpdatedAt: Long,
        val detailsUpdatedAt: Long? = null,
    ) {
        /**
         * What is persisted when the whole record is over the store's row limit: the card, and the
         * season list without its episodes, so "N seasons" survives. No `detailsUpdatedAt`, so a
         * reader treats it as a card and loads the details.
         */
        fun cardOnly(): ItemRecord = copy(
            item = item.copy(
                seasons = item.seasons?.map { Season(id = it.id, number = it.number, title = it.title) },
                videos = null,
            ),
            detailsUpdatedAt = null,
        )
    }

    @Serializable
    private data class ItemListRecord(val itemIds: List<Int>)

    @Serializable
    private data class ItemPageRecord(
        val itemIds: List<Int>,
        val pagination: Pagination,
    )

    @Serializable
    private data class HistoryPageRecord(
        val items: List<HistoryEntryRecord>,
        val pagination: Pagination,
    )

    @Serializable
    private data class HistoryEntryRecord(
        val recordId: Int? = null,
        val itemId: Int,
        val video: Video? = null,
        val season: Int? = null,
        val time: Int? = null,
        val updated: String? = null,
    ) {
        fun toHistory(item: Item): History = History(
            recordId = recordId,
            item = item,
            video = video,
            season = season,
            time = time,
            updated = updated,
        )

        companion object {
            fun from(history: History): HistoryEntryRecord = HistoryEntryRecord(
                recordId = history.recordId,
                itemId = history.item.id,
                video = history.video,
                season = history.season,
                time = history.time,
                updated = history.updated,
            )
        }
    }

    companion object {
        const val INDEX_INDEPENDENT: Long = -1L

        private const val CONTENT_PREFIX = "content:v2:"
        private const val STALE_TIMESTAMP = 0L
        private const val FIRST_PAGE = 1
        private const val MEMORY_TIER_ENCODED_CHARS = 16L * 1024 * 1024

        fun itemKey(itemId: Int): String = "${CONTENT_PREFIX}item:$itemId"
        fun queryKey(key: String): String = "${CONTENT_PREFIX}query:$key"
        fun payloadKey(key: String): String = "${CONTENT_PREFIX}payload:$key"
    }
}
