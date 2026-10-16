package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class KeyLatencyMatcherTest {
    private val out = mutableListOf<TelemetryEvent.KeyLat>()
    private val matcher = KeyLatencyMatcher { 555 }
    private val home = HomeScreen::class.java
    private val other = ScreenScreen::class.java

    private fun ms(v: Long) = v * 1_000_000L

    private fun arm(eventMs: Long, dispatchMs: Long, handled: Boolean = true, screen: Class<*>? = home) =
        matcher.arm(eventMs, ms(dispatchMs), KeyName.DPAD_DOWN, screen, handled)

    private fun frame(startMs: Long, uiMs: Long) = matcher.onFrame(ms(startMs), ms(uiMs)) { out += it }

    @Test
    fun answerOverFiftyMsEmitsLatAndFrameMs() {
        arm(1_000, 1_005)
        frame(1_010, 60)
        val e = out.single()
        assertEquals(70, e.latMs)
        assertEquals(60, e.frameMs)
        assertEquals(KeyName.DPAD_DOWN, e.key)
        assertEquals(555, e.t)
    }

    @Test
    fun fiftyIsNotSlowAndFiftyOneIs() {
        for ((lat, emits) in listOf(49L to 0, 50L to 0, 51L to 1)) {
            out.clear()
            arm(1_000, 1_000)
            frame(1_001, lat - 1)
            assertEquals(emits, out.size, "lat $lat")
            // The entry is consumed either way: the same frame again answers nothing.
            frame(1_001, lat - 1)
            assertEquals(emits, out.size, "lat $lat second")
        }
    }

    @Test
    fun latencyStartsAtEventTimeNotAtDispatch() {
        arm(970, 1_000)
        frame(1_005, 35)
        assertEquals(70, out.single().latMs)
    }

    @Test
    fun frameStartingBeforeDispatchDoesNotAnswer() {
        arm(1_000, 1_010)
        frame(1_000, 200)
        assertEquals(0, out.size)
        frame(1_012, 100)
        assertEquals(112, out.single().latMs)
    }

    @Test
    fun heldKeyArmsOnceAndMeasuresFromTheFirstDown() {
        arm(1_000, 1_000)
        frame(1_001, 99)
        frame(1_100, 99)
        assertEquals(1, out.size)
        assertEquals(100, out.single().latMs)
    }

    @Test
    fun secondDownWhilePendingDoesNotReplaceTheEarliest() {
        arm(1_000, 1_000)
        arm(1_300, 1_300)
        frame(1_301, 99)
        assertEquals(400, out.single().latMs)
    }

    @Test
    fun unhandledKeyNeverArms() {
        arm(1_000, 1_000, handled = false)
        frame(1_001, 200)
        assertEquals(0, out.size)
        arm(1_000, 1_000, handled = true)
        frame(1_001, 200)
        assertEquals(1, out.size)
    }

    @Test
    fun pendingExpiresAfterOneSecondAndLateFrameEmitsNothing() {
        arm(1_000, 1_000)
        frame(2_001, 200)
        assertEquals(0, out.size)
        frame(2_100, 200)
        assertEquals(0, out.size)
        // Control: a frame exactly one second after dispatch still answers.
        arm(5_000, 5_000)
        frame(6_000, 200)
        assertEquals(1, out.size)
    }

    @Test
    fun noFrameMeansNoLine() {
        arm(1_000, 1_000)
        arm(1_500, 1_500)
        arm(2_001, 2_001)
        assertEquals(0, out.size)
        frame(2_002, 99)
        assertEquals(100, out.single().latMs)
    }

    @Test
    fun answeredEntryIsConsumedAndNotEmittedTwice() {
        arm(1_000, 1_000)
        frame(1_001, 100)
        frame(1_200, 100)
        assertEquals(1, out.size)
    }

    @Test
    fun negativeLatencyFromAClockBaseMismatchIsNeverEmitted() {
        arm(900_000_000, 1_000)
        frame(1_001, 400)
        assertEquals(0, out.size)
        arm(1_000, 1_000)
        frame(1_001, 400)
        assertEquals(1, out.size)
    }

    @Test
    fun lineCarriesTheArmedScreenNotTheScreenAtAnswer() {
        arm(1_000, 1_000, screen = home)
        Telemetry.screen = other
        try {
            frame(1_001, 100)
        } finally {
            Telemetry.resetForTests()
        }
        assertSame(home, out.single().screen)
    }
}
