package com.kino.puber.ui.feature.auth.component

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.ScreenKey
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.navigation.GlobalRemoteHotkeyBlockedScreen
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.ui.feature.auth.model.AuthScreenParams
import com.kino.puber.ui.feature.auth.vm.AuthVM
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class AuthScreen(
    private val params: AuthScreenParams = AuthScreenParams(),
) : PuberScreen, GlobalRemoteHotkeyBlockedScreen {

    // Each reason gets its own DI scope, so a re-root to a blocked Auth does not reuse the old VM.
    @IgnoredOnParcel
    override val key: ScreenKey = params.sessionEndReason?.let { "AuthScreen_${it.name}" } ?: "AuthScreen"

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scoped { params }
            viewModelOf(::AuthVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        AuthScreenComponent()
    }
}
