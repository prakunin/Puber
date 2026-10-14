package com.kino.puber.ui.feature.player.model

import com.kino.puber.R
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.data.api.models.VideoFile
import com.kino.puber.data.api.models.VideoUrl
import com.kino.puber.util.FakeResourceProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

internal class PlayerUIMapperTest {

    private val resources = FakeResourceProvider()
    private val mapper = PlayerUIMapper(resources)

    @Test
    fun mapQualities_excludesFilesWithoutUsableStreamUrls() {
        val files = listOf(
            VideoFile(url = null, quality = "1080p", qualityId = 4),
            VideoFile(
                url = VideoUrl(hls = "https://cdn/720.m3u8"),
                quality = "720p",
                qualityId = 3,
                w = 1280,
                h = 720,
            ),
        )

        assertEquals(
            listOf(
                QualityUIState(0, resources.getString(R.string.player_aspect_auto), null, null, null),
                QualityUIState(1, "720p", 3, 1280, 720),
            ),
            mapper.mapQualities(files),
        )
    }

    @Test
    fun formatTime_matchesPlaybackClockTable() {
        val table = listOf(
            0L to "0:00",
            999L to "0:00",
            1_000L to "0:01",
            59_999L to "0:59",
            60_000L to "1:00",
            3_599_999L to "59:59",
            3_600_000L to "1:00:00",
            36_000_000L to "10:00:00",
            3_661_000L to "1:01:01",
            360_000_000L to "100:00:00",
        )

        table.forEach { (ms, expected) ->
            assertEquals(expected, mapper.formatTime(ms), "ms=$ms")
        }
    }

    @Test
    fun formatTime_keepsAsciiDigitsUnderArabicDefaultLocale() {
        val saved = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
        try {
            assertEquals("1:01:01", mapper.formatTime(3_661_000L))
            assertEquals("1:05", mapper.formatTime(65_000L))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun mapSubtitleTracks_namesLanguagesAiForcedUnknownAndVariants() {
        val tracks = mapper.mapSubtitleTracks(subtitleLinks())
        val off = resources.getString(R.string.player_subtitles_off)
        val variant = { label: String, ordinal: Int ->
            resources.getString(R.string.player_subtitle_variant_label, label, ordinal)
        }

        assertEquals(
            listOf(
                SubtitleTrackUIState(0, off, "", ""),
                SubtitleTrackUIState(1, variant("Russian", 1), "rus", "u1"),
                SubtitleTrackUIState(2, resources.getString(R.string.player_subtitle_ai_generated), "ai", "u2"),
                SubtitleTrackUIState(
                    3,
                    "English · ${resources.getString(R.string.player_subtitle_forced)}",
                    "eng",
                    "u3",
                ),
                SubtitleTrackUIState(4, resources.getString(R.string.player_subtitle_unknown, 4), " ", "u4"),
                SubtitleTrackUIState(5, variant("Russian", 2), "rus", "u5"),
            ),
            tracks,
        )
    }

    @Test
    fun mapSubtitleTracks_namesLanguagesInTheProvidersLocale() {
        resources.locale = Locale.forLanguageTag("ru")

        val labels = mapper.mapSubtitleTracks(
            listOf(SubtitleLink(lang = "eng", url = "u1"), SubtitleLink(lang = "rus", url = "u2")),
        ).map { it.label }

        assertEquals(listOf(resources.getString(R.string.player_subtitles_off), "Английский", "Русский"), labels)
    }

    private fun subtitleLinks() = listOf(
        SubtitleLink(lang = "rus", url = "u1"),
        SubtitleLink(lang = "ai", url = "u2"),
        SubtitleLink(lang = "eng", url = "u3", forced = true),
        SubtitleLink(lang = " ", url = "u4"),
        SubtitleLink(lang = "rus", url = "u5"),
    )
}
