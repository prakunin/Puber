package com.kino.puber.profile

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.kino.puber.data.repository.CryptoPreferenceRepository
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Makes the auth state of a profile-generation run observable: the release build plants no log tree
 * and the app is not debuggable, so nothing else says why the tokens went away. Logs to the tag
 * [TAG]: which token entries were written or removed (present means a non-empty stored value), and every app log line about tokens,
 * sessions or the keystore (already sanitized by the app's logger). Token values are never read here.
 */
internal object AuthEventLog {
    const val TAG = "PuberAuth"

    private val installed = AtomicBoolean(false)
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val prefs = context.applicationContext.getSharedPreferences(
            CryptoPreferenceRepository.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        Log.i(TAG, "process ${android.os.Process.myPid()} up; access=${prefs.hasValue(ACCESS)} refresh=${prefs.hasValue(REFRESH)}")
        val changed = SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
            if (key == ACCESS || key == REFRESH) {
                Log.i(TAG, "${key?.substringAfterLast('_')?.lowercase()} entry now present=${shared.hasValue(key)}")
            }
        }
        listener = changed
        prefs.registerOnSharedPreferenceChangeListener(changed)
        Timber.plant(AuthTree)
    }

    // A cleared token stays in the file as a sealed empty string, so the key alone proves nothing.
    private fun SharedPreferences.hasValue(key: String?) = !getString(key, null).isNullOrEmpty()

    private const val ACCESS = CryptoPreferenceRepository.ACCESS_TOKEN_KEY_NAME
    private const val REFRESH = CryptoPreferenceRepository.REFRESH_TOKEN_KEY_NAME

    private object AuthTree : Timber.Tree() {
        private val relevant = Regex("token|session|unseal|keystore|key |refresh|unauthor|logout|sign in", RegexOption.IGNORE_CASE)

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (!relevant.containsMatchIn(message)) return
            val cause = t?.let { " | ${it.javaClass.name}: ${it.message.orEmpty().take(MAX_CAUSE)}" }.orEmpty()
            Log.println(maxOf(priority, Log.INFO), TAG, "${tag.orEmpty()} ${message.take(MAX_MESSAGE)}$cause")
        }
    }

    private const val MAX_MESSAGE = 300
    private const val MAX_CAUSE = 200
}
