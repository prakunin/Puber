package com.kino.puber.core.startup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RevealGateTest {
    private var now = 10_000L
    private val gate = RevealGate(clock = { now })
    private val frames = mutableListOf<() -> Unit>()
    private val graces = mutableListOf<Pair<Long, () -> Unit>>()
    private val graceObserved = RecordingRevealObserver()
    private val graceGate = RevealGate(
        clock = { now },
        afterFrame = { frames += it },
        observer = graceObserved,
        graceMs = 123,
        scheduleGrace = { delay, task -> graces += delay to task },
    )

    private fun runFrames() {
        val due = frames.toList()
        frames.clear()
        due.forEach { it() }
    }

    private fun revealedLog() = graceObserved.log.filter { it.startsWith("revealed") }

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
        gate.onImageStarted(Any())
        gate.markReady()
        assertTrue(gate.covered.value)
    }

    @Test
    fun imagesSettlingAfterReadyUncovers() {
        val a = Any()
        gate.begin()
        gate.onImageStarted(a)
        gate.markReady()
        assertTrue(gate.covered.value)
        gate.onImageFinished(a)
        assertFalse(gate.covered.value)
    }

    @Test
    fun capUncoversWhileImagesInFlight() {
        gate.begin()
        gate.onImageStarted(Any())
        gate.onCap()
        assertFalse(gate.covered.value)
    }

    @Test
    fun revealNowIgnoresContentAndImages() {
        gate.begin()
        gate.onImageStarted(Any())
        gate.revealNow()
        assertFalse(gate.covered.value)
    }

    @Test
    fun imagesBusyAfterRevealDoNotCoverAgain() {
        val a = Any()
        val b = Any()
        gate.begin()
        gate.markReady()
        gate.onImageStarted(a)
        assertFalse(gate.covered.value)
        gate.onImageFinished(a)
        assertFalse(gate.covered.value)
        gate.onImageStarted(b)
        assertFalse(gate.covered.value)
    }

    @Test
    fun beginKeepsImagesBusyFromBeforeIt() {
        val a = Any()
        gate.onImageStarted(a)
        gate.begin()
        gate.markReady()
        assertTrue(gate.covered.value)
        gate.onImageFinished(a)
        assertFalse(gate.covered.value)
    }

    @Test
    fun sameRequestFinishedTwice_doesNotUncoverOthers() {
        val a = Any()
        val b = Any()
        gate.begin()
        gate.onImageStarted(a)
        gate.onImageStarted(b)
        gate.markReady()
        gate.onImageFinished(a)
        gate.onImageFinished(a)
        assertTrue(gate.covered.value)
        gate.onImageFinished(b)
        assertFalse(gate.covered.value)
    }

    @Test
    fun revealByReady_reportsCauseReady() {
        val observed = RecordingRevealObserver()
        val observedGate = RevealGate(clock = { now }, observer = observed)
        observedGate.begin()
        observedGate.markReady()
        assertEquals(listOf("ready:0", "revealed:READY:0"), observed.log)
    }

    @Test
    fun revealByCap_reportsCauseCapWithInFlightCount() {
        val observed = RecordingRevealObserver()
        val observedGate = RevealGate(clock = { now }, observer = observed)
        observedGate.begin()
        observedGate.onImageStarted(Any())
        observedGate.onImageStarted(Any())
        observedGate.onCap()
        assertEquals(listOf("revealed:CAP:2"), observed.log)
    }

    @Test
    fun revealNow_reportsCauseForced() {
        val observed = RecordingRevealObserver()
        val observedGate = RevealGate(clock = { now }, observer = observed)
        observedGate.begin()
        observedGate.revealNow()
        observedGate.onCap()
        assertEquals(listOf("revealed:FORCED:0"), observed.log)
    }

    @Test
    fun revealNowBeforeBegin_reportsForcedOnceWithoutCovering() {
        val observed = RecordingRevealObserver()
        val observedGate = RevealGate(clock = { now }, observer = observed)
        observedGate.revealNow()
        observedGate.revealNow()
        observedGate.markReady()
        assertEquals(listOf("revealed:FORCED:0"), observed.log)
        assertFalse(observedGate.covered.value)
        assertEquals(0L, observedGate.remainingCapMs())
    }

    @Test
    fun markReady_reportsInFlightCountWhenReadyTakesEffect() {
        val observed = RecordingRevealObserver()
        val frames = mutableListOf<() -> Unit>()
        val framed = RevealGate(clock = { now }, afterFrame = { frames += it }, observer = observed)
        framed.begin()
        framed.onImageStarted(Any())
        framed.markReady()
        framed.onImageStarted(Any())
        assertEquals(emptyList<String>(), observed.log)

        frames.forEach { it() }

        assertEquals(listOf("ready:2"), observed.log)
    }

    @Test
    fun imagesIdleEdge_reportedOnlyBeforeTheReveal() {
        val observed = RecordingRevealObserver()
        val observedGate = RevealGate(clock = { now }, observer = observed)
        val a = Any()
        val b = Any()
        observedGate.begin()
        observedGate.onImageStarted(a)
        observedGate.onImageFinished(a)
        assertEquals(listOf("idle"), observed.log)
        observedGate.markReady()
        observedGate.onImageStarted(b)
        observedGate.onImageFinished(b)
        assertEquals(listOf("idle", "ready:0", "revealed:READY:0"), observed.log)
    }

    @Test
    fun heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd() {
        val observed = RecordingRevealObserver()
        val frames = mutableListOf<() -> Unit>()
        val framed = RevealGate(clock = { now }, afterFrame = { frames += it }, observer = observed)
        val a = Any()
        val b = Any()
        val c = Any()
        framed.begin()
        framed.onImageStarted(a)
        framed.onImageStarted(b)
        framed.markReady()
        frames.forEach { it() }
        framed.onImageStarted(c)
        framed.onImageFinished(a)
        assertEquals(listOf("ready:2"), observed.log)
        framed.onImageFinished(b)
        assertEquals(listOf("ready:2", "drained", "revealed:READY:1"), observed.log)
        assertFalse(framed.covered.value)
        framed.onImageFinished(c)
        assertEquals(listOf("ready:2", "drained", "revealed:READY:1"), observed.log)
    }

    @Test
    fun graceAfterHeldDrained_changesNothing() {
        val a = Any()
        val b = Any()
        graceGate.begin()
        graceGate.onImageStarted(a)
        graceGate.onImageStarted(b)
        graceGate.markReady()
        runFrames()
        graceGate.onImageFinished(a)
        graceGate.onImageFinished(b)
        val before = graceObserved.log.toList()
        assertEquals(listOf("ready:2", "idle", "drained", "revealed:READY:0"), before)

        graces.single().second()

        assertEquals(before, graceObserved.log)
        assertFalse(graceGate.covered.value)
    }

    @Test
    fun graceWithHeldInFlight_revealsWithCauseGrace() {
        val a = Any()
        val b = Any()
        graceGate.begin()
        graceGate.onImageStarted(a)
        graceGate.onImageStarted(b)
        graceGate.markReady()
        runFrames()
        graceGate.onImageFinished(a)
        assertTrue(graceGate.covered.value)

        graces.single().second()

        assertFalse(graceGate.covered.value)
        assertEquals("revealed:GRACE:1", graceObserved.log.last())
        graceGate.onImageFinished(b)
        assertEquals(listOf("revealed:GRACE:1"), revealedLog())
    }

    @Test
    fun markReady_withHeldRequests_armsOneGraceOfGraceMs() {
        graceGate.begin()
        graceGate.onImageStarted(Any())
        graceGate.markReady()
        runFrames()
        graceGate.markReady()
        runFrames()

        assertEquals(listOf(123L), graces.map { it.first })
    }

    @Test
    fun markReady_withNothingHeld_armsNoGrace() {
        graceGate.begin()
        graceGate.markReady()
        runFrames()

        assertTrue(graces.isEmpty())
        assertEquals(listOf("revealed:READY:0"), revealedLog())
    }

    @Test
    fun graceFromBeforeAnotherBegin_isDropped() {
        val a = Any()
        graceGate.begin()
        graceGate.onImageStarted(a)
        graceGate.markReady()
        runFrames()
        val stale = graces.single().second
        graceGate.begin()
        graceGate.markReady()
        runFrames()
        assertTrue(graceGate.covered.value)

        stale()

        assertTrue(graceGate.covered.value)
    }

    @Test
    fun graceAfterCapReveal_keepsCauseCap() {
        graceGate.begin()
        graceGate.onImageStarted(Any())
        graceGate.markReady()
        runFrames()
        graceGate.onCap()

        graces.single().second()

        assertEquals(listOf("revealed:CAP:1"), revealedLog())
    }

    @Test
    fun capAfterGraceReveal_keepsCauseGrace() {
        graceGate.begin()
        graceGate.onImageStarted(Any())
        graceGate.markReady()
        runFrames()
        graces.single().second()

        graceGate.onCap()
        graceGate.revealNow()

        assertEquals(listOf("revealed:GRACE:1"), revealedLog())
        assertFalse(graceGate.covered.value)
    }

    @Test
    fun markReadyBeforeAnotherBegin_armsNoGrace() {
        graceGate.begin()
        graceGate.onImageStarted(Any())
        graceGate.markReady()
        graceGate.begin()

        runFrames()

        assertTrue(graces.isEmpty())
        assertTrue(graceGate.covered.value)
    }

    @Test
    fun graceAfterReset_isDropped() {
        graceGate.begin()
        graceGate.onImageStarted(Any())
        graceGate.markReady()
        runFrames()
        val stale = graces.single().second
        graceGate.reset()
        graceGate.begin()

        stale()

        assertTrue(graceGate.covered.value)
    }

    @Test
    fun markReady_reportsReadyRequestedBeforeTheFrameWait() {
        val observed = RecordingRevealObserver()
        val frames = mutableListOf<() -> Unit>()
        val framed = RevealGate(clock = { now }, afterFrame = { frames += it }, observer = observed)

        framed.markReady()
        assertEquals(0, observed.readyRequested)

        framed.begin()
        framed.markReady()
        assertEquals(1, observed.readyRequested)
        assertTrue(framed.covered.value)
        assertEquals(emptyList<String>(), observed.log)
    }

    @Test
    fun heldDrained_reportedAfterACapReveal() {
        val observed = RecordingRevealObserver()
        val observedGate = RevealGate(clock = { now }, observer = observed)
        val a = Any()
        observedGate.begin()
        observedGate.onImageStarted(a)
        observedGate.onCap()
        observedGate.markReady()
        observedGate.onImageFinished(a)
        assertEquals(listOf("revealed:CAP:1", "ready:1", "drained"), observed.log)
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
