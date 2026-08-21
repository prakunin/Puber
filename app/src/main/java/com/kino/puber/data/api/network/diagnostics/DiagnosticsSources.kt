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

/** The two API errands a diagnostics run needs, and nothing else the client can do. */
interface DiagnosticsApi {

    /** Whether one catalogue page arrives. The page itself is of no interest. */
    suspend fun loadCataloguePage(): Boolean

    /**
     * A progressive media URL served by the currently selected server, or null when the catalogue
     * offers none.
     *
     * The URL carries a token. It goes straight to the downloader and is never returned to a screen,
     * written to a log, or kept on the run.
     */
    suspend fun findProgressiveMediaUrl(): String?
}

class KinoPubDiagnosticsApi(private val api: KinoPubApiClient) : DiagnosticsApi {

    override suspend fun loadCataloguePage(): Boolean =
        api.getItems(type = PROBE_TYPE, sort = PROBE_SORT, page = 1).isSuccess

    override suspend fun findProgressiveMediaUrl(): String? {
        val itemId = api.getItems(type = PROBE_TYPE, sort = PROBE_SORT, page = 1)
            .getOrNull()
            ?.items
            ?.firstOrNull()
            ?.id
            ?: return null

        return api.getItemFiles(itemId)
            .getOrNull()
            ?.files
            ?.firstNotNullOfOrNull { file -> file.url?.http?.takeIf(String::isNotBlank) }
    }

    private companion object {
        const val PROBE_TYPE = "movie"
        const val PROBE_SORT = "-created"
    }
}
