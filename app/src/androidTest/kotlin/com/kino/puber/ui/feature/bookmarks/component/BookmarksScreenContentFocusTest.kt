package com.kino.puber.ui.feature.bookmarks.component

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.ComposeTestRule
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
import com.kino.puber.ui.feature.bookmarks.model.BookmarksViewState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookmarksScreenContentFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val items = (1..ItemCount).map { id ->
        VideoItemUIState(id = id, title = "Card $id", imageUrl = "", bigImageUrl = "", showTitle = true, isSaved = true)
    }

    @Test
    fun bookmarksItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu() {
        var shown by mutableStateOf(items)
        composeRule.setContent { Bookmarks(shown) }
        val firstAction = openMenuOnCard(OpeningCard)

        shown = shown.filter { it.title != OpeningCard }
        composeRule.mainClock.advanceTimeBy(LongPressDelayMillis)
        composeRule.waitForIdle()

        firstAction.assertIsFocused()
        assertTrue(
            "no card should hold focus while the menu is open",
            items.none { composeRule.focusedWith(it.title) },
        )
    }

    @Test
    fun bookmarksBackAfterItemChangeFocusesTheNeighbourCard() {
        var shown by mutableStateOf(items)
        composeRule.setContent { Bookmarks(shown) }
        openMenuOnCard(OpeningCard)

        shown = shown.filter { it.title != OpeningCard }
        composeRule.mainClock.advanceTimeBy(LongPressDelayMillis)
        composeRule.waitForIdle()
        pressBack()

        composeRule.waitUntil(FocusTimeoutMillis) { composeRule.focusedWith(Neighbour) }
        composeRule.onNodeWithText(Neighbour).assertIsFocused()
    }

    @Test
    fun bookmarksMenuOpenAndBackReturnsFocusToTheOpeningCard() {
        composeRule.setContent { Bookmarks(items) }
        openMenuOnCard(OpeningCard)

        pressBack()

        composeRule.waitUntil(FocusTimeoutMillis) { composeRule.focusedWith(OpeningCard) }
        composeRule.onNodeWithText(OpeningCard).assertIsFocused()
    }

    @Test
    fun bookmarksDoesNotTakeFocusWhileOuterContentFocusIsInactive() {
        composeRule.setContent { Bookmarks(items, contentFocusActive = false) }
        composeRule.mainClock.advanceTimeBy(SettleMillis)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(items.first().title).assertExists()
        assertTrue(
            "no card should take focus while the outer content focus is inactive",
            items.none { composeRule.focusedWith(it.title) },
        )
    }

    @Composable
    private fun Bookmarks(
        shown: List<VideoItemUIState>,
        contentFocusActive: Boolean = true,
    ) {
        PuberTheme {
            CompositionLocalProvider(LocalContentFocusActive provides contentFocusActive) {
                BookmarksScreenContent(
                    state = BookmarksViewState.Content(selectedFolderId = FolderId, items = shown),
                    onAction = {},
                    onFolderSelected = {},
                )
            }
        }
    }

    /** Focuses [title], opens its context menu with a long Enter press and returns the first action. */
    private fun openMenuOnCard(title: String): SemanticsNodeInteraction {
        composeRule.onNodeWithText(title).requestFocus()
        composeRule.waitUntil(FocusTimeoutMillis) { composeRule.focusedWith(title) }
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
        return firstAction
    }

    // The menu closes through its BackHandler, so BACK goes through the activity like on a remote.
    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    private fun ComposeTestRule.focusedWith(title: String): Boolean =
        onAllNodes(isFocused() and hasText(title)).fetchSemanticsNodes().isNotEmpty()

    private fun SemanticsNodeInteraction.isFocusedNow(): Boolean = runCatching { assertIsFocused() }.isSuccess

    private companion object {
        const val ItemCount = 9
        const val FolderId = 1
        const val OpeningCard = "Card 5"
        const val Neighbour = "Card 6"
        const val SettleMillis = 300L
        const val FocusTimeoutMillis = 5_000L
        const val LongPressDelayMillis = 500L
        const val NextRepeatDelayMillis = 50L
    }
}
