package com.kino.puber.core.telemetry

import android.view.KeyEvent
import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.FakeTelemetryStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class KeyThreadingTest {
    @AfterEach
    fun tearDown() {
        Telemetry.resetForTests()
    }

    @Test
    fun keyEmitAndLatencyEmitDoNoIoOnTheCallerThread() {
        val store = FakeTelemetryStore()
        val clock = FakeTelemetryClock(elapsed = 0)
        val sink = TelemetrySink(clock, store, ThreadTelemetryExecutor(), { testSessionInfo }, sid = "deadbeef")
        sink.start(System.currentTimeMillis(), {}, {})
        Telemetry.install(sink, clock)

        val machine = KeyBurstMachine()
        val code = KeyEvent.KEYCODE_DPAD_DOWN
        machine.onKey(KeyEvent.ACTION_DOWN, code, 0, 10, HomeScreen::class.java)
        machine.onKey(KeyEvent.ACTION_UP, code, 0, 20, HomeScreen::class.java)

        val matcher = KeyLatencyMatcher()
        matcher.arm(1_000, 1_000_000_000L, KeyName.DPAD_DOWN, HomeScreen::class.java, true)
        val frameThread = Thread({
            matcher.onFrame(1_001_000_000L, 200_000_000L, Telemetry::record)
        }, "FrameMetricsHandler")
        frameThread.start()
        frameThread.join(5_000)

        assertTrue(sink.flushBlocking(5_000))
        sink.stop()
        assertTrue(sink.flushBlocking(5_000))
        val types = store.files.values.flatten().map { parseLine(it).str("type") }
        assertTrue("key" in types && "key_lat" in types, "$types")
        assertTrue(store.calls.isNotEmpty())
        assertEquals(setOf(ThreadTelemetryExecutor.THREAD_NAME), store.calls.map { it.second }.toSet())
    }
}
