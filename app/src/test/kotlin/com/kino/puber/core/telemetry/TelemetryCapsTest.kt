package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

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

    private fun sample(type: TelemetryEventType): TelemetryEvent = when (type) {
        TelemetryEventType.FRAMES -> TelemetryEvent.Frames(1, HomeScreen::class.java, 1, 0, 1, 1, 1, 1)
        TelemetryEventType.KEY -> TelemetryEvent.Key(1, HomeScreen::class.java, KeyName.DPAD_UP, 1, 1)
        TelemetryEventType.KEY_LAT -> keyLat()
        else -> TelemetryEvent.Mem(1, HomeScreen::class.java, 1, 1, 1, 1, null)
    }

    private fun keyLat() = TelemetryEvent.KeyLat(1, HomeScreen::class.java, KeyName.DPAD_UP, 70, 20)

    @ParameterizedTest
    @EnumSource(
        value = TelemetryEventType::class,
        names = ["FRAMES", "KEY", "KEY_LAT", "MEM"],
    )
    fun newTypesHaveTheDeclaredPerMinuteCaps(type: TelemetryEventType) {
        val expected = mapOf(
            TelemetryEventType.FRAMES to 12,
            TelemetryEventType.KEY to 120,
            TelemetryEventType.KEY_LAT to 60,
            TelemetryEventType.MEM to 10,
        ).getValue(type)
        assertEquals(expected, type.capPerMinute)
        val h = SinkHarness()
        h.start()
        repeat(expected + 1) { h.sink.record(sample(type)) }
        h.drain()
        assertEquals(expected, h.linesOf(type.wire).size)
        assertEquals(listOf("""{"${type.wire}":1}"""), h.droppedCounts())
    }

    @Test
    fun keyLatFloodIsCountedNotLostSilentlyAndWindowResets() {
        val h = SinkHarness()
        h.start()
        repeat(200) { h.sink.record(keyLat()) }
        h.drain()
        assertEquals(60, h.linesOf("key_lat").size)
        assertEquals(listOf("""{"key_lat":140}"""), h.droppedCounts())
        h.clock.advance(60_000)
        h.sink.record(keyLat())
        h.drain()
        assertEquals(61, h.linesOf("key_lat").size)
    }
}
