package com.kino.puber.data.api

import com.kino.puber.core.coroutine.runCatchingCancellable
import com.kino.puber.core.error.ApiError
import com.kino.puber.data.api.auth.OAuthError
import com.kino.puber.data.api.network.NoConnectivityException
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val errorBodyJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Turns a refusal into a failure before anything tries to read it as the success type.
 *
 * Without this a 4xx/5xx JSON body was handed straight to the response deserializer, which happily
 * produced an empty instance of whatever the call expected — so a server saying "no" arrived at the
 * screen as "there is nothing here".
 *
 * The body is read only on the failing branch: reading it consumes the content channel, and the
 * success path still has to deserialize it.
 */
@PublishedApi
internal suspend fun HttpResponse.requireSuccessful(): HttpResponse {
    if (status.isSuccess()) return this
    val body = runCatchingCancellable { bodyAsText() }.getOrNull()
    throw httpFailure(status.value, body)
}

/**
 * The API's own wording for a refusal, when the body carries one.
 *
 * The OAuth shape is deliberately not recognised here: the content API answers with the same
 * `{"error": ...}` envelope holding human text, so parsing it as an OAuth code would turn every
 * ordinary 404 into a protocol error. Only the OAuth endpoints look for that, through
 * [parseOAuthError].
 */
internal fun httpFailure(statusCode: Int, body: String?): ApiError {
    val serverMessage = body?.let(::parseApiErrorMessage)
    return when (statusCode) {
        HttpStatusCode.Unauthorized.value, HttpStatusCode.Forbidden.value ->
            ApiError.Unauthorized(serverMessage)

        else -> ApiError.Http(statusCode, serverMessage)
    }
}

/**
 * The OAuth endpoints answer with `{"error": "...", "error_description": "..."}`, and the device
 * flow depends on telling those codes apart — `authorization_pending` is the normal answer while
 * the user is still typing the code in.
 */
internal fun parseOAuthError(body: String): ApiError.OAuth? {
    if (!body.contains("\"error\"")) return null
    return runCatching {
        errorBodyJson.decodeFromString<OAuthError>(body)
    }.getOrNull()?.let { parsed -> ApiError.OAuth(parsed.error, parsed.errorDescription) }
}

private fun parseApiErrorMessage(body: String): String? {
    if (!body.contains("\"error\"")) return null
    return runCatching {
        errorBodyJson.decodeFromString<ApiErrorBody>(body).error
    }.getOrNull()
}

@Serializable
private data class ApiErrorBody(val error: String? = null)

/**
 * Names a raw transport or decoding failure so that everything above the client can branch on the
 * type instead of on the text of a message.
 *
 * A failure it cannot name is passed through untouched: inventing a category for it would be a
 * worse lie than the generic message it ends up with.
 */
@PublishedApi
internal fun Throwable.asApiFailure(): Throwable = when (this) {
    is ApiError -> this
    is NoConnectivityException -> ApiError.Offline(this)
    is SerializationException, is ContentConvertException, is NoTransformationFoundException ->
        ApiError.Serialization(this)

    is IOException -> ApiError.Network(this)
    else -> this
}
