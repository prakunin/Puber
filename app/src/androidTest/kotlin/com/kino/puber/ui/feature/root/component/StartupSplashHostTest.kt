package com.kino.puber.ui.feature.root.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import org.junit.Rule
import org.junit.Test

class StartupSplashHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentUnderTheSplashTakesLaunchFocus() {
        composeRule.setContent {
            PuberTheme {
                StartupSplashHost(covered = true) {
                    val launchTarget = rememberFocusRequesterOnLaunch()
                    Box(
                        Modifier
                            .fillMaxSize()
                            .size(40.dp)
                            .testTag(CONTENT_TAG)
                            .focusRequester(launchTarget)
                            .focusable(),
                    )
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(500)

        composeRule.onNodeWithTag(STARTUP_SPLASH_TAG).assertExists()
        composeRule.onNodeWithTag(CONTENT_TAG).assertIsFocused()
    }

    @Test
    fun splashLeavesCompositionAfterTheFade() {
        var covered by mutableStateOf(true)
        composeRule.setContent { PuberTheme { StartupSplashHost(covered) { Box(Modifier.fillMaxSize()) } } }
        composeRule.onNodeWithTag(STARTUP_SPLASH_TAG).assertExists()

        composeRule.runOnIdle { covered = false }
        composeRule.mainClock.advanceTimeBy(REVEAL_FADE_MS + 32L)

        composeRule.onNodeWithTag(STARTUP_SPLASH_TAG).assertDoesNotExist()
    }

    @Test
    fun splashStaysWhileCovered() {
        composeRule.setContent { PuberTheme { StartupSplashHost(covered = true) { Box(Modifier.fillMaxSize()) } } }

        composeRule.mainClock.advanceTimeBy(3_000)

        composeRule.onNodeWithTag(STARTUP_SPLASH_TAG).assertExists()
    }

    @Test
    fun splashIsDrawnInTheFirstFrame() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { PuberTheme { StartupSplashHost(covered = true) { Box(Modifier.fillMaxSize()) } } }

        composeRule.onNodeWithTag(STARTUP_SPLASH_TAG).assertExists()
    }

    private companion object {
        const val CONTENT_TAG = "content"
    }
}
