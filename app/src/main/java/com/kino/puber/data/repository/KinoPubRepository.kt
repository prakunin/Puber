package com.kino.puber.data.repository

import com.kino.puber.core.coroutine.runCatchingCancellable
import com.kino.puber.core.error.ApiError
import com.kino.puber.core.logger.log
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.auth.DeviceCodeResponse
import com.kino.puber.data.api.auth.TokenResponse
import com.kino.puber.domain.model.AuthState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

class KinoPubRepository(
    private val client: KinoPubApiClient,
    private val cryptoPreferenceRepository: ICryptoPreferenceRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : IKinoPubRepository {

    override fun isAuthenticated(): Boolean = client.isAuthenticated()

    override fun getAuthState(): Flow<AuthState> = channelFlow {
        if (client.isAuthenticated()) {
            send(AuthState.Success)
            return@channelFlow
        }

        while (true) {
            val codeResult = client.getDeviceLoginCode().first()
            val deviceCode = codeResult.getOrThrow().deviceCode
            send(AuthState.Code(deviceCode.userCode, deviceCode.verificationUri, deviceCode.expiresIn))

            val token = pollUntilExpired(deviceCode)
            if (token != null) {
                cryptoPreferenceRepository.saveAccessToken(token.accessToken)
                cryptoPreferenceRepository.saveRefreshToken(token.refreshToken)
                send(AuthState.Success)
                return@channelFlow
            }
            // Code expired → loop restarts with new device code
        }
    }

    /**
     * Polls every `interval` until a token arrives, the client deadline passes, or the server
     * reports the code expired. Null means the code is spent. A pass always waits at least one
     * interval, so renewals are never closer together than that.
     */
    private suspend fun pollUntilExpired(deviceCode: DeviceCodeResponse): TokenResponse? {
        val intervalMillis = deviceCode.interval.coerceAtLeast(1) * 1000L
        val deadline = nowMillis() + deviceCode.expiresIn * 1000L
        var waited = false
        while (nowMillis() < deadline) {
            delay(intervalMillis)
            waited = true
            val token = try {
                pollForToken(deviceCode)
            } catch (_: CodeExpiredException) {
                return null
            }
            if (token != null) return token
        }
        if (!waited) delay(intervalMillis)
        return null
    }

    /**
     * One poll of the device flow. Null means "ask again": either the user has not confirmed the
     * code yet, or the attempt never reached the server.
     *
     * An expired code is signalled with [CodeExpiredException] so the caller asks for a fresh one.
     * Any other refusal the OAuth protocol pronounced, such as the user denying the code, is final
     * and is thrown so the screen says so.
     * Cancellation stays cancellation: it must stop the poll rather than count as a failed attempt.
     */
    private suspend fun pollForToken(deviceCode: DeviceCodeResponse): TokenResponse? {
        val polled = runCatchingCancellable { client.getDeviceLoginStatus(deviceCode).firstOrNull() }
        val emission = polled.onFailure(::logPollFailure).getOrNull() ?: return null
        val failure = emission.exceptionOrNull() ?: return emission.getOrNull()?.token

        if (failure is ApiError.OAuth && failure.isCodeExpired) throw CodeExpiredException()
        if (failure is ApiError.OAuth && !failure.isAuthorizationPending) throw failure

        logPollFailure(failure)
        return null
    }

    private fun logPollFailure(error: Throwable) {
        log(error, "Device login poll failed")
    }

    private class CodeExpiredException : RuntimeException()
}
