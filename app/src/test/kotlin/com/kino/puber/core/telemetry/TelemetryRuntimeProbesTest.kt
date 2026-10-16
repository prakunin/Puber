package com.kino.puber.core.telemetry

import android.app.Activity
import com.kino.puber.util.FakeSharedPreferences
import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.FakeTelemetryStore
import com.kino.puber.util.ManualTelemetryExecutor
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import timber.log.Timber

private class CountingProbe : TelemetryProbe {
    var attaches = 0
    var detaches = 0
    var attached = false
        private set

    override fun attach() {
        attaches++
        attached = true
    }

    override fun detach() {
        detaches++
        attached = false
    }
}

/** Notes whether the sink still took events, and the screen holder, when it was detached. */
private class SinkWatchingProbe(private val sink: TelemetrySink) : TelemetryProbe {
    var sinkEnabledAtDetach: Boolean? = null
    var screenAtDetach: Class<*>? = null

    override fun attach() = Unit

    override fun detach() {
        sinkEnabledAtDetach = sink.isEnabled
        screenAtDetach = Telemetry.screen
    }
}

internal class TelemetryRuntimeProbesTest {
    private val week = 7L * 24 * 3_600_000
    private val prefs = FakeSharedPreferences()
    private val clock = FakeTelemetryClock()
    private val store = FakeTelemetryStore()
    private val executor = ManualTelemetryExecutor()
    private val probe = CountingProbe()
    private val posted = mutableListOf<Runnable>()
    private lateinit var sink: TelemetrySink
    private lateinit var runtime: TelemetryRuntime
    private lateinit var forestBefore: List<Timber.Tree>
    private var handlerBefore: Thread.UncaughtExceptionHandler? = null

    @BeforeEach
    fun setUp() {
        forestBefore = Timber.forest()
        handlerBefore = Thread.getDefaultUncaughtExceptionHandler()
        sink = TelemetrySink(clock, store, executor, { testSessionInfo }, sid = "deadbeef")
        runtime = TelemetryRuntime(prefs.sharedPreferences, clock, sink, listOf(probe), posted::add)
        Telemetry.install(sink, clock)
    }

    @AfterEach
    fun tearDown() {
        Timber.forest().filterIsInstance<TelemetryErrorTree>().forEach(Timber::uproot)
        Thread.setDefaultUncaughtExceptionHandler(handlerBefore)
        Telemetry.resetForTests()
    }

    private fun runPosted() {
        val todo = posted.toList()
        posted.clear()
        todo.forEach { it.run() }
    }

    @Test
    fun freshProcessWithLogOffNeverAttachesAnyProbe() {
        runtime.start(null)
        runPosted()
        assertEquals(0, probe.attaches)
        assertEquals(0, posted.size)
        assertEquals(0, executor.useCount)
        assertTrue(store.calls.isEmpty())
        // Control: switching on attaches.
        runtime.setEnabled(true)
        runPosted()
        assertEquals(1, probe.attaches)
    }

    @Test
    fun enablingAttachesOnceOnTheMainThreadQueue() {
        runtime.setEnabled(true)
        assertEquals(0, probe.attaches)
        assertEquals(1, posted.size)
        runPosted()
        assertEquals(1, probe.attaches)
        runtime.setEnabled(true)
        runPosted()
        assertEquals(1, probe.attaches)
    }

    @Test
    fun toggleOffBeforeTheQueuedAttachRunsLeavesNoProbeAttached() {
        runtime.setEnabled(true)
        runtime.setEnabled(false)
        runPosted()
        assertFalse(probe.attached)
        assertEquals(0, probe.attaches)
    }

    @Test
    fun disablingDetachesAndOnOffOnEndsAttached() {
        runtime.setEnabled(true)
        runtime.setEnabled(false)
        runtime.setEnabled(true)
        runPosted()
        assertTrue(probe.attached)
        runtime.setEnabled(false)
        runPosted()
        assertFalse(probe.attached)
    }

    @Test
    fun switchingOffFlushesTheProbesOnMainBeforeTheSinkStops() {
        val watcher = SinkWatchingProbe(sink)
        val watching = TelemetryRuntime(prefs.sharedPreferences, clock, sink, listOf(watcher), posted::add)
        watching.setEnabled(true)
        runPosted()
        Telemetry.screen = HomeScreen::class.java
        watching.setEnabled(false)
        assertEquals(true, watcher.sinkEnabledAtDetach)
        assertEquals(HomeScreen::class.java, watcher.screenAtDetach)
        assertFalse(sink.isEnabled)
        assertNull(Telemetry.screen)
    }

