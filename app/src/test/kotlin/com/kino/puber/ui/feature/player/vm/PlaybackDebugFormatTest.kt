package com.kino.puber.ui.feature.player.vm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackDebugFormatTest {

    // region streamSource

    @Test
    fun streamSource_returnsFirstHostLabel_uppercased() {
        assertEquals(
            "MSK01",
            PlaybackDebugFormat.streamSource("https://msk01.cdn.example.net/hls/movie.m3u8?token=abc"),
        )
    }

    @Test
    fun streamSource_keepsHyphensAndDigits() {
        assertEquals(
            "SPB-02",
            PlaybackDebugFormat.streamSource("http://spb-02.cdn.example.net:8080/movie.mp4"),
        )
    }

    @Test
    fun streamSource_worksWithoutScheme() {
        assertEquals("MSK01", PlaybackDebugFormat.streamSource("msk01.cdn.example.net/movie.m3u8"))
    }

    @Test
    fun streamSource_ignoresUserInfo() {
        assertEquals("MSK01", PlaybackDebugFormat.streamSource("https://user:pass@msk01.cdn.example.net/v"))
    }

    @Test
    fun streamSource_keepsWholeHost_whenItHasNoNodeLabel() {
        assertEquals("example.net", PlaybackDebugFormat.streamSource("https://example.net/movie.m3u8"))
    }

    @Test
    fun streamSource_keepsIpv4Literal() {
        assertEquals("10.0.0.5", PlaybackDebugFormat.streamSource("http://10.0.0.5:8080/movie.mp4"))
    }

    @Test
    fun streamSource_keepsIpv6Literal() {
        assertEquals("2001:db8::1", PlaybackDebugFormat.streamSource("http://[2001:db8::1]:8080/movie.mp4"))
    }

    @Test
    fun streamSource_isUnknown_whenUrlIsMissing() {
        assertEquals("—", PlaybackDebugFormat.streamSource(null))
        assertEquals("—", PlaybackDebugFormat.streamSource("   "))
        assertEquals("—", PlaybackDebugFormat.streamSource("https:///movie.m3u8"))
    }

    // endregion

    // region bufferFill

    @Test
    fun bufferFill_showsAllocatedAgainstTarget() {
        assertEquals("39.7 / 64 MB", PlaybackDebugFormat.bufferFill(41_640_755, 64 * MIB))
    }

    @Test
    fun bufferFill_keepsOneDecimal_whenBufferIsNearlyEmpty() {
        assertEquals("0.0 / 32 MB", PlaybackDebugFormat.bufferFill(0, 32 * MIB))
    }

    @Test
    fun bufferFill_dropsTarget_whenItIsNotConfigured() {
        assertEquals("12.0 MB", PlaybackDebugFormat.bufferFill(12 * MIB, 0))
    }

    // endregion

    private companion object {
        const val MIB = 1024 * 1024
    }
}
