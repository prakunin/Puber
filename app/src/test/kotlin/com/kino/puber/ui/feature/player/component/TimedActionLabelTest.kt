package com.kino.puber.ui.feature.player.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TimedActionLabelTest {

    /** Figure space: exactly the width of a digit. */
    private val pad = ' '

    @Test
    fun timedActionDigits_printsTheSecondsLeft() {
        assertEquals("5", timedActionDigits(countdown = 5, totalSeconds = 7))
    }

    @Test
    fun timedActionDigits_keepsTheColumnAsWideAsTheTotal() {
        assertEquals("${pad}4", timedActionDigits(countdown = 4, totalSeconds = 12))
        assertEquals("12", timedActionDigits(countdown = 12, totalSeconds = 12))
    }

    @Test
    fun timedActionDigits_showsZeroForTheLastTick() {
        assertEquals("0", timedActionDigits(countdown = 0, totalSeconds = 7))
    }

    @Test
    fun timedActionDigits_clampsACountdownOutsideItsTotal() {
        assertEquals("0", timedActionDigits(countdown = -3, totalSeconds = 7))
        assertEquals("7", timedActionDigits(countdown = 9, totalSeconds = 7))
    }

    @Test
    fun timedActionDigits_isZeroWithNothingToCount() {
        assertEquals("0", timedActionDigits(countdown = 3, totalSeconds = 0))
    }

    /** Seven seconds are eight ticks of the bar: the count, and the one zero is held for. */
    @Test
    fun timedActionSecondsLeft_readsTheWholeCountOffAnUnstartedBar() {
        assertEquals(7, timedActionSecondsLeft(progress = 0f, totalSeconds = 7))
    }

    @Test
    fun timedActionSecondsLeft_flipsExactlyOnTheTickBoundary() {
        assertEquals(7, timedActionSecondsLeft(progress = 0.9f / 8, totalSeconds = 7))
        assertEquals(6, timedActionSecondsLeft(progress = 1f / 8, totalSeconds = 7))
        assertEquals(6, timedActionSecondsLeft(progress = 1.5f / 8, totalSeconds = 7))
    }

    /** A total whose ticks are not exact in float must not come out a frame late. */
    @Test
    fun timedActionSecondsLeft_survivesInexactTickBoundaries() {
        assertEquals(5, timedActionSecondsLeft(progress = 1f / 7, totalSeconds = 6))
        assertEquals(10, timedActionSecondsLeft(progress = 2f / 13, totalSeconds = 12))
    }

    @Test
    fun timedActionSecondsLeft_holdsZeroForTheLastTickAndAfter() {
        assertEquals(0, timedActionSecondsLeft(progress = 7f / 8, totalSeconds = 7))
        assertEquals(0, timedActionSecondsLeft(progress = 1f, totalSeconds = 7))
        assertEquals(0, timedActionSecondsLeft(progress = 1.2f, totalSeconds = 7))
    }

    @Test
    fun timedActionSecondsLeft_isZeroWithNothingToCount() {
        assertEquals(0, timedActionSecondsLeft(progress = 0f, totalSeconds = 0))
    }
}
