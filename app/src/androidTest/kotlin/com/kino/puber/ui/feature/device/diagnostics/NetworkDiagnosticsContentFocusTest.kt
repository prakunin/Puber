package com.kino.puber.ui.feature.device.diagnostics

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performClick
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.component.drawer.DrawerValue
import com.kino.puber.core.ui.uikit.component.drawer.LocalDrawerState
import com.kino.puber.core.ui.uikit.component.drawer.rememberDrawerState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.data.api.network.diagnostics.ThroughputSample
import com.kino.puber.domain.interactor.diagnostics.ServerTestState
import com.kino.puber.domain.interactor.diagnostics.SpeedTestServer
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsActions
import com.kino.puber.ui.feature.device.diagnostics.model.NetworkDiagnosticsViewState
import com.kino.puber.ui.feature.device.diagnostics.model.ServerSpeedUi
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val FocusTimeoutMillis = 3_000L

internal class NetworkDiagnosticsContentFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initialStateStartsAmsterdamFromTheFocusedCard() {
        val actions = mutableListOf<UIAction>()
        setContent(NetworkDiagnosticsViewState(), actions::add)
        val amsterdamTag = NetworkDiagnosticsTestTags.server(SpeedTestServer.Amsterdam.name)

        awaitFocus(amsterdamTag)
        composeRule.onNodeWithTag(amsterdamTag).press(Key.DirectionCenter)

        assertEquals(
            listOf<UIAction>(NetworkDiagnosticsActions.Start(SpeedTestServer.Amsterdam)),
            actions,
        )
    }

    @Test
    fun launchFocusRecovers_whenTheRailTakesFocusDuringTheSettleDelay() {
        lateinit var drawerValue: () -> DrawerValue
        composeRule.setContent {
            PuberTheme {
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val railRequester = remember { FocusRequester() }
                drawerValue = { drawerState.currentValue }

                CompositionLocalProvider(LocalDrawerState provides drawerState) {
                    Box {
                        NetworkDiagnosticsContent(NetworkDiagnosticsViewState())
                        Box(
                            Modifier
                                .focusRequester(railRequester)
                                .onFocusChanged { focus ->
                                    if (focus.hasFocus) drawerState.reveal() else drawerState.focusExited()
                                }
                                .focusable(),
                        )
                    }
                    LaunchedEffect(Unit) {
                        // Reproduce the navigation gap: the old screen is gone before the new
                        // screen's delayed focus request lands, so the focus system falls to rail.
                        delay(20)
                        railRequester.requestFocus()
                    }
                }
            }
        }

        val amsterdamTag = NetworkDiagnosticsTestTags.server(SpeedTestServer.Amsterdam.name)
        awaitFocus(amsterdamTag)
        composeRule.onNodeWithTag(amsterdamTag).assertIsFocused()
        composeRule.runOnIdle { assertEquals(DrawerValue.Closed, drawerValue()) }
    }

    @Test
    fun focusedServerCardKeepsFocus_whenTheRunStarts() {
        var state by mutableStateOf(
            NetworkDiagnosticsViewState(currentServer = SpeedTestServer.Amsterdam)
        )
        composeRule.setContent {
            PuberTheme { NetworkDiagnosticsContent(state) }
        }
        val amsterdamTag = NetworkDiagnosticsTestTags.server(SpeedTestServer.Amsterdam.name)
        awaitFocus(amsterdamTag)

        state = state.copy(
            running = true,
            servers = listOf(
                ServerSpeedUi(SpeedTestServer.Amsterdam, ServerTestState.Running()),
                ServerSpeedUi(SpeedTestServer.Moscow),
            ),
        )

        composeRule.onNodeWithTag(amsterdamTag).assertIsFocused()
    }

    @Test
    fun serverCard_startsThatServerOnPress() {
        val actions = mutableListOf<UIAction>()
        setContent(proposalState(), actions::add)

        composeRule
            .onNodeWithTag(NetworkDiagnosticsTestTags.server(SpeedTestServer.Moscow.name))
            .assertHasClickAction()
            .performClick()

        assertEquals(
            listOf<UIAction>(
                NetworkDiagnosticsActions.Start(SpeedTestServer.Moscow)
            ),
            actions,
        )
    }

    @Test
    fun serverCardKeepsFocusWhenResultsChange() {
        var state by mutableStateOf(proposalState())
        composeRule.setContent {
            PuberTheme { NetworkDiagnosticsContent(state) }
        }
        val amsterdamTag = NetworkDiagnosticsTestTags.server(SpeedTestServer.Amsterdam.name)
        awaitFocus(amsterdamTag)

        state = proposalState().copy(
            recommendedServer = null,
        )

        awaitFocus(amsterdamTag)
        composeRule.onNodeWithTag(amsterdamTag).assertIsFocused()
    }

    @Test
    fun currentServerCardRegainsFocusAfterStateRestoration() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            PuberTheme { NetworkDiagnosticsContent(proposalState()) }
        }
        val amsterdamTag = NetworkDiagnosticsTestTags.server(SpeedTestServer.Amsterdam.name)
        awaitFocus(amsterdamTag)

        restoration.emulateSavedInstanceStateRestore()

        awaitFocus(amsterdamTag)
        composeRule.onNodeWithTag(amsterdamTag).assertIsFocused()
    }

    private fun setContent(
        state: NetworkDiagnosticsViewState,
        onAction: (UIAction) -> Unit = {},
    ) {
        composeRule.setContent {
            PuberTheme { NetworkDiagnosticsContent(state, onAction) }
        }
    }

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

    private fun proposalState() = NetworkDiagnosticsViewState(
        currentServer = SpeedTestServer.Amsterdam,
        running = false,
        finished = true,
        recommendedServer = SpeedTestServer.Moscow,
        servers = listOf(
            ServerSpeedUi(
                SpeedTestServer.Amsterdam,
                ServerTestState.Success(ThroughputSample(10_000_000, 1_000)),
            ),
            ServerSpeedUi(
                SpeedTestServer.Moscow,
                ServerTestState.Success(ThroughputSample(20_000_000, 1_000)),
            ),
        ),
    )
}
