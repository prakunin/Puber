package com.kino.puber.ui.feature.player.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.kino.puber.core.ui.uikit.component.modifier.ContentFocusGate
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.player.model.PlayerViewState
import com.kino.puber.ui.feature.player.model.ResumeDialogState
import org.junit.Rule
import org.junit.Test

/** The Player's launch claims wait for the gate that a dialog above the screens closes. */
internal class PlayerLaunchFocusGateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var gateActive by mutableStateOf(false)

    @Test
    fun errorOverlayWaitsForTheGateBeforeTakingFocus() {
        composeRule.setContent {
            PuberTheme {
                ContentFocusGate(active = gateActive) {
                    PlayerScreenContent(
                        state = PlayerViewState.Error("failed"),
                        onAction = {},
                        exoPlayer = { null },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodes(isFocused()).assertCountEquals(0)

        composeRule.runOnIdle { gateActive = true }
        composeRule.waitForIdle()

        composeRule.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test
    fun resumeDialogWaitsForTheGateBeforeTakingFocus() {
        composeRule.setContent {
            PuberTheme {
                ContentFocusGate(active = gateActive) {
                    ResumeDialog(
                        state = ResumeDialogState(savedPosition = 1_000L, formattedTime = "0:01"),
                        onResume = {},
                        onStartFromBeginning = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodes(isFocused()).assertCountEquals(0)

        composeRule.runOnIdle { gateActive = true }
        composeRule.waitForIdle()

        composeRule.onAllNodes(isFocused()).assertCountEquals(1)
    }
}
