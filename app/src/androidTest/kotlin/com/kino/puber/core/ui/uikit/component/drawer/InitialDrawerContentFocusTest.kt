package com.kino.puber.core.ui.uikit.component.drawer

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
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
}
