package com.kino.puber.ui.feature.main.vm

import com.kino.puber.core.logger.log
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.navigation.component.TabAppRouterHolder
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.preferences.ContentPreferences
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.domain.interactor.watchstate.WatchStateSyncInteractor
import com.kino.puber.ui.feature.main.model.MainAction
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainUIMapper
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect

internal class MainVM(
    router: AppRouter,
    private val mainUIMapper: MainUIMapper,
    internal val tabRouter: TabRouter,
    private val navigationPreferencesRepository: NavigationPreferencesRepository,
    private val deviceInfoInteractor: IDeviceInfoInteractor,
    private val watchStateSyncInteractor: WatchStateSyncInteractor,
) : PuberVM<MainViewState>(router) {
    override val initialViewState = MainViewState()
    internal val tabAppRouterHolder = TabAppRouterHolder(router.screens)
    private val tabRefreshVersions = mutableMapOf<TabType, Int>()
    private var observedContentPreferences: ContentPreferences? = null

    override fun onStart() {
        val state = mainUIMapper.buildViewState()
        observedContentPreferences = navigationPreferencesRepository.contentPreferences.value
        updateViewState(state)
        tabRouter.openTab(buildTabContent(state.selectedTab, state.navigationMode))
        reportDeviceInformation()
        syncWatchState()
        launch {
            navigationPreferencesRepository.contentPreferences.collect(::onContentPreferencesChanged)
        }
    }

    /**
     * The catalogue itself reports nothing about what has been watched, so the local index is
     * refreshed once the main screen is up — that is the first point where the session is known to
     * be authenticated.
     */
    private fun syncWatchState() {
        launch {
            runCatching { watchStateSyncInteractor.syncIfStale() }
                .onFailure { error -> log(error, "Failed to sync watch state") }
        }
    }

    /**
     * Keeps the KinoPub device record in sync for sessions that skip the auth screen,
     * otherwise a device linked once stays "unknown" forever.
     */
    private fun reportDeviceInformation() {
        launch {
            deviceInfoInteractor.setDeviceInformation()
                .catch { error -> log(error, "Failed to report device information") }
                .collect()
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.ItemSelected<*> -> onTabSelected(action.item as MainTab)
            is MainAction.RefreshTab -> onTabRefresh(action.tab)
            MainAction.Resumed -> syncWatchState()
            else -> super.onAction(action)
        }
    }

    private fun onTabSelected(item: MainTab) {
        updateViewState<MainViewState> {
            mainUIMapper.updateSelectedTab(state = this, item)
        }
        tabRouter.openTab(buildTabContent(item.type, stateValue.navigationMode))
    }

    private fun onTabRefresh(item: MainTab) {
        tabRefreshVersions[item.type] = (tabRefreshVersions[item.type] ?: 0) + 1
        val refreshedTab = buildTabContent(item.type, stateValue.navigationMode)
        updateViewState<MainViewState> {
            mainUIMapper.updateSelectedTab(state = this, item)
        }
        tabRouter.openTab(refreshedTab)
    }

    private fun buildTabContent(
        type: TabType,
        navigationMode: NavigationMode,
    ) = mainUIMapper.buildTabContent(
        type = type,
        navigationMode = navigationMode,
        refreshVersion = tabRefreshVersions[type] ?: 0,
    )

    private fun onContentPreferencesChanged(preferences: ContentPreferences) {
        val previousPreferences = observedContentPreferences
        if (preferences == previousPreferences) return
        observedContentPreferences = preferences

        val previousState = stateValue
        val updatedState = mainUIMapper.buildViewState(previousState.selectedTab)
        val showAnimeChanged = previousPreferences?.showAnime != preferences.showAnime
        if (showAnimeChanged) {
            ANIME_FILTERED_TABS.forEach { tab ->
                tabRefreshVersions[tab] = (tabRefreshVersions[tab] ?: 0) + 1
            }
        }
        updateViewState(updatedState)

        val selectedTabChanged = updatedState.selectedTab != previousState.selectedTab
        val selectedTabNeedsRefresh = showAnimeChanged && updatedState.selectedTab in ANIME_FILTERED_TABS
        if (selectedTabChanged || selectedTabNeedsRefresh) {
            tabRouter.openTab(buildTabContent(updatedState.selectedTab, updatedState.navigationMode))
        }
    }

    fun onSearchClick() {
        router.navigateTo(router.screens.search())
    }

    fun onSettingsClick() {
        router.navigateTo(router.screens.deviceSettings())
    }

    override fun onCleared() {
        tabAppRouterHolder.dispose()
        super.onCleared()
    }

    private companion object {
        val ANIME_FILTERED_TABS = setOf(
            TabType.Home,
            TabType.Movies,
            TabType.Series,
            TabType.Cartoons,
        )
    }
}
