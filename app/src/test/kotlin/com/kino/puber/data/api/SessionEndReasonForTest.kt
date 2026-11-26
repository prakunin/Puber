package com.kino.puber.data.api

import com.kino.puber.core.session.SessionEndReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class SessionEndReasonForTest {

    @ParameterizedTest
    @MethodSource("refusals")
    fun mapsOnlyListedRefusalsToAReason(status: Int, message: String?, expected: SessionEndReason?) {
        assertEquals(expected, sessionEndReasonFor(status, message))
    }

    companion object {
        @JvmStatic
        fun refusals() = listOf(
            Arguments.of(401, null, SessionEndReason.Expired),
            Arguments.of(401, "anything", SessionEndReason.Expired),
            Arguments.of(403, "user_banned", SessionEndReason.Blocked),
            Arguments.of(403, "нет доступа", null),
            Arguments.of(403, null, null),
            Arguments.of(403, "USER_BANNED", null),
            Arguments.of(404, "user_banned", null),
            Arguments.of(500, null, null),
        )
    }
}
