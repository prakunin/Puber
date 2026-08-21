package com.kino.puber.ui.feature.device.diagnostics.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.diagnostics.DiagnosticStep
import com.kino.puber.domain.interactor.diagnostics.FailureReason
import com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsInteractor
import com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsRun
import com.kino.puber.domain.interactor.diagnostics.SkipReason
import com.kino.puber.domain.interactor.diagnostics.StepState
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsActions
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
    private val apiDomainInteractor = mockk<ApiDomainInteractor>(relaxed = true)
    private val errorHandler = mockk<ErrorHandler>(relaxed = true)
    private val screens = mockk<Screens>(relaxed = true)
    private val router = mockk<AppRouter>(relaxed = true)

    private fun vm(): NetworkDiagnosticsVM {
        every { router.screens } returns screens
        return NetworkDiagnosticsVM(
            interactor = interactor,
            apiDomainInteractor = apiDomainInteractor,
            errorHandler = errorHandler,
            resources = FakeResourceProvider(),
            router = router,
        )
    }

    private fun finishedRun(mirror: String? = null, apiUp: Boolean = true) =
        NetworkDiagnosticsRun(apiDomain = "service-kp.test")
            .with(
                DiagnosticStep.ApiReachability,
                if (apiUp) {
                    StepState.Success(latencyMillis = 120)
                } else {
                    StepState.Failure(FailureReason.Unreachable)
                },
            )
            .copy(workingMirrorDomain = mirror, finished = true)

    @Test
    fun onStart_runsTheDiagnostics_andPublishesTheFinishedRun() = runTest {
        every { interactor.run() } returns flowOf(finishedRun())

        val viewModel = vm()
        viewModel.testOnStart()

        assertFalse(viewModel.testStateValue.running)
        assertTrue(viewModel.testStateValue.finished)
        assertTrue(viewModel.testStateValue.advice?.apiReachable == true)
    }

    @Test
    fun cancel_stopsTheRun_andLeavesTheStepsWhereTheyStood() = runTest {
        val channel = Channel<NetworkDiagnosticsRun>(Channel.UNLIMITED)
        every { interactor.run() } returns channel.consumeAsFlow()

        val viewModel = vm()
        viewModel.testOnStart()
        channel.send(NetworkDiagnosticsRun(apiDomain = "service-kp.test"))
        viewModel.onAction(NetworkDiagnosticsActions.Cancel)

        assertFalse(viewModel.testStateValue.running)
        assertFalse(viewModel.testStateValue.finished)

        // The flag alone proves nothing: it is flipped by the same synchronous call that cancels
        // the job. What actually matters is that collection stopped, so a value sent afterwards —
        // even one that reports the run as finished — must never reach the state. (A value sent to
        // an already-cancelled channel can itself throw; either outcome proves the collector is gone.)
        runCatching {
            channel.send(NetworkDiagnosticsRun(apiDomain = "service-kp.test", finished = true))
        }

        assertFalse(viewModel.testStateValue.finished)
        assertFalse(viewModel.testStateValue.running)
    }

    @Test
    fun restart_startsAFreshRun_afterACancelledOne() = runTest {
        every { interactor.run() } returns flowOf(finishedRun())

        val viewModel = vm()
        viewModel.testOnStart()
        viewModel.onAction(NetworkDiagnosticsActions.Cancel)
        viewModel.onAction(NetworkDiagnosticsActions.Restart)

        assertTrue(viewModel.testStateValue.finished)
    }

    /** The whole point of the confirmation: the run itself must change nothing. */
    @Test
    fun run_changesNoDomain_whenAMirrorIsMerelyProposed() = runTest {
        every { interactor.run() } returns flowOf(finishedRun(mirror = "api.alador.test", apiUp = false))

        val viewModel = vm()
        viewModel.testOnStart()

        assertEquals("api.alador.test", viewModel.testStateValue.advice?.mirrorProposal)
        coVerify(exactly = 0) { apiDomainInteractor.switchToBuiltInDomain(any()) }
    }

    @Test
    fun confirmMirrorSwitch_appliesTheProposedMirror() = runTest {
        every { interactor.run() } returns flowOf(finishedRun(mirror = "api.alador.test", apiUp = false))
        coEvery { apiDomainInteractor.switchToBuiltInDomain("api.alador.test") } returns
            ApiDomainState(domain = "api.alador.test", customDomain = "api.alador.test")

        val viewModel = vm()
        viewModel.testOnStart()
        viewModel.onAction(NetworkDiagnosticsActions.ConfirmMirrorSwitch)

        coVerify(exactly = 1) { apiDomainInteractor.switchToBuiltInDomain("api.alador.test") }
        assertEquals("api.alador.test", viewModel.testStateValue.appliedMirror)
        assertNull(viewModel.testStateValue.advice?.mirrorProposal)
    }

    @Test
    fun confirmMirrorSwitch_doesNothing_whenNoMirrorWasProposed() = runTest {
        every { interactor.run() } returns flowOf(finishedRun())

        val viewModel = vm()
        viewModel.testOnStart()
        viewModel.onAction(NetworkDiagnosticsActions.ConfirmMirrorSwitch)

        coVerify(exactly = 0) { apiDomainInteractor.switchToBuiltInDomain(any()) }
    }

    /**
     * The row a cancel catches mid-measurement has to stop saying "checking". Nothing about the
     * network went wrong, so it settles as a skip rather than a failure.
     */
    @Test
    fun cancel_settlesTheStepThatWasRunning() = runTest {
        val channel = Channel<NetworkDiagnosticsRun>(Channel.UNLIMITED)
        every { interactor.run() } returns channel.consumeAsFlow()

        val viewModel = vm()
        viewModel.testOnStart()
        channel.send(
            NetworkDiagnosticsRun(apiDomain = "service-kp.test")
                .with(DiagnosticStep.ApiReachability, StepState.Success(latencyMillis = 120))
                .with(DiagnosticStep.MediaThroughput, StepState.Running)
        )
        viewModel.onAction(NetworkDiagnosticsActions.Cancel)

        val steps = viewModel.testStateValue.steps.associate { it.step to it.state }
        assertEquals(StepState.Skipped(SkipReason.Cancelled), steps[DiagnosticStep.MediaThroughput])
        assertEquals(StepState.Success(latencyMillis = 120), steps[DiagnosticStep.ApiReachability])
        assertEquals(StepState.Pending, steps[DiagnosticStep.MirrorSweep])
    }

    /**
     * The re-entrancy guard. A second press while the first switch is in flight must not start a
     * second one — the button deliberately stays enabled and focusable while it applies.
     */
    @Test
    fun confirmMirrorSwitch_ignoresASecondPress_whileTheFirstIsStillApplying() = runTest {
        every { interactor.run() } returns flowOf(finishedRun(mirror = "api.alador.test", apiUp = false))
        val gate = CompletableDeferred<Unit>()
        coEvery { apiDomainInteractor.switchToBuiltInDomain("api.alador.test") } coAnswers {
            gate.await()
            ApiDomainState(domain = "api.alador.test", customDomain = "api.alador.test")
        }

        val viewModel = vm()
        viewModel.testOnStart()
        viewModel.onAction(NetworkDiagnosticsActions.ConfirmMirrorSwitch)
        assertTrue(viewModel.testStateValue.applyingMirror)

        viewModel.onAction(NetworkDiagnosticsActions.ConfirmMirrorSwitch)
        gate.complete(Unit)

        coVerify(exactly = 1) { apiDomainInteractor.switchToBuiltInDomain("api.alador.test") }
        assertFalse(viewModel.testStateValue.applyingMirror)
        assertEquals("api.alador.test", viewModel.testStateValue.appliedMirror)
    }

    /**
     * A switch that started against one proposal must not clear a different one that a restart
     * produced while it was in flight — the user would lose an offer nobody declined.
     */
    @Test
    fun confirmMirrorSwitch_leavesAFreshProposalAlone_whenAStaleSwitchReturns() = runTest {
        every { interactor.run() } returns flowOf(finishedRun(mirror = "api.alador.test", apiUp = false))
        val gate = CompletableDeferred<Unit>()
        coEvery { apiDomainInteractor.switchToBuiltInDomain("api.alador.test") } coAnswers {
            gate.await()
            ApiDomainState(domain = "api.alador.test", customDomain = "api.alador.test")
        }

        val viewModel = vm()
        viewModel.testOnStart()
        viewModel.onAction(NetworkDiagnosticsActions.ConfirmMirrorSwitch)

        every { interactor.run() } returns flowOf(finishedRun(mirror = "api.beta.test", apiUp = false))
        viewModel.onAction(NetworkDiagnosticsActions.Restart)
        assertEquals("api.beta.test", viewModel.testStateValue.advice?.mirrorProposal)

        gate.complete(Unit)

        assertEquals("api.beta.test", viewModel.testStateValue.advice?.mirrorProposal)
        assertEquals("api.alador.test", viewModel.testStateValue.appliedMirror)
    }

    /** A switch that failed changed nothing — least of all an earlier one that worked. */
    @Test
    fun confirmMirrorSwitch_keepsTheEarlierConfirmation_whenALaterSwitchFails() = runTest {
        every { interactor.run() } returns flowOf(finishedRun(mirror = "api.alador.test", apiUp = false))
        coEvery { apiDomainInteractor.switchToBuiltInDomain("api.alador.test") } returns
            ApiDomainState(domain = "api.alador.test", customDomain = "api.alador.test")

        val viewModel = vm()
        viewModel.testOnStart()
        viewModel.onAction(NetworkDiagnosticsActions.ConfirmMirrorSwitch)
        assertEquals("api.alador.test", viewModel.testStateValue.appliedMirror)

        every { interactor.run() } returns flowOf(finishedRun(mirror = "api.beta.test", apiUp = false))
        coEvery { apiDomainInteractor.switchToBuiltInDomain("api.beta.test") } returns null
        viewModel.onAction(NetworkDiagnosticsActions.Restart)
        viewModel.onAction(NetworkDiagnosticsActions.ConfirmMirrorSwitch)

        assertEquals("api.alador.test", viewModel.testStateValue.appliedMirror)
    }

    /**
     * Without delegation to `super`, a dismissed message is never cleared and a second identical
     * failure shows the user nothing at all: the message flow still holds the first one, and the
     * snackbar is keyed on its text.
     */
    @Test
    fun snackBarDismissed_clearsTheMessage_soASecondFailureCanBeShown() = runTest {
        every { interactor.run() } returns flowOf(finishedRun(mirror = "api.alador.test", apiUp = false))
        coEvery { apiDomainInteractor.switchToBuiltInDomain("api.alador.test") } returns null

        val viewModel = vm()
        viewModel.testOnStart()
        viewModel.onAction(NetworkDiagnosticsActions.ConfirmMirrorSwitch)
        assertNotNull(viewModel.testMessageValue)

        viewModel.onAction(CommonAction.SnackBarDismissed)

        assertNull(viewModel.testMessageValue)
    }
}
