package com.kino.puber.baselineprofile

import android.content.Context
import java.io.Closeable
import java.net.InetAddress
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer

/**
 * A route-based HTTPS backend for the baseline profile journeys.
 *
 * The server is owned by the generator process, so the target application can be killed between
 * iterations without taking down the mock or its request record. A request that matches no route
 * is answered with 404 and recorded; verification fails on any such request.
 */
class BaselineMockBackend(
    private val port: Int,
    private val fixtures: BaselineFixtures,
    private val leaf: HeldCertificate,
) : Closeable {

    private val server = MockWebServer()
    private val lock = Any()
    private var activeScenario = BaselineScenario.Startup
    private var activeRoutes: List<BaselineMockRoute> = emptyList()
    private val matchedCounts = linkedMapOf<BaselineMockRoute, Int>()
    private val unknownRequests = mutableListOf<BaselineRequest>()
    private var started = false

    /** The bound port; differs from the constructor argument only when that is 0. */
    val boundPort: Int
        get() = server.port

    /** Number of requests seen since the last reset, matched or not. */
    val requestCount: Int
        get() = synchronized(lock) { matchedCounts.values.sum() + unknownRequests.size }

    fun start() {
        synchronized(lock) {
            check(!started) { "BaselineMockBackend is already started" }
            server.useHttps(HandshakeCertificates.Builder().heldCertificate(leaf).build().sslSocketFactory())
            server.dispatcher = routeDispatcher()
            server.start(InetAddress.getByName(LOOPBACK), port)
            started = true
            resetTest()
        }
    }

    /** Forgets everything recorded so far, including unknown requests. Called once per test. */
    fun resetTest() {
        synchronized(lock) {
            check(started) { "Call start() before resetTest()" }
            unknownRequests.clear()
            startIteration(activeScenario)
        }
    }

    /** Forgets matched routes for a new iteration of [scenario]; unknown requests stay recorded. */
    fun startIteration(scenario: BaselineScenario) {
        synchronized(lock) {
            check(started) { "Call start() before startIteration()" }
            activeScenario = scenario
            activeRoutes = routesFor(scenario)
            matchedCounts.clear()
        }
    }

    fun verify(): BaselineVerification = synchronized(lock) {
        BaselineVerification(
            scenario = activeScenario,
            matchedRequests = matchedCounts.values.sum(),
            matchedRoutes = matchedCounts
                .filterValues { it > 0 }
                .entries
                .associate { (route, count) -> route.description to count },
            unknownRequests = unknownRequests.toList(),
            missingRequiredRoutes = activeRoutes
                .filter { it.required && (matchedCounts[it] ?: 0) < 1 }
                .map { it.description },
        )
    }

    override fun close() {
        synchronized(lock) {
            if (!started) return
            started = false
            server.close()
        }
    }

    private fun routeDispatcher() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = synchronized(lock) {
            val route = activeRoutes.firstOrNull { it.matches(request) }
            if (route == null) {
                unknownRequests += BaselineRequest.from(request)
                return textResponse(HTTP_NOT_FOUND, JSON, """{"error":"unknown baseline route"}""")
            }
            matchedCounts[route] = (matchedCounts[route] ?: 0) + 1
            route.respond(request)
        }
    }

    /**
     * The route table. Every request one of the journeys makes is listed here; a request that is
     * not listed is a violation to be added as a route, never a reason to loosen verification.
     * Routes with `required = true` must be hit for an iteration to count as having run.
     */
    @Suppress("LongMethod")
    private fun routesFor(scenario: BaselineScenario): List<BaselineMockRoute> {
        val routes = mutableListOf<BaselineMockRoute>()
        fun route(
            path: String,
            method: String = "GET",
            required: Boolean = false,
            respond: (RecordedRequest) -> MockResponse,
        ) {
            routes += BaselineMockRoute(method, Regex(path), path, required, respond)
        }

        fun json(body: String): (RecordedRequest) -> MockResponse = { textResponse(HTTP_OK, JSON, body) }

        fun media(name: String, contentType: String): (RecordedRequest) -> MockResponse =
            { binaryResponse(contentType, fixtures.media(name)) }

        val home = scenario != BaselineScenario.Playback
        val browse = scenario == BaselineScenario.BrowseAndDetails
        val rail = scenario == BaselineScenario.Rail
        val playback = scenario == BaselineScenario.Playback

        route("/v1/watching/serials", required = home, respond = json(fixtures.json("empty")))
        route("/v1/watching/movies", respond = json(fixtures.json("empty")))
        route("/v1/watching/marktime", required = playback, respond = json(fixtures.json("ok")))
        route("/v1/watching/toggle", respond = json(fixtures.json("ok")))
        route("/v1/bookmarks", respond = json(fixtures.json("bookmarks")))
        route("/v1/bookmarks/\\d+", respond = json(fixtures.json("bookmark_items")))
        route("/v1/bookmarks/get-item-folders", respond = json(fixtures.json("empty_bookmark_folders")))
        route("/v1/collections", respond = json(fixtures.json("collections")))
        route("/v1/genres", respond = json(fixtures.json("genres")))
        route("/v1/items/hot", required = home, respond = json(fixtures.json("items")))
        route("/v1/items/fresh", required = home, respond = json(fixtures.json("items")))
        route("/v1/items/popular", respond = json(fixtures.json("items")))
        route("/v1/items", required = rail, respond = json(fixtures.json("items")))
        route("/v1/items/search", respond = json(fixtures.json("items")))
        route("/v1/history", required = home, respond = json(fixtures.json("history")))
        route("/v1/device/info", respond = json(fixtures.json("device_info")))
        route("/v1/device/notify", method = "POST", respond = json(fixtures.json("ok")))
        route("/v1/items/similar", required = browse, respond = json(fixtures.json("similar")))
        route("/v1/items/\\d+", required = browse || playback) { request ->
            val id = request.url.encodedPath.substringAfterLast('/')
            textResponse(HTTP_OK, JSON, fixtures.json("item").replace(ID_PLACEHOLDER, id))
        }
        route("/github/repos/[^/]+/[^/]+/releases/latest", required = home, respond = json(fixtures.json("github_release")))
        route("/dns-query", method = "POST", required = home, respond = ::dnsResponse)
        route("/oauth2/device", method = "POST", respond = json(fixtures.json("token")))
        route("/media/(poster|wide)\\.jpg", required = browse) { request ->
            binaryResponse(JPEG, fixtures.media(request.url.encodedPath.substringAfterLast('/')))
        }
        route("/media/stream\\.m3u8", required = playback, respond = media("stream.m3u8", HLS_PLAYLIST))
        route("/media/seg\\d+\\.ts", required = playback) { request ->
            binaryResponse(MPEG_TS, fixtures.media(request.url.encodedPath.substringAfterLast('/')))
        }
        return routes
    }

    /**
     * Answers an RFC 1035 query carried in a DNS-over-HTTPS POST body. [BASELINE_HOSTS], also as the
     * `_<port>._https.<host>` name of an HTTPS-record query, resolve to 127.0.0.1 for type A and to an
     * empty NOERROR for any other type; every other name is NXDOMAIN
     * and recorded as an unknown `DNS` request. Called inside the dispatcher's lock.
     */
    private fun dnsResponse(request: RecordedRequest): MockResponse {
        val query = request.body?.toByteArray()?.takeIf { it.size > DNS_HEADER_LENGTH }
            ?: return textResponse(HTTP_BAD_REQUEST, JSON, """{"error":"malformed DNS query"}""")
        var offset = DNS_HEADER_LENGTH
        val labels = mutableListOf<String>()
        while (offset < query.size && query[offset].toInt() != 0) {
            val length = query[offset].toInt() and BYTE_MASK
            labels += String(query, offset + 1, length, Charsets.US_ASCII)
            offset += 1 + length
        }
        val name = labels.joinToString(".").lowercase()
        val questionEnd = offset + 1 + DNS_QUESTION_TAIL_LENGTH
        val queryType = ((query[offset + 1].toInt() and BYTE_MASK) shl BYTE_BITS) or
            (query[offset + 2].toInt() and BYTE_MASK)
        val known = name.replace(HTTPS_RR_PREFIX, "") in BASELINE_HOSTS
        if (!known) unknownRequests += BaselineRequest("DNS", name)
        val answers = if (known && queryType == DNS_TYPE_A) 1 else 0
        val flags = if (known) DNS_FLAGS_NOERROR else DNS_FLAGS_NXDOMAIN
        val body = Buffer()
            .write(query, 0, 2)
            .writeShort(flags)
            .writeShort(1)
            .writeShort(answers)
            .writeShort(0)
            .writeShort(0)
            .write(query, DNS_HEADER_LENGTH, questionEnd - DNS_HEADER_LENGTH)
        if (answers == 1) {
            body.writeShort(DNS_NAME_POINTER)
                .writeShort(DNS_TYPE_A)
                .writeShort(DNS_CLASS_IN)
                .writeInt(DNS_TTL_SECONDS)
                .writeShort(IPV4_LENGTH)
                .write(byteArrayOf(IPV4_LOOPBACK_FIRST, 0, 0, 1))
        }
        return binaryResponse(DNS_MESSAGE, body.readByteArray())
    }

    private fun textResponse(code: Int, contentType: String, body: String): MockResponse =
        MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", contentType)
            .body(body)
            .build()

    private fun binaryResponse(contentType: String, bytes: ByteArray): MockResponse =
        MockResponse.Builder()
            .code(HTTP_OK)
            .addHeader("Content-Type", contentType)
            .body(Buffer().write(bytes))
            .build()

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val HTTP_OK = 200
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_NOT_FOUND = 404
        const val ID_PLACEHOLDER = "{{ID}}"
        const val JSON = "application/json; charset=utf-8"
        const val JPEG = "image/jpeg"
        const val HLS_PLAYLIST = "application/vnd.apple.mpegurl"
        const val MPEG_TS = "video/mp2t"
        const val DNS_MESSAGE = "application/dns-message"
        const val DNS_HEADER_LENGTH = 12
        const val DNS_QUESTION_TAIL_LENGTH = 4
        const val DNS_FLAGS_NOERROR = 0x8180
        const val DNS_FLAGS_NXDOMAIN = 0x8183
        const val DNS_NAME_POINTER = 0xC00C
        const val DNS_TYPE_A = 1
        const val DNS_CLASS_IN = 1
        const val DNS_TTL_SECONDS = 60
        const val IPV4_LENGTH = 4
        const val IPV4_LOOPBACK_FIRST: Byte = 127
        const val BYTE_MASK = 0xFF
        const val BYTE_BITS = 8
        val BASELINE_HOSTS = setOf("api.baseline.test", "dns.baseline.test")

        /** The HTTPS-record name (RFC 9460) the app asks for next to the address of a host on a non-default port. */
        val HTTPS_RR_PREFIX = Regex("^_\\d+\\._https\\.")
    }
}

