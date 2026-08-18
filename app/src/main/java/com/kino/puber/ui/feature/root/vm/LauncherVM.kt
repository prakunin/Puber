package com.kino.puber.ui.feature.root.vm

import com.kino.puber.core.contentlink.ContentLaunchCoordinator
import com.kino.puber.core.contentlink.toScreen
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.ui.feature.root.model.LauncherAction

internal class LauncherVM(
    router: AppRouter,
    private val cryptoPreferenceRepository: ICryptoPreferenceRepository,
    private val contentLaunchCoordinator: ContentLaunchCoordinator,
) : PuberVM<Any>(router) {
    override val initialViewState: Any = Unit

    private var startScreenOpened = false

    /**
     * The start screen is opened only once the splash animation has played, otherwise the
     * launcher screen is replaced within a frame and its logo never becomes visible.
     */
    override fun onAction(action: UIAction) {
        when (action) {
            LauncherAction.SplashShown -> openStartScreen()
            else -> super.onAction(action)
        }
    }

    private fun openStartScreen() {
        if (startScreenOpened) return
        startScreenOpened = true

        val isAuthenticated = cryptoPreferenceRepository.getAccessToken().isNullOrEmpty().not()
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
            router.newRootScreen(router.screens.auth())
        }
    }
}
