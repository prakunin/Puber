package com.kino.puber.data.api.config

import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class KinoPubConfigTest {

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

    @AfterEach
    fun resetOverride() {
        KinoPubConfig.setDomainOverride(null)
    }

    private fun expectedFor(domain: String?): ApiEndpointPreset {
        val default = KinoPubConfig.DEFAULT_API_DOMAIN
        val defaultHost = "api.$default"
        val defaultEndpoint = ApiEndpointPreset(
            domain = default,
            apiHost = defaultHost,
            mainBaseUrl = "https://$defaultHost/v1/",
            oauthBaseUrl = "https://$defaultHost/oauth2/",
            extraBaseUrl = "https://$defaultHost/",
        )
        return when (domain) {
            null -> defaultEndpoint
            "api.alador.space" -> ApiEndpointPreset(
                domain = domain,
                apiHost = domain,
                mainBaseUrl = "https://api.alador.space/v1/",
                oauthBaseUrl = "https://api.alador.space/oauth2/",
                extraBaseUrl = "https://api.alador.space/",
            )
            "cdn-service.online" -> ApiEndpointPreset(
                domain = domain,
                apiHost = domain,
                mainBaseUrl = "https://cdn-service.online/api/v1/",
                oauthBaseUrl = "https://cdn-service.online/api/oauth2/",
                extraBaseUrl = "https://cdn-service.online/",
            )
            else -> ApiEndpointPreset(
                domain = domain,
                apiHost = domain,
                mainBaseUrl = "https://$domain/v1/",
                oauthBaseUrl = defaultEndpoint.oauthBaseUrl,
                extraBaseUrl = defaultEndpoint.extraBaseUrl,
            )
        }
    }

    private fun assertGettersMatch(domain: String?) {
        val expected = expectedFor(domain)
        assertEquals(expected.domain, KinoPubConfig.CURRENT_API_DOMAIN)
        assertEquals(expected.apiHost, KinoPubConfig.CURRENT_API_HOST)
        assertEquals(expected, KinoPubConfig.CURRENT_ENDPOINT)
        assertEquals(domain, KinoPubConfig.CUSTOM_API_DOMAIN)
        assertEquals(expected.mainBaseUrl, KinoPubConfig.MAIN_API_BASE_URL)
        assertEquals(expected.oauthBaseUrl, KinoPubConfig.OAUTH_BASE_URL)
        assertEquals(expected.extraBaseUrl, KinoPubConfig.EXTRA_API_BASE_URL)
        assertEquals(
            listOf(
                expectedFor(null),
                expectedFor("api.alador.space"),
                expectedFor("cdn-service.online"),
            ),
            KinoPubConfig.BUILT_IN_ENDPOINTS,
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "api.alador.space", "cdn-service.online", "mirror.example"])
    fun getters_matchTodaysValues_forDefaultAladorLegacyCdnAndCustomMirror(domain: String) {
        val override = domain.ifEmpty { null }
        KinoPubConfig.setDomainOverride(override)
        assertGettersMatch(override)
    }

    @Test
    fun currentEndpoint_returnsTheSameInstanceUntilTheNextOverride() {
        KinoPubConfig.setDomainOverride(null)
        assertSame(KinoPubConfig.CURRENT_ENDPOINT, KinoPubConfig.CURRENT_ENDPOINT)

        KinoPubConfig.setDomainOverride("mirror.example")
        val mirror = KinoPubConfig.CURRENT_ENDPOINT
        assertSame(mirror, KinoPubConfig.CURRENT_ENDPOINT)

        KinoPubConfig.setDomainOverride("other.example")
        assertNotSame(mirror, KinoPubConfig.CURRENT_ENDPOINT)
        assertEquals("other.example", KinoPubConfig.CURRENT_ENDPOINT.domain)
    }

    @Test
    fun setDomainOverride_switchesEveryGetterTogether() {
        KinoPubConfig.setDomainOverride("mirror.example")
        assertGettersMatch("mirror.example")
        KinoPubConfig.setDomainOverride("api.alador.space")
        assertGettersMatch("api.alador.space")
        KinoPubConfig.setDomainOverride(null)
        assertGettersMatch(null)
        assertNull(KinoPubConfig.CUSTOM_API_DOMAIN)
    }

    @Test
    fun builtInEndpoints_returnsTheSameListOnRepeatedReads() {
        assertSame(KinoPubConfig.BUILT_IN_ENDPOINTS, KinoPubConfig.BUILT_IN_ENDPOINTS)
    }
}
