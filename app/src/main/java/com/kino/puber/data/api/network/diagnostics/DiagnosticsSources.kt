package com.kino.puber.data.api.network.diagnostics

import com.kino.puber.data.api.KinoPubApiClient
import okhttp3.Dns

/**
 * Resolves a host and says only how many addresses came back.
 *
 * A count rather than the addresses themselves, because the layer above must not be able to show or
 * store an IP even by accident. Making that impossible is cheaper than remembering it.
 */
fun interface HostResolver {
    fun resolve(host: String): Int
}

/**
 * Resolves through the client's own DNS, which is the DNS-over-HTTPS resolver every real request
 * uses. A resolver built for the occasion would answer a question nobody asked.
 */
class DnsHostResolver(private val dns: Dns) : HostResolver {
    override fun resolve(host: String): Int = dns.lookup(host).size
}

/**
 * What the catalogue had to offer the media step.
 *
 * "No URL" needed splitting in two, because the two halves are different news for the user. A
 * catalogue that answered with files none of which carry a progressive URL means the account's
 * streaming type is HLS — a setting the user owns and can change. A catalogue that answered with
 * nothing at all means there was no item to ask about.
 */
sealed interface MediaProbeTarget {

    /**
     * A progressive URL to measure.
     *
     * The URL carries a token. It goes straight to the downloader and is never returned to a
     * screen, written to a log, or kept on the run.
     */
    data class Progressive(val url: String) : MediaProbeTarget

    /** Files came back, and not one of them offered a progressive URL. */
    data object NoProgressiveStream : MediaProbeTarget

    /** The catalogue offered no item, or did not answer at all. */
    data object Unavailable : MediaProbeTarget
}

/** The two API errands a diagnostics run needs, and nothing else the client can do. */
interface DiagnosticsApi {

    /** Whether one catalogue page arrives. The page itself is of no interest. */
    suspend fun loadCataloguePage(): Boolean

    /** What the currently selected server has for the media step to measure, if anything. */
    suspend fun findMediaProbeTarget(): MediaProbeTarget
}

class KinoPubDiagnosticsApi(private val api: KinoPubApiClient) : DiagnosticsApi {

    override suspend fun loadCataloguePage(): Boolean =
        api.getItems(type = PROBE_TYPE, sort = PROBE_SORT, page = 1).isSuccess

    override suspend fun findMediaProbeTarget(): MediaProbeTarget {
        val itemId = api.getItems(type = PROBE_TYPE, sort = PROBE_SORT, page = 1)
            .getOrNull()
            ?.items
            ?.firstOrNull()
            ?.id
            ?: return MediaProbeTarget.Unavailable

        val files = api.getItemFiles(itemId).getOrNull()?.files.orEmpty()
        if (files.isEmpty()) return MediaProbeTarget.Unavailable

        val url = files.firstNotNullOfOrNull { file -> file.url?.http?.takeIf(String::isNotBlank) }
        return if (url == null) {
            MediaProbeTarget.NoProgressiveStream
        } else {
            MediaProbeTarget.Progressive(url)
        }
    }

    private companion object {
        const val PROBE_TYPE = "movie"
        const val PROBE_SORT = "-created"
    }
}
