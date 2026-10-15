package com.kino.puber.core.telemetry

import com.kino.puber.util.FakeSharedPreferences
import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.FakeTelemetryStore
import com.kino.puber.util.ManualTelemetryExecutor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import timber.log.Timber

internal class TelemetryRuntimeTest {
    private val day = 24L * 3_600_000
    private val week = 7 * day
    private val prefs = FakeSharedPreferences()
    private val key = TelemetryRuntime.KEY_ENABLED_AT
    private val clock = FakeTelemetryClock()
    private val store = FakeTelemetryStore()
    private val executor = ManualTelemetryExecutor()
    private lateinit var sink: TelemetrySink
    private lateinit var runtime: TelemetryRuntime

    private lateinit var forestBefore: List<Timber.Tree>
    private var handlerBefore: Thread.UncaughtExceptionHandler? = null

    @BeforeEach
    fun setUp() {
        forestBefore = Timber.forest()
        handlerBefore = Thread.getDefaultUncaughtExceptionHandler()
        build()
    }

    @AfterEach
    fun tearDown() {
        Timber.forest().filterIsInstance<TelemetryErrorTree>().forEach(Timber::uproot)
        Thread.setDefaultUncaughtExceptionHandler(handlerBefore)
        Telemetry.resetForTests()
    }

    private fun build() {
        sink = TelemetrySink(clock, store, executor, { testSessionInfo }, sid = "deadbeef")
        runtime = TelemetryRuntime(prefs.sharedPreferences, clock, sink)
    }

    private fun trees() = Timber.forest().filterIsInstance<TelemetryErrorTree>()

    private fun lines() = store.files.keys.sorted().flatMap { store.lines(it).map(::parseLine) }

    @Test
    fun freshInstallDoesNotResolveDirectoryOrCreateWriterThread() {
        runtime.start { error("startup marks must not be built") }
        assertTrue(store.calls.isEmpty())
        assertEquals(0, executor.useCount)
        assertEquals(0, prefs.transactions.size)
    }

    @Test
    fun offStartPlantsNoTreeAndNoHandler() {
        runtime.start(null)
        assertEquals(forestBefore, Timber.forest())
        assertSame(handlerBefore, Thread.getDefaultUncaughtExceptionHandler())
        assertFalse(sink.isEnabled)
        assertNull(Telemetry.startupMarks)
    }

    @Test
    fun autoOffAtStartAfterSevenDays() {
        prefs.values[key] = clock.wall - week
        runtime.start(null)
        assertFalse(prefs.values.containsKey(key))
        assertFalse(sink.isEnabled)
        assertTrue(trees().isEmpty())
        assertEquals(0, executor.useCount)
    }

    @Test
    fun oneMillisecondBeforeSevenDaysStaysOn() {
        prefs.values[key] = clock.wall - week + 1
        runtime.start(null)
        executor.runAll()
        assertTrue(sink.isEnabled)
        assertEquals(1, trees().size)
        assertTrue(prefs.values.containsKey(key))
    }

    @Test
    fun autoOffOnNextDrainWhenExpiredWhileRunning() {
        runtime.setEnabled(true)
        executor.runAll()
        clock.advance(week)
        executor.runRepeating()
        assertFalse(sink.isEnabled)
        val countAfter = lines().size
        sink.record(TelemetryEvent.Nav(1, NavCmd.PUSH, null, null))
        executor.runRepeating()
        assertEquals(countAfter, lines().size)
    }

    @Test
    fun clockGoingBackwardsNeitherExpiresNorRewritesTheSwitchTime() {
        prefs.values[key] = clock.wall + day
        runtime.start(null)
        executor.runAll()
        assertEquals(clock.wall + day, prefs.values[key])
        assertEquals(SessionLogState.Recording(week, hasFiles = true), sink.status.value)
    }

    @Test
    fun aWrongFutureSwitchTimeStillExpiresOnRecordedUptime() {
        prefs.values[key] = clock.wall + 365 * day
        runtime.start(null)
        executor.runAll()
        assertTrue(sink.isEnabled)

        clock.elapsed += week - 1
        executor.runRepeating()
        assertTrue(sink.isEnabled)
        assertTrue((sink.status.value as SessionLogState.Recording).remainingMs <= 1 * 60 * 60 * 1000L)

        clock.elapsed += 1
        executor.runRepeating()

        assertFalse(sink.isEnabled)
        assertFalse(prefs.values.containsKey(key))
        assertEquals("auto_off", lines().last().str("ev"))
    }

