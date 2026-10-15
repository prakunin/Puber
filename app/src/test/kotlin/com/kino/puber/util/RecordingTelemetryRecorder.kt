package com.kino.puber.util

import com.kino.puber.core.telemetry.TelemetryEvent
import com.kino.puber.core.telemetry.TelemetryRecorder

internal class RecordingTelemetryRecorder(
    @Volatile override var isEnabled: Boolean = true,
) : TelemetryRecorder {
    val events: MutableList<TelemetryEvent> = java.util.Collections.synchronizedList(mutableListOf())
    var failWith: RuntimeException? = null

    override fun record(event: TelemetryEvent) {
        failWith?.let { throw it }
        events += event
    }
}
