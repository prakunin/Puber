package com.kino.puber.util

import com.kino.puber.core.telemetry.SessionLogController
import com.kino.puber.core.telemetry.SessionLogState
import com.kino.puber.core.telemetry.ShareResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Scripted controller; a relaxed mock would hand the view model a `StateFlow` that never emits. */
internal class FakeSessionLogController(
    initial: SessionLogState = SessionLogState.Off(hasFiles = false),
) : SessionLogController {
    val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<SessionLogState> = mutableState

    val setEnabledCalls = mutableListOf<Boolean>()
    var refreshCalls = 0
    var shareCalls = 0

    var shareResult: ShareResult = ShareResult.NothingToShare

    /** When set, [share] waits for it, so a test can look at the state while sharing is in flight. */
    var shareGate: CompletableDeferred<Unit>? = null

    override fun setEnabled(enabled: Boolean) {
        setEnabledCalls += enabled
        val hasFiles = when (val current = mutableState.value) {
            is SessionLogState.Off -> current.hasFiles
            is SessionLogState.Recording -> current.hasFiles
            SessionLogState.Unavailable -> false
        }
        mutableState.value = if (enabled) {
            SessionLogState.Recording(remainingMs = SEVEN_DAYS_MS, hasFiles = hasFiles)
        } else {
            SessionLogState.Off(hasFiles)
        }
    }

    override fun refresh() {
        refreshCalls++
    }

    override suspend fun share(): ShareResult {
        shareCalls++
        shareGate?.await()
        return shareResult
    }

    companion object {
        const val SEVEN_DAYS_MS = 7L * 24 * 3_600_000
    }
}
