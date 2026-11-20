package com.kino.puber.core.ui.uikit.component.details

import android.content.Context
import coil3.compose.useExistingImageAsPlaceholder
import coil3.decode.DataSource
import coil3.transition.Transition
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import coil3.request.transitionFactory
import coil3.transition.CrossfadeTransition
import coil3.transition.TransitionTarget
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class VideoDetailsPosterStateTest {

    @Test
    fun heldPosterUrls_returnsSamePreviousInstance_whenLoadingWithEmptyUrls() {
        val previous = listOf("a", "a2")

        val result = heldPosterUrls(previous, emptyList(), isLoading = true)

        assertSame(previous, result)
    }

    @Test
    fun heldPosterUrls_returnsCurrent_whenNewCardArrivesDuringLoading() {
        val current = listOf("b")

        val result = heldPosterUrls(listOf("a"), current, isLoading = true)

        assertSame(current, result)
    }

    @Test
    fun heldPosterUrls_returnsCurrent_whenLoadedCardHasNoPosters() {
        val current = emptyList<String>()

        val result = heldPosterUrls(listOf("a"), current, isLoading = false)

        assertSame(current, result)
    }

    @Test
    fun heldPosterUrls_returnsCurrent_whenNothingHeldYet() {
        val current = emptyList<String>()

        val result = heldPosterUrls(null, current, isLoading = true)

        assertSame(current, result)
    }

    @Test
    fun heldPosterUrls_returnsCurrent_whenHoldExpired() {
        val current = emptyList<String>()

        val result = heldPosterUrls(listOf("a"), current, isLoading = true, holdExpired = true)

        assertSame(current, result)
    }

    @Test
    fun posterFadeTarget_isOne_whenTrailerNotRendered() {
        assertEquals(1F, posterFadeTarget(hasTrailer = true, trailerRendered = false))
    }

    @Test
    fun posterFadeTarget_isOne_whenNoTrailer() {
        assertEquals(1F, posterFadeTarget(hasTrailer = false, trailerRendered = true))
    }

    @Test
    fun posterRequest_usesCrossfadeAndExistingPlaceholder_whenUrlPresent() {
        val request = posterRequest(context, "https://example.test/p.jpg")

        assertSame(PosterCrossfade, request?.transitionFactory)
        assertTrue(request?.useExistingImageAsPlaceholder == true)
    }

    @Test
    fun posterRequest_hasNoTransition_whenCrossfadeOff() {
        val request = posterRequest(context, "https://example.test/p.jpg", crossfade = false)

        assertSame(Transition.Factory.NONE, request?.transitionFactory)
    }

    @Test
    fun cardEnterAlpha_isOne_whenTrailerRendered() {
        assertEquals(1F, cardEnterAlpha(hasTrailer = true, trailerRendered = true, enter = 0.3F))
    }

    @Test
    fun cardEnterAlpha_followsEnter_whenNoTrailer() {
        assertEquals(0.3F, cardEnterAlpha(hasTrailer = false, trailerRendered = false, enter = 0.3F))
    }

    @Test
    fun cardEnterAlpha_followsEnter_whenTrailerNotRendered() {
        assertEquals(0.3F, cardEnterAlpha(hasTrailer = true, trailerRendered = false, enter = 0.3F))
    }

    @Test
    fun posterRequest_isNull_whenUrlMissingOrEmpty() {
        assertEquals(null, posterRequest(context, null))
        assertEquals(null, posterRequest(context, ""))
    }

    @Test
    fun posterStill_staysOptedIntoSkeletonOnlyUntilFirstImage() {
        assertTrue(POSTER_SKELETON_ONLY_UNTIL_FIRST_IMAGE)
    }

    @Test
    fun posterStillAlpha_isOpaque_whenTrailerGoneButFadeStillZero() {
        assertEquals(1F, posterStillAlpha(hasTrailer = false, trailerRendered = false, fade = 0F))
    }

    @Test
    fun posterStillAlpha_isOpaque_whenTrailerNotRenderedYet() {
        assertEquals(1F, posterStillAlpha(hasTrailer = true, trailerRendered = false, fade = 0F))
    }

    @Test
    fun posterFadeTarget_isZero_whenTrailerRendered() {
        assertEquals(0F, posterFadeTarget(hasTrailer = true, trailerRendered = true))
    }

    @Test
    fun posterStillAlpha_followsFade_whenTrailerRendered() {
        assertEquals(0.4F, posterStillAlpha(hasTrailer = true, trailerRendered = true, fade = 0.4F))
    }

    @Test
    fun posterCrossfade_returnsCrossfade_whenResultFromMemoryCache() {
        val transition = PosterCrossfade.create(target, successResult(DataSource.MEMORY_CACHE))

        assertTrue(transition is CrossfadeTransition)
        assertEquals(200, (transition as CrossfadeTransition).durationMillis)
    }

    @Test
    fun posterCrossfade_returnsCrossfade_whenResultFromNetwork() {
        val transition = PosterCrossfade.create(target, successResult(DataSource.NETWORK))

        assertTrue(transition is CrossfadeTransition)
    }

    @Test
    fun posterCrossfade_returnsNone_whenResultIsError() {
        val error = mockk<ErrorResult>()

        val transition = PosterCrossfade.create(target, error)

        assertEquals(Transition.Factory.NONE.create(target, error)::class, transition::class)
        assertFalse(transition is CrossfadeTransition)
    }

    @Test
    fun heldDescription_returnsPrevious_whenLoadingAfterLoaded() {
        val previous = loadedDetails(1)

        val result = heldDescription(previous, VideoDetailsUIState.Loading)

        assertSame(previous, result)
    }

    @Test
    fun heldDescription_returnsCurrent_whenNewDetailsLoaded() {
        val current = loadedDetails(2)

        val result = heldDescription(loadedDetails(1), current)

        assertSame(current, result)
    }

    @Test
    fun heldDescription_returnsCurrent_whenNothingHeldYet() {
        val current = VideoDetailsUIState.Loading.copy()

        val result = heldDescription(null, current)

        assertSame(current, result)
    }

    @Test
    fun heldDescription_returnsCurrent_whenHoldExpired() {
        val current = VideoDetailsUIState.Loading.copy()

        val result = heldDescription(loadedDetails(1), current, holdExpired = true)

        assertSame(current, result)
    }

    private fun loadedDetails(id: Int) = VideoDetailsUIState.Loading.copy(id = id, isLoading = false)

    private val context = mockk<Context>(relaxed = true)

    private val target = mockk<TransitionTarget>(relaxed = true)

    private fun successResult(source: DataSource): SuccessResult =
        mockk<SuccessResult> { every { dataSource } returns source }
}
