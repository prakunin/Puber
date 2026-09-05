package com.kino.puber.data.repository

import com.kino.puber.core.error.ApiError
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.auth.DeviceCodeResponse
import com.kino.puber.data.api.auth.DeviceFlowResult
import com.kino.puber.data.api.auth.TokenResponse
import com.kino.puber.domain.model.AuthState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KinoPubRepositoryAuthStateTest {

    private val client = mockk<KinoPubApiClient>()
    private val preferences = mockk<ICryptoPreferenceRepository>(relaxed = true)
    private val subject = KinoPubRepository(client, preferences)

    @Test
    fun aTerminalOAuthRefusalStopsThePollInsteadOfRunningOutTheCode() = runTest {
        givenSignedOutWithACode()
        every { client.getDeviceLoginStatus(any()) } returns flowOf(
            Result.failure(ApiError.OAuth("expired_token")),
        )

        val error = assertThrows<ApiError.OAuth> { subject.getAuthState().toList() }

        assertEquals("expired_token", error.error)
    }

    @Test
    fun aPollThatNeverReachedTheServerKeepsWaitingForTheUser() = runTest {
        givenSignedOutWithACode()
        var attempt = 0
        every { client.getDeviceLoginStatus(any()) } answers {
            attempt++
            when (attempt) {
                // A transport failure says nothing about the code, so the loop has to survive it.
                1 -> flowOf(Result.failure(ApiError.Network(IOException("dropped"))))
                // The device flow reports "not confirmed yet" by emitting nothing at all.
                2 -> emptyFlow()
                else -> flowOf(Result.success(DeviceFlowResult(DEVICE_CODE, TOKEN)))
            }
        }

        val states = subject.getAuthState().toList()

        assertEquals(3, attempt)
        assertInstanceOf(AuthState.Success::class.java, states.last())
        verify { preferences.saveAccessToken("access") }
        verify { preferences.saveRefreshToken("refresh") }
    }

    private fun givenSignedOutWithACode() {
        every { client.isAuthenticated() } returns false
        every { client.getDeviceLoginCode() } returns flowOf(
            Result.success(DeviceFlowResult(DEVICE_CODE, token = null)),
        )
    }

    private companion object {
        val DEVICE_CODE = DeviceCodeResponse(
            code = "device-code",
            userCode = "USER",
            verificationUri = "https://example.test/device",
            expiresIn = 600,
            interval = 5,
        )
        val TOKEN = TokenResponse(
            accessToken = "access",
            refreshToken = "refresh",
            expiresIn = 3600,
        )
    }
}
