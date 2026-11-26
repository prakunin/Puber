package com.kino.puber.ui.feature.device.diagnostics.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.logger.log
import com.kino.puber.core.system.ConnectionTransportProvider
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsInteractor
import com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsRun
import com.kino.puber.domain.interactor.diagnostics.SpeedTestServer
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsActions
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsViewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

internal class NetworkDiagnosticsVM(
    private val interactor: NetworkDiagnosticsInteractor,
    private val transportProvider: ConnectionTransportProvider,
    private val ioDispatcher: CoroutineDispatcher,
    override val errorHandler: ErrorHandler,
    router: AppRouter,
) : PuberVM<NetworkDiagnosticsViewState>(router) {

    private var runJob: Job? = null
    private var transportJob: Job? = null

    override val initialViewState = NetworkDiagnosticsViewState()

    override fun onStart() {
        refreshTransport()
        launch {
            try {
                val currentServer = interactor.currentServer()
                updateViewState(stateValue.copy(currentServer = currentServer))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                log(error, "Failed to load the current media server for the speed test")
            }
        }
    }

    override fun handleAction(action: UIAction) {
        when (action) {
            is NetworkDiagnosticsActions.Start -> startRun(action.server)
            else -> super.handleAction(action)
        }
    }

    private fun refreshTransport() {
        transportJob?.cancel()
        transportJob = launch {
            try {
                val transport = withContext(ioDispatcher) { transportProvider.current() }
                updateViewState(stateValue.copy(transport = transport))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                log(error, "Failed to read the connection transport for the speed test")
            }
        }
    }

    private fun startRun(server: SpeedTestServer) {
        if (stateValue.running) return
        refreshTransport()
        runJob?.cancel()
        updateViewState(
            stateValue.copy(
                running = true,
                finished = false,
                recommendedServer = null,
            )
        )
        runJob = launch {
            interactor.run(server).collect { run -> publish(server, run) }
        }
    }

    private fun publish(server: SpeedTestServer, run: NetworkDiagnosticsRun) {
        val servers = stateValue.servers.map { row ->
            if (row.server == server) row.copy(state = run.state(server)) else row
        }
        val combinedRun = NetworkDiagnosticsRun(
            currentServer = run.currentServer,
            measurements = servers.associate { it.server to it.state },
            finished = run.finished,
        )
        updateViewState(
            stateValue.copy(
                servers = servers,
                currentServer = run.currentServer,
                running = !run.finished,
                finished = run.finished,
                recommendedServer = if (run.finished) combinedRun.recommendedServer else null,
            )
        )
    }
}
