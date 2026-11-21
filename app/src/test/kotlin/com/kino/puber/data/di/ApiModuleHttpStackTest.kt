package com.kino.puber.data.di

import android.content.Context
import android.net.ConnectivityManager
import android.util.Base64
import android.util.Log
import com.kino.puber.data.api.IntroDbAppApiClient
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.TheIntroDbApiClient
import com.kino.puber.data.api.TmdbApiClient
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.nio.file.Path
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal class ApiModuleHttpStackTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun mockAndroidStatics() {
            mockkStatic(Log::class)
            every { Log.isLoggable(any(), any()) } returns false
            every { Log.println(any(), any(), any()) } returns 0
            mockkStatic(Base64::class)
            every { Base64.decode(any<ByteArray>(), any()) } returns "example.test".toByteArray()
        }

        @JvmStatic
        @AfterAll
        fun unmockAndroidStatics() {
            unmockkStatic(Log::class)
            unmockkStatic(Base64::class)
        }
    }

    private fun startApiModule(cacheDir: Path): KoinApplication {
        val context = mockk<Context>(relaxed = true)
        every { context.cacheDir } returns cacheDir.toFile()
        every { context.getSystemService(ConnectivityManager::class.java) } returns mockk(relaxed = true)
        return koinApplication {
            androidContext(context)
            modules(
                apiModule,
                module { single<ICryptoPreferenceRepository> { mockk(relaxed = true) } },
            )
        }
    }

    private fun engineClientOf(owner: Any): OkHttpClient {
        val httpClient = owner.javaClass.getDeclaredField("httpClient")
            .apply { isAccessible = true }
            .get(owner) as HttpClient
        return (httpClient.engine.config as OkHttpConfig).preconfigured!!
    }

    @Test
    fun apiClients_shareOneConnectionPoolAndOneDns(@TempDir cacheDir: Path) {
        val application = startApiModule(cacheDir)
        try {
            val koin = application.koin
            val shared = koin.get<OkHttpClient>()
            val appClients = listOf(
                engineClientOf(koin.get<KinoPubApiClient>()),
                engineClientOf(koin.get<TmdbApiClient>()),
                engineClientOf(koin.get<IntroDbAppApiClient>()),
                engineClientOf(koin.get<TheIntroDbApiClient>()),
                (koin.get<HttpClient>(ImageHttpClientQualifier).engine.config as OkHttpConfig).preconfigured!!,
            )
            val dohClient = (shared.dns as DnsOverHttps).client

            (appClients + dohClient).forEach { assertSame(shared.connectionPool, it.connectionPool) }
            appClients.forEach { assertSame(shared.dns, it.dns) }
        } finally {
            application.close()
        }
    }

    @Test
    fun dohClient_sharesThePoolButResolvesWithoutDohOnItsOwnDispatcher(@TempDir cacheDir: Path) {
        val application = startApiModule(cacheDir)
        try {
            val shared = application.koin.get<OkHttpClient>()
            val dohClient = (shared.dns as DnsOverHttps).client

            assertSame(shared.connectionPool, dohClient.connectionPool)
            assertFalse(dohClient.dns is DnsOverHttps)
            assertNotSame(shared.dispatcher, dohClient.dispatcher)
        } finally {
            application.close()
        }
    }
}
