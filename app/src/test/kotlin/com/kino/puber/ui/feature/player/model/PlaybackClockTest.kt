package com.kino.puber.ui.feature.player.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

internal class PlaybackClockTest {

    @Test
    fun formatPlaybackClock_matchesPlaybackClockTable() {
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
            assertEquals(expected, formatPlaybackClock(ms), "ms=$ms")
        }
    }

    @Test
    fun formatPlaybackClock_keepsAsciiDigitsUnderArabicDefaultLocale() {
        val saved = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
        try {
            assertEquals("1:01:01", formatPlaybackClock(3_661_000L))
            assertEquals("1:05", formatPlaybackClock(65_000L))
        } finally {
            Locale.setDefault(saved)
        }
    }
}
