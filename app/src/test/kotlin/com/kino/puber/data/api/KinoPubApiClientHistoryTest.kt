package com.kino.puber.data.api

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.kino.puber.core.session.SessionEventBus
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class KinoPubApiClientHistoryTest {

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
    fun getHistoryData_usesExplicitGetWithPageAndNoStore(
        @TempDir cacheDir: Path,
    ) = runTest {
        val request = AtomicReference<CapturedRequest>()
        withServer(
            path = "/v1/history",
            handler = { exchange ->
                request.set(exchange.capture())
                exchange.respond(
                    status = 200,
                    body = """
                        {
                          "history": [],
                          "pagination": {
                            "current": 3,
                            "perpage": 20,
                            "total": 3,
                            "total_items": 0
                          }
                        }
                    """.trimIndent(),
                )
            },
        ) { baseUrl ->
            val result = client(cacheDir, baseUrl).getHistoryData(page = 3)

            assertTrue(result.isSuccess, result.exceptionOrNull()?.stackTraceToString())
            assertEquals(3, result.getOrThrow().pagination.current)
        }

        assertEquals(
            CapturedRequest(
                method = "GET",
                query = "page=3",
                cacheControl = "no-store",
            ),
            request.get(),
        )
    }

    @Test
    fun clearExactMediaHistory_non2xxFailsBeforeUnitConversion(
        @TempDir cacheDir: Path,
    ) = runTest {
        val request = AtomicReference<CapturedRequest>()
        withServer(
            path = "/v1/history/clear-for-media",
            handler = { exchange ->
                request.set(exchange.capture())
                exchange.respond(status = 422, body = """{"error":"synthetic rejection"}""")
            },
        ) { baseUrl ->
            val result = client(cacheDir, baseUrl).clearExactMediaHistory(mediaId = 73_001)

            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull()?.message
                    ?.contains("HTTP 422") == true,
                result.exceptionOrNull()?.stackTraceToString(),
            )
        }

        assertEquals(
            CapturedRequest(
                method = "POST",
                query = "id=73001",
                cacheControl = "no-store",
            ),
            request.get(),
        )
    }

    @Test
    fun updateDeviceInfo_sendsTitleHardwareAndSoftwareAsQueryParameters(
        @TempDir cacheDir: Path,
    ) = runTest {
        val sent = AtomicReference<Request>()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                sent.set(chain.request())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"status":200}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        client(cacheDir, KinoPubConfig.MAIN_API_BASE_URL, okHttpClient).updateDeviceInfo(
            title = "Puber",
            hardware = "Google Chromecast HD",
            software = "Android 14",
        )

        val request = sent.get()
        assertEquals("POST", request.method)
        assertEquals("/v1/device/notify", request.url.encodedPath)
        assertEquals("Puber", request.url.queryParameter("title"))
        assertEquals("Google Chromecast HD", request.url.queryParameter("hardware"))
        assertEquals("Android 14", request.url.queryParameter("software"))

        val body = Buffer().also { buffer -> request.body?.writeTo(buffer) }.readUtf8()
        assertEquals(
            "application/x-www-form-urlencoded",
            request.body?.contentType()?.let { "${it.type}/${it.subtype}" },
        )
        assertEquals(
            listOf("application/x-www-form-urlencoded"),
            request.headers.values("Content-Type").map { value -> value.substringBefore(';').trim() },
        )
        assertEquals(
            mapOf(
                "title" to "Puber",
                "hardware" to "Google Chromecast HD",
                "software" to "Android 14",
            ),
            body.split("&").associate { pair ->
                val (name, value) = pair.split("=", limit = 2)
                name to URLDecoder.decode(value, "UTF-8")
            },
        )
    }

    private fun client(
        cacheDir: Path,
        baseUrl: String,
        okHttpClient: OkHttpClient = OkHttpClient(),
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
            okHttpClient = okHttpClient,
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

    private fun HttpExchange.capture(): CapturedRequest {
        return CapturedRequest(
            method = requestMethod,
            query = requestURI.rawQuery,
            cacheControl = requestHeaders.getFirst("Cache-Control"),
        )
    }

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private data class CapturedRequest(
        val method: String,
        val query: String?,
        val cacheControl: String?,
    )
}
