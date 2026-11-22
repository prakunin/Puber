package com.kino.puber.data.api.network

import okhttp3.DnsCache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

object DnsOverHttpsFactory {

    /**
     * [client] carries the DoH queries themselves (system DNS), so resolving the resolver never
     * recurses; [cache] holds the answers and is the one thing a domain switch has to evict.
     */
    fun create(client: OkHttpClient, cache: DnsCache): DnsOverHttps {
        return DnsOverHttps.Builder()
            .client(client)
            .cache(cache)
            .url("https://dns.google/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("8.8.4.4"),
                InetAddress.getByName("8.8.8.8"),
            )
            .post(true)
            .includeIPv6(false)
            .build()
    }
}
