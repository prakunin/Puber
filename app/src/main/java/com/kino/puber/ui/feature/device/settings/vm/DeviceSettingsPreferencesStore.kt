package com.kino.puber.ui.feature.device.settings.vm

import com.kino.puber.core.model.AppLanguage
import com.kino.puber.data.preferences.AppLanguageRepository
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.domain.interactor.update.IAppUpdateInteractor
import com.kino.puber.ui.feature.main.model.TabType

/** Local, synchronous settings used by the Settings screen. */
internal interface DeviceSettingsPreferencesStore {
    fun read(): DeviceSettingsPreferencesSnapshot
    fun setSkipPreferences(intro: Boolean, recap: Boolean, credits: Boolean)
    fun setDebugOverlay(enabled: Boolean)
    fun setPreferSurroundAudio(enabled: Boolean)
    fun setOkTogglesPlayPause(enabled: Boolean)
    fun setShowMarkWatchedButton(enabled: Boolean)
    fun setShowWatchedIndicators(enabled: Boolean)
    fun setAutoTrailer(enabled: Boolean)
    fun getStartupTabOptions(): List<TabType>
    fun setStartupTab(tab: TabType)
    fun getVisibleTabs(): List<TabType>
    fun setTabVisible(tab: TabType, visible: Boolean)
    fun setShowAnime(enabled: Boolean)
    fun setHideWatched(enabled: Boolean)
    fun setAutoUpdateCheck(enabled: Boolean)
    fun setAppLanguage(language: AppLanguage)
}

internal data class DeviceSettingsPreferencesSnapshot(
    val skipIntroEnabled: Boolean,
    val skipRecapEnabled: Boolean,
    val skipCreditsEnabled: Boolean,
    val debugOverlayEnabled: Boolean,
    val okTogglesPlayPause: Boolean,
    val showMarkWatchedButton: Boolean,
    val preferSurroundAudio: Boolean,
    val watchedIndicatorsEnabled: Boolean,
    val autoTrailerEnabled: Boolean,
    val startupTab: TabType,
    val startupTabOptions: List<TabType>,
    val visibleTabs: List<TabType>,
    val showAnime: Boolean,
    val hideWatched: Boolean,
    val autoUpdateCheckEnabled: Boolean,
    val appLanguage: AppLanguage,
)

internal class DefaultDeviceSettingsPreferencesStore(
    private val playerPreferences: PlayerPreferencesRepository,
    private val navigationPreferences: NavigationPreferencesRepository,
    private val appLanguagePreferences: AppLanguageRepository,
    private val appUpdatePreferences: IAppUpdateInteractor,
) : DeviceSettingsPreferencesStore {

    override fun read(): DeviceSettingsPreferencesSnapshot {
        val content = navigationPreferences.contentPreferences.value
        val startupOptions = navigationPreferences.getStartupTabOptions()
        val startupTab = navigationPreferences.getStartupTab()
            .takeIf(startupOptions::contains)
            ?: TabType.Home
        return DeviceSettingsPreferencesSnapshot(
            skipIntroEnabled = playerPreferences.skipIntroEnabled,
            skipRecapEnabled = playerPreferences.skipRecapEnabled,
            skipCreditsEnabled = playerPreferences.skipCreditsEnabled,
            debugOverlayEnabled = playerPreferences.debugOverlayEnabled,
            okTogglesPlayPause = playerPreferences.okTogglesPlayPause,
            showMarkWatchedButton = playerPreferences.showMarkWatchedButton,
            preferSurroundAudio = playerPreferences.preferSurroundAudio,
            watchedIndicatorsEnabled = content.showWatchedIndicators,
            autoTrailerEnabled = navigationPreferences.getAutoTrailerEnabled(),
            startupTab = startupTab,
            startupTabOptions = startupOptions,
            visibleTabs = navigationPreferences.getVisibleTabs(),
            showAnime = content.showAnime,
            hideWatched = content.hideWatched,
            autoUpdateCheckEnabled = appUpdatePreferences.isAutoCheckEnabled(),
            appLanguage = appLanguagePreferences.getLanguage(),
        )
    }

    override fun setSkipPreferences(intro: Boolean, recap: Boolean, credits: Boolean) {
        playerPreferences.skipIntroEnabled = intro
        playerPreferences.skipRecapEnabled = recap
        playerPreferences.skipCreditsEnabled = credits
    }

    override fun setDebugOverlay(enabled: Boolean) {
        playerPreferences.debugOverlayEnabled = enabled
    }

    override fun setPreferSurroundAudio(enabled: Boolean) {
        playerPreferences.preferSurroundAudio = enabled
    }

    override fun setOkTogglesPlayPause(enabled: Boolean) {
        playerPreferences.okTogglesPlayPause = enabled
    }

    override fun setShowMarkWatchedButton(enabled: Boolean) {
        playerPreferences.showMarkWatchedButton = enabled
    }

    override fun setShowWatchedIndicators(enabled: Boolean) {
        navigationPreferences.setShowWatchedIndicators(enabled)
    }

    override fun setAutoTrailer(enabled: Boolean) {
        navigationPreferences.setAutoTrailerEnabled(enabled)
    }

    override fun getStartupTabOptions(): List<TabType> =
        navigationPreferences.getStartupTabOptions()

    override fun setStartupTab(tab: TabType) {
        navigationPreferences.setStartupTab(tab)
    }

    override fun getVisibleTabs(): List<TabType> =
        navigationPreferences.getVisibleTabs()

    override fun setTabVisible(tab: TabType, visible: Boolean) {
        navigationPreferences.setTabVisible(tab, visible)
    }

    override fun setShowAnime(enabled: Boolean) {
        navigationPreferences.setShowAnime(enabled)
    }

    override fun setHideWatched(enabled: Boolean) {
        navigationPreferences.setHideWatched(enabled)
    }

    override fun setAutoUpdateCheck(enabled: Boolean) {
        appUpdatePreferences.setAutoCheckEnabled(enabled)
    }

    override fun setAppLanguage(language: AppLanguage) {
        appLanguagePreferences.setLanguage(language)
    }
}
