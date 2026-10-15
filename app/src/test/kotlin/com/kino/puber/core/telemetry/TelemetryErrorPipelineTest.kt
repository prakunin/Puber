package com.kino.puber.core.telemetry

import com.kino.puber.core.logger.log
import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.RecordingTelemetryRecorder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import timber.log.Timber

internal class TelemetryErrorPipelineTest {
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
    fun errLineUsesSourceTypeSanitizedMessageAndAppFramesOnly() {
        val source = IllegalStateException("failed GET https://api.example.com/v1/items?access_token=tok123 now")
        source.stackTrace = arrayOf(
            StackTraceElement("java.util.Objects", "check", "Objects.java", 1),
            StackTraceElement("okhttp3.Call", "execute", "Call.kt", 2),
        ) + Array(7) { StackTraceElement("com.kino.puber.data.Repo$it", "load", "Repo.kt", 10 + it) }

        this.log(source)

        val err = recorder.events.single() as TelemetryEvent.Err
        val encoder = TelemetryEncoder("abcd1234", 0, TelemetryNames(), { testSessionInfo }, { 0 })
        val json = encoder.encode(err, "")

        assertEquals("java.lang.IllegalStateException", json.str("et"))
        val line = json.toString()
        assertFalse(line.contains("tok123"))
        assertFalse(line.contains("api.example.com"))
        assertTrue(json.str("msg").orEmpty().isNotEmpty())
        val frames = json["frames"].toString()
        assertEquals(5, Regex("Repo\\d\\.load").findAll(frames).count())
        assertFalse(frames.contains("Objects") || frames.contains("okhttp3") || frames.contains("Call"))
    }
}
