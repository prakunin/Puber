package com.kino.puber.core.telemetry

import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.RecordingTelemetryRecorder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import timber.log.Timber

internal class TelemetryErrorTreeTest {
    private val recorder = RecordingTelemetryRecorder()
    private val tree = TelemetryErrorTree()

    @BeforeEach
    fun setUp() {
        Telemetry.install(recorder, FakeTelemetryClock())
        Timber.plant(tree)
    }

    @AfterEach
    fun tearDown() {
        Timber.uproot(tree)
        Telemetry.resetForTests()
    }

    @Test
    fun onlyWarnAndAboveWithThrowableIsRecorded() {
        val t = IllegalStateException("x")
        Timber.d(t, "debug")
        Timber.i(t, "info")
        Timber.w("message only")
        Timber.e("message only")
        Timber.w(null as Throwable?, "null throwable")
        assertEquals(0, recorder.events.size)

        Timber.w(t, "warn")
        Timber.e(t)
        Timber.tag("tag").e(t, "tagged")
        assertEquals(3, recorder.events.size)
        val first = recorder.events.first() as TelemetryEvent.Err
        assertSame(t, first.error)
        assertEquals(ErrSource.LOG, first.src)
    }

    @Test
    fun nothingIsRecordedWhileDisabled() {
        recorder.isEnabled = false
        Timber.e(IllegalStateException("x"), "boom")
        assertEquals(0, recorder.events.size)
    }
}
