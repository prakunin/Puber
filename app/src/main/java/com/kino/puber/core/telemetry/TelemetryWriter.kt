package com.kino.puber.core.telemetry

import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * The writer pipeline: folders, per-type cap, encode, rotate and budget, append. Writer-thread
 * state only, no locks; a plain class so tests can drive it synchronously.
 */
internal class TelemetryWriter(
    private val clock: TelemetryClock,
    private val store: TelemetryStore,
    private val encoder: TelemetryEncoder,
    private val names: TelemetryNames,
    private val sid: String,
    private val folders: List<EventFolder> = listOf(ActFolder(), ErrFolder()),
    private val onUnavailable: () -> Unit,
) {
    private val capCounts = IntArray(TelemetryEventType.entries.size)
    private val dropped = IntArray(TelemetryEventType.entries.size)
    private var capMinute = -1L
    private var lastScr = ""
    private var currentName: String? = null
    private var currentBytes = 0L
    private var rotatePending = false
    private var fileIndex = 0
    private var unavailable = false

    fun write(event: TelemetryEvent) = guard {
        if (unavailable) return@guard
        val folder = folders.firstOrNull { it.accepts(event) }
        if (folder == null) accept(event) else folder.offer(event, ::accept)
    }

    fun noteDropped(type: TelemetryEventType, count: Int) {
        dropped[type.ordinal] += count
    }

    /** End of one drain: due folders, the `dropped` meta line, then a flush to disk. */
    fun finishDrain(force: Boolean = false) = guard {
        if (unavailable) return@guard
        flushFolders(force)
        emitDropped()
        store.flush()
    }

    fun closeFile() = guard {
        if (currentName != null) {
            store.closeCurrent()
            currentName = null
        }
    }

    fun export(): ShareResult = try {
        if (!unavailable) flushFolders(force = true)
        if (currentName != null) {
            store.closeCurrent()
            currentName = null
            rotatePending = true
        }
        val listing = store.list()
        listing.filter { !it.name.endsWith(JSONL) }.forEach { store.delete(it.name) }
        val sources = listing.filter { it.name.endsWith(JSONL) }.map { it.name }
        if (sources.isEmpty()) {
            ShareResult.NothingToShare
        } else {
            ShareResult.Ready(store.zip(sources, "puber-telemetry-$sid-${formatted(ARCHIVE_PATTERN)}$ZIP"))
        }
    } catch (_: IOException) {
        ShareResult.Failed
    }

    private fun flushFolders(force: Boolean) {
        val now = clock.elapsedMs()
        folders.forEach { it.flush(now, force, ::accept) }
    }

    private fun accept(event: TelemetryEvent) {
        if (event is TelemetryEvent.Err && event.src == ErrSource.UNCAUGHT) {
            // At most one per process and the one line a crash report cannot do without: no cap.
            append(event)
            return
        }
        val type = event.type
        val minute = clock.wallMs() / MS_PER_MINUTE
        if (minute != capMinute) {
            capMinute = minute
            capCounts.fill(0)
        }
        if (capCounts[type.ordinal] >= type.capPerMinute) {
            dropped[type.ordinal]++
            return
        }
        capCounts[type.ordinal]++
        append(event)
    }

    private fun emitDropped() {
        if (dropped.all { it == 0 }) return
        val counts = TelemetryEventType.entries
            .filter { dropped[it.ordinal] > 0 }
            .associateWith { dropped[it.ordinal] }
        dropped.fill(0)
        append(TelemetryEvent.Meta(clock.elapsedMs(), MetaEv.DROPPED, counts))
    }

    private fun append(event: TelemetryEvent) {
        val scr = when (event) {
            is TelemetryEvent.Nav -> names.owner(event.to)
            is TelemetryEvent.Ready -> names.owner(event.vm)
            is TelemetryEvent.Act -> names.owner(event.vm)
            else -> lastScr
        }
        val line = encoder.line(event, scr)
        val size = line.toByteArray(Charsets.UTF_8).size
        if (currentName != null && currentBytes + size > MAX_FILE_BYTES) {
            store.closeCurrent()
            currentName = null
            rotatePending = true
        }
        val name = currentName ?: openFile()
        store.append(name, line)
        currentBytes += size
        if (event is TelemetryEvent.Nav) lastScr = scr
    }

    private fun openFile(): String {
        enforceBudget()
        fileIndex++
        val name = "s-${formatted(FILE_PATTERN)}-$sid-${fileIndex.toString().padStart(2, '0')}$JSONL"
        currentName = name
        currentBytes = 0
        if (rotatePending) {
            rotatePending = false
            val line = encoder.line(TelemetryEvent.Meta(clock.elapsedMs(), MetaEv.ROTATE), lastScr)
            store.append(name, line)
            currentBytes += line.toByteArray(Charsets.UTF_8).size
        }
        return name
    }

    private fun enforceBudget() {
        val files = store.list()
        var total = files.sumOf { it.sizeBytes }
        // The newest archive may still be read by the app it was shared with: it is only replaced
        // by the next export. Stale archives and orphaned temporary files go before any log.
        val newestArchive = files.map { it.name }.filter(ARCHIVE_NAME::containsMatchIn)
            .maxByOrNull { it.takeLast(ARCHIVE_STAMP_LENGTH) }
        val (logs, others) = files.filter { it.name != newestArchive }.partition { it.name.endsWith(JSONL) }
        val closed = (others + logs).map { it.name to it.sizeBytes }.toMutableList()
        while (total > TOTAL_BUDGET_BYTES - MAX_FILE_BYTES && closed.isNotEmpty()) {
            val (oldest, size) = closed.removeAt(0)
            if (oldest == currentName) continue
            store.delete(oldest)
            total -= size
        }
    }

    private fun formatted(pattern: String): String {
        val format = SimpleDateFormat(pattern, Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(clock.wallMs())
    }

    private inline fun guard(block: () -> Unit) {
        try {
            block()
        } catch (_: IOException) {
            fail()
        }
    }

    private fun fail() {
        if (unavailable) return
        try {
            store.closeCurrent()
        } catch (_: IOException) {
            // The store is already failing; nothing to release.
        }
        currentName = null
        try {
            append(TelemetryEvent.Meta(clock.elapsedMs(), MetaEv.UNAVAILABLE))
            store.closeCurrent()
        } catch (_: IOException) {
            // Best effort only: the point is that recording stops, quietly.
        }
        currentName = null
        unavailable = true
        onUnavailable()
    }

    private companion object {
        const val JSONL = ".jsonl"
        const val ZIP = ".zip"
        val ARCHIVE_NAME = Regex("""\d{8}-\d{4}\.zip$""")
        const val ARCHIVE_STAMP_LENGTH = 17 // yyyyMMdd-HHmm.zip
        const val FILE_PATTERN = "yyyyMMdd-HHmmss"
        const val ARCHIVE_PATTERN = "yyyyMMdd-HHmm"
        const val MS_PER_MINUTE = 60_000L
        const val MAX_FILE_BYTES = 2L * 1024 * 1024
        const val TOTAL_BUDGET_BYTES = 20L * 1024 * 1024
    }
}
