package com.kino.puber.domain.interactor.contentlist

import com.kino.puber.core.collections.TypedTtlCache
import com.kino.puber.core.collections.TypedTtlCacheImpl
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.isAnime
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.ui.feature.contentlist.model.AnimeFilterMode
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

internal class ContentListInteractor(
    private val api: KinoPubApiClient,
    private val navigationPreferencesRepository: NavigationPreferencesRepository,
    private val watchStateRepository: WatchStateRepository,
) {

    private val detailedItemsCache: TypedTtlCache<String, Item> = TypedTtlCacheImpl()
    private val freshPagers = ConcurrentHashMap<String, FreshSectionPager>()

    /**
     * Emits whenever a setting that changes what a catalogue card shows flips — hiding watched
     * titles, or the watched marks themselves. Open lists re-page instead of showing the previous
     * choice until they happen to reload.
     */
    val displaySettingsChanges: Flow<Unit> = navigationPreferencesRepository.displaySettingsChanges

    /**
     * Emits when the local watch-state index changes — a sync landing or the user marking something
     * watched. Pages already fetched were filtered against the old index, so they have to be redone.
     */
    val watchStateChanges: Flow<Long> = watchStateRepository.settledChanges

    /**
     * Whether the index decides list *membership* rather than only how a card is drawn.
     *
     * This is what separates a watch-state change a list can redraw from one it has to re-page for:
     * see [isVisible], where the index is consulted only when this is on.
     */
    val hideWatchedEnabled: Boolean
        get() = navigationPreferencesRepository.contentPreferences.value.hideWatched

    suspend fun loadPage(config: SectionConfig, page: Int): PaginatedResponse<Item> {
        // Kept ahead of the fresh-section branch below so the version bookkeeping happens on any
        // page load, whichever kind of section asked for it.
        dropFirstPageCacheIfWatchStateMoved()
        if (config.shortcutTypes.isNotEmpty()) {
            return freshPagers
                .computeIfAbsent(config.id) { FreshSectionPager(api, config) }
                .loadPage(page)
        }
        val preferences = navigationPreferencesRepository.contentPreferences.value
        val showAnime = preferences.showAnime
        val hideWatched = preferences.hideWatched
        if (page == 1) {
            val cacheKey = listOf(
                KinoPubConfig.CURRENT_API_DOMAIN,
                config.id,
                config.shortcut.orEmpty(),
                config.type,
                config.shortcutTypes.joinToString(separator = ",") { it.value },
                config.sort,
                config.quality,
                config.genre.orEmpty(),
                config.requiredGenreId,
                config.animeFilterMode,
                showAnime,
                hideWatched,
            ).joinToString(separator = "_")
            return firstPageCache.getOrPut(cacheKey) {
                fetchFilteredPage(config, page, showAnime, hideWatched)
            }
        }
        return fetchFilteredPage(config, page, showAnime, hideWatched)
    }

    private suspend fun fetchFilteredPage(
        config: SectionConfig,
        requestedPage: Int,
        showAnime: Boolean,
        hideWatched: Boolean,
    ): PaginatedResponse<Item> {
        val filterMode = config.animeFilterMode
        val animeFilterIsNoop = filterMode == AnimeFilterMode.None ||
            filterMode == AnimeFilterMode.FollowPreference && showAnime
        if (animeFilterIsNoop && !hideWatched) {
            return fetchPage(config, requestedPage)
        }

        var currentRequestedPage = requestedPage
        var response = fetchPage(config, currentRequestedPage)
        val targetSize = response.pagination.perpage.coerceAtLeast(response.items.size)
        val visibleItems = linkedMapOf<Int, Item>()
        // The anime filter has always walked as far as it needed to refill a page. Hiding watched
        // items can knock out whole pages at a time, so that path gets a ceiling instead.
        val pageBudget = if (hideWatched) MAX_PAGES_PER_STEP else Int.MAX_VALUE
        var fetchedPages = 1
        while (true) {
            check(response.pagination.current == currentRequestedPage) {
                "Content pagination current ${response.pagination.current} " +
                    "did not match requested page $currentRequestedPage"
            }
            response.items
                .filter { item -> isVisible(item, filterMode, hideWatched) }
                .forEach { item -> visibleItems.putIfAbsent(item.id, item) }

            val pageIsFull = visibleItems.size >= targetSize || targetSize == 0
            val serverIsExhausted = response.pagination.current >= response.pagination.total
            // A heavily watched section could otherwise walk the whole catalogue in one scroll
            // step. Give back a short — possibly empty — page instead; the caller knows from the
            // pagination whether anything is left and asks for the next one.
            val budgetIsSpent = fetchedPages >= pageBudget
            if (pageIsFull || serverIsExhausted || budgetIsSpent) {
                return response.copy(items = visibleItems.values.toList())
            }
            currentRequestedPage = response.pagination.current + 1
            response = fetchPage(config, currentRequestedPage)
            fetchedPages++
        }
    }

    private fun isVisible(item: Item, filterMode: AnimeFilterMode, hideWatched: Boolean): Boolean {
        val passesAnimeFilter = when (filterMode) {
            AnimeFilterMode.None -> true
            AnimeFilterMode.FollowPreference,
            AnimeFilterMode.Exclude -> item.isAnime().not()
            AnimeFilterMode.Only -> item.isAnime()
        }
        return passesAnimeFilter && (!hideWatched || !watchStateRepository.isFullyWatched(item))
    }

    private suspend fun fetchPage(config: SectionConfig, page: Int): PaginatedResponse<Item> {
        val result = when {
            config.shortcut != null ->
                api.getItemsByShortcut(config.shortcut, config.type, page, config.genre)
            else ->
                api.getItems(config.type, config.sort, page, config.quality, config.genre)
        }
        return result.getOrThrow()
    }

    suspend fun getItemDetails(id: Int): Item {
        return detailedItemsCache.getOrPut(itemDetailsCacheKey(id)) {
            val response = api.getItemDetails(id).getOrThrow()
            checkNotNull(response.item) { "Details response for item $id carried no item" }
        }
    }

    fun invalidateFirstPageCache() {
        firstPageCache.clear()
        freshPagers.clear()
    }

    /**
     * Pages are cached with the watch state they were filtered against baked in. Putting the index
     * version in the cache key would leave an entry behind on every write, so the cache is dropped
     * once when the version moves instead — which also covers a change that happened while every
     * list was closed and nothing was listening.
     */
    private fun dropFirstPageCacheIfWatchStateMoved() {
        val version = watchStateRepository.version.value
        if (cachedWatchStateVersion.getAndSet(version) != version) {
            firstPageCache.clear()
        }
    }

    fun invalidateItemDetails(id: Int) {
        detailedItemsCache.remove(itemDetailsCacheKey(id))
    }

    private fun itemDetailsCacheKey(id: Int): String {
        return "${KinoPubConfig.CURRENT_API_DOMAIN}_$id"
    }

    companion object {
        private val cachedWatchStateVersion = java.util.concurrent.atomic.AtomicLong(0L)

        private const val MAX_PAGES_PER_STEP = 5

        private val firstPageCache = TypedTtlCacheImpl<String, PaginatedResponse<Item>>(
            defaultTtl = 3.minutes,
        )
    }
}
