package com.kino.puber.data.api.network

import okhttp3.DnsCache
import okhttp3.Dispatcher
import okhttp3.EventListener
import okhttp3.OkHttpClient
import kotlin.time.Duration

/**
 * The one HTTP stack of the app: every caller derives from [client], so they share a connection
 * pool and a resolver. The resolver is DNS-over-HTTPS, whose answers live in [dnsCache], unless
 * the resolver is null (a pinned endpoint without one), where it is the system one and [dnsCache]
 * stays unused.
 */
class HttpStack(val client: OkHttpClient, val dnsCache: DnsCache) {

    companion object {

        /**
         * Library defaults except failures: a failed lookup is never cached, so a resolver blip
         * cannot outlive the retry, the reachability report or the endpoint probe that follow it.
         */
        internal fun createDnsCache(): DnsCache = DnsCache(failureTimeToLive = Duration.ZERO)

        /**
         * [listeners] maps a label (`doh`, `app`) to the event listener that counts that client.
         * [resolver] null leaves the app client on the system resolver and builds no DoH client.
         */
        fun create(
            listeners: ((String) -> EventListener.Factory)? = null,
            resolver: DohResolver? = DohResolver.GOOGLE,
        ): HttpStack {
            val base = OkHttpClient.Builder().build()
            val dnsCache = createDnsCache()
            if (resolver == null) {
                val client = base.newBuilder()
                    .apply { listeners?.invoke("app")?.let(::eventListenerFactory) }
                    .build()
                return HttpStack(client, dnsCache)
            }
            // Own dispatcher: DoH queries never queue behind calls blocked in a lookup.
            val dohClient = base.newBuilder()
                .dispatcher(Dispatcher())
                .apply { listeners?.invoke("doh")?.let(::eventListenerFactory) }
                .build()
            val doh = DnsOverHttpsFactory.create(dohClient, dnsCache, resolver)
            val client = base.newBuilder()
                .dns(doh)
                .apply { listeners?.invoke("app")?.let(::eventListenerFactory) }
                .build()
            return HttpStack(client, dnsCache)
        }
    }
}
