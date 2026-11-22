package com.kino.puber.core.telemetry

import android.view.KeyEvent
import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.RecordingTelemetryRecorder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class KeyProbeOffTest {
    @AfterEach
    fun tearDown() {
        KeyProbe.resetForTests()
        Telemetry.resetForTests()
    }

    private fun feed(enabled: Boolean) {
        val code = KeyEvent.KEYCODE_DPAD_DOWN
        repeat(10) { press ->
            val base = press * 100L
            dispatchWithProbe(enabled, false, { true }) { handled, dropped, ns ->
                KeyProbe.onKey(KeyEvent.ACTION_DOWN, code, 0, base, ns, handled, dropped)
            }
            repeat(3) { r ->
                dispatchWithProbe(enabled, false, { true }) { handled, dropped, ns ->
                    KeyProbe.onKey(KeyEvent.ACTION_DOWN, code, r + 1, base + r + 1, ns, handled, dropped)
                }
            }
            dispatchWithProbe(enabled, false, { true }) { handled, dropped, ns ->
                KeyProbe.onKey(KeyEvent.ACTION_UP, code, 0, base + 50, ns, handled, dropped)
            }
        }
        // End on a press that is still open, so a machine that was fed has a burst to flush.
        dispatchWithProbe(enabled, false, { true }) { handled, dropped, ns ->
            KeyProbe.onKey(KeyEvent.ACTION_DOWN, code, 0, 5_000L, ns, handled, dropped)
        }
    }

    private fun drainRecorder(recorder: RecordingTelemetryRecorder) {
        KeyProbe.flush()
        val farFuture = System.nanoTime() + 1_000_000L
        KeyProbe.matcher.onFrame(farFuture, 900_000_000L, recorder::record)
    }

    @Test
    fun offDispatchNeverTouchesTheMachineOrMatcher() {
        val recorder = RecordingTelemetryRecorder()
        Telemetry.install(recorder, FakeTelemetryClock())
        feed(enabled = false)
        drainRecorder(recorder)
        assertEquals(0, recorder.events.size)

        // Control: the same feed with the probe on records bursts and a slow answer per press.
        feed(enabled = true)
        drainRecorder(recorder)
        assertEquals(11, recorder.events.count { it is TelemetryEvent.Key })
        assertEquals(11, recorder.events.count { it is TelemetryEvent.KeyLat })
    }
}
