package com.kino.puber.ui.feature.main.vm

import com.kino.puber.core.coroutine.runCatchingCancellable
import com.kino.puber.core.logger.log
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.navigation.component.TabAppRouterHolder
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.tvhome.TvHomeSyncCoordinator
import com.kino.puber.data.preferences.ContentPreferences
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.domain.interactor.watchstate.WatchStateSyncInteractor
import com.kino.puber.ui.feature.main.model.MainAction
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainUIMapper
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlin.time.Duration.Companion.seconds

internal class MainVM(
    router: AppRouter,
    private val mainUIMapper: MainUIMapper,
    internal val tabRouter: TabRouter,
    private val navigationPreferencesRepository: NavigationPreferencesRepository,
    private val deviceInfoInteractor: IDeviceInfoInteractor,
    private val watchStateSyncInteractor: WatchStateSyncInteractor,
    private val tvHomeSyncCoordinator: TvHomeSyncCoordinator? = null,
) : PuberVM<MainViewState>(router) {
    override val initialViewState = MainViewState()
    internal val tabAppRouterHolder = TabAppRouterHolder(router.screens)
    private val tabRefreshVersions = mutableMapOf<TabType, Int>()
    private var observedContentPreferences: ContentPreferences? = null

    /** The one watch-state sync this screen will have in flight; see [syncWatchState]. */
    private var watchStateSyncJob: Job? = null

    /** Whether the startup wait has already been served, so only the first run pays it. */
    private var startupSyncDelayServed = false

    override fun onStart() {
        val state = mainUIMapper.buildViewState()
        observedContentPreferences = navigationPreferencesRepository.contentPreferences.value
        updateViewState(state)
        tabRouter.openTab(buildTabContent(state.selectedTab))
        reportDeviceInformation()
        syncWatchState()
        tvHomeSyncCoordinator?.requestRefresh(immediate = true)
        launch {
            navigationPreferencesRepository.contentPreferences.collect(::onContentPreferencesChanged)
        }
        launch {
            navigationPreferencesRepository.menuTabsChanges.collect { onMenuTabsChanged() }
        }
    }

    /**
     * The catalogue itself reports nothing about what has been watched, so the local index is
     * refreshed once the main screen is up — that is the first point where the session is known to
     * be authenticated.
     *
     * Both triggers — this screen starting and the TV coming back to it — run through here, and one
     * job serves them together. They are not alternatives that happen to overlap: the first
     * ON_RESUME arrives during the very composition that starts this screen, so a startup run and a
     * resume run always coincide on a cold start. Kept apart, the resume one has nothing to wait for
     * and wins, which left [StartupSyncDelay] governing only a run that then found the index
     * already claimed and did nothing — the wait was, in effect, never served.
     *
     * So the wait belongs to the first run rather than to the trigger that asked for it, and a
     * trigger arriving while a run is in flight is dropped: the run under way is already the one it
     * would have started, and whether a later one is due at all is the interactor's decision.
     */
    private fun syncWatchState() {
        if (watchStateSyncJob?.isActive == true) return
        watchStateSyncJob = launch {
            if (!startupSyncDelayServed) {
                delay(StartupSyncDelay)
                startupSyncDelayServed = true
            }
            runCatchingCancellable { watchStateSyncInteractor.syncIfStale() }
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
        tabRouter.openTab(buildTabContent(item.type))
    }

    private fun onTabRefresh(item: MainTab) {
        tabRefreshVersions[item.type] = (tabRefreshVersions[item.type] ?: 0) + 1
        val refreshedTab = buildTabContent(item.type)
        updateViewState<MainViewState> {
            mainUIMapper.updateSelectedTab(state = this, item)
        }
        tabRouter.openTab(refreshedTab)
    }

    private fun buildTabContent(type: TabType) = mainUIMapper.buildTabContent(
        type = type,
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
            tabRouter.openTab(buildTabContent(updatedState.selectedTab))
        }
    }

    /**
     * A section was added to or removed from the menu. The tabs themselves are untouched, so only
     * the menu is rebuilt — and the tab is reopened just when the one on screen has gone away and
     * the mapper picked a different one.
     */
    private fun onMenuTabsChanged() {
        val previousState = stateValue
        val updatedState = mainUIMapper.buildViewState(previousState.selectedTab)
        updateViewState(updatedState)
        if (updatedState.selectedTab != previousState.selectedTab) {
            tabRouter.openTab(buildTabContent(updatedState.selectedTab))
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
    }

    internal companion object {
        /**
         * How long the startup watch-state sync waits before it starts.
         *
         * A heuristic, not a handshake: nothing here can observe the home screen's first frame, and
         * the point is only to keep the history walk out of the way while the screen's own requests
         * are competing for OkHttp's five-per-host budget. Overshooting costs a slightly later
         * index; undershooting costs a slower first frame, which the user actually sees.
         */
        val StartupSyncDelay = 5.seconds

        val ANIME_FILTERED_TABS = setOf(
            TabType.Home,
            TabType.Movies,
            TabType.Series,
            TabType.Cartoons,
        )
    }
}
