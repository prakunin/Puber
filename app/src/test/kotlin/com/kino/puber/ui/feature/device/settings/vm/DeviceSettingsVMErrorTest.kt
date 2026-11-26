package com.kino.puber.ui.feature.device.settings.vm

import com.kino.puber.R
import com.kino.puber.core.error.ApiError
import com.kino.puber.core.error.DefaultErrorHandler
import com.kino.puber.core.model.AppLanguage
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.data.preferences.AppLanguageRepository
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import com.kino.puber.domain.interactor.update.AppUpdateCheckCoordinator
import com.kino.puber.domain.interactor.update.IAppUpdateInteractor
import com.kino.puber.ui.feature.device.settings.mappers.DeviceUiSettingsMapper
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.FakeSessionLogController
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** A refused session is not a mirror problem, so its error view must not offer the mirror. */
class DeviceSettingsVMErrorTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val deviceSettingInteractor = mockk<IDeviceSettingInteractor>(relaxed = true)
    private val apiDomainInteractor = mockk<ApiDomainInteractor>(relaxed = true)

    @Test
    fun unauthorizedLoadFailure_hidesMirrorAction() {
        every { deviceSettingInteractor.getCurrentDeviceSettings() } returns
            flowOf(Result.failure(ApiError.Unauthorized("user_banned")))
        val vm = createVM()

        vm.testOnStart()

        val state = vm.testStateValue.state as DeviceSettingsState.Error
        assertFalse(state.showMirrorAction)
    }

    @Test
    fun refusalThatKeepsTheSession_keepsMirrorActionAndGenericMessage() {
        every { deviceSettingInteractor.getCurrentDeviceSettings() } returns
            flowOf(Result.failure(ApiError.Unauthorized("deny page", endsSession = false)))
        val vm = createVM()

        vm.testOnStart()

        val state = vm.testStateValue.state as DeviceSettingsState.Error
        assertTrue(state.showMirrorAction)
        assertEquals(FakeResourceProvider().getString(R.string.error_generic), state.error)
    }

    @Test
    fun networkLoadFailure_keepsMirrorAction() {
        every { deviceSettingInteractor.getCurrentDeviceSettings() } returns
            flowOf(Result.failure(ApiError.Network()))
        val vm = createVM()

        vm.testOnStart()

        val state = vm.testStateValue.state as DeviceSettingsState.Error
        assertTrue(state.showMirrorAction)
    }

    private fun createVM(): DeviceSettingsVM {
        every { apiDomainInteractor.getState() } returns ApiDomainState(
            domain = "service-kp.com",
            customDomain = null,
        )
        return DeviceSettingsVM(
            deviceSettingInteractor = deviceSettingInteractor,
            deviceInfoInteractor = mockk<IDeviceInfoInteractor>(relaxed = true),
            deviceUiSettingsMapper = mockk<DeviceUiSettingsMapper>(relaxed = true),
            preferencesStore = DefaultDeviceSettingsPreferencesStore(
                playerPreferences = mockk<PlayerPreferencesRepository>(relaxed = true),
                navigationPreferences = mockk<NavigationPreferencesRepository>(relaxed = true),
                appLanguagePreferences = mockk<AppLanguageRepository> {
                    every { getLanguage() } returns AppLanguage.System
                },
                appUpdatePreferences = mockk<IAppUpdateInteractor>(relaxed = true),
            ),
            apiDomainInteractor = apiDomainInteractor,
            watchStateRepository = mockk(relaxed = true),
            watchStateSyncInteractor = mockk(relaxed = true),
            updateCheckCoordinator = AppUpdateCheckCoordinator(),
            sessionLog = FakeSessionLogController(),
            contentSharer = mockk(relaxed = true),
            errorHandler = DefaultErrorHandler(FakeResourceProvider()),
            resources = FakeResourceProvider(),
            router = mockk<AppRouter>(relaxed = true),
        )
    }
}
