package com.kino.puber.ui.feature.device.settings.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState
import com.kino.puber.domain.interactor.device.DeviceSettingType
import com.kino.puber.ui.feature.main.model.TabType

@Immutable
internal data class DeviceSettingsViewState(
    val state: DeviceSettingsState = DeviceSettingsState.Loading,
    val apiDomain: ApiDomainDialogState,
    val isApiDomainDialogOpen: Boolean = false,
)

@Immutable
internal sealed interface DeviceSettingsState {
    object Loading : DeviceSettingsState
    data class Error(val error: String) : DeviceSettingsState
    @Immutable
    data class Success(
        val settings: DeviceSettingsListUi,
        val device: DeviceUi,
        val expandedType: DeviceSettingType? = null,
        val savingOptionId: Int? = null,
        val savingToggleType: DeviceSettingType? = null,
        val skipIntroEnabled: Boolean = true,
        val skipRecapEnabled: Boolean = true,
        val skipCreditsEnabled: Boolean = true,
        val debugOverlayEnabled: Boolean = false,
        val okTogglesPlayPause: Boolean = false,
        val showMarkWatchedButton: Boolean = false,
        val preferSurroundAudio: Boolean = false,
        val watchedIndicatorsEnabled: Boolean = true,
        val navigationMode: NavigationMode = NavigationMode.TopTabs,
        val startupTab: TabType = TabType.Home,
        val startupTabOptions: List<TabType> = listOf(TabType.Home),
        val menuSections: List<MenuSectionUi> = emptyList(),
        val showAnime: Boolean = true,
        val hideWatched: Boolean = false,
        val autoUpdateCheckEnabled: Boolean = true,
        val watchIndex: WatchIndexUiState = WatchIndexUiState(),
    ) : DeviceSettingsState
}

/**
 * One section the menu may or may not carry. The label comes from [TabType] at draw time, and
 * whether the row can be switched off is read off the startup tab, so neither is duplicated here.
 */
@Immutable
internal data class MenuSectionUi(
    val tab: TabType,
    val visible: Boolean,
)

/** The sections a user may add to or remove from the menu. */
internal val SelectableMenuTabs: List<TabType> = TabType.entries.filterNot { tab ->
    // Home anchors the menu, Search and Settings are structural, and SportTV has no screen yet.
    tab == TabType.Home ||
        tab == TabType.Search ||
        tab == TabType.Settings ||
        tab == TabType.SportTV
}

@Immutable
internal data class WatchIndexUiState(
    val fullyWatchedItems: Int = 0,
    val indexedItems: Int = 0,
    val isSyncing: Boolean = false,
    val currentPage: Int? = null,
    val totalPages: Int? = null,
    val totalHistoryItems: Int? = null,
    val fullHistoryWalkDone: Boolean = false,
    /**
     * The two below come off the stored cursor rather than the run in progress, so the screen can
     * say what the index already holds the moment it opens. Sync progress only exists while a run
     * is under way, which is never the case on a cold entry to the section.
     */
    val lastSyncAt: Long? = null,
    val historyResumePage: Int = 1,
)
