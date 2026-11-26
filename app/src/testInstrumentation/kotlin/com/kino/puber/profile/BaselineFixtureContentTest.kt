package com.kino.puber.profile

import com.kino.puber.BuildConfig
import com.kino.puber.data.api.history.HistoryPageResponse
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.repository.AppVersion
import com.kino.puber.data.repository.WatchCompletionPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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

    private val origin = "https://127.0.0.1:18765"
    private val lenientJson = Json { ignoreUnknownKeys = true }

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
    fun fixtures_haveNoImdbTrailerSubtitlesOrSeasonsKeys() {
        assertTrue(fixtures.isNotEmpty(), "no fixtures found in $fixtureDirectory")
        val banned = setOf("imdb", "trailer", "subtitles", "seasons")
        val offenders = fixtures.keys.flatMap { name ->
            parsed(name).keys().filter { it in banned }.map { "$name: $it" }
        }

        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun movieFixtures_carryPostersAndHlsStream() {
        val listed = listOf("items.json", "similar.json", "bookmark_items.json").flatMap {
            parsed(it).jsonObject.getValue("items").jsonArray
        }
        val detail = parsed("item.json").jsonObject.getValue("item").jsonObject

        (listed + detail).forEach { movie ->
            val posters = movie.jsonObject.getValue("posters").jsonObject
            assertEquals("$origin/media/poster.jpg", posters.getValue("medium").jsonPrimitive.content)
            assertEquals("$origin/media/wide.jpg", posters.getValue("wide").jsonPrimitive.content)
        }
        val hls = detail.getValue("videos").jsonArray.first().jsonObject
            .getValue("files").jsonArray.first().jsonObject
            .getValue("url").jsonObject.getValue("hls4").jsonPrimitive.content
        assertEquals("$origin/media/stream.m3u8", hls)
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
