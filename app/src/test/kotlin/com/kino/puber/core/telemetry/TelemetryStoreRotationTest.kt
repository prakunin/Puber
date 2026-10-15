package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TelemetryStoreRotationTest {
    private val mib = 1024L * 1024

    private fun jsonl(n: Int) = "s-20200101-0000${n.toString().padStart(2, '0')}-aaaaaaaa-01.jsonl"

    private fun SinkHarness.fill(lines: Int) {
        var left = lines
        while (left > 0) {
            val batch = minOf(left, 4_000)
            repeat(batch) { sink.record(TelemetryEvent.Meta(1, MetaEv.START)) }
            drain()
            left -= batch
        }
    }

    @Test
    fun rotatesAtTwoMiB() {
        val h = SinkHarness()
        h.start()
        h.fill(36_000)
        val names = h.store.files.keys.filter { it.startsWith("s-") && it.contains("deadbeef") }.sorted()
        assertTrue(names.size >= 2, "files: $names")
        assertTrue(h.store.sizes.getValue(names.first()) <= 2 * mib)
        val second = parseLine(h.store.lines(names[1]).first())
        assertEquals("rotate", second.str("ev"))
        assertTrue(names[1].endsWith("-02.jsonl"))
    }

    @Test
    fun oldestDeletedFirstAtTwentyMiBAndNeverTheOpenFile() {
        val h = SinkHarness()
        repeat(10) { h.store.seed(jsonl(it), 2 * mib) }
        h.start()
        assertFalse(h.store.files.containsKey(jsonl(0)))
        assertTrue(h.store.files.containsKey(jsonl(1)))
        val open = requireNotNull(h.store.currentName)
        assertTrue(h.store.files.containsKey(open))
    }

    @Test
    fun staleArchiveIsDeletedBeforeAnyLogWhenOverBudget() {
        val h = SinkHarness()
        repeat(9) { h.store.seed(jsonl(it), 2 * mib) }
        h.store.seed("puber-telemetry-old.zip", 2 * mib)
        h.start()
        assertFalse(h.store.files.containsKey("puber-telemetry-old.zip"))
        assertTrue(h.store.files.containsKey(jsonl(0)))
    }

    @Test
    fun orphanedTmpCountsTowardTheBudgetAndGoesBeforeAnyLog() {
        val h = SinkHarness()
        repeat(9) { h.store.seed(jsonl(it), 2 * mib) }
        h.store.seed("puber-telemetry-x.zip.tmp", 2 * mib)
        h.start()
        assertFalse(h.store.files.containsKey("puber-telemetry-x.zip.tmp"))
        assertTrue(h.store.files.containsKey(jsonl(0)))
    }

    @Test
    fun theNewestExportedArchiveSurvivesTheBudgetWhileLogsAndStaleArchivesGo() {
        val h = SinkHarness()
        repeat(9) { h.store.seed(jsonl(it), 2 * mib) }
        h.start()
        h.sink.record(TelemetryEvent.Nav(1, NavCmd.PUSH, null, HomeScreen::class.java))
        var shared: ShareResult? = null
        h.sink.export { shared = it }
        h.executor.runAll()
        val archive = (shared as ShareResult.Ready).archive.name
        // Logs are near the budget; the next event rotates into a new file and enforces it.
        h.store.seed(jsonl(20), 2 * mib)
        h.store.seed("puber-telemetry-old.zip", mib)
        h.sink.record(TelemetryEvent.Nav(2, NavCmd.PUSH, null, ScreenScreen::class.java))
        h.drain()

        assertTrue(h.store.files.containsKey(archive), "files: ${h.store.files.keys}")
        assertFalse(h.store.files.containsKey("puber-telemetry-old.zip"))
        assertFalse(h.store.files.containsKey(jsonl(0)))
    }

    @Test
    fun logsAreDeletedOldestFirstOnceArchivesAreGone() {
        val h = SinkHarness()
        repeat(10) { h.store.seed(jsonl(it), 2 * mib) }
        h.store.seed("puber-telemetry-old.zip", 2 * mib)
        h.start()
        assertFalse(h.store.files.containsKey("puber-telemetry-old.zip"))
        assertFalse(h.store.files.containsKey(jsonl(0)))
        assertTrue(h.store.files.containsKey(jsonl(1)))
    }

    @Test
    fun underTheBudgetNothingIsDeleted() {
        val h = SinkHarness()
        repeat(9) { h.store.seed(jsonl(it), 2 * mib) }
        h.start()
        assertTrue(h.store.calls.none { it.first == "delete" })
    }
}
