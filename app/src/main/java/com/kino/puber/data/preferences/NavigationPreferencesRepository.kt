package com.kino.puber.data.preferences

import android.content.Context
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

data class ContentPreferences(
    val showAnime: Boolean,
    val hideWatched: Boolean,
    val showWatchedIndicators: Boolean,
)

class NavigationPreferencesRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _contentPreferences = MutableStateFlow(
        ContentPreferences(
            showAnime = prefs.getBoolean(KEY_SHOW_ANIME, true),
            hideWatched = prefs.getBoolean(KEY_HIDE_WATCHED, false),
            showWatchedIndicators = readWatchedIndicators(context),
        )
    )
    val contentPreferences: StateFlow<ContentPreferences> = _contentPreferences.asStateFlow()

    private val menuTabsRevision = MutableStateFlow(0)

    /**
     * Emits whenever the set of menu sections changes, so the main screen can rebuild its menu
     * without a restart. A revision counter rather than the list itself: the list is per
     * navigation mode, and the only thing a listener needs to know is that its own mode may have
     * moved.
     */
    val menuTabsChanges: Flow<Unit> = menuTabsRevision.drop(1).map { }

    /**
     * Emits whenever a setting that changes what a catalogue card shows flips — hiding watched
     * titles, or the watched marks themselves. Screens map their cards once and hold the result,
     * and moving between screens does not pause the activity, so without this a screen would keep
     * showing the previous choice until it happened to reload for some other reason.
     */
    val displaySettingsChanges: Flow<Unit> = contentPreferences
        .map { it.hideWatched to it.showWatchedIndicators }
        .distinctUntilChanged()
        .drop(1)
        .map { }

    fun getNavigationMode(): NavigationMode {
        val name = prefs.getString(KEY_NAVIGATION_MODE, NavigationMode.TopTabs.name)
        return NavigationMode.entries.find { it.name == name } ?: NavigationMode.TopTabs
    }

    fun setNavigationMode(mode: NavigationMode) {
        prefs.edit().putString(KEY_NAVIGATION_MODE, mode.name).apply()
    }

    fun getStartupTab(): TabType {
        val name = prefs.getString(KEY_STARTUP_TAB, TabType.Home.name)
        return TabType.entries.find { it.name == name } ?: TabType.Home
    }

    fun setStartupTab(tab: TabType) {
        prefs.edit().putString(KEY_STARTUP_TAB, tab.name).apply()
    }

    fun getStartupTabOptions(mode: NavigationMode): List<TabType> {
        return getVisibleTabs(mode).filterNot { tab ->
            tab == TabType.Search || tab == TabType.Settings
        }
    }

    fun getVisibleTabs(mode: NavigationMode): List<TabType> {
        if (mode == NavigationMode.TopTabs) {
            migrateTopTabsIfNeeded()
        }
        val key = tabsKeyForMode(mode)
        val stored = prefs.getString(key, null)
        val baseTabs = ensureRequiredTabs(mode, stored?.let(::deserializeTabs) ?: defaultTabsForMode(mode))
        // Reading stays free of side effects, so the two legacy toggles keep shaping the menu
        // until the user first edits it. The edit writes the whole list, toggles included, and
        // that written list is what governs from then on.
        return if (menuOwnsTabs(mode)) baseTabs else insertLegacyOptionalTabs(baseTabs)
    }

    private fun migrateTopTabsIfNeeded() {
        val currentVersion = prefs.getInt(KEY_TOP_TABS_SCHEMA_VERSION, 0)
        if (currentVersion >= TOP_TABS_SCHEMA_VERSION_HISTORY) return

        val stored = prefs.getString(KEY_TOP_TABS, null)
        val currentTabs = stored
            ?.let(::deserializeTabs)
            ?: resolveTabNames(TOP_TABS_DEFAULT_TAB_NAMES)
        val normalizedTabs = normalizeTopTabsForHistory(currentTabs)
        val editor = prefs.edit()
        editor.putString(KEY_TOP_TABS, serializeTabs(normalizedTabs))
        editor.putInt(KEY_TOP_TABS_SCHEMA_VERSION, TOP_TABS_SCHEMA_VERSION_HISTORY)
        editor.apply()
    }

    private fun normalizeTopTabsForHistory(tabs: List<TabType>): List<TabType> {
        val normalized = tabs
            .filterNot { it == TabType.Search || it == TabType.Settings || it == TabType.History }
            .toMutableList()
        if (TabType.Home !in normalized) {
            normalized.add(index = 0, element = TabType.Home)
        }
        val collectionsIndex = normalized.indexOf(TabType.Collections)
        val historyIndex = if (collectionsIndex >= 0) collectionsIndex + 1 else normalized.size
        normalized.add(index = historyIndex, element = TabType.History)
        return normalized
    }

    fun setVisibleTabs(mode: NavigationMode, tabs: List<TabType>) {
        prefs.edit()
            .putString(tabsKeyForMode(mode), serializeTabs(ensureRequiredTabs(mode, tabs)))
            .putInt(menuSchemaKeyForMode(mode), MENU_SCHEMA_VERSION)
            .apply()
        menuTabsRevision.update { it + 1 }
    }

    /**
     * Shows or hides one section. Hiding a tab the menu cannot do without is a no-op rather than
     * an error — [ensureRequiredTabs] would put it straight back anyway.
     */
    fun setTabVisible(mode: NavigationMode, tab: TabType, visible: Boolean) {
        val current = getVisibleTabs(mode)
        if ((tab in current) == visible) return
        val updated = if (visible) insertInDeclarationOrder(current, tab) else current - tab
        setVisibleTabs(mode, updated)
    }

    fun setShowAnime(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_ANIME, show).apply()
        _contentPreferences.update { it.copy(showAnime = show) }
    }

    fun setHideWatched(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_WATCHED, hide).apply()
        _contentPreferences.update { it.copy(hideWatched = hide) }
    }

    fun setShowWatchedIndicators(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_WATCHED_INDICATORS, show).apply()
        _contentPreferences.update { it.copy(showWatchedIndicators = show) }
    }

    /**
     * The setting used to live in the player preferences, where nothing could observe it and lists
     * kept showing the old choice until they happened to reload. It moves here on first read so an
     * existing choice is not silently reset.
     */
    private fun readWatchedIndicators(context: Context): Boolean {
        if (prefs.contains(KEY_SHOW_WATCHED_INDICATORS)) {
            return prefs.getBoolean(KEY_SHOW_WATCHED_INDICATORS, true)
        }

        // Read, not copied: writing here would mean a side effect in the constructor, and the value
        // lands in the new place as soon as the setting is next touched.
        return context
            .getSharedPreferences(LEGACY_PLAYER_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(LEGACY_KEY_WATCHED_INDICATORS, true)
    }

    private fun defaultTabsForMode(mode: NavigationMode): List<TabType> {
        return when (mode) {
            NavigationMode.SideDrawer -> TabType.entries.filter(TabType::enabled)
            NavigationMode.TopTabs -> resolveTabNames(TOP_TABS_DEFAULT_TAB_NAMES)
        }
    }

    private fun resolveTabNames(names: List<String>): List<TabType> {
        return names.mapNotNull { name ->
            TabType.entries.find { it.name == name }
        }
    }

    private fun ensureRequiredTabs(mode: NavigationMode, tabs: List<TabType>): List<TabType> {
        val result = tabs.toMutableList()
        if (mode == NavigationMode.TopTabs) {
            result.removeAll { it == TabType.Search || it == TabType.Settings }
        } else {
            if (TabType.Settings !in result) {
                result.add(TabType.Settings)
            }
        }
        if (TabType.Home !in result) {
            result.add(0, TabType.Home)
        }
        return result
    }

    private fun menuOwnsTabs(mode: NavigationMode): Boolean {
        return prefs.getInt(menuSchemaKeyForMode(mode), 0) >= MENU_SCHEMA_VERSION
    }

    /** Places [tab] where the menu order — [TabType]'s own declaration order — expects it. */
    private fun insertInDeclarationOrder(tabs: List<TabType>, tab: TabType): List<TabType> {
        val index = tabs.indexOfFirst { it.ordinal > tab.ordinal }
        if (index < 0) return tabs + tab
        return tabs.toMutableList().apply { add(index, tab) }
    }

    private fun insertLegacyOptionalTabs(tabs: List<TabType>): List<TabType> {
        val normalized = tabs.filterNot { it.isOptionalContentTab() }.toMutableList()
        val optionalTabs = buildList {
            if (prefs.getBoolean(KEY_SHOW_CARTOONS_TAB, false)) add(TabType.Cartoons)
            if (prefs.getBoolean(KEY_SHOW_ANIME_TAB, false)) add(TabType.Anime)
        }
        if (optionalTabs.isEmpty()) return normalized

        val anchorIndex = normalized.indexOf(TabType.Series)
            .takeIf { it >= 0 }
            ?: normalized.indexOf(TabType.Movies).takeIf { it >= 0 }
        val insertionIndex = anchorIndex
            ?.plus(1)
            ?: normalized.indexOfFirst {
                it == TabType.Collections || it == TabType.History || it == TabType.Settings
            }.takeIf { it >= 0 }
            ?: normalized.size
        normalized.addAll(insertionIndex, optionalTabs)
        return normalized
    }

    private fun TabType.isOptionalContentTab(): Boolean {
        return this == TabType.Cartoons || this == TabType.Anime
    }

    private fun menuSchemaKeyForMode(mode: NavigationMode): String {
        return KEY_MENU_SCHEMA_VERSION_PREFIX + mode.name
    }

    private fun tabsKeyForMode(mode: NavigationMode): String {
        return when (mode) {
            NavigationMode.SideDrawer -> KEY_DRAWER_TABS
            NavigationMode.TopTabs -> KEY_TOP_TABS
        }
    }

    private fun serializeTabs(tabs: List<TabType>): String {
        return tabs.joinToString(SEPARATOR) { it.name }
    }

    private fun deserializeTabs(value: String): List<TabType> {
        if (value.isBlank()) return emptyList()
        return value.split(SEPARATOR).mapNotNull { name ->
            TabType.entries.find { it.name == name }
        }
    }

    private companion object {
        const val PREFS_NAME = "navigation_preferences"
        const val KEY_NAVIGATION_MODE = "navigation_mode"
        const val KEY_STARTUP_TAB = "startup_tab"
        const val KEY_DRAWER_TABS = "drawer_tabs_visible"
        const val KEY_TOP_TABS = "toptabs_tabs_visible"
        const val KEY_TOP_TABS_SCHEMA_VERSION = "toptabs_schema_version"
        const val KEY_MENU_SCHEMA_VERSION_PREFIX = "menu_tabs_schema_version_"
        const val KEY_SHOW_CARTOONS_TAB = "show_cartoons_tab"
        const val KEY_SHOW_ANIME_TAB = "show_anime_tab"
        const val KEY_SHOW_ANIME = "show_anime"
        const val KEY_HIDE_WATCHED = "hide_watched"
        const val KEY_SHOW_WATCHED_INDICATORS = "show_watched_indicators"
        const val LEGACY_PLAYER_PREFS_NAME = "player_preferences"
        const val LEGACY_KEY_WATCHED_INDICATORS = "watched_indicators_enabled"
        const val TOP_TABS_SCHEMA_VERSION_HISTORY = 1
        const val MENU_SCHEMA_VERSION = 1
        const val SEPARATOR = ","

        val TOP_TABS_DEFAULT_TAB_NAMES = listOf(
            "Home",
            "Movies",
            "Series",
            "Collections",
            "History",
        )
    }
}
