package com.kino.puber.ui.feature.root.vm

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.ui.feature.root.model.LauncherAction

internal class LauncherVM(
    router: AppRouter,
    private val cryptoPreferenceRepository: ICryptoPreferenceRepository,
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
            router.newRootScreen(router.screens.main())
        } else {
            router.newRootScreen(router.screens.auth())
        }
    }
}
