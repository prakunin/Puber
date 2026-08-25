package com.kino.puber.ui.feature.player.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TimedActionLabelTest {

    /** Non-breaking, so the number never wraps away from the label it counts for. */
    private val gap = '\u00A0'

    /** Figure space: exactly the width of a digit. */
    private val pad = '\u2007'

    @Test
    fun timedActionLabel_putsTheCountdownAfterTheLabel() {
        assertEquals(
            "Skip intro${gap}5",
            timedActionLabel("Skip intro", countdown = 5, totalSeconds = 7),
        )
    }

    @Test
    fun timedActionLabel_keepsTheColumnAsWideAsTheTotal() {
        assertEquals(
            "Skip intro$gap${pad}4",
            timedActionLabel("Skip intro", countdown = 4, totalSeconds = 12),
        )
        assertEquals(
            "Skip intro${gap}12",
            timedActionLabel("Skip intro", countdown = 12, totalSeconds = 12),
        )
    }

    @Test
    fun timedActionLabel_showsZeroForTheLastTick() {
        assertEquals(
            "Skip intro${gap}0",
            timedActionLabel("Skip intro", countdown = 0, totalSeconds = 7),
        )
    }

    @Test
    fun timedActionLabel_clampsACountdownOutsideItsTotal() {
        assertEquals(
            "Skip intro${gap}0",
            timedActionLabel("Skip intro", countdown = -3, totalSeconds = 7),
        )
        assertEquals(
            "Skip intro${gap}7",
            timedActionLabel("Skip intro", countdown = 9, totalSeconds = 7),
        )
    }

    @Test
    fun timedActionLabel_leavesTheLabelAloneWhenThereIsNoCountdown() {
        assertEquals(
            "Skip intro",
            timedActionLabel("Skip intro", countdown = 3, totalSeconds = 0),
        )
    }
}
