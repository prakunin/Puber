package com.kino.puber.data.api

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.kino.puber.core.error.ApiError
import com.kino.puber.core.session.SessionEndReason
import com.kino.puber.core.session.SessionEvent
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * A refusal that means the session is over has to clear the tokens and tell the app once, with the
 * reason; every other refusal must leave the session alone.
 */
internal class KinoPubApiClientSessionEndTest {

    companion object {
        private const val BANNED = """{"error":"user_banned"}"""
        private const val NEW_TOKENS =
            """{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600}"""

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
    fun aUserBannedRefusalClearsTokensAndEmitsBlocked(@TempDir cacheDir: Path) = runTest {
        val env = env(cacheDir)
        withServer("/v1/items/7", { it.respond(403, BANNED) }) { baseUrl ->
            val result = env.client(baseUrl).getItemDetails(7)

            assertInstanceOf(ApiError.Unauthorized::class.java, result.exceptionOrNull())
        }

        env.prefs.assertCleared()
        assertEquals(listOf<SessionEvent>(SessionEvent.Unauthorized(SessionEndReason.Blocked)), env.events)
    }

    @Test
    fun theEndIsPendingBeforeTheTokenIsCleared(@TempDir cacheDir: Path) = runTest {
        val env = env(cacheDir)
        withServer("/v1/items/7", { it.respond(403, BANNED) }) { baseUrl ->
            env.client(baseUrl).getItemDetails(7)
        }

        assertEquals(true, env.prefs.endPendingAtClear)
    }

    @Test
    fun theEndReasonIsPersistedWhenTheSessionEnds(@TempDir cacheDir: Path) = runTest {
        val env = env(cacheDir)
        withServer("/v1/items/7", { it.respond(403, BANNED) }) { baseUrl ->
            env.client(baseUrl).getItemDetails(7)
        }

        assertEquals("Blocked", env.prefs.getSessionEndReason())
    }

    @Test
    fun aRefusalThatEndsTheSessionSaysSo(@TempDir cacheDir: Path) = runTest {
        val env = env(cacheDir)
        withServer("/v1/items/7", { it.respond(403, BANNED) }) { baseUrl ->
            val result = env.client(baseUrl).getItemDetails(7)

            assertTrue((result.exceptionOrNull() as ApiError.Unauthorized).endsSession)
        }
    }

    @Test
    fun aRefusalAfterASuccessfulRefreshEndsTheSessionAsExpired(@TempDir cacheDir: Path) = runTest {
        val env = env(cacheDir)
        env.offHost = { request ->
            if (request.isRefresh()) reply(request, 200, NEW_TOKENS) else reply(request, 500)
        }
        val headers = CopyOnWriteArrayList<String?>()
        withServer("/v1/items/7", { exchange ->
            headers += exchange.requestHeaders.getFirst("Authorization")
            exchange.respondUnauthorized()
        }) { baseUrl ->
            val result = env.client(baseUrl).getItemDetails(7)

            assertInstanceOf(ApiError.Unauthorized::class.java, result.exceptionOrNull())
        }

        assertEquals(listOf<String?>("Bearer access", "Bearer new-access"), headers.toList())
        assertEquals(1, env.refreshPosts.get())
        env.prefs.assertCleared()
        assertEquals(listOf<SessionEvent>(SessionEvent.Unauthorized(SessionEndReason.Expired)), env.events)
    }

    @Test
    fun anUnlistedForbiddenAnswerKeepsTheSession(@TempDir cacheDir: Path) = runTest {
        val env = env(cacheDir)
        withServer("/v1/items/7", { it.respond(403, """{"error":"нет доступа"}""") }) { baseUrl ->
            val result = env.client(baseUrl).getItemDetails(7)

            assertInstanceOf(ApiError.Unauthorized::class.java, result.exceptionOrNull())
        }

        env.prefs.assertUnchanged()
        assertTrue(env.events.isEmpty())
    }

    @Test
    fun anUnlistedForbiddenAnswerSaysItKeepsTheSession(@TempDir cacheDir: Path) = runTest {
        val env = env(cacheDir)
        withServer("/v1/items/7", { it.respond(403, """{"error":"нет доступа"}""") }) { baseUrl ->
            val result = env.client(baseUrl).getItemDetails(7)

            assertFalse((result.exceptionOrNull() as ApiError.Unauthorized).endsSession)
        }

        assertNull(env.prefs.getSessionEndReason())
    }

    @ParameterizedTest
    @ValueSource(ints = [401, 403])
    fun aRefusalFromAnotherHostKeepsTheSession(status: Int, @TempDir cacheDir: Path) = runTest {
        val env = env(cacheDir)
        env.offHost = { request ->
            when {
                request.isRefresh() -> reply(request, 500)
                status == 401 -> reply(request, 401, headers = mapOf("WWW-Authenticate" to "Bearer"))
                else -> reply(request, 403, BANNED)
            }
        }

        env.client("http://127.0.0.1:1/v1/").getLatestGitHubRelease("o", "r")

        env.prefs.assertUnchanged()
        assertTrue(env.events.isEmpty())
        assertEquals(0, env.refreshPosts.get())
    }

    @Test
    fun aSecondRefusalAfterTheSessionEndedEmitsNothing(@TempDir cacheDir: Path) = runTest {
        val env = env(cacheDir)
        withServer("/v1/items/7", { it.respond(403, BANNED) }) { baseUrl ->
            val client = env.client(baseUrl)
            client.getItemDetails(7)
            client.getItemDetails(7)
        }

        assertEquals(1, env.events.size)
        assertEquals(1, env.prefs.accessClears.get())
    }

    @Test
    fun aParallelBurstOfRefusalsEndsTheSessionOnce(@TempDir cacheDir: Path) = runTest {
        val env = env(cacheDir)
        withServer("/v1/items/7", { it.respond(403, BANNED) }, threads = 8) { baseUrl ->
            val client = env.client(baseUrl)
            withContext(Dispatchers.IO) {
                (1..8).map { async { client.getItemDetails(7) } }.awaitAll()
            }
        }

        assertEquals(1, env.prefs.accessClears.get())
        assertEquals(1, env.events.size)
    }

    @Test
    fun aRefusalWithNoStoredSessionSendsNoRefresh(@TempDir cacheDir: Path) = runTest {
        val env = env(cacheDir, access = null, refresh = null)
        withServer("/v1/items/7", { it.respondUnauthorized() }) { baseUrl ->
            val result = env.client(baseUrl).getItemDetails(7)

            assertInstanceOf(ApiError.Unauthorized::class.java, result.exceptionOrNull())
        }

        assertEquals(0, env.refreshPosts.get())
        assertTrue(env.events.isEmpty())
    }

    @Test
    fun anAccessTokenWithoutARefreshTokenEndsTheSessionWithoutARefresh(@TempDir cacheDir: Path) = runTest {
        val env = env(cacheDir, access = "access", refresh = null)
        withServer("/v1/items/7", { it.respondUnauthorized() }) { baseUrl ->
            env.client(baseUrl).getItemDetails(7)
        }

        assertEquals(0, env.refreshPosts.get())
        env.prefs.assertCleared()
        assertEquals(listOf<SessionEvent>(SessionEvent.Unauthorized(SessionEndReason.Expired)), env.events)
    }

    @Test
    fun aFailedRefreshEmitsExpired(@TempDir cacheDir: Path) = runTest {
        val env = env(cacheDir)
        withServer("/v1/items/7", { it.respondUnauthorized() }) { baseUrl ->
            env.client(baseUrl).getItemDetails(7)
        }

        env.prefs.assertCleared()
        assertEquals(listOf<SessionEvent>(SessionEvent.Unauthorized(SessionEndReason.Expired)), env.events)
    }

    @ParameterizedTest
    @ValueSource(ints = [401, 403])
    fun anOAuthRefusalDuringPairingKeepsThePairingScreen(status: Int, @TempDir cacheDir: Path) = runTest {
        val env = env(cacheDir, access = null, refresh = null)
        env.offHost = { request ->
            if (request.url.query.orEmpty().contains("grant_type=device_code")) {
                reply(request, status, BANNED)
            } else {
                reply(request, 500)
            }
        }

        env.client("http://127.0.0.1:1/v1/").getDeviceCode()

        assertTrue(env.events.isEmpty())
    }

    @Test
    fun aRepairAfterTheSessionEndedSendsTheNewToken(@TempDir cacheDir: Path) = runTest {
        val env = env(cacheDir)
        val headers = CopyOnWriteArrayList<String?>()
        withServer("/v1/items/7", { exchange ->
            headers += exchange.requestHeaders.getFirst("Authorization")
            if (headers.size == 1) exchange.respond(403, BANNED) else exchange.respond(200, "{}")
        }) { baseUrl ->
            val client = env.client(baseUrl)
            client.getItemDetails(7)
            env.prefs.set("new-access", "new-refresh")
            client.getItemDetails(7)
        }

        assertEquals(listOf<String?>("Bearer access", "Bearer new-access"), headers.toList())
    }

    @Test
    fun aPairingCallBeforeTheNewTokensAreSavedDoesNotPinAnEmptyBearer(@TempDir cacheDir: Path) = runTest {
        val env = env(cacheDir)
        val headers = CopyOnWriteArrayList<String?>()
        withServer("/v1/items/7", { exchange ->
            headers += exchange.requestHeaders.getFirst("Authorization")
            if (headers.size == 1) exchange.respond(403, BANNED) else exchange.respond(200, "{}")
        }) { baseUrl ->
            val client = env.client(baseUrl)
            client.getItemDetails(7)
            client.getDeviceCode()
            env.prefs.set("new-access", "new-refresh")
            client.getItemDetails(7)
        }

        assertEquals(listOf<String?>("Bearer access", "Bearer new-access"), headers.toList())
        assertEquals(0, env.refreshPosts.get())
    }

    @Test
    fun aStaleRefusalAfterRepairingKeepsTheNewSession(@TempDir cacheDir: Path) = runTest {
        val env = env(cacheDir)
        withServer("/v1/items/7", { exchange ->
            env.prefs.set("new-access", "new-refresh")
            exchange.respond(403, BANNED)
        }) { baseUrl ->
            env.client(baseUrl).getItemDetails(7)
        }

        assertEquals("new-access", env.prefs.getAccessToken())
        assertEquals("new-refresh", env.prefs.getRefreshToken())
        assertTrue(env.events.isEmpty())
    }

    private class FakePrefs(
        access: String?,
        refresh: String?,
        private val endPending: () -> Boolean = { false },
    ) : ICryptoPreferenceRepository {
        private val accessToken = AtomicReference(access)
        private val refreshToken = AtomicReference(refresh)
        private val endReason = AtomicReference<String?>(null)
        val accessClears = AtomicInteger()

        /** Whether the session end was already pending when the access token was cleared. */
        @Volatile
        var endPendingAtClear: Boolean? = null

        fun set(access: String?, refresh: String?) {
            accessToken.set(access)
            refreshToken.set(refresh)
        }

        fun assertCleared() {
            assertNull(accessToken.get())
            assertNull(refreshToken.get())
        }

        fun assertUnchanged() {
            assertEquals("access", accessToken.get())
            assertEquals("refresh", refreshToken.get())
        }

        override fun saveAccessToken(token: String) = accessToken.set(token)
        override fun getAccessToken(): String? = accessToken.get()
        override fun clearAccessToken() {
            accessClears.incrementAndGet()
            endPendingAtClear = endPending()
            accessToken.set(null)
        }

        override fun saveRefreshToken(token: String) = refreshToken.set(token)
        override fun getRefreshToken(): String? = refreshToken.get()
        override fun clearRefreshToken() = refreshToken.set(null)
        override fun saveUsername(userName: String) = Unit
        override fun getUsername(): String? = null
        override fun clearUsername() = Unit
        override fun getAndroidId(): String? = null
        override fun saveApiDomain(domain: String?) = Unit
        override fun getApiDomain(): String? = null
        override fun saveSessionEndReason(reason: String) = endReason.set(reason)
        override fun getSessionEndReason(): String? = endReason.get()
        override fun clearSessionEndReason() = endReason.set(null)
    }

    /** One client over a stateful token store and a real bus whose events are collected into [events]. */
    private inner class Env(
        private val cacheDir: Path,
        access: String? = "access",
        refresh: String? = "refresh",
    ) {
        private val bus = SessionEventBus()
        val prefs = FakePrefs(access, refresh) { bus.hasPendingEnd }
        val events = CopyOnWriteArrayList<SessionEvent>()
        val refreshPosts = AtomicInteger()
        var offHost: (Request) -> Response = { reply(it, 500) }

        fun collectEvents(scope: TestScope) {
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
                bus.events.collect { events += it }
            }
        }

        private val interceptor = Interceptor { chain ->
            val request = chain.request()
            when {
                request.url.host == "127.0.0.1" -> chain.proceed(request)
                else -> {
                    if (request.isRefresh()) refreshPosts.incrementAndGet()
                    offHost(request)
                }
            }
        }

        fun client(baseUrl: String): KinoPubApiClient {
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
                cryptoPreferenceRepository = prefs,
                sessionEventBus = bus,
                mainApiBaseUrl = { baseUrl },
            )
        }
    }

    private fun TestScope.env(cacheDir: Path, access: String? = "access", refresh: String? = "refresh"): Env =
        Env(cacheDir, access, refresh).also { it.collectEvents(this) }

    private fun Request.isRefresh(): Boolean = url.query.orEmpty().contains("grant_type=refresh_token")

    private fun reply(
        request: Request,
        code: Int,
        body: String = "",
        headers: Map<String, String> = emptyMap(),
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("answer")
        .addHeader("Content-Type", "application/json")
        .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

    private suspend fun withServer(
        path: String,
        handler: (HttpExchange) -> Unit,
        threads: Int = 1,
        block: suspend (baseUrl: String) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.executor = Executors.newFixedThreadPool(threads)
        server.createContext(path, handler)
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/v1/")
        } finally {
            server.stop(0)
            (server.executor as java.util.concurrent.ExecutorService).shutdownNow()
        }
    }

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun HttpExchange.respondUnauthorized() {
        responseHeaders.add("WWW-Authenticate", "Bearer")
        sendResponseHeaders(401, -1)
        close()
    }
}
