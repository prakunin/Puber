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
    private const val MIN_LABELS_FOR_NODE = 3
    private const val UNKNOWN_VALUE = "—"
}
