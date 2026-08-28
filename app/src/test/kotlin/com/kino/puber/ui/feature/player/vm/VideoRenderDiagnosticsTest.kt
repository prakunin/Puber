package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.Format
import androidx.media3.exoplayer.DecoderCounters
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class VideoRenderDiagnosticsTest {

    private var nowMs = 10_000L
    private val diagnostics = VideoRenderDiagnostics { nowMs }

    @Test
    fun read_reportsNoRate_onTheFirstReading() {
        val readings = diagnostics.read(counters(renderedFrames = 100), streamFrameRate = 24f)

        assertEquals("— / 24 fps", readings.renderRate)
    }

    @Test
    fun read_measuresTheRateBetweenTwoReadings_notOverTheWholePlayback() {
        diagnostics.read(counters(renderedFrames = 1_000), streamFrameRate = 24f)
        nowMs += 2_000

        val readings = diagnostics.read(counters(renderedFrames = 1_048), streamFrameRate = 24f)

        assertEquals("24.0 / 24 fps", readings.renderRate)
    }

    @Test
    fun read_showsAPictureRunningAheadOfTheStream() {
        diagnostics.read(counters(renderedFrames = 500), streamFrameRate = 24f)
        nowMs += 1_000

        val readings = diagnostics.read(counters(renderedFrames = 572), streamFrameRate = 24f)

        assertEquals("72.0 / 24 fps", readings.renderRate)
    }

    @Test
    fun read_keepsTheLastRate_whileTheWindowIsStillTooShort() {
        diagnostics.read(counters(renderedFrames = 0), streamFrameRate = 24f)
        nowMs += 1_000
        diagnostics.read(counters(renderedFrames = 24), streamFrameRate = 24f)
        nowMs += 500

        val readings = diagnostics.read(counters(renderedFrames = 36), streamFrameRate = 24f)

        assertEquals("24.0 / 24 fps", readings.renderRate)
    }

    @Test
    fun read_opensANewWindow_whenTheRendererRestartsItsCounters() {
        diagnostics.read(counters(renderedFrames = 5_000), streamFrameRate = 24f)
        nowMs += 1_000
        diagnostics.read(counters(renderedFrames = 5_024), streamFrameRate = 24f)
        nowMs += 1_000

        // The video renderer was disabled and enabled again, so the count starts from zero.
        val afterRestart = diagnostics.read(counters(renderedFrames = 12), streamFrameRate = 24f)
        nowMs += 1_000
        val afterRestartWindow = diagnostics.read(counters(renderedFrames = 36), streamFrameRate = 24f)

        assertEquals("— / 24 fps", afterRestart.renderRate)
        assertEquals("24.0 / 24 fps", afterRestartWindow.renderRate)
    }

    @Test
    fun read_carriesTheDropCountersThrough() {
        val counters = counters(renderedFrames = 100).apply {
            droppedBufferCount = 31
            droppedToKeyframeCount = 4
            maxConsecutiveDroppedBufferCount = 12
        }

        val readings = diagnostics.read(counters, streamFrameRate = 24f)

        assertEquals("31 (keyframe 4, run 12)", readings.frameDrops)
    }

    @Test
    fun read_measuresTheReleaseOffsetOverTheWindow_notOverTheWholePlayback() {
        // An hour of punctual frames behind us, and the counters carry all of them.
        val counters = counters(renderedFrames = 86_400).apply {
            totalVideoFrameProcessingOffsetUs = 100_000
            videoFrameProcessingOffsetCount = 5_000
        }
        diagnostics.read(counters, streamFrameRate = 24f)
        nowMs += 1_000

        // Ten frames arrive 8 ms late each. Against the lifetime totals that is +0.0 ms; against
        // the window it is the picture falling behind.
        counters.totalVideoFrameProcessingOffsetUs = 100_000 - 80_000
        counters.videoFrameProcessingOffsetCount = 5_010
        counters.renderedOutputBufferCount = 86_410

        assertEquals("-8.0 ms", diagnostics.read(counters, streamFrameRate = 24f).frameReleaseOffset)
    }

    @Test
    fun read_opensANewWindow_whenTheReadingsStoppedForLongerThanThePollingInterval() {
        diagnostics.read(counters(renderedFrames = 1_000), streamFrameRate = 24f)
        // Paused with the overlay hidden: nothing polled, and the frames either side of the gap do
        // not describe one stretch of playback.
        nowMs += 30_000

        val readings = diagnostics.read(counters(renderedFrames = 1_240), streamFrameRate = 24f)

        assertEquals("— / 24 fps", readings.renderRate)
    }

    @Test
    fun read_reportsNoSwitch_untilOneHappens() {
        assertEquals("—", diagnostics.read(counters(renderedFrames = 0), 24f).videoSwitch)
    }

    @Test
    fun onFormatSwitched_recordsAQualityChangeThatKeptTheDecoder() {
        diagnostics.onFormatSwitched(
            fromFormat = format(1280, 536),
            toFormat = format(1920, 1080),
            decoderTransition = DecoderTransition.Kept,
            atPositionMs = 2_465_000,
        )

        assertEquals(
            "1280x536 -> 1920x1080, decoder kept, at 0:41:05",
            diagnostics.read(counters(renderedFrames = 0), 24f).videoSwitch,
        )
    }

    @Test
    fun onFormatSwitched_recordsAQualityChangeThatRestartedTheDecoder() {
        diagnostics.onFormatSwitched(
            fromFormat = format(1920, 1080),
            toFormat = format(1280, 536),
            decoderTransition = DecoderTransition.Restarted,
            atPositionMs = 0,
        )

        assertEquals(
            "1920x1080 -> 1280x536, decoder restarted, at 0:00:00",
            diagnostics.read(counters(renderedFrames = 0), 24f).videoSwitch,
        )
    }

    @Test
    fun onFormatSwitched_doesNotCallAFlushedDecoderKept() {
        diagnostics.onFormatSwitched(
            fromFormat = format(1280, 536),
            toFormat = format(1920, 1080),
            decoderTransition = DecoderTransition.Flushed,
            atPositionMs = 60_000,
        )

        // Reuse with a flush throws the queued frames away, so it must not read as "kept": that
        // label is the whole signal this line carries.
        assertEquals(
            "1280x536 -> 1920x1080, decoder flushed, at 0:01:00",
            diagnostics.read(counters(renderedFrames = 0), 24f).videoSwitch,
        )
    }

    @Test
    fun reset_clearsTheSwitchAndTheRateWindow() {
        diagnostics.read(counters(renderedFrames = 100), 24f)
        diagnostics.onFormatSwitched(
            fromFormat = format(1280, 536),
            toFormat = format(1920, 1080),
            decoderTransition = DecoderTransition.Kept,
            atPositionMs = 1_000,
        )
        diagnostics.reset()
        nowMs += 1_000

        val readings = diagnostics.read(counters(renderedFrames = 124), 24f)

        assertEquals("—", readings.videoSwitch)
        assertEquals("— / 24 fps", readings.renderRate)
    }

    private fun counters(renderedFrames: Int) = DecoderCounters().apply {
        renderedOutputBufferCount = renderedFrames
    }

    private fun format(width: Int, height: Int): Format = Format.Builder()
        .setWidth(width)
        .setHeight(height)
        .build()
}
