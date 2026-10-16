package com.kino.puber.core.telemetry

import android.app.Activity
import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.RecordingTelemetryRecorder
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class FakeFrameTracker(private val onDisabled: () -> Unit = {}) : FrameTracker {
    override var isTrackingEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) onDisabled()
        }
}

/** Records every `create` and hands the test the listener, since JankStats cannot run on the JVM. */
internal class FakeFrameTrackerFactory : FrameTrackerFactory {
    var onDisabled: () -> Unit = {}
    val created = mutableListOf<Triple<Activity, FrameListener, FakeFrameTracker>>()
    val listener: FrameListener get() = created.last().second
    val tracker: FakeFrameTracker get() = created.last().third

    override fun create(activity: Activity, listener: FrameListener): FrameTracker {
        val tracker = FakeFrameTracker(onDisabled)
        created += Triple(activity, listener, tracker)
        return tracker
    }
}

internal class FrameProbeTest {
    private val recorder = RecordingTelemetryRecorder()
    private val factory = FakeFrameTrackerFactory()
    private val frames = mutableListOf<TelemetryEvent.Frames>()
    private val aggregator = FrameAggregator({ frames += it }, { 1_000 }, { 0 })
    private val matcher = KeyLatencyMatcher { 1_000 }
    private val probe = FrameProbe(factory, aggregator, matcher)
    private val activity = mockk<Activity>()

    private fun ms(v: Long) = v * 1_000_000L

    @BeforeEach
    fun setUp() {
        Telemetry.install(recorder, FakeTelemetryClock())
    }

    @AfterEach
    fun tearDown() {
        Telemetry.resetForTests()
        KeyProbe.resetForTests()
    }

    @Test
    fun startCreatesOneTrackerAndStartTwiceDoesNotCreateASecond() {
        probe.start(activity)
        assertEquals(1, factory.created.size)
        probe.start(activity)
        assertEquals(1, factory.created.size)
        assertSame(activity, factory.created.single().first)
        assertTrue(factory.tracker.isTrackingEnabled)
    }

    @Test
    fun stopDisablesTrackingClosesAggregatorAndReleasesTheTracker() {
        probe.start(activity)
        Telemetry.screen = HomeScreen::class.java
        factory.listener.onFrame(ms(1), ms(8), false)
        probe.stop()
        assertFalse(factory.tracker.isTrackingEnabled)
        assertEquals(1, frames.size)
        probe.stop()
        assertEquals(1, frames.size)
        probe.start(activity)
        assertEquals(2, factory.created.size)
    }

    @Test
    fun recreatedActivityGetsANewTrackerAndTheOldOneStaysStopped() {
        val second = mockk<Activity>()
        probe.start(activity)
        val first = factory.tracker
        probe.stop()
        probe.start(second)
        assertEquals(2, factory.created.size)
        assertNotSame(first, factory.tracker)
        assertFalse(first.isTrackingEnabled)
        assertTrue(factory.tracker.isTrackingEnabled)
        assertSame(second, factory.created.last().first)
    }

    @Test
    fun listenerWhileOffTouchesNeitherAggregatorNorMatcher() {
        probe.start(activity)
        val dispatchNs = ms(100)
        matcher.arm(100, dispatchNs, KeyName.DPAD_UP, null, true)
        recorder.isEnabled = false
        factory.listener.onFrame(ms(101), ms(200), true)
        probe.stop()
        assertEquals(0, frames.size)
        // The pending key survived the off frame: an on frame still answers it.
        recorder.isEnabled = true
        val keyLats = mutableListOf<TelemetryEvent.KeyLat>()
        matcher.onFrame(ms(102), ms(200), keyLats::add)
        assertEquals(1, keyLats.size)

        // Control: the same call while on counts the frame.
        probe.start(activity)
        factory.listener.onFrame(ms(103), ms(8), false)
        probe.stop()
        assertEquals(1, frames.size)
        assertEquals(1, frames.single().frames)
    }

    @Test
    fun lateFrameAfterStopDoesNotEmitOrArm() {
        probe.start(activity)
        val late = factory.listener
        matcher.arm(100, ms(100), KeyName.DPAD_UP, null, true)
        probe.stop()
        late.onFrame(ms(101), ms(200), false)
        probe.stop()
        assertEquals(0, frames.size)
        val keyLats = mutableListOf<TelemetryEvent.KeyLat>()
        matcher.onFrame(ms(102), ms(200), keyLats::add)
        assertEquals(1, keyLats.size)
    }

    @Test
    fun listenerFeedsMatcherThenAggregatorWithTheCurrentScreenHolder() {
        probe.start(activity)
        Telemetry.screen = HomeScreen::class.java
        factory.listener.onFrame(ms(10), ms(8), false)
        Telemetry.screen = ScreenScreen::class.java
        // A slow armed key is answered by the same call that closes the Home window.
        matcher.arm(1_000, ms(1_100), KeyName.DPAD_DOWN, HomeScreen::class.java, true)
        factory.listener.onFrame(ms(1_101), ms(80), false)
        assertEquals(1, frames.size)
        assertSame(HomeScreen::class.java, frames.single().screen)
        assertEquals(1, frames.single().frames)
        val keyLat = recorder.events.filterIsInstance<TelemetryEvent.KeyLat>().single()
        assertEquals(181, keyLat.latMs)
        assertSame(HomeScreen::class.java, keyLat.screen)
        probe.stop()
        assertSame(ScreenScreen::class.java, frames.last().screen)
    }
}
