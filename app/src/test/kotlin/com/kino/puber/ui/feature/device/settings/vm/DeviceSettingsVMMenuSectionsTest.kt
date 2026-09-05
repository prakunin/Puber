package com.kino.puber.ui.feature.device.settings.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.model.AppLanguage
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.data.preferences.AppLanguageRepository
import com.kino.puber.data.api.models.DeviceResponse
import com.kino.puber.data.preferences.ContentPreferences
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import com.kino.puber.domain.interactor.update.AppUpdateCheckCoordinator
import com.kino.puber.domain.interactor.update.IAppUpdateInteractor
import com.kino.puber.domain.interactor.watchstate.WatchStateSyncInteractor
import com.kino.puber.ui.feature.device.settings.mappers.DeviceUiSettingsMapper
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.domain.model.TabType
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class DeviceSettingsVMMenuSectionsTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val navigationPreferencesRepository = mockk<NavigationPreferencesRepository>(relaxed = true)
    private var visibleTabs = listOf(TabType.Home, TabType.Movies, TabType.Concerts, TabType.Settings)

    @Test
    fun menuSections_reportEverySelectableTabAgainstTheVisibleOnes() {
        val vm = createVM()

        val sections = vm.successState().menuSections

        assertEquals(
            listOf(
                TabType.Favourites,
                TabType.Bookmarks,
                TabType.History,
                TabType.Movies,
                TabType.Series,
                TabType.Cartoons,
                TabType.Anime,
                TabType.For4k,
                TabType.Concerts,
                TabType.DocMovies,
                TabType.DocSeries,
                TabType.TvShows,
                TabType.Collections,
            ),
            sections.map { it.tab },
        )
        assertTrue(sections.single { it.tab == TabType.Movies }.visible)
        assertFalse(sections.single { it.tab == TabType.Series }.visible)
    }

    @Test
    fun togglingASection_writesItAndRefreshesTheStartupOptions() {
        val vm = createVM()

        vm.onAction(DeviceSettingsActions.ToggleMenuSection(TabType.Concerts))

        verify {
            navigationPreferencesRepository.setTabVisible(
                tab = TabType.Concerts,
                visible = false,
            )
        }
        assertFalse(vm.successState().menuSections.single { it.tab == TabType.Concerts }.visible)
        assertFalse(TabType.Concerts in vm.successState().startupTabOptions)
    }

    @Test
    fun theStartupTabsSection_cannotBeSwitchedOff() {
        val vm = createVM(startupTab = TabType.Movies)

        vm.onAction(DeviceSettingsActions.ToggleMenuSection(TabType.Movies))

        verify(exactly = 0) {
            navigationPreferencesRepository.setTabVisible(any(), any())
        }
        assertTrue(vm.successState().menuSections.single { it.tab == TabType.Movies }.visible)
    }

    private fun DeviceSettingsVM.successState(): DeviceSettingsState.Success {
        return testStateValue.state as DeviceSettingsState.Success
    }

    private fun createVM(startupTab: TabType = TabType.Home): DeviceSettingsVM {
        val deviceSettingInteractor = mockk<IDeviceSettingInteractor>(relaxed = true)
        every { deviceSettingInteractor.getCurrentDeviceSettings() } returns
            flowOf(Result.success(mockk<DeviceResponse>(relaxed = true)))
        val apiDomainInteractor = mockk<ApiDomainInteractor>(relaxed = true)
        every { apiDomainInteractor.getState() } returns ApiDomainState(
            domain = "service-kp.com",
            customDomain = null,
        )
        every { navigationPreferencesRepository.contentPreferences } returns MutableStateFlow(
            ContentPreferences(showAnime = true, hideWatched = false, showWatchedIndicators = true)
        )
        every { navigationPreferencesRepository.getStartupTab() } returns startupTab
        every { navigationPreferencesRepository.getVisibleTabs() } answers { visibleTabs }
        every { navigationPreferencesRepository.getStartupTabOptions() } answers {
            visibleTabs.filterNot { it == TabType.Search || it == TabType.Settings }
        }
        every {
            navigationPreferencesRepository.setTabVisible(any(), any())
        } answers {
            val tab = firstArg<TabType>()
            visibleTabs = if (secondArg<Boolean>()) visibleTabs + tab else visibleTabs - tab
        }

        val vm = DeviceSettingsVM(
            deviceSettingInteractor = deviceSettingInteractor,
            deviceInfoInteractor = mockk<IDeviceInfoInteractor>(relaxed = true),
            deviceUiSettingsMapper = mockk<DeviceUiSettingsMapper>(relaxed = true),
            preferencesStore = DefaultDeviceSettingsPreferencesStore(
                playerPreferences = mockk<PlayerPreferencesRepository>(relaxed = true),
                navigationPreferences = navigationPreferencesRepository,
                appLanguagePreferences = mockk<AppLanguageRepository> {
                    every { getLanguage() } returns AppLanguage.System
                },
                appUpdatePreferences = mockk<IAppUpdateInteractor>(relaxed = true),
            ),
            apiDomainInteractor = apiDomainInteractor,
            watchStateRepository = mockk<WatchStateRepository>(relaxed = true),
            watchStateSyncInteractor = mockk<WatchStateSyncInteractor>(relaxed = true),
            updateCheckCoordinator = AppUpdateCheckCoordinator(),
            errorHandler = mockk<ErrorHandler>(relaxed = true),
            resources = FakeResourceProvider(),
            router = mockk<AppRouter>(relaxed = true),
        )
        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        return vm
    }
}
