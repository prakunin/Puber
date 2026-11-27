package com.kino.puber.ui.feature.player.vm

import com.kino.puber.BuildConfig
import com.kino.puber.playertestfixtures.server.HermeticRequestJournal
import com.kino.puber.playertestfixtures.server.HermeticRoute
import com.kino.puber.playertestfixtures.server.HermeticTestServer
import com.kino.puber.playertestfixtures.server.QueryMatchMode
import com.kino.puber.playertestfixtures.server.ResponsePlan
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

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
    fun newClient(): OkHttpClient {
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(held.certificate).build()
        return OkHttpClient.Builder()
            .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
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
