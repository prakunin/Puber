package com.kino.puber.data.api

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.kino.puber.core.session.SessionEventBus
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** The update check asks GitHub unless a caller names another origin, which replaces only the origin. */
internal class KinoPubApiClientGitHubReleaseTest {

    companion object {
        private const val ACCEPT = "application/vnd.github+json"

        @JvmStatic
        @BeforeAll
        fun setUpAndroidStatics() {
            mockkStatic(Log::class)
            every { Log.isLoggable(any(), any()) } returns false
            every { Log.println(any(), any(), any()) } returns 0
            mockkStatic(Base64::class)
            every { Base64.decode(any<ByteArray>(), any()) } returns "example.test".toByteArray()
        }

        @JvmStatic
        @AfterAll
        fun tearDownAndroidStatics() {
            unmockkStatic(Log::class)
            unmockkStatic(Base64::class)
        }
    }

    private val requestedUrls = mutableListOf<String>()
    private val acceptHeaders = mutableListOf<List<String>>()

    private fun client(cacheDir: Path): KinoPubApiClient {
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            requestedUrls += request.url.toString()
            acceptHeaders += request.header("Accept").orEmpty().split(",")
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .addHeader("Content-Type", "application/json")
                .body("""{"tag_name":"v0.0.1"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }
        val connectivityManager = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        return KinoPubApiClient(
            okHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            cacheDir = cacheDir.toFile(),
            connectivityManager = connectivityManager,
            cryptoPreferenceRepository = mockk<ICryptoPreferenceRepository>(relaxed = true),
            sessionEventBus = SessionEventBus(),
        )
    }

    @Test
    fun getLatestGitHubRelease_requestsGitHubApi_byDefault(@TempDir cacheDir: Path) = runTest {
        client(cacheDir).getLatestGitHubRelease("o", "r")

        assertEquals(listOf("https://api.github.com/repos/o/r/releases/latest"), requestedUrls)
        assertTrue(ACCEPT in acceptHeaders.single(), "Accept was $acceptHeaders")
    }

    @Test
    fun getLatestGitHubRelease_replacesOnlyTheOrigin_whenBaseUrlGiven(@TempDir cacheDir: Path) = runTest {
        client(cacheDir).getLatestGitHubRelease("o", "r", "https://127.0.0.1:18765/github")

        assertEquals(listOf("https://127.0.0.1:18765/github/repos/o/r/releases/latest"), requestedUrls)
        assertTrue(ACCEPT in acceptHeaders.single(), "Accept was $acceptHeaders")
    }
}
