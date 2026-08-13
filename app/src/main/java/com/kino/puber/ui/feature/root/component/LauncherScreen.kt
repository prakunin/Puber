package com.kino.puber.ui.feature.root.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.di.puberViewModel
import com.kino.puber.core.ui.navigation.GlobalRemoteHotkeyBlockedScreen
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.ui.feature.root.model.LauncherAction
import com.kino.puber.ui.feature.root.vm.LauncherVM
import kotlinx.coroutines.delay
import kotlinx.parcelize.Parcelize
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

private const val MIN_VISIBLE_MS = 500L

@Parcelize
internal class LauncherScreen : PuberScreen, GlobalRemoteHotkeyBlockedScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            viewModelOf(::LauncherVM)
        }
    }

    /**
     * Draws the same wordmark [MainActivity] already put on screen before this tree existed, so the
     * hand-over is seamless; the short dwell only keeps it from blinking away on a warm start.
     */
    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = puberViewModel<LauncherVM>()
        vm.collectViewState()

        LaunchedEffect(Unit) {
            delay(MIN_VISIBLE_MS)
            vm.onAction(LauncherAction.SplashShown)
        }

        SplashContent()
    }
}
