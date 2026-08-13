package com.kino.puber.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.kino.puber.core.model.NavigationMode
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

private const val TOP_TABS_KEY = "toptabs_tabs_visible"
private const val SIDE_DRAWER_KEY = "drawer_tabs_visible"
private const val TOP_TABS_SCHEMA_VERSION_KEY = "toptabs_schema_version"
private const val SHOW_CARTOONS_TAB_KEY = "show_cartoons_tab"
private const val SHOW_ANIME_TAB_KEY = "show_anime_tab"
private const val SHOW_ANIME_KEY = "show_anime"
private const val HIDE_WATCHED_KEY = "hide_watched"
private const val SHOW_WATCHED_INDICATORS_KEY = "show_watched_indicators"
private const val LEGACY_PLAYER_PREFS_NAME = "player_preferences"
private const val LEGACY_WATCHED_INDICATORS_KEY = "watched_indicators_enabled"
private const val HISTORY_SCHEMA_VERSION = 1

internal class NavigationPreferencesRepositoryTest {

    @Test
    fun defaultSideDrawer_usesEnabledDeclarationOrderWithHistory() {
        val fixture = fixture()

        val tabs = fixture.repository.getVisibleTabs(NavigationMode.SideDrawer)

        assertEquals(
            listOf(
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
    fun storedSideDrawerSelection_isReturnedWithoutInsertionOrReordering() {
        val fixture = fixture(
            storedDrawerTabs = "Movies,Favourites,Settings",
        )

        val tabs = fixture.repository.getVisibleTabs(NavigationMode.SideDrawer)

        assertEquals(
            listOf(TabType.Movies, TabType.Favourites, TabType.Settings),
            tabs,
        )
        assertFalse(TabType.History in tabs)
        assertTrue(fixture.preferences.transactions.isEmpty())
    }

    @Test
    fun defaultTopTabs_placeHistoryAfterCollectionsAndPersistSchema() {
        val fixture = fixture()

        val tabs = fixture.repository.getVisibleTabs(NavigationMode.TopTabs)

        assertEquals(
            listOf(
                TabType.Home,
                TabType.Movies,
                TabType.Series,
                TabType.Collections,
                TabType.History,
            ),
            tabs,
        )
        assertEquals(1, fixture.preferences.transactions.size)
        assertEquals(
            setOf(TOP_TABS_KEY, TOP_TABS_SCHEMA_VERSION_KEY),
            fixture.preferences.transactions.single().keys,
        )
        assertEquals(HISTORY_SCHEMA_VERSION, fixture.preferences.values[TOP_TABS_SCHEMA_VERSION_KEY])
    }

    @Test
    fun migrationNormalizesRequiredTabsAndDuplicateHistory() {
        val fixture = fixture(
            storedTabs = "Movies,Search,History,Series,History,Settings,Collections",
        )

        val tabs = fixture.repository.getVisibleTabs(NavigationMode.TopTabs)

        assertEquals(
            listOf(
                TabType.Home,
                TabType.Movies,
                TabType.Series,
                TabType.Collections,
                TabType.History,
            ),
            tabs,
        )
    }

    @Test
    fun migrationAppendsHistoryWhenCollectionsAreMissing() {
        val fixture = fixture(storedTabs = "History,Series,Movies")

        val tabs = fixture.repository.getVisibleTabs(NavigationMode.TopTabs)

        assertEquals(
            listOf(TabType.Home, TabType.Series, TabType.Movies, TabType.History),
            tabs,
        )
    }

    @Test
    fun migrationIgnoresUnknownAndMalformedTabNames() {
        val fixture = fixture(storedTabs = "Unknown,Movies,, Series ,DocMovies,broken")

        val tabs = fixture.repository.getVisibleTabs(NavigationMode.TopTabs)

        assertEquals(
            listOf(TabType.Home, TabType.Movies, TabType.DocMovies, TabType.History),
            tabs,
        )
    }

    @Test
    fun migrationRunsOnlyOnceAcrossRepeatedReads() {
        val fixture = fixture(storedTabs = "Movies,Collections")

        val first = fixture.repository.getVisibleTabs(NavigationMode.TopTabs)
        val transactionsAfterFirstRead = fixture.preferences.transactions.size
        val second = fixture.repository.getVisibleTabs(NavigationMode.TopTabs)

        assertEquals(first, second)
        assertEquals(1, transactionsAfterFirstRead)
        assertEquals(transactionsAfterFirstRead, fixture.preferences.transactions.size)
    }

    @Test
    fun userCanRemoveHistoryAfterVersionOneWhileSearchAndSettingsStayOutside() {
        val fixture = fixture()
        fixture.repository.getVisibleTabs(NavigationMode.TopTabs)

        fixture.repository.setVisibleTabs(
            mode = NavigationMode.TopTabs,
            tabs = listOf(
                TabType.Search,
                TabType.Home,
                TabType.Movies,
                TabType.Settings,
            ),
        )
        val tabs = fixture.repository.getVisibleTabs(NavigationMode.TopTabs)

        assertEquals(listOf(TabType.Home, TabType.Movies), tabs)
        assertFalse(TabType.History in tabs)
        assertFalse(TabType.Search in tabs)
        assertFalse(TabType.Settings in tabs)
    }

    @Test
    fun contentPreferences_useOffOffOnDefaults() {
        val fixture = fixture()

        assertEquals(
            ContentPreferences(
                showCartoonsTab = false,
                showAnimeTab = false,
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
            showCartoonsTab = true,
            showAnimeTab = true,
            showAnime = false,
            hideWatched = true,
            showWatchedIndicators = true,
        )

        assertEquals(
            ContentPreferences(
                showCartoonsTab = true,
                showAnimeTab = true,
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

        fixture.repository.setShowCartoonsTab(true)
        assertEquals(
            ContentPreferences(
                showCartoonsTab = true,
                showAnimeTab = false,
                showAnime = true,
                hideWatched = false,
                showWatchedIndicators = true,
            ),
            emitted.await(),
        )
        fixture.repository.setShowAnimeTab(true)
        fixture.repository.setShowAnime(false)
        fixture.repository.setHideWatched(true)

        assertEquals(true, fixture.preferences.values[SHOW_CARTOONS_TAB_KEY])
        assertEquals(true, fixture.preferences.values[SHOW_ANIME_TAB_KEY])
        assertEquals(false, fixture.preferences.values[SHOW_ANIME_KEY])
        assertEquals(true, fixture.preferences.values[HIDE_WATCHED_KEY])
        assertEquals(
            ContentPreferences(
                showCartoonsTab = true,
                showAnimeTab = true,
                showAnime = false,
                hideWatched = true,
                showWatchedIndicators = true,
            ),
            fixture.repository.contentPreferences.value,
        )
    }

    @Test
    fun optionalTabs_areInsertedCanonicallyInBothNavigationModes() {
        val fixture = fixture(
            storedTabs = "Home,Movies,Series,Collections,History",
            storedDrawerTabs = "Favourites,Movies,Series,History,Settings",
            showCartoonsTab = true,
            showAnimeTab = true,
        )

        assertEquals(
            listOf(
                TabType.Home,
                TabType.Movies,
                TabType.Series,
                TabType.Cartoons,
                TabType.Anime,
                TabType.Collections,
                TabType.History,
            ),
            fixture.repository.getVisibleTabs(NavigationMode.TopTabs),
        )
        assertEquals(
            listOf(
                TabType.Favourites,
                TabType.Movies,
                TabType.Series,
                TabType.Cartoons,
                TabType.Anime,
                TabType.History,
                TabType.Settings,
            ),
            fixture.repository.getVisibleTabs(NavigationMode.SideDrawer),
        )
    }

    @Test
    fun optionalTabs_canBeEnabledIndependently() {
        val cartoonsFixture = fixture(showCartoonsTab = true)
        val animeFixture = fixture(showAnimeTab = true)

        assertEquals(
            listOf(
                TabType.Home,
                TabType.Movies,
                TabType.Series,
                TabType.Cartoons,
                TabType.Collections,
                TabType.History,
            ),
            cartoonsFixture.repository.getVisibleTabs(NavigationMode.TopTabs),
        )
        assertEquals(
            listOf(
                TabType.Home,
                TabType.Movies,
                TabType.Series,
                TabType.Anime,
                TabType.Collections,
                TabType.History,
            ),
            animeFixture.repository.getVisibleTabs(NavigationMode.TopTabs),
        )
    }

    @Test
    fun disabledOptionalTabs_overrideLegacyStoredSelections() {
        val fixture = fixture(
            storedTabs = "Home,Movies,Cartoons,Anime,Series,Collections",
            storedDrawerTabs = "Favourites,Cartoons,Movies,Anime,Settings",
        )

        assertEquals(
            listOf(TabType.Home, TabType.Movies, TabType.Series, TabType.Collections, TabType.History),
            fixture.repository.getVisibleTabs(NavigationMode.TopTabs),
        )
        assertEquals(
            listOf(TabType.Favourites, TabType.Movies, TabType.Settings),
            fixture.repository.getVisibleTabs(NavigationMode.SideDrawer),
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
            listOf(TabType.Favourites, TabType.Movies, TabType.Anime, TabType.Collections, TabType.Settings),
            moviesFixture.repository.getVisibleTabs(NavigationMode.SideDrawer),
        )
        assertEquals(
            listOf(TabType.Favourites, TabType.Cartoons, TabType.History, TabType.Settings),
            boundaryFixture.repository.getVisibleTabs(NavigationMode.SideDrawer),
        )
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
        fixture.repository.setShowAnimeTab(true)
        runCurrent()

        assertEquals(2, emissions.size)
        collector.cancel()
    }

    private fun fixture(
        storedTabs: String? = null,
        storedDrawerTabs: String? = null,
        showCartoonsTab: Boolean? = null,
        showAnimeTab: Boolean? = null,
        showAnime: Boolean? = null,
        hideWatched: Boolean? = null,
        showWatchedIndicators: Boolean? = null,
        legacyWatchedIndicators: Boolean? = null,
    ): Fixture {
        val preferences = TestPreferences()
        storedTabs?.let { preferences.values[TOP_TABS_KEY] = it }
        storedDrawerTabs?.let { preferences.values[SIDE_DRAWER_KEY] = it }
        showCartoonsTab?.let { preferences.values[SHOW_CARTOONS_TAB_KEY] = it }
        showAnimeTab?.let { preferences.values[SHOW_ANIME_TAB_KEY] = it }
        showAnime?.let { preferences.values[SHOW_ANIME_KEY] = it }
        hideWatched?.let { preferences.values[HIDE_WATCHED_KEY] = it }
        showWatchedIndicators?.let { preferences.values[SHOW_WATCHED_INDICATORS_KEY] = it }
        val legacyPreferences = TestPreferences()
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
        val preferences: TestPreferences,
    )
}

private class TestPreferences {
    val values: MutableMap<String, Any?> = mutableMapOf()
    val transactions: MutableList<Map<String, Any?>> = mutableListOf()
    val sharedPreferences: SharedPreferences = mockk()

    private val pending: MutableMap<String, Any?> = linkedMapOf()
    private val editor: SharedPreferences.Editor = mockk()

    init {
        every { sharedPreferences.getString(any(), any()) } answers {
            values[firstArg()] as? String ?: secondArg<String?>()
        }
        every { sharedPreferences.getInt(any(), any()) } answers {
            values[firstArg()] as? Int ?: secondArg()
        }
        every { sharedPreferences.getBoolean(any(), any()) } answers {
            values[firstArg()] as? Boolean ?: secondArg()
        }
        every { sharedPreferences.contains(any()) } answers { values.containsKey(firstArg()) }
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            pending[firstArg()] = secondArg<String?>()
            editor
        }
        every { editor.putInt(any(), any()) } answers {
            pending[firstArg()] = secondArg<Int>()
            editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            pending[firstArg()] = secondArg<Boolean>()
            editor
        }
        every { editor.apply() } answers {
            val transaction = pending.toMap()
            values.putAll(transaction)
            transactions += transaction
            pending.clear()
        }
    }
}
