package com.kino.puber.data.api

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.kino.puber.core.error.ApiError
import com.kino.puber.core.session.SessionEventBus
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * A refusal has to arrive as a failure. Deserializing an error body as the success type produced an
 * empty list or a null field, which every screen above reads as "the account has nothing here".
 */
internal class KinoPubApiClientFailureTest {

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
    fun aRefusedBookmarksReadFailsInsteadOfReportingAnEmptyList(
        @TempDir cacheDir: Path,
    ) = runTest {
        withServer(
            path = "/v1/bookmarks",
            handler = { exchange ->
                exchange.respond(status = 422, body = """{"error":"Ошибка сервера"}""")
            },
        ) { baseUrl ->
            val result = client(cacheDir, baseUrl).getBookmarks()

            val error = result.exceptionOrNull()
            assertInstanceOf(ApiError.Http::class.java, error)
            assertEquals(422, (error as ApiError.Http).statusCode)
            assertEquals("Ошибка сервера", error.serverMessage)
        }
    }

    @Test
    fun aForbiddenAnswerIsReportedAsUnauthorizedRatherThanAsAnEmptyDetailsPayload(
        @TempDir cacheDir: Path,
    ) = runTest {
        withServer(
            path = "/v1/items/7",
            handler = { exchange -> exchange.respond(status = 403, body = """{"error":"нет доступа"}""") },
        ) { baseUrl ->
            val result = client(cacheDir, baseUrl).getItemDetails(7)

            assertInstanceOf(ApiError.Unauthorized::class.java, result.exceptionOrNull())
        }
    }

    @Test
    fun everyContentCallHonoursTheInjectedBaseUrl(
        @TempDir cacheDir: Path,
    ) = runTest {
        val path = AtomicReference<String>()
        withServer(
            path = "/v1/items",
            handler = { exchange ->
                path.set(exchange.requestURI.path)
                exchange.respond(
                    status = 200,
                    body = """{"items":[],"pagination":{"current":1,"perpage":20,"total":1,"total_items":0}}""",
                )
            },
        ) { baseUrl ->
            val result = client(cacheDir, baseUrl).getItems(type = "movie")

            assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
        }

        assertEquals("/v1/items", path.get())
    }

    @Test
    fun aMarkTimeAnswerWithOnlyAStatusIsASuccessAndCarriesThePosition(@TempDir cacheDir: Path) = runTest {
        val query = AtomicReference<String>()

        withServer(
            path = "/v1/watching/marktime",
            handler = { exchange ->
                query.set(exchange.requestURI.rawQuery)
                exchange.respond(status = 200, body = """{"status":200}""")
            },
        ) { baseUrl ->
            val result = client(cacheDir, baseUrl).setWatchingTime(42, 1, 120, 2)

            assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
        }

        val sent = query.get().split("&").associate { it.substringBefore("=") to it.substringAfter("=") }
        assertEquals(mapOf("id" to "42", "video" to "1", "time" to "120", "season" to "2"), sent)
    }

    @Test
    fun anEmptyMarkTimeAnswerIsASuccess(@TempDir cacheDir: Path) = runTest {
        withServer(
            path = "/v1/watching/marktime",
            handler = { exchange -> exchange.respond(status = 200, body = "{}") },
        ) { baseUrl ->
            val result = client(cacheDir, baseUrl).setWatchingTime(42, 1, 120, 2)

            assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
        }
    }

    @Test
    fun aMarkTimeAnswerWithExtraFieldsIsASuccess(@TempDir cacheDir: Path) = runTest {
        withServer(
            path = "/v1/watching/marktime",
            handler = { exchange ->
                exchange.respond(status = 200, body = """{"status":200,"id":42,"time":120}""")
            },
        ) { baseUrl ->
            val result = client(cacheDir, baseUrl).setWatchingTime(42, 1, 120, 2)

            assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
        }
    }

    @Test
    fun aMarkTimeServerErrorIsAFailureAndIsSentOnce(@TempDir cacheDir: Path) = runTest {
        val requests = AtomicInteger()

        withServer(
            path = "/v1/watching/marktime",
            handler = { exchange ->
                requests.incrementAndGet()
                exchange.respond(status = 500, body = """{"error":"x"}""")
            },
        ) { baseUrl ->
            val error = client(cacheDir, baseUrl).setWatchingTime(42, 1, 120, 2).exceptionOrNull()

            val http = assertInstanceOf(ApiError.Http::class.java, error)
            assertEquals(500, http.statusCode)
        }

        assertEquals(1, requests.get())
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
