@file:Suppress("MagicNumber")

package com.kino.puber.ui.feature.device.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.data.api.network.diagnostics.LatencySample
import com.kino.puber.data.api.network.diagnostics.ThroughputSample
import com.kino.puber.domain.interactor.diagnostics.ServerTestState
import com.kino.puber.domain.interactor.diagnostics.SpeedTestServer
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsViewState
import com.kino.puber.ui.feature.device.diagnostics.model.ServerSpeedUi

@Preview(name = "Speed test — ready", device = TV_1080p)
@Composable
private fun SpeedTestReadyPreview() = PuberTheme {
    NetworkDiagnosticsContent(
        NetworkDiagnosticsViewState(currentServer = SpeedTestServer.Amsterdam)
    )
}

@Preview(name = "Speed test — running", device = TV_1080p)
@Composable
private fun SpeedTestRunningPreview() = PuberTheme {
    NetworkDiagnosticsContent(
        NetworkDiagnosticsViewState(
            currentServer = SpeedTestServer.Amsterdam,
            running = true,
            servers = listOf(
                ServerSpeedUi(
                    SpeedTestServer.Amsterdam,
                    ServerTestState.Success(
                        ThroughputSample(39_000_000, 8_000),
                        LatencySample(pingMillis = 48, jitterMillis = 6),
                    ),
                ),
                ServerSpeedUi(
                    SpeedTestServer.Moscow,
                    ServerTestState.Running(
                        ThroughputSample(62_000_000, 7_000),
                        LatencySample(pingMillis = 31, jitterMillis = 4),
                    ),
                ),
            ),
        )
    )
}

@Preview(name = "Speed test — recommendation", device = TV_1080p)
@Composable
private fun SpeedTestRecommendationPreview() = PuberTheme {
    NetworkDiagnosticsContent(
        NetworkDiagnosticsViewState(
            currentServer = SpeedTestServer.Amsterdam,
            finished = true,
            recommendedServer = SpeedTestServer.Moscow,
            servers = listOf(
                ServerSpeedUi(
                    SpeedTestServer.Amsterdam,
                    ServerTestState.Success(
                        ThroughputSample(31_000_000, 8_000),
                        LatencySample(pingMillis = 54, jitterMillis = 8),
                    ),
                ),
                ServerSpeedUi(
                    SpeedTestServer.Moscow,
                    ServerTestState.Success(
                        ThroughputSample(55_000_000, 8_000),
                        LatencySample(pingMillis = 29, jitterMillis = 3),
                    ),
                ),
            ),
        )
    )
}
