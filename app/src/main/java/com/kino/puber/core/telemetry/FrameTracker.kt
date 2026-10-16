package com.kino.puber.core.telemetry

import android.app.Activity
import androidx.metrics.performance.JankStats

internal fun interface FrameListener {
    fun onFrame(startNs: Long, uiNs: Long, isJank: Boolean)
}

internal interface FrameTracker {
    var isTrackingEnabled: Boolean
}

internal fun interface FrameTrackerFactory {
    fun create(activity: Activity, listener: FrameListener): FrameTracker
}

/** Main thread only. Reads frameStartNanos, frameDurationUiNanos, isJank; never FrameData.states. */
internal object JankStatsTrackerFactory : FrameTrackerFactory {
    override fun create(activity: Activity, listener: FrameListener): FrameTracker {
        val stats = JankStats.createAndTrack(activity.window) { data ->
            listener.onFrame(data.frameStartNanos, data.frameDurationUiNanos, data.isJank)
        }
        return object : FrameTracker {
            override var isTrackingEnabled: Boolean
                get() = stats.isTrackingEnabled
                set(value) {
                    stats.isTrackingEnabled = value
                }
        }
    }
}
