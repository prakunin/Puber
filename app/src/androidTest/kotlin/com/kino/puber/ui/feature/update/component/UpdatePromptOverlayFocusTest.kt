package com.kino.puber.ui.feature.update.component

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.modifier.ContentFocusGate
import com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.data.repository.AppVersion
import com.kino.puber.data.repository.AvailableUpdate
import com.kino.puber.ui.feature.root.component.REVEAL_FADE_MS
import com.kino.puber.ui.feature.root.component.STARTUP_SPLASH_TAG
import com.kino.puber.ui.feature.root.component.StartupSplashHost
import com.kino.puber.ui.feature.update.model.UpdatePromptAction
import com.kino.puber.ui.feature.update.model.UpdatePromptViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UpdatePromptOverlayFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val focusedSentinels = mutableStateListOf<String>()

    private val updateText get() = InstrumentationRegistry.getInstrumentation().targetContext
        .getString(R.string.update_prompt_update)

    private val laterText get() = InstrumentationRegistry.getInstrumentation().targetContext
        .getString(R.string.update_prompt_not_now)

    private val checkingText get() = InstrumentationRegistry.getInstrumentation().targetContext
        .getString(R.string.update_prompt_checking_title)

    private val available = UpdatePromptViewState.Available(
        AvailableUpdate(
            version = AppVersion(9, 9, 9),
            tagName = "v9.9.9",
            title = "",
            releaseNotes = null,
            apkAssetName = "",
            apkDownloadUrl = "",
            apkSizeBytes = null,
            checksumDownloadUrl = null,
            releasePageUrl = null,
        ),
    )

    @Test
    fun updatePromptShownBeforeContentKeepsFocusOnUpdateAfterLateClaims() {
        var published by mutableStateOf(false)
        composeRule.setContent {
            PuberTheme {
                Box(Modifier.fillMaxSize()) {
                    UpdatePromptOverlay(state = available, onAction = {})
                    // Content composes after the prompt, as Main and Home do at launch.
                    ContentFocusGate(active = false) {
                        val launchTarget = rememberFocusRequesterOnLaunch()
                        val hero = remember { FocusRequester() }
                        val contentActive = LocalContentFocusActive.current
                        Box(Modifier.size(40.dp).focusRequester(launchTarget).focusable())
                        Box(Modifier.size(40.dp).focusRequester(hero).focusable())
                        LaunchedEffect(published) {
                            if (published && contentActive) hero.requestFocus()
                        }
                    }
                }
            }
        }
        awaitFocus()
        assertFocusOn(updateText)

        composeRule.runOnIdle { published = true }
        composeRule.mainClock.advanceTimeBy(2_000)

        assertFocusOn(updateText)
    }

    @Test
    fun updatePromptOpenedUnderTheSplashHasFocusOnUpdateAfterReveal() {
        var state by mutableStateOf<UpdatePromptViewState>(UpdatePromptViewState.Hidden)
        var covered by mutableStateOf(true)
        val actions = mutableListOf<UIAction>()
        composeRule.setContent {
            PuberTheme {
                StartupSplashHost(covered) {
                    Box(Modifier.fillMaxSize()) {
                        ContentFocusGate(active = state == UpdatePromptViewState.Hidden) {
                            val launchTarget = rememberFocusRequesterOnLaunch()
                            Box(Modifier.size(40.dp).focusRequester(launchTarget).focusable())
                        }
                        UpdatePromptOverlay(state = state, onAction = { actions += it })
                    }
                }
            }
        }

        composeRule.runOnIdle { state = available }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag(STARTUP_SPLASH_TAG).assertExists()
        composeRule.runOnIdle { covered = false }
        composeRule.mainClock.advanceTimeBy(REVEAL_FADE_MS + 32L)

        composeRule.onNodeWithTag(STARTUP_SPLASH_TAG).assertDoesNotExist()
        assertFocusOn(updateText)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
        assertEquals(listOf<UIAction>(UpdatePromptAction.DismissClicked), actions.toList())
    }

    @Test
    fun updatePromptShownOverFocusedContentTakesFocusWithinOneSecond() {
        var state by mutableStateOf<UpdatePromptViewState>(UpdatePromptViewState.Hidden)
        val card = FocusRequester()
        composeRule.setContent {
            PuberTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.size(40.dp).focusRequester(card).focusable())
                    UpdatePromptOverlay(state = state, onAction = {})
                }
            }
        }
        composeRule.runOnIdle { card.requestFocus() }
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { state = available }
        composeRule.mainClock.advanceTimeBy(1_000)

        assertFocusOn(updateText)
    }

    @Test
    fun updatePromptUpdateButtonSendsUpdateClicked() {
        val actions = mutableListOf<UIAction>()
        composeRule.setContent {
            PuberTheme { UpdatePromptOverlay(state = available, onAction = { actions += it }) }
        }
        awaitFocus()

        press(Key.DirectionCenter)
        composeRule.waitForIdle()

        assertEquals(listOf<UIAction>(UpdatePromptAction.UpdateClicked), actions.toList())
    }

    @Test
    fun updatePromptDpadInAllDirectionsStaysInsidePrompt() {
        composeRule.setContent {
            PuberTheme {
                Box(Modifier.fillMaxSize()) {
                    SentinelsBeneath()
                    UpdatePromptOverlay(state = available, onAction = {})
                }
            }
        }
        awaitFocus()

        // Update first: Up, Down and Left have no target in the prompt; Right moves to Later, from
        // where Up, Down and Right have none and Left moves back.
        listOf(
            Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
            Key.DirectionUp, Key.DirectionDown, Key.DirectionRight, Key.DirectionLeft,
        ).forEachIndexed { index, key ->
            press(key)
            composeRule.waitForIdle()
            assertTrue(
                "Focus left the prompt on press #$index ($key): $focusedSentinels",
                focusedSentinels.isEmpty(),
            )
            when (index) {
                3 -> assertFocusOn(laterText)
                7 -> assertFocusOn(updateText)
            }
        }
    }

    @Test
    fun updatePromptKeepsFocusInsideWhenUpdateStartsTheDownload() {
        var state by mutableStateOf<UpdatePromptViewState>(available)
        composeRule.setContent {
            PuberTheme {
                Box(Modifier.fillMaxSize()) {
                    SentinelsBeneath()
                    UpdatePromptOverlay(state = state, onAction = {})
                }
            }
        }
        awaitFocus()
        assertFocusOn(updateText)

        composeRule.runOnIdle { state = UpdatePromptViewState.Downloading(available.update, 10) }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { state = UpdatePromptViewState.Downloading(available.update, 40) }
        composeRule.mainClock.advanceTimeBy(1_000)

        assertTrue("Focus left the prompt: $focusedSentinels", focusedSentinels.isEmpty())
        composeRule.onAllNodes(isFocused()).assertCountEquals(1)
        listOf(Key.DirectionDown, Key.DirectionLeft, Key.DirectionUp, Key.DirectionRight).forEach {
            press(it)
            composeRule.waitForIdle()
            assertTrue("Focus left the prompt on $it: $focusedSentinels", focusedSentinels.isEmpty())
        }
    }

    @Test
    fun updatePromptCheckingTakesFocusFromContentBeneath() {
        var state by mutableStateOf<UpdatePromptViewState>(UpdatePromptViewState.Hidden)
        val card = FocusRequester()
        composeRule.setContent {
            PuberTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.size(40.dp).focusRequester(card).focusable())
                    UpdatePromptOverlay(state = state, onAction = {})
                }
            }
        }
        composeRule.runOnIdle { card.requestFocus() }
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { state = UpdatePromptViewState.Checking }
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onAllNodes(isFocused()).assertCountEquals(1)
        composeRule.onNode(isFocused() and hasAnyChild(hasText(checkingText))).assertExists()
    }

    @Test
    fun updatePromptRetriesTheFocusClaimUntilTheButtonCanTakeIt() {
        var ready by mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuberTheme {
                // Focus cannot enter the prompt for the first 300 ms, so a single claim at 100 ms is refused.
                Box(
                    Modifier
                        .fillMaxSize()
                        .focusProperties { onEnter = { if (!ready) cancelFocusChange() } }
                        .focusGroup(),
                ) {
                    UpdatePromptOverlay(state = available, onAction = {})
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(300)
        composeRule.onAllNodes(isFocused()).assertCountEquals(0)
        composeRule.runOnIdle { ready = true }
        composeRule.mainClock.advanceTimeBy(700)

        assertFocusOn(updateText)
    }

    private fun awaitFocus() {
        composeRule.waitUntil(FocusTimeoutMillis) {
            // The window may hand focus to a sentinel before the prompt claims it.
            focusedSentinels.isEmpty() &&
                composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertFocusOn(text: String) {
        composeRule.onNode(isFocused() and hasAnyChild(hasText(text))).assertExists()
    }

    private fun press(key: Key) {
        composeRule.onNode(isFocused()).performKeyInput {
            keyDown(key)
            keyUp(key)
        }
    }

    private companion object {
        const val FocusTimeoutMillis = 3_000L
    }

    @Composable
    private fun SentinelsBeneath() {
        Sentinel("top", Alignment.TopCenter)
        Sentinel("bottom", Alignment.BottomCenter)
        Sentinel("left", Alignment.CenterStart)
        Sentinel("right", Alignment.CenterEnd)
    }

    @Composable
    private fun Sentinel(name: String, alignment: Alignment) {
        Box(Modifier.fillMaxSize(), contentAlignment = alignment) {
            Box(
                Modifier
                    .size(40.dp)
                    .testTag(name)
                    .onFocusChanged {
                        if (it.isFocused) focusedSentinels.add(name) else focusedSentinels.remove(name)
                    }
                    .focusable(),
            )
        }
    }
}