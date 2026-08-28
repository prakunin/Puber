package com.kino.puber.ui.feature.player.vm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackDebugFormatTest {

    // region playback-rate diagnostics

    @Test
    fun renderRate_reportsMeasuredRateAgainstTheStreamRate() {
        assertEquals(
            "24.0 / 24 fps",
            PlaybackDebugFormat.renderRate(renderedFrames = 48, elapsedMs = 2_000, streamFrameRate = 23.976f),
        )
    }

    @Test
    fun renderRate_showsAPictureRunningFasterThanTheStream() {
        assertEquals(
            "72.0 / 24 fps",
            PlaybackDebugFormat.renderRate(renderedFrames = 72, elapsedMs = 1_000, streamFrameRate = 24f),
        )
    }

    @Test
    fun renderRate_staysUnknown_untilTheWindowIsLongEnoughToHoldWholeFrames() {
        assertEquals(
            "— / 24 fps",
            PlaybackDebugFormat.renderRate(renderedFrames = 12, elapsedMs = 500, streamFrameRate = 24f),
        )
    }

    @Test
    fun renderRate_survivesAStreamThatDeclaresNoRate() {
        assertEquals(
            "24.0 / — fps",
            PlaybackDebugFormat.renderRate(renderedFrames = 24, elapsedMs = 1_000, streamFrameRate = null),
        )
    }

    @Test
    fun frameDrops_keepsTheKeyframeJumpsAndTheLongestRun() {
        assertEquals(
            "31 (keyframe 4, run 12)",
            PlaybackDebugFormat.frameDrops(dropped = 31, toKeyframe = 4, maxConsecutive = 12),
        )
    }

    @Test
    fun frameReleaseOffset_averagesTheSamples_andSignsThem() {
        assertEquals(
            "-8.0 ms",
            PlaybackDebugFormat.frameReleaseOffset(totalOffsetUs = -80_000, sampleCount = 10),
        )
        assertEquals(
            "—",
            PlaybackDebugFormat.frameReleaseOffset(totalOffsetUs = 0, sampleCount = 0),
        )
    }

    @Test
    fun videoSwitch_namesBothResolutions_andWhatBecameOfTheQueuedFrames() {
        assertEquals(
            "1280x536 -> 1920x1080, decoder kept, at 0:41:05",
            PlaybackDebugFormat.videoSwitch(
                fromResolution = "1280x536",
                toResolution = "1920x1080",
                decoderTransition = DecoderTransition.Kept,
                atPositionMs = 2_465_000,
            ),
        )
    }

    @Test
    fun videoSwitch_separatesAFlushedDecoderFromOneThatKeptItsQueue() {
        assertEquals(
            "1920x1080 -> 1280x536, decoder flushed, at 0:01:00",
            PlaybackDebugFormat.videoSwitch(
                fromResolution = "1920x1080",
                toResolution = "1280x536",
                decoderTransition = DecoderTransition.Flushed,
                atPositionMs = 60_000,
            ),
        )
    }

    @Test
    fun videoSwitch_reportsARestartedDecoder_andAnUnknownSide() {
        assertEquals(
            "— -> 1920x1080, decoder restarted, at 0:00:07",
            PlaybackDebugFormat.videoSwitch(
                fromResolution = null,
                toResolution = "1920x1080",
                decoderTransition = DecoderTransition.Restarted,
                atPositionMs = 7_400,
            ),
        )
    }

    // endregion

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
    fun streamSource_extractsLegacyMoscowNode_fromMediaHost() {
        assertEquals(
            "MSK05",
            PlaybackDebugFormat.streamSource(
                "https://video.msk-static-05.cdn.example.net/hls/segment.ts?token=abc"
            ),
        )
    }

    @Test
    fun streamSource_mapsLegacyAmsterdamAndRussiaAliases() {
        assertEquals(
            "NL02",
            PlaybackDebugFormat.streamSourceHost("video.ams-static-02.cdn.example.net"),
        )
        assertEquals(
            "RU06",
            PlaybackDebugFormat.streamSourceHost("video.rus-static-06.cdn.example.net"),
        )
    }

    @Test
    fun streamSourceHost_supportsEffectiveMediaLoadHost() {
        assertEquals(
            "MSK07",
            PlaybackDebugFormat.streamSourceHost("edge.msk-stream-07.cdn.example.net"),
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
