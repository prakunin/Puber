package com.kino.puber.core.error

import com.kino.puber.R
import com.kino.puber.util.FakeResourceProvider
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class DefaultErrorHandlerTest {

    private val subject = DefaultErrorHandler(FakeResourceProvider())

    @Test
    fun map_usesLocalizedGenericCopyInsteadOfThrowableTransportDetails() {
        val transportDetails =
            "OAuth response decoding failed: https://api.example.test/oauth?access_token=secret"

        val error = subject.map(IllegalStateException(transportDetails))

        assertEquals("string_${R.string.error_generic}", error.message)
        assertFalse(error.message.contains(transportDetails))
    }

    @Test
    fun map_tellsTheUserTheDeviceIsOfflineRatherThanThatSomethingWentWrong() {
        val error = subject.map(ApiError.Offline(IOException("no network")))

        assertEquals("string_${R.string.error_no_connection}", error.message)
        assertEquals("Offline", error.code)
    }

    @Test
    fun map_separatesAServerThatNeverAnsweredFromOneThatRefused() {
        val network = subject.map(ApiError.Network(IOException("connect timed out")))
        val http = subject.map(ApiError.Http(statusCode = 503, serverMessage = "maintenance"))

        assertEquals("string_${R.string.error_network}", network.message)
        assertEquals("string_${R.string.error_server_response}_503", http.message)
        assertNotEquals(network.message, http.message)
        assertEquals("Http503", http.code)
    }

    @Test
    fun map_neverRepeatsTheServersOwnWordingBackAtTheUser() {
        val error = subject.map(ApiError.Http(statusCode = 500, serverMessage = "token=secret expired"))

        assertFalse(error.message.contains("secret"))
    }

    @Test
    fun map_asksForAFreshSignInWhenTheSessionIsGone() {
        val error = subject.map(ApiError.Unauthorized())

        assertEquals("string_${R.string.error_unauthorized}", error.message)
        assertEquals("Unauthorized", error.code)
    }

    @Test
    fun map_namesAMalformedAnswerAsSuch() {
        val error = subject.map(ApiError.Serialization(IllegalStateException("bad json")))

        assertEquals("string_${R.string.error_malformed_response}", error.message)
        assertEquals("Serialization", error.code)
    }

    @Test
    fun map_tellsWaitingForConfirmationApartFromAFailedLogin() {
        val pending = subject.map(ApiError.OAuth(ApiError.OAuth.AUTHORIZATION_PENDING))
        val denied = subject.map(ApiError.OAuth("access_denied"))

        assertEquals("string_${R.string.error_auth_pending}", pending.message)
        assertEquals("string_${R.string.error_auth_failed}", denied.message)
        assertEquals("OAuth:authorization_pending", pending.code)
        assertEquals("OAuth:access_denied", denied.code)
    }
}
