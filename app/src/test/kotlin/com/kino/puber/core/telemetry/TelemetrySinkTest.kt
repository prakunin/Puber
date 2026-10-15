package com.kino.puber.core.telemetry

import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.FakeTelemetryStore
import com.kino.puber.util.ManualTelemetryExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Duration
import java.util.concurrent.CountDownLatch
import kotlin.reflect.KClass

internal class TelemetrySinkTest {
    @TempDir
    lateinit var root: File

    private fun nav() = TelemetryEvent.Nav(1, NavCmd.PUSH, HomeScreen::class.java, ScreenScreen::class.java)

    @Test
    fun disabledRecordNeverReachesEncoderOrStore() {
        val h = SinkHarness()
        repeat(100) { h.sink.record(nav()) }
        h.executor.runAll()
        h.drain()
        assertEquals(0, h.infoCalls)
        assertTrue(h.store.calls.isEmpty())
        assertEquals(0, h.executor.useCount)
        assertFalse(h.sink.isEnabled)
    }

    @Test
    fun nullDirectoryKeepsRecordingOffAndStatusUnavailable() {
        val h = SinkHarness()
        h.store.available = false
        h.start()
        assertFalse(h.sink.isEnabled)
        assertEquals(SessionLogState.Unavailable, h.sink.status.value)
        assertEquals(1, h.stopped)
        assertTrue(h.store.files.isEmpty())
        h.sink.record(nav())
        h.drain()
        assertTrue(h.store.files.isEmpty())
    }

    @Test
    fun ioErrorTurnsRecordingOffWithMetaUnavailableAndNeverThrows() {
        val h = SinkHarness()
        h.store.failNextAppends = 1
        h.start()
        assertFalse(h.sink.isEnabled)
        assertEquals(SessionLogState.Unavailable, h.sink.status.value)
        assertEquals(listOf("unavailable"), h.linesOf("meta").map { it.str("ev") })
        assertEquals(1, h.stopped)
        h.sink.record(nav())
        h.drain()
    }

    @Test
    fun persistentIoErrorNeverThrows() {
        val h = SinkHarness()
        h.store.throwOnAppend = true
        h.start()
        assertEquals(SessionLogState.Unavailable, h.sink.status.value)
        assertFalse(h.sink.isEnabled)
    }

    @Test
    fun fileNamingAndAppendOnTempDir() {
        val store = FileTelemetryStore { root }
        val clock = FakeTelemetryClock(elapsed = 0, wall = 1_700_000_000_000L)
        val executor = ManualTelemetryExecutor()
        val sink = TelemetrySink(clock, store, executor, { testSessionInfo }, sid = "deadbeef")
        sink.start(clock.wall, {}, {})
        executor.runAll()
        sink.record(nav())
        executor.runRepeating()
        sink.stop()
        executor.runAll()

        val files = File(root, "telemetry").listFiles().orEmpty()
        assertEquals(1, files.size)
        assertTrue(Regex("""s-\d{8}-\d{6}-deadbeef-01\.jsonl""").matches(files.single().name))
        val text = files.single().readText()
        assertTrue(text.endsWith("\n"))
        val parsed = text.lines().filter(String::isNotEmpty).map(::parseLine)
        assertEquals(listOf("session", "meta", "nav", "meta"), parsed.map { it.str("type") })
        assertEquals("Home", parsed[2].str("from"))
        assertEquals("Screen", parsed[2].str("to"))
    }

    @Test
    fun encodingAndFileIoRunOnlyOnWriterThread() {
        val store = FakeTelemetryStore()
        val infoThreads = java.util.Collections.synchronizedList(mutableListOf<String>())
        val sink = TelemetrySink(
            FakeTelemetryClock(elapsed = 0),
            store,
            ThreadTelemetryExecutor(),
            { infoThreads += Thread.currentThread().name; testSessionInfo },
            sid = "deadbeef",
        )
        sink.start(System.currentTimeMillis(), {}, {})
        sink.record(nav())
        assertTrue(sink.flushBlocking(5_000))
        sink.stop()
        assertTrue(sink.flushBlocking(5_000)) // queued behind the stop task

        assertTrue(store.calls.isNotEmpty())
        assertTrue(store.files.values.flatten().any { parseLine(it).str("type") == "nav" })
        assertEquals(setOf(ThreadTelemetryExecutor.THREAD_NAME), store.calls.map { it.second }.toSet())
        assertEquals(listOf(ThreadTelemetryExecutor.THREAD_NAME), infoThreads)
    }

