package com.kino.puber.ui.feature.player.vm

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Formatting for the diagnostics shown by the info panel and the debug overlay.
 *
 * Kept out of [PlaybackController] so the parsing rules can be tested without an ExoPlayer
 * instance. Values stay technical (English units), only the row labels around them are localised.
 */
internal object PlaybackDebugFormat {

    /** Short name of the CDN node encoded in the delivery hostname. */
    fun streamSource(streamUrl: String?): String {
        val host = host(streamUrl) ?: return UNKNOWN_VALUE
        return streamSourceHost(host)
    }

    /**
     * Converts a media-load hostname into the label shown by Stream info.
     *
     * KinoPub's older delivery names encode the region and node in a middle label such as
     * `msk-static-05`; the original client displayed that as `MSK05` and mapped Amsterdam/Russia
     * aliases to `NL`/`RU`. Newer hosts commonly put an already useful node name first
     * (`msk01.cdn.…`), which remains the fallback.
     */
    fun streamSourceHost(streamHost: String?): String {
        val host = streamHost
            ?.trim()
            ?.removePrefix("[")
            ?.removeSuffix("]")
            ?.removeSuffix(".")
            ?.takeIf(String::isNotEmpty)
            ?: return UNKNOWN_VALUE
        LEGACY_CDN_NODE.find(host)?.let { match ->
            val region = when (match.groupValues[1].uppercase(Locale.ROOT)) {
                "AMS" -> "NL"
                "RUS" -> "RU"
                else -> match.groupValues[1].uppercase(Locale.ROOT)
            }
            return region + match.groupValues[2]
        }
        val labels = host.split('.')
        return if (host.isIpLiteral() || labels.size < MIN_LABELS_FOR_NODE) {
            host
        } else {
            labels.first().uppercase(Locale.ROOT)
        }
    }

    /**
     * How much of the buffer budget is actually held, as `used / budget`.
     *
     * The budget comes from [DeviceBufferConfig], which every preset — "Auto" included — resolves
     * against the device heap, so the number is not derivable from the preset name alone.
     */
    fun bufferFill(allocatedBytes: Int, targetBytes: Int): String {
        val allocated = String.format(Locale.US, "%.1f", allocatedBytes / MIB.toDouble())
        if (targetBytes <= 0) return "$allocated $MEGABYTES"
        val target = (targetBytes / MIB.toDouble()).roundToInt()
        return "$allocated / $target $MEGABYTES"
    }

    /**
     * How fast frames actually reach the screen, against the rate the stream declares — `24.0 / 24
     * fps`.
     *
     * This is the reading that separates a picture racing ahead from a picture arriving late. A
     * renderer releasing frames early reports a rate above the stream's own; a decoder sitting on a
     * queue of finished frames reports the stream's rate exactly, while the screen shows something
     * older. Neither shows up in the dropped-frame count, because in both cases ExoPlayer believes
     * it released every frame on time.
     */
    fun renderRate(renderedFrames: Int, elapsedMs: Long, streamFrameRate: Float?): String {
        val declared = streamFrameRate
            ?.takeIf { it > 0f }
            ?.let { String.format(Locale.US, "%.0f", it) }
            ?: UNKNOWN_VALUE
        if (elapsedMs < MIN_RATE_WINDOW_MS || renderedFrames < 0) {
            return "$UNKNOWN_VALUE / $declared $FRAMES_PER_SECOND"
        }
        val measured = renderedFrames * MILLIS_PER_SECOND / elapsedMs.toDouble()
        return String.format(Locale.US, "%.1f / %s $FRAMES_PER_SECOND", measured, declared)
    }

    /** Everything the renderer threw away, not just the running total the overlay used to show. */
    fun frameDrops(dropped: Int, toKeyframe: Int, maxConsecutive: Int): String =
        "$dropped (keyframe $toKeyframe, run $maxConsecutive)"

    /**
     * The mean gap between when a frame was due and when it was released, in milliseconds.
     * Negative means late.
     */
    fun frameReleaseOffset(totalOffsetUs: Long, sampleCount: Int): String {
        if (sampleCount <= 0) return UNKNOWN_VALUE
        val meanMs = totalOffsetUs / sampleCount.toDouble() / MICROS_PER_MILLI
        return String.format(Locale.US, "%+.1f ms", meanMs)
    }

    /**
     * The last time the stream changed quality, and what it did to the frames the decoder was
     * already holding.
     *
     * Only [DecoderTransition.Kept] leaves that queue on its way to the
     * screen, which is the case this line exists to catch. Reuse alone does not mean it survived:
     * ExoPlayer also reuses a decoder by flushing it, and that throws the queue away just as
     * recreating the decoder does.
     */
    fun videoSwitch(
        fromResolution: String?,
        toResolution: String?,
        decoderTransition: DecoderTransition,
        atPositionMs: Long,
    ): String {
        val from = fromResolution?.takeIf(String::isNotBlank) ?: UNKNOWN_VALUE
        val to = toResolution?.takeIf(String::isNotBlank) ?: UNKNOWN_VALUE
        val decoder = when (decoderTransition) {
            DecoderTransition.Kept -> "decoder kept"
            DecoderTransition.Flushed -> "decoder flushed"
            DecoderTransition.Restarted -> "decoder restarted"
        }
        return "$from -> $to, $decoder, at ${positionClock(atPositionMs)}"
    }

    private fun positionClock(positionMs: Long): String {
        val totalSeconds = (positionMs / MILLIS_PER_SECOND).coerceAtLeast(0)
        return String.format(
            Locale.US,
            "%d:%02d:%02d",
            totalSeconds / SECONDS_PER_HOUR,
            totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE,
            totalSeconds % SECONDS_PER_MINUTE,
        )
    }

    private fun host(streamUrl: String?): String? {
        val url = streamUrl?.trim().orEmpty()
        if (url.isEmpty()) return null
        val authority = url
            .substringAfter("://", url)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
        val host = if (authority.startsWith('[')) {
            authority.substringAfter('[').substringBefore(']')
        } else {
            authority.substringBefore(':')
        }
        return host.ifBlank { null }
    }

    // A colon can only survive [host] on an IPv6 literal, the port having been stripped already.
    private fun String.isIpLiteral(): Boolean = contains(':') || IPV4.matches(this)

    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    private val LEGACY_CDN_NODE = Regex(
        pattern = """(?:^|\.)([a-z]{3})-[^.]*-(\d{2})(?:\.|$)""",
        option = RegexOption.IGNORE_CASE,
    )

    private const val MIB = 1024 * 1024
    private const val MEGABYTES = "MB"
    private const val FRAMES_PER_SECOND = "fps"
    private const val MILLIS_PER_SECOND = 1_000L
    private const val MICROS_PER_MILLI = 1_000.0
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3_600

    /** Below this the sample is too short to read as a rate: half a tick can miss a whole frame. */
    private const val MIN_RATE_WINDOW_MS = 1_000L
    private const val MIN_LABELS_FOR_NODE = 3
    private const val UNKNOWN_VALUE = "—"
}
