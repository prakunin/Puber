package com.kino.puber.ui.feature.root.component

import androidx.compose.runtime.Composable
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.di.puberViewModel
import com.kino.puber.core.ui.navigation.GlobalRemoteHotkeyBlockedScreen
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.ui.feature.root.vm.LauncherVM
import kotlinx.parcelize.Parcelize
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class LauncherScreen : PuberScreen, GlobalRemoteHotkeyBlockedScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            viewModel { LauncherVM(get(), get(), get(), get()) }
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = puberViewModel<LauncherVM>()
        vm.collectViewState()
    }
}
