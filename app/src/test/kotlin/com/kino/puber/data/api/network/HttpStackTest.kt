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
    fun create_usesSystemDns_withoutDoh() {
        assertSame(Dns.SYSTEM, HttpStack.create(useDoh = false).client.dns)
    }

    @Test
    fun create_keepsTheAppEventListenerAndRequestsNoDohOne_withoutDoh() {
        val requested = mutableListOf<String>()
        val factory = EventListener.Factory { _: Call -> EventListener.NONE }

        val stack = HttpStack.create(
            listeners = { label ->
                requested += label
                factory
            },
            useDoh = false,
        )

        assertEquals(listOf("app"), requested)
        assertSame(factory, stack.client.eventListenerFactory)
    }
}
