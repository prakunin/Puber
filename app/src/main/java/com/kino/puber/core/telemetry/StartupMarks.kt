package com.kino.puber.core.telemetry

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Primitive marks of one cold start; emits exactly one `startup` line, on first content or when the
 * fallback timer fires with whatever was reached.
 */
internal class StartupMarks(
    private val procStartMs: Long,
    private val appCreateMs: Long,
    private val record: (TelemetryEvent.Startup) -> Unit,
    private val clock: TelemetryClock,
) {
    private val appComposedMs = AtomicLong(NOT_REACHED)
    private val emitted = AtomicBoolean(false)

    /** Keeps the first value only. */
    fun markAppComposed() {
        appComposedMs.compareAndSet(NOT_REACHED, clock.elapsedMs())
    }

    fun markFirstContent() = emit(clock.elapsedMs())

    fun onTimeout() = emit(null)

    private fun emit(firstContentMs: Long?) {
        if (!emitted.compareAndSet(false, true)) return
        val composed = appComposedMs.get().takeIf { it != NOT_REACHED }
        record(TelemetryEvent.Startup(clock.elapsedMs(), procStartMs, appCreateMs, composed, firstContentMs))
    }

    private companion object {
        const val NOT_REACHED = -1L
    }
}
