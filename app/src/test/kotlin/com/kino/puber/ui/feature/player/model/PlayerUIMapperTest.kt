package com.kino.puber.ui.feature.player.model

import android.content.Context
import com.kino.puber.R
import com.kino.puber.data.api.models.VideoFile
import com.kino.puber.data.api.models.VideoUrl
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

internal class PlayerUIMapperTest {

    private val context = mockk<Context> {
        every { getString(R.string.player_aspect_auto) } returns "Auto"
    }
    private val mapper = PlayerUIMapper(context)

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
                QualityUIState(0, "Auto", null, null, null),
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
}
