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
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** The country, year and finished filters reach the items query the way the server reads them. */
internal class KinoPubApiClientItemsFilterTest {

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
    fun getItems_sendsCountryYearAndFinishedAsTheServerReadsThem(@TempDir cacheDir: Path) = runTest {
        val query = queryOf(cacheDir) {
            it.getItems(type = "serial", page = 1, country = 1, year = "2010-2019", finished = true)
        }

        assertTrue(query.containsAll(setOf("country=1", "year=2010-2019", "finished=1")), query.toString())
    }

    @Test
    fun getItems_withoutFilterParams_sendsNoneOfThem(@TempDir cacheDir: Path) = runTest {
        val query = queryOf(cacheDir) { it.getItems(type = "movie", page = 1) }

        assertTrue(query.none { it.substringBefore('=') in setOf("country", "year", "finished") }, query.toString())
    }

    private suspend fun queryOf(cacheDir: Path, call: suspend (KinoPubApiClient) -> Unit): Set<String> {
        val query = AtomicReference<String>()
        withServer(
            path = "/v1/items",
            handler = { exchange ->
                query.set(exchange.requestURI.query)
                exchange.respond(
                    status = 200,
                    body = """{"items":[],"pagination":{"total":1,"current":1,"perpage":50}}""",
                )
            },
        ) { baseUrl ->
            call(client(cacheDir, baseUrl))
        }
        return query.get().split('&').toSet()
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
