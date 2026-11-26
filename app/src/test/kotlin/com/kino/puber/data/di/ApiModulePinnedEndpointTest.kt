package com.kino.puber.data.di

import android.content.Context
import android.net.ConnectivityManager
import android.util.Base64
import android.util.Log
import com.kino.puber.data.api.config.ApiEndpointPreset
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.network.DohResolver
import com.kino.puber.data.api.network.HttpStack
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.nio.file.Path
import okhttp3.DnsCache
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal class ApiModulePinnedEndpointTest {

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

    @AfterEach
    fun resetConfig() {
        KinoPubConfig.clearPinnedEndpoint()
        KinoPubConfig.setDomainOverride(null)
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

    private val pin = ApiEndpointPreset(
        domain = "127.0.0.1",
        apiHost = "127.0.0.1:8080",
        mainBaseUrl = "http://127.0.0.1:8080/v1/",
        oauthBaseUrl = "http://127.0.0.1:8080/oauth2/",
        extraBaseUrl = "http://127.0.0.1:8080/",
    )

    @Test
    fun unpinnedEndpoint_buildsTheAppClientWithDoh(@TempDir cacheDir: Path) {
        val application = startApiModule(cacheDir)
        try {
            assertTrue(application.koin.get<OkHttpClient>().dns is DnsOverHttps)
        } finally {
            application.close()
        }
    }

    @Test
    fun pinnedEndpointWithResolver_buildsTheAppClientWithDoh(@TempDir cacheDir: Path) {
        KinoPubConfig.setPinnedEndpoint(
            pin,
            DohResolver("https://dns.baseline.test:18765/dns-query", listOf("127.0.0.1")),
        )
        val application = startApiModule(cacheDir)
        try {
            assertTrue(application.koin.get<OkHttpClient>().dns is DnsOverHttps)
        } finally {
            application.close()
        }
    }

    @Test
    fun pinnedEndpoint_buildsTheAppClientWithoutDoh(@TempDir cacheDir: Path) {
        KinoPubConfig.setPinnedEndpoint(pin)
        val application = startApiModule(cacheDir)
        try {
            val koin = application.koin

            assertFalse(koin.get<OkHttpClient>().dns is DnsOverHttps)
            assertSame(koin.get<HttpStack>().dnsCache, koin.get<DnsCache>())
        } finally {
            application.close()
        }
    }
}
