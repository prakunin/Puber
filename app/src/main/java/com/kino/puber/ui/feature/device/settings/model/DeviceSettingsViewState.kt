package com.kino.puber.ui.feature.device.settings.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.model.AppLanguage
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState
import com.kino.puber.domain.interactor.device.DeviceSettingType
import com.kino.puber.ui.feature.main.model.TabType

@Immutable
internal data class DeviceSettingsViewState(
    val state: DeviceSettingsState = DeviceSettingsState.Loading,
    val apiDomain: ApiDomainDialogState,
    val isApiDomainDialogOpen: Boolean = false,
    val restoreNetworkDiagnosticsFocus: Boolean = false,
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
        val discardEmbeddedArtworkMetadata: Boolean = true,
        val hagcPlaybackEnabled: Boolean = false,
        val autoTrailerEnabled: Boolean = true,
        val startupTab: TabType = TabType.Home,
        val startupTabOptions: List<TabType> = listOf(TabType.Home),
        val menuSections: List<MenuSectionUi> = emptyList(),
        val showAnime: Boolean = true,
        val hideWatched: Boolean = false,
        val autoUpdateCheckEnabled: Boolean = true,
        val appLanguage: AppLanguage = AppLanguage.System,
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

/**
 * The sections a user may add to or remove from the menu.
 *
 * Keep this list explicit: a newly declared [TabType] must not become user-visible before its
 * screen and both navigation modes are ready for it.
 */
internal val SelectableMenuTabs: List<TabType> = listOf(
    TabType.Favourites,
    TabType.Bookmarks,
    TabType.History,
    TabType.Movies,
    TabType.Series,
    TabType.Cartoons,
    TabType.Anime,
    TabType.For4k,
    TabType.Concerts,
    TabType.DocMovies,
    TabType.DocSeries,
    TabType.TvShows,
    TabType.Collections,
)

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
     * Comes off the stored cursor rather than the run in progress, so the screen can say what the
     * index already holds the moment it opens. Sync progress only exists while a run is under way,
     * which is never the case on a cold entry to the section.
     */
    val lastSyncAt: Long? = null,
) {

    /**
     * How much of the history the run under way has read, or null when it has not reported a
     * position yet. Rounded down, and never allowed to reach a hundred: pages deleted mid-walk can
     * push the current page past the total the first page reported, and "100 %" while the walk is
     * still going says the opposite of what is happening.
     */
    val walkedPercent: Int?
        get() {
            val current = currentPage ?: return null
            val total = totalPages?.takeIf { it > 0 } ?: return null
            return (current * PERCENT / total).coerceIn(0, PERCENT - 1)
        }

    private companion object {
        const val PERCENT = 100
    }
}
