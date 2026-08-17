package com.kino.puber.data.cache

import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.repository.PersistentPayloadStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.builtins.ListSerializer
import java.util.concurrent.ConcurrentHashMap

/**
 * The first page of every list surface outside home: catalogue sections, the watching list, the
 * first page of history.
 *
 * A singleton rather than a field on the interactors that use it. Those are all `scopedOf` and die
 * with their screen, and both things a [CachedFeed] carries — its memory tier and its in-flight
 * de-duplication — are per instance. Held by the screen, a feed would lose the tier on every tab
 * switch and would let two screens showing the same section issue two requests for one key.
 */
class ContentPageCache(
    store: PersistentPayloadStore,
    clock: () -> Long = System::currentTimeMillis,
) {

    private val sections = CachedFeed(
        store = store,
        serializer = PaginatedResponse.serializer(Item.serializer()),
        ttl = CacheTtl.CatalogueSection,
        keyPrefix = CacheKeys.SectionPrefix,
        clock = clock,
    )

    private val watchlistFeed = CachedFeed(
        store = store,
        serializer = ListSerializer(Item.serializer()),
        ttl = CacheTtl.Watchlist,
        keyPrefix = CacheKeys.WatchlistPrefix,
        clock = clock,
    )

    private val history = CachedFeed(
        store = store,
        serializer = PaginatedResponse.serializer(History.serializer()),
        ttl = CacheTtl.HistoryPage,
        keyPrefix = CacheKeys.HistoryPrefix,
        clock = clock,
    )

    /**
     * The watch-state index version each section key was last read under.
     *
     * A stored page was filtered against one version of the index, so a move makes it wrong however
     * fresh the clock says it is. The version cannot go in the key — that would leave a dead row
     * behind on every write — so the move forces that key's next read instead, once, and the stored
     * page is still drawn first while that read is out. Tracked per key, not as one shared flag: a
     * catalogue tab renders several sections at once, and a single flag would only force whichever one
     * happens to ask first, leaving every other section serving pages filtered against the old index
     * until its own TTL runs out.
     */
    private val seenWatchStateVersions = ConcurrentHashMap<String, Long>()

    fun sectionPage(
        key: String,
        watchStateVersion: Long,
        force: Boolean = false,
        loader: suspend () -> PaginatedResponse<Item>,
    ): Flow<Cached<PaginatedResponse<Item>>> = flow {
        // Consumed here, when the flow is collected, rather than when sectionPage() is called — a
        // caller that builds the flow without collecting it must not burn the one forced read.
        val indexMoved = seenWatchStateVersions.put(key, watchStateVersion) != watchStateVersion
        emitAll(sections.load(key = key, force = force || indexMoved, loader = loader))
    }

    fun watchlist(
        force: Boolean = false,
        loader: suspend () -> List<Item>,
    ): Flow<Cached<List<Item>>> = watchlistFeed.load(key = CacheKeys.watchlist(), force = force, loader = loader)

    fun historyFirstPage(
        force: Boolean = false,
        loader: suspend () -> PaginatedResponse<History>,
    ): Flow<Cached<PaginatedResponse<History>>> =
        history.load(key = CacheKeys.historyPage(FIRST_PAGE), force = force, loader = loader)

    private companion object {
        const val FIRST_PAGE = 1
    }
}
