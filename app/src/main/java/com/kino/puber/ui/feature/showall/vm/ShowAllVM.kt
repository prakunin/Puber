package com.kino.puber.ui.feature.showall.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.vm.ContentListPagingVM
import com.kino.puber.ui.feature.showall.model.ShowAllViewState
import com.kino.puber.data.api.models.ANIME_GENRE_ID
import com.kino.puber.data.api.models.Country
import com.kino.puber.data.api.models.Genre
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.isSeriesLike
import com.kino.puber.data.preferences.CatalogueFilterRepository
import com.kino.puber.domain.interactor.country.CountryInteractor
import com.kino.puber.domain.interactor.genre.GenreInteractor
import com.kino.puber.domain.model.ContentFilter
import com.kino.puber.ui.feature.contentlist.model.AnimeFilterMode
import com.kino.puber.ui.feature.contentlist.model.TabTypeConfig
import com.kino.puber.ui.feature.showall.model.FilterCountries
import com.kino.puber.ui.feature.showall.model.FilterGenres
import com.kino.puber.ui.feature.showall.model.ShowAllAction
import com.kino.puber.ui.feature.showall.model.ShowAllFilterState
import com.kino.puber.ui.feature.showall.model.applyTo
import com.kino.puber.ui.feature.showall.model.yearOptions
import java.time.Year
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll

