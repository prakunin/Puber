package com.kino.puber.core.telemetry

import androidx.profileinstaller.ProfileVerifier
import timber.log.Timber
import java.util.concurrent.ExecutionException

/** Where the result code of the profile install check comes from; it arrives once, on any thread. */
internal fun interface ProfileStatusSource {
    fun onStatus(callback: (Int) -> Unit)

    companion object {
        val None = ProfileStatusSource { }
    }
}

/** Reads the install result; it never asks the library to write its verification file. */
internal object ProfileVerifierStatusSource : ProfileStatusSource {
    override fun onStatus(callback: (Int) -> Unit) {
        val future = ProfileVerifier.getCompilationStatusAsync()
        future.addListener(
            {
                try {
                    callback(future.get().profileInstallResultCode)
                } catch (e: ExecutionException) {
                    Timber.w(e)
                }
            },
            Runnable::run,
        )
    }
}
