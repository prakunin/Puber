package com.kino.puber.data.repository

import com.kino.puber.data.api.IntroDbAppApiClient
import com.kino.puber.data.api.TheIntroDbApiClient
import com.kino.puber.data.api.TmdbApiClient
import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType

class SkipSegmentService(
    private val tmdbApiClient: TmdbApiClient,
    private val introDbClient: TheIntroDbApiClient,
    private val introDbAppClient: IntroDbAppApiClient,
    private val tmdbIdRepository: TmdbIdRepository,
    private val segmentRepository: SkipSegmentRepository,
) {

    /**
     * The two sources are merged per segment type rather than used first-wins.
     *
     * They disagree about coverage, not just about numbers: TheIntroDB may know a show's intro and
     * nothing else, while IntroDB.app has its outro. Returning the first non-empty answer threw the
     * other source's types away, which is why series with perfectly good credits data behaved as if
     * they had none. TheIntroDB stays authoritative for any type it does answer for.
     */
    suspend fun getSegments(imdbId: String, season: Int?, episode: Int?): List<SkipSegment> {
        return segmentRepository.getOrLoad(imdbId, season, episode) {
            val primary = tryTheIntroDB(imdbId, season, episode)
            val missingTypes = FALLBACK_TYPES - primary.mapTo(mutableSetOf()) { it.type }
            if (missingTypes.isEmpty()) {
                return@getOrLoad primary
            }
            val fallback = introDbAppClient.getSegments(imdbId, season, episode)
                .getOrDefault(emptyList())
                .filter { it.type in missingTypes }
            primary + fallback
        }
    }

    private suspend fun tryTheIntroDB(imdbId: String, season: Int?, episode: Int?): List<SkipSegment> {
        val tmdbId = resolveTmdbId(imdbId) ?: return emptyList()
        return introDbClient.getSegments(tmdbId, season, episode).getOrDefault(emptyList())
    }

    private companion object {
        /**
         * What the fallback can actually answer for. PREVIEW is not in it, so a primary that covers
         * these three is complete as far as the second source is concerned and it is left alone.
         */
        val FALLBACK_TYPES = setOf(
            SkipSegmentType.INTRO,
            SkipSegmentType.RECAP,
            SkipSegmentType.CREDITS,
        )
    }

    private suspend fun resolveTmdbId(imdbId: String): Int? {
        val cachedTmdbId = tmdbIdRepository.getTmdbId(imdbId)
        if (cachedTmdbId != null) {
            return cachedTmdbId
        }

        return tmdbApiClient.findByImdbId(imdbId).getOrNull()?.also { tmdbId ->
            tmdbIdRepository.saveTmdbId(imdbId, tmdbId)
        }
    }
}
