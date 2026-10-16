package com.kino.puber.core.telemetry

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.jsonPrimitive

internal class FrameAggregatorThroughSinkTest {
    @AfterEach
    fun tearDown() {
        Telemetry.resetForTests()
    }

    @Test
    fun backDatedWindowNeverPrecedesTheSessionOrigin() {
        val h = SinkHarness()
        h.clock.elapsed = 5_000
        h.start()
        Telemetry.install(h.sink, h.clock)
        var nano = 0L
        val agg = FrameAggregator(nanoTime = { nano })
        agg.onFrame(1_000_000_000L, 8_000_000L, false, HomeScreen::class.java)
        h.clock.advance(20_000)
        nano = 1_008_000_000L + 30_000_000_000L
        agg.close()
        h.drain()
        val line = h.linesOf("frames").single()
        val t = line.getValue("t").jsonPrimitive.content.toLong()
        assertTrue(t >= 0, "t=$t")
        assertTrue(t <= h.clock.elapsed - 5_000, "t=$t")
        assertEquals("Home", line.str("scr"))
    }

    @Test
    fun listenerShapedCallsMatchGoldenEncodedLine() {
        val out = mutableListOf<TelemetryEvent.Frames>()
        val agg = FrameAggregator({ out += it }, { 10_000 }, { 0 })
        var start = 0L
        repeat(90) { agg.onFrame(start, 8_000_000, it < 12, HomeScreen::class.java); start += 16_000_000 }
        repeat(9) { agg.onFrame(start, 20_000_000, false, HomeScreen::class.java); start += 16_000_000 }
        agg.onFrame(start, 250_000_000, false, HomeScreen::class.java)
        agg.close()
        val encoder = TelemetryEncoder("sid", 0, TelemetryNames(), { testSessionInfo }, { 0 })
        val json = encoder.encode(out.single(), "Home")
        assertEquals(setOf("t", "sid", "type", "scr", "frames", "janky", "p50", "p90", "p99", "max"), json.keys)
        assertEquals(
            listOf("100", "12", "8", "8", "20", "250"),
            listOf("frames", "janky", "p50", "p90", "p99", "max").map { json.getValue(it).jsonPrimitive.content },
        )
    }
}
