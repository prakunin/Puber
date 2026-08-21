package com.kino.puber.ui.feature.device.diagnostics.vm

import com.kino.puber.R
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.logger.log
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.diagnostics.DiagnosticStep
import com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsInteractor
import com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsRun
import com.kino.puber.domain.interactor.diagnostics.advise
import com.kino.puber.ui.feature.device.diagnostics.model.DiagnosticStepUi
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsActions
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsViewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

internal class NetworkDiagnosticsVM(
    private val interactor: NetworkDiagnosticsInteractor,
    private val apiDomainInteractor: ApiDomainInteractor,
    override val errorHandler: ErrorHandler,
    private val resources: ResourceProvider,
    router: AppRouter,
) : PuberVM<NetworkDiagnosticsViewState>(router) {

    /**
     * The run, and the only thing cancelling has to reach. The interactor writes nothing, so
     * cancelling the job is the whole story — there is no partial change to undo.
     */
    private var runJob: Job? = null

    override val initialViewState = NetworkDiagnosticsViewState()

    override fun onStart() {
        startRun()
    }

    override fun onAction(action: UIAction) {
        when (action) {
            NetworkDiagnosticsActions.Cancel -> cancelRun()
            NetworkDiagnosticsActions.Restart -> startRun()
            NetworkDiagnosticsActions.ConfirmMirrorSwitch -> applyProposedMirror()
        }
    }

    private fun startRun() {
        runJob?.cancel()
        updateViewState(
            NetworkDiagnosticsViewState(
                running = true,
                // The mirror applied in an earlier run is a fact about the app, not about this run,
                // so it survives a restart while every measurement starts over.
                appliedMirror = stateValue.appliedMirror,
            )
        )
        runJob = launch {
            interactor.run().collect(::publish)
        }
    }

    private fun cancelRun() {
        runJob?.cancel()
        runJob = null
        updateViewState(stateValue.copy(running = false))
    }

    private fun publish(run: NetworkDiagnosticsRun) {
        updateViewState(
            stateValue.copy(
                steps = DiagnosticStep.entries.map { DiagnosticStepUi(it, run.state(it)) },
                apiDomain = run.apiDomain,
                running = !run.finished,
                finished = run.finished,
                advice = if (run.finished) advise(run) else null,
            )
        )
    }

    private fun applyProposedMirror() {
        val proposal = stateValue.advice?.mirrorProposal ?: return
        if (stateValue.applyingMirror) return

        updateViewState(stateValue.copy(applyingMirror = true))
        launch {
            // Caught rather than left to the shared exception handler: that handler runs only after
            // this coroutine has already unwound, so it cannot clear `applyingMirror` or reach the
            // statements below it — a throw here would leave the flag stuck for the life of the
            // screen and block every future confirm attempt.
            val applied = try {
                apiDomainInteractor.switchToBuiltInDomain(proposal)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                log(error, "Failed to switch to the proposed mirror $proposal")
                null
            }
            updateViewState(
                stateValue.copy(
                    applyingMirror = false,
                    appliedMirror = applied?.domain,
                    // Only clear the proposal this call actually acted on: a restart in the meantime
                    // may have produced a fresh proposal that this now-stale coroutine never saw, and
                    // must not silently erase.
                    advice = if (stateValue.advice?.mirrorProposal == proposal) {
                        stateValue.advice?.copy(mirrorProposal = null)
                    } else {
                        stateValue.advice
                    },
                )
            )
            if (applied == null) {
                showMessage(resources.getString(R.string.diagnostics_mirror_switch_failed))
            }
        }
    }
}
