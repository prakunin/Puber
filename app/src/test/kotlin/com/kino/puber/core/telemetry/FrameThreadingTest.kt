package com.kino.puber.core.telemetry

import android.app.Activity
import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.FakeTelemetryStore
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FrameThreadingTest {
    @AfterEach
    fun tearDown() {
        Telemetry.resetForTests()
        KeyProbe.resetForTests()
    }

    @Test
    fun noTelemetryIoOrEncodingOnTheCallbackThread() {
        val store = FakeTelemetryStore()
        val clock = FakeTelemetryClock(elapsed = 0)
        val encodeThreads = java.util.Collections.synchronizedList(mutableListOf<String>())
        val sink = TelemetrySink(
            clock,
            store,
            ThreadTelemetryExecutor(),
            { encodeThreads += Thread.currentThread().name; testSessionInfo },
            sid = "deadbeef",
        )
        sink.start(System.currentTimeMillis(), {}, {})
        Telemetry.install(sink, clock)
        val factory = FakeFrameTrackerFactory()
        val probe = FrameProbe(factory)
        probe.start(mockk<Activity>())

        val callback = Thread({
            Telemetry.screen = HomeScreen::class.java
            repeat(20) { factory.listener.onFrame(1_000_000_000L + it * 16_000_000L, 8_000_000L, false) }
            // A screen change closes the window on this very thread.
            Telemetry.screen = ScreenScreen::class.java
            factory.listener.onFrame(2_000_000_000L, 8_000_000L, false)
        }, "FrameMetricsHandler")
        callback.start()
        callback.join(5_000)

        assertTrue(sink.flushBlocking(5_000))
        sink.stop()
        assertTrue(sink.flushBlocking(5_000))
        val types = store.files.values.flatten().map { parseLine(it).str("type") }
        assertTrue("frames" in types, "$types")
        assertEquals(setOf(ThreadTelemetryExecutor.THREAD_NAME), store.calls.map { it.second }.toSet())
        assertTrue(encodeThreads.none { it == "FrameMetricsHandler" })
    }
}
