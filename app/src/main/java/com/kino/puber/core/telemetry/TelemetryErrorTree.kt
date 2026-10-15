package com.kino.puber.core.telemetry

import android.util.Log
import timber.log.Timber

/**
 * Records WARN-and-above log calls that carry a throwable. It overrides the throwable overloads
 * directly: `Tree.prepareLog` would format the stack trace on the caller's thread first.
 */
internal class TelemetryErrorTree : Timber.Tree() {
    override fun w(t: Throwable?, message: String?, vararg args: Any?) = capture(t)
    override fun w(t: Throwable?) = capture(t)
    override fun e(t: Throwable?, message: String?, vararg args: Any?) = capture(t)
    override fun e(t: Throwable?) = capture(t)
    override fun wtf(t: Throwable?, message: String?, vararg args: Any?) = capture(t)
    override fun wtf(t: Throwable?) = capture(t)

    override fun log(priority: Int, t: Throwable?, message: String?, vararg args: Any?) {
        if (priority >= Log.WARN) capture(t)
    }

    override fun log(priority: Int, t: Throwable?) {
        if (priority >= Log.WARN) capture(t)
    }

    // Message-only calls carry nothing the log wants.
    override fun w(message: String?, vararg args: Any?) = Unit
    override fun e(message: String?, vararg args: Any?) = Unit
    override fun wtf(message: String?, vararg args: Any?) = Unit
    override fun log(priority: Int, message: String?, vararg args: Any?) = Unit

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) = Unit

    private fun capture(t: Throwable?) {
        if (t != null && Telemetry.isEnabled) {
            Telemetry.record(TelemetryEvent.Err(Telemetry.nowMs(), ErrSource.LOG, t))
        }
    }
}
