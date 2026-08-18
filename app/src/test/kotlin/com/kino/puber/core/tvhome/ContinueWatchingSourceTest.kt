package com.kino.puber.core.tvhome

import com.kino.puber.core.contentlink.ContentTarget
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.api.models.Posters
import com.kino.puber.data.api.models.Video
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ContinueWatchingSourceTest {
    private val api = mockk<KinoPubApiClient>()
    private val source = ContinueWatchingSource(api, clock = { NOW })

    @Test
    fun loadPublishesNewestEligibleRowPerItem() = runTest {
        coEvery { api.getHistoryData(1) } returns Result.success(
            page(
                history(itemId = 1, videoNumber = 3, time = 20),
                history(itemId = 1, videoNumber = 2, time = 10),
                history(itemId = 2, videoNumber = 1, time = 95),
                history(itemId = 3, videoNumber = 1, time = 10, artwork = "http://image/3.jpg"),
                history(itemId = 4, videoNumber = 5, time = 40, season = 2, type = ItemType.SERIAL),
            ),
        )

        val programs = source.load().getOrThrow()

        assertEquals(listOf(1, 4), programs.map { it.target.itemId })
        assertEquals(ContentTarget.Playback(itemId = 1, videoNumber = 3), programs[0].target)
        assertEquals(ContentTarget.Playback(itemId = 4, seasonNumber = 2, episodeNumber = 5), programs[1].target)
        assertEquals(20_000L, programs[0].positionMs)
        assertEquals(NOW, programs[0].lastEngagementTimeMs)
    }

    @Test
    fun loadKeepsFailureSoPublishedRowsAreNotReconciledAsEmpty() = runTest {
        coEvery { api.getHistoryData(1) } returns Result.failure(IllegalStateException("offline"))

        assertTrue(source.load().isFailure)
    }

    private fun history(
        itemId: Int,
        videoNumber: Int,
        time: Int,
        artwork: String = "https://image/$itemId.jpg",
        season: Int? = null,
        type: ItemType = ItemType.MOVIE,
    ) = History(
        item = Item(
            id = itemId,
            title = "Item $itemId",
            type = type,
            posters = Posters(wide = artwork),
        ),
        video = Video(id = itemId * 10, number = videoNumber, duration = 100),
        season = season,
        time = time,
    )

    private fun page(vararg histories: History) = PaginatedResponse(
        items = histories.toList(),
        pagination = Pagination(current = 1, perpage = 20, total = 1),
    )

    private companion object {
        const val NOW = 1_000_000L
    }
}
