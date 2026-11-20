package com.kino.puber.core.ui.uikit.component.drawer

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive
import androidx.compose.ui.unit.dp
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val ContentTag = "initial-drawer-content"
private const val FocusTimeoutMillis = 10_000L

internal class InitialDrawerContentFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initiallyClosedDrawer_focusesContentThatAppearsAfterLaunch() {
        lateinit var drawerState: DrawerState
        val contentHasFocus = booleanArrayOf(false)

        composeRule.setContent {
            PuberTheme {
                drawerState = rememberDrawerState(DrawerValue.Closed)
                val contentFocusRequester = remember { FocusRequester() }
                val menuFocusRequester = remember { FocusRequester() }
                val handoff = remember(drawerState, contentFocusRequester) {
                    ContentFocusHandoff(drawerState, contentFocusRequester)
                }
                var contentReady by remember { mutableStateOf(false) }

                CompositionLocalProvider(LocalContentFocusHandoff provides handoff) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        handoff = handoff,
                        drawerContent = {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .focusRequester(menuFocusRequester)
                                    .focusable(),
                            )
                        },
                        content = {
                            Box(
                                Modifier
                                    .focusRequester(contentFocusRequester)
                                    .onFocusChanged { contentHasFocus[0] = it.hasFocus }
                                    .focusGroup(),
                            ) {
                                if (contentReady) {
                                    Box(
                                        Modifier
                                            .size(48.dp)
                                            .testTag(ContentTag)
                                            .focusable(),
                                    )
                                }
                            }
                            LaunchedEffect(Unit) {
                                menuFocusRequester.requestFocus()
                                delay(300)
                                contentReady = true
                            }
                            ContentFocusHandoffEffect(
                                handoff = handoff,
                                restartKey = Unit,
                                contentFocusRequester = contentFocusRequester,
                                contentHasFocus = { contentHasFocus[0] },
                            )
                        },
                    )
                }
            }
        }

        composeRule.waitUntil(FocusTimeoutMillis) { contentHasFocus[0] }
        composeRule.onNodeWithTag(ContentTag).assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(DrawerValue.Closed, drawerState.currentValue)
        }
    }

    @Test
    fun initialHandoffWaitsWhileContentIsGatedThenFocusesContent() {
        lateinit var drawerState: DrawerState
        val contentHasFocus = booleanArrayOf(false)
        var contentActive by mutableStateOf(false)

        composeRule.setContent {
            PuberTheme {
                drawerState = rememberDrawerState(DrawerValue.Closed)
                val contentFocusRequester = remember { FocusRequester() }
                val menuFocusRequester = remember { FocusRequester() }
                val dialogFocusRequester = remember { FocusRequester() }
                val handoff = remember(drawerState, contentFocusRequester) {
                    ContentFocusHandoff(drawerState, contentFocusRequester)
                }
                var contentReady by remember { mutableStateOf(false) }

                CompositionLocalProvider(
                    LocalContentFocusHandoff provides handoff,
                    LocalContentFocusActive provides contentActive,
                ) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        handoff = handoff,
                        drawerContent = {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .focusRequester(menuFocusRequester)
                                    .focusable(),
                            )
                        },
                        content = {
                            Box(
                                Modifier
                                    .focusRequester(contentFocusRequester)
                                    .onFocusChanged { contentHasFocus[0] = it.hasFocus }
                                    .focusGroup(),
                            ) {
                                if (contentReady) {
                                    Box(
                                        Modifier
                                            .size(48.dp)
                                            .testTag(ContentTag)
                                            .focusable(),
                                    )
                                }
                            }
                            LaunchedEffect(Unit) {
                                menuFocusRequester.requestFocus()
                                // A modal takes focus while the handoff waits on content, then the
                                // content publishes something focusable under it.
                                delay(100)
                                dialogFocusRequester.requestFocus()
                                delay(200)
                                contentReady = true
                            }
                            ContentFocusHandoffEffect(
                                handoff = handoff,
                                restartKey = Unit,
                                contentFocusRequester = contentFocusRequester,
                                contentHasFocus = { contentHasFocus[0] },
                            )
                        },
                    )
                    Box(
                        Modifier
                            .size(48.dp)
                            .testTag(DialogTag)
                            .focusRequester(dialogFocusRequester)
                            .focusable(),
                    )
                }
            }
        }

        // Longer than the handoff's own timeout: a gated handoff must neither take focus nor give
        // up and reveal the rail.
        composeRule.mainClock.advanceTimeBy(HandoffWaitMillis)
        composeRule.onNodeWithTag(DialogTag).assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(false, contentHasFocus[0])
            assertEquals(DrawerValue.HandingOff, drawerState.currentValue)
        }

        composeRule.runOnIdle { contentActive = true }
        composeRule.waitUntil(FocusTimeoutMillis) { contentHasFocus[0] }
        composeRule.onNodeWithTag(ContentTag).assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(DrawerValue.Closed, drawerState.currentValue)
        }
    }

    @Test
    fun dialogDismissedOverFocusedContent_returnsFocusToContentNotTheRail() {
        lateinit var drawerState: DrawerState
        val contentHasFocus = booleanArrayOf(false)
        var contentActive by mutableStateOf(true)
        var dialogOpen by mutableStateOf(false)

        composeRule.setContent {
            PuberTheme {
                drawerState = rememberDrawerState(DrawerValue.Closed)
                val contentFocusRequester = remember { FocusRequester() }
                val dialogFocusRequester = remember { FocusRequester() }
                val handoff = remember(drawerState, contentFocusRequester) {
                    ContentFocusHandoff(drawerState, contentFocusRequester)
                }

                // The wiring Main applies when a dialog closes the gate over the screens.
                LaunchedEffect(contentActive) {
                    if (!contentActive) drawerState.beginInitialHandoff()
                }

                CompositionLocalProvider(
                    LocalContentFocusHandoff provides handoff,
                    LocalContentFocusActive provides contentActive,
                ) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        handoff = handoff,
                        drawerContent = {
                            Box(Modifier.size(48.dp).focusable())
                        },
                        content = {
                            Box(
                                Modifier
                                    .focusRequester(contentFocusRequester)
                                    .onFocusChanged { contentHasFocus[0] = it.hasFocus }
                                    .focusGroup(),
                            ) {
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .testTag(SettledContentTag)
                                        .focusable(),
                                )
                            }
                            ContentFocusHandoffEffect(
                                handoff = handoff,
                                restartKey = Unit,
                                contentFocusRequester = contentFocusRequester,
                                contentHasFocus = { contentHasFocus[0] },
                            )
                        },
                    )
                    if (dialogOpen) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .testTag(DialogTag)
                                .focusRequester(dialogFocusRequester)
                                .focusable(),
                        )
                        LaunchedEffect(Unit) { dialogFocusRequester.requestFocus() }
                    }
                }
            }
        }

        composeRule.waitUntil(FocusTimeoutMillis) { contentHasFocus[0] }
        composeRule.runOnIdle {
            contentActive = false
            dialogOpen = true
        }
        composeRule.onNodeWithTag(DialogTag).assertIsFocused()

        composeRule.runOnIdle {
            dialogOpen = false
            contentActive = true
        }
        composeRule.waitUntil(FocusTimeoutMillis) {
            contentHasFocus[0] && drawerState.currentValue == DrawerValue.Closed
        }
        composeRule.onNodeWithTag(SettledContentTag).assertIsFocused()
    }
}

private const val DialogTag = "initial-drawer-dialog"
private const val SettledContentTag = "settled-drawer-content"
private const val HandoffWaitMillis = 7_000L
