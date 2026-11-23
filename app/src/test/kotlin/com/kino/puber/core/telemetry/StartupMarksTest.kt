package com.kino.puber.core.telemetry

import com.kino.puber.core.startup.RevealCause
import com.kino.puber.util.FakeTelemetryClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class StartupMarksTest {
    private val clock = FakeTelemetryClock(elapsed = 500)
    private val out = mutableListOf<TelemetryEvent.Startup>()
    private val marks = StartupMarks(procStartMs = 100, appCreateMs = 200, record = out::add, clock = clock)
    private val queued = mutableListOf<Pair<Long, () -> Unit>>()
    private val queuedMarks = StartupMarks(
        procStartMs = 100,
        appCreateMs = 200,
        record = out::add,
        clock = clock,
        schedule = { delayMs, task -> queued += delayMs to task },
    )

    private val awaitedMarks = StartupMarks(
        procStartMs = 100,
        appCreateMs = 200,
        record = out::add,
        clock = clock,
        schedule = { delayMs, task -> queued += delayMs to task },
        awaitProfileStatus = true,
    )

    private fun runQueued(delayMs: Long? = null) {
        val due = queued.filter { delayMs == null || it.first == delayMs }
        queued.removeAll(due)
        due.forEach { it.second() }
    }

    @Test
    fun oneLineWithAscendingMarksOnFirstContent() {
        clock.elapsed = 300
        marks.markAppComposed()
        clock.elapsed = 900
        marks.markFirstContent()
        marks.markFirstContent()
        marks.onTimeout()

        val startup = out.single()
        assertEquals(100L, startup.procStartMs)
        assertEquals(200L, startup.appCreateMs)
        assertEquals(300L, startup.appComposedMs)
        assertEquals(900L, startup.firstContentMs)
    }

    @Test
    fun lineWaitsForRevealedAndCarriesBothMarks() {
        clock.elapsed = 900
        marks.markFirstContent()
        assertEquals(0, out.size)
        clock.elapsed = 1_200
        marks.markRevealed()

        val startup = out.single()
        assertEquals(900L, startup.firstContentMs)
        assertEquals(1_200L, startup.revealedMs)
    }

    @Test
    fun oneLineWhenRevealedComesBeforeFirstContent() {
        clock.elapsed = 700
        marks.markRevealed()
        assertEquals(0, out.size)
        marks.markReady(0)
        clock.elapsed = 800
        marks.markFirstContent()
        marks.markRevealed()

        val startup = out.single()
        assertEquals(800L, startup.firstContentMs)
        assertEquals(700L, startup.revealedMs)
    }

    @Test
    fun fallbackLineHasNoRevealedWhenItNeverCame() {
        clock.elapsed = 900
        marks.markFirstContent()
        marks.onTimeout()

        val startup = out.single()
        assertEquals(900L, startup.firstContentMs)
        assertNull(startup.revealedMs)
    }

    @Test
    fun revealedKeepsTheFirstValue() {
        clock.elapsed = 700
        marks.markRevealed()
        clock.elapsed = 800
        marks.markRevealed()
        marks.onTimeout()
        assertEquals(700L, out.single().revealedMs)
    }

    @Test
    fun appComposedKeepsTheFirstValue() {
        clock.elapsed = 300
        marks.markAppComposed()
        clock.elapsed = 400
        marks.markAppComposed()
        marks.onTimeout()
        assertEquals(300L, out.single().appComposedMs)
    }

    @Test
    fun revealCauseAndImageMarksAreCarriedOnTheLine() {
        clock.elapsed = 600
        marks.markReady(inFlight = 4)
        clock.elapsed = 650
        marks.markImagesIdle()
        clock.elapsed = 700
        marks.markHeldDrained()
        clock.elapsed = 710
        marks.markRevealCause(RevealCause.READY, inFlight = 1)
        marks.onTimeout()

        val startup = out.single()
        assertEquals(RevealCause.READY, startup.cause)
        assertEquals(600L, startup.readyMs)
        assertEquals(650L, startup.imagesIdleMs)
        assertEquals(700L, startup.readyDrainedMs)
        assertEquals(4, startup.readyInFlight)
        assertEquals(1, startup.inFlight)
    }

    @Test
    fun revealCauseKeepsTheFirstValue() {
        marks.markRevealCause(RevealCause.CAP, inFlight = 2)
        marks.markRevealCause(RevealCause.FORCED, inFlight = 0)
        marks.onTimeout()
        val startup = out.single()
        assertEquals(RevealCause.CAP, startup.cause)
        assertEquals(2, startup.inFlight)
    }

    @Test
    fun imagesIdleKeepsTheLastValue() {
        clock.elapsed = 600
        marks.markImagesIdle()
        clock.elapsed = 800
        marks.markImagesIdle()
        marks.onTimeout()
        assertEquals(800L, out.single().imagesIdleMs)
    }

    @Test
    fun imageMarksAreAbsentWhenNotReached() {
        marks.onTimeout()
        val startup = out.single()
        assertNull(startup.cause)
        assertNull(startup.readyMs)
        assertNull(startup.imagesIdleMs)
        assertNull(startup.readyDrainedMs)
        assertNull(startup.readyInFlight)
        assertNull(startup.inFlight)
    }

    @Test
    fun readyRequestedIsCarriedOnTheLine() {
        clock.elapsed = 650
        marks.markReadyRequested()
        marks.onTimeout()
        assertEquals(650L, out.single().readyReqMs)
    }

    @Test
    fun readyRequestedKeepsTheFirstValue() {
        clock.elapsed = 650
        marks.markReadyRequested()
        clock.elapsed = 800
        marks.markReadyRequested()
        marks.onTimeout()
        assertEquals(650L, out.single().readyReqMs)
    }

    @Test
    fun gateMarksEmitOnlyThroughSchedule() {
        queuedMarks.markFirstContent()
        queuedMarks.markRevealed()
        queuedMarks.markReady(0)
        queuedMarks.markReadyRequested()
        queuedMarks.markHeldDrained()
        queuedMarks.markRevealCause(RevealCause.FORCED, 0)
        queuedMarks.markImagesIdle()
        queuedMarks.onKeyDropped()
        assertEquals(0, out.size)

        runQueued()
        assertEquals(1, out.size)
    }

    @Test
    fun lineWaitsForReadyAfterACapReveal() {
        queuedMarks.markFirstContent()
        queuedMarks.markRevealCause(RevealCause.CAP, 3)
        queuedMarks.markRevealed()
        runQueued(0)
        assertEquals(0, out.size)

        clock.elapsed = 900
        queuedMarks.markReady(0)
        runQueued(0)

        val startup = out.single()
        assertEquals(900L, startup.readyMs)
        assertEquals(RevealCause.CAP, startup.cause)
    }

    @Test
    fun lineWaitsForHeldDrainWhenReadyHeldRequests() {
        queuedMarks.markFirstContent()
        queuedMarks.markRevealCause(RevealCause.CAP, 2)
        queuedMarks.markRevealed()
        queuedMarks.markReady(2)
        runQueued(0)
        assertEquals(0, out.size)

        clock.elapsed = 1_300
        queuedMarks.markHeldDrained()
        runQueued(0)

        assertEquals(1_300L, out.single().readyDrainedMs)
    }

    @Test
    fun graceRevealLineWaitsForHeldDrain() {
        queuedMarks.markFirstContent()
        queuedMarks.markRevealCause(RevealCause.GRACE, 2)
        queuedMarks.markRevealed()
        queuedMarks.markReady(2)
        runQueued(0)
        assertEquals(0, out.size)

        clock.elapsed = 1_300
        queuedMarks.markHeldDrained()
        runQueued(0)

        val startup = out.single()
        assertEquals(RevealCause.GRACE, startup.cause)
        assertEquals(1_300L, startup.readyDrainedMs)
    }

    @Test
    fun forcedRevealEmitsWithoutReady() {
        queuedMarks.markRevealCause(RevealCause.FORCED, 0)
        queuedMarks.markFirstContent()
        queuedMarks.markRevealed()
        runQueued(0)

        assertNull(out.single().readyMs)
        runQueued(LINE_WAIT_AFTER_REVEAL_MS)
        assertEquals(1, out.size)
    }

    @Test
    fun lineEmitsFiveSecondsAfterRevealWithoutReady() {
        queuedMarks.markFirstContent()
        queuedMarks.markRevealed()
        runQueued(0)
        assertEquals(0, out.size)
        assertEquals(listOf(5_000L), queued.map { it.first })

        runQueued(5_000)

        val startup = out.single()
        assertEquals(500L, startup.revealedMs)
        assertNull(startup.readyMs)
    }

    @Test
    fun completionAndFiveSecondBoundEmitOneLine() {
        queuedMarks.markFirstContent()
        queuedMarks.markRevealed()
        queuedMarks.markReady(0)
        assertEquals(0, out.size)

        runQueued()

        assertEquals(1, out.size)
    }

    @Test
    fun droppedKeysAreCountedOnTheLine() {
        marks.onKeyDropped()
        marks.onKeyDropped()
        marks.onTimeout()
        assertEquals(2, out.single().droppedKeys)
    }

    @Test
    fun fallbackLineAfterSixtySecondsWithMarksSoFar() {
        marks.onTimeout()
        assertEquals(1, out.size)
        assertNull(out.single().firstContentMs)
        assertNull(out.single().appComposedMs)

        marks.markFirstContent()
        assertEquals(1, out.size)
    }

    @Test
    fun readyLineWaitsForProfileStatusWhenAwaited() {
        awaitedMarks.markFirstContent()
        awaitedMarks.markRevealed()
        awaitedMarks.markReady(0)
        runQueued(0)
        assertEquals(0, out.size)

        awaitedMarks.markProfileStatus(327_680)
        runQueued(0)

        assertEquals(327_680, out.single().profileStatus)
    }

    @Test
    fun boundEmitsWithoutProfileStatusWhenAwaited() {
        awaitedMarks.markFirstContent()
        awaitedMarks.markRevealed()
        awaitedMarks.markReady(0)
        runQueued(0)
        assertEquals(0, out.size)
        assertEquals(listOf(LINE_WAIT_AFTER_REVEAL_WITH_PROFILE_MS), queued.map { it.first })

        runQueued(LINE_WAIT_AFTER_REVEAL_WITH_PROFILE_MS)

        assertNull(out.single().profileStatus)
    }

    @Test
    fun forcedRevealEmitsWithoutProfileStatusWhenAwaited() {
        awaitedMarks.markFirstContent()
        awaitedMarks.markRevealed()
        awaitedMarks.markRevealCause(RevealCause.FORCED, 0)
        runQueued(0)

        assertNull(out.single().profileStatus)
    }

    @Test
    fun profileStatusKeepsTheFirstValue() {
        awaitedMarks.markProfileStatus(1)
        awaitedMarks.markProfileStatus(2)
        awaitedMarks.onTimeout()

        assertEquals(1, out.single().profileStatus)
    }

    @Test
    fun readyLineEmitsWithoutProfileStatusByDefault() {
        queuedMarks.markFirstContent()
        queuedMarks.markRevealed()
        queuedMarks.markReady(0)
        runQueued(0)

        assertNull(out.single().profileStatus)
    }
}
