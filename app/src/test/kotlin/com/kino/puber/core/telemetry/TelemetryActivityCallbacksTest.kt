package com.kino.puber.core.telemetry

import android.app.Activity
import android.view.KeyEvent
import com.kino.puber.util.ManualTelemetryExecutor
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TelemetryActivityCallbacksTest {
    private val h = SinkHarness()
    private val memExec = ManualTelemetryExecutor()
    private val factory = FakeFrameTrackerFactory()
    private val frameProbe = FrameProbe(factory)
    private val memoryProbe = MemoryProbe(memExec, RecordingMemorySampler(), {}, {})
    private val log = mutableListOf<String>()
    private var enabled = false
    private val callbacks = TelemetryActivityCallbacks(
        { enabled },
        frameProbe,
        { log += "keyFlush"; KeyProbe.flush() },
        { log += "requestDrain" },
        memoryProbe,
    )
    private val probes = ActivityProbes(callbacks, frameProbe, memoryProbe, KeyProbe::detach)
    private val activity = mockk<Activity>()

    @AfterEach
    fun tearDown() {
        Telemetry.resetForTests()
        KeyProbe.resetForTests()
    }

    @Test
    fun resumeWhenOffDoesNotStartTrackingButTracksTheActivity() {
        callbacks.onActivityResumed(activity)
        assertEquals(0, factory.created.size)
        assertSame(activity, callbacks.resumedActivity)
        enabled = true
        callbacks.onActivityPaused(activity)
        callbacks.onActivityResumed(activity)
        assertEquals(1, factory.created.size)
    }

    @Test
    fun enableWhileResumedStartsTrackingOnThatActivity() {
        probes.attach()
        assertEquals(0, factory.created.size)
        probes.detach()
        callbacks.onActivityResumed(activity)
        enabled = true
        probes.attach()
        assertEquals(1, factory.created.size)
        assertSame(activity, factory.created.single().first)
        assertEquals(0, memExec.repeatingCount)
    }

    @Test
    fun enableWhileInTheForegroundStartsTheMemoryTimerFromAttach() {
        callbacks.onActivityStarted(activity)
        assertEquals(0, memExec.repeatingCount)
        enabled = true
        probes.attach()
        assertEquals(1, memExec.repeatingCount)
        probes.detach()
        assertEquals(0, memExec.repeatingCount)
    }

    @Test
    fun pauseStopsFrameFlushesKeysThenRequestsDrainInThatOrder() {
        Telemetry.install(h.sink, h.clock)
        h.start()
        enabled = true
        factory.onDisabled = { log += "frame.stop" }
        callbacks.onActivityResumed(activity)
        Telemetry.screen = HomeScreen::class.java
        factory.listener.onFrame(1_000_000_000L, 8_000_000L, false)
        KeyProbe.onKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0, 100, 0, false)

        callbacks.onActivityPaused(activity)
        assertEquals(listOf("frame.stop", "keyFlush", "requestDrain"), log)
        h.drain()
        assertEquals(1, h.linesOf("frames").size)
        assertEquals(1, h.linesOf("key").size)
        assertEquals("Home", h.linesOf("frames").single().str("scr"))
        assertEquals("Home", h.linesOf("key").single().str("scr"))
        assertNull(callbacks.resumedActivity)
    }

    @Test
    fun pauseWhileOffDoesNothingButClearTheActivity() {
        callbacks.onActivityResumed(activity)
        callbacks.onActivityPaused(activity)
        assertEquals(emptyList<String>(), log)
        assertNull(callbacks.resumedActivity)
    }

    @Test
    fun destroyAndPauseClearTheResumedActivity() {
        val other = mockk<Activity>()
        callbacks.onActivityResumed(activity)
        callbacks.onActivityDestroyed(other)
        assertSame(activity, callbacks.resumedActivity)
        callbacks.onActivityDestroyed(activity)
        assertNull(callbacks.resumedActivity)
        callbacks.onActivityResumed(activity)
        callbacks.onActivityPaused(activity)
        assertNull(callbacks.resumedActivity)
    }

    @Test
    fun startedCountTreatsTwoActivitiesAsForegroundUntilBothStop() {
        val other = mockk<Activity>()
        enabled = true
        probes.attach()
        assertFalse(callbacks.isForeground)
        callbacks.onActivityStarted(activity)
        callbacks.onActivityStarted(other)
        assertTrue(callbacks.isForeground)
        assertEquals(1, memExec.repeatingCount)
        callbacks.onActivityStopped(activity)
        assertTrue(callbacks.isForeground)
        assertEquals(1, memExec.repeatingCount)
        callbacks.onActivityStopped(other)
        assertFalse(callbacks.isForeground)
        assertEquals(0, memExec.repeatingCount)
        callbacks.onActivityStopped(other)
        assertFalse(callbacks.isForeground)
    }
}
