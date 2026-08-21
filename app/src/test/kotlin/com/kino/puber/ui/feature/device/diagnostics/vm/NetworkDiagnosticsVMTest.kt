package com.kino.puber.ui.feature.device.diagnostics.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.data.api.network.diagnostics.ThroughputSample
import com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsInteractor
import com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsRun
import com.kino.puber.domain.interactor.diagnostics.ServerTestState
import com.kino.puber.domain.interactor.diagnostics.SpeedTestServer
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsActions
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class NetworkDiagnosticsVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val interactor = mockk<NetworkDiagnosticsInteractor>()
    private val router = mockk<AppRouter>(relaxed = true)

    private fun vm(): NetworkDiagnosticsVM {
        every { router.screens } returns mockk<Screens>(relaxed = true)
        return NetworkDiagnosticsVM(
            interactor = interactor,
            errorHandler = mockk<ErrorHandler>(relaxed = true),
            router = router,
        )
    }

    private fun finishedRun(server: SpeedTestServer, bytes: Long) =
        NetworkDiagnosticsRun(currentServer = SpeedTestServer.Amsterdam)
            .with(server, ServerTestState.Success(ThroughputSample(bytes, 1_000)))
            .copy(finished = true)

    @Test
    fun onStart_loadsCurrentServer_withoutStartingTheTest() = runTest {
        coEvery { interactor.currentServer() } returns SpeedTestServer.Amsterdam

        val viewModel = vm()
        viewModel.testOnStart()

        assertEquals(SpeedTestServer.Amsterdam, viewModel.testStateValue.currentServer)
        assertFalse(viewModel.testStateValue.running)
        verify(exactly = 0) { interactor.run(any()) }
    }

    @Test
    fun start_testsSelectedServer_andKeepsTheOtherResult() = runTest {
        coEvery { interactor.currentServer() } returns SpeedTestServer.Amsterdam
        every { interactor.run(SpeedTestServer.Amsterdam) } returns flowOf(
            finishedRun(SpeedTestServer.Amsterdam, bytes = 1_000_000)
        )
        every { interactor.run(SpeedTestServer.Moscow) } returns flowOf(
            finishedRun(SpeedTestServer.Moscow, bytes = 2_000_000)
        )
        val viewModel = vm()
        viewModel.testOnStart()

        viewModel.onAction(NetworkDiagnosticsActions.Start(SpeedTestServer.Amsterdam))
        viewModel.onAction(NetworkDiagnosticsActions.Start(SpeedTestServer.Moscow))

        assertInstanceOf(
            ServerTestState.Success::class.java,
            viewModel.testStateValue.servers.first().state,
        )
        assertInstanceOf(
            ServerTestState.Success::class.java,
            viewModel.testStateValue.servers.last().state,
        )
        assertEquals(SpeedTestServer.Moscow, viewModel.testStateValue.recommendedServer)
    }

    @Test
    fun start_isIgnored_whileAnotherServerIsRunning() = runTest {
        coEvery { interactor.currentServer() } returns SpeedTestServer.Amsterdam
        val channel = Channel<NetworkDiagnosticsRun>(Channel.UNLIMITED)
        every { interactor.run(SpeedTestServer.Amsterdam) } returns channel.consumeAsFlow()
        val viewModel = vm()
        viewModel.testOnStart()

        viewModel.onAction(NetworkDiagnosticsActions.Start(SpeedTestServer.Amsterdam))
        channel.send(
            NetworkDiagnosticsRun(currentServer = SpeedTestServer.Amsterdam)
                .with(SpeedTestServer.Amsterdam, ServerTestState.Running())
        )
        viewModel.onAction(NetworkDiagnosticsActions.Start(SpeedTestServer.Moscow))

        assertTrue(viewModel.testStateValue.running)
        verify(exactly = 1) { interactor.run(SpeedTestServer.Amsterdam) }
        verify(exactly = 0) { interactor.run(SpeedTestServer.Moscow) }
    }
}
