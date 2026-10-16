package com.kino.puber.core.telemetry

import android.view.KeyEvent
import androidx.annotation.VisibleForTesting
import java.util.concurrent.atomic.AtomicReference

private const val IDLE_GAP_MS = 2_000L
private const val SLOW_MS = 50L
private const val EXPIRY_NS = 1_000_000_000L
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
 * Matches the earliest unanswered handled key to the first frame that started after its dispatch.
 * [arm] runs on the main thread, [onFrame] on the FrameMetrics thread; one immutable [Pending] is
 * swapped atomically, so the two never mix the fields of two presses.
 */
internal class KeyLatencyMatcher(private val nowMs: () -> Long = Telemetry::nowMs) {
    private class Pending(
        val eventMs: Long,
        val dispatchNs: Long,
        val name: KeyName,
        val screen: Class<*>?,
    )

    private val pending = AtomicReference<Pending?>(null)

    fun arm(eventMs: Long, dispatchNs: Long, name: KeyName, screen: Class<*>?, handled: Boolean) {
        if (!handled) return
        val current = pending.get()
        if (current != null && dispatchNs - current.dispatchNs <= EXPIRY_NS) return
        pending.compareAndSet(current, Pending(eventMs, dispatchNs, name, screen))
    }

    fun onFrame(startNs: Long, uiNs: Long, emit: (TelemetryEvent.KeyLat) -> Unit) {
        val p = pending.get() ?: return
        if (startNs >= p.dispatchNs) answer(p, startNs, uiNs, emit)
    }

    private fun answer(p: Pending, startNs: Long, uiNs: Long, emit: (TelemetryEvent.KeyLat) -> Unit) {
        if (startNs - p.dispatchNs > EXPIRY_NS) {
            pending.compareAndSet(p, null)
            return
        }
        if (!pending.compareAndSet(p, null)) return
        val latMs = (startNs + uiNs) / NS_PER_MS - p.eventMs
        if (latMs > SLOW_MS) emit(TelemetryEvent.KeyLat(nowMs(), p.screen, p.name, latMs, uiNs / NS_PER_MS))
    }

    fun clear() {
        pending.set(null)
    }
}

/** All logic of `MainActivity.dispatchKeyEvent`. Returns exactly what [superDispatch] returns. */
internal inline fun dispatchWithProbe(
    enabled: Boolean,
    superDispatch: () -> Boolean,
    probe: (handled: Boolean, dispatchNs: Long) -> Unit,
): Boolean {
    if (!enabled) return superDispatch()
    val dispatchNs = System.nanoTime()
    val handled = superDispatch()
    runCatching { probe(handled, dispatchNs) }
    return handled
}

/** The key probes of the process; fed from the main thread, and only while the log is on. */
internal object KeyProbe {
    val matcher = KeyLatencyMatcher()
    private val bursts = KeyBurstMachine()

    fun onKey(action: Int, keyCode: Int, repeat: Int, eventMs: Long, dispatchNs: Long, handled: Boolean) {
        val screen = Telemetry.screen
        bursts.onKey(action, keyCode, repeat, eventMs, screen)
        if (action == KeyEvent.ACTION_DOWN && repeat == 0) {
            matcher.arm(eventMs, dispatchNs, keyNameOf(keyCode), screen, handled)
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
