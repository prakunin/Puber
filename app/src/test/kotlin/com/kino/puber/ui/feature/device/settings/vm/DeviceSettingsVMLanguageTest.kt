package com.kino.puber.ui.feature.device.settings.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.model.AppLanguage
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.data.api.models.DeviceResponse
import com.kino.puber.data.preferences.AppLanguageRepository
import com.kino.puber.data.preferences.ContentPreferences
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import com.kino.puber.domain.interactor.update.IAppUpdateInteractor
import com.kino.puber.domain.interactor.watchstate.WatchStateSyncInteractor
import com.kino.puber.ui.feature.device.settings.mappers.DeviceUiSettingsMapper
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.ui.feature.main.model.TabType
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class DeviceSettingsVMLanguageTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val appLanguageRepository = mockk<AppLanguageRepository>(relaxed = true)
    private var storedLanguage = AppLanguage.System

    @Test
    fun theSectionOpensOnTheLanguageAlreadyStored() {
        storedLanguage = AppLanguage.English

        val vm = createVM()

        assertEquals(AppLanguage.English, vm.successState().appLanguage)
    }

    @Test
    fun choosingALanguageStoresItAndShowsItAsSelected() {
        val vm = createVM()

        vm.onAction(DeviceSettingsActions.ChangeAppLanguage(AppLanguage.Russian))

        verify { appLanguageRepository.setLanguage(AppLanguage.Russian) }
        assertEquals(AppLanguage.Russian, vm.successState().appLanguage)
    }

    @Test
    fun choosingTheLanguageAlreadyInUseChangesNothing() {
        storedLanguage = AppLanguage.Russian
        val vm = createVM()

        vm.onAction(DeviceSettingsActions.ChangeAppLanguage(AppLanguage.Russian))

        verify(exactly = 0) { appLanguageRepository.setLanguage(any()) }
    }

    private fun DeviceSettingsVM.successState(): DeviceSettingsState.Success {
        return testStateValue.state as DeviceSettingsState.Success
    }

    private fun createVM(): DeviceSettingsVM {
        every { appLanguageRepository.getLanguage() } answers { storedLanguage }
        every { appLanguageRepository.setLanguage(any()) } answers {
            storedLanguage = firstArg()
        }
        val deviceSettingInteractor = mockk<IDeviceSettingInteractor>(relaxed = true)
        every { deviceSettingInteractor.getCurrentDeviceSettings() } returns
            flowOf(Result.success(mockk<DeviceResponse>(relaxed = true)))
        val apiDomainInteractor = mockk<ApiDomainInteractor>(relaxed = true)
        every { apiDomainInteractor.getState() } returns ApiDomainState(
            domain = "service-kp.com",
            customDomain = null,
        )
        val navigationPreferencesRepository = mockk<NavigationPreferencesRepository>(relaxed = true)
        every { navigationPreferencesRepository.contentPreferences } returns MutableStateFlow(
            ContentPreferences(showAnime = true, hideWatched = false, showWatchedIndicators = true)
        )
        every { navigationPreferencesRepository.getNavigationMode() } returns NavigationMode.SideDrawer
        every { navigationPreferencesRepository.getStartupTab() } returns TabType.Home
        every { navigationPreferencesRepository.getVisibleTabs(any()) } returns
            listOf(TabType.Home, TabType.Settings)
        every { navigationPreferencesRepository.getStartupTabOptions(any()) } returns
            listOf(TabType.Home)

        val vm = DeviceSettingsVM(
            deviceSettingInteractor = deviceSettingInteractor,
            deviceInfoInteractor = mockk<IDeviceInfoInteractor>(relaxed = true),
            deviceUiSettingsMapper = mockk<DeviceUiSettingsMapper>(relaxed = true),
            preferencesStore = DefaultDeviceSettingsPreferencesStore(
                playerPreferences = mockk<PlayerPreferencesRepository>(relaxed = true),
                navigationPreferences = navigationPreferencesRepository,
                appLanguagePreferences = appLanguageRepository,
                appUpdatePreferences = mockk<IAppUpdateInteractor>(relaxed = true),
            ),
            apiDomainInteractor = apiDomainInteractor,
            watchStateRepository = mockk<WatchStateRepository>(relaxed = true),
            watchStateSyncInteractor = mockk<WatchStateSyncInteractor>(relaxed = true),
            errorHandler = mockk<ErrorHandler>(relaxed = true),
            resources = FakeResourceProvider(),
            router = mockk<AppRouter>(relaxed = true),
        )
        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        return vm
    }
}
