package com.kino.puber.core.telemetry

import android.view.KeyEvent
import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.RecordingTelemetryRecorder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The gate in `KeyProbe.onKey`: only a handled first DOWN is timed; repeats, UP and unhandled never. */
internal class KeyProbeGateTest {
    private val recorder = RecordingTelemetryRecorder()
    private val code = KeyEvent.KEYCODE_DPAD_DOWN

    private fun ms(v: Long) = v * 1_000_000L

    @BeforeEach
    fun setUp() {
        Telemetry.install(recorder, FakeTelemetryClock())
    }

    @AfterEach
    fun tearDown() {
        KeyProbe.resetForTests()
        Telemetry.resetForTests()
    }

    private fun key(action: Int, repeat: Int, atMs: Long, handled: Boolean = true) =
        KeyProbe.onKey(action, code, repeat, atMs, ms(atMs), handled)

    private fun frame(startMs: Long, uiMs: Long) =
        KeyProbe.matcher.onFrame(ms(startMs), ms(uiMs), recorder::record)

    private fun latCount() = recorder.events.count { it is TelemetryEvent.KeyLat }

    @Test
    fun repeatsAndUpNeverArmTheMatcher() {
        key(KeyEvent.ACTION_DOWN, 0, 1_000)
        frame(1_001, 5)
        assertEquals(0, latCount())

        key(KeyEvent.ACTION_DOWN, 1, 1_100)
        frame(1_101, 200)
        key(KeyEvent.ACTION_DOWN, 2, 1_200)
        frame(1_201, 200)
        key(KeyEvent.ACTION_UP, 0, 1_300)
        frame(1_301, 200)
        assertEquals(0, latCount())
    }

    @Test
    fun unhandledFirstDownNeverArmsAndHandledOneDoes() {
        key(KeyEvent.ACTION_DOWN, 0, 1_000, handled = false)
        frame(1_001, 200)
        assertEquals(0, latCount())

        key(KeyEvent.ACTION_DOWN, 0, 2_000)
        frame(2_001, 200)
        assertEquals(1, latCount())
    }
}
