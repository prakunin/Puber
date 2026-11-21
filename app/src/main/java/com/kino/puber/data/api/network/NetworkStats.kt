package com.kino.puber.data.api.network

import com.kino.puber.BuildConfig
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Response
import timber.log.Timber
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Debug-only network event counters, one set per client label. Every event logs one line under the
 * `NetStats` tag carrying every label's cumulative counts, so the last line of a session holds
 * its totals.
 */
object NetworkStats {

    private const val TAG = "NetStats"

    private val counters = ConcurrentHashMap<String, Counters>()

    fun factory(label: String): EventListener.Factory {
        val listener = Listener(label, counters.getOrPut(label) { Counters() })
        return EventListener.Factory { listener }
    }

    fun factoryOrNull(label: String): EventListener.Factory? =
        if (BuildConfig.DEBUG) factory(label) else null

    private fun snapshot(): String = counters.entries
        .sortedBy { it.key }
        .joinToString(" | ") { (label, c) ->
            "$label[call=${c.callStart.get()} dns=${c.dnsStart.get()} connect=${c.connectStart.get()} " +
                "hit=${c.cacheHit.get()} cond=${c.cacheConditionalHit.get()} miss=${c.cacheMiss.get()}]"
        }

    private fun log(label: String, event: String) {
        Timber.tag(TAG).d("$label.$event ${snapshot()}")
    }

    private class Counters {
        val callStart = AtomicInteger()
        val dnsStart = AtomicInteger()
        val connectStart = AtomicInteger()
        val cacheHit = AtomicInteger()
        val cacheConditionalHit = AtomicInteger()
        val cacheMiss = AtomicInteger()
    }

    private class Listener(private val label: String, private val c: Counters) : EventListener() {
        override fun callStart(call: Call) = count(c.callStart, "callStart")

        override fun dnsStart(call: Call, domainName: String) = count(c.dnsStart, "dnsStart")

        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) =
            count(c.connectStart, "connectStart")

        override fun cacheHit(call: Call, response: Response) = count(c.cacheHit, "cacheHit")

        override fun cacheConditionalHit(call: Call, cachedResponse: Response) =
            count(c.cacheConditionalHit, "cacheConditionalHit")

        override fun cacheMiss(call: Call) = count(c.cacheMiss, "cacheMiss")

        private fun count(counter: AtomicInteger, event: String) {
            counter.incrementAndGet()
            log(label, event)
        }
    }
}
