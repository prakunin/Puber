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
        val supported: Boolean = true,
    ) : DeviceSettingUIModel

    data class TypeList(
        val type: DeviceSettingType,
        val values: List<SettingOptionUi>,
    ) : DeviceSettingUIModel
}

/**
 * The title shown for a device setting. KinoPub sends one with every setting, but always in
 * Russian, so the English build would keep reading Russian. The set of settings is fixed and
 * known here, so the titles are ours; only the option names inside a list stay the service's.
 */
@get:StringRes
internal val DeviceSettingType.titleRes: Int
    get() = when (this) {
        DeviceSettingType.STREAMING_TYPE -> R.string.device_setting_streaming_type
        DeviceSettingType.SERVER_LOCATION -> R.string.device_setting_server_location
        DeviceSettingType.SUPPORT_SSL -> R.string.device_setting_support_ssl
        DeviceSettingType.SUPPORT_HEVC -> R.string.device_setting_support_hevc
        DeviceSettingType.SUPPORT_HDR -> R.string.device_setting_support_hdr
        DeviceSettingType.SUPPORT_4K -> R.string.device_setting_support_4k
        DeviceSettingType.MIXED_PLAYLIST -> R.string.device_setting_mixed_playlist
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
