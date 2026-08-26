package com.kino.puber.ui.feature.details.model

import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.R
import com.kino.puber.data.api.models.Audio
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Genre
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
    fun `chips carry the shape of the thing and the facts line what is inside it`() {
        val item = Item(
            id = 1,
            title = "Фильм / Movie",
            type = ItemType.MOVIE,
            // The year and the genres belong to the meta line. They are set so the assertions
            // that they stay out have something to catch: without them the item could not have
            // produced either string, and the check would pass on emptiness alone.
            year = 2026,
            genres = listOf(Genre(id = 1, title = "Комедия")),
            videos = listOf(Video(id = 1, files = listOf(VideoFile(quality = "1080")))),
            ac3 = 1,
            ageRating = "16+",
            voice = "Дубляж",
        )

        val mapped = mapper.map(item, isInWatchlist = false)
        val chips = mapped.info.chips
        val facts = mapped.info.factsLine

        // What the thing is goes to the chips.
        assertTrue(chips.contains("1080")) { chips.toString() }
        assertTrue(chips.contains("16+")) { chips.toString() }
        // What is inside it goes to the line below, read only once the choice is made.
        assertTrue(facts.contains("Дубляж")) { facts }
        assertFalse(facts.contains("1080")) { facts }
        assertFalse(facts.contains("16+")) { facts }
        assertFalse(facts.contains("2026")) { facts }
        assertFalse(facts.contains("Комедия")) { facts }
        // ...and the year and the genres went to the meta line.
        assertTrue(mapped.details.year.contains("2026")) { mapped.details.year }
        assertTrue(mapped.details.genres.contains("Комедия")) { mapped.details.genres }
    }

    @Test
    fun `an item with nothing to state has an empty facts line`() {
        val item = Item(id = 1, title = "Фильм", type = ItemType.MOVIE)

        assertEquals("", mapper.map(item, isInWatchlist = false).info.factsLine)
    }

    @Test
    fun `the director and the cast get a line each`() {
        val item = Item(
            id = 1,
            title = "Фильм",
            type = ItemType.MOVIE,
            director = "Иван Иванов",
            cast = "А Актёр, Б Актёр",
        )

        val info = mapper.map(item, isInWatchlist = false).info

        assertTrue(info.directorLine.contains("Иван Иванов")) { info.directorLine }
        assertTrue(info.castLine.contains("А Актёр")) { info.castLine }
        assertTrue(info.castLine.contains("Б Актёр")) { info.castLine }
        // Both used to share one single-line string, which truncated the cast at the first
        // name -- so it was never actually shown.
        assertFalse(info.castLine.contains("Иван Иванов")) { info.castLine }
    }

    @Test
    fun `a missing director leaves the cast line alone`() {
        val item = Item(id = 1, title = "Фильм", type = ItemType.MOVIE, cast = "А Актёр")

        val info = mapper.map(item, isInWatchlist = false).info

        assertEquals("", info.directorLine)
        assertTrue(info.castLine.contains("А Актёр")) { info.castLine }
    }

    @Test
    fun `a series states its seasons where a film states its duration`() {
        val series = Item(
            id = 1,
            title = "Сериал",
            type = ItemType.SERIAL,
            seasons = listOf(Season(id = 1, number = 1, episodes = emptyList())),
        )

        val mapped = mapper.map(series, isInWatchlist = false)

        assertTrue(mapped.details.duration.isNotBlank()) { mapped.details.duration }
        assertFalse(mapped.info.factsLine.contains(mapped.details.duration)) { mapped.info.factsLine }
    }

    @Test
    fun `an item with no facts and no credits maps to empty lines`() {
        val bare = Item(id = 1, title = "Фильм", type = ItemType.MOVIE)

        val info = mapper.map(bare, isInWatchlist = false).info

        assertEquals("", info.factsLine)
        assertEquals("", info.directorLine)
        assertEquals("", info.castLine)
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

    @Test
    fun map_chips_carrySeriesStatusOnlyWhenKnownAndOnlyForSeries() {
        val finished = "string_${R.string.video_details_series_status_finished}"
        val ongoing = "string_${R.string.video_details_series_status_ongoing}"

        assertTrue(mapper.map(series(trailer = null, finished = true)).info.chips.contains(finished))
        assertTrue(mapper.map(series(trailer = null, finished = false)).info.chips.contains(ongoing))

        val unknown = mapper.map(series(trailer = null, finished = null)).info.chips
        assertFalse(unknown.contains(finished)) { unknown.toString() }
        assertFalse(unknown.contains(ongoing)) { unknown.toString() }

        val film = mapper.map(movie(trailer = null, finished = true)).info.chips
        assertFalse(film.contains(finished)) { film.toString() }
    }

    @Test
    fun map_chips_openWithTheKindOfThing() {
        assertEquals(
            "string_${R.string.video_details_chip_series}",
            mapper.map(series(trailer = null)).info.chips.first(),
        )
        assertEquals(
            "string_${R.string.video_details_chip_movie}",
            mapper.map(movie(trailer = null)).info.chips.first(),
        )
    }

    @Test
    fun map_seasons_areSeparateAndOpenOnTheOneTheViewerReturnsTo() {
        val state = mapper.map(
            series(
                trailer = null,
                seasons = listOf(
                    Season(id = 1, number = 1, episodes = listOf(Episode(id = 101, number = 1, watched = 1))),
                    Season(id = 2, number = 2, episodes = listOf(Episode(id = 201, number = 1))),
                ),
            )
        )

        assertEquals(listOf(1, 2), state.seasons.map { season -> season.number })
        assertEquals(listOf(101), state.seasons.first().episodes.map { episode -> episode.id })
        assertEquals(2, state.selectedSeasonNumber)
        assertEquals(2, state.selectedSeason?.number)
    }

    @Test
    fun map_movie_hasNoSeasons() {
        assertEquals(emptyList<DetailsSeasonUIState>(), mapper.map(movie(trailer = null)).seasons)
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
        cast: String? = null,
        finished: Boolean? = null,
    ): Item {
        return Item(
            id = 1,
            title = "Movie",
            type = ItemType.MOVIE,
            trailer = trailer,
            videos = videos,
            cast = cast,
            finished = finished,
        )
    }

    private fun series(
        trailer: Trailer?,
        seasons: List<Season>? = null,
        finished: Boolean? = null,
    ): Item {
        return Item(
            id = 2,
            title = "Series",
            type = ItemType.SERIAL,
            trailer = trailer,
            seasons = seasons,
            finished = finished,
        )
    }
}
