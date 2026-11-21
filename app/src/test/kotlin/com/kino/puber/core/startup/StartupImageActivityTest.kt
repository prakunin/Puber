package com.kino.puber.core.startup

import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class StartupImageActivityTest {
    private val reports = mutableListOf<Boolean>()
    private val activity = StartupImageActivity { reports += it }

    private fun request() = mockk<ImageRequest>()

    @Test
    fun requestIsInFlightFromResolvedSizeUntilSuccess() {
        val a = request()
        activity.onStart(a)
        assertEquals(emptyList<Boolean>(), reports)
        activity.resolveSizeEnd(a, Size.ORIGINAL)
        assertEquals(listOf(true), reports)
        activity.onSuccess(a, mockk<SuccessResult>())
        assertEquals(listOf(true, false), reports)
    }

    @Test
    fun errorAndCancelEndARequest() {
        val a = request()
        val b = request()
        activity.resolveSizeEnd(a, Size.ORIGINAL)
        activity.resolveSizeEnd(b, Size.ORIGINAL)
        activity.onError(a, mockk<ErrorResult>())
        assertEquals(listOf(true), reports)
        activity.onCancel(b)
        assertEquals(listOf(true, false), reports)
    }

    @Test
    fun cancelWithoutResolvedSizeLeavesIdle() {
        val a = request()
        val b = request()
        activity.onStart(a)
        activity.onCancel(a)
        activity.onSuccess(b, mockk<SuccessResult>())
        assertEquals(emptyList<Boolean>(), reports)
    }

    @Test
    fun busyIsReportedOnlyOnEdges() {
        val a = request()
        val b = request()
        val c = request()
        listOf(a, b, c).forEach { activity.resolveSizeEnd(it, Size.ORIGINAL) }
        assertEquals(listOf(true), reports)
        activity.onSuccess(a, mockk<SuccessResult>())
        activity.onSuccess(b, mockk<SuccessResult>())
        assertEquals(listOf(true), reports)
        activity.onSuccess(c, mockk<SuccessResult>())
        assertEquals(listOf(true, false), reports)
        activity.onSuccess(c, mockk<SuccessResult>())
        assertEquals(listOf(true, false), reports)
    }
}
