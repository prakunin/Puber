package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ErrFolderAndCapTest {
    private val out = mutableListOf<TelemetryEvent.Err>()
    private val folder = ErrFolder()

    private fun emit(event: TelemetryEvent) {
        out += event as TelemetryEvent.Err
    }

    private fun error(frame: String = "a"): Throwable = IllegalStateException("x").apply {
        stackTrace = arrayOf(StackTraceElement("com.kino.puber.$frame", "run", "F.kt", 1))
    }

    private fun err(t: Long, e: Throwable) = TelemetryEvent.Err(t, ErrSource.LOG, e)

    @Test
    fun identicalErrorsFoldWithinSixtySeconds() {
        val e = error()
        folder.offer(err(0, e), ::emit)
        folder.offer(err(10_000, e), ::emit)
        folder.offer(err(59_999, e), ::emit)
        assertEquals(listOf(1), out.map { it.n })
        folder.flush(60_000, force = false, emit = ::emit)
        assertEquals(listOf(1, 2), out.map { it.n })
    }

    @Test
    fun anOccurrenceAfterTheWindowEmitsFollowUpThenStartsANewWindow() {
        val e = error()
        folder.offer(err(0, e), ::emit)
        folder.offer(err(1, e), ::emit)
        folder.offer(err(61_000, e), ::emit)
        assertEquals(listOf(1, 1, 1), out.map { it.n })
    }

    @Test
    fun aCrashIsNeverFoldedIntoAnEarlierLoggedError() {
        val e = error()
        assertTrue(folder.accepts(err(0, e)))
        assertFalse(folder.accepts(TelemetryEvent.Err(1, ErrSource.UNCAUGHT, e)))

        val h = SinkHarness()
        h.start()
        h.sink.record(err(h.clock.elapsed, e))
        h.sink.record(TelemetryEvent.Err(h.clock.elapsed, ErrSource.UNCAUGHT, e))
        h.sink.flushBlocking(1_000)
        h.executor.runAll()

        assertEquals(listOf("log", "uncaught"), h.linesOf("err").map { it.str("src") })
    }

    @Test
    fun aCrashIsWrittenEvenAfterTheErrCapIsReached() {
        val h = SinkHarness()
        h.start()
        repeat(21) { h.sink.record(err(h.clock.elapsed, error("distinct$it"))) }
        h.sink.record(TelemetryEvent.Err(h.clock.elapsed, ErrSource.UNCAUGHT, error("crash")))
        h.drain()

        assertEquals(1, h.linesOf("err").count { it.str("src") == "uncaught" })
    }

    @Test
    fun differentFirstFramesDoNotFold() {
        folder.offer(err(0, error("a")), ::emit)
        folder.offer(err(1, error("b")), ::emit)
        assertEquals(2, out.size)
    }

    @Test
    fun keyCountIsBounded() {
        repeat(40) { folder.offer(err(it.toLong(), error("f$it")), ::emit) }
        folder.offer(err(100, error("f0")), ::emit)
        assertEquals(41, out.size)
    }

    @Test
    fun twentyFirstDistinctErrorInAMinuteIsDroppedAndCounted() {
        val h = SinkHarness()
        h.start()
        repeat(21) { h.sink.record(err(h.clock.elapsed, error("distinct$it"))) }
        h.drain()
        assertEquals(20, h.linesOf("err").size)
        val dropped = h.linesOf("meta").single { it.str("ev") == "dropped" }
        assertEquals("""{"err":1}""", dropped["counts"].toString())
    }
}
