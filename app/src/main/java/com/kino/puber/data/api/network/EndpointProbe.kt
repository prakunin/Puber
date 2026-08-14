package com.kino.puber.data.api.network

import com.kino.puber.data.api.config.ApiEndpointPreset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** Answers whether an endpoint is serving the KinoPub API right now. */
internal fun interface EndpointProbe {
    fun isReachable(endpoint: ApiEndpointPreset): Boolean
}

/**
 * Asks the endpoint for a real catalogue page.
 *
 * The body is parsed rather than the status code trusted on its own: a captive portal, a parked
 * domain or an ISP block answers 200 with HTML just as readily as the API answers 200 with JSON, and
 * only the shape of the payload separates them. That is also why this cannot be a HEAD request.
 */
internal class HttpEndpointProbe(okHttpClient: OkHttpClient) : EndpointProbe {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = okHttpClient.newBuilder()
        .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override fun isReachable(endpoint: ApiEndpointPreset): Boolean {
        val request = Request.Builder()
            .url("${endpoint.mainBaseUrl}items/fresh?type=movie")
            .header("Accept", "application/json")
            .get()
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                response.code in MIN_REACHABLE_STATUS..MAX_REACHABLE_STATUS &&
                    response.code != HTTP_NOT_FOUND &&
                    response.isKinoPubApiResponse()
            }
        }.getOrDefault(false)
    }

    private fun Response.isKinoPubApiResponse(): Boolean {
        val contentType = header("Content-Type").orEmpty()
        if (!contentType.contains(JSON_CONTENT_TYPE, ignoreCase = true)) return false

        val root = runCatching {
            json.parseToJsonElement(body.string()).jsonObject
        }.getOrNull() ?: return false

        return root.hasPaginatedItems() || root.hasApiError()
    }

    private fun JsonObject.hasPaginatedItems(): Boolean {
        return containsKey(API_ITEMS_FIELD) && containsKey(API_PAGINATION_FIELD)
    }

    private fun JsonObject.hasApiError(): Boolean {
        return containsKey(API_STATUS_FIELD) &&
            (containsKey(API_ERROR_FIELD) || containsKey(API_MESSAGE_FIELD))
    }

    private companion object {
        const val PROBE_TIMEOUT_SECONDS = 5L
        const val MIN_REACHABLE_STATUS = 200
        const val MAX_REACHABLE_STATUS = 499
        const val HTTP_NOT_FOUND = 404
        const val JSON_CONTENT_TYPE = "application/json"
        const val API_ITEMS_FIELD = "items"
        const val API_PAGINATION_FIELD = "pagination"
        const val API_STATUS_FIELD = "status"
        const val API_ERROR_FIELD = "error"
        const val API_MESSAGE_FIELD = "message"
    }
}
