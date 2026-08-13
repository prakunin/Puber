package com.kino.puber.domain.interactor.api

import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.domain.interactor.genre.GenreInteractor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ApiDomainInteractorTest {

    private val preferences = mockk<ICryptoPreferenceRepository>(relaxed = true)
    private val itemDetailsRepository = mockk<ItemDetailsRepository>(relaxed = true)
    private val genreInteractor = mockk<GenreInteractor>(relaxed = true)
    private val interactor = ApiDomainInteractor(
        preferences = preferences,
        itemDetailsRepository = itemDetailsRepository,
        genreInteractor = genreInteractor,
        okHttpClient = OkHttpClient(),
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
    }

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
    fun resetToDefault_completesEvenWhenTheItemDetailsCacheFailsToClear() = runTest {
        coEvery { itemDetailsRepository.clear() } throws IllegalStateException("disk full")

        val state = interactor.resetToDefault()

        assertEquals("service-kp.test", state.domain)
        assertNull(state.customDomain)
        verify(exactly = 1) { preferences.saveApiDomain(null) }
        // The other cache still gets its chance — one failing must not skip the other.
        verify(exactly = 1) { genreInteractor.clearCache() }
        coVerify(exactly = 1) { itemDetailsRepository.clear() }
    }

    @Test
    fun saveCustomDomain_completesEvenWhenTheItemDetailsCacheFailsToClear() = runTest {
        coEvery { itemDetailsRepository.clear() } throws IllegalStateException("disk full")

        val result = interactor.saveCustomDomain("api.custom.example")

        val success = result as? ApiDomainUpdateResult.Success
            ?: error("Expected Success, got $result")
        assertEquals("api.custom.example", success.state.domain)
        assertEquals("api.custom.example", success.state.customDomain)
        verify(exactly = 1) { preferences.saveApiDomain("api.custom.example") }
        verify(exactly = 1) { genreInteractor.clearCache() }
    }
}
