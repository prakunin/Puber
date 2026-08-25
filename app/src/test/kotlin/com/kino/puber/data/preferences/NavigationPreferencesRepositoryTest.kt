package com.kino.puber.data.preferences

import android.content.Context
import com.kino.puber.util.FakeSharedPreferences
import com.kino.puber.ui.feature.main.model.TabType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val SIDE_DRAWER_KEY = "drawer_tabs_visible"
private const val LEGACY_TOP_TABS_KEY = "toptabs_tabs_visible"
private const val LEGACY_NAVIGATION_MODE_KEY = "navigation_mode"
private const val STARTUP_TAB_KEY = "startup_tab"
private const val SHOW_CARTOONS_TAB_KEY = "show_cartoons_tab"
private const val SHOW_ANIME_TAB_KEY = "show_anime_tab"
private const val SHOW_ANIME_KEY = "show_anime"
private const val HIDE_WATCHED_KEY = "hide_watched"
private const val SHOW_WATCHED_INDICATORS_KEY = "show_watched_indicators"
private const val AUTO_TRAILER_KEY = "auto_trailer_enabled"
private const val LEGACY_PLAYER_PREFS_NAME = "player_preferences"
private const val LEGACY_WATCHED_INDICATORS_KEY = "watched_indicators_enabled"

internal class NavigationPreferencesRepositoryTest {

    @Test
    fun startupTab_defaultsToHomeWithoutWritingPreferences() {
        val fixture = fixture()

        assertEquals(TabType.Home, fixture.repository.getStartupTab())
        assertTrue(fixture.preferences.transactions.isEmpty())
    }

    @Test
    fun startupTab_persistsTheSelectedTab() {
        val fixture = fixture()

        fixture.repository.setStartupTab(TabType.Favourites)

        assertEquals(TabType.Favourites, fixture.repository.getStartupTab())
        assertEquals(TabType.Favourites.name, fixture.preferences.values[STARTUP_TAB_KEY])
    }

    @Test
    fun startupTab_fallsBackToHomeForAnUnknownStoredValue() {
        val fixture = fixture(startupTab = "RemovedTab")

        assertEquals(TabType.Home, fixture.repository.getStartupTab())
    }

    @Test
    fun autoTrailer_defaultsToOnWithoutWritingPreferences() {
        val fixture = fixture()

        assertTrue(fixture.repository.getAutoTrailerEnabled())
        assertTrue(fixture.preferences.transactions.isEmpty())
    }

    @Test
    fun autoTrailer_persistsTheStoredChoice() {
        val fixture = fixture()

        fixture.repository.setAutoTrailerEnabled(false)

        assertFalse(fixture.repository.getAutoTrailerEnabled())
        assertEquals(false, fixture.preferences.values[AUTO_TRAILER_KEY])
    }

    @Test
    fun startupTabOptions_excludeUtilityDestinations() {
        val fixture = fixture()

        val options = fixture.repository.getStartupTabOptions()

        assertFalse(TabType.Search in options)
        assertFalse(TabType.Settings in options)
        assertTrue(TabType.Home in options)
        assertTrue(TabType.Favourites in options)
    }

    @Test
    fun defaultSideDrawer_includesHomeInEnabledDeclarationOrder() {
        val fixture = fixture()

        val tabs = fixture.repository.getVisibleTabs()

        assertEquals(
            listOf(
                TabType.Home,
                TabType.Search,
                TabType.Favourites,
                TabType.History,
                TabType.Movies,
                TabType.Series,
                TabType.For4k,
                TabType.Concerts,
                TabType.DocMovies,
                TabType.DocSeries,
                TabType.TvShows,
                TabType.Settings,
            ),
            tabs,
        )
        assertEquals(TabType.entries.filter(TabType::enabled), tabs)
        assertTrue(fixture.preferences.transactions.isEmpty())
    }

    @Test
    fun storedSideDrawerSelection_insertsRequiredHomeWithoutReorderingSelection() {
        val fixture = fixture(
            storedDrawerTabs = "Movies,Favourites,Settings",
        )

        val tabs = fixture.repository.getVisibleTabs()

        assertEquals(
            listOf(TabType.Home, TabType.Movies, TabType.Favourites, TabType.Settings),
            tabs,
        )
        assertFalse(TabType.History in tabs)
        assertTrue(fixture.preferences.transactions.isEmpty())
    }

