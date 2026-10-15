package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TelemetryExportTest {
    private val sevenDays = TelemetrySink.SEVEN_DAYS_MS

    private fun nav() = TelemetryEvent.Nav(1, NavCmd.PUSH, HomeScreen::class.java, ScreenScreen::class.java)

    private fun SinkHarness.share(): ShareResult? {
        var result: ShareResult? = null
        sink.export { result = it }
        executor.runAll()
        return result
    }

    @Test
    fun exportContainsEventsStillQueuedWithoutAPeriodicDrain() {
        val h = SinkHarness()
        h.start()
        h.sink.record(nav().copy(t = h.clock.elapsed))

        assertTrue(h.share() is ShareResult.Ready)

        val archived = h.store.zips.single().second.flatMap { h.store.lines(it) }.map(::parseLine)
        assertEquals(1, archived.count { it.str("type") == "nav" })
    }

    @Test
    fun exportFlushesPendingFoldsOfTheCurrentSession() {
        val h = SinkHarness()
        h.start()
        val error = IllegalStateException("boom")
        repeat(3) { h.sink.record(TelemetryEvent.Err(h.clock.elapsed + it, ErrSource.LOG, error)) }

        h.share()

        val archived = h.store.zips.single().second.flatMap { h.store.lines(it) }.map(::parseLine)
        assertEquals(listOf("1", "2"), archived.filter { it.str("type") == "err" }.map { it.str("n") })
    }

    @Test
    fun exportCarriesTheDroppedCountOfAQueueOverflow() {
        val h = SinkHarness()
        h.start()
        // Meta has no per-minute cap, so the only drops are the overflow ones.
        repeat(TelemetrySink.QUEUE_BOUND + 5) { h.sink.record(TelemetryEvent.Meta(1, MetaEv.ROTATE)) }

        h.share()

        val archived = h.store.zips.single().second.flatMap { h.store.lines(it) }.map(::parseLine)
        val dropped = archived.single { it.str("type") == "meta" && it.str("ev") == "dropped" }
        assertTrue(dropped.toString().contains("\"meta\":5"), dropped.toString())
    }

    @Test
    fun exportAlwaysAnswersEvenWhenTheStoreThrowsSomethingElse() {
        val h = SinkHarness()
        h.start()
        h.sink.record(nav())
        h.store.zipFailure = IllegalStateException("not an io error")

        assertEquals(ShareResult.Failed, h.share())
    }

    @Test
    fun exportRemovesOrphanedTmpFilesAndStaleArchives() {
        val h = SinkHarness()
        h.start()
        h.sink.record(nav())
        h.store.seed("puber-telemetry-old.zip", 10)
        h.store.seed("puber-telemetry-x.zip.tmp", 10)

        h.share()

        assertFalse(h.store.files.containsKey("puber-telemetry-old.zip"))
        assertFalse(h.store.files.containsKey("puber-telemetry-x.zip.tmp"))
    }

    @Test
    fun eventsLeftInTheQueueAtAutoOffNeverReachTheNextSession() {
        val h = SinkHarness()
        h.start()
        h.clock.advance(sevenDays)
        // Enqueued after the drain emptied the queue and before the expiry switched recording off.
        h.store.onFlush = {
            h.store.onFlush = null
            h.sink.record(nav().copy(t = h.clock.elapsed))
        }
        h.drain()
        assertFalse(h.sink.isEnabled)

        // The clock stays put: the restarted origin equals the leftover event's time, so only the
        // queue clear in expire() keeps it out.
        h.start()
        h.drain()

        assertEquals(0, h.linesOf("nav").size)
    }

    @Test
    fun aStalledProducerEventOlderThanTheOriginIsDroppedWhenDrained() {
        val h = SinkHarness()
        h.clock.advance(1_000)
        h.start()
        h.sink.record(nav().copy(t = h.clock.elapsed - 500))
        h.sink.record(nav().copy(t = h.clock.elapsed + 10))
        h.drain()

        assertEquals(listOf("10"), h.linesOf("nav").map { it.str("t") })
    }

    @Test
    fun eventsOlderThanTheNewOriginAreDroppedOnStart() {
        val h = SinkHarness()
        h.clock.advance(1_000)
        h.sink.start(h.clock.wall, onExpired = {}, onStopped = {})
        // A producer that passed the enabled check before the previous session ended.
        h.sink.record(nav().copy(t = h.clock.elapsed - 500))
        h.sink.record(nav().copy(t = h.clock.elapsed + 10))
        h.executor.runAll()
        h.drain()

        assertEquals(listOf("10"), h.linesOf("nav").map { it.str("t") })
    }

    @Test
    fun everyRecordingSessionHasItsOwnSidAndTimeOrigin() {
        val h = SinkHarness()
        h.start()
        h.clock.advance(5_000)
        h.sink.stop()
        h.executor.runAll()
        h.clock.advance(60_000)
        h.start()

        val sessions = h.linesOf("session")
        assertEquals(2, sessions.size)
        assertNotEquals(sessions[0].str("sid"), sessions[1].str("sid"))
        assertEquals("0", sessions[0].str("t"))
        assertEquals("0", sessions[1].str("t"))
    }

    @Test
    fun shareBeforeTheFirstEnableDoesNotFixTheTimeOrigin() {
        val h = SinkHarness()
        h.share()
        h.clock.advance(3_600_000)
        h.start()

        assertEquals("0", h.linesOf("session").single().str("t"))
        assertNotNull(h.linesOf("session").single().str("sid"))
    }
}
