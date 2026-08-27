package com.kino.puber.ui.feature.bookmarks.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.domain.interactor.bookmarks.BookmarkInteractor
import com.kino.puber.domain.interactor.details.DetailsInteractor
import com.kino.puber.domain.interactor.watchstate.CardDisplayChanges
import com.kino.puber.ui.feature.bookmarks.model.BookmarksViewState

internal class BookmarksVM(
    router: AppRouter,
    private val interactor: BookmarkInteractor,
    private val detailsInteractor: DetailsInteractor,
    private val mapper: VideoItemUIMapper,
    private val cardDisplayChanges: CardDisplayChanges,
    override val errorHandler: ErrorHandler,
) : PuberVM<BookmarksViewState>(router) {

    override val initialViewState: BookmarksViewState = BookmarksViewState.Loading

    /** The selected folder's items as the server sent them, kept so cards can be re-mapped. */
    private var loadedItems: List<Item>? = null

    override fun dispatchError(error: ErrorEntity) {
        when (val state = stateValue) {
            is BookmarksViewState.Content -> {
                showMessage(error.message)
                if (state.isLoadingItems) {
                    updateViewState<BookmarksViewState.Content> { copy(isLoadingItems = false) }
                }
            }
            else -> updateViewState(BookmarksViewState.Error(error.message))
        }
    }

    override fun onStart() {
        loadBookmarks()
        launch {
            cardDisplayChanges.changes.collect { remapLoadedItems() }
        }
    }

    /**
     * Re-maps the folder on screen from what was already fetched. A watched mark changes how a card
     * is drawn, not which items the folder holds, so it is not worth another request — and a reload
     * from the top would drop the user back on the first folder.
     */
    private fun remapLoadedItems() {
        if (stateValue !is BookmarksViewState.Content) return
        val items = loadedItems ?: return
        updateViewState<BookmarksViewState.Content> {
            copy(items = mapper.mapShortItemList(items).markSaved())
        }
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
            is CommonAction.ItemWatchedChanged<*> -> {
                val item = action.item as VideoItemUIState
                setItemWatched(item, action.isWatched)
            }
            else -> super.onAction(action)
        }
    }

    fun onFolderSelected(folderId: Int) {
        updateViewState<BookmarksViewState.Content> {
            copy(selectedFolderId = folderId, isLoadingItems = true)
        }
        loadFolderItems(folderId)
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
        if (ContentChangeType.Bookmark in changes.types || ContentChangeType.Watchlist in changes.types) {
            loadBookmarks()
            return
        }
        (stateValue as? BookmarksViewState.Content)?.selectedFolderId?.let(::loadFolderItems)
    }

    private fun loadBookmarks() {
        launch {
            val folders = interactor.getBookmarks()
            val firstFolder = folders.firstOrNull()
            val items = if (firstFolder != null) {
                interactor.getBookmarkItems(firstFolder.id, page = 1).items
            } else {
                emptyList()
            }
            loadedItems = items
            updateViewState(
                BookmarksViewState.Content(
                    folders = folders,
                    selectedFolderId = firstFolder?.id,
                    items = mapper.mapShortItemList(items).markSaved(),
                    isLoadingItems = false,
                )
            )
        }
    }

    private fun loadFolderItems(folderId: Int) {
        launch {
            val response = interactor.getBookmarkItems(folderId, page = 1)
            loadedItems = response.items
            updateViewState<BookmarksViewState.Content> {
                copy(
                    items = mapper.mapShortItemList(response.items).markSaved(),
                    isLoadingItems = false,
                )
            }
        }
    }

    private fun setItemSaved(item: VideoItemUIState, saved: Boolean) {
        val folderId = (stateValue as? BookmarksViewState.Content)?.selectedFolderId ?: return
        launch {
            interactor.setItemSaved(itemId = item.id, folderId = folderId, saved = saved)
            updateViewState<BookmarksViewState.Content> {
                copy(items = items.updateSaved(item.id, saved).filterNot { video -> !video.isSaved })
            }
        }
    }

    private fun setItemWatched(item: VideoItemUIState, watched: Boolean) {
        launch {
            val update = detailsInteractor.setItemWatched(
                id = item.id,
                isSeriesLike = item.isSeriesLike,
                watched = watched,
            )
            updateViewState<BookmarksViewState.Content> {
                copy(
                    items = items.map { video ->
                        if (video.id == item.id) {
                            video.copy(isWatched = update.isWatched)
                        } else {
                            video
                        }
                    },
                )
            }
        }
    }

    private fun List<VideoItemUIState>.markSaved(): List<VideoItemUIState> {
        return map { item -> item.copy(isSaved = true) }
    }

    private fun List<VideoItemUIState>.updateSaved(itemId: Int, saved: Boolean): List<VideoItemUIState> {
        return map { item ->
            if (item.id == itemId) item.copy(isSaved = saved) else item
        }
    }
}
