package com.kino.puber.profile

import com.kino.puber.BuildConfig
import com.kino.puber.data.api.history.HistoryPageResponse
import com.kino.puber.data.api.models.ApiResponse
import com.kino.puber.data.api.models.ApiResponseList
import com.kino.puber.data.api.models.BookmarkFoldersResponse
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.isFullyWatched
import com.kino.puber.data.api.models.isSeriesLike
import com.kino.puber.data.repository.AppVersion
import com.kino.puber.data.repository.WatchCompletionPolicy
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BaselineFixtureContentTest {

    private val origin = "https://127.0.0.1:${BuildConfig.BASELINE_MOCK_PORT}"
    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val fixtureDirectory: File = listOf(
        File("../baselineprofile/src/main/assets/baseline"),
        File("baselineprofile/src/main/assets/baseline"),
    ).first { it.isDirectory }

    private val fixtures: Map<String, String> = fixtureDirectory
        .listFiles { file -> file.extension == "json" }
        .orEmpty()
        .associate { it.name to it.readText() }

    private fun parsed(name: String): JsonElement = Json.parseToJsonElement(
        fixtures.getValue(name).replace("{{ID}}", "1").replace("{{MOCK}}", origin)
    )

    private fun JsonElement.strings(): List<String> = when (this) {
        is JsonPrimitive -> if (isString) listOf(content) else emptyList()
        is JsonArray -> flatMap { it.strings() }
        is JsonObject -> values.flatMap { it.strings() }
    }

    private fun JsonElement.keys(): Set<String> = when (this) {
        is JsonPrimitive -> emptySet()
        is JsonArray -> flatMapTo(mutableSetOf()) { it.keys() }
        is JsonObject -> keys + values.flatMap { it.keys() }
    }

    private fun decodedItem(): Item =
        lenientJson.decodeFromString<ApiResponse<Item>>(
            fixtures.getValue("item.json").replace("{{ID}}", "1").replace("{{MOCK}}", origin)
        ).item!!

    private fun decodedSerial(): Item =
        lenientJson.decodeFromString<ApiResponse<Item>>(
            fixtures.getValue("item_serial.json").replace("{{MOCK}}", origin)
        ).item!!

    private fun decodedItems(): List<Item> =
        lenientJson.decodeFromString<ApiResponseList<Item>>(
            fixtures.getValue("items.json").replace("{{MOCK}}", origin)
        ).items.orEmpty()

    @Test
    fun itemFixtures_decodeAsAppItems() {
        assertTrue(decodedItems().isNotEmpty())
        assertNotNull(decodedItem())
    }

    @Test
    fun itemFixtures_carryWatchedAndBookmarkedMovie() {
        assertTrue(decodedItems().drop(1).any { it.isFullyWatched() })
        assertEquals(true, decodedSerial().inWatchlist)
    }

    @Test
    fun itemFixture_isUnwatchedAndUnbookmarked_soDetailsAndPlaybackTakeTheFolderLookup() {
        val item = decodedItem()

        assertFalse(item.isFullyWatched())
        assertTrue(item.inWatchlist != true)
        assertTrue(item.bookmarks.orEmpty().isEmpty())
        assertTrue(item.videos.orEmpty().none { (it.watched ?: 0) > 0 })
    }

    @Test
    fun bookmarkFoldersFixture_listsTheWatchLaterFolder_soTheLookupFillsTheBookmarkIcon() {
        val response = lenientJson.decodeFromString<BookmarkFoldersResponse>(
            fixtures.getValue("bookmark_folders.json")
        )

        assertTrue(response.folders.isNotEmpty())
    }

    @Test
    fun itemFixtures_carryBooleanFloatAndDoubleValues() {
        val item = decodedItem()

        assertNotNull(item.advert)
        assertNotNull(item.subscribed)
        assertNotNull(item.poorQuality)
        assertNotNull(item.finished)
        assertNotNull(item.fps)
        assertNotNull(item.duration?.average)
    }

    @Test
    fun itemFixture_carriesExplicitNull() {
        val item = parsed("item.json").jsonObject.getValue("item").jsonObject

        assertTrue(item.values.any { it is JsonNull })
    }

    @Test
    fun fixtures_haveNoUrlOutsideMockOrigin() {
        assertTrue(fixtures.isNotEmpty(), "no fixtures found in $fixtureDirectory")
        val strays = fixtures.keys.flatMap { name ->
            parsed(name).strings()
                .filter { it.contains("://") && !it.startsWith(origin) }
                .map { "$name: $it" }
        }

        assertEquals(emptyList<String>(), strays)
    }

    @Test
    fun fixtures_haveNoImdbOrTrailerKeys_seasonsAndSubtitlesOnlyInSerialFixture() {
        assertTrue(fixtures.isNotEmpty(), "no fixtures found in $fixtureDirectory")
        val banned = setOf("imdb", "trailer")
        val seriesOnly = setOf("seasons", "subtitles")
        val offenders = fixtures.keys.flatMap { name ->
            parsed(name).keys()
                .filter { it in banned || (it in seriesOnly && name != "item_serial.json") }
                .map { "$name: $it" }
        }

        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun serialFixture_decodesAsSeriesWithTwoEpisodesOnMockOrigin() {
        val item = decodedSerial()

        assertTrue(item.type.isSeriesLike())
        assertEquals(true, item.inWatchlist)
        val season = item.seasons.orEmpty().single()
        val episodes = season.episodes.orEmpty()
        assertEquals(2, episodes.size)
        episodes.forEach { assertEquals("$origin/media/stream.m3u8", it.files!![0].url?.hls4) }
        assertTrue(episodes.any { ep -> ep.audios.orEmpty().any { it.type != null && it.author != null } })
        assertTrue(episodes.any { it.files!![0].translation != null })
        assertTrue(episodes.any { it.subtitles!![0].url.startsWith(origin) })
    }

    @Test
    fun movieFixture_carriesNoTrackData() {
        val keys = parsed("item.json").keys()
        assertTrue(keys.none { it in setOf("audios", "subtitles", "translation") }, "keys $keys")
    }

    @Test
    fun itemsFixture_secondEntryIsSeries() {
        val second = decodedItems().single { it.id == 2 }

        assertTrue(second.type.isSeriesLike())
        assertTrue((second.new ?: 0) > 0)
    }

    @Test
    fun itemFixtures_carryUnmodelledKey() {
        val modelled = Item.serializer().descriptor.elementNames.toSet()
        val objects = parsed("items.json").jsonObject.getValue("items").jsonArray.map { it.jsonObject } +
            parsed("item.json").jsonObject.getValue("item").jsonObject +
            parsed("item_serial.json").jsonObject.getValue("item").jsonObject

        objects.forEach { assertTrue(it.keys.any { key -> key !in modelled }, "no unmodelled key in $it") }
    }

    @Test
    fun detailsFixtures_carryGenresCountriesAndTracklist() {
        listOf(decodedItem(), decodedSerial()).forEach {
            assertTrue(it.genres.orEmpty().isNotEmpty())
            assertTrue(it.countries.orEmpty().isNotEmpty())
            assertTrue(it.tracklist.orEmpty().isNotEmpty())
        }
    }

    @Test
    fun movieFixtures_carryPostersAndHlsStream() {
        val listed = listOf("items.json", "similar.json", "bookmark_items.json").flatMap {
            parsed(it).jsonObject.getValue("items").jsonArray
        }
        val detail = parsed("item.json").jsonObject.getValue("item").jsonObject
        val serial = parsed("item_serial.json").jsonObject.getValue("item").jsonObject

        (listed + detail + serial).forEach { movie ->
            val posters = movie.jsonObject.getValue("posters").jsonObject
            assertEquals("$origin/media/poster.jpg", posters.getValue("medium").jsonPrimitive.content)
            assertEquals("$origin/media/wide.jpg", posters.getValue("wide").jsonPrimitive.content)
        }
        val hls = detail.getValue("videos").jsonArray.first().jsonObject
            .getValue("files").jsonArray.first().jsonObject
            .getValue("url").jsonObject.getValue("hls4").jsonPrimitive.content
        assertEquals("$origin/media/stream.m3u8", hls)
        serial.getValue("seasons").jsonArray.flatMap { it.jsonObject.getValue("episodes").jsonArray }.forEach {
            assertEquals("$origin/media/wide.jpg", it.jsonObject.getValue("thumbnail").jsonPrimitive.content)
        }
    }

    @Test
    fun historyFixture_carriesOneUnfinishedMovieWithHttpsWidePoster() {
        val body = fixtures.getValue("history.json").replace("{{MOCK}}", origin)

        val entries = lenientJson
            .decodeFromString<HistoryPageResponse>(body).toModel().items

        val entry = entries.single()
        assertEquals(ItemType.MOVIE, entry.item.type)
        assertEquals("$origin/media/wide.jpg", entry.item.posters?.wide)
        assertFalse(WatchCompletionPolicy.isFinished(entry.time, entry.video?.duration))
    }

    @Test
    fun githubRelease_isOlderThanAnyBuild() {
        val tag = parsed("github_release.json").jsonObject.getValue("tag_name").jsonPrimitive.content

        val version = AppVersion.parse(tag)

        assertNotNull(version)
        assertTrue(version!! <= AppVersion(0, 0, 1), "release $version")
        assertTrue(version < AppVersion.parse(BuildConfig.VERSION_NAME)!!, "build ${BuildConfig.VERSION_NAME}")
    }
}
