package com.kino.puber.core.telemetry

import android.app.Activity

/**
 * Tracks the frames of the resumed window while the log is on and feeds them to the key matcher and the
 * frame aggregator. [start] and [stop] on the main thread only; the listener runs on the FrameMetrics
 * thread, allocates nothing and holds no Activity.
 */
internal class FrameProbe(
    private val trackers: FrameTrackerFactory = JankStatsTrackerFactory,
    private val aggregator: FrameAggregator = FrameAggregator(),
    private val matcher: KeyLatencyMatcher = KeyProbe.matcher,
) {
    @Volatile
    private var active = false

    private var tracker: FrameTracker? = null

    private val emitKeyLat: (TelemetryEvent.KeyLat) -> Unit = Telemetry::record

    private val listener = FrameListener(::onFrame)

    private fun onFrame(startNs: Long, uiNs: Long, isJank: Boolean) {
        if (!active || !Telemetry.isEnabled) return
        matcher.onFrame(startNs, uiNs, emitKeyLat)
        aggregator.onFrame(startNs, uiNs, isJank, Telemetry.screen)
    }

    fun start(activity: Activity) {
        if (tracker != null) return
        active = true
        tracker = trackers.create(activity, listener)
    }

    fun stop() {
        active = false
        tracker?.isTrackingEnabled = false
        tracker = null
        aggregator.close()
    }
}
