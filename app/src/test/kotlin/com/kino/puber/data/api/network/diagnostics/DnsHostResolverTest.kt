package com.kino.puber.data.api.network.diagnostics

import okhttp3.Dns
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.UnknownHostException
import org.junit.jupiter.api.assertThrows

/**
 * The count is the privacy guarantee: an address cannot reach the layer above even by accident,
 * because the seam does not carry one. These two cases are what keeps it a count.
 */
internal class DnsHostResolverTest {

    @Test
    fun resolve_reportsHowManyAddressesCameBack() {
        val dns = Dns {
            // Literal addresses, so nothing here asks the machine's own resolver anything.
            listOf(InetAddress.getByName("192.0.2.1"), InetAddress.getByName("192.0.2.2"))
        }

        assertEquals(2, DnsHostResolver(dns).resolve("api.service-kp.test"))
    }

    @Test
    fun resolve_isZero_whenNothingCameBack() {
        val dns = Dns { emptyList() }

        assertEquals(0, DnsHostResolver(dns).resolve("api.service-kp.test"))
    }

    /** A resolver that throws is the caller's business to contain, not this seam's to swallow. */
    @Test
    fun resolve_letsALookupFailureThrough() {
        val dns = Dns { throw UnknownHostException("no such host") }

        assertThrows<UnknownHostException> { DnsHostResolver(dns).resolve("api.service-kp.test") }
    }
}
