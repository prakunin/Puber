package com.kino.puber.ui.feature.device.settings

internal object SettingsTestTags {
    const val Navigation = "settings-navigation"
    const val Content = "settings-content"
    const val ErrorRetry = "settings-error-retry"
    const val ScreenTitle = "settings-screen-title"
    const val SectionTitle = "settings-section-title"
    const val WatchSummary = "settings-watch-summary"
    const val AboutDevice = "settings-about-device"
    const val TmdbAttribution = "settings-tmdb-attribution"

    fun section(name: String) = "settings-section-$name"
}
