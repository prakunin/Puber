package com.kino.puber.ui.feature.device.settings.model

import com.kino.puber.core.model.AppLanguage
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.device.DeviceSettingType
import com.kino.puber.ui.feature.main.model.TabType

internal sealed interface DeviceSettingsActions : UIAction {

    data class ChangeSettingValue(val setting: DeviceSettingUIModel.TypeValue) : DeviceSettingsActions
    data class ToggleListExpand(val setting: DeviceSettingUIModel.TypeList) : DeviceSettingsActions
    data class SelectOption(val type: DeviceSettingType, val optionId: Int) : DeviceSettingsActions
    data object ToggleSkipIntro : DeviceSettingsActions
    data object ToggleSkipRecap : DeviceSettingsActions
    data object ToggleSkipCredits : DeviceSettingsActions
    data object ToggleDebugOverlay : DeviceSettingsActions
    data object ToggleSurroundAudio : DeviceSettingsActions
    data object ToggleOkTogglesPlayPause : DeviceSettingsActions
    data object ToggleShowMarkWatchedButton : DeviceSettingsActions
    data object ToggleWatchedIndicators : DeviceSettingsActions
    data object ToggleDiscardEmbeddedArtworkMetadata : DeviceSettingsActions
    data object ToggleHagcPlayback : DeviceSettingsActions
    data object ToggleAutoTrailer : DeviceSettingsActions
    data class ChangeStartupTab(val tab: TabType) : DeviceSettingsActions
    data class ToggleMenuSection(val tab: TabType) : DeviceSettingsActions
    data object ToggleShowAnime : DeviceSettingsActions
    data object ToggleHideWatched : DeviceSettingsActions
    data object RebuildWatchIndex : DeviceSettingsActions
    data object ToggleAutoUpdateCheck : DeviceSettingsActions
    data object CheckForUpdatesNow : DeviceSettingsActions
    data class ChangeAppLanguage(val language: AppLanguage) : DeviceSettingsActions
    data object OpenApiDomainDialog : DeviceSettingsActions
    data object CloseApiDomainDialog : DeviceSettingsActions
    data object OpenNetworkDiagnostics : DeviceSettingsActions
    data object NetworkDiagnosticsFocusRestored : DeviceSettingsActions
    data class SaveApiDomain(val domain: String) : DeviceSettingsActions
    data object DetectApiDomain : DeviceSettingsActions
    data object ResetApiDomain : DeviceSettingsActions
}
