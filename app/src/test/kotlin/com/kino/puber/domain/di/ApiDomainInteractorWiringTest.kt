package com.kino.puber.domain.di

import android.util.Base64
import com.kino.puber.data.api.network.EndpointReachability
import com.kino.puber.data.cache.ContentCacheRepository
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.DnsCache
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * The domain switch is only as good as the cache it evicts: a lambda that resolves another
 * [DnsCache], or none, would leave the resolver answering with the old domain's addresses.
 */
class ApiDomainInteractorWiringTest {

    @AfterEach
    fun tearDown() {
        stopKoin()
        unmockkStatic(Base64::class)
    }

    @Test
    fun apiDomainInteractor_evictsTheSharedDnsCache() = runTest {
        mockkStatic(Base64::class)
        every { Base64.decode(any<ByteArray>(), any()) } returns "example.test".toByteArray()
        val dnsCache = mockk<DnsCache>(relaxed = true)
        val koin = startKoin {
            modules(
                interactorModule,
                module {
                    single<ICryptoPreferenceRepository> { mockk(relaxed = true) }
                    single<ContentCacheRepository> { mockk(relaxed = true) }
                    single<ItemDetailsRepository> { mockk(relaxed = true) }
                    single { EndpointReachability() }
                    single { OkHttpClient() }
                    single<DnsCache> { dnsCache }
                },
            )
        }.koin

        koin.get<ApiDomainInteractor>().resetToDefault()

        verify(exactly = 1) { dnsCache.evictAll() }
    }
}
