package com.kino.puber.ui.feature.device.settings.vm

import com.kino.puber.R
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.model.AppLanguage
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.repository.WatchState
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.data.repository.WatchStateSyncCursor
import com.kino.puber.domain.interactor.api.ApiDomainDetectionResult
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.api.ApiDomainUpdateResult
import com.kino.puber.domain.interactor.device.DeviceSettingType
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import com.kino.puber.domain.interactor.update.AppUpdateCheckCoordinator
import com.kino.puber.domain.interactor.watchstate.WatchStateSyncProgress
import com.kino.puber.domain.interactor.watchstate.WatchStateSyncInteractor
import com.kino.puber.ui.feature.device.settings.mappers.DeviceCapabilities
import com.kino.puber.ui.feature.device.settings.mappers.DeviceUiSettingsMapper
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsListUi
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsViewState
import com.kino.puber.ui.feature.device.settings.model.MenuSectionUi
import com.kino.puber.ui.feature.device.settings.model.SelectableMenuTabs
import com.kino.puber.ui.feature.device.settings.model.WatchIndexUiState
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine

internal class DeviceSettingsVM(
    private val deviceSettingInteractor: IDeviceSettingInteractor,
    private val deviceInfoInteractor: IDeviceInfoInteractor,
    private val deviceUiSettingsMapper: DeviceUiSettingsMapper,
    private val preferencesStore: DeviceSettingsPreferencesStore,
    private val apiDomainInteractor: ApiDomainInteractor,
    private val watchStateRepository: WatchStateRepository,
    private val watchStateSyncInteractor: WatchStateSyncInteractor,
    private val updateCheckCoordinator: AppUpdateCheckCoordinator,
    override val errorHandler: ErrorHandler,
    private val resources: ResourceProvider,
    router: AppRouter,
) : PuberVM<DeviceSettingsViewState>(router) {

    private var latestWatchIndex = WatchIndexUiState()

    private val capabilities by lazy {
        DeviceCapabilities(
            hevcSupported = deviceInfoInteractor.isHevcSupported(),
            hdrSupported = deviceInfoInteractor.isHdrSupported(),
            is4kSupported = deviceInfoInteractor.is4kSupported(),
        )
    }

    override val initialViewState: DeviceSettingsViewState
        get() = DeviceSettingsViewState(apiDomain = apiDomainInteractor.getState().toDialogState())

    override fun onStart() {
        observeWatchIndex()
        loadDeviceSettings()
    }

    private fun observeWatchIndex() {
        launch {
            combine(
                watchStateRepository.snapshot,
                watchStateSyncInteractor.progress,
                watchStateRepository.syncCursorState,
            ) { index, progress, cursor -> Triple(index, progress, cursor) }
                .collect { (index, progress, cursor) ->
                    latestWatchIndex = buildWatchIndexUiState(index, progress, cursor)
                    val currentState = stateValue.state as? DeviceSettingsState.Success
                    if (currentState != null) {
                        updateViewState(
                            stateValue.copy(state = currentState.copy(watchIndex = latestWatchIndex))
                        )
                    }
                }
        }
    }

    private fun loadDeviceSettings() {
        launch {
            updateViewState(stateValue.copy(state = DeviceSettingsState.Loading))
            deviceSettingInteractor.getCurrentDeviceSettings().collect { currentDevice ->
                if (currentDevice.isSuccess) {
                    val device = currentDevice.getOrThrow()
                    val preferences = preferencesStore.read()
                    updateViewState(
                        stateValue.copy(
                            state = DeviceSettingsState.Success(
                                settings = deviceUiSettingsMapper.mapSettings(
                                    device.device.settings,
                                    capabilities
                                ),
                                device = deviceUiSettingsMapper.mapDevice(device.device),
                                skipIntroEnabled = preferences.skipIntroEnabled,
                                skipRecapEnabled = preferences.skipRecapEnabled,
                                skipCreditsEnabled = preferences.skipCreditsEnabled,
                                debugOverlayEnabled = preferences.debugOverlayEnabled,
                                okTogglesPlayPause = preferences.okTogglesPlayPause,
                                showMarkWatchedButton = preferences.showMarkWatchedButton,
                                preferSurroundAudio = preferences.preferSurroundAudio,
                                watchedIndicatorsEnabled = preferences.watchedIndicatorsEnabled,
                                autoTrailerEnabled = preferences.autoTrailerEnabled,
                                navigationMode = preferences.navigationMode,
                                startupTab = preferences.startupTab,
                                startupTabOptions = preferences.startupTabOptions,
                                menuSections = buildMenuSections(preferences.visibleTabs),
                                showAnime = preferences.showAnime,
                                hideWatched = preferences.hideWatched,
                                autoUpdateCheckEnabled = preferences.autoUpdateCheckEnabled,
                                appLanguage = preferences.appLanguage,
                                watchIndex = latestWatchIndex,
                            )
                        )
                    )
                } else {
                    throw IllegalStateException(currentDevice.exceptionOrNull())
                }
            }
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is DeviceSettingsActions.ChangeSettingValue -> onChangeSettingValue(action.setting)
            is DeviceSettingsActions.ToggleListExpand -> onToggleListExpand(action.setting)
            is DeviceSettingsActions.SelectOption -> onSelectOption(action.type, action.optionId)
            DeviceSettingsActions.ToggleSkipIntro -> toggleSkipPref { it.copy(skipIntroEnabled = !it.skipIntroEnabled) }
            DeviceSettingsActions.ToggleSkipRecap -> toggleSkipPref { it.copy(skipRecapEnabled = !it.skipRecapEnabled) }
            DeviceSettingsActions.ToggleSkipCredits -> toggleSkipPref {
                it.copy(skipCreditsEnabled = !it.skipCreditsEnabled)
            }
            DeviceSettingsActions.ToggleDebugOverlay -> toggleDebugOverlay()
            DeviceSettingsActions.ToggleSurroundAudio -> toggleSurroundAudio()
            DeviceSettingsActions.ToggleOkTogglesPlayPause -> toggleOkTogglesPlayPause()
            DeviceSettingsActions.ToggleShowMarkWatchedButton -> toggleShowMarkWatchedButton()
            DeviceSettingsActions.ToggleWatchedIndicators -> toggleWatchedIndicators()
            DeviceSettingsActions.ToggleAutoTrailer -> toggleAutoTrailer()
            is DeviceSettingsActions.ChangeNavigationMode -> onChangeNavigationMode(action.mode)
            is DeviceSettingsActions.ChangeStartupTab -> onChangeStartupTab(action.tab)
            is DeviceSettingsActions.ToggleMenuSection -> onToggleMenuSection(action.tab)
            DeviceSettingsActions.ToggleShowAnime -> toggleShowAnime()
            DeviceSettingsActions.ToggleHideWatched -> toggleHideWatched()
            DeviceSettingsActions.RebuildWatchIndex -> watchStateSyncInteractor.requestSync(rebuild = true)
            DeviceSettingsActions.ToggleAutoUpdateCheck -> toggleAutoUpdateCheck()
            DeviceSettingsActions.CheckForUpdatesNow -> updateCheckCoordinator.requestManualCheck()
            is DeviceSettingsActions.ChangeAppLanguage -> onChangeAppLanguage(action.language)
            DeviceSettingsActions.OpenApiDomainDialog -> openApiDomainDialog()
            DeviceSettingsActions.CloseApiDomainDialog -> closeApiDomainDialog()
            is DeviceSettingsActions.SaveApiDomain -> saveApiDomain(action.domain)
            DeviceSettingsActions.DetectApiDomain -> detectApiDomain()
            DeviceSettingsActions.ResetApiDomain -> resetApiDomain()
            CommonAction.RetryClicked -> onRetry()
            else -> super.onAction(action)
        }
    }

    override fun dispatchError(error: ErrorEntity) {
        val currentState = stateValue.state
        if (currentState !is DeviceSettingsState.Success) {
            updateViewState(stateValue.copy(state = DeviceSettingsState.Error(error.message)))
        }
        showMessage(error.message)
    }

    private fun onChangeSettingValue(setting: DeviceSettingUIModel.TypeValue) {
        val currentState = stateValue.state
        if (currentState !is DeviceSettingsState.Success) return
        if (currentState.savingToggleType != null) return

        // Optimistic update + show progress
        updateViewState(
            stateValue.copy(
                state = applyToggle(currentState, setting).copy(savingToggleType = setting.type)
            )
        )

        launch {
            try {
                val apiValue = if (setting.value) 1 else 0
                deviceSettingInteractor.updateDeviceSetting(setting.type, apiValue)
                // Clear progress on success
                val successState = stateValue.state
                if (successState is DeviceSettingsState.Success) {
                    updateViewState(stateValue.copy(state = successState.copy(savingToggleType = null)))
                }
            } catch (cancellation: CancellationException) {
                // The screen went away; reverting the toggle here would fight whatever replaced it.
                throw cancellation
            } catch (e: Exception) {
                // Revert on error + clear progress
                val revertedSetting = setting.copy(value = !setting.value)
                val revertState = stateValue.state
                if (revertState is DeviceSettingsState.Success) {
                    updateViewState(
                        stateValue.copy(
                            state = applyToggle(
                                revertState,
                                revertedSetting
                            ).copy(savingToggleType = null)
                        )
                    )
                }
                throw e // re-throw for dispatchError → showMessage
            }
        }
    }

    private fun applyToggle(
        currentState: DeviceSettingsState.Success,
        setting: DeviceSettingUIModel.TypeValue,
    ): DeviceSettingsState.Success {
        val updatedList = currentState.settings.settingsList.map { item ->
            if (item is DeviceSettingUIModel.TypeValue && item.type == setting.type) {
                item.copy(value = setting.value)
            } else {
                item
            }
        }
        return currentState.copy(settings = DeviceSettingsListUi(updatedList))
    }

    private fun onToggleListExpand(setting: DeviceSettingUIModel.TypeList) {
        val currentState = stateValue.state
        if (currentState !is DeviceSettingsState.Success) return

        val newExpanded = if (currentState.expandedType == setting.type) null else setting.type
        updateViewState(
            stateValue.copy(state = currentState.copy(expandedType = newExpanded))
        )
    }

    private fun onSelectOption(type: DeviceSettingType, optionId: Int) {
        val currentState = stateValue.state
        if (currentState !is DeviceSettingsState.Success) return
        if (currentState.savingOptionId != null) return

        updateViewState(
            stateValue.copy(state = currentState.copy(savingOptionId = optionId))
        )

        launch {
            try {
                deviceSettingInteractor.updateDeviceSetting(type, optionId)
                applyListSettingLocally(type, optionId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                clearSavingOption()
                throw error
            }
        }
    }

    private fun clearSavingOption() {
        val currentState = stateValue.state as? DeviceSettingsState.Success ?: return
        updateViewState(stateValue.copy(state = currentState.copy(savingOptionId = null)))
    }

    private fun applyListSettingLocally(type: DeviceSettingType, selectedOptionId: Int) {
        val currentState = stateValue.state
        if (currentState !is DeviceSettingsState.Success) return

        val updatedList = currentState.settings.settingsList.map { item ->
            if (item is DeviceSettingUIModel.TypeList && item.type == type) {
                item.copy(
                    values = item.values.map { option ->
                        option.copy(selected = option.id == selectedOptionId)
                    }
                )
            } else {
                item
            }
        }
        updateViewState(
            stateValue.copy(
                state = currentState.copy(
                    settings = DeviceSettingsListUi(updatedList),
                    expandedType = null,
                    savingOptionId = null,
                )
            )
        )
    }

    private fun toggleSkipPref(update: (DeviceSettingsState.Success) -> DeviceSettingsState.Success) {
        val currentState = stateValue.state
        if (currentState !is DeviceSettingsState.Success) return
        val newState = update(currentState)
        preferencesStore.setSkipPreferences(
            intro = newState.skipIntroEnabled,
            recap = newState.skipRecapEnabled,
            credits = newState.skipCreditsEnabled,
        )
        updateViewState(stateValue.copy(state = newState))
    }

    private fun toggleDebugOverlay() = updatePreference(
        value = { !debugOverlayEnabled },
        persist = preferencesStore::setDebugOverlay,
        update = { copy(debugOverlayEnabled = it) },
    )

    private fun toggleSurroundAudio() = updatePreference(
        value = { !preferSurroundAudio },
        persist = preferencesStore::setPreferSurroundAudio,
        update = { copy(preferSurroundAudio = it) },
    )

    private fun toggleOkTogglesPlayPause() = updatePreference(
        value = { !okTogglesPlayPause },
        persist = preferencesStore::setOkTogglesPlayPause,
        update = { copy(okTogglesPlayPause = it) },
    )

    private fun toggleShowMarkWatchedButton() = updatePreference(
        value = { !showMarkWatchedButton },
        persist = preferencesStore::setShowMarkWatchedButton,
        update = { copy(showMarkWatchedButton = it) },
    )

    private fun toggleWatchedIndicators() = updatePreference(
        value = { !watchedIndicatorsEnabled },
        persist = preferencesStore::setShowWatchedIndicators,
        update = { copy(watchedIndicatorsEnabled = it) },
    )

    private fun toggleAutoTrailer() = updatePreference(
        value = { !autoTrailerEnabled },
        persist = preferencesStore::setAutoTrailer,
        update = { copy(autoTrailerEnabled = it) },
    )

    private fun onChangeAppLanguage(language: AppLanguage) {
        val currentState = stateValue.state
        if (currentState !is DeviceSettingsState.Success) return
        if (currentState.appLanguage == language) return
        preferencesStore.setAppLanguage(language)
        updateViewState(stateValue.copy(state = currentState.copy(appLanguage = language)))
    }

    private fun onChangeNavigationMode(mode: NavigationMode) {
        val currentState = stateValue.state
        if (currentState !is DeviceSettingsState.Success) return
        if (currentState.navigationMode == mode) return
        preferencesStore.setNavigationMode(mode)
        val startupTabOptions = preferencesStore.getStartupTabOptions(mode)
        val startupTab = currentState.startupTab
            .takeIf(startupTabOptions::contains)
            ?: TabType.Home
        if (startupTab != currentState.startupTab) {
            preferencesStore.setStartupTab(startupTab)
        }
        updateViewState(
            stateValue.copy(
                state = currentState.copy(
                    navigationMode = mode,
                    startupTab = startupTab,
                    startupTabOptions = startupTabOptions,
                    menuSections = buildMenuSections(preferencesStore.getVisibleTabs(mode)),
                )
            )
        )
        showMessage(resources.getString(R.string.device_settings_restart_required))
    }

    private fun onChangeStartupTab(tab: TabType) {
        val currentState = stateValue.state
        if (currentState !is DeviceSettingsState.Success) return
        if (tab !in currentState.startupTabOptions || currentState.startupTab == tab) return
        preferencesStore.setStartupTab(tab)
        updateViewState(stateValue.copy(state = currentState.copy(startupTab = tab)))
    }

    /**
     * The startup tab has to remain reachable, so the section carrying it cannot be switched off.
     * The row is read-only to say so, and this guard covers the case where the two disagree.
     */
    private fun onToggleMenuSection(tab: TabType) {
        val currentState = stateValue.state
        if (currentState !is DeviceSettingsState.Success) return
        if (tab == currentState.startupTab) return
        val section = currentState.menuSections.firstOrNull { it.tab == tab } ?: return

        val mode = currentState.navigationMode
        preferencesStore.setTabVisible(mode, tab, visible = !section.visible)
        updateViewState(
            stateValue.copy(
                state = currentState.copy(
                    menuSections = buildMenuSections(preferencesStore.getVisibleTabs(mode)),
                    startupTabOptions = preferencesStore.getStartupTabOptions(mode),
                )
            )
        )
    }

    private fun buildMenuSections(visibleTabs: List<TabType>): List<MenuSectionUi> {
        return SelectableMenuTabs.map { tab ->
            MenuSectionUi(tab = tab, visible = tab in visibleTabs)
        }
    }

    private fun toggleShowAnime() = updatePreference(
        value = { !showAnime },
        persist = preferencesStore::setShowAnime,
        update = { copy(showAnime = it) },
    )

    private fun toggleHideWatched() = updatePreference(
        value = { !hideWatched },
        persist = preferencesStore::setHideWatched,
        update = { copy(hideWatched = it) },
    )

    private fun toggleAutoUpdateCheck() = updatePreference(
        value = { !autoUpdateCheckEnabled },
        persist = preferencesStore::setAutoUpdateCheck,
        update = { copy(autoUpdateCheckEnabled = it) },
    )

    private inline fun <T> updatePreference(
        value: DeviceSettingsState.Success.() -> T,
        persist: (T) -> Unit,
        update: DeviceSettingsState.Success.(T) -> DeviceSettingsState.Success,
    ) {
        val currentState = stateValue.state as? DeviceSettingsState.Success ?: return
        val newValue = currentState.value()
        persist(newValue)
        updateViewState(stateValue.copy(state = currentState.update(newValue)))
    }

    private fun onRetry() {
        loadDeviceSettings()
    }

    private fun openApiDomainDialog() {
        updateViewState(
            stateValue.copy(
                apiDomain = apiDomainInteractor.getState().toDialogState(),
                isApiDomainDialogOpen = true,
            )
        )
    }

    private fun closeApiDomainDialog() {
        updateViewState(stateValue.copy(isApiDomainDialogOpen = false))
    }

    private fun saveApiDomain(domain: String) {
        launch {
            when (val result = apiDomainInteractor.saveCustomDomain(domain)) {
                ApiDomainUpdateResult.Empty -> showMessage(resources.getString(R.string.api_domain_empty))
                ApiDomainUpdateResult.Invalid -> showMessage(resources.getString(R.string.api_domain_invalid))
                is ApiDomainUpdateResult.Success -> updateViewState(
                    stateValue.copy(
                        apiDomain = result.state.toDialogState(),
                        isApiDomainDialogOpen = false,
                    )
                )
            }
        }
    }

    private fun detectApiDomain() {
        if (stateValue.apiDomain.isDetecting) return
        updateViewState(stateValue.copy(apiDomain = stateValue.apiDomain.copy(isDetecting = true)))

        launch {
            when (val result = apiDomainInteractor.detectAndSaveWorkingDomain()) {
                ApiDomainDetectionResult.NotFound -> {
                    updateViewState(stateValue.copy(apiDomain = stateValue.apiDomain.copy(isDetecting = false)))
                    showMessage(resources.getString(R.string.api_domain_detect_failed))
                }

                is ApiDomainDetectionResult.Success -> updateViewState(
                    stateValue.copy(
                        apiDomain = result.state.toDialogState(),
                        isApiDomainDialogOpen = false,
                    )
                )
            }
        }
    }

    private fun resetApiDomain() {
        launch {
            val state = apiDomainInteractor.resetToDefault()
            updateViewState(
                stateValue.copy(
                    apiDomain = state.toDialogState(),
                    isApiDomainDialogOpen = false,
                )
            )
        }
    }

    private fun ApiDomainState.toDialogState(): ApiDomainDialogState {
        return ApiDomainDialogState(
            currentDomain = domain,
            customDomain = customDomain,
        )
    }

}

internal fun buildWatchIndexUiState(
    index: Map<Int, WatchState>,
    progress: WatchStateSyncProgress,
    cursor: WatchStateSyncCursor,
): WatchIndexUiState {
    return WatchIndexUiState(
        fullyWatchedItems = index.values.count { it.isFullyWatched },
        indexedItems = index.size,
        isSyncing = progress.isSyncing,
        currentPage = progress.currentPage,
        totalPages = progress.totalPages,
        // The run in progress knows the freshest count; the cursor holds the last one seen,
        // which is what a section opened between runs has to go on.
        totalHistoryItems = progress.totalHistoryItems ?: cursor.historyTotalItems,
        fullHistoryWalkDone = cursor.fullHistoryWalkDone,
        lastSyncAt = cursor.lastSyncAt,
    )
}