    @Test
    fun contentPreferences_useOffOffOnDefaults() {
        val fixture = fixture()

        assertEquals(
            ContentPreferences(
                showAnime = true,
                hideWatched = false,
                showWatchedIndicators = true,
            ),
            fixture.repository.contentPreferences.value,
        )
    }

    @Test
    fun watchedIndicators_fallBackToTheChoiceLeftInThePlayerPreferences() {
        // The setting moved out of the player preferences; an existing choice must survive the move
        // rather than silently reverting to the default.
        val fixture = fixture(legacyWatchedIndicators = false)

        assertFalse(fixture.repository.contentPreferences.value.showWatchedIndicators)
        assertTrue(fixture.preferences.transactions.isEmpty())
    }

    @Test
    fun watchedIndicators_preferTheirOwnValueOverTheLegacyOne() {
        val fixture = fixture(showWatchedIndicators = true, legacyWatchedIndicators = false)

        assertTrue(fixture.repository.contentPreferences.value.showWatchedIndicators)
    }

    @Test
    fun contentPreferences_readPersistedValues() {
        val fixture = fixture(
            showAnime = false,
            hideWatched = true,
            showWatchedIndicators = true,
        )

        assertEquals(
            ContentPreferences(
                showAnime = false,
                hideWatched = true,
                showWatchedIndicators = true,
            ),
            fixture.repository.contentPreferences.value,
        )
    }

    @Test
    fun contentPreferenceSetters_persistIndependentValuesAndEmitSnapshots() = runTest {
        val fixture = fixture()
        val emitted = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.repository.contentPreferences.drop(1).first()
        }

        fixture.repository.setShowAnime(false)
        assertEquals(
            ContentPreferences(
                showAnime = false,
                hideWatched = false,
                showWatchedIndicators = true,
            ),
            emitted.await(),
        )
        fixture.repository.setHideWatched(true)

