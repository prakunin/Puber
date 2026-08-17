package com.kino.puber.domain.interactor.home

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.KCollection
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.isAnime
import com.kino.puber.data.cache.Cached
import com.kino.puber.data.cache.CacheKeys
import com.kino.puber.data.cache.CacheTtl
import com.kino.puber.data.cache.CachedFeed
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.PersistentPayloadStore
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.domain.interactor.bookmarks.BookmarkFoldersInteractor
import com.kino.puber.domain.interactor.bookmarks.WatchLaterBookmarkInteractor
import com.kino.puber.domain.interactor.watchstate.RecentlyPlayedOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer

class HomeInteractor(
    private val api: KinoPubApiClient,
    private val watchLaterBookmarkInteractor: WatchLaterBookmarkInteractor,
    private val bookmarkFolders: BookmarkFoldersInteractor,
    private val navigationPreferencesRepository: NavigationPreferencesRepository,
    private val watchStateRepository: WatchStateRepository,
    private val store: PersistentPayloadStore,
    private val recentlyPlayedOrder: RecentlyPlayedOrder,
) {

    /**
     * How many times the content cache has been wiped.
     *
     * Every route that switches domains goes through `ApiDomainInteractor.clearDomainSensitiveCaches`
     * and so bumps this — the home screen's own dialog, the device settings screen, an auto-failover.
     * Only the first of those can tell the home screen what it did; the others do not know it exists.
     * So the wipe publishes the fact instead, and a screen holding rows built from the cache can ask
     * whether they still describe the catalogue it is talking to.
     */
    val cacheGeneration: Long get() = store.generation

    private val items = CachedFeed(
        store = store,
        serializer = ListSerializer(Item.serializer()),
        ttl = CacheTtl.HomeSection,
        keyPrefix = CacheKeys.HomePrefix,
    )

    /**
     * A row a finished episode makes wrong at once, so it gets a TTL of its own rather than the
     * half-hour the editorial rows are happy with.
     */
    private val watching = CachedFeed(
        store = store,
        serializer = ListSerializer(Item.serializer()),
        ttl = CacheTtl.ContinueWatching,
        keyPrefix = CacheKeys.HomePrefix,
    )

    private val collections = CachedFeed(
        store = store,
        serializer = ListSerializer(KCollection.serializer()),
        ttl = CacheTtl.HomeSection,
        keyPrefix = CacheKeys.HomePrefix,
    )

    fun observeWatchingItems(force: Boolean = false): Flow<Cached<List<Item>>> {
        return watching.load(CacheKeys.home(CONTINUE_WATCHING_KEY), force = force) {
            getWatchingItems().getOrThrow()
        }
    }

    /**
     * One key holds one rendered row, so the two requests behind hot items are merged and sorted
     * before they are stored rather than after they are read.
     */
    fun observeHotItems(): Flow<Cached<List<Item>>> {
        return items.load(CacheKeys.home(HOT_KEY)) {
            val movies = getHotItems("movie", HOT_ITEMS_COUNT).getOrThrow()
            val series = getHotItems("serial", HOT_ITEMS_COUNT).getOrThrow()
            (movies + series).sortedByDescending { it.ratingPercentage ?: 0 }
        }
    }

    fun observeFreshItems(): Flow<Cached<List<Item>>> {
        return items.load(CacheKeys.home(FRESH_KEY)) {
            val movies = getFreshItems("movie").getOrThrow()
            val series = getFreshItems("serial").getOrThrow()
            (movies + series).sortedByDescending { it.updatedAt.orEmpty() }
        }
    }

    fun observePopularMovies(): Flow<Cached<List<Item>>> {
        return items.load(CacheKeys.home(POPULAR_MOVIES_KEY)) { getPopularByType("movie").getOrThrow() }
    }

    fun observePopularSeries(): Flow<Cached<List<Item>>> {
        return items.load(CacheKeys.home(POPULAR_SERIES_KEY)) { getPopularByType("serial").getOrThrow() }
    }

    fun observeWatchLaterItems(): Flow<Cached<List<Item>>> {
        return items.load(CacheKeys.home(WATCH_LATER_KEY)) { getWatchLaterItems().getOrThrow() }
    }

    fun observeBookmarkItems(): Flow<Cached<List<Item>>> {
        return items.load(CacheKeys.home(BOOKMARKS_KEY)) { getGenericBookmarkItems().getOrThrow() }
    }

    fun observeCollections(): Flow<Cached<List<KCollection>>> {
        return collections.load(CacheKeys.home(COLLECTIONS_KEY)) { getCollections().getOrThrow() }
    }

    suspend fun getHotItems(type: String = "movie", limit: Int = 10): Result<List<Item>> {
        return getDiscoveryItems(shortcut = "hot", type = type, limit = limit)
    }

    suspend fun getWatchingItems(): Result<List<Item>> {
        return api.getWatchingList(onlySubscribed = true)
            .mapCatching { recentlyPlayedOrder.sort(it.items.orEmpty()) }
    }

    suspend fun getFreshItems(type: String): Result<List<Item>> {
        return getDiscoveryItems(shortcut = "fresh", type = type)
    }

    suspend fun getPopularByType(type: String): Result<List<Item>> {
        return getDiscoveryItems(shortcut = "popular", type = type)
    }

    suspend fun getBookmarkFolders(): Result<List<Bookmark>> {
        return bookmarkFolders.folders()
    }

    suspend fun getBookmarkItems(folderId: Int): Result<List<Item>> {
        return api.getBookmarkItems(folderId).map { it.items }
    }

    suspend fun getGenericBookmarkItems(): Result<List<Item>> {
        return bookmarkFolders.folders().mapCatching { folders ->
            val folder = folders.firstOrNull { it.title != WatchLaterBookmarkInteractor.FOLDER_TITLE }
                ?: return@mapCatching emptyList()
            api.getBookmarkItems(folder.id).getOrThrow().items
        }
    }

    suspend fun getWatchLaterItems(): Result<List<Item>> {
        return watchLaterBookmarkInteractor.getItems()
    }

    suspend fun getCollections(): Result<List<KCollection>> {
        return api.getCollections(page = 1).map { it.items }
    }

    suspend fun lastWatchedAt(): Map<Int, Long> = watchStateRepository.lastWatchedAt()

    /**
     * Applies the Home-specific view of the global content preferences to a cached row.
     *
     * Home caches the server payload rather than the rendered row, so a setting or watch-state
     * change can be reflected immediately without waiting for the section TTL or asking the server
     * for the same items again. Personal rows may additionally put recently played titles first;
     * the stable sort leaves titles with no history in the order their endpoint chose.
     */
    fun prepareHomeItems(
        items: List<Item>,
        lastWatchedAt: Map<Int, Long>,
        sortByLastWatched: Boolean,
    ): List<Item> {
        val visibleItems = if (navigationPreferencesRepository.contentPreferences.value.hideWatched) {
            items.filterNot(watchStateRepository::isFullyWatched)
        } else {
            items
        }
        if (!sortByLastWatched) return visibleItems
        return visibleItems.sortedByDescending { item -> lastWatchedAt[item.id] ?: 0L }
    }

    private suspend fun getDiscoveryItems(
        shortcut: String,
        type: String,
        limit: Int? = null,
    ): Result<List<Item>> {
        val firstPageResult = api.getItemsByShortcut(shortcut, type = type)
        if (navigationPreferencesRepository.contentPreferences.value.showAnime) {
            return firstPageResult.map { response ->
                response.items.limitTo(limit)
            }
        }

        return firstPageResult.mapCatching { firstPage ->
            val targetSize = limit ?: firstPage.items.size
            val visibleItems = linkedMapOf<Int, Item>()
            var currentRequestedPage = FIRST_PAGE
            var lastPage = firstPage

            while (true) {
                check(lastPage.pagination.current == currentRequestedPage) {
                    "Home discovery pagination current ${lastPage.pagination.current} " +
                        "did not match requested page $currentRequestedPage"
                }
                lastPage.items
                    .asSequence()
                    .filterNot(Item::isAnime)
                    .forEach { item -> visibleItems.putIfAbsent(item.id, item) }
                if (visibleItems.size >= targetSize || !lastPage.hasNextPage()) break
                currentRequestedPage = lastPage.pagination.current + 1
                lastPage = api.getItemsByShortcut(
                    shortcut = shortcut,
                    type = type,
                    page = currentRequestedPage,
                ).getOrThrow()
            }

            visibleItems.values.toList().limitTo(targetSize)
        }
    }

    private fun PaginatedResponse<Item>.hasNextPage(): Boolean {
        return pagination.current < pagination.total
    }

    private fun List<Item>.limitTo(limit: Int?): List<Item> {
        return limit?.let(::take) ?: this
    }

    companion object {
        private const val FIRST_PAGE = 1
        private const val HOT_ITEMS_COUNT = 20
        private const val CONTINUE_WATCHING_KEY = "continue_watching"
        private const val HOT_KEY = "hot"
        private const val FRESH_KEY = "fresh"
        private const val POPULAR_MOVIES_KEY = "popular_movies"
        private const val POPULAR_SERIES_KEY = "popular_series"
        private const val WATCH_LATER_KEY = "watch_later"
        private const val BOOKMARKS_KEY = "bookmarks"
        private const val COLLECTIONS_KEY = "collections"
    }
}
