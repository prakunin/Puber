package com.kino.puber.ui.feature.showall.content

import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.R
import com.kino.puber.core.ui.navigation.component.LocalScreenKey
import com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.data.api.models.Genre
import com.kino.puber.ui.feature.showall.model.FilterGenres
import com.kino.puber.ui.feature.showall.model.ShowAllAction
import com.kino.puber.ui.feature.showall.model.ShowAllFilterState
import com.kino.puber.ui.feature.showall.model.ShowAllViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val FilterTestTitle = "Фильмы · Все"

/**
 * The header's filter buttons against the grid: first focus, the Up and Down pins between them and
 * row one, the reset, and the panel opened from «Фильтры». The screen content is hosted without a
 * view model: the test holds the state and the filter, and applies the actions the way the view
 * model would.
 */
class ShowAllFilterFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val items = (1..ItemCount).map { id ->
        VideoItemUIState(id = id, title = "Card $id", imageUrl = "", bigImageUrl = "")
    }
    private var state by mutableStateOf<ShowAllViewState>(ShowAllViewState.Content(items))
    private var filter by mutableStateOf(ShowAllFilterState(available = true))
    private val actions = mutableListOf<UIAction>()

    private val activeFilter = ShowAllFilterState(available = true, isActive = true, genreTitle = "Драма")

    private fun setScreen() {
        composeRule.setContent {
            PuberTheme {
                // The key the navigation stack provides: the grid's first-focus flag is keyed on it, which
                // is what makes a remounted list ask for focus again. Without it the host cannot tell
                // whether ContentFocusGate holds that request back.
                CompositionLocalProvider(
                    LocalContentFocusActive provides true,
                    LocalScreenKey provides ScreenKeyUnderTest,
                ) {
                    ShowAllScreenContent(
                        title = FilterTestTitle,
                        state = state,
                        onAction = ::onAction,
                        filter = filter,
                    )
                }
            }
        }
    }

    /** What the screen asked of the filter, without the grid's load-more requests. */
    private fun filterActions(): List<UIAction> = actions.filterIsInstance<ShowAllAction>()

    private fun onAction(action: UIAction) {
        actions += action
        when (action) {
            ShowAllAction.FilterReset -> filter = ShowAllFilterState(available = true)
            ShowAllAction.FilterPanelOpened -> filter = filter.copy(panelOpen = true)
            ShowAllAction.FilterPanelClosed -> filter = filter.copy(panelOpen = false)
            is ShowAllAction.SortFieldChosen -> filter = filter.copy(draft = filter.draft.copy(sort = action.field))
            is ShowAllAction.GenreChosen -> filter = filter.copy(draft = filter.draft.copy(genre = action.genre))
        }
    }

    @Test
    fun coldOpen_focusesTheFirstCardNotFilters() {
        state = ShowAllViewState.Loading
        setScreen()
        composeRule.mainClock.advanceTimeBy(SettleMillis)

        state = ShowAllViewState.Content(items)
        awaitAnyFocus()
        composeRule.mainClock.advanceTimeBy(SettleMillis)

        composeRule.onNodeWithText(items.first().title).assertIsFocused()
        composeRule.onNodeWithText(text(R.string.show_all_filters)).assertIsDisplayed()
    }

    @Test
    fun unavailableFilter_showsNoFiltersButton() {
        filter = ShowAllFilterState.Unavailable
        setScreen()
        awaitAnyFocus()

        composeRule.onNodeWithText(FilterTestTitle).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.show_all_filters)).assertDoesNotExist()
    }

    @Test
    fun upFromFirstAndLastRowOneCard_landsOnFilters() {
        filter = activeFilter
        setScreen()
        awaitFocusOn(items.first().title)

        press(Key.DirectionUp)
        composeRule.onNodeWithText(text(R.string.show_all_filters)).assertIsFocused()

        press(Key.DirectionDown)
        repeat(SHOW_ALL_GRID_COLUMNS - 1) { press(Key.DirectionRight) }
        composeRule.onNodeWithText(items[SHOW_ALL_GRID_COLUMNS - 1].title).assertIsFocused()
        press(Key.DirectionUp)

        composeRule.onNodeWithText(text(R.string.show_all_filters)).assertIsFocused()
    }

    @Test
    fun downFromFilters_returnsToTheRowOneCardItCameFrom() {
        filter = activeFilter
        setScreen()
        awaitFocusOn(items.first().title)
        repeat(2) { press(Key.DirectionRight) }
        val cameFrom = items[2].title
        composeRule.onNodeWithText(cameFrom).assertIsFocused()

        press(Key.DirectionUp)
        composeRule.onNodeWithText(text(R.string.show_all_filters)).assertIsFocused()
        press(Key.DirectionDown)

        composeRule.onNodeWithText(cameFrom).assertIsFocused()
    }

    @Test
    fun resetPressed_focusesFiltersThenFirstCardWhenListLands() {
        filter = activeFilter
        setScreen()
        awaitFocusOn(items.first().title)
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        composeRule.onAllNodesWithText(text(R.string.show_all_filters_reset))[0].assertIsFocused()

        press(Key.DirectionCenter)
        assertEquals(listOf<UIAction>(ShowAllAction.FilterReset), filterActions())
        state = ShowAllViewState.Loading
        composeRule.waitForIdle()
        composeRule.onNodeWithText(text(R.string.show_all_filters)).assertIsFocused()

        state = ShowAllViewState.Content(items)
        awaitFocusOn(items.first().title)
        composeRule.onNodeWithText(items.first().title).assertIsFocused()
    }

    @Test
    fun filteredEmpty_showsResetAndDownFromFiltersReachesIt() {
        filter = activeFilter
        state = ShowAllViewState.Empty
        setScreen()
        composeRule.mainClock.advanceTimeBy(SettleMillis)
        val reset = text(R.string.show_all_filters_reset)
        composeRule.onNode(hasText(text(R.string.show_all_filtered_empty, "Драма"))).assertIsDisplayed()
        assertEquals(2, composeRule.onAllNodesWithText(reset).fetchSemanticsNodes().size)

        composeRule.onNodeWithText(text(R.string.show_all_filters)).requestFocus()
        composeRule.waitForIdle()
        press(Key.DirectionDown)

        composeRule.onAllNodesWithText(reset)[1].assertIsFocused()
    }

    @Test
    fun filtersOk_opensThePanelOnItsFirstRow() {
        openPanel()

        composeRule.onNode(isFocused() and hasText(text(R.string.show_all_filters_sort))).assertIsFocused()
        assertEquals(listOf<UIAction>(ShowAllAction.FilterPanelOpened), filterActions())
    }

    @Test
    fun panelBack_closesWithFocusOnFilters() {
        openPanel()

        pressBack()

        awaitFocusOn(text(R.string.show_all_filters))
        composeRule.onNodeWithText(text(R.string.show_all_filters)).assertIsFocused()
        assertEquals(ShowAllAction.FilterPanelClosed, filterActions().last())
    }

    @Test
    fun panelOpen_dpadLeftAndRightStayInsideThePanel() {
        openPanel()
        val sort = text(R.string.show_all_filters_sort)
        val genre = text(R.string.show_all_filters_genre)

        press(Key.DirectionLeft)
        press(Key.DirectionRight)
        composeRule.onNode(isFocused() and hasText(sort)).assertIsFocused()

        press(Key.DirectionDown)
        press(Key.DirectionLeft)
        press(Key.DirectionRight)
        composeRule.onNode(isFocused() and hasText(genre)).assertIsFocused()
    }

    @Test
    fun subPageBack_returnsToTheDoorItCameFrom() {
        filter = filter.copy(genres = FilterGenres.Loaded(listOf(Genre(9, "Драма"))))
        openPanel()
        val genre = text(R.string.show_all_filters_genre)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        awaitFocusOn(text(R.string.show_all_filters_genre_all))

        pressBack()

        awaitFocusOn(genre)
        composeRule.onNode(isFocused() and hasText(genre)).assertIsFocused()
        assertTrue("Back from a page leaves the panel open", filter.panelOpen)
    }

    @Test
    fun newListLanding_whilePanelOpen_keepsFocusInThePanel() {
        openPanel()

        state = ShowAllViewState.Loading
        composeRule.waitForIdle()
        state = ShowAllViewState.Content(items)
        composeRule.mainClock.advanceTimeBy(SettleMillis)
        composeRule.waitForIdle()

        composeRule.onNode(isFocused() and hasText(text(R.string.show_all_filters_sort))).assertIsFocused()
    }

    @Test
    fun newListLanding_afterApply_focusesTheFirstCard() {
        openPanel()
        press(Key.DirectionCenter)
        awaitFocusOn(text(R.string.show_all_sort_updated))
        composeRule.onAllNodesWithText(text(R.string.show_all_sort_year))[0].requestFocus()
        press(Key.DirectionCenter)
        pressBack()
        pressBack()
        awaitFocusOn(text(R.string.show_all_filters))

        state = ShowAllViewState.Loading
        composeRule.waitForIdle()
        composeRule.onNodeWithText(text(R.string.show_all_filters)).assertIsFocused()
        state = ShowAllViewState.Content(items)

        awaitFocusOn(items.first().title)
        composeRule.onNodeWithText(items.first().title).assertIsFocused()
    }

    @Test
    fun resetWithACachedBasePage_movesFocusFromFiltersToTheFirstCard() {
        filter = activeFilter
        setScreen()
        awaitFocusOn(items.first().title)
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        composeRule.onAllNodesWithText(text(R.string.show_all_filters_reset))[0].assertIsFocused()
        press(Key.DirectionCenter)
        composeRule.onNodeWithText(text(R.string.show_all_filters)).assertIsFocused()

        // The cached base page lands in the frame the reset restarts: no Loading state in between.
        state = ShowAllViewState.Content(items.reversed(), queryGeneration = 1)

        awaitFocusOn(items.last().title)
        composeRule.onNodeWithText(items.last().title).assertIsFocused()
    }

    @Test
    fun genresFailed_showsTheErrorRowAndFocusableRetry() {
        filter = filter.copy(genres = FilterGenres.Failed)
        openPanel()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        awaitFocusOn(text(R.string.show_all_filters_retry))
        composeRule.onNodeWithText(text(R.string.show_all_filters_genres_error)).assertIsDisplayed()
        press(Key.DirectionCenter)

        assertEquals(ShowAllAction.GenresRetry, filterActions().last())
    }

    private fun openPanel() {
        filter = filter.copy(available = true)
        setScreen()
        awaitFocusOn(items.first().title)
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        awaitFocusOn(text(R.string.show_all_filters_sort))
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
    }

    private fun text(res: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(res, *args)

    private fun press(key: Key) {
        composeRule.onNode(isFocused()).performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        composeRule.waitForIdle()
    }

    private fun awaitAnyFocus() {
        composeRule.waitUntil(FocusTimeoutMillis) {
            composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitFocusOn(title: String) {
        composeRule.waitUntil(FocusTimeoutMillis) {
            composeRule.onAllNodes(isFocused() and hasText(title)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val ScreenKeyUnderTest = "show-all-filter-test"
        const val ItemCount = 5 * SHOW_ALL_GRID_COLUMNS
        const val SettleMillis = 300L
        const val FocusTimeoutMillis = 5_000L
    }
}
