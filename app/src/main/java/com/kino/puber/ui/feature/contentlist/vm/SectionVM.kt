package com.kino.puber.ui.feature.contentlist.vm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.model.SectionState
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

internal class SectionVM(
    paginator: Paginator.Store<Item>,
    config: SectionConfig,
    interactor: ContentListInteractor,
    private val savedItemInteractor: SavedItemInteractor,
    mapper: VideoItemUIMapper,
    router: AppRouter,
    errorHandler: ErrorHandler,
    private val contentListRefreshCoordinator: ContentListRefreshCoordinator,
    pagingCoroutineContext: CoroutineContext = Dispatchers.Default,
) : ContentListPagingVM<SectionState>(
    paginator,
    config,
    interactor,
    mapper,
    router,
    errorHandler,
    pagingCoroutineContext,
) {

    // Collect state without back dispatcher — for use outside LazyColumn items
    @Composable
    fun collectState(): State<SectionState> {
        ensureStarted()
        return viewState.collectAsStateWithLifecycle(initialViewState)
    }

    override val initialViewState = SectionState.Loading

    override val logName = "Section"

    override fun onStart() {
        val refreshRequests = contentListRefreshCoordinator.refreshRequests()
        init()
        launch {
            refreshRequests.collect { refresh ->
                when (refresh) {
                    SectionRefresh.All -> refreshFirstPage()
                    // One title changed. Every section on the tab hears this, but only the ones
                    // actually showing it have a badge to redraw.
                    is SectionRefresh.ForItem -> if (isShowingItem(refresh.itemId)) refreshFirstPage()
                }
            }
        }
        launch {
            // The stored first page is keyed by the settings that decide what a page contains, so
            // the reload below misses the old entry rather than finding it. Nothing is dropped from
            // here — every open section wakes on this same signal, and the cache is shared, so each
            // one's clear would land on the reloads the others have already started.
            interactor.displaySettingsChanges.collect { refreshFirstPage() }
        }
        launch {
            interactor.watchStateChanges.collect {
                // Every section on the screen wakes on this same signal, so what it costs is paid
                // once per open row. With watched titles shown the index only changes how a card is
                // drawn, and the rows already fetched are still the right ones.
                //
                // Hidden, membership changes and the pages have to be re-fetched — the stored page
                // was filtered against the index this signal has just moved, and the cache answers
                // that move by revalidating rather than by discarding what it can still draw.
                if (interactor.hideWatchedEnabled) {
                    refreshFirstPage()
                } else {
                    remapLoadedItems()
                }
            }
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.LoadMore -> notifyLoadNextPage()
            is CommonAction.RetryClicked -> refreshFirstPage()
            is CommonAction.ItemSavedChanged<*> -> {
                val item = action.item as VideoItemUIState
                setItemSaved(item, action.isSaved)
            }
            else -> super.onAction(action)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dispatchListState(state: Paginator.State) {
        val newState = when (state) {
            is Paginator.State.Loading -> SectionState.Loading
            is Paginator.State.Empty -> SectionState.Empty
            is Paginator.State.ErrorEmpty -> SectionState.Error(state.error.message)
            is Paginator.State.Data<*> -> SectionState.Content(
                items = mapItems(state.data as List<Item>),
            )
            is Paginator.State.LoadingNext<*> -> SectionState.Content(
                items = mapItems(state.data as List<Item>),
                isLoadingMore = true,
            )
            is Paginator.State.Error<*> -> SectionState.Content(
                items = mapItems(state.data as List<Item>),
            )
            is Paginator.State.PageErrorNext<*> -> SectionState.Content(
                items = mapItems(state.data as List<Item>),
            )
            is Paginator.State.Refreshing<*> -> SectionState.Content(
                items = mapItems(state.data as List<Item>),
            )
            is Paginator.State.LoadingPrev<*>,
            is Paginator.State.PageErrorPrev<*> -> return
        }
        updateViewState(newState)
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
                interactor.invalidateFirstPageCache()
                contentListRefreshCoordinator.requestRefreshForItem(item.id)
            }.onFailure {
                updateSavedItem(item.id, item.isSaved)
                throw it
            }
        }
    }

    private fun updateSavedItem(itemId: Int, saved: Boolean) {
        updateViewState<SectionState.Content> {
            copy(
                items = items.map { item ->
                    if (item.id == itemId) item.copy(isSaved = saved) else item
                },
            )
        }
    }
}
