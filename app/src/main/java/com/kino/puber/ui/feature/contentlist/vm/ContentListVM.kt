package com.kino.puber.ui.feature.contentlist.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.logger.log
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.playableUrl
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.domain.interactor.genre.GenreInteractor
import com.kino.puber.ui.feature.contentlist.model.ContentListAction
import com.kino.puber.ui.feature.contentlist.model.ContentListViewState
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.showall.ShowAllScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope

internal class ContentListVM(
    router: AppRouter,
    private val interactor: ContentListInteractor,
    private val mapper: VideoItemUIMapper,
    private val genreInteractor: GenreInteractor,
    private val navPrefs: NavigationPreferencesRepository,
    private val contentListRefreshCoordinator: ContentListRefreshCoordinator,
    private val contentType: String? = null,
    private val heroConfigs: List<SectionConfig> = emptyList(),
) : PuberVM<ContentListViewState>(router) {

    override val initialViewState = ContentListViewState(
        isHeroLoading = heroConfigs.isNotEmpty(),
    )
    private var focusedItemJob: Job? = null
    private var trailerGateJob: Job? = null
    private var heroLoadJob: Job? = null

    override fun onStart() {
        val isTopTabs = navPrefs.getNavigationMode() == NavigationMode.TopTabs
        updateViewState<ContentListViewState> {
            copy(
                showDetailPanel = !isTopTabs,
                showGenreChips = isTopTabs,
            )
        }
        if (isTopTabs) {
            loadGenres()
        }
        loadHero()
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.ItemFocused<*> -> onItemFocused(action.item as VideoItemUIState)
            is CommonAction.ItemSelected<*> -> onItemSelected(action.item as VideoItemUIState)
            is CommonAction.ItemPlayed<*> -> onItemPlayed(action.item as VideoItemUIState)
            is ContentListAction.ShowAll -> openShowAll(action.config)
            is ContentListAction.GenreSelected -> onGenreSelected(action.genreId)
            is ContentListAction.HeroSelected -> openDetails(action.itemId)
            is ContentListAction.TrailerPreviewFinished -> stopTrailerPreview()
            is ContentListAction.TrailerPreviewStopped -> stopTrailerPreview()
        }
    }

    private fun loadGenres() {
        launch {
            genreInteractor.getGenres(type = contentType).onSuccess { genres ->
                updateViewState<ContentListViewState> { copy(genres = genres) }
            }
        }
    }

    private fun onGenreSelected(genreId: Int?) {
        updateViewState<ContentListViewState> { copy(selectedGenreId = genreId) }
    }

    private fun onItemFocused(item: VideoItemUIState) {
        if (!stateValue.showDetailPanel) return
        focusedItemJob?.cancel()
        updateViewState<ContentListViewState> { copy(previewTrailerUrl = null) }
        focusedItemJob = launch {
            // Counts from the moment focus landed, in parallel with the request: waiting for the
            // details first would push the trailer out by however long the network took.
            val trailerGate = async { delay(TRAILER_PREVIEW_DELAY_MS) }
            trailerGateJob = trailerGate
            delay(FOCUS_DETAILS_DEBOUNCE_MS)
            updateViewState<ContentListViewState> { copy(selectedItem = VideoDetailsUIState.Loading) }
            val details = interactor.getItemDetails(item.id)
            updateViewState<ContentListViewState> { copy(selectedItem = mapper.mapDetailedItem(details)) }

            val trailerUrl = details.trailer?.playableUrl()
            if (trailerUrl == null || !navPrefs.getAutoTrailerEnabled()) {
                trailerGate.cancel()
                return@launch
            }
            trailerGate.await()
            updateViewState<ContentListViewState> { copy(previewTrailerUrl = trailerUrl) }
        }
    }

    private fun onItemSelected(item: VideoItemUIState) {
        openDetails(item.id)
    }

    private fun openDetails(itemId: Int) {
        stopTrailerPreview()
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.details(itemId),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
        )
    }

    private fun onItemPlayed(item: VideoItemUIState) {
        stopTrailerPreview()
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.player(item.id),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
        )
    }

    /**
     * The ViewModel outlives a trip to the details screen or the player. Without this the trailer
     * would be playing the instant the user came back, with none of the pause that starts it.
     *
     * This cancels only the trailer gate, not [focusedItemJob] itself: an in-flight details
     * request must be left to finish and publish `selectedItem`, or a change made while opening
     * the item (e.g. marking it watched) would refresh against a stale id.
     *
     * Every stop goes through here, including the player's own `TrailerPreviewFinished`: cancelling
     * an already-completed gate is a no-op, and routing everything through one place means a stop
     * dispatched from composition disposal cannot leave a pending gate behind to publish a trailer
     * onto a screen the user has already left.
     */
    private fun stopTrailerPreview() {
        trailerGateJob?.cancel()
        updateViewState<ContentListViewState> { copy(previewTrailerUrl = null) }
    }

    private fun openShowAll(config: SectionConfig) {
        stopTrailerPreview()
        router.navigateForResult<ContentChangeSet>(
            screen = ShowAllScreen(config),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedFromShowAll,
        )
    }

    private fun onReturnedFromShowAll(changes: ContentChangeSet?) {
        if (changes == null || changes.isEmpty) return
        refreshContent(changes)
    }

    private fun onReturnedContentChanges(changes: ContentChangeSet?) {
        if (changes == null || changes.isEmpty) return
        refreshContent(changes)
    }

    private fun refreshContent(changes: ContentChangeSet) {
        interactor.invalidateFirstPageCache()
        contentListRefreshCoordinator.requestRefresh()
        loadHero()
        val selectedItemId = stateValue.selectedItem.id
        if (selectedItemId > 0 && changes.affectsItem(selectedItemId)) {
            focusedItemJob?.cancel()
            focusedItemJob = launch {
                val details = interactor.getItemDetails(selectedItemId)
                updateViewState<ContentListViewState> {
                    copy(selectedItem = mapper.mapDetailedItem(details))
                }
            }
        }
    }

    private fun loadHero() {
        if (heroConfigs.isEmpty()) return
        heroLoadJob?.cancel()
        updateViewState<ContentListViewState> {
            copy(isHeroLoading = true)
        }
        heroLoadJob = launch {
            try {
                val items = supervisorScope {
                    heroConfigs
                        .map { config ->
                            async { loadHeroItems(config) }
                        }
                        .flatMap { it.await() }
                }
                    .distinctBy { it.id }
                    .sortedByDescending { it.ratingPercentage ?: 0 }
                    .take(HERO_ITEMS_COUNT)
                updateViewState<ContentListViewState> {
                    copy(
                        heroItems = mapper.mapHeroItems(items),
                        isHeroLoading = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log(error, "Failed to load content-list hero")
                updateViewState<ContentListViewState> {
                    copy(
                        heroItems = emptyList(),
                        isHeroLoading = false,
                    )
                }
            }
        }
    }

    private suspend fun loadHeroItems(config: SectionConfig) = try {
        interactor.loadPage(config, page = 1).items
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        log(error, "Failed to load content-list hero ${config.type}")
        emptyList()
    }

    private companion object {
        const val FOCUS_DETAILS_DEBOUNCE_MS = 150L
        const val TRAILER_PREVIEW_DELAY_MS = 2000L
        const val HERO_ITEMS_COUNT = 10
    }
}