    @Test
    fun switchingOffOffTheMainThreadPostsTheDetachInstead() {
        val offMain = TelemetryRuntime(
            prefs.sharedPreferences, clock, sink, listOf(probe), posted::add, isMainThread = { false },
        )
        offMain.setEnabled(true)
        runPosted()
        offMain.setEnabled(false)
        assertEquals(0, probe.detaches)
        runPosted()
        assertEquals(1, probe.detaches)
    }

    @Test
    fun autoOffAfterSevenDaysAndStorageFailureAlsoDetach() {
        runtime.setEnabled(true)
        executor.runAll()
        runPosted()
        clock.advance(week + 1)
        executor.runRepeating()
        runPosted()
        assertEquals(1, probe.detaches)

        val failing = CountingProbe()
        val failStore = FakeTelemetryStore().also { it.available = false }
        val failSink = TelemetrySink(clock, failStore, executor, { testSessionInfo }, sid = "feedbeef")
        val failRuntime = TelemetryRuntime(prefs.sharedPreferences, clock, failSink, listOf(failing), posted::add)
        failRuntime.setEnabled(true)
        executor.runAll()
        runPosted()
        // The storage failure stopped the sink before the queued attach ran: it is skipped.
        assertEquals(0, failing.attaches)
        assertEquals(1, failing.detaches)
    }

    @Test
    fun detachRaisedOnTheWriterThreadIsPostedNotRunInline() {
        runtime.setEnabled(true)
        executor.runAll()
        runPosted()
        clock.advance(week + 1)
        executor.runRepeating()
        assertEquals(0, probe.detaches)
        assertEquals(1, posted.size)
        runPosted()
        assertEquals(1, probe.detaches)
    }

    @Test
    fun screenHolderIsNullAfterDetach() {
        runtime.setEnabled(true)
        Telemetry.screen = HomeScreen::class.java
        runtime.setEnabled(false)
        assertNull(Telemetry.screen)
    }

    @Test
    fun reenableWritesEmptyScrUntilTheNextNav() {
        runtime.setEnabled(true)
        executor.runAll()
        Telemetry.screen = HomeScreen::class.java
        runtime.setEnabled(false)
        executor.runAll()
        runtime.setEnabled(true)
        executor.runAll()
        Telemetry.record(TelemetryEvent.Frames(clock.elapsed, Telemetry.screen, 1, 0, 1, 1, 1, 1))
        executor.runRepeating()
        val lines = store.files.keys.sorted().flatMap { store.lines(it).map(::parseLine) }
        assertEquals("", lines.single { it.str("type") == "frames" }.str("scr"))
    }

    @Test
    fun offAfterOnFlushesTheOpenWindowAndWritesNothingFurther() {
        val factory = FakeFrameTrackerFactory()
        val frameProbe = FrameProbe(factory)
        val memoryProbe = MemoryProbe(executor, RecordingMemorySampler(), {}, {})
        val callbacks = TelemetryActivityCallbacks(
            { Telemetry.isEnabled },
            frameProbe,
            KeyProbe::flush,
            sink::requestDrain,
            memoryProbe,
        )
        val real = ActivityProbes(callbacks, frameProbe, memoryProbe, KeyProbe::detach)
        val rt = TelemetryRuntime(prefs.sharedPreferences, clock, sink, listOf(real), posted::add)
        val activity = mockk<Activity>()
        callbacks.onActivityResumed(activity)
        assertEquals(0, factory.created.size)

        rt.setEnabled(true)
        executor.runAll()
        runPosted()
        assertEquals(1, factory.created.size)
        Telemetry.screen = HomeScreen::class.java

        fun feed(from: Long) = repeat(20) { factory.listener.onFrame(from + it * 16_000_000L, 8_000_000L, false) }
        // Control: a window closed while on produces a line.
        feed(1_000_000_000L)
        callbacks.onActivityPaused(activity)
        executor.runAll()
        executor.runRepeating()
        val framesLines = {
            store.files.keys.sumOf { name -> store.lines(name).count { parseLine(it).str("type") == "frames" } }
        }
        assertEquals(1, framesLines())

        callbacks.onActivityResumed(activity)
        feed(5_000_000_000L)
        rt.setEnabled(false)
        executor.runAll()
        runPosted()
        executor.runRepeating()
        // The window that was open at the toggle is written, once: nothing is lost with the switch.
        assertEquals(2, framesLines())
        assertFalse(factory.tracker.isTrackingEnabled)
        feed(9_000_000_000L)
        callbacks.onActivityPaused(activity)
        executor.runAll()
        executor.runRepeating()
        assertEquals(2, framesLines())
    }
}
