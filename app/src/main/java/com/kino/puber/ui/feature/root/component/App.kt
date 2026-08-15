package com.kino.puber.ui.feature.root.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.lifecycle.Lifecycle
import com.kino.puber.core.di.LocalPuberKoinScope
import com.kino.puber.core.di.puberViewModel
import androidx.tv.material3.Surface
import com.kino.puber.core.lifecycle.ReportAppForeground
import com.kino.puber.core.logger.log
import com.kino.puber.core.session.SessionEvent
import com.kino.puber.core.session.SessionEventBus
import com.kino.puber.data.repository.PersistentPayloadStore
import com.kino.puber.domain.interactor.prefetch.DetailsPrefetcher
import com.kino.puber.domain.interactor.watchstate.WatchStateSyncInteractor
import kotlinx.coroutines.CancellationException
import com.kino.puber.core.ui.uikit.component.LifecycleAction
import com.kino.puber.core.ui.model.VideoItemTypeMapper
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.AppLauncherImpl
import com.kino.puber.core.ui.navigation.AppRemoteHotkeyHandler
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.component.FlowComponent
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.ui.ScreensImpl
import com.kino.puber.ui.feature.update.component.UpdatePromptOverlay
import com.kino.puber.ui.feature.update.model.UpdatePromptAction
import com.kino.puber.ui.feature.update.vm.UpdatePromptVM
import org.koin.core.module.Module
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.ScopeID
import org.koin.dsl.module
import org.koin.mp.KoinPlatform.getKoin

private fun buildFlowModule(
    scopeId: ScopeID,
    appLauncher: AppLauncher,
): Module = module {
    scope(named(scopeId)) {
        scoped<AppLauncher> { appLauncher }
        scoped<Screens> { ScreensImpl }
        scoped { VideoItemUIMapper(get(), get(), get()) }
        scopedOf(::VideoItemTypeMapper)
        viewModelOf(::UpdatePromptVM)
    }
}

private const val ScopeRoot = "Root"

@Composable
private fun SessionExpiredHandler() {
    val scope = checkNotNull(LocalPuberKoinScope.current) { "SessionExpiredHandler needs an enclosing DIScope" }
    val router by scope.inject<AppRouter>()
    val sessionEventBus = getKoin().get<SessionEventBus>()
    val watchStateSyncInteractor = getKoin().get<WatchStateSyncInteractor>()
    val payloadStore = getKoin().get<PersistentPayloadStore>()
    val detailsPrefetcher = getKoin().get<DetailsPrefetcher>()
    LaunchedEffect(Unit) {
        // This collect is the app's only subscriber to session-expiry events. An exception escaping
        // it would kill the collector, and every later Unauthorized event for the rest of the
        // process would then go unhandled — so the clears below must never be able to do that.
        suspend fun clearWithoutFailing(clear: suspend () -> Unit) {
            try {
                clear()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                log(error, "Failed to clear session-scoped state on logout")
            }
        }

        sessionEventBus.events.collect { event ->
            when (event) {
                SessionEvent.Unauthorized -> {
                    // The watch-state index is one account's viewing history, and the payload store
                    // is that account's cached view of one domain's catalogue; neither must outlive
                    // the session. Ejecting the user to auth is the part that must not be optional,
                    // so it runs unconditionally below; these clears ahead of it are best-effort,
                    // same as clearDomainSensitiveCaches's clears on a domain switch.
                    clearWithoutFailing { watchStateSyncInteractor.invalidate() }
                    clearWithoutFailing { payloadStore.clear() }
                    // Last, so it forgets a cache that is already gone rather than one a warm in
                    // flight could still refill behind it.
                    clearWithoutFailing { detailsPrefetcher.invalidate() }
                    router.newRootScreen(router.screens.auth())
                }
            }
        }
    }
}

@Composable
private fun UpdatePromptHost() {
    val vm = puberViewModel<UpdatePromptVM>()
    val state by vm.collectViewState()
    val onAction = remember(vm) { vm::onAction }

    LifecycleAction(
        event = Lifecycle.Event.ON_RESUME,
        onAction = onAction,
        action = UpdatePromptAction.OnResume,
    )
    UpdatePromptOverlay(
        state = state,
        onAction = onAction,
    )
}

@Composable
fun App() {
    val appLauncher = AppLauncherImpl.rememberAppLauncher()
    PuberTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape
        ) {
            FlowComponent(
                scopeName = ScopeRoot,
                screen = LauncherScreen(),
                remoteKeyHandler = AppRemoteHotkeyHandler::handle,
                moduleFactory = { scopeId, parentScope ->
                    buildFlowModule(
                        scopeId,
                        appLauncher = appLauncher,
                    )
                },
            ) {
                // Here rather than on the main screen: this composition outlives every screen, and
                // the background walk has to stand down even when a fullscreen screen is on top.
                ReportAppForeground(getKoin().get())
                SessionExpiredHandler()
                UpdatePromptHost()
            }
        }
    }
}
