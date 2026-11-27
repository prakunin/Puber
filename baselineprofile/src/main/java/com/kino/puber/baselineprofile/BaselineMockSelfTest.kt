package com.kino.puber.baselineprofile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.format.DateTimeFormatter
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Proves the mock records unknown routes and that [JourneyCheck] rejects each kind of bad run. */
@RunWith(AndroidJUnit4::class)
class BaselineMockSelfTest {

    private lateinit var backend: BaselineMockBackend
    private lateinit var client: OkHttpClient
    private lateinit var origin: String
    private lateinit var h2Origin: String
    private lateinit var leaf: HeldCertificate
    private lateinit var doh: DnsOverHttps
    private lateinit var apiClient: OkHttpClient
    private val dohProtocols = mutableListOf<Protocol>()
    private val dohDates = mutableListOf<String?>()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().context
        leaf = HeldCertificate.decode(
            context.assets.open("baseline/tls/leaf-test-only.pem").bufferedReader().use { it.readText() }
        )
        val dummyOrigin = "https://127.0.0.1:0"
        backend = BaselineMockBackend(0, 0, BaselineFixtures.from(context, dummyOrigin), leaf)
        backend.start()
        origin = "https://127.0.0.1:${backend.boundPort}"
        h2Origin = "https://127.0.0.1:${backend.boundH2Port}"
        val trust = HandshakeCertificates.Builder().addInsecureHost("127.0.0.1").build()
        client = OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
        doh = dohResolver(leaf)
        val apiTrust = HandshakeCertificates.Builder().addTrustedCertificate(leaf.certificate).build()
        apiClient = OkHttpClient.Builder()
            .sslSocketFactory(apiTrust.sslSocketFactory(), apiTrust.trustManager)
            .dns(doh)
            .build()
        backend.startIteration(BaselineScenario.Startup)
    }

    /** DNS-over-HTTPS the way the app builds it: the mock's `dns.baseline.test`, bootstrapped from 127.0.0.1. */
    private fun dohResolver(leaf: HeldCertificate): DnsOverHttps {
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(leaf.certificate).build()
        val dohClient = OkHttpClient.Builder()
            .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
            .addNetworkInterceptor { chain ->
                chain.proceed(chain.request()).also {
                    dohProtocols += it.protocol
                    dohDates += it.header("Date")
                }
            }
            .build()
        return DnsOverHttps.Builder()
            .client(dohClient)
            .url("https://dns.baseline.test:${backend.boundH2Port}/dns-query".toHttpUrl())
            .bootstrapDnsHosts(InetAddress.getByName("127.0.0.1"))
            .post(true)
            .includeIPv6(false)
            .build()
    }

    @After
    fun tearDown() {
        backend.close()
    }

    private fun get(path: String, base: String = origin): Int = call("$base$path").use { it.code }

    private fun call(url: String, httpClient: OkHttpClient = client): Response =
        httpClient.newCall(Request.Builder().url(url).build()).execute()

    private fun hitStartupRoutes() {
        assertEquals(200, get("/v1/items/hot"))
        assertEquals(200, get("/v1/watching/serials"))
        assertEquals(200, get("/v1/items/fresh"))
        assertEquals(200, get("/v1/history"))
        assertEquals(200, get("/github/repos/o/r/releases/latest", h2Origin))
        assertEquals(listOf(InetAddress.getByName("127.0.0.1")), doh.lookup("api.baseline.test"))
    }

    private fun assertCheckFails(
        output: String,
        vararg expected: String,
        check: (String, BaselineVerification) -> Unit = JourneyCheck::check,
    ) {
        try {
            check(output, backend.verify())
        } catch (error: IllegalStateException) {
            expected.forEach { assertTrue(error.message, error.message.orEmpty().contains(it)) }
            return
        }
        fail("JourneyCheck accepted a bad run")
    }

    @Test
    fun apiHost_negotiatesHttp11_dnsAndGithub_negotiateHttp2() {
        call("https://api.baseline.test:${backend.boundPort}/v1/items/hot", apiClient).use {
            assertEquals(200, it.code)
            assertEquals(Protocol.HTTP_1_1, it.protocol)
        }
        call("$h2Origin/github/repos/o/r/releases/latest").use {
            assertEquals(200, it.code)
            assertEquals(Protocol.HTTP_2, it.protocol)
            assertTrue(it.body.string().startsWith("{"))
        }
        assertEquals(listOf(InetAddress.getByName("127.0.0.1")), doh.lookup("api.baseline.test"))
        assertTrue("DoH protocols $dohProtocols", dohProtocols.isNotEmpty() && dohProtocols.all { it == Protocol.HTTP_2 })
    }

    @Test
    fun jsonRouteOnH1_isChunked_mediaRouteIsFixedLength() {
        call("$origin/v1/items/hot").use {
            assertEquals("chunked", it.header("Transfer-Encoding"))
            assertEquals(-1L, it.body.contentLength())
        }
        call("$origin/media/poster.jpg").use {
            assertEquals(200, it.code)
            assertTrue(it.header("Content-Length") != null)
        }
    }

    @Test
    fun everyResponse_carriesDateHeader() {
        val urls = listOf(
            "$origin/v1/items/hot",
            "$origin/media/poster.jpg",
            "$origin/v1/not-a-route",
            "$h2Origin/github/repos/o/r/releases/latest",
            "$h2Origin/v1/not-a-route",
        )
        urls.forEach { url ->
            call(url).use {
                val date = it.header("Date")
                assertTrue("$url has no Date", date != null)
                DateTimeFormatter.RFC_1123_DATE_TIME.parse(date)
            }
        }
        doh.lookup("api.baseline.test")
        assertTrue("DoH responses carried no Date: $dohDates", dohDates.isNotEmpty() && dohDates.all { it != null })
        dohDates.forEach { DateTimeFormatter.RFC_1123_DATE_TIME.parse(it) }
    }

    @Test
    fun routeOfOtherServer_returns404AndIsListedAsUnknown() {
        assertEquals(404, get("/v1/items/hot", h2Origin))
        assertEquals(404, get("/github/repos/o/r/releases/latest"))

        val unknown = backend.verify().unknownRequests
        assertTrue(unknown.contains(BaselineRequest("GET", "/v1/items/hot")))
        assertTrue(unknown.contains(BaselineRequest("GET", "/github/repos/o/r/releases/latest")))
    }

    @Test
    fun close_stopsBothServers() {
        backend.close()

        listOf(origin, h2Origin).forEach { base ->
            val failure = runCatching { get("/v1/items/hot", base) }.exceptionOrNull()
            assertTrue("$base failed with $failure", failure is IOException)
        }
    }

    @Test
    fun unknownRoute_returns404AndIsListedInVerify() {
        assertEquals(404, get("/v1/not-a-route"))

        val verification = backend.verify()
        assertEquals(listOf(BaselineRequest("GET", "/v1/not-a-route")), verification.unknownRequests)
    }

    @Test
    fun journeyCheck_fails_whenUnknownRouteServed() {
        hitStartupRoutes()
        get("/v1/not-a-route")

        assertCheckFails("Broadcast completed: result=-1", "/v1/not-a-route")
    }

    @Test
    fun journeyCheck_fails_whenEgressJournalNotEmpty() {
        hitStartupRoutes()

        assertCheckFails("Broadcast completed: result=2, data=\"https://example.org:443/\"", "example.org")
    }

    @Test
    fun journeyCheck_fails_whenRequiredRouteMissing() {
        assertEquals(200, get("/v1/items/hot"))

        assertCheckFails("Broadcast completed: result=-1", "/v1/watching/serials")
    }

    @Test
    fun validate_allowsSeasonsAndSubtitlesOnlyInSerialFixture() {
        val body = """{"item":{"seasons":[],"subtitles":[]}}"""
        fun fixtures(name: String, content: String) =
            BaselineFixtures(mapOf(name to content), { ByteArray(0) }, origin)

        fixtures("item_serial", body).validate()
        listOf(
            fixtures("item", body),
            fixtures("item_serial", """{"item":{"imdb":1}}"""),
            fixtures("item_serial", """{"item":{"trailer":{}}}"""),
        ).forEach { candidate ->
            val failure = runCatching { candidate.validate() }.exceptionOrNull()
            assertTrue("validate threw $failure", failure is IllegalStateException)
        }
    }

    @Test
    fun seriesRoute_servesSeriesInBrowse_movieInPlayback() {
        backend.startIteration(BaselineScenario.BrowseAndDetails)
        call("$origin/v1/items/2").use {
            val body = it.body.string()
            assertTrue(body, body.contains("\"type\":\"serial\"") && body.contains("\"seasons\""))
        }

        backend.startIteration(BaselineScenario.Playback)
        call("$origin/v1/items/2").use {
            val body = it.body.string()
            assertTrue(body, body.contains("\"id\":2") && body.contains("\"type\":\"movie\""))
            assertTrue(body, !body.contains("\"seasons\""))
        }
    }

    @Test
    fun browseScenario_requiresSeriesRoute() {
        backend.startIteration(BaselineScenario.BrowseAndDetails)
        hitStartupRoutes()
        assertEquals(200, get("/v1/items/similar"))
        assertEquals(200, get("/v1/items/1"))
        assertEquals(200, get("/v1/bookmarks/get-item-folders"))
        assertEquals(200, get("/media/poster.jpg"))

        assertEquals(listOf("GET /v1/items/2"), backend.verify().missingRequiredRoutes)
        assertCheckFails("Broadcast completed: result=-1", "GET /v1/items/2")
    }

    @Test
    fun browseScenario_requiresItemFoldersRoute() {
        backend.startIteration(BaselineScenario.BrowseAndDetails)
        hitStartupRoutes()
        assertEquals(200, get("/v1/items/similar"))
        assertEquals(200, get("/v1/items/2"))
        assertEquals(200, get("/v1/items/3"))
        assertEquals(200, get("/media/poster.jpg"))

        assertEquals(listOf("GET /v1/bookmarks/get-item-folders"), backend.verify().missingRequiredRoutes)
    }

    @Test
    fun startupScenario_requiresFreshHistoryGithubAndDnsQuery() {
        val required = listOf(
            "GET /v1/items/fresh",
            "GET /v1/history",
            "GET /github/repos/[^/]+/[^/]+/releases/latest",
            "POST /dns-query",
        )
        listOf(BaselineScenario.Startup, BaselineScenario.BrowseAndDetails, BaselineScenario.Rail).forEach { scenario ->
            backend.startIteration(scenario)

            val missing = backend.verify().missingRequiredRoutes

            assertTrue("$scenario missing $missing", missing.containsAll(required))
        }
    }

    @Test
    fun dnsQuery_resolvesBothTestHostsToLoopback_throughOkHttpDoh() {
        val loopback = listOf(InetAddress.getByName("127.0.0.1"))

        assertEquals(loopback, doh.lookup("api.baseline.test"))
        assertEquals(loopback, doh.lookup("dns.baseline.test"))
    }

    @Test
    fun dnsQuery_answersNxdomainAndListsUnknown_forOtherName() {
        val failure = runCatching { doh.lookup("other.baseline.test") }.exceptionOrNull()

        assertTrue("lookup failed with $failure", failure is UnknownHostException)
        assertTrue(backend.verify().unknownRequests.contains(BaselineRequest("DNS", "other.baseline.test")))
    }

    @Test
    fun lateCheck_fails_whenUnknownRouteServedAfterTheLastIteration() {
        hitStartupRoutes()
        get("/v1/not-a-route")

        assertCheckFails("Broadcast completed: result=-1", "/v1/not-a-route", check = JourneyCheck::checkLate)
    }

    @Test
    fun lateCheck_fails_whenEgressJournalNotEmpty() {
        assertCheckFails(
            "Broadcast completed: result=2, data=\"https://example.org:443/\"",
            "example.org",
            check = JourneyCheck::checkLate,
        )
    }

    @Test
    fun lateCheck_passes_withNoRequiredRoutesHit() {
        JourneyCheck.checkLate("Broadcast completed: result=-1", backend.verify())
    }

    @Test
    fun journeyCheck_passes_whenRequiredRoutesHitAndNothingElse() {
        hitStartupRoutes()

        JourneyCheck.check("Broadcast completed: result=-1", backend.verify())
    }
}
