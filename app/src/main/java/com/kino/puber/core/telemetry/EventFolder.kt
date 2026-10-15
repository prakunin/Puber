package com.kino.puber.core.telemetry

/** Writer-thread stage that folds bursts of identical events into one line with a count. */
internal interface EventFolder {
    fun accepts(event: TelemetryEvent): Boolean
    fun offer(event: TelemetryEvent, emit: (TelemetryEvent) -> Unit)
    fun flush(nowElapsedMs: Long, force: Boolean, emit: (TelemetryEvent) -> Unit)
}

/** Identical consecutive actions of one view model within one second become one line with `n`. */
internal class ActFolder : EventFolder {
    private var pending: TelemetryEvent.Act? = null

    override fun accepts(event: TelemetryEvent): Boolean = event is TelemetryEvent.Act

    override fun offer(event: TelemetryEvent, emit: (TelemetryEvent) -> Unit) {
        val act = event as TelemetryEvent.Act
        val held = pending
        if (held != null && continuesBurst(held, act)) {
            pending = held.copy(n = held.n + act.n)
        } else {
            held?.let(emit)
            pending = act
        }
    }

    private fun continuesBurst(held: TelemetryEvent.Act, act: TelemetryEvent.Act): Boolean =
        act.t - held.t < WINDOW_MS && held.vm === act.vm && held.action === act.action

    override fun flush(nowElapsedMs: Long, force: Boolean, emit: (TelemetryEvent) -> Unit) {
        val held = pending ?: return
        if (force || nowElapsedMs - held.t >= WINDOW_MS) {
            pending = null
            emit(held)
        }
    }

    private companion object {
        const val WINDOW_MS = 1_000L
    }
}

/**
 * Identical errors (type and first app frame) inside 60 s emit once; the repeats come out as one
 * follow-up line with `n` when the window closes. At most 32 keys are kept.
 */
internal class ErrFolder : EventFolder {
    private class Entry(
        val windowStart: Long,
        val src: ErrSource,
        val error: Throwable,
        var lastT: Long,
        var repeats: Int,
    )

    private val entries = LinkedHashMap<Pair<String, String?>, Entry>()

    // A crash is never folded: it is written at once as `src=uncaught`.
    override fun accepts(event: TelemetryEvent): Boolean =
        event is TelemetryEvent.Err && event.src != ErrSource.UNCAUGHT

    override fun offer(event: TelemetryEvent, emit: (TelemetryEvent) -> Unit) {
        val err = event as TelemetryEvent.Err
        val key = ErrorScrubber.type(err.error) to ErrorScrubber.frames(err.error).firstOrNull()
        val entry = entries[key]
        if (entry != null && err.t - entry.windowStart < WINDOW_MS) {
            entry.repeats += err.n
            entry.lastT = err.t
            return
        }
        if (entry != null) {
            entries.remove(key)
            followUp(entry, emit)
        }
        if (entries.size >= MAX_KEYS) {
            val oldest = entries.keys.first()
            entries.remove(oldest)?.let { followUp(it, emit) }
        }
        entries[key] = Entry(err.t, err.src, err.error, err.t, 0)
        emit(err)
    }

    override fun flush(nowElapsedMs: Long, force: Boolean, emit: (TelemetryEvent) -> Unit) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (force || nowElapsedMs - entry.windowStart >= WINDOW_MS) {
                iterator.remove()
                followUp(entry, emit)
            }
        }
    }

    private fun followUp(entry: Entry, emit: (TelemetryEvent) -> Unit) {
        if (entry.repeats > 0) {
            emit(TelemetryEvent.Err(entry.lastT, entry.src, entry.error, entry.repeats))
        }
    }

    private companion object {
        const val WINDOW_MS = 60_000L
        const val MAX_KEYS = 32
    }
}
