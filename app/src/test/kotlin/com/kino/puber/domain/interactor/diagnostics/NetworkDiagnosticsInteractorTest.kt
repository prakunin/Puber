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
        assertTrue(downloadedUrls.single().startsWith("https://speed.msk-static-05.cdntogo.net/"))
        assertTrue(downloadedUrls.single().endsWith("r=fixed&ckSize=100"))
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

        assertEquals(ServerTestState.Failure, result.state(SpeedTestServer.Amsterdam))
        assertTrue(result.finished)
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

}
