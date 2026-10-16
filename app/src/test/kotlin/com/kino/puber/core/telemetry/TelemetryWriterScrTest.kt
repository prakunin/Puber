package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class TelemetryWriterScrTest {
    private val home = HomeScreen::class.java
    private val other = ScreenScreen::class.java

    private fun nav(to: Class<*>) = TelemetryEvent.Nav(1, NavCmd.PUSH, null, to)

    private fun frames(screen: Class<*>?) = TelemetryEvent.Frames(1, screen, 1, 0, 1, 1, 1, 1)

    private fun started(): SinkHarness = SinkHarness().also { it.start() }

    @Test
    fun framesKeepTheirOwnScreenAfterALaterNav() {
        val h = started()
        h.sink.record(nav(other))
        h.sink.record(frames(home))
        h.sink.record(TelemetryEvent.Ready(1, HomeVM::class.java, SampleState.Content::class.java, 5))
        h.drain()
        assertEquals("Home", h.linesOf("frames").single().str("scr"))
        assertEquals("Screen", h.linesOf("nav").single().str("scr"))
        assertEquals("Home", h.linesOf("ready").single().str("scr"))
    }

    @ParameterizedTest
    @EnumSource(
        value = TelemetryEventType::class,
        names = ["FRAMES", "KEY", "KEY_LAT", "MEM"],
    )
    fun keyKeyLatAndMemKeepTheirOwnScreen(type: TelemetryEventType) {
        val h = started()
        h.sink.record(nav(other))
        h.sink.record(
            when (type) {
                TelemetryEventType.FRAMES -> frames(home)
                TelemetryEventType.KEY -> TelemetryEvent.Key(1, home, KeyName.DPAD_UP, 1, 1)
                TelemetryEventType.KEY_LAT -> TelemetryEvent.KeyLat(1, home, KeyName.DPAD_UP, 70, 20)
                else -> TelemetryEvent.Mem(1, home, 1, 1, 1, 1, null)
            },
        )
        h.drain()
        assertEquals("Home", h.linesOf(type.wire).single().str("scr"))
    }

    @Test
    fun screenScopedNullScreenWritesEmptyNotLastNav() {
        val h = started()
        h.sink.record(nav(other))
        h.sink.record(frames(null))
        h.drain()
        assertEquals("", h.linesOf("frames").single().str("scr"))
    }

    @Test
    fun screenScopedEventsDoNotMoveLastScreen() {
        val h = started()
        h.sink.record(nav(other))
        repeat(TelemetryEventType.FRAMES.capPerMinute + 1) { h.sink.record(frames(home)) }
        h.drain()
        val dropped = h.linesOf("meta").single { it.str("ev") == "dropped" }
        assertEquals("Screen", dropped.str("scr"))
        assertEquals("Home", h.linesOf("frames").first().str("scr"))
    }

    @Test
    fun navStillSetsLastScreenAndOldTypesUnchanged() {
        val h = started()
        h.sink.record(nav(home))
        h.sink.record(TelemetryEvent.Ready(1, HomeVM::class.java, SampleState.Content::class.java, 5))
        h.sink.record(nav(other))
        h.sink.record(TelemetryEvent.Meta(1, MetaEv.ROTATE))
        h.drain()
        assertEquals(listOf("Home", "Screen"), h.linesOf("nav").map { it.str("scr") })
        assertEquals("Home", h.linesOf("ready").single().str("scr"))
        assertEquals("Screen", h.linesOf("meta").last().str("scr"))
    }
}
