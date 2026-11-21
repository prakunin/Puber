package com.kino.puber.core.telemetry

import com.kino.puber.util.FakeTelemetryClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class StartupMarksTest {
    private val clock = FakeTelemetryClock(elapsed = 500)
    private val out = mutableListOf<TelemetryEvent.Startup>()
    private val marks = StartupMarks(procStartMs = 100, appCreateMs = 200, record = out::add, clock = clock)

    @Test
    fun oneLineWithAscendingMarksOnFirstContent() {
        clock.elapsed = 300
        marks.markAppComposed()
        clock.elapsed = 900
        marks.markFirstContent()
        marks.markFirstContent()
        marks.onTimeout()

        val startup = out.single()
        assertEquals(100L, startup.procStartMs)
        assertEquals(200L, startup.appCreateMs)
        assertEquals(300L, startup.appComposedMs)
        assertEquals(900L, startup.firstContentMs)
    }

    @Test
    fun lineWaitsForRevealedAndCarriesBothMarks() {
        clock.elapsed = 900
        marks.markFirstContent()
        assertEquals(0, out.size)
        clock.elapsed = 1_200
        marks.markRevealed()

        val startup = out.single()
        assertEquals(900L, startup.firstContentMs)
        assertEquals(1_200L, startup.revealedMs)
    }

    @Test
    fun oneLineWhenRevealedComesBeforeFirstContent() {
        clock.elapsed = 700
        marks.markRevealed()
        assertEquals(0, out.size)
        clock.elapsed = 800
        marks.markFirstContent()
        marks.markRevealed()

        val startup = out.single()
        assertEquals(800L, startup.firstContentMs)
        assertEquals(700L, startup.revealedMs)
    }

    @Test
    fun fallbackLineHasNoRevealedWhenItNeverCame() {
        clock.elapsed = 900
        marks.markFirstContent()
        marks.onTimeout()

        val startup = out.single()
        assertEquals(900L, startup.firstContentMs)
        assertNull(startup.revealedMs)
    }

    @Test
    fun revealedKeepsTheFirstValue() {
        clock.elapsed = 700
        marks.markRevealed()
        clock.elapsed = 800
        marks.markRevealed()
        marks.onTimeout()
        assertEquals(700L, out.single().revealedMs)
    }

    @Test
    fun appComposedKeepsTheFirstValue() {
        clock.elapsed = 300
        marks.markAppComposed()
        clock.elapsed = 400
        marks.markAppComposed()
        marks.onTimeout()
        assertEquals(300L, out.single().appComposedMs)
    }

    @Test
    fun fallbackLineAfterSixtySecondsWithMarksSoFar() {
        marks.onTimeout()
        assertEquals(1, out.size)
        assertNull(out.single().firstContentMs)
        assertNull(out.single().appComposedMs)

        marks.markFirstContent()
        assertEquals(1, out.size)
    }
}
