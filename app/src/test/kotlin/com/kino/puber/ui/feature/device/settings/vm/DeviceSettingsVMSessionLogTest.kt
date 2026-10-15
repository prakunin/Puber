package com.kino.puber.ui.feature.device.settings.vm

import com.kino.puber.R
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.system.ContentSharer
import com.kino.puber.core.telemetry.SessionLogState
import com.kino.puber.core.telemetry.ShareResult
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.CommonAction
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
import com.kino.puber.domain.interactor.update.AppUpdateCheckCoordinator
import com.kino.puber.domain.interactor.update.IAppUpdateInteractor
import com.kino.puber.domain.interactor.watchstate.WatchStateSyncInteractor
import com.kino.puber.domain.model.TabType
import com.kino.puber.ui.feature.device.settings.mappers.DeviceUiSettingsMapper
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.ui.feature.device.settings.model.SessionLogUi
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.FakeSessionLogController
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File

internal class DeviceSettingsVMSessionLogTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val resources = FakeResourceProvider()
    private val controller = FakeSessionLogController()
    private val sharer = mockk<ContentSharer>(relaxed = true)
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun str(id: Int) = resources.getString(id)

    private fun DeviceSettingsVM.sessionLog(): SessionLogUi =
        (testStateValue.state as DeviceSettingsState.Success).sessionLog

    private fun DeviceSettingsVM.message(): String? =
        testMessageValue?.message

    private fun idle() = mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun offStateShowsToggleOffAndOffDescription() {
        val vm = createVM()

        val ui = vm.sessionLog()
        assertFalse(ui.enabled)
        assertEquals(str(R.string.settings_session_log_off), ui.description)
        assertEquals(1, controller.refreshCalls)
    }

    @Test
    fun recordingStateShowsIndicatorWithDaysAndHoursLeft() {
        val vm = createVM()

        controller.mutableState.value = SessionLogState.Recording(6 * day + 23 * hour, hasFiles = false)
        assertTrue(vm.sessionLog().enabled)
        assertEquals(resources.getString(R.string.settings_session_log_recording, 6, 23), vm.sessionLog().description)

        controller.mutableState.value = SessionLogState.Recording(7 * day, hasFiles = false)
        assertEquals(resources.getString(R.string.settings_session_log_recording, 7, 0), vm.sessionLog().description)

        controller.mutableState.value = SessionLogState.Recording(0, hasFiles = false)
        assertEquals(str(R.string.settings_session_log_recording_under_hour), vm.sessionLog().description)

        controller.mutableState.value = SessionLogState.Unavailable
        assertFalse(vm.sessionLog().enabled)
        assertEquals(str(R.string.settings_session_log_unavailable), vm.sessionLog().description)
    }

    @Test
    fun toggleActionCallsSetEnabledAndRefreshesState() {
        val vm = createVM()

        vm.onAction(DeviceSettingsActions.ToggleSessionLog)
        assertEquals(listOf(true), controller.setEnabledCalls)
        assertTrue(vm.sessionLog().enabled)

        vm.onAction(DeviceSettingsActions.ToggleSessionLog)
        assertEquals(listOf(true, false), controller.setEnabledCalls)
        assertFalse(vm.sessionLog().enabled)
    }

    @Test
    fun tappingWhileStorageIsUnavailableTurnsTheLogOffForGood() {
        controller.mutableState.value = SessionLogState.Unavailable
        val vm = createVM()

        vm.onAction(DeviceSettingsActions.ToggleSessionLog)

        assertEquals(listOf(false), controller.setEnabledCalls)
        assertFalse(vm.sessionLog().enabled)

        vm.onAction(DeviceSettingsActions.ToggleSessionLog)
        assertEquals(listOf(false, true), controller.setEnabledCalls)
    }

    @Test
    fun shareRowDisabledWhenNoFiles() {
        val vm = createVM()

        assertFalse(vm.sessionLog().shareEnabled)
        assertEquals(str(R.string.settings_share_log_empty), vm.sessionLog().shareSubtitle)

        vm.onAction(DeviceSettingsActions.ShareSessionLog)

        assertEquals(str(R.string.settings_share_log_empty), vm.message())
        assertEquals(0, controller.shareCalls)
        verify(exactly = 0) { sharer.shareFile(any(), any(), any()) }
    }

    @Test
    fun shareResultsMapToMessagesAndSharingFlag() {
        controller.mutableState.value = SessionLogState.Off(hasFiles = true)
        val archive = File("log.zip")
        every { sharer.shareFile(archive, "application/zip", str(R.string.settings_share_log_chooser)) } returns true
        val vm = createVM()
        assertTrue(vm.sessionLog().shareEnabled)
        assertEquals(str(R.string.settings_share_log_subtitle), vm.sessionLog().shareSubtitle)

        // Opened: no message, and the focus is asked back after the chooser closes.
        controller.shareResult = ShareResult.Ready(archive)
        val gate = CompletableDeferred<Unit>()
        controller.shareGate = gate
        vm.onAction(DeviceSettingsActions.ShareSessionLog)
        assertTrue(vm.sessionLog().sharing)
        assertEquals(str(R.string.settings_share_log_preparing), vm.sessionLog().shareSubtitle)
        gate.complete(Unit)
        idle()
        assertFalse(vm.sessionLog().sharing)
        assertNull(vm.message())
        assertTrue(vm.testStateValue.restoreShareFocus)
        vm.onAction(DeviceSettingsActions.SessionLogShareFocusRestored)
        assertFalse(vm.testStateValue.restoreShareFocus)

        // The system refused to open a chooser.
        every { sharer.shareFile(archive, any(), any()) } returns false
        controller.shareGate = null
        vm.onAction(DeviceSettingsActions.ShareSessionLog)
        idle()
        assertEquals(str(R.string.settings_share_log_failed), vm.message())
        assertFalse(vm.testStateValue.restoreShareFocus)

        vm.onAction(CommonAction.SnackBarDismissed)
        assertNull(vm.message())
        controller.shareResult = ShareResult.Failed
        vm.onAction(DeviceSettingsActions.ShareSessionLog)
        idle()
        assertEquals(str(R.string.settings_share_log_failed), vm.message())

        vm.onAction(CommonAction.SnackBarDismissed)
        assertNull(vm.message())
        controller.shareResult = ShareResult.NothingToShare
        vm.onAction(DeviceSettingsActions.ShareSessionLog)
        idle()
        assertEquals(str(R.string.settings_share_log_empty), vm.message())
        assertFalse(vm.sessionLog().sharing)
    }

    @Test
    fun aSecondShareWhileOneIsInFlightIsIgnored() {
        controller.mutableState.value = SessionLogState.Off(hasFiles = true)
        val gate = CompletableDeferred<Unit>()
        controller.shareGate = gate
        val vm = createVM()

        vm.onAction(DeviceSettingsActions.ShareSessionLog)
        vm.onAction(DeviceSettingsActions.ShareSessionLog)
        gate.complete(Unit)
        idle()

        assertEquals(1, controller.shareCalls)
    }

    private fun createVM(): DeviceSettingsVM {
        val navigationPreferences = mockk<NavigationPreferencesRepository>(relaxed = true)
        every { navigationPreferences.getAutoTrailerEnabled() } returns true
        every { navigationPreferences.contentPreferences } returns MutableStateFlow(
            ContentPreferences(showAnime = true, hideWatched = false, showWatchedIndicators = true),
        )
        every { navigationPreferences.getStartupTab() } returns TabType.Home
        every { navigationPreferences.getVisibleTabs() } returns listOf(TabType.Home, TabType.Settings)
        every { navigationPreferences.getStartupTabOptions() } returns listOf(TabType.Home)

        val deviceSettingInteractor = mockk<IDeviceSettingInteractor>(relaxed = true)
        every { deviceSettingInteractor.getCurrentDeviceSettings() } returns
            flowOf(Result.success(mockk<DeviceResponse>(relaxed = true)))
        val apiDomainInteractor = mockk<ApiDomainInteractor>(relaxed = true)
        every { apiDomainInteractor.getState() } returns ApiDomainState(domain = "service-kp.com", customDomain = null)

        val vm = DeviceSettingsVM(
            deviceSettingInteractor = deviceSettingInteractor,
            deviceInfoInteractor = mockk<IDeviceInfoInteractor>(relaxed = true),
            deviceUiSettingsMapper = mockk<DeviceUiSettingsMapper>(relaxed = true),
            preferencesStore = DefaultDeviceSettingsPreferencesStore(
                playerPreferences = mockk<PlayerPreferencesRepository>(relaxed = true),
                navigationPreferences = navigationPreferences,
                appLanguagePreferences = mockk<AppLanguageRepository>(relaxed = true),
                appUpdatePreferences = mockk<IAppUpdateInteractor>(relaxed = true),
            ),
            apiDomainInteractor = apiDomainInteractor,
            watchStateRepository = mockk<WatchStateRepository>(relaxed = true),
            watchStateSyncInteractor = mockk<WatchStateSyncInteractor>(relaxed = true),
            updateCheckCoordinator = AppUpdateCheckCoordinator(),
            sessionLog = controller,
            contentSharer = sharer,
            errorHandler = mockk<ErrorHandler>(relaxed = true),
            resources = resources,
            router = mockk<AppRouter>(relaxed = true),
        )
        vm.testOnStart()
        idle()
        return vm
    }
}
