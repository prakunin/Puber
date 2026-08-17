package com.kino.puber.ui.feature.device.settings.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.kino.puber.R
import com.kino.puber.domain.interactor.device.DeviceSettingType

internal enum class SettingsSection(@StringRes val titleRes: Int) {
    General(R.string.settings_section_general),
    Playback(R.string.settings_section_playback),
    Content(R.string.settings_section_content),
    Navigation(R.string.settings_section_navigation),
    Network(R.string.settings_section_network),
    Data(R.string.settings_section_data),
    Developer(R.string.settings_section_developer),
}

@Immutable
sealed interface DeviceSettingUIModel {
    data class TypeValue(
        val type: DeviceSettingType,
        val value: Boolean,
        val label: String,
        val supported: Boolean = true,
    ) : DeviceSettingUIModel

    data class TypeList(
        val type: DeviceSettingType,
        val values: List<SettingOptionUi>,
        val label: String,
    ) : DeviceSettingUIModel
}

@Immutable
internal data class DeviceSettingsListUi(
    val settingsList: List<DeviceSettingUIModel>
)

@Immutable
data class SettingOptionUi(
    val id: Int,
    val label: String,
    val description: String = "",
    val selected: Boolean
)

@Immutable
internal data class DeviceUi(
    val title: String,
    val hardware: String,
    val software: String
)
