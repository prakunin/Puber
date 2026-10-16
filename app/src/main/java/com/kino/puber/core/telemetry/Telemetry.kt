package com.kino.puber.core.telemetry

import androidx.annotation.VisibleForTesting

internal interface TelemetryRecorder {
    val isEnabled: Boolean
    fun record(event: TelemetryEvent)
}

private object DisabledRecorder : TelemetryRecorder {
    override val isEnabled: Boolean = false
    override fun record(event: TelemetryEvent) = Unit
}

/**
 * The single entry point of the session log. Every probe is
 * `if (Telemetry.isEnabled) Telemetry.record(...)`, so nothing is allocated while the log is off.
 */
internal object Telemetry {
    @Volatile
    private var recorder: TelemetryRecorder = DisabledRecorder

    @Volatile
    internal var clock: TelemetryClock = JvmTelemetryClock

    @Volatile
    var controller: SessionLogController = DisabledSessionLogController
        private set

    @Volatile
    internal var startupMarks: StartupMarks? = null

    /** The screen on top while recording; written only by `recordNav`, reset on probe detach. */
    @Volatile
    internal var screen: Class<*>? = null

    val isEnabled: Boolean get() = recorder.isEnabled

    fun record(event: TelemetryEvent) {
        val current = recorder
        if (current.isEnabled) current.record(event)
    }

    fun nowMs(): Long = clock.elapsedMs()

    internal fun install(recorder: TelemetryRecorder, clock: TelemetryClock) {
        this.recorder = recorder
        this.clock = clock
    }

    internal fun installController(controller: SessionLogController) {
        this.controller = controller
    }

    @VisibleForTesting
    internal fun resetForTests() {
        recorder = DisabledRecorder
        clock = JvmTelemetryClock
        controller = DisabledSessionLogController
        startupMarks = null
        screen = null
    }
}
