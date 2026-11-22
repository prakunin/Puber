package com.kino.puber.data.api

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.kino.puber.core.session.SessionEventBus
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
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
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * The view models call this client from Main, so decoding a response body in the caller's context
 * would put every large payload's parse on the thread that draws the frame.
 */
internal class KinoPubApiClientDecodeDispatcherTest {

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
    fun responseBodyIsDecodedOnTheInjectedDispatcher(@TempDir cacheDir: Path) = runTest {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "decode") }
        val recording = RecordingDispatcher(executor.asCoroutineDispatcher())
        try {
            withServer(
                path = "/v1/items/7",
                handler = { exchange -> exchange.respond(status = 200, body = """{"status":"200"}""") },
            ) { baseUrl ->
                val result = client(cacheDir, baseUrl, recording).getItemDetails(7)

                assertEquals("200", result.getOrThrow().status)
                assertTrue(recording.dispatches.get() >= 1, "dispatches: ${recording.dispatches.get()}")
            }
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun aThirdPartyResponseBodyIsDecodedOnTheInjectedDispatcher(@TempDir cacheDir: Path) = runTest {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "decode") }
        try {
            withServer(
                path = "/v1/probe",
                handler = { exchange -> exchange.respond(status = 200, body = "\"x\"") },
            ) { baseUrl ->
                val probeClient = probeHttpClient()
                val result = client(cacheDir, baseUrl, executor.asCoroutineDispatcher())
                    .thirdPartyApiCall<ThreadProbe> { probeClient.get(baseUrl + "probe") }

                assertEquals("decode", result.getOrThrow().thread)
            }
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun theApiCallBodyIsDecodedOnTheDecodeThread(@TempDir cacheDir: Path) = runTest {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "decode") }
        try {
            withServer(
                path = "/v1/probe",
                handler = { exchange -> exchange.respond(status = 200, body = "\"x\"") },
            ) { baseUrl ->
                val probeClient = probeHttpClient()
                val result = client(cacheDir, baseUrl, executor.asCoroutineDispatcher())
                    .apiCall<ThreadProbe> { probeClient.get(baseUrl + "probe") }

                assertEquals("decode", result.getOrThrow().thread)
            }
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun theOAuthResponseTextIsDecodedOnTheInjectedDispatcher(@TempDir cacheDir: Path) = runTest {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "decode") }
        val recording = RecordingDispatcher(executor.asCoroutineDispatcher())
        val oauthStub = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    """{"code":"c","user_code":"u","verification_uri":"v","expires_in":1,"interval":1}"""
                        .toResponseBody("application/json".toMediaType()),
                )
                .build()
        }
        try {
            val result = client(
                cacheDir,
                "http://127.0.0.1:1/v1/",
                recording,
                OkHttpClient.Builder().addInterceptor(oauthStub).build(),
            ).getDeviceCode()

            assertEquals("c", result.getOrThrow().code)
            assertEquals(listOf("decode"), recording.ranOn.toList())
        } finally {
            executor.shutdown()
        }
    }

    /** Records the thread its own deserialization runs on, which is where the body is decoded. */
    @Serializable(with = ThreadProbeSerializer::class)
    private class ThreadProbe(val thread: String)

    private object ThreadProbeSerializer : KSerializer<ThreadProbe> {
        override val descriptor = PrimitiveSerialDescriptor("ThreadProbe", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): ThreadProbe {
            decoder.decodeString()
            return ThreadProbe(Thread.currentThread().name)
        }

        override fun serialize(encoder: Encoder, value: ThreadProbe) = encoder.encodeString(value.thread)
    }

    private fun probeHttpClient() = HttpClient(OkHttp) {
        install(ContentNegotiation) { json() }
    }

    private class RecordingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        val dispatches = AtomicInteger()
        val ranOn = CopyOnWriteArrayList<String>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches.incrementAndGet()
            delegate.dispatch(context) {
                ranOn.add(Thread.currentThread().name)
                block.run()
            }
        }
    }

    private fun client(
        cacheDir: Path,
        baseUrl: String,
        decodeDispatcher: CoroutineDispatcher,
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
            decodeDispatcher = decodeDispatcher,
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
