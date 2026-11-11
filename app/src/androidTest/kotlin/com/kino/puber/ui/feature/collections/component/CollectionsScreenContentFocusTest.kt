package com.kino.puber.ui.feature.collections.component

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.collections.model.CollectionUIState
import com.kino.puber.ui.feature.collections.model.CollectionsViewState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CollectionsScreenContentFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val collections = (1..ItemCount).map { id ->
        CollectionUIState(id = id, title = "Collection $id", imageUrl = "", wideImageUrl = "", count = 1)
    }

    // Written from the composition so the test can enter the restored host the way the rail does.
    private var host: FocusRequester? = null

    @Test
    fun collectionsHostEntryAfterReentryFocusesTheFocusedCard() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            PuberTheme {
                CompositionLocalProvider(LocalContentFocusActive provides true) {
                    val requester = remember { FocusRequester() }
                    host = requester
                    Box(Modifier.focusRequester(requester).focusRestorer().focusGroup()) {
                        CollectionsScreenContent(
                            state = CollectionsViewState.Content(collections),
                            onCollectionClick = {},
                            onLoadMore = {},
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText(collections.first().title).requestFocus()
        composeRule.onNode(isFocused()).performDirection(Key.DirectionDown)
        composeRule.mainClock.advanceTimeBy(SettleMillis)
        composeRule.onNode(isFocused()).performDirection(Key.DirectionRight)
        composeRule.mainClock.advanceTimeBy(SettleMillis)
        val title = focusedTitle()
        assertTrue("focus moved off the first card", title != collections.first().title)

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.runOnUiThread { host!!.requestFocus() }

        composeRule.waitUntil(FocusTimeoutMillis) {
            composeRule.onAllNodes(isFocused() and hasText(title)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(title).assertIsFocused()
    }

    private fun focusedTitle(): String = collections.map { it.title }.first { title ->
        composeRule.onAllNodes(isFocused() and hasText(title)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun SemanticsNodeInteraction.performDirection(key: Key) {
        performKeyInput {
            keyDown(key)
            keyUp(key)
        }
    }

    private companion object {
        const val ItemCount = 24
        const val SettleMillis = 300L
        const val FocusTimeoutMillis = 5_000L
    }
}
