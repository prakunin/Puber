package com.kino.puber.data.api.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Base64
import com.kino.puber.core.session.SessionEventBus
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import io.ktor.client.request.get
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ImageHttpClientTest {

    @TempDir
    lateinit var cacheDir: Path

    @Test
    fun imageRequest_carriesNoKinoPubAuthHeaderOrQuery() = runTest {
        val shared = HttpStack.create().client
        kinoPubClient(shared)
        val seen = mutableListOf<Request>()
        val capture = Interceptor { chain ->
            seen += chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody())
                .build()
        }

        val client = createImageHttpClient(shared.newBuilder().addInterceptor(capture).build())
        client.get("https://img.example/p.jpg?w=300")
        client.close()

        val request = seen.single()
        assertNull(request.header("Authorization"))
        assertFalse(request.header("User-Agent").orEmpty().startsWith("kinopub/"))
        assertEquals("w=300", request.url.query)
    }

    private fun kinoPubClient(shared: OkHttpClient): KinoPubApiClient {
        val connectivityManager = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        val preferences = mockk<ICryptoPreferenceRepository>(relaxed = true)
        every { preferences.getAccessToken() } returns "secret-token"
        every { preferences.getRefreshToken() } returns null
        every { preferences.getUsername() } returns null
        every { preferences.getAndroidId() } returns null
        return KinoPubApiClient(
            okHttpClient = shared,
            cacheDir = cacheDir.toFile(),
            connectivityManager = connectivityManager,
            cryptoPreferenceRepository = preferences,
            sessionEventBus = SessionEventBus(),
        )
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun mockBase64() {
            mockkStatic(Base64::class)
        }

        @AfterAll
        @JvmStatic
        fun unmockBase64() {
            unmockkStatic(Base64::class)
        }
    }
}
