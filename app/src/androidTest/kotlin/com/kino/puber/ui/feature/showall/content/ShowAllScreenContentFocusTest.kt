package com.kino.puber.ui.feature.showall.content

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.showall.model.ShowAllFilterState
import com.kino.puber.ui.feature.showall.model.ShowAllViewState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val TestTitle = "Фильмы · Новинки"

class ShowAllScreenContentFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val items = (1..ItemCount).map { id ->
        VideoItemUIState(id = id, title = "Card $id", imageUrl = "", bigImageUrl = "")
    }

    @Test
    fun showAllHeaderShowsTheTitleAndFirstFocusStaysOnTheFirstCard() {
        composeRule.setContent {
            PuberTheme {
                CompositionLocalProvider(LocalContentFocusActive provides true) {
                    ShowAllScreenContent(
                        title = TestTitle,
                        state = ShowAllViewState.Content(items),
                        onAction = {},
                    )
                }
            }
        }
        awaitAnyFocus()

        composeRule.onNodeWithText(TestTitle).assertIsDisplayed().assert(!isFocused())
        composeRule.onNodeWithText(items.first().title).assertIsFocused()
    }

    @Test
    fun showAllHeaderIsShownInLoadingAndErrorStates() {
        var state by mutableStateOf<ShowAllViewState>(ShowAllViewState.Loading)
        composeRule.setContent {
            PuberTheme {
                CompositionLocalProvider(LocalContentFocusActive provides true) {
                    ShowAllScreenContent(title = TestTitle, state = state, onAction = {})
                }
            }
        }
        composeRule.onNodeWithText(TestTitle).assertIsDisplayed()

        state = ShowAllViewState.Error("boom")

        composeRule.onNodeWithText(TestTitle).assertIsDisplayed()
    }

    @Test
    fun showAllFocusReturnsToTheFocusedCardAfterReentry() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            PuberTheme {
                CompositionLocalProvider(LocalContentFocusActive provides true) {
                    ShowAllScreenContent(title = TestTitle, state = ShowAllViewState.Content(items), onAction = {})
                }
            }
        }
        awaitAnyFocus()
        composeRule.onNode(isFocused()).performDirection(Key.DirectionDown)
        composeRule.mainClock.advanceTimeBy(SettleMillis)
        composeRule.onNode(isFocused()).performDirection(Key.DirectionRight)
        composeRule.mainClock.advanceTimeBy(SettleMillis)
        val title = focusedTitle()
        assertTrue("focus moved off the first card", title != items.first().title)

        restoration.emulateSavedInstanceStateRestore()

        composeRule.waitUntil(FocusTimeoutMillis) {
            composeRule.onAllNodesFocusedWith(title)
        }
        composeRule.onNodeWithText(title).assertIsFocused()
    }

    @Test
    fun activeFilter_focusReturnsToTheFocusedCardAfterReentry() {
        val filter = ShowAllFilterState(available = true, isActive = true, genreTitle = "Драма")
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            PuberTheme {
                CompositionLocalProvider(LocalContentFocusActive provides true) {
                    ShowAllScreenContent(
                        title = TestTitle,
                        state = ShowAllViewState.Content(items),
                        onAction = {},
                        filter = filter,
                    )
                }
            }
        }
        awaitAnyFocus()
        composeRule.onNode(isFocused()).performDirection(Key.DirectionDown)
        composeRule.mainClock.advanceTimeBy(SettleMillis)
        repeat(2) {
            composeRule.onNode(isFocused()).performDirection(Key.DirectionRight)
            composeRule.mainClock.advanceTimeBy(SettleMillis)
        }
        val title = focusedTitle()
        assertTrue(
            "focus is on card 3 of row 2",
            items.indexOfFirst { it.title == title } == GridColumns + 2,
        )

        restoration.emulateSavedInstanceStateRestore()

        composeRule.waitUntil(FocusTimeoutMillis) {
            composeRule.onAllNodesFocusedWith(title)
        }
        composeRule.onNodeWithText(title).assertIsFocused()
    }

    @Test
    fun showAllFocusReturnsToACardBelowTheFirstViewportAfterReentry() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            PuberTheme {
                CompositionLocalProvider(LocalContentFocusActive provides true) {
                    ShowAllScreenContent(title = TestTitle, state = ShowAllViewState.Content(items), onAction = {})
                }
            }
        }
        awaitAnyFocus()
        repeat(DeepRows) {
            composeRule.onNode(isFocused()).performDirection(Key.DirectionDown)
            composeRule.mainClock.advanceTimeBy(SettleMillis)
        }
        val title = focusedTitle()
        assertTrue(
            "focus moved below the first viewport",
            items.indexOfFirst { it.title == title } >= DeepRows * GridColumns,
        )
        val first = composeRule.onAllNodesWithText(items.first().title)
        assertTrue(
            "card 1 is off the viewport",
            (0 until first.fetchSemanticsNodes().size).none { first[it].isDisplayed() },
        )

        restoration.emulateSavedInstanceStateRestore()

        composeRule.waitUntil(FocusTimeoutMillis) {
            composeRule.onAllNodesFocusedWith(title)
        }
        composeRule.onNodeWithText(title).assertIsFocused()
    }

    @Test
    fun showAllFocusMovesToTheNeighbourWhenTheFocusedCardLeftTheListBeforeReentry() {
        // A plain var: only the restored composition reads the new list, so the entry fallback runs.
        var shown = items
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            PuberTheme {
                CompositionLocalProvider(LocalContentFocusActive provides true) {
                    ShowAllScreenContent(title = TestTitle, state = ShowAllViewState.Content(shown), onAction = {})
                }
            }
        }
        awaitAnyFocus()
        repeat(DeepRows) {
            composeRule.onNode(isFocused()).performDirection(Key.DirectionDown)
            composeRule.mainClock.advanceTimeBy(SettleMillis)
        }
        val index = items.indexOfFirst { it.title == focusedTitle() }
        shown = items.filterIndexed { i, _ -> i != index }

        restoration.emulateSavedInstanceStateRestore()

        val neighbour = shown[index].title
        composeRule.waitUntil(FocusTimeoutMillis) {
            composeRule.onAllNodesFocusedWith(neighbour)
        }
        composeRule.onNodeWithText(neighbour).assertIsFocused()
        composeRule.onNodeWithText(items[index].title).assertDoesNotExist()
    }

    @Test
    fun showAllFocusMovesToTheNeighbourWhenTheFocusedCardLeavesTheList() {
        var shown by mutableStateOf(items)
        composeRule.setContent {
            PuberTheme {
                CompositionLocalProvider(LocalContentFocusActive provides true) {
                    ShowAllScreenContent(title = TestTitle, state = ShowAllViewState.Content(shown), onAction = {})
                }
            }
        }
        awaitAnyFocus()
        composeRule.onNode(isFocused()).performDirection(Key.DirectionDown)
        composeRule.mainClock.advanceTimeBy(SettleMillis)
        val index = items.indexOfFirst { it.title == focusedTitle() }

        shown = items.filterIndexed { i, _ -> i != index }

        val neighbour = shown[index].title
        composeRule.waitUntil(FocusTimeoutMillis) {
            composeRule.onAllNodesFocusedWith(neighbour)
        }
        composeRule.onNodeWithText(neighbour).assertIsFocused()
    }

    @Test
    fun showAllDoesNotTakeFocusAfterReentryWhileContentFocusIsInactive() {
        var active by mutableStateOf(true)
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            PuberTheme {
                CompositionLocalProvider(LocalContentFocusActive provides active) {
                    ShowAllScreenContent(title = TestTitle, state = ShowAllViewState.Content(items), onAction = {})
                }
            }
        }
        awaitAnyFocus()
        composeRule.onNode(isFocused()).performDirection(Key.DirectionDown)
        composeRule.mainClock.advanceTimeBy(SettleMillis)

        active = false
        restoration.emulateSavedInstanceStateRestore()
        composeRule.mainClock.advanceTimeBy(SettleMillis)
        composeRule.waitForIdle()

        assertTrue(
            "no node should hold focus",
            composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun showAllLoadMoreFiresAgainWhenAPageLandsInsideTheThreshold() {
        var shown by mutableStateOf(items.take(SHOW_ALL_GRID_COLUMNS * 2))
        var loadMoreCount = 0
        composeRule.setContent {
            PuberTheme {
                CompositionLocalProvider(LocalContentFocusActive provides true) {
                    ShowAllScreenContent(
                        title = TestTitle,
                        state = ShowAllViewState.Content(shown),
                        onAction = { if (it == CommonAction.LoadMore) loadMoreCount++ },
                    )
                }
            }
        }
        composeRule.waitUntil(FocusTimeoutMillis) { loadMoreCount >= 1 }
        composeRule.waitForIdle()
        val beforeAppend = loadMoreCount

        shown = items.take(SHOW_ALL_GRID_COLUMNS * 4)

        composeRule.waitUntil(FocusTimeoutMillis) { loadMoreCount > beforeAppend }
    }

    private fun awaitAnyFocus() {
        composeRule.waitUntil(FocusTimeoutMillis) {
            composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun focusedTitle(): String = items.map { it.title }.first { title ->
        composeRule.onAllNodesFocusedWith(title)
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesFocusedWith(
        title: String,
    ): Boolean = onAllNodes(isFocused() and hasText(title)).fetchSemanticsNodes().isNotEmpty()

    private fun SemanticsNodeInteraction.performDirection(key: Key) {
        performKeyInput {
            keyDown(key)
            keyUp(key)
        }
    }

    private companion object {
        const val DeepRows = 5
        const val GridColumns = SHOW_ALL_GRID_COLUMNS
        const val ItemCount = (DeepRows + 3) * SHOW_ALL_GRID_COLUMNS
        const val SettleMillis = 300L
        const val FocusTimeoutMillis = 5_000L
    }
}
