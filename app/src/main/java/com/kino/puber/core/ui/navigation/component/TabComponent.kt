package com.kino.puber.core.ui.navigation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.kino.puber.R
import com.kino.puber.core.di.LocalPuberKoinScope
import com.kino.puber.core.telemetry.NavCmd
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.TabHostScreen
import com.kino.puber.core.ui.navigation.TabCommand
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.compose.koinInject

internal val LocalTabAppRouterHolder = staticCompositionLocalOf<TabAppRouterHolder?> { null }

/** The tab host's holder for tab subtrees; it lives as long as the host, not as long as the covered tab. */
internal val LocalTabSubtreeStateHolder = staticCompositionLocalOf<SaveableStateHolder?> { null }

/** The screen instance that hosts the tab rail, provided by it, for telemetry naming. */
internal val LocalTabHostScreen = staticCompositionLocalOf<TabHostScreen?> { null }

internal class TabFlowSession(
    val router: AppRouter,
    initialContentInstanceKey: ScreenKey,
) {
    private var contentInstanceKey: ScreenKey = initialContentInstanceKey

    fun beginContentInstance(nextKey: ScreenKey): Boolean {
        val previousKey = contentInstanceKey
        contentInstanceKey = nextKey
        return previousKey != nextKey
    }
}

internal class TabAppRouterHolder(private val screens: Screens) {
    private data class Entry(val session: TabFlowSession, val scope: CoroutineScope)

    private val entries = mutableMapOf<ScreenKey, Entry>()

    fun getOrCreate(
        key: ScreenKey,
        initialContentInstanceKey: ScreenKey,
    ): TabFlowSession {
        return entries.getOrPut(key) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            Entry(
                session = TabFlowSession(
                    router = AppRouter(coroutineScope = scope, screens = screens),
                    initialContentInstanceKey = initialContentInstanceKey,
                ),
                scope = scope,
            )
        }.session
    }

    fun dispose() {
        entries.values.forEach { it.scope.cancel() }
        entries.clear()
    }
}

@Composable
internal fun TabComponent(
    tabRouter: TabRouter = koinInject(),
    tabAppRouterHolder: TabAppRouterHolder? = null,
    content: @Composable () -> Unit,
) {
    val parentScope = LocalPuberKoinScope.current
    val screens: Screens? = remember(parentScope) { parentScope?.getOrNull() }
    val rememberedHolder = remember(screens, tabAppRouterHolder) {
        if (tabAppRouterHolder == null) {
            screens?.let { TabAppRouterHolder(it) }
        } else {
            null
        }
    }
    val holder = tabAppRouterHolder ?: rememberedHolder
    DisposableEffect(rememberedHolder) {
        onDispose {
            rememberedHolder?.dispose()
        }
    }

    val scopeName = parentScope?.id ?: ""
    val subtreeStateHolder = rememberSaveableStateHolder()
    CompositionLocalProvider(
        LocalTabAppRouterHolder provides holder,
        LocalTabSubtreeStateHolder provides subtreeStateHolder,
    ) {
        TabNavigator(
            tab = LoadingTab,
            // Voyager retains one nested Navigator per logical tab so switching tabs preserves its stack.
            // Refreshes replace that Navigator's root instead of creating generation-specific child Navigators.
            disposeNestedNavigators = false,
            key = scopeName,
        ) {
            val navigator = LocalTabNavigator.current
            LaunchedEffect(tabRouter) {
                tabRouter.events()
                    .collect { event ->
                        when (event) {
                            is TabCommand.Open -> {
                                val from = navigator.current
                                navigator.current = event.tab
                                // The router replays its last Open when the host re-enters composition;
                                // that replay is not a navigation. Identity, not ==: a rail reselect
                                // builds an equal but distinct PuberTab and must still be recorded.
                                if (from !== event.tab) recordNav(NavCmd.TAB, from, event.tab)
                            }
                        }
                    }
            }
            content()
        }
    }
}

@Composable
fun PuberCurrentTab() {
    val tabNavigator = LocalTabNavigator.current
    val currentTab = tabNavigator.current

    val stateHolder = checkNotNull(LocalTabSubtreeStateHolder.current) {
        "PuberCurrentTab needs an enclosing TabComponent"
    }

    key(currentTab.key) {
        // Not tabNavigator.saveableState: Voyager registers that state on the tab list navigator, and
        // the root navigator's nested dispose removes it when a root push covers the host, losing the
        // tab's stack. The host-owned holder keeps it until the host itself leaves.
        stateHolder.SaveableStateProvider(currentTab.key) {
            currentTab.Content()
        }
    }
}

private object LoadingTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(index = 0U, title = stringResource(R.string.loading), icon = null)

    @Composable
    override fun Content() {
        FullScreenProgressIndicator()
    }
}
