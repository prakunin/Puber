package com.kino.puber.ui.feature.main.vm

import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.House
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.data.preferences.ContentPreferences
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.domain.interactor.watchstate.WatchStateSyncInteractor
import com.kino.puber.ui.feature.main.model.MainAction
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainUIMapper
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class MainVMWatchStateSyncTest {

    companion object {
        private val dispatcher = StandardTestDispatcher()

        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension(dispatcher)
    }

    /**
     * The first walk of an unindexed account spends hundreds of history requests, and OkHttp allows
     * five in flight per host. Started here, it takes those slots from the requests the home screen
     * needs to draw its first frame.
     */
    @Test
    fun onStart_doesNotSyncWatchStateWhileTheStartupRequestsAreStillOut() = runTest(dispatcher) {
        val sync = mockk<WatchStateSyncInteractor>(relaxed = true)
        val vm = vm(sync)

        vm.testOnStart()
        runCurrent()

        coVerify(exactly = 0) { sync.syncIfStale(any()) }
        vm.testCancelScope()
    }

    @Test
    fun onStart_syncsWatchStateOnceTheStartupBurstHasPassed() = runTest(dispatcher) {
        val sync = mockk<WatchStateSyncInteractor>(relaxed = true)
        val vm = vm(sync)

        vm.testOnStart()
        advanceTimeBy(MainVM.StartupSyncDelay.inWholeMilliseconds + 1)
        runCurrent()

        coVerify(exactly = 1) { sync.syncIfStale(any()) }
        vm.testCancelScope()
    }

    /**
     * The first ON_RESUME arrives during the composition that starts this screen, so it is part of
     * the cold start rather than a return to it. Letting it sync straight away is what used to make
     * the startup wait meaningless: the resume run went out into the middle of the home screen's
     * burst, and the delayed run then found the index already claimed and did nothing.
     */
    @Test
    fun resumed_doesNotSyncWatchStateWhileTheStartupRequestsAreStillOut() = runTest(dispatcher) {
        val sync = mockk<WatchStateSyncInteractor>(relaxed = true)
        val vm = vm(sync)

        vm.testOnStart()
        vm.onAction(MainAction.Resumed)
        runCurrent()

        coVerify(exactly = 0) { sync.syncIfStale(any()) }
        vm.testCancelScope()
    }

    /**
     * Both triggers coincide on a cold start, and between them they are owed one run — not one
     * each. The screen starting and the first resume are the same arrival.
     */
    @Test
    fun startupAndFirstResume_syncWatchStateOnceBetweenThem() = runTest(dispatcher) {
        val sync = mockk<WatchStateSyncInteractor>(relaxed = true)
        val vm = vm(sync)

        vm.testOnStart()
        vm.onAction(MainAction.Resumed)
        advanceTimeBy(MainVM.StartupSyncDelay.inWholeMilliseconds + 1)
        runCurrent()

        coVerify(exactly = 1) { sync.syncIfStale(any()) }
        vm.testCancelScope()
    }

    /** Once the startup burst is behind us, a return to the screen syncs without waiting again. */
    @Test
    fun resumed_syncsWatchStateWithoutWaitingOnceTheStartupBurstHasPassed() = runTest(dispatcher) {
        val sync = mockk<WatchStateSyncInteractor>(relaxed = true)
        val vm = vm(sync)

        vm.testOnStart()
        advanceTimeBy(MainVM.StartupSyncDelay.inWholeMilliseconds + 1)
        runCurrent()

        vm.onAction(MainAction.Resumed)
        runCurrent()

        coVerify(exactly = 2) { sync.syncIfStale(any()) }
        vm.testCancelScope()
    }

    private fun vm(watchStateSyncInteractor: WatchStateSyncInteractor): MainVM {
        val screens = mockk<Screens>(relaxed = true)
        val router = mockk<AppRouter>(relaxed = true)
        every { router.screens } returns screens
        val mapper = mockk<MainUIMapper>()
        val state = MainViewState(
            tabs = listOf(
                MainTab(
                    type = TabType.Home,
                    icon = PhosphorIcons.Duotone.House,
                    isSelected = true,
                )
            ),
            selectedTab = TabType.Home,
        )
        every { mapper.buildViewState(any()) } returns state
        every { mapper.buildTabContent(any(), any()) } answers {
            val screen = mockk<PuberScreen>()
            every { screen.key } returns "HomeScreen"
            PuberTab(screen = screen, tag = firstArg(), instanceKey = "")
        }

        val preferences = mockk<NavigationPreferencesRepository>(relaxed = true)
        every { preferences.contentPreferences } returns MutableStateFlow(
            ContentPreferences(
                showAnime = true,
                hideWatched = false,
                showWatchedIndicators = true,
            )
        )

        val deviceInfoInteractor = mockk<IDeviceInfoInteractor>()
        every { deviceInfoInteractor.setDeviceInformation() } returns flowOf(Unit)

        return MainVM(
            router = router,
            mainUIMapper = mapper,
            tabRouter = mockk<TabRouter>(relaxed = true),
            navigationPreferencesRepository = preferences,
            deviceInfoInteractor = deviceInfoInteractor,
            watchStateSyncInteractor = watchStateSyncInteractor,
        )
    }
}
