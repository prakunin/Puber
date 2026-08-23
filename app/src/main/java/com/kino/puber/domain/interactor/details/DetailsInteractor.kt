package com.kino.puber.domain.interactor.details

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.BookmarkFolder
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.TmdbCastMember
import com.kino.puber.data.api.models.WatchingToggleResponse
import com.kino.puber.data.api.models.isSeriesLike
import com.kino.puber.data.cache.Cached
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.data.repository.TmdbCastRepository
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.domain.interactor.bookmarks.WatchLaterBookmarkInteractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

internal class DetailsInteractor(
    private val api: KinoPubApiClient,
    private val itemDetailsRepository: ItemDetailsRepository,
    private val watchLaterBookmarkInteractor: WatchLaterBookmarkInteractor,
    private val watchStateRepository: WatchStateRepository,
    private val tmdbCastRepository: TmdbCastRepository,
) {

    fun observeItemDetails(id: Int, force: Boolean = false): Flow<Cached<Item>> {
        return itemDetailsRepository.observeItemDetails(id, force = force)
    }

    fun observeSimilarItems(id: Int, force: Boolean = false): Flow<Cached<List<Item>>> {
        return itemDetailsRepository.observeSimilarItems(id, force = force)
    }

    suspend fun getItemDetails(id: Int): Item {
        return itemDetailsRepository.getItemDetails(id)
    }

    suspend fun refreshItemDetails(id: Int): Item {
        return itemDetailsRepository.refresh(id)
    }

    suspend fun getSimilarItems(id: Int): List<Item> {
        return api.getSimilarItems(id).getOrThrow().items.orEmpty()
    }

    /**
     * What the item payload itself says about the watchlist, with no second request.
     *
     * Enough to draw the screen with. For a movie whose payload lists no bookmarks this can be a
     * false negative — [isInWatchLaterFolder] is the authoritative answer, and the caller patches it
     * in once it arrives rather than holding the screen for it.
     */
    fun seededWatchlistFlag(item: Item): Boolean {
        if (item.type.isSeriesLike()) return item.inWatchlist ?: false
        return item.bookmarks.orEmpty().isNotEmpty()
    }

    suspend fun getTmdbCast(imdbId: String): List<TmdbCastMember> {
        return tmdbCastRepository.getCast(imdbId)
    }

    suspend fun isInWatchLaterFolder(item: Item): Boolean {
        if (item.type.isSeriesLike()) return item.inWatchlist ?: false
        if (item.bookmarks.orEmpty().isNotEmpty()) {
            return true
        }
        return getMovieBookmarkFolders(item.id).isNotEmpty()
    }

    suspend fun setMovieBookmarked(id: Int, bookmarked: Boolean): MovieBookmarkUpdate {
        if (bookmarked) {
            val folder = watchLaterBookmarkInteractor.add(id).getOrThrow().let { bookmark ->
                BookmarkFolder(id = bookmark.id, title = bookmark.title, count = bookmark.count ?: 0)
            }
            itemDetailsRepository.invalidate(id)
            return MovieBookmarkUpdate(
                isBookmarked = true,
                folderTitle = folder.title,
            )
        }

        val folders = getMovieBookmarkFolders(id)
        val folder = folders.firstOrNull()
        if (folder != null) {
            api.removeBookmarkItem(itemId = id, folderId = folder.id).getOrThrow()
            itemDetailsRepository.invalidate(id)
        }
        val remainingFolders = if (folder != null) {
            readRemainingBookmarkFolders(id, folders.drop(1))
        } else {
            emptyList()
        }
        return MovieBookmarkUpdate(
            isBookmarked = remainingFolders.isNotEmpty(),
            folderTitle = folder?.title,
        )
    }

    // Cancellation is rethrown, but the optimistic local mark has to be reverted first — detekt
    // wants the throw to be the first statement, which would leave the movie hidden for good.
    @Suppress("SuspendFunSwallowedCancellation")
    suspend fun setMovieWatched(id: Int, watched: Boolean): MovieWatchedUpdate {
        // Written before the call so the catalogue reflects the mark immediately; a sync cannot
        // undo it while the row stays pending.
        watchStateRepository.markLocally(itemId = id, isSeriesLike = false, isFullyWatched = watched)
        val response = try {
            api.toggleWatchingStatus(
                id = id,
                status = if (watched) WATCHED_STATUS else UNWATCHED_STATUS,
            ).getOrThrow()
        } catch (error: Throwable) {
            // A pending row is never corrected by a sync, so a failed toggle must not leave one
            // behind — that would hide the movie for good.
            watchStateRepository.revertLocalMark(id)
            throw error
        }
        itemDetailsRepository.invalidate(id)
        val confirmed = response.confirmedWatchedOr(watched)
        watchStateRepository.markLocally(itemId = id, isSeriesLike = false, isFullyWatched = confirmed)
        watchStateRepository.confirmLocalMark(id)
        return MovieWatchedUpdate(isWatched = confirmed)
    }

    suspend fun setEpisodeWatched(id: Int, season: Int, episode: Int, watched: Boolean): WatchedUpdate {
        val response = api.toggleWatchingStatus(
            id = id,
            status = if (watched) WATCHED_STATUS else UNWATCHED_STATUS,
            season = season,
            video = episode,
        ).getOrThrow()
        itemDetailsRepository.invalidate(id)
        reindexSeries(id)
        return WatchedUpdate(isWatched = response.confirmedWatchedOr(watched))
    }

    suspend fun setSeasonWatched(id: Int, season: Int, watched: Boolean): WatchedUpdate {
        val response = api.toggleWatchingStatus(
            id = id,
            status = if (watched) WATCHED_STATUS else UNWATCHED_STATUS,
            season = season,
        ).getOrThrow()
        itemDetailsRepository.invalidate(id)
        reindexSeries(id)
        return WatchedUpdate(isWatched = response.confirmedWatchedOr(watched))
    }

    /**
     * Brings the local index in line after an episode or a season was toggled.
     *
     * Unlike a movie, the toggle response says nothing about the series as a whole: marking one
     * episode watched may or may not have been the last one. Only the item's own counters can tell,
     * so the freshly invalidated details are read back and recorded — the catalogue would otherwise
     * keep the previous verdict until the next sync, up to an hour later.
     *
     * A failure here costs nothing but freshness, so it must not fail the toggle the user asked for.
     */
    private suspend fun reindexSeries(id: Int) {
        try {
            itemDetailsRepository.getItemDetails(id)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // The toggle itself succeeded; the index catches up on the next sync.
        }
    }

    private suspend fun readRemainingBookmarkFolders(
        id: Int,
        knownRemaining: List<BookmarkFolder>,
    ): List<BookmarkFolder> {
        return try {
            getMovieBookmarkFolders(id)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            knownRemaining
        }
    }

    private fun WatchingToggleResponse.confirmedWatchedOr(requested: Boolean): Boolean {
        return when {
            watched != null -> watched == WATCHED_STATUS
            watching?.status != null -> watching.status == WATCHED_STATUS
            else -> requested
        }
    }

    private suspend fun getMovieBookmarkFolders(id: Int): List<BookmarkFolder> {
        return api.getItemBookmarkFolders(id).getOrThrow()
    }

    private companion object {
        const val WATCHED_STATUS = 1
        const val UNWATCHED_STATUS = 0
    }
}

internal data class MovieWatchedUpdate(
    val isWatched: Boolean,
)

internal data class MovieBookmarkUpdate(
    val isBookmarked: Boolean,
    val folderTitle: String?,
)

internal data class WatchedUpdate(
    val isWatched: Boolean,
)
