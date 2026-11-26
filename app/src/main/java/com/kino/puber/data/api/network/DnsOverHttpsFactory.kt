package com.kino.puber.data.api.network

import okhttp3.DnsCache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/** A DNS-over-HTTPS endpoint and the plain addresses its own host name is bootstrapped from. */
data class DohResolver(val url: String, val bootstrapHosts: List<String>) {
    companion object {
        val GOOGLE = DohResolver("https://dns.google/dns-query", listOf("8.8.4.4", "8.8.8.8"))
    }
}

object DnsOverHttpsFactory {

    /**
     * [client] carries the DoH queries themselves (system DNS), so resolving the resolver never
     * recurses; [cache] holds the answers and is the one thing a domain switch has to evict.
     */
    fun create(
        client: OkHttpClient,
        cache: DnsCache,
        resolver: DohResolver = DohResolver.GOOGLE,
    ): DnsOverHttps {
        return DnsOverHttps.Builder()
            .client(client)
            .cache(cache)
            .url(resolver.url.toHttpUrl())
            .bootstrapDnsHosts(resolver.bootstrapHosts.map(InetAddress::getByName))
            .post(true)
            .includeIPv6(false)
            .build()
    }
}
