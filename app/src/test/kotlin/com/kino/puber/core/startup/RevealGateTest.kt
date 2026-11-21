package com.kino.puber.core.startup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RevealGateTest {
    private var now = 10_000L
    private val gate = RevealGate(clock = { now })

    @Test
    fun remainingCapMs_countsFromBegin() {
        assertEquals(0L, gate.remainingCapMs())
        gate.begin()
        assertEquals(2_500L, gate.remainingCapMs())
        now += 1_000
        assertEquals(1_500L, gate.remainingCapMs())
        now += 5_000
        assertEquals(0L, gate.remainingCapMs())
    }

    @Test
    fun cap_uncoversWithoutReady() {
        gate.begin()
        assertTrue(gate.covered.value)
        gate.onCap()
        assertFalse(gate.covered.value)
    }

    @Test
    fun markReady_uncoversImmediately() {
        gate.begin()
        assertTrue(gate.covered.value)
        gate.markReady()
        assertFalse(gate.covered.value)
    }

    @Test
    fun markReady_doesNothingBeforeBegin() {
        gate.markReady()
        assertFalse(gate.covered.value)
    }

    @Test
    fun begin_coversAgainAfterAPreviousReveal() {
        gate.begin()
        gate.markReady()
        assertFalse(gate.covered.value)
        now += 4_000
        gate.begin()
        assertTrue(gate.covered.value)
        assertEquals(2_500L, gate.remainingCapMs())
    }

    @Test
    fun markReady_takesEffectOnlyAfterTheNextFrame() {
        val frames = mutableListOf<() -> Unit>()
        val framed = RevealGate(clock = { now }, afterFrame = { frames += it })
        framed.begin()

        framed.markReady()
        assertTrue(framed.covered.value)

        frames.forEach { it() }
        assertFalse(framed.covered.value)
    }

    @Test
    fun markReady_madeBeforeAnotherBeginIsDropped() {
        val frames = mutableListOf<() -> Unit>()
        val framed = RevealGate(clock = { now }, afterFrame = { frames += it })
        framed.begin()
        framed.markReady()
        framed.begin()

        frames.forEach { it() }

        assertTrue(framed.covered.value)
    }

    @Test
    fun readyWaitsForImagesInFlight() {
        gate.begin()
        gate.onImagesBusy(true)
        gate.markReady()
        assertTrue(gate.covered.value)
    }

    @Test
    fun imagesSettlingAfterReadyUncovers() {
        gate.begin()
        gate.onImagesBusy(true)
        gate.markReady()
        assertTrue(gate.covered.value)
        gate.onImagesBusy(false)
        assertFalse(gate.covered.value)
    }

    @Test
    fun capUncoversWhileImagesInFlight() {
        gate.begin()
        gate.onImagesBusy(true)
        gate.onCap()
        assertFalse(gate.covered.value)
    }

    @Test
    fun revealNowIgnoresContentAndImages() {
        gate.begin()
        gate.onImagesBusy(true)
        gate.revealNow()
        assertFalse(gate.covered.value)
    }

    @Test
    fun imagesBusyAfterRevealDoNotCoverAgain() {
        gate.begin()
        gate.markReady()
        gate.onImagesBusy(true)
        assertFalse(gate.covered.value)
        gate.onImagesBusy(false)
        assertFalse(gate.covered.value)
        gate.onImagesBusy(true)
        assertFalse(gate.covered.value)
    }

    @Test
    fun beginKeepsImagesBusyFromBeforeIt() {
        gate.onImagesBusy(true)
        gate.begin()
        gate.markReady()
        assertTrue(gate.covered.value)
        gate.onImagesBusy(false)
        assertFalse(gate.covered.value)
    }

    @Test
    fun reset_uncoversAndForgetsTheStart() {
        gate.begin()
        assertTrue(gate.covered.value)
        gate.reset()
        assertFalse(gate.covered.value)
        assertEquals(0L, gate.remainingCapMs())
    }
}
