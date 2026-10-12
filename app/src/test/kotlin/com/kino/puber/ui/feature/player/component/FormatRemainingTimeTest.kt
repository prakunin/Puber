package com.kino.puber.ui.feature.player.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FormatRemainingTimeTest {

    @Test
    fun prefixesRemainingTimeWithMinus() {
        assertEquals("-0:07", formatRemainingTime(currentPosition = 3_000L, duration = 10_000L))
    }

    @Test
    fun zeroDurationShowsEmptyRemainingTime() {
        assertEquals("-0:00", formatRemainingTime(currentPosition = 5_000L, duration = 0L))
    }

    @Test
    fun positionPastDurationClampsToZero() {
        assertEquals("-0:00", formatRemainingTime(currentPosition = 5_000L, duration = 3_000L))
    }

    @Test
    fun remainingOverOneHourIncludesHours() {
        assertEquals("-1:06:40", formatRemainingTime(currentPosition = 0L, duration = 4_000_000L))
    }
}
