package com.kino.puber.core.ui

import com.kino.puber.core.startup.StartupReveal
import com.kino.puber.core.telemetry.StartupContentState
import com.kino.puber.core.telemetry.Telemetry
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import com.kino.puber.ui.feature.player.model.PlayerViewState
import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.MainDispatcherExtension
import com.kino.puber.util.RecordingTelemetryRecorder
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private sealed interface RevealState {
    data object Loading : RevealState
    data class Content(val n: Int) : RevealState, StartupContentState
    data object Failed : RevealState
}

private sealed interface RevealOverlayState {
    data object Hidden : RevealOverlayState
    data object Available : RevealOverlayState
}

private class RevealVM(router: AppRouter) : PuberVM<RevealState>(router) {
    override val initialViewState: RevealState = RevealState.Loading

    fun publish(state: RevealState) = updateViewState(state)
}

private class RevealOverlayVM(router: AppRouter) : PuberVM<RevealOverlayState>(router) {
    override val initialViewState: RevealOverlayState = RevealOverlayState.Hidden

    fun publish(state: RevealOverlayState) = updateViewState(state)
}

internal class PuberVMRevealTest {
    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    @AfterEach
    fun tearDown() {
        StartupReveal.resetForTests()
        Telemetry.resetForTests()
    }

    private fun vm() = RevealVM(mockk(relaxed = true))

    @Test
    fun firstStartupContentStateRevealsTheSplash() {
        Telemetry.install(RecordingTelemetryRecorder(), FakeTelemetryClock(elapsed = 1_000))
        StartupReveal.begin()

        vm().publish(RevealState.Content(1))

        assertFalse(StartupReveal.isCovered)
    }

    @Test
    fun revealDoesNotDependOnTelemetryRecording() {
        StartupReveal.begin()

        vm().publish(RevealState.Content(1))

        assertFalse(StartupReveal.isCovered)
    }

    @Test
    fun loadingErrorAndOverlayStatesKeepTheSplash() {
        StartupReveal.begin()
        val landing = vm()

        landing.publish(RevealState.Failed)
        landing.publish(RevealState.Loading)
        RevealOverlayVM(mockk(relaxed = true)).publish(RevealOverlayState.Available)
        assertTrue(StartupReveal.isCovered)

        landing.publish(RevealState.Content(1))
        assertFalse(StartupReveal.isCovered)
    }

    @Test
    fun contentLinkTargetStatesRevealTheSplash() {
        assertTrue(StartupContentState::class.java.isAssignableFrom(DetailsScreenState.Content::class.java))
        assertTrue(StartupContentState::class.java.isAssignableFrom(PlayerViewState.Content::class.java))
    }
}
