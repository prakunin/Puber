package com.kino.puber.domain.interactor.diagnostics

import com.kino.puber.core.logger.log
import com.kino.puber.data.api.network.diagnostics.BoundedDownloader
import com.kino.puber.data.api.network.diagnostics.LatencyProbe
import com.kino.puber.data.api.network.diagnostics.LatencySample
import com.kino.puber.data.api.network.diagnostics.ThroughputSample
import com.kino.puber.data.api.network.diagnostics.latencySampleOf
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Reproduces the useful part of KinoPub's speed test: the same bounded payload is downloaded from
 * the Amsterdam and Moscow media CDNs, in order, without changing the selected server.
 */
internal class NetworkDiagnosticsInteractor(
    private val deviceSettings: IDeviceSettingInteractor,
    private val downloader: BoundedDownloader,
    private val latencyProbe: LatencyProbe,
    private val cacheBuster: () -> String = { UUID.randomUUID().toString() },
) {

    suspend fun currentServer(): SpeedTestServer? {
        val response = deviceSettings.getCurrentDeviceSettings().first().getOrThrow()
        val selectedId = response.device.settings.serverLocation.value
            .firstOrNull { it.selected == 1 }
            ?.id
        return SpeedTestServer.fromSettingOptionId(selectedId)
    }

    fun run(server: SpeedTestServer): Flow<NetworkDiagnosticsRun> = channelFlow {
        val selectedServer = try {
            currentServer()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            log(error, "Failed to load the current media server; continuing with the speed test")
            null
        }
        var current = NetworkDiagnosticsRun(currentServer = selectedServer)
        send(current)

        current = current.with(server, ServerTestState.Running())
        send(current)
        val latency = measureLatency(server)
        current = current.with(server, ServerTestState.Running(latency = latency))
        send(current)

        var latestSample: ThroughputSample? = null
        val result = try {
            val sample = downloader.measure(
                url = server.testUrl(cacheBuster()),
                maxBytes = TEST_MAX_BYTES,
                onProgress = { progress ->
                    latestSample = progress
                    trySend(current.with(server, ServerTestState.Running(progress, latency)))
                },
            )
            if (sample.bytes > 0L) {
                ServerTestState.Success(sample, latency)
            } else {
                ServerTestState.Failure
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            log(error, "Speed test failed for $server after ${latestSample?.bytes ?: 0L} bytes")
            ServerTestState.Failure
        }

        current = current.with(server, result)
        send(current)
        send(current.copy(finished = true))
    }

    private fun SpeedTestServer.testUrl(cacheBuster: String): String =
        "$endpoint?r=$cacheBuster&ckSize=$TEST_SIZE_MEBIBYTES"

    private suspend fun measureLatency(server: SpeedTestServer): LatencySample? {
        val samples = mutableListOf<Long>()
        repeat(LATENCY_ATTEMPTS) {
            try {
                samples += latencyProbe.roundTripMillis(server.latencyUrl(cacheBuster()))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                log(error, "Latency probe failed for $server")
            }
        }
        return latencySampleOf(samples)
    }

    private fun SpeedTestServer.latencyUrl(cacheBuster: String): String =
        "$endpoint?r=$cacheBuster&ckSize=$LATENCY_SIZE_MEBIBYTES"

    private companion object {
        const val LATENCY_ATTEMPTS = 5
        const val LATENCY_SIZE_MEBIBYTES = 1
        const val TEST_SIZE_MEBIBYTES = 100
        const val TEST_MAX_BYTES = TEST_SIZE_MEBIBYTES * 1024L * 1024L
    }
}
