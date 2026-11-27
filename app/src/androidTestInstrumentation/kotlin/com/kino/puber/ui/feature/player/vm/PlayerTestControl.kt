package com.kino.puber.ui.feature.player.vm

import com.kino.puber.BuildConfig
import com.kino.puber.playertestfixtures.server.HermeticRequestJournal
import com.kino.puber.playertestfixtures.server.HermeticRoute
import com.kino.puber.playertestfixtures.server.HermeticTestServer
import com.kino.puber.playertestfixtures.server.QueryMatchMode
import com.kino.puber.playertestfixtures.server.ResponsePlan
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import mockwebserver3.RecordedRequest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

/**
 * Instrumentation-owned lifecycle boundary for the common hermetic player
 * server. Android tests may define routes and inspect bounded evidence, but
 * they do not create or own the underlying server.
 *
 * The server speaks HTTPS with a certificate generated per instance; only the client
 * from [newClient] trusts it, so the instrumentation network security config stays untouched.
 */
internal class PlayerTestControl(
    port: Int = BuildConfig.PLAYER_FIXTURE_PORT,
) : Closeable {

    private val held = HeldCertificate.Builder().addSubjectAlternativeName(LOOPBACK_HOST).build()
    private val server = HermeticTestServer(
        port = port,
        serverCertificates = HandshakeCertificates.Builder().heldCertificate(held).build(),
    )

    private val clients = CopyOnWriteArrayList<OkHttpClient>()

    val requestJournal: HermeticRequestJournal.Snapshot
        get() = server.requestJournal

    val activeRequestCount: Int
        get() = server.activeRequestCount

    fun start(initialRoutes: List<HermeticRoute> = emptyList()) {
        server.start()
        server.reset(initialRoutes)
    }

    fun reset(routes: List<HermeticRoute>) {
        server.reset(routes)
    }

    fun route(
        id: String,
        method: String = "GET",
        path: String,
        query: Map<String, String> = emptyMap(),
        queryMode: QueryMatchMode = QueryMatchMode.Exact,
        response: ResponsePlan,
        required: Boolean = false,
        minimumRequests: Int = 1,
    ): HermeticRoute = server.route(
        id = id,
        method = method,
        path = path,
        query = query,
        queryMode = queryMode,
        response = response,
        required = required,
        minimumRequests = minimumRequests,
    )

    fun url(path: String, query: Map<String, String> = emptyMap()): String =
        server.url(path, query)

    /**
     * A client that trusts this server's certificate and nothing else. It is remembered so [close]
     * can drop its keep-alive connections: an idle pooled socket keeps a MockWebServer connection
     * task running, and the server then gives up waiting for its queue to shut down.
     */
    fun newClient(interceptor: Interceptor? = null): OkHttpClient {
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(held.certificate).build()
        return OkHttpClient.Builder()
            .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
            .apply { interceptor?.let(::addInterceptor) }
            .build()
            .also { clients += it }
    }

    fun takeRequest(
        timeout: Long,
        unit: TimeUnit,
    ): RecordedRequest? = server.takeRequest(timeout, unit)

    fun awaitQuiescence(
        timeout: Long = DEFAULT_QUIESCENCE_TIMEOUT_SECONDS,
        unit: TimeUnit = TimeUnit.SECONDS,
    ) {
        server.awaitQuiescence(timeout, unit)
    }

    fun assertRequiredRoutesRequested() {
        val missing = server.verifyRequiredRoutes()
        if (missing.isNotEmpty()) {
            throw IllegalStateException("Required routes were not requested: $missing")
        }
    }

    fun assertNoUnknownRequests(
        maxDiagnostics: Int = DEFAULT_MAX_DIAGNOSTIC_REQUESTS,
    ) {
        val unknownRequests = requestJournal.unknownRequests
        if (unknownRequests.isNotEmpty()) {
            throw AssertionError(
                "Unexpected loopback requests: ${unknownRequests.takeLast(maxDiagnostics)}",
            )
        }
    }

    override fun close() {
        clients.forEach { client ->
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
        }
        server.close()
    }

    private companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
        const val DEFAULT_QUIESCENCE_TIMEOUT_SECONDS = 10L
        const val DEFAULT_MAX_DIAGNOSTIC_REQUESTS = 12
    }
}

/**
 * Raises one latch per request path when the consumer closes that response body, which the loader
 * normally does once it is done with the playlist. Any close counts, so it is a bound on parsing,
 * not proof of it. Starting a request for a path replaces that path's latch, so an earlier close of
 * the same path does not satisfy a later wait. An interceptor, not an `EventListener`:
 * debug builds of the controller replace the client's event listener factory. A response of
 * another path never raises the latch.
 */
internal class ClosedResponseSignals : Interceptor {
    private val latches = ConcurrentHashMap<String, CountDownLatch>()

    fun awaitClosed(path: String, timeoutMs: Long): Boolean =
        latch(path).await(timeoutMs, TimeUnit.MILLISECONDS)

    override fun intercept(chain: Interceptor.Chain): Response {
        val path = chain.request().url.encodedPath
        latches[path] = CountDownLatch(1)
        val response = chain.proceed(chain.request())
        val body = response.body
        return response.newBuilder().body(SignalingBody(body, path)).build()
    }

    private fun latch(path: String) = latches.computeIfAbsent(path) { CountDownLatch(1) }

    private inner class SignalingBody(private val delegate: ResponseBody, private val path: String) : ResponseBody() {
        private val signalingSource = object : ForwardingSource(delegate.source()) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    latch(path).countDown()
                }
            }
        }.buffer()

        override fun contentType() = delegate.contentType()

        override fun contentLength() = delegate.contentLength()

        override fun source(): BufferedSource = signalingSource
    }
}
