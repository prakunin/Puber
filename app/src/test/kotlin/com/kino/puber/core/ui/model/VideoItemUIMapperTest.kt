package com.kino.puber.core.ui.model

import com.kino.puber.R
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.Country
import com.kino.puber.data.api.models.Duration
import com.kino.puber.data.api.models.Genre
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Posters
import com.kino.puber.data.api.models.Season
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.api.models.WatchingInfo
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoQualityBadge
import com.kino.puber.util.FakeResourceProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class VideoItemUIMapperTest {

    private val mapper = VideoItemUIMapper(FakeResourceProvider())

    // region isWatched mapping

    @Test
    fun mapShortItem_isWatched_true_whenMovieWatched() {
        val item = testItem(type = ItemType.MOVIE, watched = 1, new = null)
        val result = mapper.mapShortItem(item)
        assertEquals(true, result.isWatched)
    }

    @Test
    fun mapShortItem_isWatched_false_whenMovieNotWatched() {
        val item = testItem(type = ItemType.MOVIE, watched = 0, new = null)
        val result = mapper.mapShortItem(item)
        assertEquals(false, result.isWatched)
    }

    @Test
    fun mapShortItem_isWatched_false_whenWatchedNull() {
        val item = testItem(type = ItemType.MOVIE, watched = null, new = null)
        val result = mapper.mapShortItem(item)
        assertEquals(false, result.isWatched)
    }

    @Test
    fun mapShortItem_isWatched_true_whenSeriesAllWatched() {
        // Series with watched > 0 and no new episodes
        val item = testItem(type = ItemType.SERIAL, watched = 10, new = 0)
        val result = mapper.mapShortItem(item)
        assertEquals(true, result.isWatched)
    }

    @Test
    fun mapShortItem_isWatched_false_whenSeriesHasNewEpisodes() {
        val item = testItem(type = ItemType.SERIAL, watched = 8, new = 3)
        val result = mapper.mapShortItem(item)
        assertEquals(false, result.isWatched)
    }

    @Test
    fun mapShortItem_isWatched_true_whenEveryEpisodeOfASeriesWasWatched() {
        // Without `new` the episode count is the only thing left that can settle it.
        val item = testItem(type = ItemType.SERIAL, watched = 10, new = null, total = 10)
        val result = mapper.mapShortItem(item)
        assertEquals(true, result.isWatched)
    }

    @Test
    fun mapShortItem_isWatched_false_forAPartlyWatchedSeriesWithoutNew() {
        // `watched` counts episodes: 3 of 10 says the account started, not that it finished.
        val item = testItem(type = ItemType.SERIAL, watched = 3, new = null, total = 10)
        val result = mapper.mapShortItem(item)
        assertEquals(false, result.isWatched)
    }

    @Test
    fun mapShortItem_isWatched_false_whenWatchedZeroEvenIfNewZero() {
        val item = testItem(type = ItemType.SERIAL, watched = 0, new = 0)
        val result = mapper.mapShortItem(item)
        assertEquals(false, result.isWatched)
    }

    // endregion

    // region watch progress mapping

    @Test
    fun mapShortItem_progress_forPartiallyWatchedSeries() {
        val item = testItem(type = ItemType.SERIAL, watched = 3, new = 7, total = 10)
        assertEquals(0.3f, mapper.mapShortItem(item).progressPercent)
    }

    @Test
    fun mapShortItem_progress_null_forFullyWatchedSeries() {
        val item = testItem(type = ItemType.SERIAL, watched = 10, new = 0, total = 10)
        assertEquals(null, mapper.mapShortItem(item).progressPercent)
    }

    @Test
    fun mapShortItem_progress_null_forUntouchedSeries() {
        val item = testItem(type = ItemType.SERIAL, watched = 0, new = 10, total = 10)
        assertEquals(null, mapper.mapShortItem(item).progressPercent)
    }

    @Test
    fun mapShortItem_progress_null_whenSeriesTotalIsMissing() {
        val item = testItem(type = ItemType.SERIAL, watched = 3, new = 7, total = null)
        assertEquals(null, mapper.mapShortItem(item).progressPercent)
    }

    @Test
    fun mapShortItem_progress_forPartiallyWatchedMovie() {
        val item = testItem(
            type = ItemType.MOVIE,
            videos = listOf(Video(id = 1, duration = 120, watching = WatchingInfo(time = 30))),
        )
        assertEquals(0.25f, mapper.mapShortItem(item).progressPercent)
    }

    @Test
    fun mapShortItem_progress_forMovieWithItemLevelPositionUsesTheVideoLength() {
        val item = testItem(
            type = ItemType.MOVIE,
            watching = WatchingInfo(time = 30),
            videos = listOf(Video(id = 1, duration = 120)),
        )
        assertEquals(0.25f, mapper.mapShortItem(item).progressPercent)
    }

    @Test
    fun mapShortItem_progress_forMovieWithItemLevelPositionFallsBackToTheTotalDuration() {
        val item = testItem(
            type = ItemType.MOVIE,
            watching = WatchingInfo(time = 30),
            duration = Duration(total = 120),
        )
        assertEquals(0.25f, mapper.mapShortItem(item).progressPercent)
    }

    @Test
    fun mapShortItem_progress_null_whenMovieHasNoLengthAnywhere() {
        val item = testItem(type = ItemType.MOVIE, watching = WatchingInfo(time = 30))
        assertEquals(null, mapper.mapShortItem(item).progressPercent)
    }

    @Test
    fun mapShortItem_progress_null_whenMovieHasNoWatchingInfo() {
        val item = testItem(type = ItemType.MOVIE, watched = 0)
        assertEquals(null, mapper.mapShortItem(item).progressPercent)
    }

    @Test
    fun mapShortItem_progress_null_whenMoviesVideoIsWatched() {
        val item = testItem(
            type = ItemType.MOVIE,
            videos = listOf(
                Video(id = 1, duration = 120, watched = 1, watching = WatchingInfo(time = 118)),
            ),
        )
        val mapped = mapper.mapShortItem(item)
        assertEquals(true, mapped.isWatched)
        assertEquals(null, mapped.progressPercent)
    }

    // endregion

    // region basic mapping

    @Test
    fun mapShortItem_mapsTitle() {
        val item = testItem(title = "Breaking Bad")
        assertEquals("Breaking Bad", mapper.mapShortItem(item).title)
    }

    @Test
    fun mapShortItem_mapsUnwatchedCount() {
        val item = testItem(new = 5)
        assertEquals(5, mapper.mapShortItem(item).unwatchedCount)
    }

    @Test
    fun mapShortItem_mapsId() {
        val item = testItem(id = 42)
        assertEquals(42, mapper.mapShortItem(item).id)
    }

    @Test
    fun mapShortItem_mapsYear() {
        val item = testItem(year = 2024)
        assertEquals("2024", mapper.mapShortItem(item).year)
    }

    @Test
    fun mapShortItem_marksMovieSaved_whenBookmarksExist() {
        val item = testItem(type = ItemType.MOVIE, bookmarks = listOf(Bookmark(id = 1, title = "Saved")))
        assertEquals(true, mapper.mapShortItem(item).isSaved)
    }

    @Test
    fun mapShortItem_marksSeriesSaved_whenInWatchlist() {
        val item = testItem(type = ItemType.SERIAL, inWatchlist = true)
        assertEquals(true, mapper.mapShortItem(item).isSaved)
    }

    @Test
    fun mapShortItem_marksSeriesSaved_whenSubscribed() {
        val item = testItem(type = ItemType.SERIAL, subscribed = true)
        assertEquals(true, mapper.mapShortItem(item).isSaved)
    }

    @Test
    fun mapShortItem_doesNotTreatSeriesBookmarkFoldersAsWatchlist() {
        val item = testItem(type = ItemType.SERIAL, bookmarks = listOf(Bookmark(id = 1, title = "Folder")))
        assertEquals(false, mapper.mapShortItem(item).isSaved)
    }

    // endregion

    // region hero mapping

    @Test
    fun mapHeroItems_prefersWidePosterAndFallsBackToBigPosters() {
        val item = testItem(
            posters = Posters(
                wide = "http://wide.example/hero.jpg",
                big = "http://big.example/hero.jpg",
            ),
        )

        val result = mapper.mapHeroItems(listOf(item)).single()

        assertEquals("https://wide.example/hero.jpg", result.wideImageUrl)
        assertEquals("https://big.example/hero.jpg", result.fallbackImageUrl)
        assertEquals(emptyList<String>(), result.fallbackImageUrls)
    }

    @Test
    fun mapHeroItems_mapsMovieMetadata() {
        val item = testItem(
            id = 42,
            title = "Movie",
            year = 2024,
            genres = listOf(Genre(1, "Anime"), Genre(2, "Action")),
            countries = listOf(Country(1, "Japan")),
            duration = Duration(total = 5 * 60),
            kinopoiskRating = "8.1",
            imdbRating = "7.9",
            ratingPercentage = 86,
        )

        val result = mapper.mapHeroItems(listOf(item)).single()

        assertEquals(42, result.id)
        assertEquals("Movie", result.title)
        assertEquals("2024", result.year)
        assertEquals("Anime, Action", result.genres)
        assertEquals("Japan", result.country)
        assertEquals(mapper.buildDuration(item), result.duration)
        assertEquals(3, result.ratings.size)
    }

    @Test
    fun mapHeroItems_mapsSeriesSeasonCount() {
        val item = testItem(
            type = ItemType.SERIAL,
            seasons = listOf(
                Season(id = 1, number = 1),
                Season(id = 2, number = 2),
            ),
        )

        val result = mapper.mapHeroItems(listOf(item)).single()

        assertEquals(
            "string_${R.string.video_details_label_seasons}_2",
            result.duration,
        )
    }

    @Test
    fun mapHeroItems_leavesDurationEmptyForSeriesWithoutSeasons() {
        val item = testItem(
            type = ItemType.SERIAL,
            duration = Duration(total = 5 * 60),
            seasons = null,
        )

        val result = mapper.mapHeroItems(listOf(item)).single()

        assertEquals("", result.duration)
    }

    @Test
    fun mapHeroItems_usesMovieDurationForNonSeriesWithSeasons() {
        val item = testItem(
            type = ItemType.MOVIE,
            duration = Duration(total = 5 * 60),
            seasons = listOf(
                Season(id = 1, number = 1),
                Season(id = 2, number = 2),
            ),
        )

        val result = mapper.mapHeroItems(listOf(item)).single()

        assertEquals(
            "string_${R.string.video_details_label_duration}_" +
                "string_${R.string.duration_minutes_only}_5",
            result.duration,
        )
    }

    // endregion

    // region poster card fields

    @Test
    fun mapShortItem_qualityBadgeUhd_whenQualityIs2160() {
        assertEquals(VideoQualityBadge.UHD, mapper.mapShortItem(testItem(quality = 2160)).qualityBadge)
    }

    @Test
    fun mapShortItem_qualityBadgeHd_whenQualityIs1080() {
        assertEquals(VideoQualityBadge.HD, mapper.mapShortItem(testItem(quality = 1080)).qualityBadge)
    }

    @Test
    fun mapShortItem_qualityBadgeHd_whenQualityIs720() {
        assertEquals(VideoQualityBadge.HD, mapper.mapShortItem(testItem(quality = 720)).qualityBadge)
    }

    @Test
    fun mapShortItem_qualityBadgeNull_whenQualityIs719() {
        assertEquals(null, mapper.mapShortItem(testItem(quality = 719)).qualityBadge)
    }

    @Test
    fun mapShortItem_qualityBadgeNull_whenQualityNull() {
        assertEquals(null, mapper.mapShortItem(testItem(quality = null)).qualityBadge)
    }

    @Test
    fun mapShortItem_originalTitleAndYear_whenTitleHasOriginalAndYear() {
        val result = mapper.mapShortItem(testItem(title = "Рус / Orig", year = 2020))
        assertEquals("Orig", result.originalTitle)
        assertEquals("2020", result.year)
    }

    @Test
    fun mapShortItem_yearOnly_whenTitleHasNoOriginal() {
        val result = mapper.mapShortItem(testItem(title = "Рус", year = 2020))
        assertEquals("", result.originalTitle)
        assertEquals("2020", result.year)
    }

    @Test
    fun mapShortItem_originalOnly_whenYearNull() {
        val result = mapper.mapShortItem(testItem(title = "Рус / Orig", year = null))
        assertEquals("Orig", result.originalTitle)
        assertEquals("", result.year)
    }

    @Test
    fun mapShortItem_noOriginalAndNoYear_whenBothMissing() {
        val result = mapper.mapShortItem(testItem(title = "Рус", year = null))
        assertEquals("", result.originalTitle)
        assertEquals("", result.year)
    }

    @Test
    fun mapShortItem_splitsLocalAndOriginalTitle_whenTitleHasSeparator() {
        val result = mapper.mapShortItem(testItem(title = "Рус / Orig"))
        assertEquals("Рус", result.localTitle)
        assertEquals("Orig", result.originalTitle)
    }

    @Test
    fun mapShortItem_leavesOriginalTitleEmpty_whenTitleHasNoSeparator() {
        val result = mapper.mapShortItem(testItem(title = "Рус"))
        assertEquals("Рус", result.localTitle)
        assertEquals("", result.originalTitle)
    }

    @Test
    fun mapShortItem_keepsBareSlashInLocalTitle() {
        val result = mapper.mapShortItem(testItem(title = "Fate/Zero"))
        assertEquals("Fate/Zero", result.localTitle)
        assertEquals("", result.originalTitle)
    }

    @Test
    fun mapShortItem_keepsRawTitle() {
        assertEquals("Рус / Orig", mapper.mapShortItem(testItem(title = "Рус / Orig")).title)
    }

    @Test
    fun mapShortItem_keepsYearText() {
        assertEquals("2020", mapper.mapShortItem(testItem(year = 2020)).year)
    }

    @Test
    fun mapShortItem_pillRatingsPubWithoutTrailingZero() {
        assertEquals(
            listOf(RatingUIState.PUB("10")),
            mapper.mapShortItem(testItem(ratingPercentage = 100)).pillRatings,
        )
        assertEquals(
            listOf(RatingUIState.PUB("8.3")),
            mapper.mapShortItem(testItem(ratingPercentage = 83)).pillRatings,
        )
    }

    // Guards a future switch to locale-aware number formatting: the pill text must keep the dot.
    @Test
    fun mapShortItem_pillRatingsUseDotDecimal_underRussianLocale() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ru"))
        try {
            assertEquals(
                listOf(RatingUIState.PUB("8.3")),
                mapper.mapShortItem(testItem(ratingPercentage = 83)).pillRatings,
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun mapShortItem_pillRatingsOrderPubKpImdb() {
        val result = mapper.mapShortItem(
            testItem(ratingPercentage = 90, kinopoiskRating = "7.2", imdbRating = "7.1"),
        )
        assertEquals(
            listOf(
                RatingUIState.PUB::class,
                RatingUIState.KP::class,
                RatingUIState.IMDB::class,
            ),
            result.pillRatings.map { it::class },
        )
    }

    @Test
    fun mapShortItem_pillRatingsSkipMissingRatings() {
        val result = mapper.mapShortItem(
            testItem(ratingPercentage = 0, kinopoiskRating = "7.2", imdbRating = ""),
        )
        assertEquals(listOf(RatingUIState.KP("7.2")), result.pillRatings)
    }

    @Test
    fun mapShortItem_pillRatingsRoundKpAndImdbToOneDecimal() {
        val result = mapper.mapShortItem(testItem(kinopoiskRating = "7.43", imdbRating = "6.871"))
        assertEquals(listOf(RatingUIState.KP("7.4"), RatingUIState.IMDB("6.9")), result.pillRatings)
    }

    @Test
    fun mapShortItem_pillRatingsKpAndImdbDropTrailingZero() {
        val result = mapper.mapShortItem(testItem(kinopoiskRating = "5.978", imdbRating = "8.0"))
        assertEquals(listOf(RatingUIState.KP("6"), RatingUIState.IMDB("8")), result.pillRatings)
    }

    @Test
    fun mapShortItem_pillRatingsKpAndImdbUseDotDecimal_underRussianLocale() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ru"))
        try {
            assertEquals(
                listOf(RatingUIState.KP("7.4")),
                mapper.mapShortItem(testItem(kinopoiskRating = "7.43")).pillRatings,
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun mapShortItem_yearEmpty_whenYearIsZero() {
        assertEquals("", mapper.mapShortItem(testItem(year = 0)).year)
    }

    @Test
    fun mapShortItem_usesOriginalAsLocalTitle_whenLocalPartEmpty() {
        val result = mapper.mapShortItem(testItem(title = " / Orig", year = 2020))
        assertEquals("Orig", result.localTitle)
        assertEquals("", result.originalTitle)
    }

    @Test
    fun buildRatings_keepsPubDecimalText() {
        assertEquals(
            listOf(RatingUIState.PUB("10.0")),
            mapper.buildRatings(testItem(ratingPercentage = 100)),
        )
        assertEquals(
            listOf(RatingUIState.PUB("8.3")),
            mapper.buildRatings(testItem(ratingPercentage = 83)),
        )
    }

    @Test
    fun buildRatings_keepsRawKpAndImdbText() {
        assertEquals(
            listOf(RatingUIState.KP("5.978"), RatingUIState.IMDB("7.43")),
            mapper.buildRatings(testItem(kinopoiskRating = "5.978", imdbRating = "7.43")),
        )
    }

    // endregion

    private fun testItem(
        id: Int = 1,
        title: String = "Test",
        type: ItemType = ItemType.MOVIE,
        year: Int? = null,
        quality: Int? = null,
        genres: List<Genre>? = null,
        countries: List<Country>? = null,
        duration: Duration? = null,
        posters: Posters? = null,
        seasons: List<Season>? = null,
        kinopoiskRating: String? = null,
        imdbRating: String? = null,
        ratingPercentage: Int? = null,
        watched: Int? = null,
        new: Int? = null,
        total: Int? = null,
        watching: WatchingInfo? = null,
        subscribed: Boolean? = null,
        inWatchlist: Boolean? = null,
        bookmarks: List<Bookmark>? = null,
        videos: List<Video>? = null,
    ) = Item(
        id = id,
        title = title,
        type = type,
        year = year,
        quality = quality,
        genres = genres,
        countries = countries,
        duration = duration,
        posters = posters,
        seasons = seasons,
        kinopoiskRating = kinopoiskRating,
        imdbRating = imdbRating,
        ratingPercentage = ratingPercentage,
        watched = watched,
        new = new,
        total = total,
        watching = watching,
        subscribed = subscribed,
        inWatchlist = inWatchlist,
        bookmarks = bookmarks,
        videos = videos,
    )
}
