package com.kino.puber.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.kino.puber.domain.model.TabType
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
     * without a restart. A revision counter rather than the list itself: the only thing a listener
     * needs to know is that the menu has moved.
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

    /**
     * Whether a card that keeps focus swaps its still for the trailer. Defaults to on; the key is
     * written only when the user changes it, so nothing is stored for a default install.
     */
    fun getAutoTrailerEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_TRAILER, true)

    fun setAutoTrailerEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_TRAILER, enabled) }
    }

    fun getStartupTab(): TabType {
        val name = prefs.getString(KEY_STARTUP_TAB, TabType.Home.name)
        return TabType.entries.find { it.name == name } ?: TabType.Home
    }

    fun setStartupTab(tab: TabType) {
        prefs.edit { putString(KEY_STARTUP_TAB, tab.name) }
    }

    fun getStartupTabOptions(): List<TabType> {
        return getVisibleTabs().filterNot { tab ->
            tab == TabType.Search || tab == TabType.Settings
        }
    }

    fun getVisibleTabs(): List<TabType> {
        adoptTopTabsMenuIfNeeded()
        val stored = prefs.getString(KEY_DRAWER_TABS, null)
        val baseTabs = ensureRequiredTabs(stored?.let(::deserializeTabs) ?: defaultTabs())
        // Reading stays free of side effects, so the two legacy toggles keep shaping the menu
        // until the user first edits it. The edit writes the whole list, toggles included, and
        // that written list is what governs from then on.
        return if (menuOwnsTabs()) baseTabs else insertLegacyOptionalTabs(baseTabs)
    }

    /**
     * Carries the menu of an install that was left on the top-tabs mode into the one menu there is
     * now. That list is what the user last saw and arranged; the drawer's own list, if they ever
     * had one, is older than it. Runs once — the legacy keys are dropped with the same edit, so a
     * later change to the menu cannot be undone by this.
     */
    private fun adoptTopTabsMenuIfNeeded() {
        val legacyMode = prefs.getString(KEY_LEGACY_NAVIGATION_MODE, null) ?: return
        val legacyTabs = prefs.getString(KEY_LEGACY_TOP_TABS, null)
        prefs.edit {
            if (legacyMode == LEGACY_TOP_TABS_MODE && legacyTabs != null) {
                putString(KEY_DRAWER_TABS, serializeTabs(ensureRequiredTabs(deserializeTabs(legacyTabs))))
                putInt(KEY_MENU_SCHEMA_VERSION, MENU_SCHEMA_VERSION)
            }
            remove(KEY_LEGACY_NAVIGATION_MODE)
            remove(KEY_LEGACY_TOP_TABS)
        }
    }

    fun setVisibleTabs(tabs: List<TabType>) {
        prefs.edit {
            putString(KEY_DRAWER_TABS, serializeTabs(ensureRequiredTabs(tabs)))
            putInt(KEY_MENU_SCHEMA_VERSION, MENU_SCHEMA_VERSION)
        }
        menuTabsRevision.update { it + 1 }
    }

    /**
     * Shows or hides one section. Hiding a tab the menu cannot do without is a no-op rather than
     * an error — [ensureRequiredTabs] would put it straight back anyway.
     */
    fun setTabVisible(tab: TabType, visible: Boolean) {
        val current = getVisibleTabs()
        if ((tab in current) == visible) return
        val updated = if (visible) insertInDeclarationOrder(current, tab) else current - tab
        setVisibleTabs(updated)
    }

    fun setShowAnime(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_ANIME, show) }
        _contentPreferences.update { it.copy(showAnime = show) }
    }

    fun setHideWatched(hide: Boolean) {
        prefs.edit { putBoolean(KEY_HIDE_WATCHED, hide) }
        _contentPreferences.update { it.copy(hideWatched = hide) }
    }

    fun setShowWatchedIndicators(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_WATCHED_INDICATORS, show) }
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

    private fun defaultTabs(): List<TabType> = TabType.entries.filter(TabType::enabled)

    private fun ensureRequiredTabs(tabs: List<TabType>): List<TabType> {
        val result = tabs.toMutableList()
        if (TabType.Settings !in result) {
            result.add(TabType.Settings)
        }
        if (TabType.Home !in result) {
            result.add(0, TabType.Home)
        }
        return result
    }

    private fun menuOwnsTabs(): Boolean {
        return prefs.getInt(KEY_MENU_SCHEMA_VERSION, 0) >= MENU_SCHEMA_VERSION
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
        const val KEY_STARTUP_TAB = "startup_tab"
        const val KEY_DRAWER_TABS = "drawer_tabs_visible"

        /**
         * What the navigation mode and the top-tabs menu were stored under while the app had two
         * menus. Read once by [adoptTopTabsMenuIfNeeded] and then removed.
         */
        const val KEY_LEGACY_NAVIGATION_MODE = "navigation_mode"
        const val KEY_LEGACY_TOP_TABS = "toptabs_tabs_visible"
        const val LEGACY_TOP_TABS_MODE = "TopTabs"
        // The suffix is what the drawer's menu was stored under while there was a second
        // navigation mode to tell it apart from. Kept verbatim so an existing install keeps the
        // menu it has.
        const val KEY_MENU_SCHEMA_VERSION = "menu_tabs_schema_version_SideDrawer"
        const val KEY_SHOW_CARTOONS_TAB = "show_cartoons_tab"
        const val KEY_SHOW_ANIME_TAB = "show_anime_tab"
        const val KEY_SHOW_ANIME = "show_anime"
        const val KEY_HIDE_WATCHED = "hide_watched"
        const val KEY_SHOW_WATCHED_INDICATORS = "show_watched_indicators"
        const val KEY_AUTO_TRAILER = "auto_trailer_enabled"
        const val LEGACY_PLAYER_PREFS_NAME = "player_preferences"
        const val LEGACY_KEY_WATCHED_INDICATORS = "watched_indicators_enabled"
        const val MENU_SCHEMA_VERSION = 1
        const val SEPARATOR = ","
    }
}
