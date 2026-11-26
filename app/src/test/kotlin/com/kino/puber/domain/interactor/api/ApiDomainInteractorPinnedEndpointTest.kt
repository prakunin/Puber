package com.kino.puber.domain.interactor.api

import android.util.Base64
import com.kino.puber.data.api.config.ApiEndpointPreset
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.network.EndpointProbe
import com.kino.puber.data.api.network.EndpointReachability
import com.kino.puber.data.cache.ContentCacheRepository
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.domain.interactor.prefetch.DetailsPrefetcher
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * With an endpoint pinned, the six mutators other than `autoResolveWorkingDomain` must leave it
 * alone: no probe, no preference write, no cache clear. The walk probes the pin and nothing else.
 * The probe answers [probeAnswer] for every endpoint (true by default), so a mutator missing its
 * guard would switch away from the pin.
 */
internal class ApiDomainInteractorPinnedEndpointTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun mockBase64() {
            mockkStatic(Base64::class)
            every { Base64.decode(any<ByteArray>(), any()) } returns "example.test".toByteArray()
        }

        @JvmStatic
        @AfterAll
        fun unmockBase64() {
            unmockkStatic(Base64::class)
        }
    }

    private val pin = ApiEndpointPreset(
        domain = "127.0.0.1",
        apiHost = "127.0.0.1:8080",
        mainBaseUrl = "http://127.0.0.1:8080/v1/",
        oauthBaseUrl = "http://127.0.0.1:8080/oauth2/",
        extraBaseUrl = "http://127.0.0.1:8080/",
    )

    private val preferences = mockk<ICryptoPreferenceRepository>(relaxed = true)
    private val contentCache = mockk<ContentCacheRepository>(relaxed = true)
    private val detailsPrefetcher = mockk<DetailsPrefetcher>(relaxed = true)
    private val probeCalls = mutableListOf<String>()
    private var evictCalls = 0
    private var probeAnswer = true
    private val interactor = ApiDomainInteractor(
        preferences = preferences,
        contentCache = contentCache,
        probe = EndpointProbe { endpoint ->
            probeCalls += endpoint.domain
            probeAnswer
        },
        reachability = EndpointReachability(),
        detailsPrefetcher = detailsPrefetcher,
        evictDnsCache = { evictCalls++ },
    )

    @BeforeEach
    fun setUp() {
        every { preferences.getApiDomain() } returns "mirror.example"
        KinoPubConfig.setDomainOverride(null)
        KinoPubConfig.setPinnedEndpoint(pin)
    }

    @AfterEach
    fun tearDown() {
        KinoPubConfig.clearPinnedEndpoint()
        KinoPubConfig.setDomainOverride(null)
    }

    private fun assertNothingTouched() {
        assertEquals(pin, KinoPubConfig.CURRENT_ENDPOINT)
        assertEquals(pin.mainBaseUrl, KinoPubConfig.MAIN_API_BASE_URL)
        assertEquals(pin.oauthBaseUrl, KinoPubConfig.OAUTH_BASE_URL)
        assertEquals(pin.extraBaseUrl, KinoPubConfig.EXTRA_API_BASE_URL)
        assertNull(KinoPubConfig.CUSTOM_API_DOMAIN)
        assertTrue(probeCalls.isEmpty(), "probed $probeCalls")
        verify(exactly = 0) { preferences.saveApiDomain(any()) }
        assertEquals(0, evictCalls)
        coVerify(exactly = 0) { contentCache.clear() }
        coVerify(exactly = 0) { detailsPrefetcher.invalidate() }
    }

    @Test
    fun initialize_keepsThePin_whenPinned() {
        interactor.initialize()

        assertNothingTouched()
    }

    @Test
    fun saveCustomDomain_writesNothing_whenPinned() = runBlocking {
        val result = interactor.saveCustomDomain("api.custom.example")

        assertEquals(ApiDomainUpdateResult.Success(interactor.getState()), result)
        assertNothingTouched()
    }

    @Test
    fun detectAndSaveWorkingDomain_probesNothing_whenPinned() = runBlocking {
        val result = interactor.detectAndSaveWorkingDomain()

        assertEquals(ApiDomainDetectionResult.Success(interactor.getState()), result)
        assertNothingTouched()
    }

    @Test
    fun detectAndSaveAlternativeBuiltInDomain_probesNothing_whenPinned() = runBlocking {
        val result = interactor.detectAndSaveAlternativeBuiltInDomain()

        assertEquals(ApiDomainDetectionResult.Success(interactor.getState()), result)
        assertNothingTouched()
    }

    @Test
    fun switchToBuiltInDomain_keepsThePin_whenPinned() = runBlocking {
        val result = interactor.switchToBuiltInDomain("api.alador.space")

        assertNotNull(result)
        assertEquals(interactor.getState(), result)
        assertNothingTouched()
    }

    private fun assertOnlyThePinWasProbed() {
        assertEquals(listOf(pin.domain), probeCalls)
        probeCalls.clear()
        assertNothingTouched()
    }

    @Test
    fun autoResolveWorkingDomain_probesOnlyThePin_whenPinned() = runBlocking {
        val result = interactor.autoResolveWorkingDomain()

        assertEquals(
            ApiDomainAutoResolveResult.Success(interactor.getState(), changed = false),
            result,
        )
        assertOnlyThePinWasProbed()
    }

    @Test
    fun autoResolveWorkingDomain_returnsNotFound_whenThePinDoesNotAnswer() = runBlocking {
        probeAnswer = false

        val result = interactor.autoResolveWorkingDomain()

        assertEquals(ApiDomainAutoResolveResult.NotFound, result)
        assertOnlyThePinWasProbed()
    }

    /** Real time on purpose: under runTest the virtual timeout fires at withContext(Dispatchers.IO). */
    @Test
    fun autoResolveWorkingDomain_leavesNoWalkInFlight_whenPinned() = runBlocking {
        interactor.autoResolveWorkingDomain()
        assertOnlyThePinWasProbed()

        KinoPubConfig.clearPinnedEndpoint()
        val result = withTimeout(5.seconds) { interactor.autoResolveWorkingDomain() }

        assertTrue(result is ApiDomainAutoResolveResult.Success)
    }

    @Test
    fun resetToDefault_writesNothing_whenPinned() = runBlocking {
        val result = interactor.resetToDefault()

        assertEquals(interactor.getState(), result)
        assertNothingTouched()
    }
}
