package com.kino.puber.ui.feature.device.diagnostics.model

import androidx.compose.runtime.Immutable
import com.kino.puber.domain.interactor.diagnostics.ServerTestState
import com.kino.puber.domain.interactor.diagnostics.SpeedTestServer

@Immutable
internal data class ServerSpeedUi(
    val server: SpeedTestServer,
    val state: ServerTestState = ServerTestState.Pending,
)

@Immutable
internal data class NetworkDiagnosticsViewState(
    val servers: List<ServerSpeedUi> = SpeedTestServer.entries.map(::ServerSpeedUi),
    val currentServer: SpeedTestServer? = null,
    val running: Boolean = false,
    val finished: Boolean = false,
    val recommendedServer: SpeedTestServer? = null,
)
