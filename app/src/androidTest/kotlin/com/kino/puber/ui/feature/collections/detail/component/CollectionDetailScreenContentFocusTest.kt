package com.kino.puber.ui.feature.collections.detail.component

import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.collections.detail.model.CollectionDetailViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CollectionDetailScreenContentFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val items = (1..ItemCount).map { id ->
        VideoItemUIState(id = id, title = "Card $id", imageUrl = "", bigImageUrl = "")
    }

    @Test
    fun collectionDetailFocusReturnsToTheFocusedCardAfterReentry() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { Detail(items) }
        val title = focusAwayFromTheFirstCard()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.waitUntil(FocusTimeoutMillis) { composeRule.focusedWith(title) }
        composeRule.onNodeWithText(title).assertIsFocused()
    }

    @Test
    fun collectionDetailFocusesTheFirstCardOnFirstLoad() {
        composeRule.setContent { Detail(items) }

        composeRule.waitUntil(FocusTimeoutMillis) { composeRule.focusedWith(items.first().title) }
        composeRule.onNodeWithText(items.first().title).assertIsFocused()
    }

    @Test
    fun collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu() {
        var shown by mutableStateOf(items)
        composeRule.setContent { Detail(shown) }
        val title = focusAwayFromTheFirstCard()
        val focusedId = items.first { it.title == title }.id
        val firstActionTitle = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.context_menu_watch_movie)

        composeRule.onNode(isFocused()).performKeyInput {
            keyDown(Key.Enter)
            advanceEventTime(LongPressDelayMillis)
        }
        val firstAction = composeRule.onNodeWithText(firstActionTitle, useUnmergedTree = true).onParent()
        composeRule.waitUntil(FocusTimeoutMillis) { firstAction.isFocusedNow() }
        firstAction.performKeyInput {
            advanceEventTime(NextRepeatDelayMillis)
            keyUp(Key.Enter)
        }

        shown = shown.filter { it.id != focusedId }
        composeRule.mainClock.advanceTimeBy(LongPressDelayMillis)
        composeRule.waitForIdle()

        firstAction.assertIsFocused()
        assertTrue(
            "no card should hold focus while the menu is open",
            items.none { composeRule.focusedWith(it.title) },
        )

        // The menu closes through its BackHandler, so BACK goes through the activity like on a remote.
        val neighbour = items[items.indexOfFirst { it.id == focusedId } + 1].title
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitUntil(FocusTimeoutMillis) { composeRule.focusedWith(neighbour) }
        composeRule.onNodeWithText(neighbour).assertIsFocused()
    }

    @Test
    fun collectionDetailFocusStaysOnTheFocusedCardWhenContentIsReplaced() {
        var shown by mutableStateOf(items)
        composeRule.setContent { Detail(shown) }
        val title = focusCardInRowFour()

        shown = shown.map { if (it.title == title) it.copy(isSaved = !it.isSaved) else it }
        composeRule.mainClock.advanceTimeBy(SettleMillis)
        composeRule.waitForIdle()

        composeRule.waitUntil(FocusTimeoutMillis) { composeRule.focusedWith(title) }
        composeRule.onNodeWithText(title).assertIsFocused()
        composeRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun collectionDetailFocusMovesToTheNeighbourWhenTheFocusedCardLeavesReplacedContent() {
        var shown by mutableStateOf(items)
        composeRule.setContent { Detail(shown) }
        val title = focusCardInRowFour()
        val neighbour = items[items.indexOfFirst { it.title == title } + 1].title

        shown = shown.filter { it.title != title }
        composeRule.mainClock.advanceTimeBy(SettleMillis)
        composeRule.waitForIdle()

        runCatching {
            composeRule.waitUntil(FocusTimeoutMillis) { composeRule.focusedWith(neighbour) }
        }.onFailure {
            val focused = items.filter { composeRule.focusedWith(it.title) }.joinToString { it.title }
            assertTrue("expected $neighbour focused, focused: ${focused.ifEmpty { "none" }}", false)
        }
        composeRule.onNodeWithText(neighbour).assertIsFocused()
        composeRule.onNodeWithText(neighbour).assertIsDisplayed()
        assertTrue("focus must not fall back to the first card", !composeRule.focusedWith(items.first().title))
    }

    @androidx.compose.runtime.Composable
    private fun Detail(shown: List<VideoItemUIState>) {
        PuberTheme {
            CompositionLocalProvider(LocalContentFocusActive provides true) {
                CollectionDetailScreenContent(
                    state = CollectionDetailViewState.Content(title = "Collection", items = shown),
                    onAction = {},
                    onItemClick = {},
                )
            }
        }
    }

    private fun focusAwayFromTheFirstCard(): String {
        composeRule.onNodeWithText(items.first().title).requestFocus()
        composeRule.onNode(isFocused()).performDirection(Key.DirectionDown)
        composeRule.mainClock.advanceTimeBy(SettleMillis)
        composeRule.onNode(isFocused()).performDirection(Key.DirectionRight)
        composeRule.mainClock.advanceTimeBy(SettleMillis)
        val title = items.map { it.title }.first { composeRule.focusedWith(it) }
        assertTrue("focus moved off the first card", title != items.first().title)
        return title
    }

    private fun focusCardInRowFour(): String {
        composeRule.onNodeWithText(items.first().title).requestFocus()
        repeat(RowFourSteps) {
            composeRule.onNode(isFocused()).performDirection(Key.DirectionDown)
            composeRule.mainClock.advanceTimeBy(SettleMillis)
        }
        val title = items.map { it.title }.first { composeRule.focusedWith(it) }
        assertEquals(RowFourCard, title)
        return title
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.focusedWith(title: String): Boolean =
        onAllNodes(isFocused() and hasText(title)).fetchSemanticsNodes().isNotEmpty()

    private fun SemanticsNodeInteraction.isFocusedNow(): Boolean = runCatching { assertIsFocused() }.isSuccess

    private fun SemanticsNodeInteraction.performDirection(key: Key) {
        performKeyInput {
            keyDown(key)
            keyUp(key)
        }
    }

    private companion object {
        const val ItemCount = 24
        const val RowFourSteps = 3
        const val RowFourCard = "Card 10"
        const val SettleMillis = 300L
        const val FocusTimeoutMillis = 5_000L
        const val LongPressDelayMillis = 500L
        const val NextRepeatDelayMillis = 50L
    }
}
