package com.kino.puber.ui.feature.home.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.logger.log
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.content.ContentChangeSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import com.kino.puber.R
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.KCollection
import com.kino.puber.data.cache.Cached
import com.kino.puber.domain.interactor.api.ApiDomainAutoResolveResult
import com.kino.puber.domain.interactor.api.ApiDomainDetectionResult
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.api.ApiDomainUpdateResult
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.home.HomeInteractor
import com.kino.puber.domain.interactor.watchstate.CardDisplayChanges
import com.kino.puber.ui.feature.collections.detail.CollectionDetailScreen
import com.kino.puber.ui.feature.home.model.HomeAction
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeUIMapper
import com.kino.puber.ui.feature.home.model.HomeViewState
import kotlinx.coroutines.flow.Flow

internal class HomeVM(
    router: AppRouter,
    private val interactor: HomeInteractor,
    private val mapper: HomeUIMapper,
    private val videoItemMapper: VideoItemUIMapper,
    private val apiDomainInteractor: ApiDomainInteractor,
    private val savedItemInteractor: SavedItemInteractor,
    private val cardDisplayChanges: CardDisplayChanges,
    private val resources: ResourceProvider,
    override val errorHandler: ErrorHandler,
) : PuberVM<HomeViewState>(router) {

    companion object {
        private const val HERO_ITEMS_COUNT = 10
        private const val TOTAL_SECTIONS = 8
    }

    override val initialViewState: HomeViewState = HomeViewState.Loading()
    private var loadHomeJob: Job? = null

    /**
     * What each section last returned, keyed by the row it draws.
     *
     * A watched mark landing changes how a card is *drawn*, not what the server would return, so it
     * is re-mapped from this instead of costing another round of section requests. Sections also
     * arrive independently now, so this is what a partial screen is published from. An ordinary
     * refresh never clears this: it overwrites each row in place as its own value arrives, rather
     * than dropping the whole screen to whatever the first section to answer happens to be. Only a
     * domain switch clears it — see [clearRowsFromPreviousCatalogue].
     */
    private val loadedSections = linkedMapOf<HomeSectionType, List<Item>>()
    private var loadedCollections: List<KCollection>? = null
    private var lastWatchedAt: Map<Int, Long> = emptyMap()

    /**
     * The content-cache generation the rows above were built under.
     *
     * A domain switch performed anywhere else in the app — the device settings screen is the one that
     * matters, since it neither re-roots nor knows this screen exists — wipes the cache and leaves
     * these rows describing a catalogue the app has stopped talking to. The auto-resolve on the
     * resume that follows reports `changed = false`, because settings already applied the domain, so
     * that signal cannot see it. The generation can.
     */
    private var loadedCacheGeneration = interactor.cacheGeneration

    override fun dispatchError(error: ErrorEntity) {
        if (stateValue is HomeViewState.Content) {
            showMessage(error.message)
        } else {
            updateViewState(HomeViewState.Error(error.message, apiDomainDialog = currentDialogState()))
        }
    }

    override fun onStart() {
        loadHome()
        launch {
            cardDisplayChanges.changes.collect { refreshHomePresentation() }
        }
    }

    private suspend fun refreshHomePresentation() {
        if (stateValue !is HomeViewState.Content) return
        lastWatchedAt = interactor.lastWatchedAt()
        publishSections()
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.ItemSelected<*> -> {
                val item = action.item as VideoItemUIState
                openDetails(item.id)
            }
            is CommonAction.ItemPlayed<*> -> {
                val item = action.item as VideoItemUIState
                openPlayer(item.id)
            }
            is CommonAction.ItemSavedChanged<*> -> {
                val item = action.item as VideoItemUIState
                setItemSaved(item, action.isSaved)
            }
            is CommonAction.RetryClicked -> loadHome()
            is CommonAction.OnResume -> silentRefresh()
            HomeAction.OpenApiDomainDialog -> openApiDomainDialog()
            HomeAction.CloseApiDomainDialog -> closeApiDomainDialog()
            is HomeAction.SaveApiDomain -> saveApiDomain(action.domain)
            HomeAction.DetectApiDomain -> detectApiDomain()
            HomeAction.ResetApiDomain -> resetApiDomain()
            else -> super.onAction(action)
        }
    }

    fun onHeroClick(itemId: Int) {
        openDetails(itemId)
    }

    fun onCollectionClick(id: Int, title: String) {
        router.navigateTo(CollectionDetailScreen(id, title))
    }

    private fun silentRefresh() {
        if (stateValue !is HomeViewState.Content) return
        loadHome(showDomainSearch = false)
    }

    private fun openDetails(itemId: Int) {
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.details(itemId),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
        )
    }

    private fun openPlayer(itemId: Int) {
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.player(itemId),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
        )
    }

    private fun onReturnedContentChanges(changes: ContentChangeSet?) {
        if (changes == null || changes.isEmpty || stateValue !is HomeViewState.Content) return
        silentRefresh()
    }

    private fun loadHome(showDomainSearch: Boolean = stateValue !is HomeViewState.Content) {
        loadHomeJob?.cancel()
        loadHomeJob = launch {
            clearRowsIfContentCacheWasWiped()
            val preserveContentOnResolveFailure = !showDomainSearch && stateValue is HomeViewState.Content
            if (showDomainSearch) {
                updateViewState(
                    HomeViewState.Loading(
                        message = resources.getString(R.string.api_domain_auto_searching),
                        apiDomainDialog = currentDialogState(),
                    )
                )
            }

            when (val result = apiDomainInteractor.autoResolveWorkingDomain()) {
                ApiDomainAutoResolveResult.NotFound -> {
                    if (preserveContentOnResolveFailure) {
                        showMessage(resources.getString(R.string.api_domain_auto_failed))
                        return@launch
                    }
                    updateViewState(
                        HomeViewState.Error(
                            message = resources.getString(R.string.api_domain_auto_failed),
                            apiDomainDialog = currentDialogState(),
                        )
                    )
                    return@launch
                }

                is ApiDomainAutoResolveResult.Success -> if (result.changed) {
                    clearRowsFromPreviousCatalogue()
                    showMessage(resources.getString(R.string.api_domain_auto_switched, result.state.domain))
                }
            }

            loadContentSections(forceWatching = !showDomainSearch)
        }
    }

    private suspend fun loadContentSections(forceWatching: Boolean) = supervisorScope {
        // Scoped to this call rather than kept on the instance: `loadHome` cancels the previous
        // job without joining it, so a stale collector's `finally` block can still be in flight
        // when this run starts. Reading a shared counter there would let up to TOTAL_SECTIONS
        // stale increments land on this run's count; a fresh holder per run can't be touched by
        // a previous one no matter how cancellation is interleaved.
        val run = LoadRun()
        lastWatchedAt = interactor.lastWatchedAt()

        val sections = listOf(
            HomeSectionType.ContinueWatching to interactor.observeWatchingItems(force = forceWatching),
            HomeSectionType.Hot to interactor.observeHotItems(),
            HomeSectionType.Fresh to interactor.observeFreshItems(),
            HomeSectionType.PopularMovies to interactor.observePopularMovies(),
            HomeSectionType.PopularSeries to interactor.observePopularSeries(),
            HomeSectionType.WatchLater to interactor.observeWatchLaterItems(),
            HomeSectionType.Bookmarks to interactor.observeBookmarkItems(),
        )
        sections.forEach { (type, flow) ->
            launch { collectSection(run, type, flow) }
        }
        launch { collectCollections(run) }
    }

    private suspend fun collectSection(run: LoadRun, type: HomeSectionType, flow: Flow<Cached<List<Item>>>) {
        try {
            flow.collect { cached ->
                when (cached) {
                    is Cached.Value -> {
                        loadedSections[type] = cached.value
                        run.publishedAnything = true
                        publishSections()
                    }
                    is Cached.RefreshFailed -> log(cached.error, "Failed to refresh $type")
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log(error, "Failed to load $type")
        } finally {
            onSectionFinished(run)
        }
    }

    private suspend fun collectCollections(run: LoadRun) {
        try {
            interactor.observeCollections().collect { cached ->
                when (cached) {
                    is Cached.Value -> {
                        loadedCollections = cached.value
                        run.publishedAnything = true
                        publishSections()
                    }
                    is Cached.RefreshFailed -> log(cached.error, "Failed to refresh collections")
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log(error, "Failed to load collections")
        } finally {
            onSectionFinished(run)
        }
    }

    /**
     * Shows the error screen only once every section in this run has given up without this run
     * having published anything.
     *
     * A single failing row is not worth replacing a working screen over, but a screen that would
     * stay empty forever has to say so rather than spin. "Published anything" is asked of this
     * run, not of [loadedSections]: a section carried over from an earlier successful load must
     * not silently excuse a run where every section just failed.
     */
    private fun onSectionFinished(run: LoadRun) {
        run.finishedSections += 1
        if (run.finishedSections < TOTAL_SECTIONS) return
        if (run.publishedAnything) return
        if (stateValue is HomeViewState.Content) return
        updateViewState(
            HomeViewState.Error(
                message = resources.getString(R.string.error_generic),
                apiDomainDialog = currentDialogState(),
            )
        )
    }

    /**
     * Forgets every row, because the catalogue they came from is no longer the one the app talks to.
     *
     * The counterpart to the preservation documented on [loadedSections], and deliberately narrower:
     * carrying rows across a load is what stops a resume from collapsing the screen and moving focus,
     * but a domain switch genuinely replaced the catalogue. Kept here, a row whose new request is
     * still in flight would sit next to the new domain's rows, and a row whose new request fails
     * would show the old domain's content for as long as the screen lives.
     *
     * Three callers, because no single one covers every route, and the function is idempotent so
     * overlap costs nothing:
     * - [clearRowsIfContentCacheWasWiped], at the top of [loadHome] — the durable one, catching a
     *   wipe performed by anybody, including screens that have never heard of this one.
     * - the auto-resolve reporting `changed`, which wiped the caches *after* that check already ran.
     * - the three dialog paths below, directly, so a switch made here shows the moment it is applied
     *   rather than one auto-resolve later.
     *
     * The screen goes back to loading with the rows. Emptying the map alone would not be enough: nothing
     * republishes until a section answers, so a switch where none of them ever does would leave the
     * last frame — drawn entirely from the old domain — up for as long as the screen lives. Loading
     * is also what lets [onSectionFinished] report a switch that failed outright.
     */
    private fun clearRowsFromPreviousCatalogue() {
        loadedSections.clear()
        loadedCollections = null
        loadedCacheGeneration = interactor.cacheGeneration
        if (stateValue is HomeViewState.Content) {
            updateViewState(HomeViewState.Loading(apiDomainDialog = currentDialogState()))
        }
    }

    /**
     * Drops the rows when the content cache has been wiped since they were built.
     *
     * The wipe is the one fact every domain switch has in common, and the only one that reaches this
     * screen from routes that have never heard of it: the device settings screen switches the domain
     * with no re-root and no callback, and `autoResolveWorkingDomain` reports `changed = false` on the
     * resume that follows because settings already applied it. Asking the store what it did, rather
     * than waiting for whoever did it to say so, is what closes that.
     */
    private fun clearRowsIfContentCacheWasWiped() {
        if (interactor.cacheGeneration != loadedCacheGeneration) {
            clearRowsFromPreviousCatalogue()
        }
    }

    /** Maps what the sections returned into cards, against whatever the index and settings say now. */
    private fun publishSections() {
        val mapped = listOfNotNull(
            *loadedSections
                .map { (type, items) ->
                    mapper.mapItemSection(
                        interactor.prepareHomeItems(
                            items = items,
                            lastWatchedAt = lastWatchedAt,
                            sortByLastWatched = type == HomeSectionType.WatchLater ||
                                type == HomeSectionType.Bookmarks,
                        ),
                        type,
                    )
                }
                .toTypedArray(),
            loadedCollections?.let { mapper.mapCollectionSection(it) },
        ).sortedBy { it.type.ordinal }

        val hotItems = interactor.prepareHomeItems(
            items = loadedSections[HomeSectionType.Hot].orEmpty(),
            lastWatchedAt = lastWatchedAt,
            sortByLastWatched = false,
        )
        updateViewState(
            HomeViewState.Content(
                heroItems = videoItemMapper.mapHeroItems(hotItems.take(HERO_ITEMS_COUNT)),
                sections = mapped,
                apiDomainDialog = currentDialogState(),
            )
        )
    }

    /** Per-[loadContentSections] call counters, so a cancelled run can't skew the next one's. */
    private class LoadRun {
        var finishedSections = 0
        var publishedAnything = false
    }

    private fun openApiDomainDialog() {
        updateApiDomainDialog(apiDomainInteractor.getState().toDialogState())
    }

    private fun closeApiDomainDialog() {
        updateApiDomainDialog(null)
    }

    private fun updateApiDomainDialog(dialogState: ApiDomainDialogState?) {
        updateViewState(
            when (val state = stateValue) {
                is HomeViewState.Content -> state.copy(apiDomainDialog = dialogState)
                is HomeViewState.Error -> state.copy(apiDomainDialog = dialogState)
                is HomeViewState.Loading -> state.copy(apiDomainDialog = dialogState)
            }
        )
    }

    private fun saveApiDomain(domain: String) {
        launch {
            when (val result = apiDomainInteractor.saveCustomDomain(domain)) {
                ApiDomainUpdateResult.Empty -> showMessage(resources.getString(R.string.api_domain_empty))
                ApiDomainUpdateResult.Invalid -> showMessage(resources.getString(R.string.api_domain_invalid))
                is ApiDomainUpdateResult.Success -> {
                    clearRowsFromPreviousCatalogue()
                    closeApiDomainDialog()
                    showMessage(resources.getString(R.string.api_domain_saved, result.state.domain))
                    loadHome()
                }
            }
        }
    }

    private fun detectApiDomain() {
        val dialogState = currentDialogState() ?: return
        if (dialogState.isDetecting) return
        updateApiDomainDialog(dialogState.copy(isDetecting = true))

        launch {
            when (val result = apiDomainInteractor.detectAndSaveWorkingDomain()) {
                ApiDomainDetectionResult.NotFound -> {
                    updateApiDomainDialog(dialogState.copy(isDetecting = false))
                    showMessage(resources.getString(R.string.api_domain_detect_failed))
                }

                is ApiDomainDetectionResult.Success -> {
                    clearRowsFromPreviousCatalogue()
                    closeApiDomainDialog()
                    showMessage(resources.getString(R.string.api_domain_detected, result.state.domain))
                    loadHome()
                }
            }
        }
    }

    private fun resetApiDomain() {
        launch {
            apiDomainInteractor.resetToDefault()
            clearRowsFromPreviousCatalogue()
            closeApiDomainDialog()
            showMessage(resources.getString(R.string.api_domain_reset_done))
            loadHome()
        }
    }

    private fun currentDialogState(): ApiDomainDialogState? {
        return stateValue.apiDomainDialog
    }

    private fun setItemSaved(item: VideoItemUIState, saved: Boolean) {
        updateSavedItem(item.id, saved)
        launch {
            savedItemInteractor.setSaved(
                itemId = item.id,
                isSeriesLike = item.isSeriesLike,
                saved = saved,
            ).onSuccess { actualSaved ->
                updateSavedItem(item.id, actualSaved)
                showMessage(savedMessage(item, actualSaved))
            }.onFailure {
                updateSavedItem(item.id, item.isSaved)
                throw it
            }
        }
    }

    private fun updateSavedItem(itemId: Int, saved: Boolean) {
        updateViewState<HomeViewState.Content> {
            copy(
                sections = sections.map { section ->
                    if (section.type == HomeSectionType.Collections) {
                        section
                    } else {
                        section.copy(
                            items = section.items.map { item ->
                                if (item.id == itemId) item.copy(isSaved = saved) else item
                            },
                        )
                    }
                },
            )
        }
    }

    private fun savedMessage(item: VideoItemUIState, saved: Boolean): String {
        val messageRes = when {
            item.isSeriesLike && saved -> R.string.video_details_watchlist_added
            item.isSeriesLike -> R.string.video_details_watchlist_removed
            saved -> R.string.video_details_watch_later_added
            else -> R.string.video_details_watch_later_removed
        }
        return resources.getString(messageRes)
    }

    private fun ApiDomainState.toDialogState(): ApiDomainDialogState {
        return ApiDomainDialogState(
            currentDomain = domain,
            customDomain = customDomain,
        )
    }
}
