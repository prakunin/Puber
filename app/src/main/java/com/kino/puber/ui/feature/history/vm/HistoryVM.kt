package com.kino.puber.ui.feature.history.vm

import androidx.annotation.VisibleForTesting
import com.kino.puber.core.collections.EquallyFunction
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.logger.log
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.paginator.PagingVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.History
import com.kino.puber.data.cache.Cached
import com.kino.puber.domain.interactor.history.HistoryInteractor
import com.kino.puber.domain.interactor.watchstate.WatchStateSyncInteractor
import com.kino.puber.domain.interactor.history.HistoryRowKey
import com.kino.puber.domain.interactor.history.HistoryTraversal
import com.kino.puber.domain.interactor.history.rowKeyOrNull
import com.kino.puber.domain.interactor.history.semanticKeyOrNull
import com.kino.puber.ui.feature.history.model.HistoryAction
import com.kino.puber.ui.feature.history.model.HistoryItemUIState
import com.kino.puber.ui.feature.history.model.HistoryPlaybackTarget
import com.kino.puber.ui.feature.history.model.HistoryUIMapper
import com.kino.puber.ui.feature.history.model.HistoryViewState
import com.kino.puber.ui.feature.details.model.DetailsEpisodeTarget
import com.kino.puber.ui.feature.player.model.PlayerStartMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlin.math.min
import kotlin.time.Duration

internal const val FIRST_PAGE = 1

internal val HistoryRowComparator = EquallyFunction<History> { oldItem, newItem ->
    oldItem.rowKeyOrNull()?.let { it == newItem.rowKeyOrNull() } ?: false
}

private fun rawItemForKey(key: HistoryRowKey?, history: List<History>): History? {
    return key?.let { target ->
        history.firstOrNull { it.rowKeyOrNull() == target }
    }
}

