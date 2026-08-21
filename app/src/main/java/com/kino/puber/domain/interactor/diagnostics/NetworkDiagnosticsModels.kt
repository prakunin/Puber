package com.kino.puber.domain.interactor.diagnostics

import com.kino.puber.data.api.network.diagnostics.ThroughputSample
import com.kino.puber.data.api.network.diagnostics.LatencySample

/** Media locations measured by the official KinoPub client's speed test. */
internal enum class SpeedTestServer(
    val settingOptionId: Int,
    val endpoint: String,
) {
    Amsterdam(
        settingOptionId = 1,
        endpoint = "https://speed.ams-static-14.cdntogo.net/speedtest/garbage.php",
    ),
    Moscow(
        settingOptionId = 2,
        endpoint = "https://speed.msk-static-05.cdntogo.net/speedtest/garbage.php",
    ),
    ;

    companion object {
        fun fromSettingOptionId(id: Int?): SpeedTestServer? = entries.firstOrNull {
            it.settingOptionId == id
        }
    }
}

internal sealed interface ServerTestState {
    data object Pending : ServerTestState
    data class Running(
        val sample: ThroughputSample? = null,
        val latency: LatencySample? = null,
    ) : ServerTestState
    data class Success(
        val sample: ThroughputSample,
        val latency: LatencySample? = null,
    ) : ServerTestState
    data object Failure : ServerTestState
    data object Cancelled : ServerTestState
}

internal data class NetworkDiagnosticsRun(
    val currentServer: SpeedTestServer? = null,
    val measurements: Map<SpeedTestServer, ServerTestState> = SpeedTestServer.entries.associateWith {
        ServerTestState.Pending
    },
    val finished: Boolean = false,
) {
    fun state(server: SpeedTestServer): ServerTestState =
        measurements[server] ?: ServerTestState.Pending

    fun with(server: SpeedTestServer, state: ServerTestState): NetworkDiagnosticsRun =
        copy(measurements = measurements + (server to state))

    val recommendedServer: SpeedTestServer?
        get() {
            val selected = currentServer ?: return null
            val results = SpeedTestServer.entries.mapNotNull { server ->
                val sample = (state(server) as? ServerTestState.Success)?.sample
                sample?.let { server to it.bitsPerSecond }
            }
            val fastest = results.maxByOrNull { it.second } ?: return null
            if (fastest.first == selected) return null

            val selectedRate = results.firstOrNull { it.first == selected }?.second
            return fastest.first.takeIf {
                selectedRate == null || fastest.second >= selectedRate * RECOMMENDATION_MARGIN
            }
        }

    private companion object {
        /** Avoid proposing a settings change for ordinary measurement jitter. */
        const val RECOMMENDATION_MARGIN = 1.05
    }
}
