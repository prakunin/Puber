package com.kino.puber.domain.interactor.contentlist

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.isAnime
import com.kino.puber.data.cache.CacheKeys
import com.kino.puber.data.cache.Cached
import com.kino.puber.data.cache.ContentPageCache
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.ui.feature.contentlist.model.AnimeFilterMode
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap

internal class ContentListInteractor(
    private val api: KinoPubApiClient,
    private val navigationPreferencesRepository: NavigationPreferencesRepository,
    private val watchStateRepository: WatchStateRepository,
    private val itemDetailsRepository: ItemDetailsRepository,
    private val contentPageCache: ContentPageCache,
) {

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

    /**
     * The first page of a section: whatever is stored is emitted at once, and the network is
     * consulted behind it when the entry is stale, when the watch-state index has moved, or when
     * [force] says the caller knows the server's answer has changed.
     */
    fun observeFirstPage(
        config: SectionConfig,
        force: Boolean = false,
    ): Flow<Cached<PaginatedResponse<Item>>> {
        // A fresh section's page one is also what primes its FreshSectionPager: the pager builds
        // page two out of the state page one left behind, and refuses a cursor it never issued.
        // Served from the store, the section would draw its first page and then fail on its second.
        if (config.shortcutTypes.isNotEmpty()) {
            return flow {
                emit(Cached.Value(loadPage(config, page = FIRST_PAGE), isStale = false))
            }
        }
        val preferences = navigationPreferencesRepository.contentPreferences.value
        return contentPageCache.sectionPage(
            key = CacheKeys.section(cacheKey(config, preferences.showAnime, preferences.hideWatched)),
            // Only a page filtered against the index is baked against a version of it — see
            // [hideWatchedEnabled] and [fetchFilteredPage], which does not consult the index at all
            // otherwise, and the watched marks an unfiltered page draws come from the index at
            // mapping time rather than from the stored payload. `hideWatched` is part of the key, so
            // such a page is index-independent by construction, and forcing a read for it would cost
            // a request per section on every catalogue tab entered after any playback.
            watchStateVersion = if (preferences.hideWatched) {
                watchStateRepository.version.value
            } else {
                ContentPageCache.INDEX_INDEPENDENT
            },
            force = force,
        ) {
            loadPage(config, page = FIRST_PAGE)
        }
    }

    suspend fun loadPage(config: SectionConfig, page: Int): PaginatedResponse<Item> {
        if (config.shortcutTypes.isNotEmpty()) {
            return freshPagers
                .computeIfAbsent(config.id) { FreshSectionPager(api, config) }
                .loadPage(page)
        }
        val preferences = navigationPreferencesRepository.contentPreferences.value
        return fetchFilteredPage(config, page, preferences.showAnime, preferences.hideWatched)
    }

    /**
     * Everything that decides what a page contains, so flipping one of them cannot serve another's
     * cache.
     *
     * `KinoPubConfig.CURRENT_API_DOMAIN` is deliberately absent, though the in-memory cache this
     * replaced had it: a domain switch goes through `ApiDomainInteractor.clearDomainSensitiveCaches`,
     * which empties the whole table and moves the store generation every cached feed already watches.
     * Kept in the key, the domain would only leave dead rows behind.
     */
    private fun cacheKey(
        config: SectionConfig,
        showAnime: Boolean,
        hideWatched: Boolean,
    ): String = listOf(
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
        return itemDetailsRepository.getItemDetailsCacheOnly(id)
    }

    /** Drops the pagination cursors the fresh-section pagers hold. */
    fun invalidateFirstPageCache() {
        freshPagers.clear()
    }

    private companion object {
        const val FIRST_PAGE = 1

        const val MAX_PAGES_PER_STEP = 5
    }
}
