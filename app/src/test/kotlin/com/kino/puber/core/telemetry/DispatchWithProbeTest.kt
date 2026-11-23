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
            val result = dispatchWithProbe(false, false, { log += "super"; value }) { _, _, _ -> log += "probe" }
            assertEquals(value, result)
            assertEquals(listOf("super"), log)
        }
    }

    @Test
    fun onReturnsSupersValueAndProbesAfterSuper() {
        for (value in listOf(true, false)) {
            log.clear()
            var seen: Boolean? = null
            val result = dispatchWithProbe(true, false, { log += "super"; value }) { handled, _, _ ->
                log += "probe"
                seen = handled
            }
            assertEquals(value, result)
            assertEquals(listOf("super", "probe"), log)
            assertEquals(value, seen)
        }
    }

    @Test
    fun droppedKeyReturnsTrueWithoutCallingSuper() {
        for (enabled in listOf(true, false)) {
            log.clear()
            val result = dispatchWithProbe(enabled, true, { log += "super"; false }) { _, _, _ -> }
            assertTrue(result, "enabled $enabled")
            assertEquals(emptyList<String>(), log, "enabled $enabled")
        }
    }

    @Test
    fun droppedKeyIsProbedAsDropped() {
        var seen: Pair<Boolean, Boolean>? = null
        dispatchWithProbe(true, true, { false }) { handled, dropped, _ -> seen = handled to dropped }
        assertEquals(true to true, seen)
        dispatchWithProbe(true, false, { true }) { handled, dropped, _ -> seen = handled to dropped }
        assertEquals(true to false, seen)
    }

    @Test
    fun probeThatThrowsCannotChangeTheReturnValueOrBreakDispatch() {
        for (value in listOf(true, false)) {
            val result = dispatchWithProbe(true, false, { value }) { _, _, _ -> error("telemetry bug") }
            assertEquals(value, result)
        }
    }

    @Test
    fun superThatThrowsPropagatesAndProbeIsNotCalled() {
        var probed = false
        assertThrows(IllegalStateException::class.java) {
            dispatchWithProbe(true, false, { error("dispatch failed") }) { _, _, _ -> probed = true }
        }
        assertFalse(probed)
        assertTrue(dispatchWithProbe(true, false, { true }) { _, _, _ -> probed = true })
        assertTrue(probed)
    }
}
