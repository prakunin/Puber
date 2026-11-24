package com.kino.puber.core.ui.uikit.component.moviesList

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class VideoItemPosterUrlsTest {

    @Test
    fun posterCardImageUrls_startsWithTheStandTier() {
        val state = state(imageUrl = "medium", bigImageUrl = "big", wideImageUrl = "wide")
        assertEquals(listOf("medium", "big"), posterCardImageUrls(state))
    }

    @Test
    fun posterCardImageUrls_neverContainsWideUrl() {
        val state = state(
            imageUrl = "medium",
            bigImageUrl = "big",
            wideImageUrl = "wide",
            imageFallbackUrls = listOf("wide", "other"),
        )
        val urls = posterCardImageUrls(state)
        assertFalse(urls.contains("wide"))
        assertEquals(listOf("medium", "big", "other"), urls)
    }

    @Test
    fun posterCardImageUrls_dropsBlankUrlsAndFallsBack() {
        val state = state(imageUrl = "", bigImageUrl = "", imageFallbackUrls = listOf("a"))
        assertEquals(listOf("a"), posterCardImageUrls(state))
    }

    private fun state(
        imageUrl: String,
        bigImageUrl: String,
        wideImageUrl: String = "",
        imageFallbackUrls: List<String> = emptyList(),
    ) = VideoItemUIState(
        id = 1,
        title = "Title",
        imageUrl = imageUrl,
        bigImageUrl = bigImageUrl,
        wideImageUrl = wideImageUrl,
        imageFallbackUrls = imageFallbackUrls,
    )
}
