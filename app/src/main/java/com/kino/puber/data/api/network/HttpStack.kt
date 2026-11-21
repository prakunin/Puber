package com.kino.puber.data.api.network

import okhttp3.DnsCache
import okhttp3.Dispatcher
import okhttp3.EventListener
import okhttp3.OkHttpClient
import kotlin.time.Duration

/**
 * The one HTTP stack of the app: every caller derives from [client], so they share a connection
 * pool and a DNS-over-HTTPS resolver whose answers live in [dnsCache].
 */
class HttpStack(val client: OkHttpClient, val dnsCache: DnsCache) {

    companion object {

        /**
         * Library defaults except failures: a failed lookup is never cached, so a resolver blip
         * cannot outlive the retry, the reachability report or the endpoint probe that follow it.
         */
        internal fun createDnsCache(): DnsCache = DnsCache(failureTimeToLive = Duration.ZERO)

        /** [listeners] maps a label (`doh`, `app`) to the event listener that counts that client. */
        fun create(listeners: ((String) -> EventListener.Factory)? = null): HttpStack {
            val base = OkHttpClient.Builder().build()
            val dnsCache = createDnsCache()
            // Own dispatcher: DoH queries never queue behind calls blocked in a lookup.
            val dohClient = base.newBuilder()
                .dispatcher(Dispatcher())
                .apply { listeners?.invoke("doh")?.let(::eventListenerFactory) }
                .build()
            val doh = DnsOverHttpsFactory.create(dohClient, dnsCache)
            val client = base.newBuilder()
                .dns(doh)
                .apply { listeners?.invoke("app")?.let(::eventListenerFactory) }
                .build()
            return HttpStack(client, dnsCache)
        }
    }
}
