package com.kino.puber.core.error

/**
 * Why a request failed, in the terms the rest of the app has to act on.
 *
 * Before this existed every failure travelled as a bare [Exception] with a human-readable message,
 * so the only way to tell "the code is not confirmed yet" from "the code is dead" was to compare
 * that message against a string constant — a test that silently changes meaning whenever the text
 * does. The type is the contract now; the message is for logs only, and must never be shown to the
 * user: it can carry a URL or a token from the transport that produced it.
 */
sealed class ApiError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** The device has no usable network, so the request never left it. Says nothing about the host. */
    class Offline(cause: Throwable? = null) : ApiError("No network connectivity", cause)

    /** The request left the device and the transport gave out: DNS, connect, socket or timeout. */
    class Network(cause: Throwable? = null) : ApiError("Network request failed", cause)

    /**
     * The server answered and the answer was a refusal. [serverMessage] is the API's own wording
     * when the error body could be parsed, which is the only place that text is ever available.
     */
    class Http(
        val statusCode: Int,
        val serverMessage: String? = null,
        cause: Throwable? = null,
    ) : ApiError("HTTP $statusCode" + serverMessage?.let { ": $it" }.orEmpty(), cause)

    /**
     * Credentials are missing, rejected, or the session ended. Raised both by a 401/403 answer and
     * by a refresh that could not renew the session, which is what "sign in again" is made of.
     */
    class Unauthorized(
        val serverMessage: String? = null,
        cause: Throwable? = null,
    ) : ApiError("Unauthorized" + serverMessage?.let { ": $it" }.orEmpty(), cause)

    /** The body arrived but is not the shape the model claims. */
    class Serialization(cause: Throwable? = null) : ApiError("Malformed response", cause)

    /**
     * An OAuth device-flow refusal, carrying the protocol's own error code so callers can branch on
     * it. `authorization_pending` is the polling loop's normal answer; every other code is terminal
     * and means the loop has to stop instead of running out the clock.
     */
    class OAuth(
        val error: String,
        val description: String? = null,
    ) : ApiError("OAuth error: $error" + description?.let { " ($it)" }.orEmpty()) {

        val isAuthorizationPending: Boolean get() = error.equals(AUTHORIZATION_PENDING, ignoreCase = true)

        companion object {
            const val AUTHORIZATION_PENDING = "authorization_pending"
        }
    }
}
