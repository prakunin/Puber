package com.kino.puber.core.telemetry

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Both idempotent; always called on the main thread (`TelemetryRuntime` posts them). */
internal interface TelemetryProbe {
    fun attach()
    fun detach()
}

/**
 * Follows the activities. It always tracks the resumed activity and the started count, two field
 * writes, so a probe planted at toggle time can find the window that is already on screen. Everything
 * else happens only while the log is on.
 */
internal class TelemetryActivityCallbacks(
    private val isEnabled: () -> Boolean,
    private val frameProbe: FrameProbe,
    private val keyFlush: () -> Unit,
    private val requestDrain: () -> Unit,
    private val memoryProbe: MemoryProbe,
) : Application.ActivityLifecycleCallbacks {
    private var startedCount = 0

    var resumedActivity: Activity? = null
        private set

    val isForeground: Boolean get() = startedCount > 0

    override fun onActivityStarted(activity: Activity) {
        startedCount++
        if (isEnabled()) memoryProbe.onForeground(isForeground)
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount = (startedCount - 1).coerceAtLeast(0)
        if (isEnabled()) memoryProbe.onForeground(isForeground)
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivity = activity
        if (isEnabled()) frameProbe.start(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivity === activity) resumedActivity = null
        if (isEnabled()) {
            // Close the open windows first, so the drain writes them.
            frameProbe.stop()
            keyFlush()
            requestDrain()
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (resumedActivity === activity) resumedActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

/** The one [TelemetryProbe] for the frame, key and memory probes. */
internal class ActivityProbes(
    private val callbacks: TelemetryActivityCallbacks,
    private val frameProbe: FrameProbe,
    private val memoryProbe: MemoryProbe,
    private val keyDetach: () -> Unit,
) : TelemetryProbe {
    override fun attach() {
        callbacks.resumedActivity?.let(frameProbe::start)
        memoryProbe.attach()
        memoryProbe.onForeground(callbacks.isForeground)
    }

    override fun detach() {
        frameProbe.stop()
        keyDetach()
        memoryProbe.detach()
    }
}
