package com.kino.puber.core.telemetry

import android.view.KeyEvent
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class KeyBurstThroughSinkTest {
    @AfterEach
    fun tearDown() {
        Telemetry.resetForTests()
    }

    private fun feedHeldDpad(machine: KeyBurstMachine) {
        val code = KeyEvent.KEYCODE_DPAD_DOWN
        machine.onKey(KeyEvent.ACTION_DOWN, code, 0, 100, HomeScreen::class.java)
        for (r in 1..9) machine.onKey(KeyEvent.ACTION_DOWN, code, r, 100L + r * 50, HomeScreen::class.java)
        machine.onKey(KeyEvent.ACTION_UP, code, 0, 700, HomeScreen::class.java)
    }

    @Test
    fun heldDpadWritesOneKeyLineWithFields() {
        val off = SinkHarness()
        Telemetry.install(off.sink, off.clock)
        feedHeldDpad(KeyBurstMachine())
        off.drain()
        assertEquals(0, off.linesOf("key").size)

        val h = SinkHarness()
        h.start()
        Telemetry.install(h.sink, h.clock)
        h.clock.advance(10)
        feedHeldDpad(KeyBurstMachine())
        h.drain()
        val line = h.linesOf("key").single()
        assertEquals(setOf("t", "sid", "type", "scr", "key", "n", "dur_ms"), line.keys)
        assertEquals("dpad_down", line.str("key"))
        assertEquals("10", line.str("n"))
        assertEquals("600", line.str("dur_ms"))
        assertEquals("Home", line.str("scr"))
        assertTrue(line.getValue("t").jsonPrimitive.content.toLong() >= 0)
    }
}
