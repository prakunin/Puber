package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal class KeyLatencyMatcherConcurrencyTest {
    @Test
    fun armOnOneThreadAnswerOnAnotherNeverDoubleEmits() {
        val matcher = KeyLatencyMatcher { 0 }
        val rounds = 10_000
        val latestDispatchNs = AtomicLong(0)
        val armed = AtomicInteger()
        val emitted = AtomicInteger()
        val failures = AtomicInteger()
        val armerDone = AtomicBoolean(false)
        val answerer = Thread {
            runCatching {
                while (!armerDone.get()) {
                    val start = latestDispatchNs.get() + 1_000_000
                    matcher.onFrame(start, 100_000_000) { emitted.incrementAndGet() }
                }
                matcher.onFrame(latestDispatchNs.get() + 1_000_000, 100_000_000) { emitted.incrementAndGet() }
            }.onFailure { failures.incrementAndGet() }
        }
        answerer.start()
        runCatching {
            for (i in 1..rounds) {
                val dispatchNs = i * 2_000_000_000L
                latestDispatchNs.set(dispatchNs)
                matcher.arm(0, dispatchNs, KeyName.DPAD_UP, null, true)
                armed.incrementAndGet()
            }
        }.onFailure { failures.incrementAndGet() }
        armerDone.set(true)
        answerer.join(5_000)
        assertEquals(0, failures.get())
        assertTrue(emitted.get() <= armed.get(), "emitted ${emitted.get()} armed ${armed.get()}")
        assertTrue(emitted.get() > 0)
    }
}
