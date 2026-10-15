package com.kino.puber.data.api

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.kino.puber.core.error.ApiError
import com.kino.puber.core.session.SessionEventBus
import com.kino.puber.core.telemetry.ErrSource
import com.kino.puber.core.telemetry.Telemetry
import com.kino.puber.core.telemetry.TelemetryEvent
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.RecordingTelemetryRecorder
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path

/** A token refresh that fails reaches the log as `err src=auth`, and still throws as before. */
internal class KinoPubApiClientAuthProbeTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpAndroidLogging() {
            mockkStatic(Log::class)
            every { Log.isLoggable(any(), any()) } returns false
            every { Log.println(any(), any(), any()) } returns 0
            mockkStatic(Base64::class)
            every { Base64.decode(any<ByteArray>(), any()) } returns "example.test".toByteArray()
        }

        @JvmStatic
        @AfterAll
        fun tearDownAndroidLogging() {
            unmockkStatic(Log::class)
            unmockkStatic(Base64::class)
        }
    }

    private val recorder = RecordingTelemetryRecorder()

    @BeforeEach
    fun setUp() {
        Telemetry.install(recorder, FakeTelemetryClock())
    }

    @AfterEach
    fun tearDown() {
        Telemetry.resetForTests()
    }

    @Test
    fun sessionExpiredFailureRecordsAuthErrThenThrowsUnauthorized(@TempDir cacheDir: Path) = runTest {
        withExpiredSessionServer { baseUrl ->
            val error = client(cacheDir, baseUrl).getItemDetails(7).exceptionOrNull()

            assertInstanceOf(ApiError.Unauthorized::class.java, error)
            val err = recorder.events.filterIsInstance<TelemetryEvent.Err>().single()
            assertEquals(ErrSource.AUTH, err.src)
            assertSame(error, err.error)
        }
    }

    @Test
    fun nothingIsRecordedWhenTheLogIsOff(@TempDir cacheDir: Path) = runTest {
        recorder.isEnabled = false
        withExpiredSessionServer { baseUrl ->
            val error = client(cacheDir, baseUrl).getItemDetails(7).exceptionOrNull()

            assertInstanceOf(ApiError.Unauthorized::class.java, error)
            assertEquals(0, recorder.events.size)
        }
    }

    private suspend fun withExpiredSessionServer(block: suspend (baseUrl: String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/v1/items/7") { exchange ->
            exchange.responseHeaders.add("WWW-Authenticate", "Bearer")
            exchange.respond(401)
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/v1/")
        } finally {
            server.stop(0)
        }
    }

    private fun HttpExchange.respond(status: Int) {
        sendResponseHeaders(status, -1)
        close()
    }

    private fun client(cacheDir: Path, baseUrl: String): KinoPubApiClient {
        val connectivityManager = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true

        val preferences = mockk<ICryptoPreferenceRepository>(relaxed = true)
        every { preferences.getAccessToken() } returns "expired-access"
        every { preferences.getRefreshToken() } returns "dead-refresh"
        every { preferences.getUsername() } returns null
        every { preferences.getAndroidId() } returns null

        // Anything not addressed to the local server (the OAuth refresh) is refused with a 500, so the
        // refresh fails without a network.
        val refusingRefresh = Interceptor { chain ->
            val request = chain.request()
            if (request.url.host == "127.0.0.1") {
                chain.proceed(request)
            } else {
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("refused")
                    .body("".toResponseBody())
                    .build()
            }
        }

        return KinoPubApiClient(
            okHttpClient = OkHttpClient.Builder().addInterceptor(refusingRefresh).build(),
            cacheDir = cacheDir.toFile(),
            connectivityManager = connectivityManager,
            cryptoPreferenceRepository = preferences,
            sessionEventBus = SessionEventBus(),
            mainApiBaseUrl = { baseUrl },
        )
    }
}
