package com.kino.puber.domain.interactor.favorites

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.ApiResponseList
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.domain.interactor.watchstate.RecentlyPlayedOrder
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FavoritesInteractorTest {

    private lateinit var api: KinoPubApiClient
    private lateinit var watchState: WatchStateRepository
    private lateinit var interactor: FavoritesInteractor

    @BeforeEach
    fun setup() {
        api = mockk()
        watchState = mockk()
        coEvery { watchState.lastWatchedAt() } returns emptyMap()
        interactor = FavoritesInteractor(
            api = api,
            itemDetailsRepository = mockk<ItemDetailsRepository>(relaxed = true),
            // The real ordering, because what is under test is the watchlist arriving ordered — a
            // mocked one would only assert that the interactor calls something.
            recentlyPlayedOrder = RecentlyPlayedOrder(api = api, watchState = watchState),
        )
    }

    /**
     * The screen behind this list is the one the account opens to carry on watching, so it leads with
     * the title played last for the same reason the home row does.
     */
    @Test
    fun watchlistLeadsWithTheTitlePlayedLast() = runTest {
        val playedToday = item(id = 1)
        val playedLastWeek = item(id = 2)
        val neverPlayed = item(id = 3)
        coEvery { api.getWatchingList(onlySubscribed = true) } returns Result.success(
            ApiResponseList(items = listOf(neverPlayed, playedLastWeek, playedToday))
        )
        coEvery { api.getHistoryData(1) } returns Result.success(
            historyPage(historyEntry(itemId = playedToday.id, lastSeen = 1_000L))
        )
        coEvery { watchState.lastWatchedAt() } returns mapOf(playedLastWeek.id to 100L)

        val result = interactor.getWatchlist()

        assertEquals(listOf(playedToday, playedLastWeek, neverPlayed), result)
    }

    private fun historyPage(vararg entries: History) = PaginatedResponse(
        items = entries.toList(),
        pagination = Pagination(current = 1, perpage = entries.size, total = 1),
    )

    private fun historyEntry(itemId: Int, lastSeen: Long) = History(
        item = item(id = itemId),
        updated = lastSeen.toString(),
    )

    private fun item(id: Int) = Item(id = id, title = "Item $id", type = ItemType.SERIAL)
}
