package com.kino.puber.data.cache

import com.kino.puber.data.api.models.Audio
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.Country
import com.kino.puber.data.api.models.Duration
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Genre
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Posters
import com.kino.puber.data.api.models.Season
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.data.api.models.Tracklist
import com.kino.puber.data.api.models.Trailer
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.api.models.VideoFile
import com.kino.puber.data.api.models.WatchingInfo
import com.kino.puber.util.FakePayloadStore
import java.lang.reflect.Modifier
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The merge that keeps a sparse list response from erasing what the details endpoint loaded names
 * every field by hand. A field added to [Item] without a line there is dropped on every list merge,
 * silently and everywhere, which is exactly the kind of thing no other test would notice.
 *
 * So the coverage is asserted as behaviour: an item with every field set, merged over a record that
 * has none of them, must come back whole. The second test is what makes the first one honest — it
 * fails when the fixture stops setting a field [Item] declares, which is the same moment a new
 * field appears.
 */
class ItemMergeCoverageTest {

    private val store = FakePayloadStore()
    private val subject = ContentCacheRepository(store = store, clock = { NOW })

    @Test
    fun everyFieldOfAnItemSurvivesAMergeOverASparseRecord() = runTest {
        subject.mergeItems(listOf(SPARSE))

        val merged = subject.mergeItems(listOf(FULL)).single()

        assertEquals(FULL, merged)
    }

    @Test
    fun theFixtureSetsEveryFieldItemDeclares() {
        val unset = Item::class.java.declaredFields
            .filterNot { field -> field.isSynthetic || Modifier.isStatic(field.modifiers) }
            .filter { field ->
                field.isAccessible = true
                field.get(FULL) == null
            }
            .map { field -> field.name }

        assertEquals(emptyList<String>(), unset, "Set these on the fixture, then cover them in mergeSparse")
    }

    private companion object {
        const val NOW = 1_000_000L

        /** What a list endpoint returns: an identity and nothing else. */
        val SPARSE = Item(id = 7, title = "Sparse", type = ItemType.MOVIE)

        val FULL = Item(
            id = 7,
            title = "Full",
            type = ItemType.SERIAL,
            year = 2024,
            rating = "8.1",
            genres = listOf(Genre(id = 1, title = "Drama")),
            countries = listOf(Country(id = 2, title = "France")),
            director = "Director",
            cast = "Cast",
            plot = "Plot",
            duration = Duration(average = 45.0, total = 2700),
            posters = Posters(small = "s", medium = "m", big = "b", wide = "w"),
            trailer = Trailer(id = 3, url = "u", file = "f", quality = "1080p"),
            quality = 1080,
            ac3 = 1,
            advert = true,
            subscribed = true,
            inWatchlist = true,
            imdb = "tt1234567",
            imdbRating = "8.2",
            imdbVotes = 1_000,
            kinopoisk = "9999",
            kinopoiskRating = "7.9",
            kinopoiskVotes = 500,
            langs = "ru,en",
            poorQuality = true,
            ratingPercentage = 81,
            ratingVotes = 42,
            subtype = "multi",
            tracklist = listOf(Tracklist(artists = "Artist", title = "Track", url = "url")),
            updatedAt = "2024-01-02",
            createdAt = "2024-01-01",
            views = 12_345,
            voice = "Voice",
            finished = true,
            comments = 7,
            seasons = listOf(
                Season(id = 11, number = 1, title = "Season 1", episodes = listOf(Episode(id = 21, number = 1))),
            ),
            videos = listOf(
                Video(
                    id = 31,
                    number = 1,
                    title = "Episode",
                    thumbnail = "thumb",
                    duration = 2700,
                    tracks = 2,
                    ac3 = 1,
                    audios = listOf(Audio(id = 51, index = 1)),
                    watched = 1,
                    watching = WatchingInfo(time = 10, duration = 20, status = 1),
                    subtitles = listOf(SubtitleLink(lang = "rus", url = "sub")),
                    files = listOf(VideoFile(quality = "1080p")),
                ),
            ),
            bookmarks = listOf(Bookmark(id = 41, title = "Folder", count = 1, createdAt = "2024-01-03")),
            total = 10,
            watched = 1,
            new = 2,
            watching = WatchingInfo(time = 30, duration = 60, status = 1, updatedAt = "2024-01-04"),
            fps = 23.976f,
            ageRating = "18+",
        )
    }
}
