package com.kino.puber.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.http.headersOf
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApiRetryPolicyTest {

    private val attempts = AtomicInteger()

    @Test
    fun aFailedToggleIsIssuedOnceBecauseRepeatingItWouldFlipTheMarkBack() = runTest {
        val client = client(HttpStatusCode.InternalServerError)

        client.get("$BASE_URL/watching/toggle?id=42")

        assertEquals(1, attempts.get())
    }

    @Test
    fun aFailedMarkTimeIsIssuedOnceBecauseRepeatingItWouldMoveThePositionAgain() = runTest {
        val client = client(HttpStatusCode.InternalServerError)

        client.get("$BASE_URL/watching/marktime?id=42&time=10")

        assertEquals(1, attempts.get())
    }

    @Test
    fun aFailedWatchlistToggleIsIssuedOnce() = runTest {
        val client = client(HttpStatusCode.InternalServerError)

        client.get("$BASE_URL/watching/togglewatchlist?id=42")

        assertEquals(1, attempts.get())
    }

    @Test
    fun aPlainContentReadIsRetriedOnAServerError() = runTest {
        val client = client(HttpStatusCode.InternalServerError)

        client.get("$BASE_URL/items?type=movie")

        assertEquals(MAX_RETRIES + 1, attempts.get())
    }

    @Test
    fun aPostIsNeverRetriedBecauseA500IsNoPromiseTheWriteDidNotLand() = runTest {
        val client = client(HttpStatusCode.InternalServerError)

        client.post("$BASE_URL/bookmarks/toggle")

        assertEquals(1, attempts.get())
    }

    @Test
    fun aSuccessfulReadIsIssuedOnce() = runTest {
        val client = client(HttpStatusCode.OK)

        client.get("$BASE_URL/items")

        assertEquals(1, attempts.get())
    }

    private fun client(status: HttpStatusCode): HttpClient {
        val engine = MockEngine {
            attempts.incrementAndGet()
            respond(
                content = "{}",
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return HttpClient(engine) {
            expectSuccess = false
            install(HttpRequestRetry) { retryOnlyRepeatableRequests(MAX_RETRIES) }
        }
    }

    private companion object {
        const val BASE_URL = "https://api.example.test/v1"
        const val MAX_RETRIES = 3
    }
}
