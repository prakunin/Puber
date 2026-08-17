package com.kino.puber.ui.feature.device.settings

internal object SettingsTestTags {
    const val Navigation = "settings-navigation"
    const val Content = "settings-content"
    const val ErrorRetry = "settings-error-retry"

    fun section(name: String) = "settings-section-$name"
}
