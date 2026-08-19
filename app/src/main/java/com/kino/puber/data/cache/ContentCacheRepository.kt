package com.kino.puber.data.cache

import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.repository.PersistentPayloadStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
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
) {

    private val cache = CachedFeed(
        store = store,
        serializer = String.serializer(),
        ttl = CachedFeed.HardCeiling,
        keyPrefix = CONTENT_PREFIX,
        clock = clock,
    )
    private val seenWatchStateVersions = ConcurrentHashMap<String, Long>()

    val generation: Long
        get() = store.generation

    fun observeItems(
        key: String,
        ttl: Duration,
        force: Boolean = false,
        loader: suspend () -> List<Item>,
    ): Flow<Cached<List<Item>>> = flow {
        cache.load(
            key = queryKey(key),
            force = force,
            ttl = ttl,
        ) {
            val generation = store.generation
            val items = loader()
            mergeItems(items, details = false, expectedGeneration = generation)
            encode(ItemListRecord(items.map(Item::id)))
        }.collect { cached ->
            when (cached) {
                is Cached.Value -> {
                    val record = decode<ItemListRecord>(cached.value)
                    emit(
                        Cached.Value(
                            value = readItems(record.itemIds),
                            isStale = cached.isStale,
                            updatedAt = cached.updatedAt,
                        )
                    )
                }
                is Cached.RefreshFailed -> emit(cached)
            }
        }
    }

    fun observeItemPage(
        key: String,
        ttl: Duration,
        force: Boolean = false,
        loader: suspend () -> PaginatedResponse<Item>,
    ): Flow<Cached<PaginatedResponse<Item>>> = flow {
        cache.load(
            key = queryKey(key),
            force = force,
            ttl = ttl,
        ) {
            val generation = store.generation
            val page = loader()
            mergeItems(page.items, details = false, expectedGeneration = generation)
            encode(ItemPageRecord(page.items.map(Item::id), page.pagination))
        }.collect { cached ->
            when (cached) {
                is Cached.Value -> {
                    val record = decode<ItemPageRecord>(cached.value)
                    emit(
                        Cached.Value(
                            value = PaginatedResponse(
                                items = readItems(record.itemIds),
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
    }

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
        cache.load(
            key = queryKey(CacheKeys.historyPage(FIRST_PAGE)),
            force = force,
            ttl = CacheTtl.HistoryPage,
        ) {
            val generation = store.generation
            val page = loader()
            mergeItems(
                items = page.items.map(History::item),
                details = false,
                expectedGeneration = generation,
            )
            encode(
                HistoryPageRecord(
                    items = page.items.map(HistoryEntryRecord::from),
                    pagination = page.pagination,
                )
            )
        }.collect { cached ->
            when (cached) {
                is Cached.Value -> {
                    val record = decode<HistoryPageRecord>(cached.value)
                    val items = readItems(record.items.map(HistoryEntryRecord::itemId))
                    emit(
                        Cached.Value(
                            value = PaginatedResponse(
                                items = record.items.zip(items) { entry, item -> entry.toHistory(item) },
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
    }

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
        cache.load(
            key = itemKey(itemId),
            force = force || cachedRecordRequiresRefresh,
            ttl = CacheTtl.ItemDetails,
        ) {
            val record = mergeItemRecord(
                existing = readItemRecord(itemId),
                incoming = loader(),
                details = true,
            )
            encode(record)
        }.collect { cached ->
            when (cached) {
                is Cached.Value -> {
                    val record = decode<ItemRecord>(cached.value)
                    if (record.detailsUpdatedAt != null) {
                        emit(Cached.Value(record.item, cached.isStale, cached.updatedAt))
                    }
                }
                is Cached.RefreshFailed -> emit(cached)
            }
        }
    }

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
        cache.load(
            key = payloadKey(key),
            force = force,
            ttl = ttl,
            loader = { json.encodeToString(serializer, loader()) },
        ).collect { cached ->
            when (cached) {
                is Cached.Value -> emit(
                    Cached.Value(
                        value = json.decodeFromString(serializer, cached.value),
                        isStale = cached.isStale,
                        updatedAt = cached.updatedAt,
                    )
                )
                is Cached.RefreshFailed -> emit(cached)
            }
        }
    }

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
    ): List<Item> {
        mergeItems(items, details = false, expectedGeneration = expectedGeneration)
        return readItems(items.map(Item::id))
    }

    suspend fun markItemDetailsStale(itemId: Int) {
        val current = readItemRecord(itemId) ?: return
        cache.put(itemKey(itemId), encode(current.copy(detailsUpdatedAt = STALE_TIMESTAMP)))
    }

    suspend fun invalidateItemDetails(itemId: Int) {
        val current = readItemRecord(itemId) ?: return
        cache.put(itemKey(itemId), encode(current.copy(detailsUpdatedAt = null)))
    }

    suspend fun invalidateQuery(key: String) {
        cache.invalidate(queryKey(key))
        cache.invalidate(payloadKey(key))
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
        cache.putAll(
            values = itemsById.map { (itemId, item) ->
                itemKey(itemId) to encode(
                    mergeItemRecord(
                        existing = existing[itemId],
                        incoming = item,
                        details = details,
                        updatedAt = updatedAt,
                    )
                )
            }.toMap(),
            updatedAt = updatedAt,
        )
        checkGeneration(expectedGeneration)
    }

    private fun checkGeneration(expected: Long) {
        check(store.generation == expected) { "Content source changed while a request was in flight" }
    }

    private suspend fun readItems(ids: List<Int>): List<Item> {
        val records = readItemRecords(ids)
        return ids.map { itemId ->
            checkNotNull(records[itemId]) {
                "Content query references missing item $itemId"
            }.item
        }
    }

    private suspend fun readItemRecord(itemId: Int): ItemRecord? {
        return readItemRecords(listOf(itemId))[itemId]
    }

    private suspend fun readItemRecords(ids: List<Int>): Map<Int, ItemRecord> {
        val keysById = ids.distinct().associateWith(::itemKey)
        val cachedByKey = cache.peekAll(keysById.values.toList())
        val records = mutableMapOf<Int, ItemRecord>()
        keysById.forEach { (itemId, key) ->
            val cached = cachedByKey[key] ?: return@forEach
            runCatching { decode<ItemRecord>(cached.value) }
                .onSuccess { records[itemId] = it }
                .onFailure { cache.invalidate(key) }
        }
        return records
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

    private inline fun <reified T> encode(value: T): String = json.encodeToString(value)

    private inline fun <reified T> decode(value: String): T = json.decodeFromString(value)

    @Serializable
    private data class ItemRecord(
        val item: Item,
        val cardUpdatedAt: Long,
        val detailsUpdatedAt: Long? = null,
    )

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

        private const val CONTENT_PREFIX = "content:v1:"
        private const val STALE_TIMESTAMP = 0L
        private const val FIRST_PAGE = 1

        fun itemKey(itemId: Int): String = "${CONTENT_PREFIX}item:$itemId"
        fun queryKey(key: String): String = "${CONTENT_PREFIX}query:$key"
        fun payloadKey(key: String): String = "${CONTENT_PREFIX}payload:$key"
    }
}
