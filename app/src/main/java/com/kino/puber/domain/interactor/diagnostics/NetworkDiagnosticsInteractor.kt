package com.kino.puber.domain.interactor.diagnostics

import com.kino.puber.core.logger.log
import com.kino.puber.data.api.network.diagnostics.BoundedDownloader
import com.kino.puber.data.api.network.diagnostics.LatencyProbe
import com.kino.puber.data.api.network.diagnostics.LatencySample
import com.kino.puber.data.api.network.diagnostics.ThroughputSample
import com.kino.puber.data.api.network.diagnostics.latencySampleOf
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import java.util.UUID
import kotlin.random.Random

/**
 * Reproduces the useful part of KinoPub's speed test: the same bounded payload is downloaded from
 * the Amsterdam and Moscow media CDNs, in order, without changing the selected server. Each CDN is
 * served by a handful of shards, and the test moves between them rather than reporting the first
 * unreachable one as the state of the connection.
 */
internal class NetworkDiagnosticsInteractor(
    private val deviceSettings: IDeviceSettingInteractor,
    private val downloader: BoundedDownloader,
    private val latencyProbe: LatencyProbe,
    private val cacheBuster: () -> String = { UUID.randomUUID().toString() },
    private val random: Random = Random.Default,
) {

    suspend fun currentServer(): SpeedTestServer? {
        val response = deviceSettings.getCurrentDeviceSettings().first().getOrThrow()
        val selectedId = response.device.settings.serverLocation.value
            .firstOrNull { it.selected == 1 }
            ?.id
        return SpeedTestServer.fromSettingOptionId(selectedId)
    }

    fun run(server: SpeedTestServer): Flow<NetworkDiagnosticsRun> = channelFlow {
        val base = NetworkDiagnosticsRun(currentServer = selectedServerOrNull())
        send(base)

        val measured = base.with(server, measure(base, server))
        send(measured)
        send(measured.copy(finished = true))
    }

    private suspend fun selectedServerOrNull(): SpeedTestServer? = try {
        currentServer()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        log(error, "Failed to load the current media server; continuing with the speed test")
        null
    }

    /**
     * Walks the server's shards until one of them measures. A shard that answers nothing is a dead
     * host rather than a slow link, so the next one is worth trying; once bytes have arrived it is
     * the link that broke, retrying would only measure the same break again, and the bytes that did
     * arrive are kept as the result.
     *
     * The ping is measured first and it decides whether the download is attempted at all. Five
     * one-second probes are what a dead host costs here; the download that follows would cost the
     * viewer twenty more to learn the same thing.
     */
    private suspend fun ProducerScope<NetworkDiagnosticsRun>.measure(
        base: NetworkDiagnosticsRun,
        server: SpeedTestServer,
    ): ServerTestState {
        send(base.with(server, ServerTestState.Running()))
        // Kept across shards only as evidence that the CDN answered at all, which is worth showing
        // beside a failure. Never paired with a rate: a ping belongs to the host that answered it.
        var lastLatency: LatencySample? = null

        for (shard in server.shards.shuffled(random).take(MAX_SHARD_ATTEMPTS)) {
            val latency = measureLatency(server, shard) ?: continue
            lastLatency = latency
            send(base.with(server, ServerTestState.Running(latency = latency)))

            var retained: ThroughputSample? = null
            val sample = try {
                downloader.measure(
                    url = server.testUrl(shard, cacheBuster()),
                    maxBytes = TEST_MAX_BYTES,
                    onProgress = { progress ->
                        if (progress.bytes > 0L) retained = progress
                        trySend(base.with(server, ServerTestState.Running(progress, latency)))
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                log(error, "Speed test failed on shard ${server.endpoint(shard)}")
                null
            }

            if (sample != null && sample.isAMeasurement()) {
                return ServerTestState.Success(sample, latency)
            }
            // Bytes arrived, so the host is alive and it was the transfer that broke; another shard
            // would only measure the same break. A shard that delivered nothing gets no such credit.
            val partial = sample?.takeIf { it.bytes > 0L } ?: retained
            partial?.let { return ServerTestState.Failure(it, latency) }
        }
        return ServerTestState.Failure(latency = lastLatency)
    }

    /**
     * Whether a sample is a reading of the link or a record of a transfer that died.
     *
     * The downloader does not distinguish the two: it keeps the bytes that arrived when a transfer
     * breaks, on purpose, because the deadline ending a healthy but slow transfer produces exactly
     * the same short read — and on a television that is the ordinary case, since the full payload at
     * this size needs more than 40 Mbit/s to arrive inside the deadline.
     *
     * Duration is what separates them, once a shard that delivered nothing at all is out of the
     * way. A transfer still running when the deadline arrived measured the link for as long as it
     * was given, however little it fetched. One that ended after a fraction of a second measured
     * mostly connection setup, and reporting it as a rate lets a broken attempt beat a real
     * measurement of the other server for "best result".
     */
    private fun ThroughputSample.isAMeasurement(): Boolean =
        bytes > 0L && (bytes >= TEST_MAX_BYTES || elapsedMillis >= MIN_MEASURED_MILLIS)

    private fun SpeedTestServer.testUrl(shard: Int, cacheBuster: String): String =
        "${endpoint(shard)}?r=$cacheBuster&ckSize=$TEST_SIZE_MEBIBYTES"

    private suspend fun measureLatency(server: SpeedTestServer, shard: Int): LatencySample? {
        val samples = mutableListOf<Long>()
        repeat(LATENCY_ATTEMPTS) {
            try {
                samples += latencyProbe.roundTripMillis(server.latencyUrl(shard, cacheBuster()))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                log(error, "Latency probe failed for $server")
            }
        }
        return latencySampleOf(samples)
    }

    private fun SpeedTestServer.latencyUrl(shard: Int, cacheBuster: String): String =
        "${endpoint(shard)}?r=$cacheBuster&ckSize=$LATENCY_SIZE_MEBIBYTES"

    private companion object {
        const val LATENCY_ATTEMPTS = 5

        /**
         * Bounded because every attempt costs the viewer a call timeout in front of a screen that
         * is meant to answer one question. Three shards separate a dead host from a dead link;
         * walking ten of them only makes an unreachable CDN take longer to say so.
         */
        const val MAX_SHARD_ATTEMPTS = 3
        /**
         * Comfortably inside the downloader's own call deadline, so a transfer that ran to that
         * deadline always clears it, and long enough that connection setup is a small share of
         * whatever rate is computed.
         */
        const val MIN_MEASURED_MILLIS = 5_000L
        const val LATENCY_SIZE_MEBIBYTES = 1
        const val TEST_SIZE_MEBIBYTES = 100
        const val TEST_MAX_BYTES = TEST_SIZE_MEBIBYTES * 1024L * 1024L
    }
}
