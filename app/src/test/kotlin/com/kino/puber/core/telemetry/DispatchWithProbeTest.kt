package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DispatchWithProbeTest {
    private val log = mutableListOf<String>()

    @Test
    fun offCallsSuperOnceAndNeverTheProbe() {
        for (value in listOf(true, false)) {
            log.clear()
            val result = dispatchWithProbe(false, { log += "super"; value }) { _, _ -> log += "probe" }
            assertEquals(value, result)
            assertEquals(listOf("super"), log)
        }
    }

    @Test
    fun onReturnsSupersValueAndProbesAfterSuper() {
        for (value in listOf(true, false)) {
            log.clear()
            var seen: Boolean? = null
            val result = dispatchWithProbe(true, { log += "super"; value }) { handled, _ ->
                log += "probe"
                seen = handled
            }
            assertEquals(value, result)
            assertEquals(listOf("super", "probe"), log)
            assertEquals(value, seen)
        }
    }

    @Test
    fun probeThatThrowsCannotChangeTheReturnValueOrBreakDispatch() {
        for (value in listOf(true, false)) {
            val result = dispatchWithProbe(true, { value }) { _, _ -> error("telemetry bug") }
            assertEquals(value, result)
        }
    }

    @Test
    fun superThatThrowsPropagatesAndProbeIsNotCalled() {
        var probed = false
        assertThrows(IllegalStateException::class.java) {
            dispatchWithProbe(true, { error("dispatch failed") }) { _, _ -> probed = true }
        }
        assertFalse(probed)
        assertTrue(dispatchWithProbe(true, { true }) { _, _ -> probed = true })
        assertTrue(probed)
    }
}
