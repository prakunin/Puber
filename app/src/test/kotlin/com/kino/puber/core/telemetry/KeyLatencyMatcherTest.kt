package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
        assertEquals(70L, e.latMs)
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
        assertEquals(70L, out.single().latMs)
    }

    @Test
    fun frameStartingBeforeDispatchDoesNotAnswer() {
        arm(1_000, 1_010)
        frame(1_000, 200)
        assertEquals(0, out.size)
        frame(1_012, 100)
        assertEquals(112L, out.single().latMs)
    }

    @Test
    fun heldKeyArmsOnceAndMeasuresFromTheFirstDown() {
        arm(1_000, 1_000)
        frame(1_001, 99)
        frame(1_100, 99)
        assertEquals(1, out.size)
        assertEquals(100L, out.single().latMs)
    }

    @Test
    fun eachPendingKeyIsAnsweredByTheFirstFrameAfterIt() {
        arm(1_000, 1_000)
        arm(1_300, 1_300)
        frame(1_301, 99)
        assertEquals(listOf<Long?>(400L, 100L), out.map { it.latMs })
        frame(1_400, 99)
        assertEquals(2, out.size)
    }

    @Test
    fun frameBetweenTwoPendingKeysAnswersOnlyTheEarlier() {
        arm(1_000, 1_000)
        arm(1_100, 1_100)
        frame(1_050, 100)
        assertEquals(listOf<Long?>(150L), out.map { it.latMs })
        frame(1_101, 100)
        assertEquals(listOf<Long?>(150L, 101L), out.map { it.latMs })
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
    fun keyAnsweredBetweenOneAndFiveSecondsIsEmitted() {
        arm(1_000, 1_000)
        frame(2_001, 200)
        assertEquals(1_201L, out.single().latMs)
        assertEquals(false, out.single().expired)
    }

    @Test
    fun keyUnansweredPastFiveSecondsIsEmittedExpiredWithoutLat() {
        arm(1_000, 1_000)
        frame(6_001, 200)
        val e = out.single()
        assertNull(e.latMs)
        assertEquals(true, e.expired)
        assertEquals(200, e.frameMs)
        frame(6_100, 200)
        assertEquals(1, out.size)
    }

    @Test
    fun pendingListHoldsAtMostSixteenKeys() {
        for (i in 0 until 17) arm(1_000L + i, 1_000L + i)
        frame(2_000, 100)
        assertEquals(16, out.size)
    }

    @Test
    fun noFrameMeansNoLine() {
        arm(1_000, 1_000)
        arm(1_500, 1_500)
        arm(2_001, 2_001)
        assertEquals(0, out.size)
        frame(2_002, 99)
        assertEquals(listOf<Long?>(1_101L, 601L, 100L), out.map { it.latMs })
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

    @Test
    fun keyLatCarriesDispatchWait() {
        arm(970, 1_000)
        frame(1_010, 60)
        val e = out.single()
        assertEquals(30L, e.waitMs)
        assertEquals(100L, e.latMs)
    }

    @Test
    fun expiredKeyLatCarriesDispatchWait() {
        arm(970, 1_000)
        frame(6_001, 10)
        val e = out.single()
        assertEquals(true, e.expired)
        assertEquals(30L, e.waitMs)
    }
}
