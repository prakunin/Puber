package com.kino.puber.ui.feature.player.vm

import android.os.SystemClock
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters

/**
 * What a quality switch did to the frames the decoder had already accepted. Media3 reports four
 * outcomes; only reuse that skips the flush lets the queue through, so the other three collapse
 * into the two that discard it.
 *
 * Declared outside [VideoRenderDiagnostics] on purpose: that class is opted in to Media3's unstable
 * API, and nothing about these three states is, so naming them costs [PlaybackDebugFormat] no
 * opt-in of its own.
 */
internal enum class DecoderTransition {
    /** Reused without a flush: whatever it had queued is still on its way to the screen. */
    Kept,

    /** Reused, but flushed first, so the queue went with the flush. */
    Flushed,

    /** Recreated outright. */
    Restarted,
}

/**
 * The readings that answer one question: is the picture running at the speed of the stream?
 *
 * Neither half of the answer is visible in the counters the overlay showed before. A renderer that
 * releases frames early and a decoder that sits on a queue of finished frames both leave the
 * dropped-frame count alone — in both cases ExoPlayer believes every frame went out on time — and
 * both are reached through the same door: a quality switch on a bad buffer, which
 * [PlaybackErrorPolicy] chooses deliberately when a segment fails to load.
 *
 * So this records the rate frames actually reach the screen at, how late they are when they get
 * there, and the last switch that could have caused it.
 */
@UnstableApi
internal class VideoRenderDiagnostics(
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
) {

    data class Readings(
        val renderRate: String,
        val frameDrops: String,
        val frameReleaseOffset: String,
        val videoSwitch: String,
    )

    private var renderedFramesAtSample = 0
    private var offsetTotalAtSampleUs = 0L
    private var offsetCountAtSample = 0
    private var sampleAtMs = 0L
    private var renderRate = unknownRate(streamFrameRate = null)
    private var frameReleaseOffset = PlaybackDebugFormat.frameReleaseOffset(0, 0)
    private var videoSwitch: String? = null

    fun reset() {
        renderedFramesAtSample = 0
        offsetTotalAtSampleUs = 0L
        offsetCountAtSample = 0
        sampleAtMs = 0L
        renderRate = unknownRate(streamFrameRate = null)
        frameReleaseOffset = PlaybackDebugFormat.frameReleaseOffset(0, 0)
        videoSwitch = null
    }

    /**
     * Takes formats rather than the `DecoderReuseEvaluation` they came from: that class touches
     * `TextUtils` in its constructor, and reading it here would drag the Android framework into
     * every test of this one.
     */
    fun onFormatSwitched(
        fromFormat: Format?,
        toFormat: Format,
        decoderTransition: DecoderTransition,
        atPositionMs: Long,
    ) {
        videoSwitch = PlaybackDebugFormat.videoSwitch(
            fromResolution = fromFormat.resolutionLabel(),
            toResolution = toFormat.resolutionLabel(),
            decoderTransition = decoderTransition,
            atPositionMs = atPositionMs,
        )
    }

    fun read(counters: DecoderCounters?, streamFrameRate: Float?): Readings {
        // Written on the playback thread; ExoPlayer's own debug helper reads them the same way.
        counters?.ensureUpdated()
        sampleWindow(counters, streamFrameRate)
        return Readings(
            renderRate = renderRate,
            frameDrops = PlaybackDebugFormat.frameDrops(
                dropped = counters?.droppedBufferCount ?: 0,
                toKeyframe = counters?.droppedToKeyframeCount ?: 0,
                maxConsecutive = counters?.maxConsecutiveDroppedBufferCount ?: 0,
            ),
            frameReleaseOffset = frameReleaseOffset,
            videoSwitch = videoSwitch ?: UNKNOWN_VALUE,
        )
    }

    /**
     * Both readings are differences between two calls, not totals for the playback.
     *
     * The rate has to be, to describe what is on screen now. So does the release offset, and for a
     * sharper reason: its counters run for the life of the renderer, so frames that start arriving
     * 40 ms late an hour in are averaged against tens of thousands of punctual ones and the line
     * would still read `+0.0 ms` while the picture is visibly late — the very case this class
     * exists to catch.
     */
    private fun sampleWindow(counters: DecoderCounters?, streamFrameRate: Float?) {
        val renderedFrames = counters?.renderedOutputBufferCount ?: 0
        val offsetTotalUs = counters?.totalVideoFrameProcessingOffsetUs ?: 0L
        val offsetCount = counters?.videoFrameProcessingOffsetCount ?: 0
        val now = elapsedRealtimeMs()
        val elapsedMs = now - sampleAtMs

        when {
            isNewWindow(renderedFrames, offsetCount, elapsedMs) -> {
                renderRate = unknownRate(streamFrameRate)
                frameReleaseOffset = PlaybackDebugFormat.frameReleaseOffset(0, 0)
            }

            elapsedMs >= WINDOW_MS -> {
                renderRate = PlaybackDebugFormat.renderRate(
                    renderedFrames = renderedFrames - renderedFramesAtSample,
                    elapsedMs = elapsedMs,
                    streamFrameRate = streamFrameRate,
                )
                frameReleaseOffset = PlaybackDebugFormat.frameReleaseOffset(
                    totalOffsetUs = offsetTotalUs - offsetTotalAtSampleUs,
                    sampleCount = offsetCount - offsetCountAtSample,
                )
            }

            else -> return
        }
        renderedFramesAtSample = renderedFrames
        offsetTotalAtSampleUs = offsetTotalUs
        offsetCountAtSample = offsetCount
        sampleAtMs = now
    }

    /**
     * A counter that went backwards means the video renderer was re-enabled with fresh counters. A
     * window far older than the polling interval means the readings stopped and started again —
     * paused with the overlay hidden, say — and the frames either side of that gap do not describe
     * one stretch of playback: measuring across it would report a slow picture that never happened.
     */
    private fun isNewWindow(renderedFrames: Int, offsetCount: Int, elapsedMs: Long): Boolean =
        sampleAtMs == 0L ||
            elapsedMs > MAX_WINDOW_AGE_MS ||
            renderedFrames < renderedFramesAtSample ||
            offsetCount < offsetCountAtSample

    private fun unknownRate(streamFrameRate: Float?): String = PlaybackDebugFormat.renderRate(
        renderedFrames = 0,
        elapsedMs = 0,
        streamFrameRate = streamFrameRate,
    )

    private fun Format?.resolutionLabel(): String? =
        this?.takeIf { it.width > 0 && it.height > 0 }?.let { "${it.width}x${it.height}" }

    private companion object {
        const val UNKNOWN_VALUE = "—"

        /** Long enough that a 24 fps stream contributes whole frames to every reading. */
        const val WINDOW_MS = 1_000L

        /** Six polling intervals: a longer gap is a break in the readings, not a slow picture. */
        const val MAX_WINDOW_AGE_MS = 3_000L
    }
}
