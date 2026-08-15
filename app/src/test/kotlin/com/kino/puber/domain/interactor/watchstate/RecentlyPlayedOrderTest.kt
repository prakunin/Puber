package com.kino.puber.domain.interactor.watchstate

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.repository.WatchStateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RecentlyPlayedOrderTest {

    private lateinit var api: KinoPubApiClient
    private lateinit var watchState: WatchStateRepository
    private lateinit var order: RecentlyPlayedOrder

    @BeforeEach
    fun setup() {
        api = mockk()
        watchState = mockk()
        coEvery { watchState.lastWatchedAt() } returns emptyMap()
        order = RecentlyPlayedOrder(api = api, watchState = watchState)
    }

    /**
     * The list means "carry on where you left off", so the title played last belongs at its front.
     * `/watching/serials` returns an order of its own that has nothing to do with when anything was
     * played, which is what this ordering replaces.
     */
    @Test
    fun leadsWithTheTitlePlayedLast() = runTest {
        val playedToday = item(id = 1)
        val playedLastWeek = item(id = 2)
        val neverPlayed = item(id = 3)
        coEvery { api.getHistoryData(1) } returns Result.success(
            historyPage(
                // Two episodes of the same series, so the newest entry has an older one to beat.
                historyEntry(itemId = playedToday.id, lastSeen = 500L),
                historyEntry(itemId = playedToday.id, lastSeen = 1_000L),
            )
        )
        coEvery { watchState.lastWatchedAt() } returns mapOf(playedLastWeek.id to 100L)

        val result = order.sort(listOf(neverPlayed, playedLastWeek, playedToday))

        assertEquals(listOf(playedToday, playedLastWeek, neverPlayed), result)
    }

    /** The history page decides an order, not what the list holds, so losing it must not lose the list. */
    @Test
    fun fallsBackToTheIndexWhenTheHistoryPageFails() = runTest {
        val recent = item(id = 1)
        val older = item(id = 2)
        coEvery { api.getHistoryData(1) } returns Result.failure(IllegalStateException("offline"))
        coEvery { watchState.lastWatchedAt() } returns mapOf(older.id to 100L, recent.id to 200L)

        val result = order.sort(listOf(older, recent))

        assertEquals(listOf(recent, older), result)
    }

    @Test
    fun keepsTheServerOrderForTitlesNeitherSourceCanDate() = runTest {
        val first = item(id = 1)
        val second = item(id = 2)
        val dated = item(id = 3)
        coEvery { api.getHistoryData(1) } returns Result.success(
            historyPage(historyEntry(itemId = dated.id, lastSeen = 10L))
        )

        val result = order.sort(listOf(first, second, dated))

        assertEquals(listOf(dated, first, second), result)
    }

    /** A list of one is already in whatever order it has, so the history page would be paid for nothing. */
    @Test
    fun aListOfOneAsksForNoHistory() = runTest {
        val only = item(id = 1)

        val result = order.sort(listOf(only))

        assertEquals(listOf(only), result)
        coVerify(exactly = 0) { api.getHistoryData(any()) }
        coVerify(exactly = 0) { watchState.lastWatchedAt() }
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
