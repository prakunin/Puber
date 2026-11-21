package com.kino.puber.ui.feature.main.vm

import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.House
import com.kino.puber.core.startup.StartupSettled
import com.kino.puber.core.startup.settledStartup
import com.kino.puber.core.startup.unsettledStartup
import com.kino.puber.core.tvhome.TvHomeSyncCoordinator
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.data.preferences.ContentPreferences
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.domain.interactor.watchstate.WatchStateSyncInteractor
import com.kino.puber.domain.model.TabType
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainUIMapper
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class MainVMStartupSlotsTest {

    companion object {
        private val dispatcher = StandardTestDispatcher()

        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension(dispatcher)
    }

    private val coordinator = mockk<TvHomeSyncCoordinator>(relaxed = true)
    private val deviceInfo = mockk<IDeviceInfoInteractor>()
    private val sync = mockk<WatchStateSyncInteractor>(relaxed = true)

    @Test
    fun onStart_runsDeferredWorkInSlotOrderAfterStartupSettles() = runTest(dispatcher) {
        val vm = vm(settledStartup(CoroutineScope(dispatcher)))

        vm.testOnStart()

        assertSlotsFromNow()
        vm.testCancelScope()
    }

    @Test
    fun deferredStartupWorkNeverRunsAfterTheViewModelIsCleared() = runTest(dispatcher) {
        val startup = unsettledStartup(CoroutineScope(dispatcher))
        val vm = vm(startup)
        vm.testOnStart()
        runCurrent()

        vm.testCancelScope()
        startup.onSplashGone()
        advanceTimeBy(10_000)
        runCurrent()

        verify(exactly = 0) { coordinator.requestRefresh(any()) }
        verify(exactly = 0) { deviceInfo.setDeviceInformation() }
        coVerify(exactly = 0) { sync.syncIfStale(any()) }
    }

    @Test
    fun onStartAfterStartupSettled_countsSlotsFromMainStart() = runTest(dispatcher) {
        val vm = vm(settledStartup(CoroutineScope(dispatcher)))
        advanceTimeBy(10_000)
        runCurrent()

        vm.testOnStart()

        assertSlotsFromNow()
        vm.testCancelScope()
    }

    /** Offsets are counted from the call, and each call is absent one millisecond before its offset. */
    private fun TestScope.assertSlotsFromNow() {
        runCurrent()
        advanceTimeBy(499)
        runCurrent()
        verify(exactly = 0) { coordinator.requestRefresh(any()) }
        advanceTimeBy(1)
        runCurrent()
        verify(exactly = 1) { coordinator.requestRefresh(true) }
        verify(exactly = 0) { deviceInfo.setDeviceInformation() }

        advanceTimeBy(999)
        runCurrent()
        verify(exactly = 0) { deviceInfo.setDeviceInformation() }
        advanceTimeBy(1)
        runCurrent()
        verify(exactly = 1) { deviceInfo.setDeviceInformation() }
        coVerify(exactly = 0) { sync.syncIfStale(any()) }

        advanceTimeBy(499)
        runCurrent()
        coVerify(exactly = 0) { sync.syncIfStale(any()) }
        advanceTimeBy(1)
        runCurrent()
        coVerify(exactly = 1) { sync.syncIfStale(any()) }
    }

    private fun vm(startupSettled: StartupSettled): MainVM {
        val router = mockk<AppRouter>(relaxed = true)
        every { router.screens } returns mockk<Screens>(relaxed = true)
        val mapper = mockk<MainUIMapper>()
        every { mapper.buildViewState(any()) } returns MainViewState(
            tabs = listOf(MainTab(type = TabType.Home, icon = PhosphorIcons.Duotone.House, isSelected = true)),
            selectedTab = TabType.Home,
        )
        every { mapper.buildTabContent(any(), any()) } answers {
            val screen = mockk<PuberScreen>()
            every { screen.key } returns "HomeScreen"
            PuberTab(screen = screen, tag = firstArg(), instanceKey = "")
        }
        val preferences = mockk<NavigationPreferencesRepository>(relaxed = true)
        every { preferences.contentPreferences } returns MutableStateFlow(
            ContentPreferences(showAnime = true, hideWatched = false, showWatchedIndicators = true)
        )
        every { deviceInfo.setDeviceInformation() } returns flowOf(Unit)
        return MainVM(
            router = router,
            mainUIMapper = mapper,
            tabRouter = mockk<TabRouter>(relaxed = true),
            navigationPreferencesRepository = preferences,
            deviceInfoInteractor = deviceInfo,
            watchStateSyncInteractor = sync,
            startupSettled = startupSettled,
            tvHomeSyncCoordinator = coordinator,
        )
    }
}
