package com.kino.puber.ui.feature.main.component

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ExitConfirmationTest {

    private var now = 10_000L
    private val confirmation = ExitConfirmation(nowMillis = { now })

    @Test
    fun `a fresh confirmation is not armed`() {
        // Regression: the never-armed sentinel is Long.MIN_VALUE, and subtracting it overflows to a
        // negative age, which read as "armed a moment ago" and sent the very first Back press
        // straight to the launcher.
        assertFalse(confirmation.isArmed)
    }

    @Test
    fun `arming holds within the window`() {
        confirmation.arm()

        now += 2_999L

        assertTrue(confirmation.isArmed)
    }

    @Test
    fun `arming lapses after the window`() {
        confirmation.arm()

        now += 3_001L

        assertFalse(confirmation.isArmed)
    }

    @Test
    fun `disarming takes effect immediately`() {
        confirmation.arm()

        confirmation.disarm()

        assertFalse(confirmation.isArmed)
    }

    @Test
    fun `a disarmed confirmation stays unarmed as time passes`() {
        confirmation.arm()
        confirmation.disarm()

        now += 1L

        assertFalse(confirmation.isArmed)
    }
}
