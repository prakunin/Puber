package com.kino.puber.baselineprofile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
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
    private lateinit var doh: DnsOverHttps

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val leaf = HeldCertificate.decode(
            context.assets.open("baseline/tls/leaf-test-only.pem").bufferedReader().use { it.readText() }
        )
        val dummyOrigin = "https://127.0.0.1:0"
        backend = BaselineMockBackend(0, BaselineFixtures.from(context, dummyOrigin), leaf)
        backend.start()
        origin = "https://127.0.0.1:${backend.boundPort}"
        val trust = HandshakeCertificates.Builder().addInsecureHost("127.0.0.1").build()
        client = OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
        doh = dohResolver(leaf)
        backend.startIteration(BaselineScenario.Startup)
    }

    /** DNS-over-HTTPS the way the app builds it: the mock's `dns.baseline.test`, bootstrapped from 127.0.0.1. */
    private fun dohResolver(leaf: HeldCertificate): DnsOverHttps {
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(leaf.certificate).build()
        val dohClient = OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
        return DnsOverHttps.Builder()
            .client(dohClient)
            .url("https://dns.baseline.test:${backend.boundPort}/dns-query".toHttpUrl())
            .bootstrapDnsHosts(InetAddress.getByName("127.0.0.1"))
            .post(true)
            .includeIPv6(false)
            .build()
    }

    @After
    fun tearDown() {
        backend.close()
    }

    private fun get(path: String): Int =
        client.newCall(Request.Builder().url("$origin$path").build()).execute().use { it.code }

    private fun hitStartupRoutes() {
        assertEquals(200, get("/v1/items/hot"))
        assertEquals(200, get("/v1/watching/serials"))
        assertEquals(200, get("/v1/items/fresh"))
        assertEquals(200, get("/v1/history"))
        assertEquals(200, get("/github/repos/o/r/releases/latest"))
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
