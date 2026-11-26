package com.kino.puber.profile

import com.kino.puber.data.repository.ICryptoPreferenceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BaselineAuthSeedTest {

    private class InMemoryStore(
        var access: String?,
        var refresh: String? = null,
        var user: String? = null,
    ) : ICryptoPreferenceRepository {
        private var apiDomain: String? = null
        private var sessionEndReason: String? = null

        override fun saveAccessToken(token: String) { access = token }
        override fun getAccessToken(): String? = access
        override fun clearAccessToken() { access = "" }
        override fun saveRefreshToken(token: String) { refresh = token }
        override fun getRefreshToken(): String? = refresh
        override fun clearRefreshToken() { refresh = "" }
        override fun saveUsername(userName: String) { user = userName }
        override fun getUsername(): String? = user
        override fun clearUsername() { user = "" }
        override fun getAndroidId(): String? = "in-memory"
        override fun saveApiDomain(domain: String?) { apiDomain = domain }
        override fun getApiDomain(): String? = apiDomain
        override fun saveSessionEndReason(reason: String) { sessionEndReason = reason }
        override fun getSessionEndReason(): String? = sessionEndReason
        override fun clearSessionEndReason() { sessionEndReason = null }
    }

    @Test
    fun ensure_sealsSyntheticTokens_whenAccessTokenIsNullOrEmpty() {
        listOf(null, "").forEach { initial ->
            val store = InMemoryStore(access = initial)

            BaselineAuthSeed.ensure(store)

            assertEquals("baseline-access-token", store.getAccessToken(), "access from $initial")
            assertEquals("baseline-refresh-token", store.getRefreshToken(), "refresh from $initial")
            assertEquals("baseline-profile", store.getUsername(), "username from $initial")
        }
    }

    @Test
    fun ensure_keepsTheStoredTokens_whenAccessTokenIsPresent() {
        val store = InMemoryStore(access = "kept-access", refresh = "kept-refresh", user = "kept-user")

        BaselineAuthSeed.ensure(store)

        assertEquals("kept-access", store.getAccessToken())
        assertEquals("kept-refresh", store.getRefreshToken())
        assertEquals("kept-user", store.getUsername())
    }
}
