package com.kino.puber.core.error

import com.kino.puber.R
import com.kino.puber.core.system.ResourceProvider

internal class DefaultErrorHandler(
    private val resources: ResourceProvider,
) : ErrorHandler {
    override fun proceed(action: ((ErrorEntity) -> Unit)?): (Throwable) -> Unit {
        return { e -> proceedInvoke(e, action) }
    }

    override fun proceedInvoke(
        e: Throwable, action: ((ErrorEntity) -> Unit)?
    ) {
        action?.invoke(map(e))
    }

    /**
     * Only the failure's type reaches the screen. The throwable's own message is deliberately left
     * out of every branch: it is transport text, sometimes carrying a URL with a token in it, and
     * the app is Russian-speaking while the API is not.
     */
    override fun map(e: Throwable): ErrorEntity = when (e) {
        is ApiError.Offline -> ErrorEntity(
            message = resources.getString(R.string.error_no_connection),
            code = CODE_OFFLINE,
        )

        is ApiError.Network -> ErrorEntity(
            message = resources.getString(R.string.error_network),
            code = CODE_NETWORK,
        )

        is ApiError.Unauthorized -> ErrorEntity(
            message = resources.getString(R.string.error_unauthorized),
            code = CODE_UNAUTHORIZED,
        )

        is ApiError.Http -> ErrorEntity(
            message = resources.getString(R.string.error_server_response, e.statusCode),
            code = "$CODE_HTTP${e.statusCode}",
        )

        is ApiError.Serialization -> ErrorEntity(
            message = resources.getString(R.string.error_malformed_response),
            code = CODE_SERIALIZATION,
        )

        is ApiError.OAuth -> mapOAuth(e)

        else -> ErrorEntity(
            message = resources.getString(R.string.error_generic),
            code = CODE_UNKNOWN,
        )
    }

    private fun mapOAuth(e: ApiError.OAuth): ErrorEntity = if (e.isAuthorizationPending) {
        ErrorEntity(
            message = resources.getString(R.string.error_auth_pending),
            code = "$CODE_OAUTH${e.error}",
        )
    } else {
        ErrorEntity(
            message = resources.getString(R.string.error_auth_failed),
            code = "$CODE_OAUTH${e.error}",
        )
    }

    private companion object {
        const val CODE_UNKNOWN = "Unknown"
        const val CODE_OFFLINE = "Offline"
        const val CODE_NETWORK = "Network"
        const val CODE_UNAUTHORIZED = "Unauthorized"
        const val CODE_SERIALIZATION = "Serialization"
        const val CODE_HTTP = "Http"
        const val CODE_OAUTH = "OAuth:"
    }
}
