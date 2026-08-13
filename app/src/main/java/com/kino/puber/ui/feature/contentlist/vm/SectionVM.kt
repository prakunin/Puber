package com.kino.puber.ui.feature.contentlist.vm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.logger.log
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.paginator.PagingVM
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
import kotlinx.coroutines.delay
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

internal class SectionVM(
    paginator: Paginator.Store<Item>,
    private val config: SectionConfig,
    private val interactor: ContentListInteractor,
    private val savedItemInteractor: SavedItemInteractor,
    private val mapper: VideoItemUIMapper,
    router: AppRouter,
    errorHandler: ErrorHandler,
    private val contentListRefreshCoordinator: ContentListRefreshCoordinator,
    pagingCoroutineContext: CoroutineContext = Dispatchers.Default,
) : PagingVM<Item, SectionState>(paginator, router, errorHandler, pagingCoroutineContext) {

    // Collect state without back dispatcher — for use outside LazyColumn items
    @Composable
    fun collectState(): State<SectionState> {
        ensureStarted()
        return viewState.collectAsStateWithLifecycle(initialViewState)
    }

    private var currentPage = 0
    private var emptyPageChain = 0
    private var publishedAnyItems = false
    private var cachedInput: List<Item>? = null
    private var cachedOutput: List<VideoItemUIState> = emptyList()

    override val initialViewState = SectionState.Loading

    override fun onStart() {
        val refreshRequests = contentListRefreshCoordinator.refreshRequests()
        init()
        launch {
            refreshRequests.collect {
                refreshFirstPage()
            }
        }
        launch {
            interactor.displaySettingsChanges.collect {
                interactor.invalidateFirstPageCache()
                refreshFirstPage()
            }
        }
        launch {
            interactor.watchStateChanges.collect {
                interactor.invalidateFirstPageCache()
                refreshFirstPage()
            }
        }
    }

    fun refreshFirstPage() {
        resetPaging()
    }

    override fun onLoadFirstPage() {
        currentPage = 0
        emptyPageChain = 0
        publishedAnyItems = false
        pagingLaunch(errorHandlerGeneral) { loadPage(page = 1, isFirstPage = true) }
    }

    override fun onLoadNextPage(key: Item?) {
        pagingLaunch(errorHandlerPaging) { loadPage(page = currentPage + 1, isFirstPage = false) }
    }

    /**
     * Hiding watched titles can empty a whole server page, and a heavily watched section can empty
     * several in a row. The paginator is told to keep walking in that case rather than reading the
     * blank page as the end of the list — but only so far, or one load could walk the catalogue in
     * a single burst. What is left over is picked up by [resumeWalkAfterPause].
     */
    private suspend fun loadPage(page: Int, isFirstPage: Boolean) {
        val response = interactor.loadPage(config, page)
        currentPage = response.pagination.current
        val serverHasMore = currentPage < response.pagination.total
        emptyPageChain = if (response.items.isEmpty()) emptyPageChain + 1 else 0
        val keepWalking = serverHasMore && emptyPageChain in 1..MAX_EMPTY_PAGE_CHAIN
        val budgetIsSpent = response.items.isEmpty() && serverHasMore && !keepWalking
        isFullDataNext = !serverHasMore
        if (response.items.isNotEmpty()) publishedAnyItems = true
        // A walk that gave up without ever finding anything belongs on the empty state, not on a
        // content row holding nothing.
        if (isFirstPage || (!publishedAnyItems && !keepWalking)) {
            replace(response.items, hasMorePages = keepWalking)
        } else {
            setNextPage(response.items, hasMorePages = keepWalking)
        }
        if (budgetIsSpent) resumeWalkAfterPause()
    }

    /**
     * Picks the walk up again where the page budget ran out.
     *
     * That budget bounds one burst of requests, not the section. A run of watched titles longer
     * than the budget would otherwise leave the section reading as empty for as long as the screen
     * is open, with nothing left that would ask for the pages behind it: an empty row is hidden, so
     * there is no retry for the user to press and no way into "show all" either.
     *
     * The pause is what keeps this from being the same burst by another name — it hands the screen
     * back its dispatcher between rounds and gives the cancellation a place to land. A restart or a
     * closed screen drops the walk with the rest of the paging work.
     */
    private fun resumeWalkAfterPause() {
        val resumeFrom = currentPage + 1
        log(
            "Section ${config.id}: nothing to show in $emptyPageChain pages, " +
                "resuming from page $resumeFrom"
        )
        pagingLaunch(errorHandlerPaging) {
            delay(WALK_RESUME_PAUSE)
            emptyPageChain = 0
            loadPage(page = resumeFrom, isFirstPage = false)
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.LoadMore -> notifyLoadNextPage()
            is CommonAction.RetryClicked -> resetPaging()
            is CommonAction.ItemSavedChanged<*> -> {
                val item = action.item as VideoItemUIState
                setItemSaved(item, action.isSaved)
            }
        }
    }

    private fun mapItems(items: List<Item>): List<VideoItemUIState> {
        if (items === cachedInput) return cachedOutput
        return mapper.mapShortItemList(items).also {
            cachedInput = items
            cachedOutput = it
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
                contentListRefreshCoordinator.requestRefresh()
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

    private companion object {
        /**
         * How many blank pages in a row one load will walk past. Each of them already cost the
         * interactor several server pages, so this is a ceiling on a single load, not on the list.
         */
        const val MAX_EMPTY_PAGE_CHAIN = 3

        /** How long the walk waits before spending the next round of that budget. */
        val WALK_RESUME_PAUSE = 500.milliseconds
    }
}
