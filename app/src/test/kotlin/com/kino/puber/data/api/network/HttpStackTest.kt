package com.kino.puber.data.api.network

import okhttp3.Call
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.dnsoverhttps.DnsOverHttps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class HttpStackTest {

    @Test
    fun create_resolvesThroughDoh_byDefault() {
        assertTrue(HttpStack.create().client.dns is DnsOverHttps)
    }

    @Test
    fun create_usesSystemDns_whenResolverIsNull() {
        assertSame(Dns.SYSTEM, HttpStack.create(resolver = null).client.dns)
    }

    @Test
    fun create_keepsTheAppEventListenerAndRequestsNoDohOne_whenResolverIsNull() {
        val requested = mutableListOf<String>()
        val factory = EventListener.Factory { _: Call -> EventListener.NONE }

        val stack = HttpStack.create(
            listeners = { label ->
                requested += label
                factory
            },
            resolver = null,
        )

        assertEquals(listOf("app"), requested)
        assertSame(factory, stack.client.eventListenerFactory)
    }
}
