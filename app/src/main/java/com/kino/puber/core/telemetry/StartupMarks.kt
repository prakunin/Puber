package com.kino.puber.core.telemetry

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Primitive marks of one cold start; emits exactly one `startup` line, once first content and reveal
 * are both in, or when the fallback timer fires with whatever was reached.
 */
internal class StartupMarks(
    private val procStartMs: Long,
    private val appCreateMs: Long,
    private val record: (TelemetryEvent.Startup) -> Unit,
    private val clock: TelemetryClock,
) {
    private val appComposedMs = AtomicLong(NOT_REACHED)
    private val firstContentMs = AtomicLong(NOT_REACHED)
    private val revealedMs = AtomicLong(NOT_REACHED)
    private val emitted = AtomicBoolean(false)

    /** Keeps the first value only. */
    fun markAppComposed() {
        appComposedMs.compareAndSet(NOT_REACHED, clock.elapsedMs())
    }

    fun markFirstContent() {
        firstContentMs.compareAndSet(NOT_REACHED, clock.elapsedMs())
        emitWhenComplete()
    }

    fun markRevealed() {
        revealedMs.compareAndSet(NOT_REACHED, clock.elapsedMs())
        emitWhenComplete()
    }

    fun onTimeout() = emit()

    private fun emitWhenComplete() {
        if (firstContentMs.get() != NOT_REACHED && revealedMs.get() != NOT_REACHED) emit()
    }

    private fun emit() {
        if (!emitted.compareAndSet(false, true)) return
        record(
            TelemetryEvent.Startup(
                clock.elapsedMs(),
                procStartMs,
                appCreateMs,
                appComposedMs.reached(),
                firstContentMs.reached(),
                revealedMs.reached(),
            ),
        )
    }

    private fun AtomicLong.reached(): Long? = get().takeIf { it != NOT_REACHED }

    private companion object {
        const val NOT_REACHED = -1L
    }
}
