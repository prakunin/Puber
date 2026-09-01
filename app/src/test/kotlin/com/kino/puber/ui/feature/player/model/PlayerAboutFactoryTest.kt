package com.kino.puber.ui.feature.player.model

import com.kino.puber.data.api.models.Country
import com.kino.puber.data.api.models.Duration
import com.kino.puber.data.api.models.Genre
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PlayerAboutFactoryTest {

    @Test
    fun build_laysEveryKnownFactOutInReadingOrder() {
        val about = PlayerAboutFactory.build(
            item = movie(
                year = 2010,
                countries = listOf("США", "Великобритания"),
                ageRating = "16+",
                genres = listOf("Фантастика", "Боевик"),
                imdbRating = "8.8",
                kinopoiskRating = "8.7",
                plot = "Кобб — талантливый вор.",
                director = "Кристофер Нолан",
                cast = "Леонардо ДиКаприо, Джозеф Гордон-Левитт",
            ),
            durationLabel = "2 ч 28 мин",
            imdbLabel = "IMDb",
            kinopoiskLabel = "Кинопоиск",
        )

        assertEquals(
            PlayerAboutUIState(
                title = "Начало",
                metaLine = "2010 · 2 ч 28 мин · США, Великобритания · 16+",
                genresLine = "Фантастика, Боевик",
                ratingsLine = "IMDb 8.8 · Кинопоиск 8.7",
                description = "Кобб — талантливый вор.",
                director = "Кристофер Нолан",
                cast = "Леонардо ДиКаприо, Джозеф Гордон-Левитт",
            ),
            about,
        )
        assertFalse(about.isEmpty)
    }

    @Test
    fun build_dropsMissingFactsRatherThanLeavingTheSeparatorsBehind() {
        val about = PlayerAboutFactory.build(
            item = movie(year = 2010, countries = emptyList(), ageRating = null),
            durationLabel = null,
            imdbLabel = "IMDb",
            kinopoiskLabel = "Кинопоиск",
        )

        assertEquals("2010", about.metaLine)
    }

    @Test
    fun build_trimsTheStrayCommasAndBlanksTheApiSendsInsideCastAndGenres() {
        val about = PlayerAboutFactory.build(
            item = movie(
                genres = listOf(" Драма ", "  "),
                cast = "Том Хэнкс,  , Робин Райт ,",
                director = "  ",
            ),
            durationLabel = null,
            imdbLabel = "IMDb",
            kinopoiskLabel = "Кинопоиск",
        )

        assertEquals("Драма", about.genresLine)
        assertEquals("Том Хэнкс, Робин Райт", about.cast)
        assertNull(about.director)
    }

    @Test
    fun build_ignoresRatingsThatAreZeroOrNotNumbers() {
        val about = PlayerAboutFactory.build(
            item = movie(imdbRating = "0", kinopoiskRating = "null"),
            durationLabel = null,
            imdbLabel = "IMDb",
            kinopoiskLabel = "Кинопоиск",
        )

        assertEquals("", about.ratingsLine)
    }

    @Test
    fun build_isEmptyWhenTheTitleIsAllTheApiGaveUs() {
        val about = PlayerAboutFactory.build(
            item = movie(),
            durationLabel = null,
            imdbLabel = "IMDb",
            kinopoiskLabel = "Кинопоиск",
        )

        assertEquals("Начало", about.title)
        assertTrue(about.isEmpty)
    }

    @Test
    fun durationSeconds_prefersWhatIsActuallyPlaying() {
        assertEquals(
            1500,
            PlayerAboutFactory.durationSeconds(
                item = movie(totalDurationSeconds = 8880),
                resolvedDurationSeconds = 1500,
            ),
        )
    }

    @Test
    fun durationSeconds_fallsBackToTheItemOnAFilm() {
        assertEquals(
            8880,
            PlayerAboutFactory.durationSeconds(
                item = movie(totalDurationSeconds = 8880),
                resolvedDurationSeconds = null,
            ),
        )
    }

    /** A series carries the run time of every episode added together; in the player that reads as a lie. */
    @Test
    fun durationSeconds_neverShowsASeriesTotalAsTheEpisodeLength() {
        assertNull(
            PlayerAboutFactory.durationSeconds(
                item = movie(totalDurationSeconds = 172_800).copy(type = ItemType.SERIAL),
                resolvedDurationSeconds = null,
            ),
        )
    }

    @Test
    fun durationSeconds_treatsAZeroReadingAsNoReading() {
        assertNull(
            PlayerAboutFactory.durationSeconds(
                item = movie(totalDurationSeconds = 0),
                resolvedDurationSeconds = 0,
            ),
        )
    }

    @Suppress("LongParameterList")
    private fun movie(
        year: Int? = null,
        countries: List<String> = emptyList(),
        ageRating: String? = null,
        genres: List<String> = emptyList(),
        imdbRating: String? = null,
        kinopoiskRating: String? = null,
        plot: String? = null,
        director: String? = null,
        cast: String? = null,
        totalDurationSeconds: Int? = null,
    ) = Item(
        id = 1,
        title = "Начало",
        type = ItemType.MOVIE,
        year = year,
        genres = genres.mapIndexed { index, title -> Genre(index, title) },
        countries = countries.mapIndexed { index, title -> Country(index, title) },
        director = director,
        cast = cast,
        plot = plot,
        duration = totalDurationSeconds?.let { Duration(total = it) },
        imdbRating = imdbRating,
        kinopoiskRating = kinopoiskRating,
        ageRating = ageRating,
    )
}
