package com.kino.puber.ui.feature.device.settings.model

import com.kino.puber.R
import com.kino.puber.domain.interactor.device.DeviceSettingType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class DeviceSettingsUiModelsTest {

    @Test
    fun localizedLabelRes_mapsKnownRussianServerLabels() {
        assertEquals(
            R.string.device_setting_server_netherlands,
            option("Нидерланды").localizedLabelRes(DeviceSettingType.SERVER_LOCATION),
        )
        assertEquals(
            R.string.device_setting_server_moscow,
            option("Москва").localizedLabelRes(DeviceSettingType.SERVER_LOCATION),
        )
        assertEquals(
            R.string.device_setting_server_automatic,
            option("Автоматически").localizedLabelRes(DeviceSettingType.SERVER_LOCATION),
        )
    }

    @Test
    fun localizedLabelRes_keepsUnknownOrNonServerLabelsUnmapped() {
        assertNull(option("New location").localizedLabelRes(DeviceSettingType.SERVER_LOCATION))
        assertNull(option("Москва").localizedLabelRes(DeviceSettingType.STREAMING_TYPE))
    }

    private fun option(label: String) = SettingOptionUi(
        id = 1,
        label = label,
        selected = false,
    )
}
