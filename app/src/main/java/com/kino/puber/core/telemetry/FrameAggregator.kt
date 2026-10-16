package com.kino.puber.core.telemetry

private const val BUCKETS = 101
private const val OVERFLOW_BUCKET = BUCKETS - 1
private const val WINDOW_NS = 10_000_000_000L
private const val NS_PER_MS = 1_000_000L
private const val P50 = 50
private const val P90 = 90
private const val P99 = 99
private const val PERCENT = 100

/**
 * Folds frame durations into one window per screen: a 1 ms histogram, counters and the exact maximum.
 * [onFrame] only writes fields and array slots: no allocation, no string, no `Telemetry` call. A window
 * closes lazily, on the first frame after 10 s or on another screen, or on [close]; there is no timer.
 */
internal class FrameAggregator(
    private val emit: (TelemetryEvent.Frames) -> Unit = Telemetry::record,
    private val nowMs: () -> Long = Telemetry::nowMs,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val histogram = IntArray(BUCKETS)
    private var frames = 0
    private var janky = 0
    private var maxNs = 0L
    private var windowStartNs = 0L
    private var lastEndNs = 0L
    private var openedMs = 0L
    private var screen: Class<*>? = null

    @Synchronized
    fun onFrame(startNs: Long, uiNs: Long, isJank: Boolean, screen: Class<*>?) {
        if (frames > 0 && (screen !== this.screen || startNs - windowStartNs >= WINDOW_NS)) closeWindow()
        if (frames == 0) {
            windowStartNs = startNs
            openedMs = nowMs()
            this.screen = screen
        }
        histogram[(uiNs / NS_PER_MS).coerceIn(0, OVERFLOW_BUCKET.toLong()).toInt()]++
        frames++
        if (isJank) janky++
        if (uiNs > maxNs) maxNs = uiNs
        lastEndNs = startNs + uiNs
    }

    @Synchronized
    fun close() = closeWindow()

    private fun closeWindow() {
        if (frames == 0) return
        val maxMs = (maxNs / NS_PER_MS).toInt()
        val t = maxOf(nowMs() - (nanoTime() - lastEndNs) / NS_PER_MS, openedMs)
        val event = TelemetryEvent.Frames(
            t = t,
            screen = screen,
            frames = frames,
            janky = janky,
            p50 = percentile(P50, maxMs),
            p90 = percentile(P90, maxMs),
            p99 = percentile(P99, maxMs),
            maxMs = maxMs,
        )
        histogram.fill(0)
        frames = 0
        janky = 0
        maxNs = 0
        screen = null
        emit(event)
    }

    private fun percentile(p: Int, maxMs: Int): Int {
        val rank = maxOf(1, (frames * p + PERCENT - 1) / PERCENT)
        var sum = 0
        for (bucket in 0 until BUCKETS) {
            sum += histogram[bucket]
            if (sum >= rank) return if (bucket == OVERFLOW_BUCKET) maxMs else bucket
        }
        return maxMs
    }
}