internal class ShowAllVM private constructor(
    paginator: Paginator.Store<Item>,
    config: SectionConfig,
    interactor: ContentListInteractor,
    private val genreInteractor: GenreInteractor,
    private val countryInteractor: CountryInteractor,
    private val savedItemInteractor: SavedItemInteractor,
    mapper: VideoItemUIMapper,
    router: AppRouter,
    errorHandler: ErrorHandler,
    private val filterRepository: CatalogueFilterRepository,
    pagingCoroutineContext: CoroutineContext,
    restored: ContentFilter,
) : ContentListPagingVM<ShowAllViewState>(
    paginator,
    restored.applyTo(config),
    interactor,
    mapper,
    router,
    errorHandler,
    pagingCoroutineContext,
    initialCacheable = !restored.isActive,
) {

    /**
     * The filter is restored here, before the super call, so the first load already pages the
     * filtered query: restoring through [changeQuery] after start would load the unfiltered page first.
     */
    constructor(
        paginator: Paginator.Store<Item>,
        config: SectionConfig,
        interactor: ContentListInteractor,
        genreInteractor: GenreInteractor,
        countryInteractor: CountryInteractor,
        savedItemInteractor: SavedItemInteractor,
        mapper: VideoItemUIMapper,
        router: AppRouter,
        errorHandler: ErrorHandler,
        filterRepository: CatalogueFilterRepository,
        pagingCoroutineContext: CoroutineContext = Dispatchers.Default,
    ) : this(
        paginator,
        config,
        interactor,
        genreInteractor,
        countryInteractor,
        savedItemInteractor,
        mapper,
        router,
        errorHandler,
        filterRepository,
        pagingCoroutineContext,
        restored = restoreFilter(config, filterRepository, interactor),
    )

    private val base = config
    private val filterable = TabTypeConfig.isFilterable(config)
    private val statusAvailable = ItemType.entries.any { it.value == config.type && it.isSeriesLike() }
    private val mutableFilterState = MutableStateFlow(
        if (filterable) {
            ShowAllFilterState(
                available = true,
                statusAvailable = statusAvailable,
                yearOptions = yearOptions(Year.now().value),
            ).withApplied(restored)
        } else {
            ShowAllFilterState.Unavailable
        },
    )

    /** What the grid header and the filter panel draw; [ShowAllFilterState.Unavailable] on a shelf grid. */
    val filterState: StateFlow<ShowAllFilterState> = mutableFilterState.asStateFlow()

    private var applied = restored
    private var rawGenres: List<Genre>? = null
    private var genresJob: Job? = null
    private var rawCountries: List<Country>? = null
    private var countriesJob: Job? = null

    private var contentChanges = ContentChangeSet.empty()
    private val pendingMutations = mutableSetOf<Job>()
    private var closing = false

    override val initialViewState = ShowAllViewState.Loading

    override val logName = "Show all"

    override fun onStart() {
        init()
        launch {
            // Same as the section rows: the settings that decide what a page contains are part of
            // the stored first page's key, so re-paging already misses the old entry. Nothing is
            // dropped from here — that would only knock over the reloads other open lists are
            // running off the same signal.
            interactor.displaySettingsChanges.collect { refreshFirstPage() }
        }
        launch {
            interactor.watchStateChanges.collect {
                // Same split as the section rows: with watched titles shown the index changes how a
                // card is drawn, not which cards belong, so the grid redraws instead of re-paging.
                // Hidden, the re-page is needed, and the stored page — filtered against the index
                // this signal has just moved — is revalidated rather than discarded.
                if (interactor.hideWatchedEnabled) {
                    refreshFirstPage()
                } else {
                    remapLoadedItems()
                }
            }
        }
    }

    override fun handleAction(action: UIAction) {
        if (closing) return
        when (action) {
            is ShowAllAction -> handleFilterAction(action)
            is CommonAction.LoadMore -> notifyLoadNextPage()
            is CommonAction.RetryClicked -> refreshFirstPage()
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
            else -> super.handleAction(action)
        }
    }

    private fun handleFilterAction(action: ShowAllAction) {
        if (!filterable) return
        when (action) {
            ShowAllAction.FilterPanelOpened -> openFilterPanel()
            ShowAllAction.FilterPanelClosed -> closeFilterPanel()
            is ShowAllAction.SortFieldChosen -> updateDraft { copy(sort = action.field) }
            is ShowAllAction.SortDirectionChosen -> updateDraft { copy(sortDirection = action.direction) }
            is ShowAllAction.GenreChosen -> updateDraft { copy(genre = action.genre) }
            ShowAllAction.GenresRetry -> loadGenres()
            is ShowAllAction.CountryChosen -> updateDraft { copy(country = action.country) }
            ShowAllAction.CountriesRetry -> loadCountries()
            is ShowAllAction.YearChosen -> updateDraft { copy(yearRange = action.range) }
            is ShowAllAction.StatusChosen -> if (statusAvailable) {
                updateDraft { copy(finished = if (action.finishedOnly) true else null) }
            }
            ShowAllAction.FilterReset -> resetFilter()
        }
    }

    private fun updateDraft(change: ContentFilter.() -> ContentFilter) {
        mutableFilterState.update { it.copy(draft = it.draft.change()) }
    }

    private fun openFilterPanel() {
        mutableFilterState.update { it.copy(panelOpen = true, draft = applied) }
        val genres = rawGenres
        if (genres != null) {
            mutableFilterState.update { it.copy(genres = FilterGenres.Loaded(visibleGenres(genres))) }
        } else {
            loadGenres()
        }
        val countries = rawCountries
        if (countries != null) {
            mutableFilterState.update { it.copy(countries = FilterCountries.Loaded(countries)) }
        } else {
            loadCountries()
        }
    }

    private fun closeFilterPanel() {
        val draft = mutableFilterState.value.draft
        val changed = draft != applied
        applied = draft
        mutableFilterState.update { it.withApplied(applied).copy(panelOpen = false) }
        if (changed) {
            filterRepository.write(base.id, applied)
            changeQuery(applied.applyTo(base), cacheable = !applied.isActive)
        }
    }

    private fun resetFilter() {
        if (!applied.isActive) return
        applied = ContentFilter()
        filterRepository.clear(base.id)
        mutableFilterState.update { it.withApplied(applied).copy(draft = applied) }
        changeQuery(base, cacheable = true)
    }

    private fun ShowAllFilterState.withApplied(filter: ContentFilter) = copy(
        isActive = filter.isActive,
        genreTitle = filter.genre?.title,
        countryTitle = filter.country?.title,
        yearRange = filter.yearRange,
        finishedOnly = filter.finished == true,
        sort = filter.sort,
        direction = filter.sortDirection,
    )

    /** The anime genre is offered only while the catalogue shows anime for this grid at all. */
    private fun visibleGenres(genres: List<Genre>): List<Genre> {
        val hideAnime = base.animeFilterMode == AnimeFilterMode.FollowPreference && !interactor.showAnimeEnabled
        return if (hideAnime) genres.filterNot { it.id == ANIME_GENRE_ID } else genres
    }

    private fun loadGenres() {
        if (genresJob?.isActive == true) return
        mutableFilterState.update { it.copy(genres = FilterGenres.Loading) }
        genresJob = launch {
            genreInteractor.getGenres(base.type ?: base.quality)
                .onSuccess { genres ->
                    rawGenres = genres
                    mutableFilterState.update { it.copy(genres = FilterGenres.Loaded(visibleGenres(genres))) }
                }
                .onFailure {
                    mutableFilterState.update { state -> state.copy(genres = FilterGenres.Failed) }
                }
        }
    }

    private fun loadCountries() {
        if (countriesJob?.isActive == true) return
        mutableFilterState.update { it.copy(countries = FilterCountries.Loading) }
        countriesJob = launch {
            countryInteractor.getCountries()
                .onSuccess { countries ->
                    rawCountries = countries
                    mutableFilterState.update { it.copy(countries = FilterCountries.Loaded(countries)) }
                }
                .onFailure {
                    mutableFilterState.update { state -> state.copy(countries = FilterCountries.Failed) }
                }
        }
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
        if (changes == null || changes.isEmpty) return
        contentChanges = contentChanges.merge(changes)
        interactor.invalidateFirstPageCache()
        refreshFirstPage()
    }

    @Suppress("UNCHECKED_CAST")
    override fun dispatchListState(state: Paginator.State) {
        val newState = when (state) {
            is Paginator.State.Loading -> ShowAllViewState.Loading
            is Paginator.State.Empty -> ShowAllViewState.Empty
            is Paginator.State.ErrorEmpty -> ShowAllViewState.Error(state.error.message)
            is Paginator.State.Data<*> -> ShowAllViewState.Content(
                items = mapItems(state.data as List<Item>),
                queryGeneration = currentQueryGeneration,
            )
            is Paginator.State.LoadingNext<*> -> ShowAllViewState.Content(
                items = mapItems(state.data as List<Item>),
                isLoadingMore = true,
                queryGeneration = currentQueryGeneration,
            )
            is Paginator.State.Error<*> -> ShowAllViewState.Content(
                items = mapItems(state.data as List<Item>),
                queryGeneration = currentQueryGeneration,
            )
            is Paginator.State.PageErrorNext<*> -> ShowAllViewState.Content(
                items = mapItems(state.data as List<Item>),
                queryGeneration = currentQueryGeneration,
            )
            is Paginator.State.Refreshing<*> -> ShowAllViewState.Content(
                items = mapItems(state.data as List<Item>),
                queryGeneration = currentQueryGeneration,
            )
            is Paginator.State.LoadingPrev<*>,
            is Paginator.State.PageErrorPrev<*> -> return
        }
        updateViewState(newState)
    }

    private fun setItemSaved(item: VideoItemUIState, saved: Boolean) {
        updateSavedItem(item.id, saved)
        launchMutation {
            savedItemInteractor.setSaved(
                itemId = item.id,
                isSeriesLike = item.isSeriesLike,
                saved = saved,
            ).onSuccess { actualSaved ->
                updateSavedItem(item.id, actualSaved)
                contentChanges = contentChanges.merge(
                    ContentChangeSet.single(
                        itemId = item.id,
                        type = if (item.isSeriesLike) {
                            ContentChangeType.Watchlist
                        } else {
                            ContentChangeType.Bookmark
                        },
                    )
                )
                interactor.invalidateFirstPageCache()
                refreshFirstPage()
            }.onFailure {
                updateSavedItem(item.id, item.isSaved)
                throw it
            }
        }
    }

    private fun updateSavedItem(itemId: Int, saved: Boolean) {
        updateViewState<ShowAllViewState.Content> {
            copy(
                items = items.map { item ->
                    if (item.id == itemId) item.copy(isSaved = saved) else item
                },
            )
        }
    }

    override fun onBackPressed() {
        if (closing) {
            router.addBackDispatcher(this)
            return
        }
        closing = true
        router.addBackDispatcher(this)
        launch {
            awaitPendingMutations()
            router.removeBackDispatcher(this@ShowAllVM)
            router.back(RESULT_CONTENT_CHANGED, contentChanges)
        }
    }

    private fun launchMutation(block: suspend CoroutineScope.() -> Unit): Job {
        lateinit var job: Job
        job = launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                pendingMutations.remove(job)
            }
        }
        pendingMutations += job
        job.start()
        return job
    }

    private suspend fun awaitPendingMutations() {
        while (true) {
            val activeJobs = pendingMutations.filter(Job::isActive)
            if (activeJobs.isEmpty()) return
            activeJobs.joinAll()
        }
    }

    private companion object {
        /**
         * The stored filter of a browse grid, or none on a shelf. A stored «Аниме» genre is left out
         * while the catalogue hides anime: paging it would walk an empty grid. The entry stays until the
         * next changed filter replaces it, «Аниме» included.
         */
        fun restoreFilter(
            config: SectionConfig,
            repository: CatalogueFilterRepository,
            interactor: ContentListInteractor,
        ): ContentFilter {
            if (!TabTypeConfig.isFilterable(config)) return ContentFilter()
            val stored = repository.read(config.id)
            val skipAnime = stored.genre?.id == ANIME_GENRE_ID &&
                config.animeFilterMode == AnimeFilterMode.FollowPreference &&
                !interactor.showAnimeEnabled
            return if (skipAnime) stored.copy(genre = null) else stored
        }
    }
}
