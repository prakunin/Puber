package com.kino.puber.domain.interactor.bookmarks

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.Item

class WatchLaterBookmarkInteractor(
    private val api: KinoPubApiClient,
    private val bookmarkFolders: BookmarkFoldersInteractor,
) {

    suspend fun getItems(): Result<List<Item>> {
        return bookmarkFolders.folders().mapCatching { folders ->
            val folder = folders.findWatchLaterFolder() ?: return@mapCatching emptyList()
            api.getBookmarkItems(folder.id).getOrThrow().items
        }
    }

    suspend fun isBookmarked(itemId: Int): Result<Boolean> {
        return api.getItemBookmarkFolders(itemId).map { folders ->
            folders.any { it.title == FOLDER_TITLE }
        }
    }

    suspend fun add(itemId: Int): Result<Bookmark> {
        return ensureFolder().mapCatching { folder ->
            api.addBookmarkItem(itemId = itemId, folderId = folder.id).getOrThrow()
            folder
        }
    }

    suspend fun remove(itemId: Int): Result<Unit> {
        return bookmarkFolders.folders().mapCatching { folders ->
            val folder = folders.findWatchLaterFolder() ?: return@mapCatching
            api.removeBookmarkItem(itemId = itemId, folderId = folder.id).getOrThrow()
        }
    }

    private suspend fun ensureFolder(): Result<Bookmark> {
        return bookmarkFolders.folders().mapCatching { folders ->
            folders.findWatchLaterFolder() ?: api.createBookmark(FOLDER_TITLE).getOrThrow().also {
                // The cached list was read before this folder existed, and the very next reader
                // would otherwise be told it still does not.
                bookmarkFolders.invalidate()
            }
        }
    }

    private fun List<Bookmark>.findWatchLaterFolder(): Bookmark? {
        return firstOrNull { it.title == FOLDER_TITLE }
    }

    companion object {
        const val FOLDER_TITLE = "Буду смотреть"
    }
}
