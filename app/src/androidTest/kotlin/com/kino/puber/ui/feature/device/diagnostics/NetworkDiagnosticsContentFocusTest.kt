package com.kino.puber.ui.feature.device.diagnostics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.domain.interactor.diagnostics.DiagnosticStep
import com.kino.puber.domain.interactor.diagnostics.DiagnosticsAdvice
import com.kino.puber.domain.interactor.diagnostics.QualityCeiling
import com.kino.puber.domain.interactor.diagnostics.SkipReason
import com.kino.puber.domain.interactor.diagnostics.StepState
import com.kino.puber.ui.feature.device.diagnostics.model.DiagnosticStepUi
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsActions
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsViewState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

// Generous on purpose: on a real television createComposeRule() launches a fresh Activity per
// test method, and the screen's own auto-focus (rememberFocusRequesterOnLaunch) waits out a
// 100ms debounce on top of that cold start. 3s proved too tight running on shared lab hardware.
private const val FocusTimeoutMillis = 8_000L

/**
 * Proves the diagnostics screen is drivable with nothing but a remote control: starting, cancelling
 * and restarting a run, and confirming a proposed mirror switch, all happen on the button the D-pad
 * already has focus on.
 */
internal class NetworkDiagnosticsContentFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Starting and stopping the test are both one press on the row that already holds focus. */
    @Test
    fun runningStateOffersCancelOnTheFocusedButton() {
        val actions = mutableListOf<UIAction>()
        setContent(state = runningState(), onAction = actions::add)

        awaitFocus(NetworkDiagnosticsTestTags.PrimaryAction)
        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.PrimaryAction).assertIsFocused()
        composeRule.onNodeWithText(context.getString(R.string.diagnostics_cancel)).assertExists()

        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.PrimaryAction).press(Key.DirectionCenter)

        assertEquals(listOf<UIAction>(NetworkDiagnosticsActions.Cancel), actions)
    }

    @Test
    fun finishedStateOffersRestartOnTheFocusedButton() {
        val actions = mutableListOf<UIAction>()
        setContent(state = finishedState(), onAction = actions::add)

        awaitFocus(NetworkDiagnosticsTestTags.PrimaryAction)
        composeRule.onNodeWithText(context.getString(R.string.diagnostics_restart)).assertExists()

        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.PrimaryAction).press(Key.DirectionCenter)

        assertEquals(listOf<UIAction>(NetworkDiagnosticsActions.Restart), actions)
    }

    /**
     * A proposed change is only fair to confirm with one press if it was explained first — so the
     * sentence naming what is being proposed has to actually be on screen alongside the button.
     */
    @Test
    fun mirrorProposalExplainsTheProposedChange() {
        val state = proposalState()
        setContent(state = state)

        awaitFocus(NetworkDiagnosticsTestTags.MirrorSwitch)

        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.MirrorProposal).assertExists()
        composeRule
            .onNodeWithText(
                context.getString(
                    R.string.diagnostics_mirror_proposal,
                    state.apiDomain,
                    state.advice?.mirrorProposal,
                )
            )
            .assertExists()
    }

    /**
     * The proposal is a change to the user's settings, so it must be the thing under their thumb
     * when the run ends — and reachable by nothing but the remote.
     */
    @Test
    fun mirrorProposalTakesFocusAndConfirmsOnPress() {
        val actions = mutableListOf<UIAction>()
        setContent(state = proposalState(), onAction = actions::add)

        awaitFocus(NetworkDiagnosticsTestTags.MirrorSwitch)
        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.MirrorSwitch).assertIsFocused()

        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.MirrorSwitch).press(Key.DirectionCenter)

        assertEquals(listOf<UIAction>(NetworkDiagnosticsActions.ConfirmMirrorSwitch), actions)
    }

    /**
     * A switch in flight stays on the focus chain — it no longer disables — and says so with its
     * own label rather than dropping the user's thumb off the button they just pressed.
     */
    @Test
    fun mirrorSwitchShowsItsOwnLabelWhileApplyingAndStaysFocused() {
        val actions = mutableListOf<UIAction>()
        setContent(state = proposalState().copy(applyingMirror = true), onAction = actions::add)

        awaitFocus(NetworkDiagnosticsTestTags.MirrorSwitch)
        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.MirrorSwitch).assertIsFocused()
        composeRule
            .onNodeWithText(context.getString(R.string.diagnostics_mirror_switching))
            .assertExists()

        // Staying on the focus chain while applying only proves something if the button is still
        // a real, pressable button rather than a cosmetic label — so a press must still land.
        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.MirrorSwitch).press(Key.DirectionCenter)

        assertEquals(listOf<UIAction>(NetworkDiagnosticsActions.ConfirmMirrorSwitch), actions)
    }

    @Test
    fun restartStaysReachableFromTheProposalWithTheDPad() {
        setContent(state = proposalState())

        awaitFocus(NetworkDiagnosticsTestTags.MirrorSwitch)
        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.MirrorSwitch).press(Key.DirectionRight)

        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.PrimaryAction).assertIsFocused()
    }

    /**
     * Once a switch resolves the proposal leaves composition and takes the button the user's thumb
     * was on with it — so focus has to be handed back to the primary action rather than dropped.
     */
    @Test
    fun primaryActionRegainsFocusOnceTheProposalIsResolved() {
        val proposal = proposalState()
        var state by mutableStateOf(proposal)
        composeRule.setContent {
            PuberTheme {
                NetworkDiagnosticsContent(state = state)
            }
        }
        awaitFocus(NetworkDiagnosticsTestTags.MirrorSwitch)

        state = finishedState().copy(appliedMirror = proposal.advice?.mirrorProposal)

        awaitFocus(NetworkDiagnosticsTestTags.PrimaryAction)
        composeRule.onNodeWithTag(NetworkDiagnosticsTestTags.PrimaryAction).assertIsFocused()
    }

    /** A skipped step must read as skipped, not as a failure. */
    @Test
    fun skippedStepsAreDrawnWithTheirOwnReason() {
        setContent(state = finishedState())

        composeRule
            .onNodeWithText(context.getString(R.string.diagnostics_skipped_mirror_ok))
            .assertExists()
    }

    private fun setContent(
        state: NetworkDiagnosticsViewState,
        onAction: (UIAction) -> Unit = {},
    ) {
        composeRule.setContent {
            PuberTheme {
                NetworkDiagnosticsContent(state = state, onAction = onAction)
            }
        }
    }

    // Queries the tagged node directly and reads its own Focused property, rather than scanning
    // every focused node on screen for one whose tag happens to match. Button and Surface apply
    // the caller's modifier — testTag, focusRequester and the clickable/focusable behaviour alike
    // — to the same underlying node (confirmed by inspecting the tv-material 1.1.0 Surface/Button
    // implementation), so this is not just simpler but exactly what the widget guarantees.
    private fun awaitFocus(tag: String) {
        composeRule.waitUntil(FocusTimeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().any { node ->
                node.config.getOrNull(SemanticsProperties.Focused) == true
            }
        }
    }

    private fun SemanticsNodeInteraction.press(key: Key): SemanticsNodeInteraction {
        performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        composeRule.waitForIdle()
        return this
    }

    private fun runningState() = NetworkDiagnosticsViewState(
        steps = listOf(
            DiagnosticStepUi(DiagnosticStep.ApiReachability, StepState.Success(latencyMillis = 142)),
            DiagnosticStepUi(DiagnosticStep.NameResolution, StepState.Success(latencyMillis = 38)),
            DiagnosticStepUi(DiagnosticStep.ApiResponsiveness, StepState.Success(latencyMillis = 340)),
            DiagnosticStepUi(DiagnosticStep.MediaThroughput, StepState.Running),
            DiagnosticStepUi(
                DiagnosticStep.MirrorSweep,
                StepState.Skipped(SkipReason.CurrentMirrorAnswers),
            ),
        ),
        apiDomain = "api.example.test",
        running = true,
    )

    private fun finishedState() = runningState().copy(
        steps = runningState().steps.map { row ->
            if (row.step == DiagnosticStep.MediaThroughput) {
                row.copy(state = StepState.Success(latencyMillis = null))
            } else {
                row
            }
        },
        running = false,
        finished = true,
        advice = DiagnosticsAdvice(
            apiReachable = true,
            mediaBitsPerSecond = 18_000_000.0,
            ceiling = QualityCeiling.Hd1080,
            mirrorProposal = null,
        ),
    )

    private fun proposalState() = finishedState().copy(
        advice = DiagnosticsAdvice(
            apiReachable = false,
            mediaBitsPerSecond = null,
            ceiling = null,
            mirrorProposal = "api.alador.test",
        ),
        workingMirrorDomain = "api.alador.test",
    )
}
