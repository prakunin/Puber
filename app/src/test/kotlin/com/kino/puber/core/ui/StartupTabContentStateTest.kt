package com.kino.puber.core.ui

import android.content.Context
import com.kino.puber.core.telemetry.StartupContentState
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.domain.model.TabType
import com.kino.puber.ui.feature.bookmarks.model.BookmarksViewState
import com.kino.puber.ui.feature.collections.model.CollectionsViewState
import com.kino.puber.ui.feature.contentlist.model.SectionState
import com.kino.puber.ui.feature.device.settings.model.SelectableMenuTabs
import com.kino.puber.ui.feature.favorites.model.FavoriteViewState
import com.kino.puber.ui.feature.history.model.HistoryViewState
import com.kino.puber.ui.feature.home.model.HomeViewState
import com.kino.puber.util.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class StartupTabContentStateTest {

    /** The loaded states of the screen a tab opens; every one must mark first content. */
    private fun loadedStatesOf(tab: TabType): List<Any> = when (tab) {
        TabType.Home -> listOf(HomeViewState.Content())
        TabType.Favourites -> listOf(FavoriteViewState.Empty)
        TabType.History -> listOf(HistoryViewState.Empty)
        TabType.Bookmarks -> listOf(BookmarksViewState.Content())
        TabType.Collections -> listOf(CollectionsViewState.Content())
        TabType.Movies,
        TabType.Series,
        TabType.Cartoons,
        TabType.Anime,
        TabType.For4k,
        TabType.Concerts,
        TabType.DocMovies,
        TabType.DocSeries,
        TabType.TvShows -> listOf(SectionState.Content(emptyList()))
        // Not offered as a startup tab: search is opened on demand, settings and the placeholder
        // are not content screens.
        TabType.Search,
        TabType.Settings,
        TabType.SportTV -> emptyList()
    }

    @Test
    fun everyStartupTabOptionLandsOnAScreenWhoseContentStateIsMarked() {
        val options = repository(showCartoons = true, showAnime = true).also { repo ->
            repo.setVisibleTabs(listOf(TabType.Home) + SelectableMenuTabs + TabType.Settings)
        }.getStartupTabOptions()

        assertTrue(TabType.Cartoons in options && TabType.Anime in options)
        for (tab in options) {
            val states = loadedStatesOf(tab)
            assertTrue(states.isNotEmpty(), "$tab has no mapped content state")
            states.forEach { assertTrue(it is StartupContentState, "$tab: $it is not marked") }
        }
        assertEquals(options.toSet(), options.filter { loadedStatesOf(it).isNotEmpty() }.toSet())
    }

    @Test
    fun sectionLoadingEmptyAndErrorAreNotMarked() {
        // Empty is the paginator store's initial state, replayed before the first load.
        assertTrue(SectionState.Empty as Any !is StartupContentState)
        assertTrue(SectionState.Loading as Any !is StartupContentState)
        assertTrue(SectionState.Error("e") as Any !is StartupContentState)
    }

    private fun repository(showCartoons: Boolean, showAnime: Boolean): NavigationPreferencesRepository {
        val preferences = FakeSharedPreferences()
        preferences.values["show_cartoons_tab"] = showCartoons
        preferences.values["show_anime_tab"] = showAnime
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), Context.MODE_PRIVATE) } returns
            preferences.sharedPreferences
        return NavigationPreferencesRepository(context)
    }
}
