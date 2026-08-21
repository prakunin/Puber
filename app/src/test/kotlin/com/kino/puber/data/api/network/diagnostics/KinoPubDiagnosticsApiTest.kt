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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class KinoPubDiagnosticsApiTest {

    private val client = mockk<KinoPubApiClient>()
    private val api = KinoPubDiagnosticsApi(client)

    @Test
    fun findMediaProbeTarget_returnsTheProgressiveUrl_whenTheItemOffersOne() = runTest {
        givenCatalogue(itemId = 42)
        givenFiles(fileWith(VideoUrl(http = "https://cdn.test/a.mp4")))

        assertEquals(
            MediaProbeTarget.Progressive("https://cdn.test/a.mp4"),
            api.findMediaProbeTarget(),
        )
    }

    /** The reason `firstNotNullOfOrNull` is there: the first file need not be the one with a URL. */
    @Test
    fun findMediaProbeTarget_looksPastAFileWithoutAUrl() = runTest {
        givenCatalogue(itemId = 42)
        givenFiles(
            VideoFile(url = null),
            fileWith(VideoUrl(hls4 = "https://cdn.test/a.m3u8")),
            fileWith(VideoUrl(http = "https://cdn.test/b.mp4")),
        )

        assertEquals(
            MediaProbeTarget.Progressive("https://cdn.test/b.mp4"),
            api.findMediaProbeTarget(),
        )
    }

    /**
     * An account served only HLS is the whole reason this is not [MediaProbeTarget.Unavailable]:
     * the row it produces names the setting that would make the measurement possible.
     */
    @Test
    fun findMediaProbeTarget_saysNoProgressiveStream_whenOnlyHlsIsOnOffer() = runTest {
        givenCatalogue(itemId = 42)
        givenFiles(fileWith(VideoUrl(hls4 = "https://cdn.test/a.m3u8")))

        assertEquals(MediaProbeTarget.NoProgressiveStream, api.findMediaProbeTarget())
    }

    @Test
    fun findMediaProbeTarget_saysNoProgressiveStream_whenEveryFileHasANullUrl() = runTest {
        givenCatalogue(itemId = 42)
        givenFiles(VideoFile(url = null))

        assertEquals(MediaProbeTarget.NoProgressiveStream, api.findMediaProbeTarget())
    }

    @Test
    fun findMediaProbeTarget_isUnavailable_whenTheCatalogueIsEmpty() = runTest {
        coEvery { client.getItems(type = any(), sort = any(), page = any()) } returns
            Result.success(PaginatedResponse(items = emptyList(), pagination = pagination()))

        assertEquals(MediaProbeTarget.Unavailable, api.findMediaProbeTarget())
    }

    @Test
    fun findMediaProbeTarget_isUnavailable_whenTheCatalogueRequestFails() = runTest {
        coEvery { client.getItems(type = any(), sort = any(), page = any()) } returns
            Result.failure(IllegalStateException("offline"))

        assertEquals(MediaProbeTarget.Unavailable, api.findMediaProbeTarget())
    }

    @Test
    fun findMediaProbeTarget_isUnavailable_whenTheFilesRequestFails() = runTest {
        givenCatalogue(itemId = 42)
        coEvery { client.getItemFiles(42) } returns Result.failure(IllegalStateException("offline"))

        assertEquals(MediaProbeTarget.Unavailable, api.findMediaProbeTarget())
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

    private fun givenFiles(vararg files: VideoFile) {
        coEvery { client.getItemFiles(42) } returns
            Result.success(ItemFiles(id = 42, files = files.toList()))
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
