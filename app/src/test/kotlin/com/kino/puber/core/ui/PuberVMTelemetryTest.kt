package com.kino.puber.core.ui

import com.kino.puber.core.telemetry.ErrSource
import com.kino.puber.core.telemetry.StartupContentState
import com.kino.puber.core.telemetry.StartupMarks
import com.kino.puber.core.telemetry.Telemetry
import com.kino.puber.core.telemetry.TelemetryErrorTree
import com.kino.puber.core.telemetry.TelemetryEvent
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.auth.model.AuthViewState
import com.kino.puber.ui.feature.home.model.HomeViewState
import com.kino.puber.ui.feature.update.model.UpdatePromptViewState
import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.MainDispatcherExtension
import com.kino.puber.util.RecordingTelemetryRecorder
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import timber.log.Timber

private sealed interface TestState {
    data object Loading : TestState
    data class Content(val n: Int) : TestState, StartupContentState
    data object Failed : TestState
}

private sealed interface OverlayState {
    data object Hidden : OverlayState
    data object Available : OverlayState
}

private class OverlayVM(router: AppRouter) : PuberVM<OverlayState>(router) {
    override val initialViewState: OverlayState = OverlayState.Hidden

    fun publish(state: OverlayState) = updateViewState(state)
}

private data class Handled(val id: Int) : UIAction
private data object Unhandled : UIAction

private class TestVM(router: AppRouter) : PuberVM<TestState>(router) {
    override val initialViewState: TestState = TestState.Loading

    var handled = 0

    fun publish(state: TestState) = updateViewState(state)

    fun publishMessage(text: String) = showMessage(text)

    fun failInLaunch() {
        launch { error("boom") }
    }

    override fun handleAction(action: UIAction) {
        when (action) {
            is Handled -> handled++
            else -> super.handleAction(action)
        }
    }
}

internal class PuberVMTelemetryTest {
    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val clock = FakeTelemetryClock(elapsed = 1_000)
    private val recorder = RecordingTelemetryRecorder()
    private val tree = TelemetryErrorTree()

    @BeforeEach
    fun setUp() {
        Telemetry.install(recorder, clock)
    }

    @AfterEach
    fun tearDown() {
        if (tree in Timber.forest()) Timber.uproot(tree)
        Telemetry.resetForTests()
    }

    private fun vm() = TestVM(mockk(relaxed = true))

    @Test
    fun stateAndActionProbesAreSilentWhenOff() {
        recorder.isEnabled = false
        val vm = vm()
        vm.publish(TestState.Content(1))
        vm.onAction(Handled(1))
        vm.onAction(CommonAction.SnackBarDismissed)
        assertEquals(0, recorder.events.size)
        assertEquals(TestState.Content(1), vm.testStateValue)
        assertEquals(1, vm.handled)
    }

    @Test
    fun readyOnlyOnStateClassChangeWithMsSinceOpen() {
        val vm = vm()
        clock.elapsed = 1_250
        vm.publish(TestState.Content(1))
        clock.elapsed = 1_300
        vm.publish(TestState.Content(2))
        clock.elapsed = 1_400
        vm.publish(TestState.Failed)

        val ready = recorder.events.filterIsInstance<TelemetryEvent.Ready>()
        assertEquals(listOf(TestState.Content::class.java, TestState.Failed::class.java), ready.map { it.state })
        assertEquals(listOf(250L, 400L), ready.map { it.sinceOpenMs })
        assertSame(TestVM::class.java, ready.first().vm)
    }

    @Test
    fun aDataClassOnlyStateNeverFiresASecondReady() {
        // Four view models keep one data class as their state (spec F11): documented gap.
        val vm = vm()
        vm.publish(TestState.Content(1))
        vm.publish(TestState.Content(2))
        vm.publish(TestState.Content(3))
        assertEquals(1, recorder.events.filterIsInstance<TelemetryEvent.Ready>().size)
    }

    @Test
    fun firstContentIsTheFirstContentStateOfTheLandingScreenAndNeverALaterScreen() {
        val startups = mutableListOf<TelemetryEvent.Startup>()
        Telemetry.startupMarks = StartupMarks(0, 0, startups::add, clock)
        val landing = vm()
        val later = vm()
        assertTrue(startups.isEmpty())

        clock.elapsed = 1_500
        landing.publish(TestState.Content(1))
        clock.elapsed = 40_000
        later.publish(TestState.Content(2))

        assertEquals(listOf(1_500L), startups.map { it.firstContentMs })
    }

    @Test
    fun loadingThenErrorThenContentRecordsFirstContentAtTheContent() {
        val startups = mutableListOf<TelemetryEvent.Startup>()
        Telemetry.startupMarks = StartupMarks(0, 0, startups::add, clock)
        val home = vm()

        clock.elapsed = 1_200
        home.publish(TestState.Failed)
        assertTrue(startups.isEmpty())
        clock.elapsed = 3_000
        home.publish(TestState.Content(1))

        assertEquals(listOf(3_000L), startups.map { it.firstContentMs })
    }

    @Test
    fun anOverlayViewModelReadyBeforeTheLandingContentDoesNotMarkFirstContent() {
        val startups = mutableListOf<TelemetryEvent.Startup>()
        Telemetry.startupMarks = StartupMarks(0, 0, startups::add, clock)
        val home = vm()
        val updatePrompt = OverlayVM(mockk(relaxed = true))

        clock.elapsed = 1_100
        updatePrompt.publish(OverlayState.Available)
        assertTrue(startups.isEmpty())
        clock.elapsed = 2_500
        home.publish(TestState.Content(1))

        assertEquals(listOf(2_500L), startups.map { it.firstContentMs })
    }

    @Test
    fun theRealLandingStatesAreMarkedAndErrorLoadingAndOverlayStatesAreNot() {
        assertTrue(HomeViewState.Content() as Any is StartupContentState)
        assertTrue(AuthViewState.Content("c", "u") as Any is StartupContentState)
        assertFalse(HomeViewState.Loading() as Any is StartupContentState)
        assertFalse(HomeViewState.Error("e") as Any is StartupContentState)
        assertFalse(AuthViewState.Loading() as Any is StartupContentState)
        assertFalse(UpdatePromptViewState.Hidden as Any is StartupContentState)
        assertFalse(UpdatePromptViewState.CheckError("e") as Any is StartupContentState)
    }

    @Test
    fun noReadyMeansNoFirstContentFromTheInitialState() {
        val startups = mutableListOf<TelemetryEvent.Startup>()
        Telemetry.startupMarks = StartupMarks(0, 0, startups::add, clock)
        vm()
        assertTrue(startups.isEmpty())
    }

    @Test
    fun actRecordedForHandledAndUnhandledActions() {
        val vm = vm()
        vm.publishMessage("stale")
        vm.onAction(Handled(1))
        vm.onAction(Unhandled)
        vm.onAction(CommonAction.SnackBarDismissed)

        val acts = recorder.events.filterIsInstance<TelemetryEvent.Act>()
        val expected = listOf(Handled::class.java, Unhandled::class.java, CommonAction.SnackBarDismissed::class.java)
        assertEquals(expected, acts.map { it.action })
        assertEquals(1, vm.handled)
        assertNull(vm.testMessageValue)
    }

    @Test
    fun coroutineFailureInLaunchReachesErrThroughLoggerWithThrowable() {
        Timber.plant(tree)
        vm().failInLaunch()

        val err = recorder.events.filterIsInstance<TelemetryEvent.Err>().single()
        assertEquals(ErrSource.LOG, err.src)
    }
}
