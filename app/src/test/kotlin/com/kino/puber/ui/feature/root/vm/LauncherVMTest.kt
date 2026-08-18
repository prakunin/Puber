package com.kino.puber.ui.feature.root.vm

import com.kino.puber.core.contentlink.ContentLaunchCoordinator
import com.kino.puber.core.contentlink.ContentUriCodec
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.ui.feature.root.model.LauncherAction
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class LauncherVMTest {

    companion object {
        private val dispatcher = StandardTestDispatcher()

        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension(dispatcher)
    }

    @Test
    fun start_keepsSplashOnScreenUntilItsAnimationHasPlayed() = runTest(dispatcher) {
        val router = router()
        val vm = LauncherVM(router, preferences(accessToken = "token"), coordinator())

        vm.testOnStart()

        verify(exactly = 0) { router.newRootScreen(*anyVararg()) }
        vm.testCancelScope()
    }

    @Test
    fun splashShown_opensMainScreenForAuthenticatedSession() = runTest(dispatcher) {
        val router = router()
        val mainScreen = mockk<PuberScreen>()
        every { router.screens.main() } returns mainScreen
        val vm = LauncherVM(router, preferences(accessToken = "token"), coordinator())

        vm.testOnStart()
        vm.onAction(LauncherAction.SplashShown)

        verify(exactly = 1) { router.newRootScreen(mainScreen) }
        vm.testCancelScope()
    }

    @Test
    fun splashShown_opensAuthScreenWithoutAccessToken() = runTest(dispatcher) {
        val router = router()
        val authScreen = mockk<PuberScreen>()
        every { router.screens.auth() } returns authScreen
        val vm = LauncherVM(router, preferences(accessToken = null), coordinator())

        vm.testOnStart()
        vm.onAction(LauncherAction.SplashShown)

        verify(exactly = 1) { router.newRootScreen(authScreen) }
        vm.testCancelScope()
    }

    @Test
    fun splashShownTwice_opensStartScreenOnlyOnce() = runTest(dispatcher) {
        val router = router()
        val mainScreen = mockk<PuberScreen>()
        every { router.screens.main() } returns mainScreen
        val vm = LauncherVM(router, preferences(accessToken = "token"), coordinator())

        vm.testOnStart()
        vm.onAction(LauncherAction.SplashShown)
        vm.onAction(LauncherAction.SplashShown)

        verify(exactly = 1) { router.newRootScreen(mainScreen) }
        vm.testCancelScope()
    }

    private fun router(): AppRouter {
        val router = mockk<AppRouter>(relaxed = true)
        every { router.screens } returns mockk<Screens>(relaxed = true)
        return router
    }

    private fun preferences(accessToken: String?): ICryptoPreferenceRepository {
        return mockk<ICryptoPreferenceRepository>(relaxed = true).apply {
            every { getAccessToken() } returns accessToken
        }
    }

    private fun coordinator() = ContentLaunchCoordinator(ContentUriCodec())
}