    @Test
    fun clockSyncingForwardAfterAPreSyncBootDoesNotSwitchTheLogOff() {
        val enabledAt = clock.wall
        prefs.values[key] = enabledAt
        clock.wall = 946_684_800_000L // boot clock before time sync: 2000-01-01
        runtime.start(null)
        executor.runAll()

        clock.wall = enabledAt + day
        executor.runRepeating()

        assertTrue(sink.isEnabled)
        assertEquals(enabledAt, prefs.values[key])
        assertTrue(lines().none { it.str("ev") == "auto_off" })
    }

    @Test
    fun autoOffEmitsMetaClosesFileClearsPrefUprootsAndStatusOff() {
        runtime.setEnabled(true)
        executor.runAll()
        clock.advance(week + 1)
        executor.runRepeating()

        assertEquals("auto_off", lines().last().str("ev"))
        assertNull(store.currentName)
        assertFalse(prefs.values.containsKey(key))
        assertTrue(trees().isEmpty())
        assertSame(handlerBefore, Thread.getDefaultUncaughtExceptionHandler())
        assertEquals(SessionLogState.Off(hasFiles = true), runtime.state.value)
    }

    @Test
    fun enablingAtRuntimePlantsTreeAndHandlerWithoutRestart() {
        runtime.setEnabled(true)
        executor.runAll()

        assertEquals(1, trees().size)
        assertTrue(Thread.getDefaultUncaughtExceptionHandler() is TelemetryUncaughtHandler)
        assertTrue(sink.isEnabled)
        assertEquals(clock.wall, prefs.values[key])
        assertEquals(listOf("session", "meta"), lines().map { it.str("type") })
        assertEquals("start", lines().last().str("ev"))
    }

    @Test
    fun enablingTwiceNeverPlantsASecondTree() {
        runtime.setEnabled(true)
        runtime.setEnabled(true)
        executor.runAll()
        assertEquals(1, trees().size)
    }

    @Test
    fun tapWhileStorageIsUnavailablePlantsNoSecondTreeOrHandler() {
        store.available = false
        runtime.setEnabled(true)
        runtime.setEnabled(true)
        assertEquals(1, trees().size)
        executor.runAll()
        assertTrue(trees().isEmpty())
        assertSame(handlerBefore, Thread.getDefaultUncaughtExceptionHandler())
        assertEquals(SessionLogState.Unavailable, runtime.state.value)

        runtime.setEnabled(true)
        runtime.setEnabled(true)
        val handler = Thread.getDefaultUncaughtExceptionHandler() as TelemetryUncaughtHandler
        assertSame(handlerBefore, handler.previous)
        executor.runAll()
        assertTrue(trees().isEmpty())
        assertSame(handlerBefore, Thread.getDefaultUncaughtExceptionHandler())
    }

    @Test
    fun disablingUprootsTreeAndRestoresHandler() {
        runtime.setEnabled(true)
        executor.runAll()
        runtime.setEnabled(false)
        executor.runAll()

        assertEquals(forestBefore, Timber.forest())
        assertSame(handlerBefore, Thread.getDefaultUncaughtExceptionHandler())
        assertFalse(sink.isEnabled)
        assertFalse(prefs.values.containsKey(key))
        assertEquals("stop", lines().last().str("ev"))
        assertEquals(SessionLogState.Off(hasFiles = true), runtime.state.value)
        runtime.setEnabled(false)
        executor.runAll()
    }

    @Test
    fun enabledStateSurvivesNewRuntimeInstance() {
        runtime.setEnabled(true)
        executor.runAll()
        val enabledAt = prefs.values[key]
        runtime.detachProbes() // process death: the probes die with the process, the preference stays
        clock.advance(day)

        build()
        runtime.start(null)
        executor.runAll()

        assertTrue(sink.isEnabled)
        assertEquals(enabledAt, prefs.values[key])
        assertEquals(
            SessionLogState.Recording(week - day, hasFiles = true),
            sink.status.value,
        )
    }

    @Test
    fun coldStartResumeBuildsStartupMarksAndSchedulesFallback() {
        prefs.values[key] = clock.wall
        var marks: StartupMarks? = null
        runtime.start { StartupMarks(0, 0, sink::record, clock).also { marks = it } }
        assertSame(marks, Telemetry.startupMarks)
        assertEquals(listOf(60_000L), executor.scheduledDelays)
        executor.runAll()
        executor.runScheduled()
        executor.runRepeating()
        assertTrue(lines().any { it.str("type") == "startup" })
    }
}
