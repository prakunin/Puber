package com.kino.puber.data.api

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.kino.puber.core.error.ApiError
import com.kino.puber.core.session.SessionEventBus
import com.kino.puber.data.api.models.Genre
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** The genres endpoint answers `{status, items}`; a refusal has to arrive as a failure. */
internal class KinoPubApiClientGenresTest {

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

    @Test
    fun getGenres_unwrapsTheItemsOfAStatusItemsBody(@TempDir cacheDir: Path) = runTest {
        withServer(
            path = "/v1/genres",
            handler = { exchange ->
                exchange.respond(
                    status = 200,
                    body = """{"status":200,"items":[{"id":9,"title":"Драма","type":"movie"}]}""",
                )
            },
        ) { baseUrl ->
            val result = client(cacheDir, baseUrl).getGenres("movie")

            assertEquals(listOf(Genre(9, "Драма")), result.getOrNull(), result.exceptionOrNull()?.stackTraceToString())
        }
    }

    @Test
    fun getGenres_sendsTheTypeParameter(@TempDir cacheDir: Path) = runTest {
        val query = AtomicReference<String>()
        withServer(
            path = "/v1/genres",
            handler = { exchange ->
                query.set(exchange.requestURI.query)
                exchange.respond(status = 200, body = """{"status":200,"items":[]}""")
            },
        ) { baseUrl ->
            client(cacheDir, baseUrl).getGenres("serial")
        }

        assertEquals("type=serial", query.get())
    }

    @Test
    fun getGenres_refusedRead_failsInsteadOfAnEmptyList(@TempDir cacheDir: Path) = runTest {
        withServer(
            path = "/v1/genres",
            handler = { exchange -> exchange.respond(status = 422, body = """{"error":"Ошибка сервера"}""") },
        ) { baseUrl ->
            val error = client(cacheDir, baseUrl).getGenres("movie").exceptionOrNull()

            assertEquals(422, assertInstanceOf(ApiError.Http::class.java, error).statusCode)
        }
    }

    private fun client(
        cacheDir: Path,
        baseUrl: String,
    ): KinoPubApiClient {
        val connectivityManager = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } returns true

        val preferences = mockk<ICryptoPreferenceRepository>(relaxed = true)
        every { preferences.getAccessToken() } returns null
        every { preferences.getRefreshToken() } returns null
        every { preferences.getUsername() } returns null
        every { preferences.getAndroidId() } returns null

        return KinoPubApiClient(
            okHttpClient = OkHttpClient(),
            cacheDir = cacheDir.toFile(),
            connectivityManager = connectivityManager,
            cryptoPreferenceRepository = preferences,
            sessionEventBus = SessionEventBus(),
            mainApiBaseUrl = { baseUrl },
        )
    }

    private suspend fun withServer(
        path: String,
        handler: (HttpExchange) -> Unit,
        block: suspend (baseUrl: String) -> Unit,
    ) {
        val server = HttpServer.create(
            InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
            0,
        )
        server.createContext(path, handler)
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/v1/")
        } finally {
            server.stop(0)
        }
    }

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
