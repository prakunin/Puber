package com.kino.puber.ui.feature.showall.content

import android.view.KeyEvent
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
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
import com.kino.puber.data.api.models.Country
import com.kino.puber.data.api.models.Genre
import com.kino.puber.domain.model.ContentFilter
import com.kino.puber.domain.model.SortField
import com.kino.puber.ui.feature.showall.model.FilterCountries
import com.kino.puber.ui.feature.showall.model.FilterGenres
import com.kino.puber.ui.feature.showall.model.ShowAllAction
import com.kino.puber.ui.feature.showall.model.ShowAllFilterState
import com.kino.puber.ui.feature.showall.model.ShowAllViewState
import com.kino.puber.ui.feature.showall.model.YEARS_BEFORE_OLDEST_DECADE
import com.kino.puber.ui.feature.showall.model.yearOptions
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
    private var hostView: View? = null

    private val activeFilter = ShowAllFilterState(available = true, isActive = true, genreTitle = "Драма")

    private fun setScreen() {
        composeRule.setContent {
            hostView = LocalView.current
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
            is ShowAllAction.CountryChosen -> filter = filter.copy(draft = filter.draft.copy(country = action.country))
            is ShowAllAction.YearChosen -> filter = filter.copy(draft = filter.draft.copy(yearRange = action.range))
            is ShowAllAction.StatusChosen -> filter = filter.copy(
                draft = filter.draft.copy(finished = if (action.finishedOnly) true else null),
            )
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
    fun coldOpenWithRestoredFilter_focusesTheFirstCardNotReset() {
        filter = activeFilter.copy(
            countryTitle = "США",
            yearRange = 2010..2019,
            finishedOnly = true,
            statusAvailable = true,
        )
        state = ShowAllViewState.Loading
        setScreen()
        composeRule.mainClock.advanceTimeBy(SettleMillis)

        // A real cold open hands the window's initial focus to the first focusable node while the
        // grid is still loading; here that is the only thing that could take it. The host is already
        // focused, so the entry is forced the way a window gaining focus does it.
        composeRule.runOnUiThread {
            hostView?.clearFocus()
            hostView?.requestFocus()
        }
        composeRule.waitForIdle()
        // The guard assertions: they fail when «Фильтры» or «Сброс» is focusable while loading.
        composeRule.onAllNodesWithText(text(R.string.show_all_filters_reset))[0].assertIsNotFocused()
        composeRule.onNodeWithText(text(R.string.show_all_filters)).assertIsNotFocused()

        state = ShowAllViewState.Content(items)
        awaitAnyFocus()
        composeRule.mainClock.advanceTimeBy(SettleMillis)

        composeRule.onNodeWithText(items.first().title).assertIsFocused()
        val reset = composeRule.onAllNodesWithText(text(R.string.show_all_filters_reset))[0]
        reset.assertIsDisplayed()
        reset.assertIsNotFocused()
    }

    @Test
    fun coldOpenWithRestoredFilter_thatPagesEmpty_focusesTheEmptyStateReset() {
        filter = activeFilter
        state = ShowAllViewState.Loading
        setScreen()
        composeRule.mainClock.advanceTimeBy(SettleMillis)

        state = ShowAllViewState.Empty
        awaitAnyFocus()
        composeRule.mainClock.advanceTimeBy(SettleMillis)

        val reset = text(R.string.show_all_filters_reset)
        assertEquals(2, composeRule.onAllNodesWithText(reset).fetchSemanticsNodes().size)
        composeRule.onAllNodesWithText(reset)[1].assertIsFocused()
    }

    @Test
    fun panelClosedThenFilteredEmpty_keepsFocusOnFiltersNotReset() {
        openPanel()
        pressBack()
        awaitFocusOn(text(R.string.show_all_filters))
        filter = activeFilter

        state = ShowAllViewState.Loading
        composeRule.waitForIdle()
        state = ShowAllViewState.Empty
        composeRule.mainClock.advanceTimeBy(SettleMillis)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(text(R.string.show_all_filters)).assertIsFocused()
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

    @Test
    fun filterSummary_namesGenreCountryYearStatusThenSort() {
        filter = activeFilter.copy(
            countryTitle = "США",
            yearRange = 2010..2019,
            finishedOnly = true,
            sort = SortField.YEAR,
        )
        setScreen()
        composeRule.mainClock.advanceTimeBy(SettleMillis)

        val sortSummary = text(
            R.string.show_all_sort_summary,
            text(R.string.show_all_sort_year),
            text(R.string.show_all_sort_arrow_desc),
        )
        val summary = listOf(
            "Драма",
            "США",
            text(R.string.show_all_filters_year_decade, 2010),
            text(R.string.show_all_filters_status_finished),
            sortSummary,
        ).joinToString(" · ")
        composeRule.onAllNodesWithText(summary)[0].assertIsDisplayed()
    }

    @Test
    fun yearLabel_namesSingleYearDecadeBefore1980AndAnOffListRange() {
        var labels: List<String> = emptyList()
        composeRule.setContent {
            labels = listOf(2020..2020, 2010..2019, YEARS_BEFORE_OLDEST_DECADE, null, 2003..2007).map { yearLabel(it) }
        }
        composeRule.waitForIdle()

        assertEquals(
            listOf(
                "2020",
                text(R.string.show_all_filters_year_decade, 2010),
                text(R.string.show_all_filters_year_before_1980),
                text(R.string.show_all_filters_year_any),
                text(R.string.show_all_filters_year_range, 2003, 2007),
            ),
            labels,
        )
    }

    @Test
    fun statusDoor_absentWhenStatusUnavailable() {
        openPanel()

        repeat(4) { press(Key.DirectionDown) }

        awaitFocusOnExact(text(R.string.show_all_filters_year))
        val statusDoors = composeRule.onAllNodes(hasText(text(R.string.show_all_filters_status), substring = false))
        assertEquals(0, statusDoors.fetchSemanticsNodes().size)
    }

    @Test
    fun countriesFailed_showsTheErrorRowAndFocusableRetry() {
        filter = filter.copy(countries = FilterCountries.Failed)
        openPanel()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        awaitFocusOn(text(R.string.show_all_filters_retry))
        composeRule.onNodeWithText(text(R.string.show_all_filters_countries_error)).assertIsDisplayed()
        press(Key.DirectionCenter)

        assertEquals(ShowAllAction.CountriesRetry, filterActions().last())
    }

    @Test
    fun rootDoors_downReachesCountryYearAndStatus() {
        filter = filter.copy(statusAvailable = true)
        openPanel()

        listOf(
            R.string.show_all_filters_genre,
            R.string.show_all_filters_country,
            R.string.show_all_filters_year,
            R.string.show_all_filters_status,
        ).forEach { door ->
            press(Key.DirectionDown)
            awaitFocusOnExact(text(door))
        }
        press(Key.DirectionDown)

        awaitFocusOnExact(text(R.string.show_all_filters_status))
    }

    @Test
    fun newPagesBack_returnToTheDoorTheyCameFrom() {
        filter = filter.copy(statusAvailable = true, yearOptions = yearOptions(2026), countries = allCountries())
        openPanel()
        press(Key.DirectionDown)

        listOf(
            R.string.show_all_filters_country to R.string.show_all_filters_country_all,
            R.string.show_all_filters_year to R.string.show_all_filters_year_any,
            R.string.show_all_filters_status to R.string.show_all_filters_status_any,
        ).forEach { (door, firstRow) ->
            press(Key.DirectionDown)
            awaitFocusOnExact(text(door))
            press(Key.DirectionCenter)
            awaitFocusOnRow(text(firstRow), text(door))

            pressBack()

            awaitFocusOnExact(text(door))
            assertTrue(filter.panelOpen)
        }
    }

    @Test
    fun countryPage_opensFocusedOnTheSelectedCountryFarDown() {
        val chosen = Country(100, countryTitle(100))
        filter = filter.copy(draft = ContentFilter(country = chosen), countries = allCountries())
        openPanel()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        awaitFocusOnRow(chosen.title, text(R.string.show_all_filters_country))

        composeRule.onNode(isFocused() and hasText(chosen.title)).assertIsDisplayed()
    }

    @Test
    fun countryPage_downReachesTheLastCountry() {
        filter = filter.copy(countries = allCountries())
        openPanel()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        awaitFocusOnRow(text(R.string.show_all_filters_country_all), text(R.string.show_all_filters_country))

        repeat(CountryCount) { press(Key.DirectionDown) }

        awaitFocusOnRow(countryTitle(CountryCount), text(R.string.show_all_filters_country))
        press(Key.DirectionDown)
        composeRule.onNode(hasText(countryTitle(CountryCount))).assertIsFocused()
    }

    @Test
    fun countryPage_listLandingAfterLoading_movesFocusToTheSelectedCountry() {
        val chosen = Country(100, countryTitle(100))
        filter = filter.copy(draft = ContentFilter(country = chosen), countries = FilterCountries.Loading)
        openPanel()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        awaitFocusOnRow(text(R.string.show_all_filters_country_all), text(R.string.show_all_filters_country))

        filter = filter.copy(countries = allCountries())

        awaitFocusOnRow(chosen.title, text(R.string.show_all_filters_country))
    }

    @Test
    fun yearPage_downReachesBefore1980() {
        val options = yearOptions(2026)
        filter = filter.copy(yearOptions = options)
        openPanel()
        repeat(3) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        awaitFocusOnRow(text(R.string.show_all_filters_year_any), text(R.string.show_all_filters_year))

        repeat(options.size) { press(Key.DirectionDown) }

        awaitFocusOnRow(text(R.string.show_all_filters_year_before_1980), text(R.string.show_all_filters_year))
        press(Key.DirectionCenter)
        assertEquals(ShowAllAction.YearChosen(YEARS_BEFORE_OLDEST_DECADE), filterActions().last())
    }

    @Test
    fun yearPage_aStoredYearOutsideThePresets_isTheCheckedRowAndTakesFocus() {
        val offList = 2003..2007
        filter = filter.copy(draft = ContentFilter(yearRange = offList), yearOptions = yearOptions(2026))
        openPanel()
        repeat(3) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)

        awaitFocusOnRow(
            text(R.string.show_all_filters_year_range, offList.first, offList.last),
            text(R.string.show_all_filters_year),
        )
    }

    private fun countryTitle(id: Int) = "Страна %03d".format(id)

    private fun allCountries() = FilterCountries.Loaded((1..CountryCount).map { Country(it, countryTitle(it)) })

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

    private fun awaitFocusOnExact(title: String) {
        composeRule.waitUntil(FocusTimeoutMillis) {
            composeRule.onAllNodes(isFocused() and hasText(title, substring = false)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** A page row, not the root door that carries the same value as its trailing text. */
    private fun awaitFocusOnRow(title: String, door: String) {
        composeRule.waitUntil(FocusTimeoutMillis) {
            composeRule.onAllNodes(
                isFocused() and hasText(title, substring = false) and !hasText(door, substring = false),
            ).fetchSemanticsNodes().isNotEmpty()
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
        const val CountryCount = 102
        const val SettleMillis = 300L
        const val FocusTimeoutMillis = 5_000L
    }
}
