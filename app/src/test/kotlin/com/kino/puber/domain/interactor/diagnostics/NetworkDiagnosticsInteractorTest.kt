package com.kino.puber.domain.interactor.diagnostics

import com.kino.puber.data.api.models.DeviceResponse
import com.kino.puber.data.api.models.SettingOption
import com.kino.puber.data.api.network.diagnostics.BoundedDownloader
import com.kino.puber.data.api.network.diagnostics.LatencyProbe
import com.kino.puber.data.api.network.diagnostics.ThroughputSample
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class NetworkDiagnosticsInteractorTest {

    private val settings = mockk<IDeviceSettingInteractor>(relaxed = true)
    private val downloadedUrls = mutableListOf<String>()

    private fun interactor(
        downloader: BoundedDownloader = BoundedDownloader { url, maxBytes, onProgress ->
            downloadedUrls += url
            val sample = ThroughputSample(maxBytes, 1_000)
            onProgress(sample)
            sample
        },
        latencyProbe: LatencyProbe = LatencyProbe { 20L },
    ) = NetworkDiagnosticsInteractor(
        deviceSettings = settings,
        downloader = downloader,
        latencyProbe = latencyProbe,
        cacheBuster = { "fixed" },
        random = Random(SHUFFLE_SEED),
    )

    private fun currentServer(server: SpeedTestServer = SpeedTestServer.Amsterdam) {
        val response = mockk<DeviceResponse>()
        every { response.device.settings.serverLocation.value } returns listOf(
            SettingOption(server.settingOptionId, server.name, selected = 1),
        )
        every { settings.getCurrentDeviceSettings() } returns flowOf(Result.success(response))
    }

    @Test
    fun run_testsOnlyTheSelectedServer_usingItsOfficialEndpoint() = runTest {
        currentServer()

        val result = interactor().run(SpeedTestServer.Moscow).toList().last()

        assertTrue(result.finished)
        assertInstanceOf(ServerTestState.Success::class.java, result.state(SpeedTestServer.Moscow))
        assertEquals(ServerTestState.Pending, result.state(SpeedTestServer.Amsterdam))
        assertEquals(1, downloadedUrls.size)
        val expectedUrl = Regex(
            """https://speed\.msk-static-0[567]\.cdntogo\.net/speedtest/garbage\.php\?r=fixed&ckSize=100""",
        )
        assertTrue(downloadedUrls.single().matches(expectedUrl), downloadedUrls.single())
    }

    @Test
    fun run_publishesLiveProgress() = runTest {
        currentServer()

        val emissions = interactor().run(SpeedTestServer.Amsterdam).toList()

        assertTrue(
            emissions.any {
                val state = it.state(SpeedTestServer.Amsterdam)
                state is ServerTestState.Running && state.sample != null
            }
        )
    }

    @Test
    fun run_calculatesMedianPingAndJitter_forTheSelectedServer() = runTest {
        currentServer()
        val values = ArrayDeque(listOf(40L, 50L, 45L, 60L, 55L))

        val result = interactor(latencyProbe = LatencyProbe { values.removeFirst() })
            .run(SpeedTestServer.Amsterdam)
            .toList()
            .last()
        val amsterdam = result.state(SpeedTestServer.Amsterdam) as ServerTestState.Success

        assertEquals(50L, amsterdam.latency?.pingMillis)
        assertEquals(7L, amsterdam.latency?.jitterMillis)
    }

    @Test
    fun run_finishesWithFailure_whenSelectedServerFails() = runTest {
        currentServer()
        val downloader = BoundedDownloader { url, maxBytes, _ ->
            if (url.contains("ams-static")) error("Amsterdam unavailable")
            ThroughputSample(maxBytes, 2_000)
        }

        val result = interactor(downloader).run(SpeedTestServer.Amsterdam).toList().last()

        val amsterdam = result.state(SpeedTestServer.Amsterdam) as ServerTestState.Failure
        assertEquals(null, amsterdam.sample)
        // The CDN answered every ping and served nothing: worth saying on the screen.
        assertEquals(20L, amsterdam.latency?.pingMillis)
        assertTrue(result.finished)
    }

    @Test
    fun run_movesToTheNextShard_whenOneIsUnreachable() = runTest {
        currentServer()
        val downloader = BoundedDownloader { url, maxBytes, onProgress ->
            downloadedUrls += url
            if (downloadedUrls.size == 1) error("Shard unreachable")
            val sample = ThroughputSample(maxBytes, 1_000)
            onProgress(sample)
            sample
        }

        val result = interactor(downloader).run(SpeedTestServer.Amsterdam).toList().last()

        assertInstanceOf(ServerTestState.Success::class.java, result.state(SpeedTestServer.Amsterdam))
        assertEquals(2, downloadedUrls.size)
        assertEquals(2, downloadedUrls.distinct().size)
    }

    @Test
    fun run_skipsAShardThatNeverAnswers_withoutAttemptingItsDownload() = runTest {
        currentServer()
        val probedUrls = linkedSetOf<String>()
        val latencyProbe = LatencyProbe { url ->
            probedUrls += url
            if (probedUrls.size == 1) error("Probe refused") else 20L
        }

        val result = interactor(latencyProbe = latencyProbe)
            .run(SpeedTestServer.Amsterdam)
            .toList()
            .last()
        val amsterdam = result.state(SpeedTestServer.Amsterdam) as ServerTestState.Success

        // The silent shard cost five one-second probes, not a download timeout on top of them.
        assertEquals(1, downloadedUrls.size)
        val silentShard = probedUrls.first().substringBefore("/speedtest")
        assertFalse(downloadedUrls.single().startsWith(silentShard))
        assertEquals(20L, amsterdam.latency?.pingMillis)
    }

    @Test
    fun run_keepsTheMeasuredSpeed_whenTheTransferBreaksAfterProgress() = runTest {
        currentServer()
        val downloader = BoundedDownloader { url, _, onProgress ->
            downloadedUrls += url
            onProgress(ThroughputSample(2_000_000, 1_000))
            error("Connection reset")
        }

        val result = interactor(downloader).run(SpeedTestServer.Amsterdam).toList().last()
        val amsterdam = result.state(SpeedTestServer.Amsterdam) as ServerTestState.Failure

        assertEquals(2_000_000, amsterdam.sample?.bytes)
        assertEquals(20L, amsterdam.latency?.pingMillis)
        // The link broke, not the shard: another shard would only measure the same break again.
        assertEquals(1, downloadedUrls.size)
    }

    @Test
    fun run_doesNotCallASecondOfTransferARate_whenTheDownloadDiedEarly() = runTest {
        currentServer()
        // The downloader keeps the bytes that arrived when a transfer breaks, so a dead connection
        // reaches the interactor as an ordinary short sample rather than as an error.
        val downloader = BoundedDownloader { url, _, onProgress ->
            downloadedUrls += url
            val sample = ThroughputSample(2_000_000, 300)
            onProgress(sample)
            sample
        }

        val result = interactor(downloader).run(SpeedTestServer.Amsterdam).toList().last()
        val amsterdam = result.state(SpeedTestServer.Amsterdam) as ServerTestState.Failure

        assertEquals(2_000_000, amsterdam.sample?.bytes)
        assertEquals(1, downloadedUrls.size)
    }

    @Test
    fun run_acceptsAShortSample_whenTheTransferRanForTheWholeDeadline() = runTest {
        currentServer()
        val downloader = BoundedDownloader { url, _, onProgress ->
            downloadedUrls += url
            // Far below the requested payload, but it measured the link for twenty seconds: that is
            // an ordinary television on an ordinary connection.
            val sample = ThroughputSample(20_000_000, 20_000)
            onProgress(sample)
            sample
        }

        val result = interactor(downloader).run(SpeedTestServer.Amsterdam).toList().last()

        assertInstanceOf(ServerTestState.Success::class.java, result.state(SpeedTestServer.Amsterdam))
    }

    @Test
    fun run_movesToTheNextShard_whenOneDeliversNothingWithoutFailing() = runTest {
        currentServer()
        val downloader = BoundedDownloader { url, maxBytes, onProgress ->
            downloadedUrls += url
            val sample = if (downloadedUrls.size == 1) {
                ThroughputSample(0, 8_000)
            } else {
                ThroughputSample(maxBytes, 1_000)
            }
            onProgress(sample)
            sample
        }

        val result = interactor(downloader).run(SpeedTestServer.Amsterdam).toList().last()

        assertInstanceOf(ServerTestState.Success::class.java, result.state(SpeedTestServer.Amsterdam))
        assertEquals(2, downloadedUrls.size)
    }

    @Test
    fun run_stopsAfterThreeShards_whenTheServerIsUnreachable() = runTest {
        currentServer()
        val downloader = BoundedDownloader { url, _, _ ->
            downloadedUrls += url
            error("Shard unreachable")
        }

        interactor(downloader).run(SpeedTestServer.Amsterdam).toList()

        assertEquals(3, downloadedUrls.size)
    }

    @Test
    fun result_recommendsTheFasterServer_whenItIsNotCurrent() {
        val run = NetworkDiagnosticsRun(currentServer = SpeedTestServer.Amsterdam)
            .with(
                SpeedTestServer.Amsterdam,
                ServerTestState.Success(ThroughputSample(1_000_000, 1_000)),
            )
            .with(
                SpeedTestServer.Moscow,
                ServerTestState.Success(ThroughputSample(2_000_000, 1_000)),
            )

        assertEquals(SpeedTestServer.Moscow, run.recommendedServer)
    }

    @Test
    fun result_doesNotRecommendAChange_whenCurrentServerIsFastest() {
        val run = NetworkDiagnosticsRun(currentServer = SpeedTestServer.Amsterdam)
            .with(
                SpeedTestServer.Amsterdam,
                ServerTestState.Success(ThroughputSample(2_000_000, 1_000)),
            )
            .with(
                SpeedTestServer.Moscow,
                ServerTestState.Success(ThroughputSample(1_000_000, 1_000)),
            )

        assertFalse(run.recommendedServer != null)
    }

    @Test
    fun result_doesNotRecommendAChange_forMeasurementJitter() {
        val run = NetworkDiagnosticsRun(currentServer = SpeedTestServer.Amsterdam)
            .with(
                SpeedTestServer.Amsterdam,
                ServerTestState.Success(ThroughputSample(1_000_000, 1_000)),
            )
            .with(
                SpeedTestServer.Moscow,
                ServerTestState.Success(ThroughputSample(1_040_000, 1_000)),
            )

        assertEquals(null, run.recommendedServer)
    }

    private companion object {
        /** A fixed seed so the shard the test measures does not change between runs. */
        const val SHUFFLE_SEED = 7
    }
}
