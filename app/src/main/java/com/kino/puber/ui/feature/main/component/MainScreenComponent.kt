package com.kino.puber.ui.feature.main.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.navigation.component.TabAppRouterHolder
import com.kino.puber.core.ui.navigation.component.PuberCurrentTab
import com.kino.puber.core.ui.navigation.component.TabComponent
import com.kino.puber.core.ui.uikit.component.drawer.ContentFocusHandoff
import com.kino.puber.core.ui.uikit.component.drawer.DrawerState
import com.kino.puber.core.ui.uikit.component.drawer.DrawerValue
import com.kino.puber.core.ui.uikit.component.drawer.LocalContentFocusHandoff
import com.kino.puber.core.ui.uikit.component.drawer.LocalDrawerState
import com.kino.puber.core.ui.uikit.component.drawer.ModalNavigationDrawer
import com.kino.puber.core.ui.uikit.component.drawer.rememberDrawerState
import com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import androidx.lifecycle.Lifecycle
import com.kino.puber.core.ui.uikit.component.LifecycleAction
import com.kino.puber.ui.feature.main.model.MainAction
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.vm.MainVM
import com.kino.puber.core.di.puberViewModel

@Composable
internal fun MainScreenComponent() {
    val vm = puberViewModel<MainVM>()
    val state by vm.collectViewState()
    val onAction: (UIAction) -> Unit = remember { vm::onAction }
    // The watch-state index goes stale while the TV sits idle for hours; coming back is the moment
    // to catch it up.
    LifecycleAction(
        event = Lifecycle.Event.ON_RESUME,
        onAction = onAction,
        action = MainAction.Resumed,
    )
    DrawerMainContent(
        state = state,
        onAction = onAction,
        tabRouter = vm.tabRouter,
        tabAppRouterHolder = vm.tabAppRouterHolder,
    )
}

@Composable
private fun DrawerMainContent(
    state: MainViewState,
    onAction: (UIAction) -> Unit,
    tabRouter: TabRouter,
    tabAppRouterHolder: TabAppRouterHolder,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val mainContentFocus = rememberFocusRequesterOnLaunch()
    val handoff = remember(drawerState, mainContentFocus) {
        ContentFocusHandoff(drawerState, mainContentFocus)
    }
    val exitConfirmation = remember { ExitConfirmation() }

    CompositionLocalProvider(
        LocalDrawerState provides drawerState,
        LocalContentFocusHandoff provides handoff,
    ) {
        // Registered BEFORE the tab content on purpose. OnBackPressedDispatcher gives the event to
        // the last enabled callback, so a pushed tab screen — whose TabBackHandler registers later
        // — keeps Back while the rail is closed. Only when there is nothing to pop does this fire.
        RailClosedBackHandler(drawerState, exitConfirmation)

        ModalNavigationDrawer(
            drawerState = drawerState,
            handoff = handoff,
            scrimBrush = Brush.horizontalGradient(
                listOf(
                    MaterialTheme.colorScheme.scrim, Color.Transparent
                )
            ),
            drawerContent = {
                MainSideMenuContent(
                    state = state,
                    onAction = onAction,
                    drawerState = drawerState,
                )
            },
            content = {
                // The rows restore the card the user left from their own remembered position, but
                // only once something tells them the content stopped holding focus. The rail is that
                // something, and only the top-tab layout used to say it: here the flag stayed true
                // forever, every row went on believing it still held focus, and the restore that
                // returning from the rail is supposed to trigger was skipped. Focus then fell to
                // whatever a plain focus search found first — the hero — and the list jumped to the
                // top with it.
                //
                // Handing off counts as active: the rail is already on its way out and the content
                // is being asked for focus, so the rows must be free to answer with the card the
                // user left rather than waiting for the rail to finish closing.
                CompositionLocalProvider(
                    LocalContentFocusActive provides (drawerState.currentValue != DrawerValue.Open),
                ) {
                    MainScreenContentBody(mainContentFocus, tabRouter, tabAppRouterHolder)
                }
            },
        )

        // Registered AFTER the tab content, which is the only way the rail can win over
        // TabBackHandler while it is on top: the dispatcher picks the last enabled callback, and
        // `enabled` alone cannot reorder that. Disabled while closed so the handler above governs.
        RailOpenBackHandler(drawerState, exitConfirmation)
    }
}

