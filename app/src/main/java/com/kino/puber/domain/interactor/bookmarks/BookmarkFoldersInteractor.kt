package com.kino.puber.domain.interactor.bookmarks

import com.kino.puber.core.coroutine.runCatchingCancellable
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.cache.CacheKeys
import com.kino.puber.data.cache.CacheTtl
import com.kino.puber.data.cache.ContentCacheRepository
import kotlinx.serialization.builtins.ListSerializer

/**
 * The account's bookmark folders, fetched once for everyone who needs them.
 *
 * Nothing can ask for the contents of a folder without first learning its id, so every bookmark-backed
 * row starts with this same list. On the home screen two of them — watch later and the ordinary
 * bookmark row — start together, and before this they each paid for their own copy.
 *
 * The TTL is short on purpose. What this is for is collapsing callers that arrive at once; holding the
 * list any longer would only trade requests for a window in which a folder made elsewhere is invisible.
 */
class BookmarkFoldersInteractor(
    private val api: KinoPubApiClient,
    private val contentCache: ContentCacheRepository,
) {

    /**
     * Failures are deliberately not cached, so the next caller retries for real.
     */
    suspend fun folders(): Result<List<Bookmark>> = runCatchingCancellable {
        contentCache.getPayload(
            key = CacheKeys.BookmarkFolders,
            serializer = ListSerializer(Bookmark.serializer()),
            ttl = CacheTtl.BookmarkFolders,
        ) {
            api.getBookmarks().getOrThrow()
        }
    }

    /** Drops the list, for when something has changed which folders exist. */
    suspend fun invalidate() = contentCache.invalidateQuery(CacheKeys.BookmarkFolders)
}
