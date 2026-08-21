package com.kino.puber.ui.feature.device.diagnostics.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.logger.log
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsInteractor
import com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsRun
import com.kino.puber.domain.interactor.diagnostics.SpeedTestServer
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsActions
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsViewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

internal class NetworkDiagnosticsVM(
    private val interactor: NetworkDiagnosticsInteractor,
    override val errorHandler: ErrorHandler,
    router: AppRouter,
) : PuberVM<NetworkDiagnosticsViewState>(router) {

    private var runJob: Job? = null

    override val initialViewState = NetworkDiagnosticsViewState()

    override fun onStart() {
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

    override fun onAction(action: UIAction) {
        when (action) {
            is NetworkDiagnosticsActions.Start -> startRun(action.server)
            else -> super.onAction(action)
        }
    }

    private fun startRun(server: SpeedTestServer) {
        if (stateValue.running) return
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