        assertEquals(false, fixture.preferences.values[SHOW_ANIME_KEY])
        assertEquals(true, fixture.preferences.values[HIDE_WATCHED_KEY])
        assertEquals(
            ContentPreferences(
                showAnime = false,
                hideWatched = true,
                showWatchedIndicators = true,
            ),
            fixture.repository.contentPreferences.value,
        )
    }

    @Test
    fun optionalTabs_areInsertedCanonically() {
        val fixture = fixture(
            storedDrawerTabs = "Favourites,Movies,Series,History,Settings",
            showCartoonsTab = true,
            showAnimeTab = true,
        )

        assertEquals(
            listOf(
                TabType.Home,
                TabType.Favourites,
                TabType.Movies,
                TabType.Series,
                TabType.Cartoons,
                TabType.Anime,
                TabType.History,
                TabType.Settings,
            ),
            fixture.repository.getVisibleTabs(),
        )
    }

    @Test
    fun optionalTabs_canBeEnabledIndependently() {
        val cartoonsFixture = fixture(
            storedDrawerTabs = "Home,Movies,Series,Settings",
            showCartoonsTab = true,
        )
        val animeFixture = fixture(
            storedDrawerTabs = "Home,Movies,Series,Settings",
            showAnimeTab = true,
        )

        assertEquals(
            listOf(TabType.Home, TabType.Movies, TabType.Series, TabType.Cartoons, TabType.Settings),
            cartoonsFixture.repository.getVisibleTabs(),
        )
        assertEquals(
            listOf(TabType.Home, TabType.Movies, TabType.Series, TabType.Anime, TabType.Settings),
            animeFixture.repository.getVisibleTabs(),
        )
    }

    @Test
    fun disabledOptionalTabs_overrideLegacyStoredSelections() {
        val fixture = fixture(
            storedDrawerTabs = "Favourites,Cartoons,Movies,Anime,Settings",
        )

        assertEquals(
            listOf(TabType.Home, TabType.Favourites, TabType.Movies, TabType.Settings),
            fixture.repository.getVisibleTabs(),
        )
    }

    @Test
    fun optionalTabs_useMoviesThenBoundaryAsFallbackAnchors() {
        val moviesFixture = fixture(
            storedDrawerTabs = "Favourites,Movies,Collections,Settings",
            showAnimeTab = true,
        )
        val boundaryFixture = fixture(
            storedDrawerTabs = "Favourites,History,Settings",
            showCartoonsTab = true,
        )

        assertEquals(
            listOf(
                TabType.Home,
                TabType.Favourites,
                TabType.Movies,
                TabType.Anime,
                TabType.Collections,
                TabType.Settings,
            ),
            moviesFixture.repository.getVisibleTabs(),
        )
        assertEquals(
            listOf(
                TabType.Home,
                TabType.Favourites,
                TabType.Cartoons,
                TabType.History,
                TabType.Settings,
            ),
            boundaryFixture.repository.getVisibleTabs(),
        )
    }

    @Test
    fun hidingATab_dropsItFromTheMenuAndFromTheStartupOptions() {
        val fixture = fixture()

        fixture.repository.setTabVisible(TabType.Concerts, visible = false)

        val tabs = fixture.repository.getVisibleTabs()
        assertFalse(TabType.Concerts in tabs)
        assertFalse(
            TabType.Concerts in fixture.repository.getStartupTabOptions(),
        )
        assertTrue(TabType.For4k in tabs)
    }

    @Test
    fun showingATab_putsItBackInDeclarationOrder() {
        val fixture = fixture(storedDrawerTabs = "Home,Movies,Series,Collections,History,Settings")

        fixture.repository.setTabVisible(TabType.For4k, visible = true)

        assertEquals(
            listOf(
                TabType.Home,
                TabType.Movies,
                TabType.Series,
                TabType.For4k,
                TabType.Collections,
                TabType.History,
                TabType.Settings,
            ),
            fixture.repository.getVisibleTabs(),
        )
    }

    @Test
    fun showingATabAlreadyInTheMenu_leavesTheOrderAlone() {
        val fixture = fixture(storedDrawerTabs = "Home,Movies,Favourites,Settings")

        fixture.repository.setTabVisible(TabType.Favourites, visible = true)

        assertEquals(
            listOf(TabType.Home, TabType.Movies, TabType.Favourites, TabType.Settings),
            fixture.repository.getVisibleTabs(),
        )
    }

    @Test
    fun hidingARequiredTab_isRefused() {
        val fixture = fixture()

        fixture.repository.setTabVisible(TabType.Home, visible = false)
        fixture.repository.setTabVisible(TabType.Settings, visible = false)

        val tabs = fixture.repository.getVisibleTabs()
        assertTrue(TabType.Home in tabs)
        assertTrue(TabType.Settings in tabs)
    }

    @Test
    fun writingTheMenu_takesOverFromTheLegacyOptionalToggles() {
        // The optional tabs were seeded from the old toggles, so the written list carries them;
        // from then on the toggles no longer have a say.
        val fixture = fixture(showAnimeTab = true)

        fixture.repository.setTabVisible(TabType.Concerts, visible = false)
        fixture.preferences.values[SHOW_ANIME_TAB_KEY] = false

        val tabs = fixture.repository.getVisibleTabs()
        assertTrue(TabType.Anime in tabs)
        assertFalse(TabType.Concerts in tabs)
    }

    @Test
    fun menuTabsChanges_fireOnlyForVisibilityWrites() = runTest {
        val fixture = fixture()
        val emissions = mutableListOf<Unit>()
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.repository.menuTabsChanges.collect(emissions::add)
        }

        fixture.repository.setTabVisible(TabType.Concerts, visible = false)
        runCurrent()
        fixture.repository.setStartupTab(TabType.Movies)
        runCurrent()

        assertEquals(1, emissions.size)
        collector.cancel()
    }

    @Test
    fun displaySettingsChanges_fireForBothHidingAndTheWatchedMarks() = runTest {
        val fixture = fixture()
        val emissions = mutableListOf<Unit>()
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.repository.displaySettingsChanges.collect(emissions::add)
        }

        // Stepped one at a time: a StateFlow conflates, and setting three values in a row would
        // only ever be seen as the last of them.
        fixture.repository.setHideWatched(true)
        runCurrent()
        fixture.repository.setShowWatchedIndicators(false)
        runCurrent()
        // An unrelated preference must not drag every screen through a reload.
        fixture.repository.setShowAnime(false)
        runCurrent()

        assertEquals(2, emissions.size)
        collector.cancel()
    }

    /**
     * The menu a user last saw is the one they configured under the navigation mode they were in.
     * Dropping the top-tabs mode must not drop their sections with it: an install that was on top
     * tabs comes back with that list in the one menu that is left.
     */
    @Test
    fun anInstallLeftOnTopTabs_keepsThatMenuInTheDrawer() {
        val fixture = fixture(
            storedDrawerTabs = "Home,Favourites,Settings",
            storedTopTabs = "Home,Movies,Collections,History",
            activeNavigationMode = "TopTabs",
        )

        val tabs = fixture.repository.getVisibleTabs()

        assertEquals(
            listOf(TabType.Home, TabType.Movies, TabType.Collections, TabType.History, TabType.Settings),
            tabs,
        )
    }

    @Test
    fun anInstallLeftOnTheDrawer_keepsItsOwnMenu() {
        val fixture = fixture(
            storedDrawerTabs = "Home,Favourites,Settings",
            storedTopTabs = "Home,Movies,Collections,History",
            activeNavigationMode = "SideDrawer",
        )

        val tabs = fixture.repository.getVisibleTabs()

        assertEquals(listOf(TabType.Home, TabType.Favourites, TabType.Settings), tabs)
    }

    @Test
    fun theTopTabsMenuIsCarriedOverOnlyOnce() {
        val fixture = fixture(
            storedTopTabs = "Home,Movies,History",
            activeNavigationMode = "TopTabs",
        )
        fixture.repository.getVisibleTabs()

        fixture.repository.setTabVisible(TabType.Movies, visible = false)
        val tabs = fixture.repository.getVisibleTabs()

        assertEquals(listOf(TabType.Home, TabType.History, TabType.Settings), tabs)
    }

    private fun fixture(
        storedDrawerTabs: String? = null,
        storedTopTabs: String? = null,
        activeNavigationMode: String? = null,
        startupTab: String? = null,
        showCartoonsTab: Boolean? = null,
        showAnimeTab: Boolean? = null,
        showAnime: Boolean? = null,
        hideWatched: Boolean? = null,
        showWatchedIndicators: Boolean? = null,
        legacyWatchedIndicators: Boolean? = null,
    ): Fixture {
        val preferences = FakeSharedPreferences()
        storedDrawerTabs?.let { preferences.values[SIDE_DRAWER_KEY] = it }
        storedTopTabs?.let { preferences.values[LEGACY_TOP_TABS_KEY] = it }
        activeNavigationMode?.let { preferences.values[LEGACY_NAVIGATION_MODE_KEY] = it }
        startupTab?.let { preferences.values[STARTUP_TAB_KEY] = it }
        showCartoonsTab?.let { preferences.values[SHOW_CARTOONS_TAB_KEY] = it }
        showAnimeTab?.let { preferences.values[SHOW_ANIME_TAB_KEY] = it }
        showAnime?.let { preferences.values[SHOW_ANIME_KEY] = it }
        hideWatched?.let { preferences.values[HIDE_WATCHED_KEY] = it }
        showWatchedIndicators?.let { preferences.values[SHOW_WATCHED_INDICATORS_KEY] = it }
        val legacyPreferences = FakeSharedPreferences()
        legacyWatchedIndicators?.let {
            legacyPreferences.values[LEGACY_WATCHED_INDICATORS_KEY] = it
        }
        val context = mockk<Context>()
        every {
            context.getSharedPreferences(LEGACY_PLAYER_PREFS_NAME, Context.MODE_PRIVATE)
        } returns legacyPreferences.sharedPreferences
        every {
            context.getSharedPreferences(neq(LEGACY_PLAYER_PREFS_NAME), Context.MODE_PRIVATE)
        } returns preferences.sharedPreferences
        return Fixture(
            repository = NavigationPreferencesRepository(context),
            preferences = preferences,
        )
    }

    private data class Fixture(
        val repository: NavigationPreferencesRepository,
        val preferences: FakeSharedPreferences,
    )
}
