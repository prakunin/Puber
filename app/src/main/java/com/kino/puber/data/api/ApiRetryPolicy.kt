package com.kino.puber.data.api

import com.kino.puber.data.api.network.NoConnectivityException
import io.ktor.client.plugins.HttpRequestRetryConfig
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpMethod
import java.io.IOException

/**
 * Endpoints that change account state behind a GET.
 *
 * KinoPub models these as reads, so the HTTP verb cannot be trusted to say whether repeating a
 * request is safe: a retried `watching/toggle` flips the watched mark back, and a retried
 * `marktime` rewrites a position the user never asked to move. Listed by path because that is the
 * only thing that tells them apart from the ordinary catalogue GETs around them.
 */
internal val MUTATING_GET_PATHS = setOf(
    "watching/toggle",
    "watching/togglewatchlist",
    "watching/marktime",
)

private const val FIRST_SERVER_ERROR_STATUS = 500

/**
 * Retries only what can be repeated without changing anything.
 *
 * POSTs are never retried: every one of them writes, and a 500 is no promise the write did not
 * land. Transport failures are retried on the same terms as a 5xx, because a dropped connection
 * says even less about whether the server acted on the request.
 */
internal fun HttpRequestRetryConfig.retryOnlyRepeatableRequests(maxRetries: Int) {
    this.maxRetries = maxRetries
    retryIf { request, response ->
        request.isSafeToRepeat() && response.status.value >= FIRST_SERVER_ERROR_STATUS
    }
    retryOnExceptionIf { request, cause ->
        request.isSafeToRepeat() && cause.isRepeatableTransportFailure()
    }
    exponentialDelay()
}

/**
 * The two halves of the plugin hand out different request types — a built request when a response
 * came back, a builder when the transport failed — so the decision itself is kept apart from them.
 */
private fun isSafeToRepeat(method: HttpMethod, encodedPath: String): Boolean {
    if (method != HttpMethod.Get) return false
    val path = encodedPath.trimEnd('/')
    return MUTATING_GET_PATHS.none { mutating -> path.endsWith("/$mutating") }
}

internal fun HttpRequest.isSafeToRepeat(): Boolean = isSafeToRepeat(method, url.encodedPath)

internal fun HttpRequestBuilder.isSafeToRepeat(): Boolean =
    isSafeToRepeat(method, url.encodedPathSegments.joinToString("/"))

/**
 * The transport gave out before an answer arrived. [NoConnectivityException] is excluded because it
 * is raised before the request leaves the device, and retrying it only burns the backoff.
 */
private fun Throwable.isRepeatableTransportFailure(): Boolean =
    this is IOException && this !is NoConnectivityException