enum class BaselineScenario {
    Startup,
    BrowseAndDetails,
    Rail,
    Playback,
}

class BaselineMockRoute(
    val method: String,
    val path: Regex,
    val pathText: String,
    val required: Boolean,
    val respond: (RecordedRequest) -> MockResponse,
) {
    val description: String
        get() = "$method $pathText"

    fun matches(request: RecordedRequest): Boolean =
        method.equals(request.method, ignoreCase = true) &&
            path.matches(request.url.encodedPath.trimEnd('/').ifEmpty { "/" })
}

data class BaselineRequest(
    val method: String,
    val path: String,
) {
    companion object {
        fun from(request: RecordedRequest): BaselineRequest =
            BaselineRequest(request.method, request.url.encodedPath)
    }
}

data class BaselineVerification(
    val scenario: BaselineScenario,
    val matchedRequests: Int,
    val matchedRoutes: Map<String, Int>,
    val unknownRequests: List<BaselineRequest>,
    val missingRequiredRoutes: List<String>,
) {
    val isSuccessful: Boolean
        get() = unknownRequests.isEmpty() && missingRequiredRoutes.isEmpty()
}

/**
 * Bodies served by the mock: JSON fixtures with `{{MOCK}}` already replaced by the mock origin,
 * and the binary media. Fixtures carry no real image, media, trailer or metadata-provider URL.
 */
