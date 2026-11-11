package com.kino.puber.ui.feature.favorites.content

import android.view.KeyEvent
import androidx.compose.runtime.Composable
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
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.favorites.model.FavoriteViewState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FavoriteScreenContentFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val firstRow = (1..RowSize).map(::card)
    private val secondRow = (SecondRowStart until SecondRowStart + RowSize).map(::card)
    private val titles = (firstRow + secondRow).map { it.title }

    @Test
    fun favoriteItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu() {
        var grid by mutableStateOf(grid(secondRow))
        composeRule.setContent { Favorites(grid) }
        val firstAction = openMenuOnCard(OpeningCard)

        grid = grid(secondRow.filter { it.title != OpeningCard })
        composeRule.mainClock.advanceTimeBy(LongPressDelayMillis)
        composeRule.waitForIdle()

        firstAction.assertIsFocused()
        assertTrue(
            "no card should hold focus while the menu is open",
            titles.none { composeRule.focusedWith(it) },
        )
    }

    @Test
    fun favoriteBackAfterItemChangeFocusesTheNeighbourCard() {
        var grid by mutableStateOf(grid(secondRow))
        composeRule.setContent { Favorites(grid) }
        openMenuOnCard(OpeningCard)

        grid = grid(secondRow.filter { it.title != OpeningCard })
        composeRule.mainClock.advanceTimeBy(LongPressDelayMillis)
        composeRule.waitForIdle()
        pressBack()

        composeRule.waitUntil(FocusTimeoutMillis) { composeRule.focusedWith(Neighbour) }
        composeRule.onNodeWithText(Neighbour).assertIsFocused()
    }

    @Test
    fun favoriteRemoveFromMenuFocusesTheNeighbourCard() {
        var grid by mutableStateOf(grid(secondRow))
        composeRule.setContent {
            Favorites(grid) { action ->
                if (action is CommonAction.ItemSavedChanged<*>) {
                    grid = grid(secondRow.filter { it.title != OpeningCard })
                }
            }
        }
        openMenuOnCard(OpeningCard)

        chooseRemoveFromMenu()

        composeRule.waitUntil(FocusTimeoutMillis) { composeRule.focusedWith(Neighbour) }
        composeRule.onNodeWithText(Neighbour).assertIsFocused()
    }

    @Test
    fun favoriteMenuOpenAndBackReturnsFocusToTheOpeningCard() {
        composeRule.setContent { Favorites(grid(secondRow)) }
        openMenuOnCard(OpeningCard)

        pressBack()

        composeRule.waitUntil(FocusTimeoutMillis) { composeRule.focusedWith(OpeningCard) }
        composeRule.onNodeWithText(OpeningCard).assertIsFocused()
    }

    @Composable
    private fun Favorites(
        grid: VideoGridUIState,
        onAction: (UIAction) -> Unit = {},
    ) {
        PuberTheme {
            FavoriteScreenContent(
                state = FavoriteViewState.Content(
                    gridState = grid,
                    selectedItem = VideoDetailsUIState.Loading.copy(isLoading = false),
                ),
                onAction = onAction,
            )
        }
    }

    private fun grid(second: List<VideoItemUIState>) = VideoGridUIState(
        list = listOf(
            VideoGridItemUIState.Items(items = firstRow, rowKey = "first"),
            VideoGridItemUIState.Items(items = second, rowKey = "second"),
        ),
    )

    /** Walks to [title] (second row) with D-pad keys, long-presses Enter to open its menu, returns the first action. */
    private fun openMenuOnCard(title: String): SemanticsNodeInteraction {
        composeRule.waitUntil(FocusTimeoutMillis) { titles.any { composeRule.focusedWith(it) } }
        // A direct requestFocus on a second-row card does not stick; walk there with the remote.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.waitForIdle()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
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

    /** Walks down the open menu with D-pad keys to "Remove from bookmarks" and selects it. */
    private fun chooseRemoveFromMenu() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val removeTitle = instrumentation.targetContext.getString(R.string.context_menu_remove_from_bookmarks)
        val remove = composeRule.onNodeWithText(removeTitle, useUnmergedTree = true).onParent()
        repeat(MenuActionCount) {
            if (!remove.isFocusedNow()) {
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
                composeRule.waitForIdle()
            }
        }
        composeRule.waitUntil(FocusTimeoutMillis) { remove.isFocusedNow() }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
    }

    // The menu closes through its BackHandler, so BACK goes through the activity like on a remote.
    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    private fun card(id: Int) =
        VideoItemUIState(id = id, title = "Card $id", imageUrl = "", bigImageUrl = "", showTitle = true, isSaved = true)

    private fun ComposeTestRule.focusedWith(title: String): Boolean =
        onAllNodes(isFocused() and hasText(title)).fetchSemanticsNodes().isNotEmpty()

    private fun SemanticsNodeInteraction.isFocusedNow(): Boolean = runCatching { assertIsFocused() }.isSuccess

    private companion object {
        const val RowSize = 4
        const val SecondRowStart = 11
        const val OpeningCard = "Card 12"
        const val Neighbour = "Card 13"
        const val MenuActionCount = 2
        const val FocusTimeoutMillis = 5_000L
        const val LongPressDelayMillis = 500L
        const val NextRepeatDelayMillis = 50L
    }
}