/**
 * Back while the rail is closed: reveal it, or leave the app when the previous Back closed the rail.
 *
 * The exit lives here because Back is the only way out of a TV app and the rail took over the
 * gesture that used to provide it. Two presses rather than one so a stray press cannot drop the
 * user onto the launcher.
 */
@Composable
private fun RailClosedBackHandler(
    drawerState: DrawerState,
    exitConfirmation: ExitConfirmation,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    BackHandler(enabled = drawerState.currentValue == DrawerValue.Closed) {
        if (exitConfirmation.isArmed) {
            activity?.finish()
        } else {
            drawerState.reveal()
        }
        exitConfirmation.disarm()
    }
}

/** Back while the rail is open or handing off. Always consumed — the rail is what is on top. */
@Composable
private fun RailOpenBackHandler(
    drawerState: DrawerState,
    exitConfirmation: ExitConfirmation,
) {
    val context = LocalContext.current
    val exitPrompt = stringResource(R.string.main_press_back_again_to_exit)

    BackHandler(enabled = drawerState.currentValue != DrawerValue.Closed) {
        // HandingOff is swallowed rather than acted on: the rail is already closing, and letting
        // the event through would pop the screen arriving underneath it.
        if (drawerState.currentValue != DrawerValue.Open) return@BackHandler

        drawerState.beginHandoff(expectsNewContent = false)
        // The user has just walked back out of the menu, so the next Back has nowhere left to go
        // inside the app.
        exitConfirmation.arm()
        Toast.makeText(context, exitPrompt, Toast.LENGTH_SHORT).show()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun NavigationDrawerScope.MainSideMenuContent(
    state: MainViewState,
    drawerState: DrawerState,
    onAction: (UIAction) -> Unit
) {
    val tabFocusRequesters = remember(state.tabs.map(MainTab::type)) {
        state.tabs.associate { it.type to FocusRequester() }
    }
    val emptyMenuFocusRequester = remember { FocusRequester() }
    val fallbackFocusItem = tabFocusRequesters[state.selectedTab]
        ?: tabFocusRequesters.values.firstOrNull()
        ?: emptyMenuFocusRequester
    val backgroundColor = animateColorAsState(
        targetValue = if (drawerState.isOpen) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surface
        }
    )

    Column(
        Modifier
            .background(backgroundColor.value)
            .fillMaxHeight()
            .padding(horizontal = 4.dp)
            .verticalScroll(rememberScrollState())
            .focusRestorer(fallbackFocusItem)
            .focusGroup(),
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(Modifier.height(16.dp))
        state.tabs.forEach { tab ->
            key(tab.type) {
                MainSideMenuItem(
                    tabFocusRequester = tabFocusRequesters.getValue(tab.type),
                    tab = tab,
                    onAction = onAction,
                    drawerState = drawerState,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private val DrawerState.isOpen: Boolean
    get() = currentValue == DrawerValue.Open

@Composable
private fun NavigationDrawerScope.MainSideMenuItem(
    tab: MainTab,
    drawerState: DrawerState,
    tabFocusRequester: FocusRequester,
    onAction: (UIAction) -> Unit
) {
    val modifier = Modifier
        .height(40.dp)
        .focusRequester(tabFocusRequester)

    NavigationDrawerItem(
        modifier = modifier,
        selected = tab.isSelected,
        onClick = {
            onAction(CommonAction.ItemSelected(tab))
            // Re-picking the active tab composes no new content, so the tab already on screen is
            // the one that has to confirm the handoff.
            drawerState.beginHandoff(expectsNewContent = !tab.isSelected)
        },
        leadingContent = {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
            )
        },
    ) {
        Text(text = stringResource(tab.type.title))
    }
}

@Composable
private fun MainScreenContentBody(
    focusRequester: FocusRequester,
    tabRouter: TabRouter,
    tabAppRouterHolder: TabAppRouterHolder,
) {
    val closeDrawerWidth = 60.dp
    TabComponent(
        tabRouter = tabRouter,
        tabAppRouterHolder = tabAppRouterHolder,
    ) {
        Box(
            Modifier
                .padding(start = closeDrawerWidth)
                .focusRequester(focusRequester)
                .focusRestorer()
                .focusGroup()
        ) {
            PuberCurrentTab()
        }
    }
}
