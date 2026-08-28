package com.kino.puber.domain.interactor.diagnostics

import com.kino.puber.data.api.network.diagnostics.ThroughputSample
import com.kino.puber.data.api.network.diagnostics.LatencySample

/** Media locations measured by the official KinoPub client's speed test. */
internal enum class SpeedTestServer(
    val settingOptionId: Int,
    val hostCode: String,
    /**
     * The CDN answers on numbered shards, and one dead shard is indistinguishable from a dead link
     * when only one of them is ever tried.
     *
     * Listed here are the shards that answered `garbage.php` over HTTPS, which is the only evidence
     * worth listing: every name under the domain resolves, most of them to the
     * `clients.cdntogo.net` wildcard, and several that do carry an address of their own
     * (`ams-static-09` through `ams-static-13`) accept a connection and then serve nothing. A shard
     * added here without that check costs a viewer a timeout for every run that draws it.
     */
    val shards: List<Int>,
) {
    Amsterdam(
        settingOptionId = 1,
        hostCode = "ams",
        shards = AMSTERDAM_SHARDS,
    ),
    Moscow(
        settingOptionId = 2,
        hostCode = "msk",
        shards = MOSCOW_SHARDS,
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
    /**
     * [sample] is whatever had arrived when the transfer broke. A rate the viewer watched climb for
     * ten seconds is the one measurement the attempt did produce, so it stays on the screen instead
     * of being replaced by the word "failed".
     */
    data class Failure(
        val sample: ThroughputSample? = null,
        val latency: LatencySample? = null,
    ) : ServerTestState
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

private val AMSTERDAM_SHARDS = listOf(1, 2, 3, 14, 15)
private val MOSCOW_SHARDS = listOf(5, 6, 7)

/** One shard's copy of the bounded payload, e.g. `speed.ams-static-01.cdntogo.net`. */
internal fun SpeedTestServer.endpoint(shard: Int): String {
    val paddedShard = shard.toString().padStart(SHARD_NUMBER_DIGITS, '0')
    return "https://speed.$hostCode-static-$paddedShard.$CDN_HOST_SUFFIX/speedtest/garbage.php"
}

private const val CDN_HOST_SUFFIX = "cdntogo.net"
private const val SHARD_NUMBER_DIGITS = 2
