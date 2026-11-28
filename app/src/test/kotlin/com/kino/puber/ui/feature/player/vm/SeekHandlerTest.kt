package com.kino.puber.ui.feature.player.vm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeekHandlerTest {

    // Default steps: [10, 10, 20, 30, 60, 60]

    @Test
    fun firstCall_returnsFirstStep() {
        val handler = SeekHandler()
        assertEquals(10, handler.nextStep())
    }

    @Test
    fun rapidConsecutiveCalls_incrementsStep() {
        val handler = SeekHandler()
        val first = handler.nextStep()   // returns steps[0] = 10
        val second = handler.nextStep()  // returns steps[1] = 10
        val third = handler.nextStep()   // returns steps[2] = 20
        assertEquals(10, first)
        assertEquals(10, second)
        assertEquals(20, third)
        assertTrue(third >= second, "Each rapid call should advance to a larger or equal step")
    }

    @Test
    fun maxStep_clampsAtLastValue() {
        val steps = intArrayOf(10, 10, 20, 30, 60, 60)
        val handler = SeekHandler(steps = steps)
        // Exhaust all index advances.
        repeat(steps.size + 5) { handler.nextStep() }
        // The next call must still return the last step value.
        assertEquals(steps.last(), handler.nextStep())
    }

    @Test
    fun reset_resetsIndex() {
        val handler = SeekHandler()
        // Advance the index several times.
        repeat(4) { handler.nextStep() }
        // After reset the next call must return the first step.
        handler.reset()
        assertEquals(10, handler.nextStep())
    }
}
