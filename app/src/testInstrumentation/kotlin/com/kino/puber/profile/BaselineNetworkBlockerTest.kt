package com.kino.puber.profile

import com.kino.puber.BuildConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

class BaselineNetworkBlockerTest {

    @TempDir
    lateinit var dir: File

    private val allowedOrigins = BaselineInstrumentationEnvironment.allowedOrigins()
    private val h1 = BuildConfig.BASELINE_MOCK_PORT
    private val h2 = BuildConfig.BASELINE_MOCK_H2_PORT
    private val other = maxOf(h1, h2) + 1

    private fun blocker(journal: BaselineNetworkJournal = BaselineNetworkJournal(dir)) =
        BaselineNetworkBlocker.selector(allowedOrigins, journal) to journal

    private fun assertDeadProxy(proxies: List<Proxy>) {
        assertEquals(1, proxies.size)
        val proxy = proxies.single()
        assertEquals(Proxy.Type.HTTP, proxy.type())
        val address = proxy.address() as InetSocketAddress
        assertEquals("127.0.0.1", address.hostString)
        assertEquals(9, address.port)
    }

    @Test
    fun select_returnsDeadProxyAndJournals_whenHostIsNotLoopback() {
        val (selector, journal) = blocker()

        val proxies = selector.select(URI("https://api.example.org/v1/items"))

        assertDeadProxy(proxies)
        assertEquals(listOf("https://api.example.org:443/v1/items"), journal.snapshot())
    }

    @Test
    fun select_returnsDeadProxyAndJournals_whenLoopbackPortDiffers() {
        val (selector, journal) = blocker()

        val proxies = selector.select(URI("https://127.0.0.1:$other/v1/items"))

        assertDeadProxy(proxies)
        assertEquals(listOf("https://127.0.0.1:$other/v1/items"), journal.snapshot())
    }

    @Test
    fun select_returnsNoProxy_whenOriginIsMock() {
        val (selector, journal) = blocker()

        val onH1 = selector.select(URI("https://127.0.0.1:$h1/v1/items/hot"))
        val onH2 = selector.select(URI("https://127.0.0.1:$h2/github/repos/o/r/releases/latest"))

        assertEquals(listOf(Proxy.NO_PROXY), onH1)
        assertEquals(listOf(Proxy.NO_PROXY), onH2)
        assertEquals(emptyList<String>(), journal.snapshot())
    }

    @Test
    fun select_returnsNoProxy_whenOriginIsApiOrDnsTestHost() {
        val (selector, journal) = blocker()

        val api = selector.select(URI("https://api.baseline.test:$h1/v1/items/hot"))
        val dns = selector.select(URI("https://dns.baseline.test:$h2/dns-query"))

        assertEquals(listOf(Proxy.NO_PROXY), api)
        assertEquals(listOf(Proxy.NO_PROXY), dns)
        assertEquals(emptyList<String>(), journal.snapshot())
    }

    @Test
    fun select_returnsNoProxy_whenOriginIsPlayerFixture() {
        val (selector, journal) = blocker()

        val proxies = selector.select(URI("https://127.0.0.1:${BuildConfig.PLAYER_FIXTURE_PORT}/hls/master.m3u8"))

        assertEquals(listOf(Proxy.NO_PROXY), proxies)
        assertEquals(emptyList<String>(), journal.snapshot())
    }

    @Test
    fun select_returnsDeadProxyAndJournals_whenTestHostIsNotAllowed() {
        val (selector, journal) = blocker()

        val proxies = selector.select(URI("https://other.baseline.test:$h1/x"))

        assertDeadProxy(proxies)
        assertEquals(listOf("https://other.baseline.test:$h1/x"), journal.snapshot())
    }

    @Test
    fun select_returnsDeadProxyAndJournals_whenAllowedTestHostPortDiffers() {
        val (selector, journal) = blocker()

        val proxies = selector.select(URI("https://api.baseline.test:$other/x"))

        assertDeadProxy(proxies)
        assertEquals(listOf("https://api.baseline.test:$other/x"), journal.snapshot())
    }

    @Test
    fun select_returnsDeadProxyAndJournals_whenApiOnH2OrDnsOnH1() {
        val (selector, journal) = blocker()

        val api = selector.select(URI("https://api.baseline.test:$h2/x"))
        val dns = selector.select(URI("https://dns.baseline.test:$h1/dns-query"))

        assertDeadProxy(api)
        assertDeadProxy(dns)
        assertEquals(
            listOf("https://api.baseline.test:$h2/x", "https://dns.baseline.test:$h1/dns-query"),
            journal.snapshot(),
        )
    }

    @Test
    fun connectFailed_journalsUri() {
        val (selector, journal) = blocker()

        selector.connectFailed(URI("https://tracker.example.org/x"), null, IOException("refused"))

        assertEquals(listOf("https://tracker.example.org:443/x"), journal.snapshot())
    }

    @Test
    fun journal_keepsLines_whenNewBlockerOpensSameDir() {
        val (selector, _) = blocker()
        selector.select(URI("http://example.org/a"))

        val reopened = BaselineNetworkJournal(dir)

        assertEquals(listOf("http://example.org:80/a"), reopened.snapshot())
    }

    @Test
    fun clear_emptiesJournal() {
        val (selector, journal) = blocker()
        selector.select(URI("http://example.org/a"))
        assertEquals(listOf("http://example.org:80/a"), journal.snapshot())

        journal.clear()

        assertEquals(emptyList<String>(), journal.snapshot())
    }
}
