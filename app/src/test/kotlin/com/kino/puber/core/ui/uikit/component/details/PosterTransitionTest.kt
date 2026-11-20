package com.kino.puber.core.ui.uikit.component.details

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class PosterTransitionTest {

    /** One frame per 16 ms of the test's virtual time. */
    private class VirtualFrameClock(private val now: () -> Long) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(FRAME_MS)
            return onFrame(now() * NANOS_PER_MS)
        }
    }

    private fun transition(offsetFrom: Float = 0F, alphaFrom: Float = 1F) = PosterTransition(
        sign = 1,
        image = mockk<coil3.Image>(relaxed = true),
        outgoingOffsetFrom = offsetFrom,
        outgoingAlphaFrom = alphaFrom,
        shiftPx = 48F,
    )

    @Test
    fun run_clearsOutgoingImage_whenMotionEnds() = runTest {
        val record = transition().also { it.settle() }

        withFrameClock { record.run(48F) }

        assertNull(record.outgoingImage)
        assertEquals(0F, record.stillOffset.value)
    }

    @Test
    fun run_keepsOutgoingImageUnderTheStill_untilMotionEnds() = runTest {
        val record = transition().also { it.settle() }
        val job = launch { withFrameClock { record.run(48F) } }

        advanceTimeBy(PanelMotion.DURATION_MS / 2L)

        assertNotNull(record.outgoingImage)
        job.join()
        assertNull(record.outgoingImage)
    }

    @Test
    fun run_bringsASeededOutgoingLayerToRest_whileWaitingForThePicture() = runTest {
        // Seeded from a ready predecessor that was mid-motion: see-through and shifted.
        val record = transition(offsetFrom = 30F, alphaFrom = 0.5F)
        val job = launch { withFrameClock { record.run(48F) } }

        advanceTimeBy(PanelMotion.DURATION_MS + FRAME_MS)

        assertNotNull(record.outgoingImage)
        assertEquals(1F, record.outgoingAlpha.value)
        assertEquals(0F, record.outgoingOffset.value)
        job.cancel()
    }

    @Test
    fun run_keepsRestingTheAlpha_whenSettledBeforeRun() = runTest {
        val record = transition(offsetFrom = 30F, alphaFrom = 0.5F).also { it.settle() }
        val job = launch { withFrameClock { record.run(48F) } }

        advanceTimeBy(PanelMotion.POSTER_FADE_IN_MS + 2L * FRAME_MS)

        assertNotNull(record.outgoingImage)
        assertEquals(1F, record.outgoingAlpha.value)
        job.join()
        assertNull(record.outgoingImage)
    }

    @Test
    fun run_keepsRestingTheAlpha_whenSettledMidRest() = runTest {
        val record = transition(offsetFrom = 30F, alphaFrom = 0.5F)
        val job = launch { withFrameClock { record.run(48F) } }
        advanceTimeBy(FRAME_MS)
        val settledAt = currentTime

        record.settle()
        Snapshot.sendApplyNotifications()
        advanceTimeBy(PanelMotion.POSTER_FADE_IN_MS + 2L * FRAME_MS)

        assertNotNull(record.outgoingImage)
        assertEquals(1F, record.outgoingAlpha.value)
        job.join()
        assertTrue(
            currentTime - settledAt <= PanelMotion.DURATION_MS + 2 * FRAME_MS,
            "ended ${currentTime - settledAt} ms after the settle",
        )
    }

    @Test
    fun run_bringsTheOutgoingOffsetToZero_whenSettledWithoutDirection() = runTest {
        val record = PosterTransition(
            sign = 0,
            image = mockk<coil3.Image>(relaxed = true),
            outgoingOffsetFrom = 30F,
            outgoingAlphaFrom = 1F,
            shiftPx = 48F,
        ).also { it.settle() }
        val job = launch { withFrameClock { record.run(48F) } }

        advanceTimeBy(PanelMotion.DURATION_MS + 2L * FRAME_MS)

        assertEquals(0F, record.outgoingOffset.value)
        job.join()
    }

    @Test
    fun seedFrom_isASettledLayer_withoutPredecessor() {
        assertEquals(0F to 1F, seedFrom(null))
    }

    @Test
    fun seedFrom_takesTheStill_whenPredecessorIsReadyMidMotion() = runTest {
        val previous = transition(offsetFrom = 30F, alphaFrom = 0.5F).also { it.settle() }
        val job = launch { withFrameClock { previous.run(48F) } }
        advanceTimeBy(100)

        val seed = seedFrom(previous)

        assertEquals(previous.stillOffset.value to previous.stillAlpha.value, seed)
        assertTrue(seed.first > 0F && seed.first < 48F && seed.second > 0F && seed.second < 1F, "seed $seed")
        job.cancel()
    }

    @Test
    fun seedFrom_takesTheOutgoingLayer_whenPredecessorIsWaiting() {
        val previous = transition(offsetFrom = 30F, alphaFrom = 0.5F)

        assertEquals(30F to 0.5F, seedFrom(previous))
    }

    @Test
    fun run_endsTheHold_whenSettledBeforeTheCap() = runTest {
        val record = transition()
        val job = launch { withFrameClock { record.run(48F) } }
        advanceTimeBy(100)

        record.settle()
        Snapshot.sendApplyNotifications()
        job.join()

        assertNull(record.outgoingImage)
        assertTrue(currentTime < POSTER_HOLD_MS, "ended at $currentTime ms")
    }

    @Test
    fun run_clearsOutgoingImage_whenHoldExpiresWithoutPicture() = runTest {
        val record = transition()

        withFrameClock { record.run(48F) }

        assertNull(record.outgoingImage)
        assertEquals(POSTER_HOLD_MS, currentTime)
        assertEquals(1F, record.stillAlpha.value)
    }

    private suspend fun TestScope.withFrameClock(block: suspend () -> Unit) =
        withContext(VirtualFrameClock { currentTime }) { block() }

    private companion object {
        const val FRAME_MS = 16L
        const val NANOS_PER_MS = 1_000_000L
    }
}
