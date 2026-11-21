package com.kino.puber.domain.interactor.api

import com.kino.puber.data.api.config.ApiEndpointPreset
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.network.EndpointProbe
import com.kino.puber.data.api.network.EndpointReachability
import com.kino.puber.data.cache.ContentCacheRepository
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.domain.interactor.prefetch.DetailsPrefetcher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class ApiDomainInteractorTest {

    private val preferences = mockk<ICryptoPreferenceRepository>(relaxed = true)
    private val contentCache = mockk<ContentCacheRepository>(relaxed = true)
    private val detailsPrefetcher = mockk<DetailsPrefetcher>(relaxed = true)
    private var reachableDomains = emptySet<String>()
    private val probeCalls = mutableListOf<String>()
    private var now = 1_000_000L
    private val reachability = EndpointReachability(clock = { now })
    private val interactor = ApiDomainInteractor(
        preferences = preferences,
        contentCache = contentCache,
        probe = EndpointProbe { endpoint ->
            probeCalls += endpoint.domain
            endpoint.domain in reachableDomains
        },
        reachability = reachability,
        detailsPrefetcher = detailsPrefetcher,
    )

    private var domainOverride: String? = null

    @BeforeEach
    fun setUp() {
        // DEFAULT_API_DOMAIN decodes a Base64 constant via android.util.Base64, which isn't mocked
        // on the plain JVM unit test classpath. Stand the object in with a fake override so this
        // test can exercise the domain-switch flow without touching that dependency.
        mockkObject(KinoPubConfig)
        domainOverride = null
        every { KinoPubConfig.DEFAULT_API_DOMAIN } returns "service-kp.test"
        every { KinoPubConfig.CUSTOM_API_DOMAIN } answers { domainOverride }
        every { KinoPubConfig.setDomainOverride(any()) } answers { domainOverride = firstArg() }
        every { KinoPubConfig.CURRENT_API_DOMAIN } answers { domainOverride ?: "service-kp.test" }
        every { KinoPubConfig.CURRENT_ENDPOINT } answers { endpointFor(domainOverride ?: "service-kp.test") }
        every { KinoPubConfig.BUILT_IN_ENDPOINTS } returns listOf(
            endpointFor("service-kp.test"),
            endpointFor("api.alador.test"),
        )
        reachableDomains = emptySet()
        probeCalls.clear()
    }

    private fun endpointFor(domain: String) = ApiEndpointPreset(
        domain = domain,
        apiHost = domain,
        mainBaseUrl = "https://$domain/v1/",
        oauthBaseUrl = "https://$domain/oauth2/",
        extraBaseUrl = "https://$domain/",
    )

    @AfterEach
    fun tearDown() {
        unmockkObject(KinoPubConfig)
    }

    /**
     * By the time clearDomainSensitiveCaches runs, the domain switch has already taken effect
     * (preferences persisted, KinoPubConfig repointed). A cache that fails to clear is stale data,
     * not a reason to strand the caller mid-switch with the dialog still open and the state never
     * updated — see Task 5 review finding 2.
     */
    @Test
    fun resetToDefault_completesEvenWhenTheContentCacheFailsToClear() = runTest {
        coEvery { contentCache.clear() } throws IllegalStateException("disk full")

        val state = interactor.resetToDefault()

        assertEquals("service-kp.test", state.domain)
        assertNull(state.customDomain)
        verify(exactly = 1) { preferences.saveApiDomain(null) }
        coVerify(exactly = 1) { contentCache.clear() }
    }

    @Test
    fun saveCustomDomain_completesEvenWhenTheContentCacheFailsToClear() = runTest {
        coEvery { contentCache.clear() } throws IllegalStateException("disk full")

        val result = interactor.saveCustomDomain("api.custom.example")

        val success = result as? ApiDomainUpdateResult.Success
            ?: error("Expected Success, got $result")
        assertEquals("api.custom.example", success.state.domain)
        assertEquals("api.custom.example", success.state.customDomain)
        verify(exactly = 1) { preferences.saveApiDomain("api.custom.example") }
        coVerify(exactly = 1) { contentCache.clear() }
    }

    /**
     * The home screen auto-resolves on every load, including the one behind each ON_RESUME, and a
     * probe is a full catalogue GET whose body is read and parsed. A domain that answered a moment
     * ago is not worth asking again before anything has shown on screen.
     */
    @Test
    fun autoResolve_doesNotReProbeADomainThatAnsweredWithinTheCacheWindow() = runTest {
        reachableDomains = setOf("service-kp.test")

        interactor.autoResolveWorkingDomain()
        interactor.autoResolveWorkingDomain()

        assertEquals(listOf("service-kp.test"), probeCalls)
    }

    /** Like Dispatchers.Main.immediate: resumes inline on its own thread, posts to it from any other. */
    private class ImmediateThreadDispatcher : CoroutineDispatcher() {
        private val executor = Executors.newSingleThreadExecutor()
        private val thread = executor.submit<Thread> { Thread.currentThread() }.get()

        override fun isDispatchNeeded(context: CoroutineContext) = Thread.currentThread() !== thread

        override fun dispatch(context: CoroutineContext, block: Runnable) = executor.execute(block)

        /** Returns once every task posted so far has run, that is, once the coroutines on it have suspended. */
        fun idle() {
            executor.submit {}.get()
        }

        fun shutdown() {
            executor.shutdownNow()
        }
    }

    private fun interactorWith(probe: EndpointProbe) = ApiDomainInteractor(
        preferences = preferences,
        contentCache = contentCache,
        probe = probe,
        reachability = reachability,
        detailsPrefetcher = detailsPrefetcher,
    )

    /**
     * The startup warm-up and the Home screen both resolve the domain within the same moment; the second
     * caller must join the walk in flight and answer with its result, not probe again. The first caller
     * is held inside its probe until the second has joined, so no timing is involved.
     */
    @Test
    fun autoResolve_concurrentCallersShareOneProbe() = runTest {
        val concurrentProbeCalls = Collections.synchronizedList(mutableListOf<String>())
        val probing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val sharing = interactorWith(
            EndpointProbe { endpoint ->
                concurrentProbeCalls += endpoint.domain
                probing.countDown()
                release.await()
                endpoint.domain == "service-kp.test"
            },
        )

        val first = async(start = CoroutineStart.UNDISPATCHED) { sharing.autoResolveWorkingDomain() }
        probing.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { sharing.autoResolveWorkingDomain() }
        release.countDown()
        val results = listOf(first, second).awaitAll()

        assertEquals(listOf("service-kp.test"), concurrentProbeCalls.toList())
        results.forEach { result ->
            val success = result as? ApiDomainAutoResolveResult.Success ?: error("Expected Success, got $result")
            assertFalse(success.changed)
        }
    }

    /** A failed walk is shared too: nothing is recorded for an unreachable domain, so a late caller would repeat it. */
    @Test
    fun autoResolve_concurrentCallersShareAWalkThatFoundNothing() = runTest {
        val concurrentProbeCalls = Collections.synchronizedList(mutableListOf<String>())
        val probing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val sharing = interactorWith(
            EndpointProbe { endpoint ->
                concurrentProbeCalls += endpoint.domain
                probing.countDown()
                release.await()
                false
            },
        )

        val first = async(start = CoroutineStart.UNDISPATCHED) { sharing.autoResolveWorkingDomain() }
        probing.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { sharing.autoResolveWorkingDomain() }
        release.countDown()
        val results = listOf(first, second).awaitAll()

        assertEquals(listOf("service-kp.test", "api.alador.test"), concurrentProbeCalls.toList())
        results.forEach { assertEquals(ApiDomainAutoResolveResult.NotFound, it) }
    }

    @Test
    fun autoResolve_walksAgainOnceTheSharedWalkHasFinished() = runTest {
        interactor.autoResolveWorkingDomain()
        interactor.autoResolveWorkingDomain()

        assertEquals(
            listOf("service-kp.test", "api.alador.test", "service-kp.test", "api.alador.test"),
            probeCalls,
        )
    }

    /**
     * Both callers run on one immediate dispatcher, as two Home loads do on Dispatchers.Main.immediate:
     * the owner's failure resumes the joiner inline, so it must find the slot already empty and walk
     * itself instead of re-joining the dead walk. A third call afterwards walks again.
     */
    @Test
    fun autoResolve_joinerWalksItselfWhenTheOwnerFailsAndAThirdCallWalksAgain() = runBlocking {
        val main = ImmediateThreadDispatcher()
        val probing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val probeCount = AtomicInteger()
        val retrying = interactorWith(
            EndpointProbe {
                if (probeCount.incrementAndGet() == 1) {
                    probing.countDown()
                    release.await()
                    error("owner probe failed")
                }
                false
            },
        )

        try {
            withTimeout(10.seconds) {
                val owner = async(main) { runCatching { retrying.autoResolveWorkingDomain() } }
                probing.await()
                val joiner = async(main) { retrying.autoResolveWorkingDomain() }
                main.idle()
                release.countDown()

                assertTrue(owner.await().isFailure)
                assertEquals(ApiDomainAutoResolveResult.NotFound, joiner.await())
                val probesAfterJoiner = probeCount.get()
                assertEquals(
                    ApiDomainAutoResolveResult.NotFound,
                    async(main) { retrying.autoResolveWorkingDomain() }.await(),
                )
                assertTrue(probeCount.get() > probesAfterJoiner)
            }
        } finally {
            main.shutdown()
        }
    }

    /** The walk can take seconds; a domain the user sets meanwhile is newer than what the walk found. */
    @Test
    fun autoResolve_doesNotOverwriteADomainSetWhileItWalks() = runTest {
        lateinit var walking: ApiDomainInteractor
        walking = interactorWith(
            EndpointProbe { endpoint ->
                if (endpoint.domain == "service-kp.test") {
                    runBlocking { walking.saveCustomDomain("api.custom.example") }
                }
                endpoint.domain == "api.alador.test"
            },
        )

        val result = walking.autoResolveWorkingDomain()

        val success = result as? ApiDomainAutoResolveResult.Success ?: error("Expected Success, got $result")
        assertEquals("api.custom.example", success.state.domain)
        assertFalse(success.changed)
        verify(exactly = 0) { preferences.saveApiDomain("api.alador.test") }
        assertEquals("api.custom.example", domainOverride)
    }

    @Test
    fun autoResolve_stillReportsSuccessWhenItAnswersFromTheCache() = runTest {
        reachableDomains = setOf("service-kp.test")
        interactor.autoResolveWorkingDomain()

        val result = interactor.autoResolveWorkingDomain()

        val success = result as? ApiDomainAutoResolveResult.Success
            ?: error("Expected Success, got $result")
        assertEquals("service-kp.test", success.state.domain)
        assertFalse(success.changed)
    }

    /**
     * The window is a tolerance for staleness, not a promise the domain is up. Once it lapses the
     * failover has to be able to run again, or a domain that has since been blocked never recovers.
     */
    @Test
    fun autoResolve_probesAgainOnceTheCacheWindowHasPassed() = runTest {
        reachableDomains = setOf("service-kp.test")
        interactor.autoResolveWorkingDomain()

        now += 16.minutes.inWholeMilliseconds
        interactor.autoResolveWorkingDomain()

        assertEquals(listOf("service-kp.test", "service-kp.test"), probeCalls)
    }

    /**
     * The window saves probes on the assumption that a domain which just answered still answers.
     * A caller whose every request has since failed against it knows better, and saying so has to
     * put the failover back within reach — otherwise a mirror that dies right after a probe holds
     * the app for the rest of the window.
     */
    @Test
    fun autoResolve_probesAgainAfterTheCurrentDomainIsReportedUnreachable() = runTest {
        reachableDomains = setOf("service-kp.test")
        interactor.autoResolveWorkingDomain()

        reachability.markUnreachable(KinoPubConfig.CURRENT_API_DOMAIN)
        interactor.autoResolveWorkingDomain()

        assertEquals(listOf("service-kp.test", "service-kp.test"), probeCalls)
    }

    /** Reporting a dead domain has to reach a live mirror, not merely cost an extra probe. */
    @Test
    fun autoResolve_failsOverToAMirrorAfterTheCurrentDomainIsReportedUnreachable() = runTest {
        reachableDomains = setOf("service-kp.test")
        interactor.autoResolveWorkingDomain()

        reachableDomains = setOf("api.alador.test")
        reachability.markUnreachable(KinoPubConfig.CURRENT_API_DOMAIN)
        val result = interactor.autoResolveWorkingDomain()

        val success = result as? ApiDomainAutoResolveResult.Success
            ?: error("Expected Success, got $result")
        assertEquals("api.alador.test", success.state.domain)
        assertTrue(success.changed)
    }

    /**
     * The report names no domain, so it can only mean the one in use when it was made. A switch
     * that happened in between leaves a verdict belonging to the new domain, and a report about the
     * old one must not take it down.
     */
    @Test
    fun autoResolve_keepsTheCacheWhenTheReportedDomainIsNoLongerTheCurrentOne() = runTest {
        reachableDomains = setOf("service-kp.test", "api.custom.example")
        interactor.saveCustomDomain("api.custom.example")
        interactor.autoResolveWorkingDomain()
        val callsBefore = probeCalls.size

        domainOverride = "service-kp.test"
        reachability.markUnreachable(KinoPubConfig.CURRENT_API_DOMAIN)
        domainOverride = "api.custom.example"
        interactor.autoResolveWorkingDomain()

        assertEquals(callsBefore, probeCalls.size)
    }

    /**
     * The cache answers for one domain only. A switch made anywhere — this interactor's own dialog,
     * the device settings screen — must not be able to inherit the previous domain's verdict.
     */
    @Test
    fun autoResolve_doesNotAnswerFromTheCacheAfterTheDomainChanged() = runTest {
        reachableDomains = setOf("service-kp.test", "api.custom.example")
        interactor.autoResolveWorkingDomain()

        interactor.saveCustomDomain("api.custom.example")
        interactor.autoResolveWorkingDomain()

        assertEquals(listOf("service-kp.test", "api.custom.example"), probeCalls)
    }

    /**
     * Payloads describe one domain's catalogue. Keeping them across a switch shows the previous
     * domain's content under the new one, so the whole store must be dropped, not just the
     * repository-level namespaces.
     */
    @Test
    fun resetToDefault_dropsEveryCachedPayload() = runTest {
        interactor.resetToDefault()

        coVerify(exactly = 1) { contentCache.clear() }
    }

    /**
     * The prefetch index is independent from the content cache and must still be cleared if the
     * persistent cache wipe fails.
     */
    @Test
    fun resetToDefault_stillClearsPrefetchWhenTheContentCacheFailsToClear() = runTest {
        coEvery { contentCache.clear() } throws IllegalStateException("disk full")

        interactor.resetToDefault()

        verify(exactly = 1) { detailsPrefetcher.invalidate() }
    }

    /**
     * The prefetcher's record of what is warm describes the caches this switch just emptied. Left
     * standing it suppresses fetches for ids the new domain has never cached, and the details
     * screen goes back to showing a spinner for them.
     */
    @Test
    fun resetToDefault_makesThePrefetcherForgetWhatItWarmed() = runTest {
        interactor.resetToDefault()

        verify(exactly = 1) { detailsPrefetcher.invalidate() }
    }

    @Test
    fun resetToDefault_stillClearsThePrefetcherWhenTheContentCacheFailsToClear() = runTest {
        coEvery { contentCache.clear() } throws IllegalStateException("disk full")

        interactor.resetToDefault()

        verify(exactly = 1) { detailsPrefetcher.invalidate() }
    }

    @Test
    fun switchToBuiltInDomain_appliesTheMirror_whenItIsBuiltIn() = runTest {
        val state = interactor.switchToBuiltInDomain("api.alador.test")

        assertEquals("api.alador.test", state?.domain)
        assertEquals("api.alador.test", state?.customDomain)
        verify(exactly = 1) { preferences.saveApiDomain("api.alador.test") }
    }

    /**
     * The default domain is stored as "no override" rather than as itself, so returning to it has
     * to clear the preference — otherwise a later change to the built-in default would be pinned
     * shut by a value the user never chose.
     */
    @Test
    fun switchToBuiltInDomain_clearsTheOverride_whenTheTargetIsTheDefault() = runTest {
        interactor.switchToBuiltInDomain("api.alador.test")

        val state = interactor.switchToBuiltInDomain("service-kp.test")

        assertEquals("service-kp.test", state?.domain)
        assertNull(state?.customDomain)
        verify(exactly = 1) { preferences.saveApiDomain(null) }
    }

    /** A domain that is not one of ours is not something a diagnostic may switch to. */
    @Test
    fun switchToBuiltInDomain_changesNothing_whenTheDomainIsUnknown() = runTest {
        val state = interactor.switchToBuiltInDomain("evil.test")

        assertNull(state)
        // Any save at all, not just one carrying the rejected string: falling through to a default
        // preset would be the mistake worth catching, and it would never save "evil.test".
        verify(exactly = 0) { preferences.saveApiDomain(any()) }
    }
}
