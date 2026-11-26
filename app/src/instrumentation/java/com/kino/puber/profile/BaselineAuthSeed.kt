package com.kino.puber.profile

import android.content.Context
import com.kino.puber.data.repository.CryptoPreferenceRepository
import com.kino.puber.data.repository.ICryptoPreferenceRepository

/**
 * Seals synthetic tokens into the real token store, so the app runs the production read path
 * (keystore decrypt included) without a paired account. Nothing here leaves the device.
 */
internal object BaselineAuthSeed {

    const val ACCESS_TOKEN = "baseline-access-token"
    const val REFRESH_TOKEN = "baseline-refresh-token"
    const val USERNAME = "baseline-profile"

    fun ensure(context: Context) = ensure(CryptoPreferenceRepository(context))

    /**
     * Null or empty, not a key check: `clearAccessToken` seals an empty string, which reads back
     * as empty rather than null.
     */
    internal fun ensure(store: ICryptoPreferenceRepository) {
        if (!store.getAccessToken().isNullOrEmpty()) return
        store.saveAccessToken(ACCESS_TOKEN)
        store.saveRefreshToken(REFRESH_TOKEN)
        store.saveUsername(USERNAME)
    }
}
