package com.kino.puber.core.ui.uikit.component.details

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TrailerSurfaceRedrawTest {

    @Test
    fun surfaceNeedsRedraw_true_whenSizeGoesFromZeroToNonZero() {
        assertTrue(surfaceNeedsRedraw(0, 0, 1350, 759))
    }

    @Test
    fun surfaceNeedsRedraw_false_whenSizeUnchanged() {
        assertFalse(surfaceNeedsRedraw(1350, 759, 1350, 759))
    }

    @Test
    fun surfaceNeedsRedraw_true_whenSizeChangesBetweenNonZeroSizes() {
        assertTrue(surfaceNeedsRedraw(1350, 759, 1280, 720))
    }

    @Test
    fun surfaceNeedsRedraw_false_whenSizeBecomesZero() {
        assertFalse(surfaceNeedsRedraw(1350, 759, 0, 0))
    }

    @Test
    fun surfaceNeedsRedraw_false_whenOnlyOneDimensionIsZero() {
        assertFalse(surfaceNeedsRedraw(0, 0, 1350, 0))
        assertFalse(surfaceNeedsRedraw(0, 0, 0, 759))
    }
}
