package com.kino.puber.core.telemetry

import android.os.SystemClock

internal interface TelemetryClock {
    fun elapsedMs(): Long
    fun wallMs(): Long
}

/** Default clock: touches no `android.*` class, so it is safe in JVM unit tests. */
internal object JvmTelemetryClock : TelemetryClock {
    override fun elapsedMs(): Long = System.nanoTime() / NANOS_PER_MILLI
    override fun wallMs(): Long = System.currentTimeMillis()

    private const val NANOS_PER_MILLI = 1_000_000L
}

/** Installed by `TelemetryRuntime` only; reads the device boot clock. */
internal object SystemTelemetryClock : TelemetryClock {
    override fun elapsedMs(): Long = SystemClock.elapsedRealtime()
    override fun wallMs(): Long = System.currentTimeMillis()
}
