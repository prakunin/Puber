package com.kino.puber.ui.feature.root.vm

import com.kino.puber.core.contentlink.ContentLaunchCoordinator
import com.kino.puber.core.contentlink.toScreen
import com.kino.puber.core.session.SessionEndReason
import com.kino.puber.core.session.SessionEventBus
import com.kino.puber.core.startup.AppStartup
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LauncherVM(
    router: AppRouter,
    private val cryptoPreferenceRepository: ICryptoPreferenceRepository,
    private val contentLaunchCoordinator: ContentLaunchCoordinator,
    private val appStartup: AppStartup,
    private val sessionEventBus: SessionEventBus,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PuberVM<Any>(router) {
    override val initialViewState: Any = Unit

    private var startScreenOpened = false

    /**
     * Routes as soon as the saved API domain is applied: the splash now covers the whole app from
     * [MainActivity], so there is no animation left to wait for. The token is read off the main
     * thread, where the encrypted preferences are first opened.
     */
    override fun onStart() {
        launch {
            appStartup.awaitDomainInitialized()
            val (token, endReason) = withContext(ioDispatcher) {
                cryptoPreferenceRepository.getAccessToken() to cryptoPreferenceRepository.getSessionEndReason()
            }
            openStartScreen(isAuthenticated = !token.isNullOrEmpty(), persistedEndReason = endReason)
        }
    }

    private fun openStartScreen(isAuthenticated: Boolean, persistedEndReason: String?) {
        if (startScreenOpened) return
        // The token above is read outside the session lock, so it can predate an end that is already
        // pending. The handler in App routes an end, to Auth with its reason.
        if (sessionEventBus.hasPendingEnd) return
        startScreenOpened = true

        if (isAuthenticated) {
            val target = contentLaunchCoordinator.consumeForAuthenticatedStart()
            if (target == null) {
                router.newRootScreen(router.screens.main())
            } else {
                router.newRootScreens(
                    listOf(
                        router.screens.main(),
                        target.toScreen(router.screens),
                    )
                )
            }
        } else {
            contentLaunchCoordinator.waitForAuthentication()
            // No end is pending in this process: after a restart only the persisted reason is left.
            val reason = SessionEndReason.entries.firstOrNull { it.name == persistedEndReason }
            router.newRootScreen(router.screens.auth(reason))
        }
    }
}
