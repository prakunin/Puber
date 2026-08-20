package com.kino.puber.domain.interactor.trailer

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Trailer
import com.kino.puber.data.api.models.TrailerLinksResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TrailerLinkInteractorTest {

    private val api = mockk<KinoPubApiClient>()
    private val interactor = TrailerLinkInteractor(api)

    @Test
    fun `a signed link in the payload is used as it stands`() = runTest {
        val item = item(Trailer(url = "https://cdn/trailer.m3u8"))

        assertEquals("https://cdn/trailer.m3u8", interactor.resolve(item))
        coVerify(exactly = 0) { api.getTrailerLinks(any()) }
    }

    @Test
    fun `a bare path is exchanged for a signed link`() = runTest {
        coEvery { api.getTrailerLinks(42) } returns Result.success(
            TrailerLinksResponse(
                status = 200,
                trailer = listOf(Trailer(id = 42, url = "https://cdn/signed.m3u8")),
            )
        )

        assertEquals("https://cdn/signed.m3u8", interactor.resolve(item(Trailer(file = "/trailers/d/02/x.mp4"))))
    }

    @Test
    fun `the first playable rendition wins`() = runTest {
        coEvery { api.getTrailerLinks(42) } returns Result.success(
            TrailerLinksResponse(
                trailer = listOf(
                    Trailer(id = 42, file = "/trailers/d/02/x.mp4"),
                    Trailer(id = 42, url = "https://cdn/second.m3u8"),
                )
            )
        )

        assertEquals("https://cdn/second.m3u8", interactor.resolve(item(Trailer(file = "/trailers/d/02/x.mp4"))))
    }

    @Test
    fun `an item without a trailer is never asked about`() = runTest {
        assertNull(interactor.resolve(item(trailer = null)))
        coVerify(exactly = 0) { api.getTrailerLinks(any()) }
    }

    @Test
    fun `a failed request leaves the item without a trailer`() = runTest {
        coEvery { api.getTrailerLinks(42) } returns Result.failure(IllegalStateException("404"))

        assertNull(interactor.resolve(item(Trailer(file = "/trailers/d/02/x.mp4"))))
    }

    @Test
    fun `an answer with nothing playable in it leaves the item without a trailer`() = runTest {
        coEvery { api.getTrailerLinks(42) } returns Result.success(
            TrailerLinksResponse(trailer = listOf(Trailer(id = 42, file = "/trailers/d/02/x.mp4")))
        )

        assertNull(interactor.resolve(item(Trailer(file = "/trailers/d/02/x.mp4"))))
    }

    private fun item(trailer: Trailer?) = Item(
        id = 42,
        title = "Item",
        type = ItemType.MOVIE,
        trailer = trailer,
    )
}