internal class HistoryVM(
    paginator: Paginator.Store<History>,
    private val interactor: HistoryInteractor,
    private val mapper: HistoryUIMapper,
    private val watchStateSyncInteractor: WatchStateSyncInteractor,
    router: AppRouter,
    errorHandler: ErrorHandler,
) : PagingVM<History, HistoryViewState>(paginator, router, errorHandler) {

    override val initialViewState: HistoryViewState = HistoryViewState.Loading

    private val runtime = HistoryRuntimeStore()
    private val pageLoader = HistoryPageLoader(interactor)
    private val contentPublicationLock = Any()

    /**
     * Guards [play] against a second OK press landing while the first is still invalidating the
     * cache. That invalidation is a real Room delete now, not the synchronous map removal it used
     * to be, so the window in which a second press could fire off a second navigation is no
     * longer zero.
     */
    private var playbackJob: Job? = null

    @VisibleForTesting
    internal val testRuntimeState: HistoryRuntimeState
        get() = runtime.snapshot()

    @VisibleForTesting
    internal var testAfterDeleteAvailabilityRead: (() -> Unit)? = null

    @VisibleForTesting
    internal var testBeforeFocusPublicationLockAcquire: (() -> Unit)? = null

    /**
     * Fires between the reads that choose the spinner in [dispatchLoadingState] and the write that
     * publishes it — inside the publication lock, which is the whole point: it is the window in
     * which a concurrent publication used to be able to slip past and be overwritten.
     */
    @VisibleForTesting
    internal var testBeforeLoadingStatePublication: (() -> Unit)? = null

    override fun onStart() {
        init()
        drawStoredFirstPage()
        catchUpWatchState()
        launch { interactor.displaySettingsChanges.collect { requestResumeRefresh() } }
    }

    /**
     * Catches the watch-state index up on what this screen is about to show.
     *
     * The index is built from the same history the list below draws, so arriving here is the moment
     * it is most worth being current — and the moment the user is most likely to notice that it is
     * not. Only the account's age gate is waived: the interactor still refuses to start a second
     * walk while one is running, and still keeps its floor between runs, so bouncing in and out of
     * the screen costs one catch-up rather than one per visit.
     *
     * Owned by the interactor's own scope rather than by this view model, so leaving the screen
     * does not abandon a walk that has only just started.
     */
    private fun catchUpWatchState() {
        watchStateSyncInteractor.requestSync(force = false, maxIndexAge = Duration.ZERO)
    }

    /**
     * Draws the stored first page while the depth walk is still out.
     *
     * A publication and nothing more: [showContent] leaves [runtime] holding the walk's own view of
     * the list, so these rows are replaced by the first page the walk publishes and no part of the
     * state machine has to know they were ever there. Once the walk has settled the rows are its
     * own, so a stored page arriving late is dropped rather than drawn over them.
     *
     * The flow is collected to the end even when nothing is drawn: the collection is what carries
     * the revalidation that leaves a fresh page behind for the next visit. Its failures stay here.
     * With nothing stored, `CachedFeed` rethrows the loader's failure rather than reporting
     * `RefreshFailed`, and this is not the load the user is waiting on — letting it reach the
     * shared exception handler would reset the runtime under a walk that is still out and turn a
     * successful load into a full-screen error.
     */
    private fun drawStoredFirstPage() {
        launch {
            try {
                interactor.observeFirstPage().collect { cached ->
                    when (cached) {
                        is Cached.Value -> drawStoredRows(cached.value.items)
                        is Cached.RefreshFailed ->
                            log(cached.error, "Failed to refresh the history first page")
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                log(error, "Failed to read the stored history first page")
            }
        }
    }

    /**
     * The stored page goes through the same filter the walk applies: one server page can carry two
     * records of the same episode, and the list keys its rows by media identity, so an unfiltered
     * page would hand it a duplicate key. A page with nothing left to draw is not drawn at all —
     * the spinner says more than a blank list, and the walk decides between rows and empty soon
     * enough.
     *
     * The check that the walk has not published yet, and the publication itself, are one critical
     * section. The walk publishes from `pagingScope` on another thread, so a check made outside the
     * lock could pass, wait for the walk's own [showContent] to finish inside it, and then draw the
     * stored page over the rows the walk had just published — leaving the view a page behind the
     * runtime, which answers `hasMorePages` and `LoadMore` from the full depth. The monitor is
     * reentrant, so [showContent] takes it again from here.
     */
    private fun drawStoredRows(stored: List<History>) {
        val rows = HistoryTraversal().filterFirstOccurrences(stored)
        if (rows.isEmpty()) return
        synchronized(contentPublicationLock) {
            if (runtime.snapshot().hasCompletedInitialLoad) return
            showContent(rows, isRefreshing = true)
        }
    }

    override fun onAction(action: UIAction) {
        if (action.isBlockedDuringDeletionFlow() && runtime.isDeletionFlowActive()) return
        when (action) {
            is CommonAction.ItemSelected<*> -> openDetails(action.item as HistoryItemUIState)
            is CommonAction.ItemFocused<*> -> onItemFocused(action.item as HistoryItemUIState)
            CommonAction.LoadMore,
            CommonAction.ReloadNextPage -> requestNextPage()
            CommonAction.Refresh -> requestRefresh()
            CommonAction.OnResume -> {
                catchUpWatchState()
                requestResumeRefresh()
            }
            CommonAction.RetryClicked -> retry()
            is HistoryAction.OpenContextMenu -> openContextMenu(action.item)
            HistoryAction.DismissContextMenu -> dismissContextMenu()
            is HistoryAction.Play -> play(
                item = action.item,
                startMode = action.startMode,
            )
            is HistoryAction.OpenDetails -> openDetails(action.item)
            is HistoryAction.DeleteExactMedia -> deleteExactMedia(action.item)
            HistoryAction.RetryReconciliation -> retry()
            else -> super.onAction(action)
        }
    }

    // Cancellation is rethrown, but the in-flight page operation has to be released first, or the
    // runtime keeps rejecting later pages as stale. Detekt wants the throw to come first instead.
    @Suppress("SuspendFunSwallowedCancellation")
    override fun onLoadFirstPage() {
        val request = runtime.beginFirstPage() ?: return
        pagingLaunch {
            try {
                val result = pageLoader.loadDepth(request.loadedPageDepth)
                val accepted = runtime.acceptFirstPage(
                    operationId = request.operationId,
                    result = result,
                ) ?: return@pagingLaunch
                replace(
                    result.items,
                    rawItemForKey(accepted.focusedKey, result.items),
                )
            } catch (error: CancellationException) {
                runtime.cancelFirstPage(request.operationId)
                throw error
            } catch (error: Throwable) {
                handleFirstPageFailure(
                    operationId = request.operationId,
                    error = errorHandler.map(error),
                )
            }
        }
    }

    // See [onLoadFirstPage] — the operation slot has to be released before cancellation propagates.
    @Suppress("SuspendFunSwallowedCancellation")
    override fun onLoadNextPage(key: History?) {
        val request = runtime.currentNextPageRequest() ?: return
        pagingLaunch {
            try {
                val result = pageLoader.loadNextRenderable(
                    startPage = runtime.snapshot().currentPage,
                    alreadyOnScreen = runtime.snapshot().stableHistory,
                )
                val accepted = runtime.acceptNextPage(
                    operationId = request.operationId,
                    result = result,
                    merge = ::mergeStableHistory,
                )
                if (accepted) {
                    setNextPage(result.items)
                }
            } catch (error: CancellationException) {
                runtime.cancelNextPage(request.operationId)
                throw error
            } catch (error: Throwable) {
                val mappedError = errorHandler.map(error)
                val accepted = runtime.failNextPage(
                    operationId = request.operationId,
                    message = mappedError.message,
                )
                if (accepted) {
                    setPageError(mappedError)
                }
            }
        }
    }

    override fun dispatchError(error: ErrorEntity) {
        val state = runtime.resetAfterUnhandledError()
        if (state.stableHistory.isEmpty()) {
            updateViewState(HistoryViewState.Error(error.message))
        } else {
            replace(
                state.stableHistory,
                rawItemForKey(state.focusedKey, state.stableHistory),
            )
            showMessage(error.message)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dispatchListState(state: Paginator.State) {
        when (state) {
            Paginator.State.Loading -> dispatchLoadingState()
            Paginator.State.Empty -> dispatchEmptyState()
            is Paginator.State.ErrorEmpty -> {
                runtime.update { it.copy(operation = HistoryOperation.Idle) }
                updateViewState(HistoryViewState.Error(state.error.message))
            }
            is Paginator.State.Data<*> -> {
                showContent(
                    history = state.data as List<History>,
                    paginatorFocus = state.key as History?,
                )
                finishContentPublication(
                    HistoryPublicationKind.FIRST_PAGE,
                    HistoryPublicationKind.REFRESH_ERROR,
                    HistoryPublicationKind.NEXT_PAGE,
                )
            }
            is Paginator.State.Refreshing<*> -> showContent(
                history = state.data as List<History>,
                isRefreshing = true,
            )
            is Paginator.State.LoadingNext<*> -> showContent(
                history = state.data as List<History>,
                isLoadingMore = true,
            )
            is Paginator.State.LoadingPrev<*> -> showContent(state.data as List<History>)
            is Paginator.State.Error<*> -> {
                showMessage(state.error.message)
                showContent(state.data as List<History>)
                finishContentPublication(HistoryPublicationKind.REFRESH_ERROR)
            }
            is Paginator.State.PageErrorNext<*> -> {
                showMessage(state.error.message)
                showContent(state.data as List<History>)
                finishContentPublication(HistoryPublicationKind.NEXT_PAGE_ERROR)
            }
            is Paginator.State.PageErrorPrev<*> -> {
                showMessage(state.error.message)
                showContent(state.data as List<History>)
            }
        }
    }

    /**
     * Under [contentPublicationLock] like every other writer of this state, and for the same reason.
     * It runs on the paginator's actor thread and decides what to write from what it reads — the
     * runtime and the state on screen — while [showContent] holds the monitor across a
     * read-modify-write of its own. Outside it, a decision taken against an empty screen could be
     * carried out after the stored page had been drawn, blanking those rows back into the spinner
     * this whole path exists to avoid.
     *
     * No new lock ordering: everything reached from here takes the runtime store's monitor after
     * this one, never before, which is the order the rest of the class already keeps.
     */
    private fun dispatchLoadingState() {
        synchronized(contentPublicationLock) {
            val runtimeState = runtime.snapshot()
            when {
                runtimeState.stableHistory.isNotEmpty() -> showContent(
                    history = runtimeState.stableHistory,
                    isRefreshing = true,
                )
                // Rows on screen with nothing stable behind them are the stored first page and
                // nothing else. This state reaches us from the paginator's own actor thread, so it
                // can arrive after the publication that drew them; blanking them back to a spinner
                // would be exactly the flicker the stored page exists to remove. The walk replaces
                // them when it lands, and a walk that fails still reaches the error through
                // handleFirstPageFailure.
                stateValue is HistoryViewState.Content -> Unit
                else -> {
                    testBeforeLoadingStatePublication?.invoke()
                    updateViewState(HistoryViewState.Loading)
                }
            }
        }
    }

    private fun dispatchEmptyState() {
        if (!runtime.hasPendingEmptyPublication()) return
        updateViewState(HistoryViewState.Empty)
        runtime.completeEmptyPublication()
        runQueuedDeletionIfReady()
    }

    private fun completePublishedOperation(vararg kinds: HistoryPublicationKind) {
        runtime.completePublication(*kinds)
    }

    private fun finishContentPublication(vararg kinds: HistoryPublicationKind) {
        completePublishedOperation(*kinds)
        runQueuedDeletionIfReady()
        runDeferredRefreshIfReady()
        updateDeleteExactMediaAvailability()
    }

    private fun showContent(
        history: List<History>,
        isRefreshing: Boolean = false,
        isLoadingMore: Boolean = false,
        paginatorFocus: History? = null,
    ) {
        synchronized(contentPublicationLock) {
            val items = mapper.map(history)
            val availableKeys = items.mapTo(mutableSetOf(), HistoryItemUIState::rowKey)
            val paginatorFocusKey = paginatorFocus?.rowKeyOrNull()
            val runtimeState = runtime.prepareContentPublication(availableKeys, paginatorFocusKey)
            updateViewState(
                runtimeState.toContentViewState(
                    items = items,
                    availableKeys = availableKeys,
                    isRefreshing = isRefreshing,
                    isLoadingMore = isLoadingMore,
                ),
            )
        }
    }

    private fun requestNextPage() {
        val started = runtime.beginNextPage()
        if (!started) return
        updateDeleteExactMediaAvailability()
        notifyLoadNextPage()
    }

    private fun requestRefresh(deferIfBusy: Boolean = false) {
        if (stateValue !is HistoryViewState.Content) return
        val started = runtime.beginRefresh(deferIfBusy)
        if (!started) return
        enqueueRefresh()
    }

    private fun enqueueRefresh() {
        val state = runtime.snapshot()
        updateDeleteExactMediaAvailability()
        replace(
            state.stableHistory,
            rawItemForKey(state.focusedKey, state.stableHistory),
        )
        refresh()
    }

    private fun requestResumeRefresh() {
        if (!runtime.snapshot().hasCompletedInitialLoad) return
        when (stateValue) {
            HistoryViewState.Loading -> Unit
            HistoryViewState.Empty,
            is HistoryViewState.Error -> requestRestart()
            is HistoryViewState.Content -> requestRefresh(deferIfBusy = true)
        }
    }

    private fun retry() {
        val operation = runtime.snapshot().operation
        if (operation is HistoryOperation.ReconciliationFailed) {
            retryReconciliation(operation.reconciliation)
        } else {
            requestRestart()
        }
    }

    private fun requestRestart() {
        if (runtime.beginRestart()) {
            resetPaging()
        }
    }

    private fun onItemFocused(item: HistoryItemUIState) {
        testBeforeFocusPublicationLockAcquire?.invoke()
        synchronized(contentPublicationLock) {
            runtime.focus(item)
            updateViewState<HistoryViewState.Content> { copy(focusKey = item.rowKey) }
        }
    }

    private fun openContextMenu(item: HistoryItemUIState) {
        synchronized(contentPublicationLock) {
            val opened = runtime.openMenu(item)
            if (!opened) return
            updateViewState<HistoryViewState.Content> {
                copy(openMenuKey = item.rowKey, focusKey = item.rowKey)
            }
        }
    }

    private fun dismissContextMenu() {
        synchronized(contentPublicationLock) {
            runtime.dismissMenu()
            updateViewState<HistoryViewState.Content> { copy(openMenuKey = null) }
        }
    }

    private fun play(
        item: HistoryItemUIState,
        startMode: PlayerStartMode = PlayerStartMode.ResumeIfAvailable,
    ) {
        when (val target = item.playbackTarget) {
            is HistoryPlaybackTarget.Movie -> playAndNavigate(item) {
                router.screens.player(
                    itemId = item.itemId,
                    videoNumber = target.videoNumber,
                    startMode = startMode,
                )
            }
            is HistoryPlaybackTarget.Episode -> playAndNavigate(item) {
                router.screens.player(
                    itemId = item.itemId,
                    seasonNumber = target.seasonNumber,
                    episodeNumber = target.episodeNumber,
                    startMode = startMode,
                )
            }
            HistoryPlaybackTarget.Details -> openDetails(item)
        }
    }

    /**
     * Invalidates the cached item details, then navigates to the player regardless of whether
     * that invalidation succeeded — Play silently doing nothing because a Room delete failed
     * would be a far worse experience than the player briefly reading a stale cache entry. Guarded
     * by [playbackJob] so a second press while the first is still in flight is ignored rather than
     * pushing the player screen twice.
     */
    private fun playAndNavigate(item: HistoryItemUIState, screen: () -> PuberScreen) {
        if (playbackJob?.isActive == true) return
        playbackJob = launch {
            try {
                interactor.invalidateItemDetails(item.itemId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                log(error, "Failed to invalidate cached item details before playback")
            }
            router.navigateTo(screen())
        }
    }

    private fun openDetails(item: HistoryItemUIState) {
        dismissContextMenu()
        router.navigateTo(router.screens.historyDetails(item))
    }

    private fun deleteExactMedia(item: HistoryItemUIState) {
        val content = stateValue as? HistoryViewState.Content ?: return
        val currentItem = content.items.firstOrNull { it.rowKey == item.rowKey } ?: return
        val reconciliation = createReconciliationContext(content, currentItem, currentPageDepth()) ?: return
        startDeletion(
            item = currentItem,
            reconciliation = reconciliation,
            queueIfBusy = true,
        )
    }

    private fun startDeletion(
        item: HistoryItemUIState,
        reconciliation: HistoryReconciliationContext,
        queueIfBusy: Boolean = false,
    ) {
        val transition = runtime.beginDeletion(item, reconciliation, queueIfBusy)
        if (transition.queued) {
            showQueuedDeletion(item)
            return
        }
        val deletion = transition.operation ?: return
        showDeletionPending(item)
        launch { performDeletion(deletion, item, reconciliation) }
    }

    private fun showQueuedDeletion(item: HistoryItemUIState) {
        synchronized(contentPublicationLock) {
            updateViewState<HistoryViewState.Content> {
                copy(
                    openMenuKey = null,
                    focusKey = item.rowKey,
                    isDeleteExactMediaAvailable = false,
                )
            }
        }
    }

    private fun showDeletionPending(item: HistoryItemUIState) {
        synchronized(contentPublicationLock) {
            val content = stateValue as? HistoryViewState.Content
            val deletingKeys = if (content?.items?.any { it.rowKey == item.rowKey } == true) {
                setOf(item.rowKey)
            } else {
                emptySet()
            }
            if (content != null) {
                updateViewState(
                    content.copy(
                        openMenuKey = null,
                        deletingKeys = deletingKeys,
                        nextPageErrorMessage = null,
                        focusKey = item.rowKey,
                        isDeleteExactMediaAvailable = false,
                    ),
                )
            }
        }
    }

    // See [onLoadFirstPage] — the pending deletion has to be released before cancellation propagates.
    @Suppress("SuspendFunSwallowedCancellation")
    private suspend fun performDeletion(
        deletion: HistoryOperation.Deleting,
        item: HistoryItemUIState,
        reconciliation: HistoryReconciliationContext,
    ) {
        try {
            interactor.clearExactMediaHistory(
                mediaId = item.deletionMediaId,
                itemId = item.itemId,
            )
        } catch (error: CancellationException) {
            runtime.cancelDeletion(deletion.operationId)
            throw error
        } catch (error: Throwable) {
            onMutationFailure(
                operationId = deletion.operationId,
                error = errorHandler.map(error),
            )
            return
        }

        val retained = runtime.snapshot().stableHistory.filterNot { history ->
            if (item.semanticKey != null) {
                history.semanticKeyOrNull() == item.semanticKey
            } else {
                history.video?.id == item.deletionMediaId
            }
        }
        val requestedFocusKey = resolveHistoryFocusKey(
            items = mapper.map(retained),
            reconciliation = reconciliation,
        )
        val reconciliationStart = runtime.beginReconciliation(
            deletionId = deletion.operationId,
            reconciliation = reconciliation,
            retained = retained,
            requestedFocusKey = requestedFocusKey,
        ) ?: return
        showContent(reconciliationStart.retained, isRefreshing = true)
        reconcile(
            operationId = reconciliationStart.operationId,
            reconciliation = reconciliation,
        )
    }

    private fun onMutationFailure(
        operationId: Long,
        error: ErrorEntity,
    ) {
        val restored = runtime.failDeletion(operationId) ?: return
        showContent(restored.stableHistory)
        showMessage(error.message)
        runDeferredRefreshIfReady()
    }

    private fun retryReconciliation(reconciliation: HistoryReconciliationContext) {
        val operationId = runtime.beginReconciliationRetry(reconciliation) ?: return
        showContent(runtime.snapshot().stableHistory, isRefreshing = true)
        launch {
            reconcile(
                operationId = operationId,
                reconciliation = reconciliation,
            )
        }
    }

    private suspend fun reconcile(
        operationId: Long,
        reconciliation: HistoryReconciliationContext,
    ) {
        try {
            val result = pageLoader.loadDepth(reconciliation.loadedPageDepth)
            val requestedFocusKey = resolveHistoryFocusKey(
                items = mapper.map(result.items),
                reconciliation = reconciliation,
            )
            runtime.acceptReconciliation(
                operationId = operationId,
                result = result,
                requestedFocusKey = requestedFocusKey,
            ) ?: return
            if (result.items.isEmpty()) {
                updateViewState(HistoryViewState.Empty)
            } else {
                showContent(
                    history = result.items,
                    paginatorFocus = rawItemForKey(requestedFocusKey, result.items),
                )
            }
            val published = runtime.completeReconciliationPublication(operationId) ?: return
            replace(
                result.items,
                rawItemForKey(published.requestedFocusKey, result.items),
            )
            runQueuedDeletionIfReady()
            runDeferredRefreshIfReady()
            updateDeleteExactMediaAvailability()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val errorMessage = errorHandler.map(error).message
            val stableHistory = runtime.snapshot().stableHistory
            val requestedFocusKey = resolveHistoryFocusKey(
                items = mapper.map(stableHistory),
                reconciliation = reconciliation,
            )
            val failed = runtime.failReconciliation(
                operationId = operationId,
                reconciliation = reconciliation,
                message = errorMessage,
                requestedFocusKey = requestedFocusKey,
            ) ?: return
            showContent(failed.stableHistory)
            showMessage(errorMessage)
        }
    }

    /**
     * A stored first page on screen does not soften this, deliberately. It was drawn as a
     * publication and never entered `stableHistory`, so a failed walk still finds no stable rows
     * and replaces them with the full-screen error — rows then error, where before the stored page
     * existed it was spinner then error. The alternative is to let the stored page write
     * `stableHistory`, or to teach this path about rows the runtime does not hold, and both make
     * the machine responsible for content no page ever produced. The failing load here is the one
     * the user is waiting on, not a background revalidation, so an error is the honest answer.
     */
    private fun handleFirstPageFailure(
        operationId: Long,
        error: ErrorEntity,
    ) {
        val failed = runtime.failFirstPage(operationId) ?: return
        if (failed.stableHistory.isEmpty()) {
            setGeneralError(error)
            return
        }
        replace(
            failed.stableHistory,
            rawItemForKey(failed.focusedKey, failed.stableHistory),
        )
        showMessage(error.message)
    }

    private fun currentPageDepth(): Int = runtime.snapshot().currentPage.coerceAtLeast(FIRST_PAGE)

    private fun runDeferredRefreshIfReady() {
        if (stateValue !is HistoryViewState.Content) return
        if (runtime.beginDeferredRefresh()) {
            enqueueRefresh()
        }
    }

    private fun runQueuedDeletionIfReady() {
        val state = runtime.snapshot()
        if (state.operation != HistoryOperation.Idle) return
        val queued = state.queuedDeletion ?: return
        val latestContent = stateValue as? HistoryViewState.Content
        val currentItem = latestContent
            ?.items
            ?.firstOrNull { it.rowKey == queued.rowKey }
        val reconciliation = currentItem
            ?.let { createReconciliationContext(latestContent, it, currentPageDepth()) }
        if (currentItem == null || reconciliation == null) {
            if (runtime.dropQueuedDeletion(queued.rowKey)) {
                updateDeleteExactMediaAvailability()
                runDeferredRefreshIfReady()
            }
            return
        }
        startDeletion(currentItem, reconciliation)
    }

    private fun updateDeleteExactMediaAvailability() {
        synchronized(contentPublicationLock) {
            val content = stateValue as? HistoryViewState.Content ?: return
            val isDeleteExactMediaAvailable = runtime.isDeleteAvailable()
            testAfterDeleteAvailabilityRead?.invoke()
            updateViewState(
                content.copy(
                    isDeleteExactMediaAvailable = isDeleteExactMediaAvailable,
                ),
            )
        }
    }

}

/**
 * Where the row sits in the list right now, so a delete can put the focus somewhere sensible and a
 * failure can put the row back where it was.
 */
private fun createReconciliationContext(
    content: HistoryViewState.Content,
    item: HistoryItemUIState,
    loadedPageDepth: Int,
): HistoryReconciliationContext? {
    val oldIndex = content.items.indexOfFirst { it.rowKey == item.rowKey }
    if (oldIndex < 0) return null
    return HistoryReconciliationContext(
        oldIndex = oldIndex,
        nextKey = content.items.getOrNull(oldIndex + 1)?.rowKey,
        previousKey = content.items.getOrNull(oldIndex - 1)?.rowKey,
        loadedPageDepth = loadedPageDepth,
    )
}

private fun Screens.historyDetails(item: HistoryItemUIState): PuberScreen {
    return when (val target = item.playbackTarget) {
        is HistoryPlaybackTarget.Episode -> details(
            itemId = item.itemId,
            initialEpisode = DetailsEpisodeTarget(
                seasonNumber = target.seasonNumber,
                episodeNumber = target.episodeNumber,
            ),
        )
        is HistoryPlaybackTarget.Movie,
        HistoryPlaybackTarget.Details -> details(item.itemId)
    }
}
