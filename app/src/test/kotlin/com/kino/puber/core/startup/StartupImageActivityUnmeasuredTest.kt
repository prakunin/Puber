package com.kino.puber.core.startup

import coil3.request.ImageRequest
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

internal class StartupImageActivityUnmeasuredTest {
    private val gate = RevealGate(clock = { 0 })
    private val activity = StartupImageActivity(gate)

    @Test
    fun requestThatStartedButWasNeverMeasuredDoesNotHoldTheSplash() {
        val a = mockk<ImageRequest>()
        gate.begin()
        activity.onStart(a)
        gate.markReady()
        assertFalse(gate.covered.value)
    }
}
