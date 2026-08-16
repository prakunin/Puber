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

    /**
     * Short name of the CDN node serving the stream.
     *
     * The delivery hosts carry the node code in their first DNS label (`msk01.…`), so that label —
     * uppercased — is the whole answer. A two-label host has no node part to pick out and an IP
     * literal has no labels at all, so both are shown verbatim rather than mangled.
     */
    fun streamSource(streamUrl: String?): String {
        val host = host(streamUrl) ?: return UNKNOWN_VALUE
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

    private const val MIB = 1024 * 1024
    private const val MEGABYTES = "MB"
    private const val MIN_LABELS_FOR_NODE = 3
    private const val UNKNOWN_VALUE = "—"
}
