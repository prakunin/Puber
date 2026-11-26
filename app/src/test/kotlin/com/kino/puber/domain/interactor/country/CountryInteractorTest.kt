package com.kino.puber.domain.interactor.country

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Country
import com.kino.puber.data.cache.ContentCacheRepository
import com.kino.puber.util.FakePayloadStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CountryInteractorTest {

    private val api = mockk<KinoPubApiClient>()
    private val interactor = CountryInteractor(api, ContentCacheRepository(FakePayloadStore()))

    @Test
    fun getCountries_secondCallIsServedFromTheCache() = runTest {
        val countries = listOf(Country(1, "США"), Country(2, "Франция"))
        coEvery { api.getCountries() } returns Result.success(countries)

        assertEquals(countries, interactor.getCountries().getOrThrow())
        assertEquals(countries, interactor.getCountries().getOrThrow())

        coVerify(exactly = 1) { api.getCountries() }
    }
}
