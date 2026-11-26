package com.kino.puber.ui.feature.device.diagnostics.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.system.ConnectionTransport
import com.kino.puber.core.system.ConnectionTransportProvider
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
import kotlinx.coroutines.CompletableDeferred
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
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
    private val transportProvider = mockk<ConnectionTransportProvider> {
        every { current() } returns ConnectionTransport.Wifi
    }

    private val errorHandler = mockk<ErrorHandler>(relaxed = true)

    private fun vm(ioDispatcher: CoroutineDispatcher = mainDispatcher.dispatcher): NetworkDiagnosticsVM {
        every { router.screens } returns mockk<Screens>(relaxed = true)
        return NetworkDiagnosticsVM(
            interactor = interactor,
            transportProvider = transportProvider,
            ioDispatcher = ioDispatcher,
            errorHandler = errorHandler,
            router = router,
        )
    }

    private fun finishedRun(server: SpeedTestServer, bytes: Long) =
        NetworkDiagnosticsRun(currentServer = SpeedTestServer.Amsterdam)
            .with(server, ServerTestState.Success(ThroughputSample(bytes, 1_000)))
            .copy(finished = true)

    @Test
    fun state_transportNull_beforeStart() {
        assertNull(vm().testStateValue.transport)
    }

    @Test
    fun onStart_setsTransportInState_whenProviderReturnsWifi() = runTest {
        coEvery { interactor.currentServer() } returns SpeedTestServer.Amsterdam

        val viewModel = vm()
        viewModel.testOnStart()

        assertEquals(ConnectionTransport.Wifi, viewModel.testStateValue.transport)
    }

    @Test
    fun onStart_readsTransportOnIoDispatcher_notOnMain() = runTest {
        coEvery { interactor.currentServer() } returns SpeedTestServer.Amsterdam
        val viewModel = vm(StandardTestDispatcher(mainDispatcher.dispatcher.scheduler))

        viewModel.testOnStart()

        assertNull(viewModel.testStateValue.transport)
        verify(exactly = 0) { transportProvider.current() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ConnectionTransport.Wifi, viewModel.testStateValue.transport)
    }

    @Test
    fun startRun_rereadsTransport_whenNetworkChangedSinceStart() = runTest {
        coEvery { interactor.currentServer() } returns SpeedTestServer.Amsterdam
        every { interactor.run(SpeedTestServer.Amsterdam) } returns flowOf(
            finishedRun(SpeedTestServer.Amsterdam, bytes = 1_000_000)
        )
        every { transportProvider.current() } returnsMany listOf(
            ConnectionTransport.Ethernet,
            ConnectionTransport.Wifi,
        )
        val viewModel = vm()
        viewModel.testOnStart()
        assertEquals(ConnectionTransport.Ethernet, viewModel.testStateValue.transport)

        viewModel.onAction(NetworkDiagnosticsActions.Start(SpeedTestServer.Amsterdam))

        assertEquals(ConnectionTransport.Wifi, viewModel.testStateValue.transport)
    }

    @Test
    fun onStart_keepsCurrentServer_whenTransportArrivesAfterIt() = runTest {
        val gate = CompletableDeferred<SpeedTestServer>()
        coEvery { interactor.currentServer() } coAnswers { gate.await() }
        val viewModel = vm(StandardTestDispatcher(mainDispatcher.dispatcher.scheduler))

        viewModel.testOnStart()
        gate.complete(SpeedTestServer.Amsterdam)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SpeedTestServer.Amsterdam, viewModel.testStateValue.currentServer)
        assertEquals(ConnectionTransport.Wifi, viewModel.testStateValue.transport)
    }

    @Test
    fun onStart_keepsLoadingServer_whenTransportReadThrows() = runTest {
        coEvery { interactor.currentServer() } returns SpeedTestServer.Amsterdam
        every { transportProvider.current() } throws IllegalStateException()

        val viewModel = vm()
        viewModel.testOnStart()

        assertNull(viewModel.testStateValue.transport)
        assertEquals(SpeedTestServer.Amsterdam, viewModel.testStateValue.currentServer)
        verify(exactly = 0) { errorHandler.proceedInvoke(any(), any()) }
    }

    @Test
    fun startRun_keepsNewerTransport_whenAnOlderReadLandsLast() = runTest {
        coEvery { interactor.currentServer() } returns SpeedTestServer.Amsterdam
        every { interactor.run(SpeedTestServer.Amsterdam) } returns flowOf(
            finishedRun(SpeedTestServer.Amsterdam, bytes = 1_000_000)
        )
        var reading = ConnectionTransport.Wifi
        every { transportProvider.current() } answers { reading }
        val held = mutableListOf<Runnable>()
        val holdingDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                held += block
            }
        }
        val viewModel = vm(holdingDispatcher)

        viewModel.testOnStart()
        viewModel.onAction(NetworkDiagnosticsActions.Start(SpeedTestServer.Amsterdam))
        assertEquals(2, held.size)
        // The newer read completes first, then the older one is released.
        held[1].run()
        reading = ConnectionTransport.Ethernet
        held[0].run()

        assertEquals(ConnectionTransport.Wifi, viewModel.testStateValue.transport)
    }

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

    @Test
    fun start_doesNotRereadTransport_whileRunning() = runTest {
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

        verify(exactly = 2) { transportProvider.current() }
    }
}
