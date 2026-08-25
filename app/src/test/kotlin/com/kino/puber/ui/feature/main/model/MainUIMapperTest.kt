package com.kino.puber.ui.feature.main.model

import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.House
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.ui.ScreensImpl
import com.kino.puber.ui.feature.history.component.HistoryScreen
import com.kino.puber.util.FakeResourceProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MainUIMapperTest {

    private val mapper = MainUIMapper(
        resources = FakeResourceProvider(),
        screens = ScreensImpl,
        navPrefs = mockk<NavigationPreferencesRepository>(relaxed = true),
    )

    @Test
    fun historyTab_resolvesToHistoryScreen() {
        val screens = mockk<Screens>()
        every { screens.history() } answers { HistoryScreen() }
        val historyMapper = createMapper(
            navPrefs = mockk(relaxed = true),
            screens = screens,
        )

        val tab = historyMapper.buildTabContent(type = TabType.History)

        assertHistoryTab(tab)
        verify(exactly = 1) {
            screens.history()
        }
        assertTrue(TabType.History.enabled)
    }

    @Test
    fun selectingHistory_opensHistoryContent() {
        val home = mainTab(TabType.Home, isSelected = true)
        val history = mainTab(TabType.History)
        val selected = mapper.updateSelectedTab(
            state = MainViewState(
                tabs = listOf(home, history),
                selectedTab = TabType.Home,
            ),
            tab = history,
        )

        assertEquals(TabType.History, selected.selectedTab)
        assertEquals(listOf(false, true), selected.tabs.map(MainTab::isSelected))
        assertHistoryTab(mapper.buildTabContent(type = selected.selectedTab))
    }

    @Test
    fun menuState_resolvesTheHistoryScreen() {
        val navPrefs = mockk<NavigationPreferencesRepository>()
        every {
            navPrefs.getVisibleTabs()
        } returns listOf(TabType.Home, TabType.Favourites, TabType.History, TabType.Movies)
        every { navPrefs.getStartupTab() } returns TabType.Home
        val screens = mockk<Screens>()
        every { screens.history() } answers { HistoryScreen() }
        val menuMapper = createMapper(navPrefs, screens)

        val state = menuMapper.buildViewState()

        assertEquals(TabType.Home, state.selectedTab)
        assertEquals(
            listOf(TabType.Home, TabType.Favourites, TabType.History, TabType.Movies),
            state.tabs.map(MainTab::type),
        )
        assertEquals(listOf(true, false, false, false), state.tabs.map(MainTab::isSelected))
        assertHistoryTab(menuMapper.buildTabContent(type = TabType.History))
        verify(exactly = 1) {
            screens.history()
        }
    }

    @Test
    fun animeTab_resolvesToContentListScreen() {
        val screens = mockk<Screens>()
        val animeScreen = mockk<PuberScreen>()
        every { animeScreen.key } returns "AnimeContentList"
        every { screens.contentList(TabType.Anime) } returns animeScreen
        val animeMapper = createMapper(
            navPrefs = mockk(relaxed = true),
            screens = screens,
        )

        val tab = animeMapper.buildTabContent(
            type = TabType.Anime,
        )

        assertEquals(TabType.Anime, tab.tag)
        assertEquals("Tab:AnimeContentList", tab.key)
        verify(exactly = 1) { screens.contentList(TabType.Anime) }
    }

    @Test
    fun buildViewState_preservesSelectedTabWhenItRemainsVisible() {
        val navPrefs = mockk<NavigationPreferencesRepository>()
        every {
            navPrefs.getVisibleTabs()
        } returns listOf(TabType.Home, TabType.Movies, TabType.Anime)
        val stateMapper = createMapper(navPrefs)

        val state = stateMapper.buildViewState(previousSelectedTab = TabType.Anime)

        assertEquals(TabType.Anime, state.selectedTab)
        assertEquals(
            listOf(false, false, true),
            state.tabs.map(MainTab::isSelected),
        )
    }

    @Test
    fun buildViewState_usesConfiguredStartupTab() {
        val navPrefs = mockk<NavigationPreferencesRepository>()
        every {
            navPrefs.getVisibleTabs()
        } returns listOf(TabType.Home, TabType.Favourites, TabType.Movies)
        every { navPrefs.getStartupTab() } returns TabType.Favourites
        val stateMapper = createMapper(navPrefs)

        val state = stateMapper.buildViewState()

        assertEquals(TabType.Favourites, state.selectedTab)
        assertEquals(listOf(false, true, false), state.tabs.map(MainTab::isSelected))
    }

    @Test
    fun buildViewState_fallsBackToHomeWhenSelectedTabDisappears() {
        val navPrefs = mockk<NavigationPreferencesRepository>()
        every {
            navPrefs.getVisibleTabs()
        } returns listOf(TabType.Home, TabType.Favourites, TabType.Movies, TabType.Settings)
        every { navPrefs.getStartupTab() } returns TabType.Anime
        val stateMapper = createMapper(navPrefs)

        val state = stateMapper.buildViewState(previousSelectedTab = TabType.Anime)

        assertEquals(TabType.Home, state.selectedTab)
        assertEquals(
            listOf(true, false, false, false),
            state.tabs.map(MainTab::isSelected),
        )
    }

    @Test
    fun historyRefresh_keepsLogicalTabKeyAndAdvancesScreenScopeGeneration() {
        val initial = mapper.buildTabContent(
            type = TabType.History,
        )
        val sameInstance = mapper.buildTabContent(
            type = TabType.History,
        )
        val refreshed = mapper.buildTabContent(
            type = TabType.History,
            refreshVersion = 2,
        )

        assertEquals(initial.key, sameInstance.key)
        assertEquals(
            "Tab:${HistoryScreen().key}",
            initial.key,
        )
        assertEquals(initial.key, refreshed.key)
        assertNotEquals(initial.contentInstanceKey, refreshed.contentInstanceKey)
        assertEquals(
            "Tab:${HistoryScreen().key}:refresh_2",
            refreshed.contentInstanceKey,
        )
        assertEquals(TabType.History, refreshed.tag)
    }

    private fun assertHistoryTab(tab: PuberTab) {
        assertEquals("Tab:${HistoryScreen().key}", tab.key)
        assertEquals(TabType.History, tab.tag)
    }

    private fun mainTab(
        type: TabType,
        isSelected: Boolean = false,
    ): MainTab {
        return MainTab(
            type = type,
            icon = PhosphorIcons.Duotone.House,
            isSelected = isSelected,
        )
    }

    private fun createMapper(
        navPrefs: NavigationPreferencesRepository,
        screens: Screens = ScreensImpl,
    ): MainUIMapper {
        return MainUIMapper(
            resources = FakeResourceProvider(),
            screens = screens,
            navPrefs = navPrefs,
        )
    }
}