class BaselineFixtures(
    private val jsonBodies: Map<String, String>,
    private val mediaReader: (String) -> ByteArray,
    private val origin: String,
) {
    fun json(name: String): String = jsonBodies.getValue(name)

    fun media(name: String): ByteArray = mediaReader(name)

    fun validate() {
        jsonBodies.forEach { (name, body) ->
            val stray = REMOTE_URL.findAll(body).map { it.value }.firstOrNull { !it.startsWith(origin) }
            check(stray == null) { "Baseline fixture $name holds a URL outside the mock origin: $stray" }
            val key = BANNED_KEY.find(body)?.value
            check(key == null) { "Baseline fixture $name carries a banned key: $key" }
        }
    }

    companion object {
        private val NAMES = listOf(
            "items", "item", "similar", "collections", "bookmarks", "bookmark_items",
            "empty_bookmark_folders", "genres", "empty", "token", "history", "ok", "device_info", "github_release",
        )
        private val REMOTE_URL = Regex("(?i)https?://[^\"\\s]*")
        private val BANNED_KEY = Regex("\"(imdb|trailer|subtitles|seasons)\"")

        fun from(context: Context, origin: String): BaselineFixtures {
            fun read(name: String): String =
                context.assets.open("baseline/$name.json").bufferedReader().use { it.readText() }
                    .replace("{{MOCK}}", origin)

            return BaselineFixtures(
                jsonBodies = NAMES.associateWith(::read),
                mediaReader = { name -> context.assets.open("baseline/media/$name").use { it.readBytes() } },
                origin = origin,
            ).also(BaselineFixtures::validate)
        }
    }
}

/** Fails an iteration unless the target reported an empty egress journal and the mock saw a clean run. */
object JourneyCheck {

    /**
     * Fails unless nothing reached the mock or the network blocker after a journey's last iteration:
     * the journal is empty and no unknown route was hit. Required routes are not asked for, no
     * journey is running.
     */
    fun checkLate(verifyBroadcastOutput: String, verification: BaselineVerification) {
        val journalClean = verifyBroadcastOutput.contains("result=-1")
        check(journalClean && verification.unknownRequests.isEmpty()) {
            "Late requests after ${verification.scenario}: journal=${verifyBroadcastOutput.trim()}, " +
                "unknown=${verification.unknownRequests}"
        }
    }

    fun check(verifyBroadcastOutput: String, verification: BaselineVerification) {
        val journalClean = verifyBroadcastOutput.contains("result=-1")
        check(journalClean && verification.isSuccessful) {
            buildString {
                append("Baseline iteration failed for ")
                append(verification.scenario)
                append(": journal=")
                append(verifyBroadcastOutput.trim())
                append(", unknown=")
                append(verification.unknownRequests)
                append(", missing=")
                append(verification.missingRequiredRoutes)
                append(", matched=")
                append(verification.matchedRoutes)
            }
        }
    }
}
