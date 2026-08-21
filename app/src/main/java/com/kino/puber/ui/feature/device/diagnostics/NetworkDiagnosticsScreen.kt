package com.kino.puber.ui.feature.device.diagnostics

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.di.puberViewModel
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.uikit.component.ScaffoldMessage
import com.kino.puber.ui.feature.device.diagnostics.vm.NetworkDiagnosticsVM
import kotlinx.parcelize.Parcelize
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class NetworkDiagnosticsScreen : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            viewModelOf(::NetworkDiagnosticsVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val viewModel = puberViewModel<NetworkDiagnosticsVM>()
        val state by viewModel.collectViewState()
        val message by viewModel.collectMessage()
        val onAction = remember(viewModel) { viewModel::onAction }

        Box {
            NetworkDiagnosticsContent(state = state, onAction = onAction)
            ScaffoldMessage(message = message, onAction = onAction)
        }
    }
}
