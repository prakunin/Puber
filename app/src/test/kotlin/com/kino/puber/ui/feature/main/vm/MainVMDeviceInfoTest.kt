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
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainUIMapper
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class MainVMDeviceInfoTest {

    companion object {
        private val dispatcher = StandardTestDispatcher()

        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension(dispatcher)
    }

    @Test
    fun onStart_reportsDeviceInformationForAlreadyLinkedSession() = runTest(dispatcher) {
        val deviceInfoInteractor = mockk<IDeviceInfoInteractor>()
        every { deviceInfoInteractor.setDeviceInformation() } returns flowOf(Unit)
        val vm = vm(deviceInfoInteractor)

        vm.testOnStart()
        runCurrent()

        verify(exactly = 1) { deviceInfoInteractor.setDeviceInformation() }
        vm.testCancelScope()
    }

    @Test
    fun onStart_keepsMainScreenUsableWhenDeviceReportFails() = runTest(dispatcher) {
        val deviceInfoInteractor = mockk<IDeviceInfoInteractor>()
        every { deviceInfoInteractor.setDeviceInformation() } returns flow {
            throw IllegalStateException("notify failed")
        }
        val vm = vm(deviceInfoInteractor)

        vm.testOnStart()
        runCurrent()

        assertEquals(TabType.Home, vm.testStateValue.selectedTab)
        assertEquals(null, vm.testMessageValue)
        vm.testCancelScope()
    }

    private fun vm(deviceInfoInteractor: IDeviceInfoInteractor): MainVM {
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

        return MainVM(
            router = router,
            mainUIMapper = mapper,
            tabRouter = mockk<TabRouter>(relaxed = true),
            navigationPreferencesRepository = preferences,
            deviceInfoInteractor = deviceInfoInteractor,
            watchStateSyncInteractor = mockk(relaxed = true),
        )
    }
}
