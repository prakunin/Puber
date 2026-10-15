package com.kino.puber.core.telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

internal sealed interface SessionLogState {
    data class Off(val hasFiles: Boolean) : SessionLogState
    data class Recording(val remainingMs: Long, val hasFiles: Boolean) : SessionLogState
    data object Unavailable : SessionLogState
}

internal sealed interface ShareResult {
    data class Ready(val archive: File) : ShareResult
    data object NothingToShare : ShareResult
    data object Failed : ShareResult
}

/** What Settings sees of the session log. */
internal interface SessionLogController {
    val state: StateFlow<SessionLogState>
    fun setEnabled(enabled: Boolean)
    fun refresh()
    suspend fun share(): ShareResult
}

/** Installed until the runtime replaces it: always off, nothing to share, no side effects. */
internal object DisabledSessionLogController : SessionLogController {
    override val state: StateFlow<SessionLogState> = MutableStateFlow(SessionLogState.Off(false))
    override fun setEnabled(enabled: Boolean) = Unit
    override fun refresh() = Unit
    override suspend fun share(): ShareResult = ShareResult.NothingToShare
}
