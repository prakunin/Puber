package com.kino.puber.core.telemetry

import com.kino.puber.core.startup.RevealCause
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** How long after the reveal the `startup` line is written without waiting for ready. */
internal const val LINE_WAIT_AFTER_REVEAL_MS = 5_000L

/**
 * The same bound when the line also waits for the profile status: ProfileInstaller reports it about
 * 5 s after the process starts, so the plain bound could write `prof` as absent on a fast start.
 */
internal const val LINE_WAIT_AFTER_REVEAL_WITH_PROFILE_MS = 7_000L

/**
 * Primitive marks of one cold start; emits exactly one `startup` line. First content and reveal must
 * be in, then one of: the reveal was forced; [LINE_WAIT_AFTER_REVEAL_MS] ([LINE_WAIT_AFTER_REVEAL_WITH_PROFILE_MS]
 * when [awaitProfileStatus]) passed since the reveal;
 * ready was marked and every image held at ready has ended. The fallback timer writes whatever was
 * reached. Gate marks arrive inside the gate lock, so they never emit directly: each hands the check
 * to [schedule], which production runs on the telemetry executor.
 */
internal class StartupMarks(
    private val procStartMs: Long,
    private val appCreateMs: Long,
    private val record: (TelemetryEvent.Startup) -> Unit,
    private val clock: TelemetryClock,
    private val schedule: (delayMs: Long, task: () -> Unit) -> Unit = { _, task -> task() },
    private val awaitProfileStatus: Boolean = false,
) {
    private val appComposedMs = AtomicLong(NOT_REACHED)
    private val firstContentMs = AtomicLong(NOT_REACHED)
    private val revealedMs = AtomicLong(NOT_REACHED)
    private val readyMs = AtomicLong(NOT_REACHED)
    private val readyReqMs = AtomicLong(NOT_REACHED)
    private val readyInFlight = AtomicInteger(NOT_REACHED.toInt())
    private val imagesIdleMs = AtomicLong(NOT_REACHED)
    private val readyDrainedMs = AtomicLong(NOT_REACHED)
    private val revealCause = AtomicReference<RevealCause?>(null)
    private val revealInFlight = AtomicInteger(NOT_REACHED.toInt())
    private val profileStatus = AtomicInteger(NOT_REACHED.toInt())
    private val droppedKeys = AtomicInteger(0)
    private val boundPassed = AtomicBoolean(false)
    private val emitted = AtomicBoolean(false)

    /** Keeps the first value only. */
    fun markAppComposed() {
        appComposedMs.compareAndSet(NOT_REACHED, clock.elapsedMs())
    }

    fun markFirstContent() {
        firstContentMs.compareAndSet(NOT_REACHED, clock.elapsedMs())
        scheduleCheck()
    }

    fun markRevealed() {
        if (!revealedMs.compareAndSet(NOT_REACHED, clock.elapsedMs())) return
        scheduleCheck()
        schedule(if (awaitProfileStatus) LINE_WAIT_AFTER_REVEAL_WITH_PROFILE_MS else LINE_WAIT_AFTER_REVEAL_MS) {
            boundPassed.set(true)
            emitWhenComplete()
        }
    }

    /** Keeps the first value only. */
    fun markReady(inFlight: Int) {
        if (!readyMs.compareAndSet(NOT_REACHED, clock.elapsedMs())) return
        readyInFlight.set(inFlight)
        scheduleCheck()
    }

    /** Keeps the last value: the edge that matters is the one nearest the reveal. */
    fun markImagesIdle() {
        imagesIdleMs.set(clock.elapsedMs())
    }

    fun markHeldDrained() {
        if (readyDrainedMs.compareAndSet(NOT_REACHED, clock.elapsedMs())) scheduleCheck()
    }

    fun markRevealCause(cause: RevealCause, inFlight: Int) {
        if (!revealCause.compareAndSet(null, cause)) return
        revealInFlight.set(inFlight)
        scheduleCheck()
    }

    /** Keeps the first value only; does not schedule a check. */
    fun markReadyRequested() {
        readyReqMs.compareAndSet(NOT_REACHED, clock.elapsedMs())
    }

    /** Keeps the first value only; a status after the line was written changes nothing. */
    fun markProfileStatus(code: Int) {
        if (!profileStatus.compareAndSet(NOT_REACHED.toInt(), code)) return
        scheduleCheck()
    }

    fun onKeyDropped() {
        droppedKeys.incrementAndGet()
    }

    fun onTimeout() = emit()

    private fun scheduleCheck() = schedule(0) { emitWhenComplete() }

    private fun emitWhenComplete() {
        if (firstContentMs.get() == NOT_REACHED || revealedMs.get() == NOT_REACHED) return
        val readyComplete = readyMs.get() != NOT_REACHED &&
            readyInFlight.get() != NOT_REACHED.toInt() &&
            (readyInFlight.get() == 0 || readyDrainedMs.get() != NOT_REACHED) &&
            (!awaitProfileStatus || profileStatus.get() != NOT_REACHED.toInt())
        if (revealCause.get() == RevealCause.FORCED || boundPassed.get() || readyComplete) emit()
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
                cause = revealCause.get(),
                readyMs = readyMs.reached(),
                imagesIdleMs = imagesIdleMs.reached(),
                inFlight = revealInFlight.reached(),
                readyInFlight = readyInFlight.reached(),
                readyDrainedMs = readyDrainedMs.reached(),
                droppedKeys = droppedKeys.get(),
                readyReqMs = readyReqMs.reached(),
                profileStatus = profileStatus.reached(),
            ),
        )
    }

    private fun AtomicLong.reached(): Long? = get().takeIf { it != NOT_REACHED }

    private fun AtomicInteger.reached(): Int? = get().takeIf { it.toLong() != NOT_REACHED }

    private companion object {
        const val NOT_REACHED = -1L
    }
}
