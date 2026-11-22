package com.kino.puber.core.telemetry

import android.view.KeyEvent
import androidx.annotation.VisibleForTesting
import java.util.concurrent.atomic.AtomicReference

private const val IDLE_GAP_MS = 2_000L
private const val SLOW_MS = 50L
private const val EXPIRY_NS = 5_000_000_000L
private const val MAX_PENDING = 16
private const val NS_PER_MS = 1_000_000L

/**
 * Folds the key events of the main thread into one [TelemetryEvent.Key] per press or held burst.
 * Main thread only. A repeat allocates nothing; the one event per burst is the only allocation.
 */
internal class KeyBurstMachine(
    private val emit: (TelemetryEvent.Key) -> Unit = Telemetry::record,
    private val nowMs: () -> Long = Telemetry::nowMs,
) {
    private var open = false
    private var code = 0
    private var name = KeyName.OTHER
    private var screen: Class<*>? = null
    private var startMs = 0L
    private var lastMs = 0L
    private var n = 0

    fun onKey(action: Int, keyCode: Int, repeat: Int, eventMs: Long, screen: Class<*>?) {
        if (open && eventMs - lastMs > IDLE_GAP_MS) closeAt(lastMs)
        when (action) {
            KeyEvent.ACTION_DOWN -> onDown(keyCode, repeat, eventMs, screen)
            KeyEvent.ACTION_UP -> if (open && keyCode == code) closeAt(eventMs)
        }
    }

    fun flush() {
        if (open) closeAt(lastMs)
    }

    fun reset() {
        open = false
    }

    private fun onDown(keyCode: Int, repeat: Int, eventMs: Long, screen: Class<*>?) {
        if (repeat > 0 && open && keyCode == code) {
            n++
            lastMs = eventMs
            return
        }
        if (open) closeAt(lastMs)
        open = true
        code = keyCode
        name = keyNameOf(keyCode)
        this.screen = screen
        startMs = eventMs
        lastMs = eventMs
        n = 1
    }

    private fun closeAt(endMs: Long) {
        open = false
        emit(TelemetryEvent.Key(nowMs(), screen, name, n, endMs - startMs))
    }
}

/**
 * Matches every unanswered handled key to the first frame that started after its dispatch.
 * [arm] runs on the main thread, [onFrame] on the FrameMetrics thread; the pending keys are one
 * immutable list swapped atomically, so the two never mix the fields of two presses and a key is
 * emitted by exactly one frame. At most [MAX_PENDING] keys wait; a key no frame answered within
 * [EXPIRY_NS] is emitted as expired, without a latency.
 */
internal class KeyLatencyMatcher(private val nowMs: () -> Long = Telemetry::nowMs) {
    private class Pending(
        val eventMs: Long,
        val dispatchNs: Long,
        val name: KeyName,
        val screen: Class<*>?,
    )

    private val pending = AtomicReference<List<Pending>>(emptyList())

    fun arm(eventMs: Long, dispatchNs: Long, name: KeyName, screen: Class<*>?, handled: Boolean) {
        if (!handled) return
        val added = Pending(eventMs, dispatchNs, name, screen)
        while (true) {
            val current = pending.get()
            if (current.size >= MAX_PENDING) return
            if (pending.compareAndSet(current, current + added)) return
        }
    }

    fun onFrame(startNs: Long, uiNs: Long, emit: (TelemetryEvent.KeyLat) -> Unit) {
        while (true) {
            val current = pending.get()
            if (current.isEmpty()) return
            var answered = 0
            for (i in current.indices) if (current[i].dispatchNs <= startNs) answered++
            if (answered == 0) return
            val remaining = if (answered == current.size) emptyList() else remainingAfter(current, startNs)
            if (!pending.compareAndSet(current, remaining)) continue
            for (i in current.indices) if (current[i].dispatchNs <= startNs) answer(current[i], startNs, uiNs, emit)
            return
        }
    }

    private fun remainingAfter(current: List<Pending>, startNs: Long): List<Pending> {
        val kept = ArrayList<Pending>(current.size)
        for (i in current.indices) if (current[i].dispatchNs > startNs) kept.add(current[i])
        return kept
    }

    private fun answer(p: Pending, startNs: Long, uiNs: Long, emit: (TelemetryEvent.KeyLat) -> Unit) {
        val frameMs = uiNs / NS_PER_MS
        val waitMs = p.dispatchNs / NS_PER_MS - p.eventMs
        if (startNs - p.dispatchNs > EXPIRY_NS) {
            emit(
                TelemetryEvent.KeyLat(
                    nowMs(), p.screen, p.name, latMs = null, frameMs = frameMs, expired = true, waitMs = waitMs,
                ),
            )
            return
        }
        val latMs = (startNs + uiNs) / NS_PER_MS - p.eventMs
        if (latMs > SLOW_MS) emit(TelemetryEvent.KeyLat(nowMs(), p.screen, p.name, latMs, frameMs, waitMs = waitMs))
    }

    fun clear() {
        pending.set(emptyList())
    }
}

/**
 * All logic of `MainActivity.dispatchKeyEvent`. Returns exactly what [superDispatch] returns, except
 * for a [dropped] key (swallowed under the startup splash), which is handled without reaching super.
 */
internal inline fun dispatchWithProbe(
    enabled: Boolean,
    dropped: Boolean,
    superDispatch: () -> Boolean,
    probe: (handled: Boolean, dropped: Boolean, dispatchNs: Long) -> Unit,
): Boolean {
    if (dropped) {
        if (enabled) runCatching { probe(true, true, System.nanoTime()) }
        return true
    }
    if (!enabled) return superDispatch()
    val dispatchNs = System.nanoTime()
    val handled = superDispatch()
    runCatching { probe(handled, false, dispatchNs) }
    return handled
}

/** The key probes of the process; fed from the main thread, and only while the log is on. */
internal object KeyProbe {
    val matcher = KeyLatencyMatcher()
    private val bursts = KeyBurstMachine()

    fun onKey(
        action: Int,
        keyCode: Int,
        repeat: Int,
        eventMs: Long,
        dispatchNs: Long,
        handled: Boolean,
        dropped: Boolean = false,
    ) {
        val screen = Telemetry.screen
        bursts.onKey(action, keyCode, repeat, eventMs, screen)
        if (action == KeyEvent.ACTION_DOWN && repeat == 0) {
            if (dropped) {
                Telemetry.startupMarks?.onKeyDropped()
            } else {
                matcher.arm(eventMs, dispatchNs, keyNameOf(keyCode), screen, handled)
            }
        }
    }

    fun flush() = bursts.flush()

    fun detach() {
        bursts.flush()
        matcher.clear()
    }

    @VisibleForTesting
    fun resetForTests() {
        bursts.reset()
        matcher.clear()
    }
}
