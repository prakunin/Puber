package com.kino.puber.core.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.Qualifier
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

val LocalPuberKoinScope: ProvidableCompositionLocal<Scope?> = staticCompositionLocalOf { null }
val LocalPuberScopePrefix: ProvidableCompositionLocal<String?> = staticCompositionLocalOf { null }

@Composable
inline fun <reified VM : ViewModel> puberViewModel(
    qualifier: Qualifier? = null,
    key: String? = null,
): VM = koinViewModel<VM>(
    qualifier = qualifier,
    key = key,
    scope = checkNotNull(LocalPuberKoinScope.current) {
        "puberViewModel() must be called inside a DIScope"
    },
)

@Composable
fun DIScope(
    scopeName: String,
    moduleFactory: (scopeId: ScopeID, parentScope: Scope) -> Module = { _, _ ->
        module {}
    },
    content: @Composable () -> Unit,
) {
    val scopePrefix = LocalPuberScopePrefix.current
    val effectiveScopeName = if (scopePrefix == null) {
        scopeName
    } else {
        "$scopePrefix:$scopeName"
    }
    CompositionLocalProvider(
        value = LocalPuberKoinScope provides rememberDIScope(
            scopeName = effectiveScopeName,
            moduleFactory = moduleFactory,
        ),
        content = content,
    )
}
