package com.kino.puber.core.startup

import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class StartupImageActivityTest {
    private val observed = RecordingRevealObserver()
    private val gate = RevealGate(clock = { 0 }, observer = observed)
    private val activity = StartupImageActivity(gate)

    private fun request() = mockk<ImageRequest>()

    @Test
    fun requestIsInFlightFromResolvedSizeUntilSuccess() {
        val a = request()
        gate.begin()
        activity.onStart(a)
        activity.resolveSizeEnd(a, Size.ORIGINAL)
        gate.markReady()
        assertTrue(gate.covered.value)
        activity.onSuccess(a, mockk<SuccessResult>())
        assertFalse(gate.covered.value)
    }

    @Test
    fun errorAndCancelEndARequest() {
        val a = request()
        val b = request()
        gate.begin()
        activity.resolveSizeEnd(a, Size.ORIGINAL)
        activity.resolveSizeEnd(b, Size.ORIGINAL)
        gate.markReady()
        activity.onError(a, mockk<ErrorResult>())
        assertTrue(gate.covered.value)
        activity.onCancel(b)
        assertFalse(gate.covered.value)
    }

    @Test
    fun cancelWithoutResolvedSizeLeavesIdle() {
        val a = request()
        val b = request()
        gate.begin()
        activity.onStart(a)
        activity.onCancel(a)
        activity.onSuccess(b, mockk<SuccessResult>())
        gate.markReady()
        assertFalse(gate.covered.value)
    }

    @Test
    fun inFlightCountAtRevealCountsDistinctRequests() {
        val a = request()
        val b = request()
        gate.begin()
        activity.resolveSizeEnd(a, Size.ORIGINAL)
        activity.resolveSizeEnd(a, Size.ORIGINAL)
        activity.resolveSizeEnd(b, Size.ORIGINAL)
        gate.onCap()
        assertEquals(listOf("revealed:CAP:2"), observed.log)
    }
}
