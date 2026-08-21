package com.kino.puber.ui.feature.device.diagnostics.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.diagnostics.DiagnosticStep
import com.kino.puber.domain.interactor.diagnostics.FailureReason
import com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsInteractor
import com.kino.puber.domain.interactor.diagnostics.NetworkDiagnosticsRun
import com.kino.puber.domain.interactor.diagnostics.StepState
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsActions
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
}
