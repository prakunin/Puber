package com.kino.puber.ui.feature.collections.detail.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.collections.CollectionInteractor
import com.kino.puber.ui.feature.collections.detail.model.CollectionDetailViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll

internal class CollectionDetailVM(
    router: AppRouter,
    private val collectionId: Int,
    private val collectionTitle: String,
    private val interactor: CollectionInteractor,
    private val savedItemInteractor: SavedItemInteractor,
    private val mapper: VideoItemUIMapper,
    override val errorHandler: ErrorHandler,
) : PuberVM<CollectionDetailViewState>(router) {

    override val initialViewState = CollectionDetailViewState.Loading
    private var contentChanges = ContentChangeSet.empty()
    private val pendingMutations = mutableSetOf<Job>()
    private var loadJob: Job? = null
    private val savedSinceLastPublish = mutableSetOf<Int>()
    private val saveJobItemIds = mutableMapOf<Job, Int>()
    private var closing = false

    override fun dispatchError(error: ErrorEntity) {
        if (stateValue is CollectionDetailViewState.Content) {
            showMessage(error.message)
        } else {
            updateViewState(CollectionDetailViewState.Error(error.message))
        }
    }

    override fun onStart() {
        loadItems()
    }

    override fun handleAction(action: UIAction) {
        if (closing) return
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
            is CommonAction.RetryClicked -> loadItems()
            else -> super.handleAction(action)
        }
    }

    private fun loadItems() {
        if (stateValue !is CollectionDetailViewState.Content) {
            updateViewState(CollectionDetailViewState.Loading)
        }
        saveJobItemIds.keys.removeAll { !it.isActive }
        savedSinceLastPublish.retainAll(saveJobItemIds.values.toSet())
        loadJob?.cancel()
        loadJob = launch {
            val items = interactor.getCollectionItems(collectionId)
            awaitPendingMutations()
            publishItems(mapper.mapShortItemList(items))
        }
    }

    private fun publishItems(fetched: List<VideoItemUIState>) {
        val onScreen = (stateValue as? CollectionDetailViewState.Content)?.items.orEmpty()
            .associateBy(VideoItemUIState::id)
        val patched = fetched.map { item ->
            val current = onScreen[item.id]
            if (item.id in savedSinceLastPublish && current != null) {
                item.copy(isSaved = current.isSaved)
            } else {
                item
            }
        }
        updateViewState(CollectionDetailViewState.Content(title = collectionTitle, items = patched))
        savedSinceLastPublish.clear()
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
        loadItems()
    }

    private fun setItemSaved(item: VideoItemUIState, saved: Boolean) {
        savedSinceLastPublish += item.id
        updateSavedItem(item.id, saved)
        val job = launchMutation {
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
            }.onFailure {
                updateSavedItem(item.id, item.isSaved)
                throw it
            }
        }
        saveJobItemIds[job] = item.id
    }

    private fun updateSavedItem(itemId: Int, saved: Boolean) {
        updateViewState<CollectionDetailViewState.Content> {
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
            router.removeBackDispatcher(this@CollectionDetailVM)
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
}
