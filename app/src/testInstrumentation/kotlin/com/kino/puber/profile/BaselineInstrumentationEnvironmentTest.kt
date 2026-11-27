package com.kino.puber.profile

import com.kino.puber.BuildConfig
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.network.DohResolver
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BaselineInstrumentationEnvironmentTest {

    @TempDir
    lateinit var dir: File

    private var savedSelector: ProxySelector? = null

    @BeforeEach
    fun saveSelector() {
        savedSelector = ProxySelector.getDefault()
    }

    @AfterEach
    fun restore() {
        ProxySelector.setDefault(savedSelector)
        KinoPubConfig.clearPinnedEndpoint()
    }

    @Test
    fun configure_installsTheBlockerAsDefaultProxySelector() {
        BaselineInstrumentationEnvironment.configure(dir)

        val proxies = ProxySelector.getDefault().select(URI("https://example.org/"))

        val address = proxies.single().address() as InetSocketAddress
        assertEquals(Proxy.Type.HTTP, proxies.single().type())
        assertEquals("127.0.0.1", address.hostString)
        assertEquals(9, address.port)
        assertEquals(listOf("https://example.org:443/"), BaselineNetworkJournal(dir).snapshot())
    }

    @Test
    fun configure_pinsApiToH1PortAndDohToH2Port() {
        val h1 = BuildConfig.BASELINE_MOCK_PORT
        val h2 = BuildConfig.BASELINE_MOCK_H2_PORT
        BaselineInstrumentationEnvironment.configure(dir)

        assertTrue(KinoPubConfig.IS_PINNED_ENDPOINT)
        assertEquals("https://api.baseline.test:$h1/v1/", KinoPubConfig.MAIN_API_BASE_URL)
        assertEquals("https://api.baseline.test:$h1/", KinoPubConfig.EXTRA_API_BASE_URL)
        assertEquals(
            DohResolver("https://dns.baseline.test:$h2/dns-query", listOf("127.0.0.1")),
            KinoPubConfig.DNS_RESOLVER,
        )
    }

    @Test
    fun allowedOrigins_listsEachServedOriginOnItsServerPort() {
        val h1 = BuildConfig.BASELINE_MOCK_PORT
        val h2 = BuildConfig.BASELINE_MOCK_H2_PORT
        assertNotEquals(h1, h2)

        assertEquals(
            setOf(
                "https://127.0.0.1:$h1",
                "https://api.baseline.test:$h1",
                "https://127.0.0.1:$h2",
                "https://dns.baseline.test:$h2",
            ),
            BaselineInstrumentationEnvironment.allowedOrigins(),
        )
        assertEquals("https://127.0.0.1:$h2", BaselineInstrumentationEnvironment.mockH2Origin())
    }
}
