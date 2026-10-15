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
