package com.kino.puber.data.repository

import com.kino.puber.data.api.IntroDbAppApiClient
import com.kino.puber.data.api.TheIntroDbApiClient
import com.kino.puber.data.api.TmdbApiClient
import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The two segment sources cover different types for the same episode, so the service has to merge
 * them rather than take whichever answers first.
 */
class SkipSegmentServiceTest {

    private val tmdbApiClient = mockk<TmdbApiClient>()
    private val introDbClient = mockk<TheIntroDbApiClient>()
    private val introDbAppClient = mockk<IntroDbAppApiClient>()
    private val tmdbIdRepository = mockk<TmdbIdRepository>()
    private val segmentRepository = mockk<SkipSegmentRepository>()
    private lateinit var service: SkipSegmentService

    private val intro = SkipSegment(SkipSegmentType.INTRO, startMs = 6_000, endMs = 37_384)
    private val fallbackIntro = SkipSegment(SkipSegmentType.INTRO, startMs = 5_000, endMs = 46_000)
    private val fallbackCredits = SkipSegment(SkipSegmentType.CREDITS, startMs = 792_000, endMs = 959_000)

    @BeforeEach
    fun setup() {
        coEvery { tmdbIdRepository.getTmdbId(IMDB_ID) } returns TMDB_ID
        @Suppress("UNCHECKED_CAST")
        coEvery { segmentRepository.getOrLoad(any(), any(), any(), any()) } coAnswers {
            (arg(3) as suspend () -> List<SkipSegment>).invoke()
        }
        service = SkipSegmentService(
            tmdbApiClient = tmdbApiClient,
            introDbClient = introDbClient,
            introDbAppClient = introDbAppClient,
            tmdbIdRepository = tmdbIdRepository,
            segmentRepository = segmentRepository,
        )
    }

    @Test
    fun fillsTypesThePrimarySourceDoesNotCover() = runTest {
        coEvery { introDbClient.getSegments(TMDB_ID, 1, 1) } returns Result.success(listOf(intro))
        coEvery { introDbAppClient.getSegments(IMDB_ID, 1, 1) } returns
            Result.success(listOf(fallbackIntro, fallbackCredits))

        val result = service.getSegments(IMDB_ID, season = 1, episode = 1)

        // The primary keeps the intro it knows; only the type it is missing comes from the fallback.
        assertEquals(listOf(intro, fallbackCredits), result)
    }

    @Test
    fun doesNotAskTheFallback_whenThePrimaryCoversEveryTypeItCouldAnswerFor() = runTest {
        // No PREVIEW: the fallback cannot supply it, so its absence must not provoke a second call.
        val covered = listOf(SkipSegmentType.INTRO, SkipSegmentType.RECAP, SkipSegmentType.CREDITS)
            .map { type -> SkipSegment(type, startMs = 1_000, endMs = 2_000) }
        coEvery { introDbClient.getSegments(TMDB_ID, 1, 1) } returns Result.success(covered)

        val result = service.getSegments(IMDB_ID, season = 1, episode = 1)

        assertEquals(covered, result)
        coVerify(exactly = 0) { introDbAppClient.getSegments(any(), any(), any()) }
    }

    @Test
    fun takesTheWholeFallback_whenThePrimaryKnowsNothing() = runTest {
        coEvery { introDbClient.getSegments(TMDB_ID, 1, 1) } returns Result.success(emptyList())
        coEvery { introDbAppClient.getSegments(IMDB_ID, 1, 1) } returns
            Result.success(listOf(fallbackIntro, fallbackCredits))

        val result = service.getSegments(IMDB_ID, season = 1, episode = 1)

        assertEquals(listOf(fallbackIntro, fallbackCredits), result)
    }

    private companion object {
        const val IMDB_ID = "33204697"
        const val TMDB_ID = 261579
    }
}
