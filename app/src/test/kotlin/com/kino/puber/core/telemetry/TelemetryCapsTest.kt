package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TelemetryCapsTest {
    private fun nav() = TelemetryEvent.Nav(1, NavCmd.PUSH, HomeScreen::class.java, ScreenScreen::class.java)

    private fun SinkHarness.navLines() = linesOf("nav").size

    private fun SinkHarness.droppedCounts() = linesOf("meta")
        .filter { it.str("ev") == "dropped" }
        .map { it["counts"].toString() }

    @Test
    fun excessAboveCapDropsAndMetaReportsCountsPerType() {
        val h = SinkHarness()
        h.start()
        repeat(130) { h.sink.record(nav()) }
        h.drain()
        assertEquals(120, h.navLines())
        assertEquals(listOf("""{"nav":10}"""), h.droppedCounts())
    }

    @Test
    fun capWindowResetsEachWallMinute() {
        val h = SinkHarness()
        h.start()
        repeat(125) { h.sink.record(nav()) }
        h.drain()
        h.clock.advance(60_000)
        repeat(3) { h.sink.record(nav()) }
        h.drain()
        assertEquals(123, h.navLines())
    }

    @Test
    fun queueOverflowDropsAreCountedAndQueueStaysAtFourThousandNinetySix() {
        val h = SinkHarness()
        h.start()
        // META is uncapped, so every event that got into the queue reaches the file.
        repeat(TelemetrySink.QUEUE_BOUND + 5) {
            h.sink.record(TelemetryEvent.Meta(1, MetaEv.ROTATE))
        }
        h.drain()
        val metas = h.linesOf("meta")
        assertEquals(TelemetrySink.QUEUE_BOUND, metas.count { it.str("ev") == "rotate" })
        val dropped = metas.filter { it.str("ev") == "dropped" }
        assertEquals(listOf("""{"meta":5}"""), dropped.map { it["counts"].toString() })
    }
}
