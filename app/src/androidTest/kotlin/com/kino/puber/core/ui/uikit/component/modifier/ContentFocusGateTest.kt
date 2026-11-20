package com.kino.puber.core.ui.uikit.component.modifier

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ContentFocusGateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun launchRequestScheduledBeforeGateClosesDoesNotTakeFocus() {
        var autoFocusEnabled by mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuberTheme {
                CompositionLocalProvider(LocalAutoFocusOnLaunchEnabled provides autoFocusEnabled) {
                    val requester = rememberFocusRequesterOnLaunch()
                    Box(
                        Modifier
                            .size(48.dp)
                            .focusRequester(requester)
                            .focusable()
                            .testTag(TargetTag),
                    )
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(50)
        composeRule.runOnIdle { autoFocusEnabled = false }
        composeRule.mainClock.advanceTimeBy(450)

        composeRule.onNodeWithTag(TargetTag).assertIsNotFocused()

        // The cancelled request must not count as made: reopening the gate claims focus again.
        composeRule.runOnIdle { autoFocusEnabled = true }
        composeRule.mainClock.advanceTimeBy(500)

        composeRule.onNodeWithTag(TargetTag).assertIsFocused()
    }

    @Test
    fun contentFocusGateKeepsOuterInactiveValue() {
        var contentActive: Boolean? = null
        var autoFocusEnabled: Boolean? = null
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContentFocusActive provides false,
                LocalAutoFocusOnLaunchEnabled provides false,
            ) {
                ContentFocusGate(active = true) {
                    contentActive = LocalContentFocusActive.current
                    autoFocusEnabled = LocalAutoFocusOnLaunchEnabled.current
                }
            }
        }

        composeRule.runOnIdle {
            assertEquals(false, contentActive)
            assertEquals(false, autoFocusEnabled)
        }
    }

    @Test
    fun launchRequestReArmsWhenGateOpens() {
        var gateActive by mutableStateOf(false)
        composeRule.setContent {
            PuberTheme {
                ContentFocusGate(active = gateActive) {
                    val requester = rememberFocusRequesterOnLaunch()
                    Box(
                        Modifier
                            .size(48.dp)
                            .focusRequester(requester)
                            .focusable()
                            .testTag(TargetTag),
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag(TargetTag).assertIsNotFocused()

        composeRule.runOnIdle { gateActive = true }
        composeRule.mainClock.advanceTimeBy(500)

        composeRule.onNodeWithTag(TargetTag).assertIsFocused()
    }

    private companion object {
        const val TargetTag = "target"
    }
}
