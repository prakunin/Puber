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
import androidx.compose.runtime.LaunchedEffect
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
import com.kino.puber.core.ui.navigation.component.LocalTabStackState
import com.kino.puber.core.ui.navigation.component.RailBackOwner
import com.kino.puber.core.ui.navigation.component.TabAppRouterHolder
import com.kino.puber.core.ui.navigation.component.TabStackState
import com.kino.puber.core.ui.navigation.component.railBackOwner
import com.kino.puber.core.ui.navigation.component.PuberCurrentTab
import com.kino.puber.core.ui.navigation.component.TabComponent
import com.kino.puber.core.ui.uikit.component.drawer.ContentFocusHandoff
import com.kino.puber.core.ui.uikit.component.drawer.DrawerState
import com.kino.puber.core.ui.uikit.component.drawer.DrawerValue
import com.kino.puber.core.ui.uikit.component.drawer.HandoffKind
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
internal fun DrawerMainContent(
    state: MainViewState,
    onAction: (UIAction) -> Unit,
    tabRouter: TabRouter,
    tabAppRouterHolder: TabAppRouterHolder,
    exitConfirmation: ExitConfirmation = remember { ExitConfirmation() },
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val outerActive = LocalContentFocusActive.current
    val mainContentFocus = rememberFocusRequesterOnLaunch()
    val tabStack = remember { TabStackState() }
    // A failed Dismiss or Initial handoff leaves the rail closed at any depth. A failed tab pick
    // reopens it at a tab root; over a pushed screen Back still has somewhere to go, so content that
    // never takes focus (Show all in Error) leaves the rail closed and the next Back pops it.
    val handoff = remember(drawerState, mainContentFocus, tabStack) {
        ContentFocusHandoff(drawerState, mainContentFocus, closeOnFail = { tabStack.canPop })
    }
    // A tab pick takes the user somewhere else, so an exit armed at the root they left must not let
    // the next Back there leave the app.
    val onRailAction: (UIAction) -> Unit = remember(onAction, exitConfirmation) {
        { action ->
            if (action is CommonAction.ItemSelected<*>) exitConfirmation.disarm()
            onAction(action)
        }
    }

    // A dialog over the screens (outerActive false) is dismissed by removing the node that holds
    // focus, and the focus system then lands on the rail, which reveals it. A handoff in flight is
    // the contract that makes the rail pass such a bounce back to the content, which restores the
    // item the user left. It stays pending while the dialog is up and settles once the content
    // holds focus again.
    LaunchedEffect(outerActive) {
        if (!outerActive) drawerState.beginInitialHandoff()
    }

    CompositionLocalProvider(
        LocalDrawerState provides drawerState,
        LocalContentFocusHandoff provides handoff,
        LocalTabStackState provides tabStack,
    ) {
        // Back has one owner at a time, picked by railBackOwner from the rail's state and whether
        // the tab can pop, so registration order does not decide: this handler is enabled only
        // for a closed rail over a tab root.
        RailClosedBackHandler(drawerState, tabStack, exitConfirmation, outerActive)
        ExitDisarmOnTabPush(tabStack, exitConfirmation)

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
                    onAction = onRailAction,
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
                    LocalContentFocusActive provides (outerActive && drawerState.currentValue != DrawerValue.Open),
                ) {
                    MainScreenContentBody(mainContentFocus, tabRouter, tabAppRouterHolder)
                }
            },
        )

        // Enabled only while the rail is open or handing off; TabBackHandler is disabled then too.
        RailOpenBackHandler(drawerState, tabStack, exitConfirmation, outerActive)
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
    tabStack: TabStackState,
    exitConfirmation: ExitConfirmation,
    outerActive: Boolean,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val owner = railBackOwner(drawerState.currentValue, tabStack.canPop)
    BackHandler(enabled = outerActive && owner == RailBackOwner.RailClosed) {
        if (exitConfirmation.isArmed) {
            activity?.finish()
        } else {
            drawerState.reveal()
        }
        exitConfirmation.disarm()
    }
}

/**
 * Back while the rail is open or handing off. Always consumed — the rail is what is on top — and
 * leaves the app when it is the second Back at a tab root while the first one's handoff is pending.
 */
@Composable
private fun RailOpenBackHandler(
    drawerState: DrawerState,
    tabStack: TabStackState,
    exitConfirmation: ExitConfirmation,
    outerActive: Boolean,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val exitPrompt = stringResource(R.string.main_press_back_again_to_exit)

    val owner = railBackOwner(drawerState.currentValue, tabStack.canPop)
    BackHandler(enabled = outerActive && owner == RailBackOwner.RailOpen) {
        if (drawerState.currentValue != DrawerValue.Open) {
            // An armed Dismiss is the second Back at the same root: only the Back below arms, and
            // a pick or a push or pop disarms. Content with nothing focusable keeps that handoff
            // pending, so the exit has to be taken here. Any other Back while handing off is
            // swallowed: the rail is already closing, and letting it through would pop the screen
            // arriving underneath.
            if (drawerState.handoffKind == HandoffKind.Dismiss && exitConfirmation.isArmed) {
                activity?.finish()
                exitConfirmation.disarm()
            }
            return@BackHandler
        }

        drawerState.beginHandoff(HandoffKind.Dismiss)
        // Over a tab root the user has just walked back out of the menu, so the next Back has
        // nowhere left to go inside the app. Over a pushed screen it still has: it pops.
        if (!tabStack.canPop) {
            exitConfirmation.arm()
            Toast.makeText(context, exitPrompt, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Disarms the exit when a screen is pushed or popped in the tab, so an arm made at the root does
 * not let a later Back leave the app from a different place. Its own composable so the read of
 * [TabStackState.canPop] does not recompose [DrawerMainContent].
 */
@Composable
private fun ExitDisarmOnTabPush(tabStack: TabStackState, exitConfirmation: ExitConfirmation) {
    LaunchedEffect(tabStack.canPop) { exitConfirmation.disarm() }
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

/**
 * A collapsed rail can still hold an item focused. During a Dismiss (Back, Right, re-pick), or
 * Closed after a failed handoff, Center there must not pick a tab the user cannot see. During an
 * initial or tab-pick handoff the tab may still be loading with the rail item focused, and a pick of
 * another tab goes there instead or corrects a mistaken one; [DrawerState.beginHandoff] restarts
 * the handoff for that tab.
 */
private fun DrawerState.acceptsPickOf(tab: MainTab): Boolean = when (currentValue) {
    DrawerValue.Open -> true
    DrawerValue.HandingOff -> handoffKind != HandoffKind.Dismiss && !tab.isSelected
    DrawerValue.Closed -> false
}

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
            if (drawerState.acceptsPickOf(tab)) {
                onAction(CommonAction.ItemSelected(tab))
                // A re-pick of the active tab composes no new content, so it dismisses the rail over
                // the tab on screen, which is the one that has to confirm the handoff.
                drawerState.beginHandoff(if (tab.isSelected) HandoffKind.Dismiss else HandoffKind.TabPick)
            }
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
