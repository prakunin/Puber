package com.kino.puber.ui.feature.details.model

import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.R
import com.kino.puber.data.api.models.Audio
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Season
import com.kino.puber.data.api.models.Trailer
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.api.models.VideoFile
import com.kino.puber.util.FakeResourceProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DetailsScreenUIMapperTest {

    private val resources = FakeResourceProvider()
    private val mapper = DetailsScreenUIMapper(
        resources = resources,
        itemMapper = VideoItemUIMapper(FakeResourceProvider()),
    )

    @Test
    fun map_movieButtons_includeTrailerWatchlistAndWatchedActions() {
        val state = mapper.map(movie(trailer = Trailer(url = "https://trailer")))

        assertEquals(1, state.buttons.count<DetailsButtonUIState.TextButton>(DetailsAction.PlayClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.TextButton>(DetailsAction.TrailerClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.WatchlistToggle>(DetailsAction.WatchlistToggleClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.WatchedToggle>(DetailsAction.WatchedToggleClicked))
    }

    @Test
    fun map_seriesButtons_doNotIncludeWatchedActionOrDuplicateTrailerAction() {
        val state = mapper.map(series(trailer = Trailer(url = "https://trailer")))

        assertEquals(1, state.buttons.count<DetailsButtonUIState.TextButton>(DetailsAction.PlayClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.TextButton>(DetailsAction.SelectSeasonClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.IconOnly>(DetailsAction.TrailerClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.WatchlistToggle>(DetailsAction.WatchlistToggleClicked))
        assertEquals(0, state.buttons.count<DetailsButtonUIState.WatchedToggle>(DetailsAction.WatchedToggleClicked))
    }

    @Test
    fun mapSimilarItems_enablesTitlesForRelatedCards() {
        val items = mapper.mapSimilarItems(listOf(movie(trailer = null)))

        assertEquals(true, items.single().showTitle)
    }

    @Test
    fun map_movieInfo_usesPlayableVideoAudioCount() {
        val state = mapper.map(
            movie(
                trailer = null,
                videos = listOf(Video(id = 1, audios = listOf(audio("rus"), audio("eng")))),
            )
        )

        assertTrue(state.info.factsLine.contains(resources.getString(R.string.video_details_facts_audio_tracks, 2)))
    }

    @Test
    fun map_seriesInfo_usesFirstUnwatchedEpisodeAudioCount() {
        val state = mapper.map(
            series(
                trailer = null,
                seasons = listOf(
                    Season(
                        id = 1,
                        number = 1,
                        episodes = listOf(
                            Episode(id = 1, number = 1, watched = 1, audios = listOf(audio("rus"))),
                            Episode(id = 2, number = 2, watched = 0, audios = listOf(audio("rus"), audio("eng"))),
                        ),
                    )
                ),
            )
        )

        assertTrue(state.info.factsLine.contains(resources.getString(R.string.video_details_facts_audio_tracks, 2)))
    }

    @Test
    fun `the facts line carries what the meta line does not`() {
        val item = Item(
            id = 1,
            title = "Фильм / Movie",
            type = ItemType.MOVIE,
            videos = listOf(Video(id = 1, files = listOf(VideoFile(quality = "1080")))),
            ac3 = 1,
            ageRating = "16+",
            voice = "Дубляж",
        )

        val facts = mapper.map(item, isInWatchlist = false).info.factsLine

        assertTrue(facts.contains("1080")) { facts }
        assertTrue(facts.contains("16+")) { facts }
        assertTrue(facts.contains("Дубляж")) { facts }
        // The meta line already carries these; they must not be repeated here.
        assertFalse(facts.contains("2026")) { facts }
    }

    @Test
    fun `an item with nothing to state has an empty facts line`() {
        val item = Item(id = 1, title = "Фильм", type = ItemType.MOVIE)

        assertEquals("", mapper.map(item, isInWatchlist = false).info.factsLine)
    }

    @Test
    fun `the credits line names the director and the cast`() {
        val item = Item(
            id = 1,
            title = "Фильм",
            type = ItemType.MOVIE,
            director = "Иван Иванов",
            cast = "А Актёр, Б Актёр",
        )

        val credits = mapper.map(item, isInWatchlist = false).info.creditsLine

        assertTrue(credits.contains("Иван Иванов")) { credits }
        assertTrue(credits.contains("А Актёр")) { credits }
    }

    @Test
    fun `a missing director leaves no dangling separator`() {
        val item = Item(id = 1, title = "Фильм", type = ItemType.MOVIE, cast = "А Актёр")

        val credits = mapper.map(item, isInWatchlist = false).info.creditsLine

        assertFalse(credits.startsWith(" · ")) { credits }
        assertFalse(credits.endsWith(" · ")) { credits }
    }

    @Test
    fun map_initialEpisodeSelectsExactEpisodeForPanelFocus() {
        val state = mapper.map(
            item = series(
                trailer = null,
                seasons = listOf(
                    Season(
                        id = 1,
                        number = 1,
                        episodes = listOf(Episode(id = 101, number = 1)),
                    ),
                    Season(
                        id = 2,
                        number = 2,
                        episodes = listOf(
                            Episode(id = 201, number = 1),
                            Episode(id = 204, number = 4),
                        ),
                    ),
                ),
            ),
            isInWatchlist = false,
            initialEpisode = DetailsEpisodeTarget(
                seasonNumber = 2,
                episodeNumber = 4,
            ),
        )

        assertEquals(204, state.currentEpisode?.id)
        assertEquals(204, state.initialEpisodeFocusId)
    }

    private inline fun <reified T : DetailsButtonUIState> List<DetailsButtonUIState>.count(
        action: DetailsAction,
    ): Int {
        return filterIsInstance<T>().count { button ->
            when (button) {
                is DetailsButtonUIState.TextButton -> button.action == action
                is DetailsButtonUIState.IconOnly -> button.action == action
                is DetailsButtonUIState.WatchlistToggle -> button.action == action
                is DetailsButtonUIState.WatchedToggle -> button.action == action
            }
        }
    }

    private fun audio(lang: String): Audio {
        return Audio(id = lang.hashCode(), lang = lang)
    }

    private fun movie(
        trailer: Trailer?,
        videos: List<Video>? = null,
    ): Item {
        return Item(id = 1, title = "Movie", type = ItemType.MOVIE, trailer = trailer, videos = videos)
    }

    private fun series(
        trailer: Trailer?,
        seasons: List<Season>? = null,
    ): Item {
        return Item(id = 2, title = "Series", type = ItemType.SERIAL, trailer = trailer, seasons = seasons)
    }
}
