package com.kino.puber.ui.feature.root.vm

import com.kino.puber.core.contentlink.ContentLaunchCoordinator
import com.kino.puber.core.contentlink.ContentUriCodec
import com.kino.puber.core.session.SessionEndReason
import com.kino.puber.core.session.SessionEvent
import com.kino.puber.core.session.SessionEventBus
import com.kino.puber.core.startup.AppStartup
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.Executors

internal class LauncherVMTest {

    companion object {
        private val dispatcher = StandardTestDispatcher()

        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension(dispatcher)
    }

    @Test
    fun start_opensMainScreenWithoutASplashAction() = runTest(dispatcher) {
        val router = router()
        val mainScreen = mockk<PuberScreen>()
        every { router.screens.main() } returns mainScreen
        val vm = LauncherVM(router, preferences(accessToken = "token"), coordinator(),
            startup(),
            SessionEventBus(),
            dispatcher,
        )

        vm.testOnStart()
        advanceUntilIdle()

        verify(exactly = 1) { router.newRootScreen(mainScreen) }
        vm.testCancelScope()
    }

    @Test
    fun start_readsTheAccessTokenOnTheIoDispatcher() = runTest(dispatcher) {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "launcher-io") }
        try {
            val readOn = CompletableDeferred<String>()
            val preferences = mockk<ICryptoPreferenceRepository>(relaxed = true)
            every { preferences.getAccessToken() } answers {
                readOn.complete(Thread.currentThread().name)
                "token"
            }
            val vm = LauncherVM(router(), preferences, coordinator(),
            startup(),
            SessionEventBus(),
            executor.asCoroutineDispatcher(),
        )

            vm.testOnStart()
            advanceUntilIdle()

            val thread = withContext(Dispatchers.Default) { withTimeout(5_000) { readOn.await() } }
            assertTrue(thread.startsWith("launcher-io"), thread)
            vm.testCancelScope()
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun start_waitsForDomainInitBeforeReadingTheToken() = runTest(dispatcher) {
        val router = router()
        val mainScreen = mockk<PuberScreen>()
        every { router.screens.main() } returns mainScreen
        val preferences = preferences(accessToken = "token")
        val gate = CompletableDeferred<Unit>()
        val vm = LauncherVM(router, preferences, coordinator(), startup(gate), SessionEventBus(), dispatcher)

        vm.testOnStart()
        advanceUntilIdle()

        verify(exactly = 0) { preferences.getAccessToken() }
        verify(exactly = 0) { router.newRootScreen(*anyVararg()) }

        gate.complete(Unit)
        advanceUntilIdle()

        verify(exactly = 1) { router.newRootScreen(mainScreen) }
        vm.testCancelScope()
    }

    @Test
    fun start_opensMainScreenForAuthenticatedSession() = runTest(dispatcher) {
        val router = router()
        val mainScreen = mockk<PuberScreen>()
        every { router.screens.main() } returns mainScreen
        val vm = LauncherVM(router, preferences(accessToken = "token"), coordinator(),
            startup(),
            SessionEventBus(),
            dispatcher,
        )

        vm.testOnStart()
        advanceUntilIdle()

        verify(exactly = 1) { router.newRootScreen(mainScreen) }
        vm.testCancelScope()
    }

    @Test
    fun start_opensAuthScreenWithoutAccessToken() = runTest(dispatcher) {
        val router = router()
        val authScreen = mockk<PuberScreen>()
        every { router.screens.auth() } returns authScreen
        val vm = LauncherVM(
            router,
            preferences(accessToken = null),
            coordinator(),
            startup(),
            SessionEventBus(),
            dispatcher,
        )

        vm.testOnStart()
        advanceUntilIdle()

        verify(exactly = 1) { router.newRootScreen(authScreen) }
        vm.testCancelScope()
    }

    @Test
    fun start_leavesAuthRoutingToTheSessionHandler_whenASessionEndIsPending() = runTest(dispatcher) {
        val router = router()
        val bus = SessionEventBus().apply { emit(SessionEvent.Unauthorized(SessionEndReason.Blocked)) }
        val vm = LauncherVM(
            router,
            preferences(accessToken = null),
            coordinator(),
            startup(),
            bus,
            dispatcher,
        )

        vm.testOnStart()
        advanceUntilIdle()

        verify(exactly = 0) { router.newRootScreen(*anyVararg()) }
        vm.testCancelScope()
    }

    @Test
    fun start_leavesRoutingToTheSessionHandler_whenATokenIsStillReadableAndAnEndIsPending() = runTest(dispatcher) {
        val router = router()
        val bus = SessionEventBus().apply { emit(SessionEvent.Unauthorized(SessionEndReason.Expired)) }
        val vm = LauncherVM(
            router,
            preferences(accessToken = "token"),
            coordinator(),
            startup(),
            bus,
            dispatcher,
        )

        vm.testOnStart()
        advanceUntilIdle()

        verify(exactly = 0) { router.newRootScreen(*anyVararg()) }
        verify(exactly = 0) { router.newRootScreens(any()) }
        vm.testCancelScope()
    }

    @Test
    fun start_opensAuthWithThePersistedReason_afterTheProcessDiedPastASessionEnd() = runTest(dispatcher) {
        val router = router()
        val authScreen = mockk<PuberScreen>()
        every { router.screens.auth(SessionEndReason.Blocked) } returns authScreen
        val vm = LauncherVM(
            router,
            preferences(accessToken = null, endReason = "Blocked"),
            coordinator(),
            startup(),
            SessionEventBus(),
            dispatcher,
        )

        vm.testOnStart()
        advanceUntilIdle()

        verify(exactly = 1) { router.newRootScreen(authScreen) }
        vm.testCancelScope()
    }

    @Test
    fun start_opensPlainAuth_whenThePersistedReasonIsUnknown() = runTest(dispatcher) {
        val router = router()
        val authScreen = mockk<PuberScreen>()
        every { router.screens.auth() } returns authScreen
        val vm = LauncherVM(
            router,
            preferences(accessToken = null, endReason = "Gone"),
            coordinator(),
            startup(),
            SessionEventBus(),
            dispatcher,
        )

        vm.testOnStart()
        advanceUntilIdle()

        verify(exactly = 1) { router.newRootScreen(authScreen) }
        vm.testCancelScope()
    }

    @Test
    fun start_opensMainAndTheContentLinkTarget_whenALinkIsPending() = runTest(dispatcher) {
        val router = router()
        val screens = mockk<Screens>(relaxed = true)
        every { router.screens } returns screens
        val mainScreen = mockk<PuberScreen>()
        val detailsScreen = mockk<PuberScreen>()
        every { screens.main() } returns mainScreen
        every { screens.details(42) } returns detailsScreen
        val coordinator = coordinator().apply { accept("puber://content/items/42") }
        val vm = LauncherVM(
            router,
            preferences(accessToken = "token"),
            coordinator,
            startup(),
            SessionEventBus(),
            dispatcher,
        )

        vm.testOnStart()
        advanceUntilIdle()

        verify(exactly = 1) { router.newRootScreens(listOf(mainScreen, detailsScreen)) }
        vm.testCancelScope()
    }

    @Test
    fun startTwice_opensStartScreenOnlyOnce() = runTest(dispatcher) {
        val router = router()
        val mainScreen = mockk<PuberScreen>()
        every { router.screens.main() } returns mainScreen
        val vm = LauncherVM(router, preferences(accessToken = "token"), coordinator(),
            startup(),
            SessionEventBus(),
            dispatcher,
        )

        vm.testOnStart()
        vm.testOnStart()
        advanceUntilIdle()

        verify(exactly = 1) { router.newRootScreen(mainScreen) }
        vm.testCancelScope()
    }

    private fun startup(gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)): AppStartup =
        mockk<AppStartup>().apply { coEvery { awaitDomainInitialized() } coAnswers { gate.await() } }

    private fun router(): AppRouter {
        val router = mockk<AppRouter>(relaxed = true)
        every { router.screens } returns mockk<Screens>(relaxed = true)
        return router
    }

    private fun preferences(accessToken: String?, endReason: String? = null): ICryptoPreferenceRepository {
        return mockk<ICryptoPreferenceRepository>(relaxed = true).apply {
            every { getAccessToken() } returns accessToken
            every { getSessionEndReason() } returns endReason
        }
    }

    private fun coordinator() = ContentLaunchCoordinator(ContentUriCodec())
}
