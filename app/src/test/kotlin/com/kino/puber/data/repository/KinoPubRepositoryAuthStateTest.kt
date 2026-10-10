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
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KinoPubRepositoryAuthStateTest {

    private val client = mockk<KinoPubApiClient>()
    private val preferences = mockk<ICryptoPreferenceRepository>(relaxed = true)
    private var now: () -> Long = { 0L }
    private val subject = KinoPubRepository(client, preferences) { now() }

    /** Runs on virtual time and lets the repository's deadline read the same clock as `delay`. */
    private fun test(block: suspend TestScope.() -> Unit) = runTest {
        now = { testScheduler.currentTime }
        block()
    }

    @Test
    fun aDeniedRefusalStillStopsThePoll() = test {
        givenSignedOutWithACode()
        every { client.getDeviceLoginStatus(any()) } returns flowOf(
            Result.failure(ApiError.OAuth("access_denied")),
        )

        val error = assertThrows<ApiError.OAuth> { subject.getAuthState().toList() }

        assertEquals("access_denied", error.error)
        verify(exactly = 1) { client.getDeviceLoginCode() }
    }

    @Test
    fun anExpiredCodeRefusalRenewsTheCodeInsteadOfThrowing() = test {
        givenSignedOutWithACode()
        every { client.getDeviceLoginStatus(any()) } returns flowOf(
            Result.failure(ApiError.OAuth("expired_token")),
        )

        val codes = subject.getAuthState().take(2).toList()

        assertEquals(2, codes.size)
        assertInstanceOf(AuthState.Code::class.java, codes[1])
    }

    @Test
    fun everyExpiredStringRenewsWithTheNewCode() = test {
        listOf("expired_token", "authorization_expired", "code_expired", "EXPIRED_Token").forEach { assertRenews(it) }
    }

    private suspend fun assertRenews(refusal: String) {
        every { client.isAuthenticated() } returns false
        var requests = 0
        every { client.getDeviceLoginCode() } answers {
            requests++
            flowOf(Result.success(DeviceFlowResult(DEVICE_CODE.copy(userCode = "USER$requests"), token = null)))
        }
        every { client.getDeviceLoginStatus(any()) } returns flowOf(Result.failure(ApiError.OAuth(refusal)))

        val codes = subject.getAuthState().take(2).toList().filterIsInstance<AuthState.Code>()

        assertEquals(listOf("USER1", "USER2"), codes.map { it.code })
    }

    @Test
    fun aPollPastTheDeadlineThatIsRefusedAsExpiredRenews() = test {
        every { client.isAuthenticated() } returns false
        val requestTimes = mutableListOf<Long>()
        every { client.getDeviceLoginCode() } answers {
            requestTimes += testScheduler.currentTime
            flowOf(Result.success(DeviceFlowResult(DEVICE_CODE.copy(expiresIn = 1, interval = 2), token = null)))
        }
        val pollTimes = mutableListOf<Long>()
        every { client.getDeviceLoginStatus(any()) } answers {
            pollTimes += testScheduler.currentTime
            flowOf(Result.failure(ApiError.OAuth("expired_token")))
        }

        val codes = subject.getAuthState().take(2).toList()

        assertEquals(2, codes.size)
        // The only poll lands at 2 s, a second after the 1 s deadline, and is refused as expired.
        assertEquals(listOf(2000L), pollTimes.take(1))
        assertEquals(listOf(0L, 2000L), requestTimes)
    }

    @Test
    fun codeRunningOutOnPendingAnswersRequestsANewCode() = test {
        every { client.isAuthenticated() } returns false
        val requestTimes = mutableListOf<Long>()
        every { client.getDeviceLoginCode() } answers {
            requestTimes += testScheduler.currentTime
            flowOf(Result.success(DeviceFlowResult(DEVICE_CODE.copy(expiresIn = 10, interval = 3), token = null)))
        }
        every { client.getDeviceLoginStatus(any()) } returns emptyFlow()

        val codes = subject.getAuthState().take(2).toList()

        assertEquals(2, codes.size)
        // Polls at 3, 6, 9 and 12 s; the 12 s round is the first one past the 10 s deadline.
        assertEquals(listOf(0L, 12_000L), requestTimes)
    }

    @Test
    fun anUnlistedRefusalStillStopsThePollInsteadOfRenewing() = test {
        givenSignedOutWithACode()
        every { client.getDeviceLoginStatus(any()) } returns flowOf(
            Result.failure(ApiError.OAuth("invalid_grant")),
        )

        val error = assertThrows<ApiError.OAuth> { subject.getAuthState().toList() }

        assertEquals("invalid_grant", error.error)
        verify(exactly = 1) { client.getDeviceLoginCode() }
    }

    @Test
    fun aCodeThatIsAlreadyExpiredStillWaitsAnIntervalBeforeRenewing() = test {
        assertRenewalGapAtLeast(expiresIn = 0, interval = 5, minGapMillis = 5000L)
    }

    @Test
    fun aZeroIntervalIsFlooredToOneSecond() = test {
        assertRenewalGapAtLeast(expiresIn = 0, interval = 0, minGapMillis = 1000L)
    }

    private suspend fun TestScope.assertRenewalGapAtLeast(expiresIn: Int, interval: Int, minGapMillis: Long) {
        every { client.isAuthenticated() } returns false
        val requestTimes = mutableListOf<Long>()
        every { client.getDeviceLoginCode() } answers {
            requestTimes += testScheduler.currentTime
            flowOf(
                Result.success(
                    DeviceFlowResult(DEVICE_CODE.copy(expiresIn = expiresIn, interval = interval), token = null),
                ),
            )
        }
        every { client.getDeviceLoginStatus(any()) } returns emptyFlow()

        subject.getAuthState().take(3).toList()

        assertEquals(3, requestTimes.size)
        requestTimes.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= minGapMillis) }
    }

    @Test
    fun aFailingNewCodeRequestSurfacesAndStopsTheLoop() = test {
        every { client.isAuthenticated() } returns false
        var requests = 0
        every { client.getDeviceLoginCode() } answers {
            requests++
            if (requests == 1) {
                flowOf(Result.success(DeviceFlowResult(DEVICE_CODE, token = null)))
            } else {
                flowOf(Result.failure(ApiError.Network(IOException("down"))))
            }
        }
        every { client.getDeviceLoginStatus(any()) } returns flowOf(
            Result.failure(ApiError.OAuth("expired_token")),
        )

        assertThrows<ApiError.Network> { subject.getAuthState().toList() }

        assertEquals(2, requests)
    }

    @Test
    fun renewalsAreNeverCloserThanTheInterval() = test {
        every { client.isAuthenticated() } returns false
        val requestTimes = mutableListOf<Long>()
        every { client.getDeviceLoginCode() } answers {
            requestTimes += testScheduler.currentTime
            flowOf(Result.success(DeviceFlowResult(DEVICE_CODE, token = null)))
        }
        every { client.getDeviceLoginStatus(any()) } returns flowOf(
            Result.failure(ApiError.OAuth("expired_token")),
        )

        subject.getAuthState().take(3).toList()

        assertEquals(3, requestTimes.size)
        requestTimes.zipWithNext().forEach { (a, b) ->
            assertTrue(b - a >= DEVICE_CODE.interval * 1000L)
        }
    }

    @Test
    fun afterRenewalPollsUseTheNewCode() = test {
        every { client.isAuthenticated() } returns false
        var requests = 0
        every { client.getDeviceLoginCode() } answers {
            requests++
            flowOf(Result.success(DeviceFlowResult(DEVICE_CODE.copy(code = "code$requests"), token = null)))
        }
        val polled = mutableListOf<String>()
        every { client.getDeviceLoginStatus(any()) } answers {
            polled += firstArg<DeviceCodeResponse>().code
            if (polled.size == 1) {
                flowOf(Result.failure(ApiError.OAuth("expired_token")))
            } else {
                flowOf(Result.success(DeviceFlowResult(DEVICE_CODE, TOKEN)))
            }
        }

        subject.getAuthState().toList()

        assertEquals(listOf("code1", "code2"), polled)
    }

    @Test
    fun aPollThatNeverReachedTheServerKeepsWaitingForTheUser() = test {
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
