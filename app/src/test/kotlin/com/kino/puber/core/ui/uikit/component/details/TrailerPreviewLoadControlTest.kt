package com.kino.puber.core.ui.uikit.component.details

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TrailerPreviewLoadControlTest {

    @Test
    fun trailerPreviewBuffer_matchesAcceptedValues() {
        assertEquals(10_000, TRAILER_PREVIEW_BUFFER.minBufferMs)
        assertEquals(15_000, TRAILER_PREVIEW_BUFFER.maxBufferMs)
        assertEquals(1_000, TRAILER_PREVIEW_BUFFER.bufferForPlaybackMs)
        assertEquals(2_000, TRAILER_PREVIEW_BUFFER.bufferForPlaybackAfterRebufferMs)
        assertEquals(12 * 1024 * 1024, TRAILER_PREVIEW_BUFFER.targetBufferBytes)
        assertEquals(false, TRAILER_PREVIEW_BUFFER.prioritizeTimeOverSize)
    }

    @Test
    fun buildTrailerPreviewLoadControl_doesNotThrow_withPreviewBuffer() {
        assertDoesNotThrow<Any> { buildTrailerPreviewLoadControl() }
    }
}
