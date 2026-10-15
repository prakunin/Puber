package com.kino.puber.core.telemetry

import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.FakeTelemetryStore
import com.kino.puber.util.ManualTelemetryExecutor
import com.kino.puber.util.RecordingTelemetryRecorder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.time.Duration
import java.util.concurrent.CountDownLatch

internal class TelemetryUncaughtHandlerTest {
    private var saved: Thread.UncaughtExceptionHandler? = null
    private val calls = mutableListOf<Pair<Thread, Throwable>>()
    private val previous = Thread.UncaughtExceptionHandler { t, e -> calls += t to e }

    @BeforeEach
    fun setUp() {
        saved = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(previous)
    }

    @AfterEach
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(saved)
        Telemetry.resetForTests()
    }

    private fun manualSink() =
        TelemetrySink(FakeTelemetryClock(), FakeTelemetryStore(), ManualTelemetryExecutor(), { testSessionInfo })

    private fun realSink(store: FakeTelemetryStore): TelemetrySink {
        val executor = ThreadTelemetryExecutor()
        val sink = TelemetrySink(FakeTelemetryClock(), store, executor, { testSessionInfo }, "deadbeef")
        Telemetry.install(sink, FakeTelemetryClock())
        return sink
    }

    @Test
    fun previousHandlerCalledOnceWithSameThreadAndThrowable() {
        val store = FakeTelemetryStore()
        val sink = realSink(store)
        sink.start(System.currentTimeMillis(), {}, {})
        val handler = TelemetryUncaughtHandler.install(sink)
        val error = IllegalStateException("boom")

        handler.uncaughtException(Thread.currentThread(), error)

        assertEquals(1, calls.size)
        assertSame(Thread.currentThread(), calls.single().first)
        assertSame(error, calls.single().second)
        val lines = store.files.values.flatten().map(::parseLine)
        assertTrue(lines.any { it.str("src") == "uncaught" })
        TelemetryUncaughtHandler.uninstall(handler)
        sink.stop()
    }

    @Test
    fun previousHandlerStillCalledWhenSinkThrows() {
        val recorder = RecordingTelemetryRecorder().apply { failWith = IllegalStateException("sink") }
        Telemetry.install(recorder, FakeTelemetryClock())
        val sink = manualSink()
        val handler = TelemetryUncaughtHandler(previous, sink)
        val error = RuntimeException("crash")

        handler.uncaughtException(Thread.currentThread(), error)

        assertEquals(1, calls.size)
        assertSame(error, calls.single().second)
    }

    @Test
    fun flushIsBoundedAndSkippedWhenCrashIsOnWriterThread() {
        val store = FakeTelemetryStore()
        val gate = CountDownLatch(1)
        store.blockAppend = gate
        val sink = realSink(store)
        sink.start(System.currentTimeMillis(), {}, {}) // the writer blocks in its first append
        val handler = TelemetryUncaughtHandler(previous, sink)

        assertTimeoutPreemptively(Duration.ofSeconds(4)) {
            handler.uncaughtException(Thread.currentThread(), RuntimeException("crash"))
        }
        assertEquals(1, calls.size)
        gate.countDown()
        sink.stop()

        // On the writer thread the handler must not wait for the writer (itself).
        val manual = ManualTelemetryExecutor()
        val manualSink = TelemetrySink(FakeTelemetryClock(), FakeTelemetryStore(), manual, { testSessionInfo })
        Telemetry.install(manualSink, FakeTelemetryClock())
        manualSink.start(System.currentTimeMillis(), {}, {})
        manual.runAll()
        val onWriter = TelemetryUncaughtHandler(previous, manualSink)
        calls.clear()
        manual.execute { onWriter.uncaughtException(Thread.currentThread(), RuntimeException("on writer")) }
        assertTimeoutPreemptively(Duration.ofSeconds(1)) { manual.runAll() }
        assertEquals(1, calls.size)
    }

    @Test
    fun disableRestoresPreviousHandlerOnlyIfStillDefault() {
        val sink = manualSink()
        val ours = TelemetryUncaughtHandler.install(sink)
        TelemetryUncaughtHandler.uninstall(ours)
        assertSame(previous, Thread.getDefaultUncaughtExceptionHandler())

        val second = TelemetryUncaughtHandler.install(sink)
        val foreign = Thread.UncaughtExceptionHandler { _, _ -> }
        Thread.setDefaultUncaughtExceptionHandler(foreign)
        TelemetryUncaughtHandler.uninstall(second)
        assertSame(foreign, Thread.getDefaultUncaughtExceptionHandler())
        assertFalse(second.active)
    }
}
