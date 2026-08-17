package com.kino.puber.ui.feature.favorites.vm

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.logger.log
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.cache.Cached
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.favorites.FavoritesInteractor
import com.kino.puber.ui.feature.favorites.model.FavoriteItemUIMapper
import com.kino.puber.ui.feature.favorites.model.FavoriteViewState
import kotlinx.coroutines.Job

internal class FavoriteVM(
    router: AppRouter,
    private val interactor: FavoritesInteractor,
    private val savedItemInteractor: SavedItemInteractor,
    private val favoriteItemUIMapper: FavoriteItemUIMapper,
) : PuberVM<FavoriteViewState>(router) {

    override val initialViewState = FavoriteViewState.Loading
    private var loadDataJob: Job? = null
    private var focusedItemJob: Job? = null

    override fun onStart() {
        loadData()
    }

    /**
     * Every signal that reloads the watching list comes through here, and each one replaces the
     * collection before it. Two left alive at once settle in whatever order their emissions arrive
     * in — the forced one is on `reload` while the one before it is still on `getOrPut` — so the
     * older list can land last and win.
     */
    private fun loadData(force: Boolean = false) {
        loadDataJob?.cancel()
        loadDataJob = launch {
            interactor.observeWatchlist(force = force).collect { cached ->
                when (cached) {
                    is Cached.Value -> publish(interactor.sortByRecentlyPlayed(cached.value))
                    is Cached.RefreshFailed -> log(cached.error, "Failed to refresh the watching list")
                }
            }
        }
    }

    /**
     * Rows first, side panel second — the shape [onItemFocused] already uses.
     *
     * The details go through `ItemDetailsRepository`, which waits for the last emission of its own
     * feed: the network, whenever that entry is stale or absent. A cold start is exactly that, so a
     * grid published only after the details had answered would come off disk in milliseconds and
     * then wait for a request anyway — which is the one thing serving this list from the cache was
     * for.
     */
    private suspend fun publish(items: List<Item>) {
        updateViewState(favoriteItemUIMapper.mapToState(items = items, selectedItem = null))
        val firstItem = items.firstOrNull() ?: return
        val details = interactor.getItemDetails(firstItem.id)
        updateViewState<FavoriteViewState.Content> {
            copy(selectedItem = favoriteItemUIMapper.mapSelectedItem(items, details))
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.ItemSelected<*> -> onItemSelected(action.item as VideoItemUIState)
            is CommonAction.ItemPlayed<*> -> onItemPlayed(action.item as VideoItemUIState)
            is CommonAction.ItemFocused<*> -> onItemFocused(action.item as VideoItemUIState)
            is CommonAction.ItemSavedChanged<*> -> {
                val item = action.item as VideoItemUIState
                setItemSaved(item, action.isSaved)
            }
            is CommonAction.RetryClicked -> loadData(force = true)
            else -> super.onAction(action)
        }
    }

    private fun onItemSelected(state: VideoItemUIState) {
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.details(itemId = state.id),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
        )
    }

    private fun onItemPlayed(state: VideoItemUIState) {
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.player(itemId = state.id),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
        )
    }

    private fun onReturnedContentChanges(changes: ContentChangeSet?) {
        if (changes == null || changes.isEmpty) return
        loadData(force = true)
    }

    private fun onItemFocused(selectedItem: VideoItemUIState) {
        focusedItemJob?.cancel()
        focusedItemJob = launch {
            updateViewState<FavoriteViewState.Content> {
                copy(selectedItem = VideoDetailsUIState.Loading)
            }

            val details = interactor.getItemDetails(selectedItem.id)

            updateViewState<FavoriteViewState.Content> {
                copy(selectedItem = favoriteItemUIMapper.mapDetailedItem(details))
            }
        }
    }

    private fun setItemSaved(item: VideoItemUIState, saved: Boolean) {
        launch {
            savedItemInteractor.setSaved(
                itemId = item.id,
                isSeriesLike = item.isSeriesLike,
                saved = saved,
            ).getOrThrow()
            loadData(force = true)
        }
    }
}
