package com.kino.puber.domain.interactor.player

import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.data.repository.SkipSegmentService

class SkipSegmentInteractor(
    private val service: SkipSegmentService,
    private val preferences: PlayerPreferencesRepository,
) {

    suspend fun loadSegments(item: Item, season: Int?, episode: Int?): List<SkipSegment> {
        val imdbId = item.imdb
        if (imdbId == null) {
            return emptyList()
        }
        return service.getSegments(imdbId, season, episode)
    }

    /**
     * @param leadInMs how long before a segment starts it already counts as active, so the prompt
     * can be up and counting by the time the segment itself is on screen.
     */
    fun findActiveSegment(
        segments: List<SkipSegment>,
        positionMs: Long,
        leadInMs: Long = 0L,
    ): SkipSegment? {
        return segments.firstOrNull { segment ->
            isSegmentTypeEnabled(segment.type) &&
                positionMs >= segment.startMs - leadInMs &&
                positionMs <= (segment.endMs ?: Long.MAX_VALUE)
        }
    }

    // No settings check here: credits segment is used for next-episode timing
    // regardless of skip-credits toggle. The skip overlay visibility is controlled
    // by findActiveSegment() which does check isSegmentTypeEnabled().
    fun findCreditsSegment(segments: List<SkipSegment>): SkipSegment? {
        return segments.firstOrNull { it.type == SkipSegmentType.CREDITS }
    }

    fun isSegmentTypeEnabled(type: SkipSegmentType): Boolean {
        return when (type) {
            SkipSegmentType.INTRO -> preferences.skipIntroEnabled
            SkipSegmentType.RECAP -> preferences.skipRecapEnabled
            SkipSegmentType.CREDITS -> preferences.skipCreditsEnabled
            SkipSegmentType.PREVIEW -> preferences.skipIntroEnabled
        }
    }
}
