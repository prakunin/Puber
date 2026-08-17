package com.kino.puber.domain.interactor.favorites

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.ApiResponseList
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.cache.Cached
import com.kino.puber.data.cache.ContentPageCache
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.domain.interactor.watchstate.RecentlyPlayedOrder
import com.kino.puber.util.FakePayloadStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FavoritesInteractorTest {

    private lateinit var api: KinoPubApiClient
    private lateinit var watchState: WatchStateRepository
    private lateinit var interactor: FavoritesInteractor
    private val store = FakePayloadStore()
    private val now = 1_000_000L

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
            contentPageCache = ContentPageCache(store = store, clock = { now }),
        )
    }

    /**
     * Stored sorted, the recently-played order would freeze at write time — which is the one thing
     * this list exists to express.
     */
    @Test
    fun theWatchlistIsCachedUnsortedSoTheOrderIsRecomputedOnEveryRead() = runTest {
        coEvery { api.getWatchingList(onlySubscribed = true) } returns
            Result.success(watchingListOf(item(1), item(2)))
        val order = mockk<RecentlyPlayedOrder>()
        coEvery { order.sort(any()) } answers { firstArg<List<Item>>().sortedByDescending(Item::id) }
        val subject = FavoritesInteractor(
            api = api,
            itemDetailsRepository = mockk(relaxed = true),
            recentlyPlayedOrder = order,
            contentPageCache = ContentPageCache(store = store, clock = { now }),
        )

        subject.observeWatchlist().toList()
        val emissions = subject.observeWatchlist().toList()

        assertEquals(listOf(1, 2), (emissions.single() as Cached.Value).value.map(Item::id))
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
        coEvery { api.getHistoryData(1) } returns Result.success(
            historyPage(historyEntry(itemId = playedToday.id, lastSeen = 1_000L))
        )
        coEvery { watchState.lastWatchedAt() } returns mapOf(playedLastWeek.id to 100L)

        val result = interactor.sortByRecentlyPlayed(listOf(neverPlayed, playedLastWeek, playedToday))

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

    private fun watchingListOf(vararg items: Item) = ApiResponseList(items = items.toList())
}
