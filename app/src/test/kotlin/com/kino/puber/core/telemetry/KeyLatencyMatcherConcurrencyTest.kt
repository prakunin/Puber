package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal class KeyLatencyMatcherConcurrencyTest {
    @Test
    fun armOnOneThreadAnswerOnAnotherNeverDoubleEmits() {
        val matcher = KeyLatencyMatcher { 0 }
        val rounds = 10_000
        val latestDispatchNs = AtomicLong(0)
        val seen: MutableSet<Long> = ConcurrentHashMap.newKeySet()
        val duplicates = AtomicInteger()
        val failures = AtomicInteger()
        val armerDone = AtomicBoolean(false)
        val answerer = Thread {
            runCatching {
                while (!armerDone.get()) {
                    val start = latestDispatchNs.get() + 1_000_000
                    matcher.onFrame(start, 100_000_000) { record(it, seen, duplicates) }
                }
                matcher.onFrame(latestDispatchNs.get() + 1_000_000, 100_000_000) { record(it, seen, duplicates) }
            }.onFailure { failures.incrementAndGet() }
        }
        answerer.start()
        runCatching {
            for (i in 1..rounds) {
                val dispatchNs = i * 2_000_000_000L
                latestDispatchNs.set(dispatchNs)
                // waitMs == i identifies the key on its line.
                matcher.arm(dispatchNs / NS_PER_MS - i, dispatchNs, KeyName.DPAD_UP, null, true)
            }
        }.onFailure { failures.incrementAndGet() }
        armerDone.set(true)
        answerer.join(5_000)
        assertEquals(0, failures.get())
        assertEquals(0, duplicates.get(), "keys emitted twice")
        assertTrue(seen.isNotEmpty())
    }

    private fun record(line: TelemetryEvent.KeyLat, seen: MutableSet<Long>, duplicates: AtomicInteger) {
        if (!seen.add(line.waitMs!!)) duplicates.incrementAndGet()
    }

    private companion object {
        const val NS_PER_MS = 1_000_000L
    }
}
