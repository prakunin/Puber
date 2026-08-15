package com.kino.puber.ui.feature.device.settings.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState
import com.kino.puber.domain.interactor.device.DeviceSettingType

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
        val showCartoonsTab: Boolean = false,
        val showAnimeTab: Boolean = false,
        val showAnime: Boolean = true,
        val hideWatched: Boolean = false,
        val autoUpdateCheckEnabled: Boolean = true,
        val watchIndex: WatchIndexUiState = WatchIndexUiState(),
    ) : DeviceSettingsState
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
)
