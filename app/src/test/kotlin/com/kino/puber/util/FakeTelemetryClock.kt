package com.kino.puber.util

import com.kino.puber.core.telemetry.TelemetryClock

internal class FakeTelemetryClock(
    @Volatile var elapsed: Long = 1_000L,
    @Volatile var wall: Long = 1_700_000_000_000L,
) : TelemetryClock {
    override fun elapsedMs(): Long = elapsed
    override fun wallMs(): Long = wall

    fun advance(ms: Long) {
        elapsed += ms
        wall += ms
    }
}
