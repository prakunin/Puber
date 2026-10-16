package com.kino.puber.core.telemetry

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Debug

private const val SAMPLE_PERIOD_MS = 60_000L
private const val BYTES_PER_KB = 1024L

internal class MemReading(val javaKb: Long, val javaMaxKb: Long, val nativeKb: Long, val pssKb: Long?)

internal fun interface MemorySampler {
    fun sample(withPss: Boolean): MemReading
}

/** Writer thread only. One reused [Debug.MemoryInfo], created on the first PSS sample. */
internal class DebugMemorySampler : MemorySampler {
    private var info: Debug.MemoryInfo? = null

    override fun sample(withPss: Boolean): MemReading {
        val runtime = Runtime.getRuntime()
        return MemReading(
            javaKb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_KB,
            javaMaxKb = runtime.maxMemory() / BYTES_PER_KB,
            nativeKb = Debug.getNativeHeapAllocatedSize() / BYTES_PER_KB,
            pssKb = if (withPss) pss() else null,
        )
    }

    private fun pss(): Long {
        val reused = info ?: Debug.MemoryInfo().also { info = it }
        Debug.getMemoryInfo(reused)
        return reused.totalPss.toLong()
    }
}

/**
 * One `mem` line per foreground minute and one per trim. [attach], [detach], [onForeground] and
 * [onTrimMemory] run on the main thread; sampling runs on the telemetry executor.
 */
internal class MemoryProbe(
    private val executor: TelemetryExecutor,
    private val sampler: MemorySampler,
    private val registerTrim: (ComponentCallbacks2) -> Unit,
    private val unregisterTrim: (ComponentCallbacks2) -> Unit,
    private val emit: (TelemetryEvent) -> Unit = Telemetry::record,
    private val nowMs: () -> Long = Telemetry::nowMs,
) : ComponentCallbacks2 {
    private var attached = false
    private var timer: AutoCloseable? = null

    fun attach() {
        if (attached) return
        attached = true
        registerTrim(this)
    }

    fun onForeground(foreground: Boolean) {
        if (!attached) return
        if (foreground) {
            if (timer == null) {
                timer = executor.scheduleRepeating(SAMPLE_PERIOD_MS) { sample(withPss = true, trim = null) }
            }
        } else {
            stopTimer()
        }
    }

    fun detach() {
        if (!attached) return
        attached = false
        stopTimer()
        unregisterTrim(this)
    }

    override fun onTrimMemory(level: Int) {
        // UI_HIDDEN fires on every backgrounding: a visibility signal, not memory pressure.
        if (!attached || level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return
        executor.execute { sample(withPss = false, trim = level) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Replaced by onTrimMemory", ReplaceWith("onTrimMemory"))
    override fun onLowMemory() = Unit

    private fun stopTimer() {
        timer?.close()
        timer = null
    }

    private fun sample(withPss: Boolean, trim: Int?) {
        runCatching {
            val reading = sampler.sample(withPss)
            val pss = if (withPss) reading.pssKb else null
            emit(
                TelemetryEvent.Mem(
                    nowMs(),
                    Telemetry.screen,
                    reading.javaKb,
                    reading.javaMaxKb,
                    reading.nativeKb,
                    pss,
                    trim,
                ),
            )
        }
    }
}
