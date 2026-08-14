package com.kino.puber.domain.interactor.bookmarks

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Bookmark
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BookmarkFoldersInteractorTest {

    private val api = mockk<KinoPubApiClient>(relaxed = true)
    private val interactor = BookmarkFoldersInteractor(api)

    private val folders = listOf(
        Bookmark(id = 1, title = WatchLaterBookmarkInteractor.FOLDER_TITLE),
        Bookmark(id = 2, title = "Favorites"),
    )

    /**
     * The home screen loads its watch-later row and its ordinary bookmark row at the same time, and
     * each of them needs the folder list before it can ask for any items. Two callers arriving
     * together is the normal case, not a race worth spending a second request on.
     */
    @Test
    fun concurrentCallersShareOneRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { api.getBookmarks() } coAnswers {
            gate.await()
            Result.success(folders)
        }

        val first = async { interactor.folders() }
        val second = async { interactor.folders() }
        gate.complete(Unit)

        assertEquals(folders, first.await().getOrThrow())
        assertEquals(folders, second.await().getOrThrow())
        coVerify(exactly = 1) { api.getBookmarks() }
    }

    /**
     * A failure must not be what the next caller gets served. It is not cached, so the retry behind
     * a pull-to-refresh actually reaches the server.
     */
    @Test
    fun aFailedLoadIsNotCached() = runTest {
        coEvery { api.getBookmarks() } returns Result.failure(IllegalStateException("offline"))

        interactor.folders()
        interactor.folders()

        coVerify(exactly = 2) { api.getBookmarks() }
    }

    /**
     * Creating the watch-later folder changes the very list this caches, so the entry has to go or
     * the next reader is told the folder still does not exist.
     */
    @Test
    fun invalidateForcesTheNextCallerToReload() = runTest {
        coEvery { api.getBookmarks() } returns Result.success(folders)
        interactor.folders()

        interactor.invalidate()
        interactor.folders()

        coVerify(exactly = 2) { api.getBookmarks() }
    }
}