    @Test
    fun recordReturnsWhileStoreIsBlocked() {
        val store = FakeTelemetryStore()
        val gate = CountDownLatch(1)
        store.blockAppend = gate
        val executor = ThreadTelemetryExecutor()
        val sink = TelemetrySink(FakeTelemetryClock(elapsed = 0), store, executor, { testSessionInfo }, "deadbeef")
        sink.start(System.currentTimeMillis(), {}, {})
        assertTimeoutPreemptively(Duration.ofSeconds(2)) { repeat(100) { sink.record(nav()) } }
        gate.countDown()
        assertTrue(sink.flushBlocking(5_000))
        assertEquals(100, store.files.values.flatten().count { parseLine(it).str("type") == "nav" })
        sink.stop()
    }

    @Test
    fun eventsHoldNoStringBuiltByTheProbe() {
        val members: List<KClass<out TelemetryEvent>> = TelemetryEvent::class.sealedSubclasses
        assertTrue(members.size >= 7)
        for (member in members) {
            val strings = member.java.declaredFields.filter { it.type == String::class.java }
            assertTrue(strings.isEmpty(), "${member.simpleName} holds a String: $strings")
        }
    }

    @Test
    fun statusIsRecordingWithRemainingHoursFlooredAndOffAfterStop() {
        val h = SinkHarness()
        h.start()
        val recording = h.sink.status.value as SessionLogState.Recording
        assertEquals(7L * 24 * 3_600_000, recording.remainingMs)
        h.clock.advance(61 * 60_000L)
        h.drain()
        assertEquals(
            7L * 24 * 3_600_000 - 2 * 3_600_000,
            (h.sink.status.value as SessionLogState.Recording).remainingMs,
        )
        h.sink.stop()
        h.executor.runAll()
        assertEquals(SessionLogState.Off(hasFiles = true), h.sink.status.value)
        assertEquals(listOf("start", "stop"), h.linesOf("meta").map { it.str("ev") })
    }

    @Test
    fun actsFoldReadyAndNavCarryScrAndStopFlushesThePendingFold() {
        val h = SinkHarness()
        h.start()
        h.sink.record(TelemetryEvent.Nav(2_000, NavCmd.PUSH, null, HomeScreen::class.java))
        repeat(3) {
            h.sink.record(TelemetryEvent.Act(2_100L + it, HomeVM::class.java, SampleState.Content::class.java))
        }
        h.sink.record(TelemetryEvent.Ready(2_200, HomeVM::class.java, SampleState.Content::class.java, 200))
        h.drain()
        // The burst is younger than a second: still held by the folder.
        assertEquals(0, h.linesOf("act").size)

        h.sink.stop()
        h.executor.runAll()

        val act = h.linesOf("act").single()
        assertEquals("3", act.str("n"))
        assertEquals("Home", h.linesOf("nav").single().str("scr"))
        assertEquals("Home", act.str("scr"))
        assertEquals("Home", h.linesOf("ready").single().str("scr"))
        val stop = h.linesOf("meta").last()
        assertEquals("stop", stop.str("ev"))
        assertEquals("Home", stop.str("scr"))
    }

    @Test
    fun actAndReadyCarryTheScreenOfTheirOwnViewModelNotTheLastNavigation() {
        val h = SinkHarness()
        h.start()
        h.sink.record(TelemetryEvent.Nav(2_000, NavCmd.PUSH, null, HomeScreen::class.java))
        h.sink.record(TelemetryEvent.Act(2_100, DialogVM::class.java, SampleState.Content::class.java))
        h.sink.record(TelemetryEvent.Ready(2_200, DialogVM::class.java, SampleState.Content::class.java, 100))
        h.drain()
        h.sink.stop()
        h.executor.runAll()

        assertEquals("Dialog", h.linesOf("act").single().str("scr"))
        assertEquals("Dialog", h.linesOf("ready").single().str("scr"))
        assertEquals("Home", h.linesOf("meta").last().str("scr"))
    }

    @Test
    fun actCapAppliesAfterFoldingAndTheOverflowIsCounted() {
        val h = SinkHarness()
        h.start()
        repeat(250) {
            val action = if (it % 2 == 0) SampleState.Content::class.java else ScreenScreen::class.java
            h.sink.record(TelemetryEvent.Act(h.clock.elapsed, HomeVM::class.java, action))
        }
        h.drain()
        h.sink.stop()
        h.executor.runAll()

        assertEquals(240, h.linesOf("act").size)
        val dropped = h.linesOf("meta").filter { it.str("ev") == "dropped" }
            .sumOf { Regex("\"act\":(\\d+)").find(it.toString())?.groupValues?.get(1)?.toInt() ?: 0 }
        assertEquals(10, dropped)
    }
}
