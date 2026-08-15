package com.kino.puber.domain.interactor.home

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.cache.Cached
import com.kino.puber.data.preferences.ContentPreferences
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.PersistentPayloadStore
import com.kino.puber.data.repository.StoredPayload
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.domain.interactor.bookmarks.BookmarkFoldersInteractor
import com.kino.puber.domain.interactor.bookmarks.WatchLaterBookmarkInteractor
import com.kino.puber.domain.interactor.watchstate.RecentlyPlayedOrder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeInteractorCacheTest {

    private val api = mockk<KinoPubApiClient>(relaxed = true)
    private val watchLaterBookmarkInteractor = mockk<WatchLaterBookmarkInteractor>(relaxed = true)
    private val navigationPreferencesRepository = mockk<NavigationPreferencesRepository>(relaxed = true)
    private val store = InMemoryPayloadStore()

    private val interactor = HomeInteractor(
        api = api,
        watchLaterBookmarkInteractor = watchLaterBookmarkInteractor,
        bookmarkFolders = BookmarkFoldersInteractor(api),
        navigationPreferencesRepository = navigationPreferencesRepository,
        store = store,
        recentlyPlayedOrder = RecentlyPlayedOrder(
            api = api,
            watchState = mockk<WatchStateRepository>(relaxed = true),
        ),
    )

    @Test
    fun theWatchingRowIsServedFromStorageOnASecondCall() = runTest {
        coEvery { api.getWatchingList(onlySubscribed = true) } returns Result.success(
            com.kino.puber.data.api.models.ApiResponseList(items = listOf(item(1)))
        )
        interactor.observeWatchingItems().toList()

        val emissions = interactor.observeWatchingItems().toList()

        assertOnlyValue(listOf(item(1)), isStale = false, emissions = emissions)
        coVerify(exactly = 1) { api.getWatchingList(onlySubscribed = true) }
    }

    @Test
    fun forcingTheWatchingRowServesStorageThenRevalidates() = runTest {
        coEvery { api.getWatchingList(onlySubscribed = true) } returns Result.success(
            com.kino.puber.data.api.models.ApiResponseList(items = listOf(item(1)))
        )
        interactor.observeWatchingItems().toList()

        val emissions = interactor.observeWatchingItems(force = true).toList()

        assertValue(listOf(item(1)), isStale = true, emission = emissions[0])
        assertValue(listOf(item(1)), isStale = false, emission = emissions[1])
        coVerify(exactly = 2) { api.getWatchingList(onlySubscribed = true) }
    }

    /**
     * A domain switch wipes the store and nothing else — this interactor is screen-scoped, so the
     * global that owns the wipe cannot reach in and clear these feeds. The row must still come back
     * from the server rather than out of a memory tier that survived the wipe.
     */
    @Test
    fun wipingTheStoreMakesEveryHomeRowAskTheServerAgain() = runTest {
        allowAnime()
        coEvery { api.getWatchingList(onlySubscribed = true) } returns Result.success(
            com.kino.puber.data.api.models.ApiResponseList(items = listOf(item(1)))
        )
        coEvery { api.getItemsByShortcut("hot", type = "movie") } returns Result.success(page(item(2)))
        coEvery { api.getItemsByShortcut("hot", type = "serial") } returns Result.success(page(item(3)))
        interactor.observeWatchingItems().toList()
        interactor.observeHotItems().toList()

        store.clear()
        interactor.observeWatchingItems().toList()
        interactor.observeHotItems().toList()

        coVerify(exactly = 2) { api.getWatchingList(onlySubscribed = true) }
        coVerify(exactly = 2) { api.getItemsByShortcut("hot", type = "movie") }
    }

    @Test
    fun theWatchingRowAndTheHotRowUseSeparateKeys() = runTest {
        coEvery { api.getWatchingList(onlySubscribed = true) } returns Result.success(
            com.kino.puber.data.api.models.ApiResponseList(items = listOf(item(1)))
        )

        interactor.observeWatchingItems().toList()

        assertEquals(null, store.read("home:hot"))
        assertEquals(true, store.read("home:continue_watching") != null)
    }

    /**
     * Hot merges two single-type requests and sorts by rating before storing — the only real logic
     * this task moved out of `HomeVM`. A naive concatenation, a reversed sort, or a broken null
     * fallback on `ratingPercentage` would all produce a list different from the one asserted here.
     */
    @Test
    fun theHotRowIsTheRatingSortedMergeOfMoviesAndSeries() = runTest {
        allowAnime()
        val midMovie = item(1, ratingPercentage = 10)
        val nullRatingMovie = item(2, ratingPercentage = null)
        val topSeries = item(3, ratingPercentage = 50)
        val negativeSeries = item(4, ratingPercentage = -5)
        coEvery { api.getItemsByShortcut("hot", type = "movie") } returns
            Result.success(page(midMovie, nullRatingMovie))
        coEvery { api.getItemsByShortcut("hot", type = "serial") } returns
            Result.success(page(topSeries, negativeSeries))

        val emissions = interactor.observeHotItems().toList()

        // Plain concatenation would read [midMovie, nullRatingMovie, topSeries, negativeSeries].
        // A rating-descending sort, with the null treated as 0, reads as below instead.
        assertOnlyValue(
            listOf(topSeries, midMovie, nullRatingMovie, negativeSeries),
            isStale = false,
            emissions = emissions,
        )
    }

    /**
     * Fresh merges the same way as Hot but sorts by `updatedAt` descending, with a null falling back
     * to `""` via `orEmpty()` so it sorts behind every real timestamp.
     */
    @Test
    fun theFreshRowIsTheUpdatedAtSortedMergeOfMoviesAndSeries() = runTest {
        allowAnime()
        val midMovie = item(1, updatedAt = "2024-06-01")
        val nullUpdatedMovie = item(2, updatedAt = null)
        val topSeries = item(3, updatedAt = "2024-12-01")
        val oldSeries = item(4, updatedAt = "2020-01-01")
        coEvery { api.getItemsByShortcut("fresh", type = "movie") } returns
            Result.success(page(midMovie, nullUpdatedMovie))
        coEvery { api.getItemsByShortcut("fresh", type = "serial") } returns
            Result.success(page(topSeries, oldSeries))

        val emissions = interactor.observeFreshItems().toList()

        // Plain concatenation would read [midMovie, nullUpdatedMovie, topSeries, oldSeries].
        // An updatedAt-descending sort, with the null falling back to "", reads as below instead.
        assertOnlyValue(
            listOf(topSeries, midMovie, oldSeries, nullUpdatedMovie),
            isStale = false,
            emissions = emissions,
        )
    }

    /**
     * These cases are about what the feed serves and whether it went back to the server, not about
     * the stamp the value carries — which is a real clock here and so cannot be asserted on.
     */
    private fun <T> assertOnlyValue(expected: T, isStale: Boolean, emissions: List<Cached<T>>) {
        assertEquals(1, emissions.size)
        assertValue(expected, isStale, emissions.single())
    }

    private fun <T> assertValue(expected: T, isStale: Boolean, emission: Cached<T>) {
        val value = emission as Cached.Value
        assertEquals(expected, value.value)
        assertEquals(isStale, value.isStale)
    }

    /** Hot and Fresh both go through the discovery passthrough branch, sidestepping anime pagination. */
    private fun allowAnime() {
        every { navigationPreferencesRepository.contentPreferences } returns
            MutableStateFlow(
                ContentPreferences(
                    showCartoonsTab = false,
                    showAnimeTab = false,
                    showAnime = true,
                    hideWatched = false,
                    showWatchedIndicators = true,
                )
            )
    }

    private fun page(vararg items: Item) = PaginatedResponse(
        items = items.toList(),
        pagination = Pagination(current = 1, perpage = items.size, total = 1),
    )

    private fun item(
        id: Int,
        ratingPercentage: Int? = null,
        updatedAt: String? = null,
    ) = Item(
        id = id,
        title = "Item $id",
        type = ItemType.MOVIE,
        ratingPercentage = ratingPercentage,
        updatedAt = updatedAt,
    )

    private class InMemoryPayloadStore : PersistentPayloadStore {
        private val rows = mutableMapOf<String, StoredPayload>()

        override var generation: Long = 0L
            private set

        override suspend fun read(key: String): StoredPayload? = rows[key]

        override suspend fun write(key: String, payload: String, updatedAt: Long) {
            rows[key] = StoredPayload(payload = payload, updatedAt = updatedAt)
        }

        override suspend fun touch(key: String, updatedAt: Long) {
            rows[key]?.let { rows[key] = it.copy(updatedAt = updatedAt) }
        }

        override suspend fun remove(key: String) {
            rows.remove(key)
        }

        override suspend fun removeByPrefix(prefix: String) {
            rows.keys.filter { it.startsWith(prefix) }.forEach(rows::remove)
        }

        override suspend fun clear() {
            generation += 1
            rows.clear()
        }
    }
}
