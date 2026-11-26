package com.kino.puber.ui.feature.root.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.lifecycle.Lifecycle
import com.kino.puber.core.contentlink.ContentLaunchCoordinator
import com.kino.puber.core.contentlink.toScreen
import com.kino.puber.core.di.LocalPuberKoinScope
import com.kino.puber.core.di.puberViewModel
import androidx.tv.material3.Surface
import com.kino.puber.core.lifecycle.ReportAppForeground
import com.kino.puber.core.startup.StartupReveal
import com.kino.puber.core.session.SessionEvent
import com.kino.puber.core.session.SessionEndCleanup
import com.kino.puber.core.session.SessionEventBus
import com.kino.puber.core.tvhome.TvHomeSyncCoordinator
import com.kino.puber.data.cache.ContentCacheRepository
import com.kino.puber.domain.interactor.prefetch.DetailsPrefetcher
import com.kino.puber.domain.interactor.watchstate.WatchStateSyncInteractor
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
import com.kino.puber.ui.feature.update.model.UpdatePromptViewState
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
    val contentCache = getKoin().get<ContentCacheRepository>()
    val detailsPrefetcher = getKoin().get<DetailsPrefetcher>()
    val contentLaunchCoordinator = getKoin().get<ContentLaunchCoordinator>()
    val tvHomeSyncCoordinator = getKoin().get<TvHomeSyncCoordinator>()
    val cleanup = remember {
        SessionEndCleanup(
            bus = sessionEventBus,
            resetDeepLink = contentLaunchCoordinator::clearSession,
            // The watch-state index and the content cache belong to the ended account; the details
            // prefetcher goes last so it forgets a cache that is already gone.
            clears = listOf(
                { tvHomeSyncCoordinator.clearAccountPrograms() },
                { watchStateSyncInteractor.invalidate() },
                { contentCache.clear() },
                { detailsPrefetcher.invalidate() },
            ),
        )
    }
    LaunchedEffect(Unit) {
        // The bus keeps the latest end until a pairing succeeds, so an end that happened before this
        // collect started (the cold-start warm-up, core/startup/AppStartup.kt) is handled here too.
        sessionEventBus.events.collect { event ->
            when (event) {
                is SessionEvent.Unauthorized -> {
                    // Routing to auth must not depend on the clears, which are best-effort.
                    cleanup.run()
                    router.newRootScreen(router.screens.auth(event.reason))
                }
            }
        }
    }
}

@Composable
private fun WarmContentLaunchHandler() {
    val scope = checkNotNull(LocalPuberKoinScope.current) {
        "WarmContentLaunchHandler needs an enclosing navigation scope"
    }
    val router by scope.inject<AppRouter>()
    val coordinator = getKoin().get<ContentLaunchCoordinator>()
    LaunchedEffect(router, coordinator) {
        coordinator.changes.collect {
            val target = coordinator.consumeForWarmRouting() ?: return@collect
            router.newRootScreens(
                listOf(
                    router.screens.main(),
                    target.toScreen(router.screens),
                )
            )
        }
    }
}

@Composable
private fun UpdatePromptHost(onOpenChange: (Boolean) -> Unit) {
    val vm = puberViewModel<UpdatePromptVM>()
    val state by vm.collectViewState()
    val onAction = remember(vm) { vm::onAction }
    val latestOnOpenChange by rememberUpdatedState(onOpenChange)
    val isOpen = state != UpdatePromptViewState.Hidden
    SideEffect { latestOnOpenChange(isOpen) }

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
    var updatePromptOpen by remember { mutableStateOf(false) }
    PuberTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape
        ) {
            FlowComponent(
                scopeName = ScopeRoot,
                screen = LauncherScreen(),
                remoteKeyHandler = AppRemoteHotkeyHandler::handle,
                screenFocusActive = !updatePromptOpen,
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
                WarmContentLaunchHandler()
                UpdatePromptHost(
                    onOpenChange = { open ->
                        updatePromptOpen = open
                        StartupReveal.onUpdatePromptOpenChanged(open)
                    },
                )
            }
        }
    }
}
