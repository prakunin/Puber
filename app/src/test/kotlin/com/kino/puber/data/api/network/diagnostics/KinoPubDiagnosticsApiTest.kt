package com.kino.puber.data.api.network.diagnostics

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.ItemFiles
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.api.models.VideoFile
import com.kino.puber.data.api.models.VideoUrl
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class KinoPubDiagnosticsApiTest {

    private val client = mockk<KinoPubApiClient>()
    private val api = KinoPubDiagnosticsApi(client)

    @Test
    fun findProgressiveMediaUrl_returnsTheProgressiveUrl_whenTheItemOffersOne() = runTest {
        givenCatalogue(itemId = 42)
        coEvery { client.getItemFiles(42) } returns Result.success(
            ItemFiles(id = 42, files = listOf(fileWith(VideoUrl(http = "https://cdn.test/a.mp4"))))
        )

        assertEquals("https://cdn.test/a.mp4", api.findProgressiveMediaUrl())
    }

    /**
     * An item that only offers HLS is a fact about the item, not about the network — the caller
     * turns null into a skipped step rather than a failed one.
     */
    @Test
    fun findProgressiveMediaUrl_returnsNull_whenOnlyHlsIsOnOffer() = runTest {
        givenCatalogue(itemId = 42)
        coEvery { client.getItemFiles(42) } returns Result.success(
            ItemFiles(id = 42, files = listOf(fileWith(VideoUrl(hls4 = "https://cdn.test/a.m3u8"))))
        )

        assertNull(api.findProgressiveMediaUrl())
    }

    @Test
    fun findProgressiveMediaUrl_returnsNull_whenTheCatalogueIsEmpty() = runTest {
        coEvery { client.getItems(type = any(), sort = any(), page = any()) } returns
            Result.success(PaginatedResponse(items = emptyList(), pagination = pagination()))

        assertNull(api.findProgressiveMediaUrl())
    }

    @Test
    fun findProgressiveMediaUrl_returnsNull_whenTheCatalogueRequestFails() = runTest {
        coEvery { client.getItems(type = any(), sort = any(), page = any()) } returns
            Result.failure(IllegalStateException("offline"))

        assertNull(api.findProgressiveMediaUrl())
    }

    @Test
    fun loadCataloguePage_isTrue_whenThePageArrives() = runTest {
        givenCatalogue(itemId = 42)

        assertTrue(api.loadCataloguePage())
    }

    @Test
    fun loadCataloguePage_isFalse_whenTheRequestFails() = runTest {
        coEvery { client.getItems(type = any(), sort = any(), page = any()) } returns
            Result.failure(IllegalStateException("offline"))

        assertFalse(api.loadCataloguePage())
    }

    private fun givenCatalogue(itemId: Int) {
        coEvery { client.getItems(type = any(), sort = any(), page = any()) } returns
            Result.success(
                PaginatedResponse(items = listOf(item(itemId)), pagination = pagination())
            )
    }

    private fun item(id: Int) = Item(id = id, title = "Item $id", type = ItemType.MOVIE)

    private fun pagination() = Pagination(total = 1, current = 1, perpage = 1, totalItems = 1)

    private fun fileWith(url: VideoUrl) = VideoFile(url = url)
}
