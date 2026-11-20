package com.kino.puber.core.ui.uikit.component.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import com.kino.puber.core.ui.navigation.component.LocalScreenKey
import com.kino.puber.core.ui.uikit.component.drawer.DrawerValue
import com.kino.puber.core.ui.uikit.component.drawer.LocalDrawerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * When `false`, [rememberFocusRequesterOnLaunch] will not auto-request focus. Provided as `false`
 * by a host that owns focus itself and must not have it taken by the content it shows.
 */
val LocalAutoFocusOnLaunchEnabled = staticCompositionLocalOf { true }

/**
 * Whether the surrounding navigation host currently owns focus for its content. The side rail
 * keeps this false while it is open, so a retained screen underneath cannot reclaim focus while
 * the user is in the menu.
 */
val LocalContentFocusActive = staticCompositionLocalOf { true }

/**
 * Closes the focus claims of [content] while [active] is false: both [LocalContentFocusActive] and
 * [LocalAutoFocusOnLaunchEnabled] become the outer value AND [active], so a host that is already
 * inactive stays inactive.
 */
@Composable
internal fun ContentFocusGate(active: Boolean, content: @Composable () -> Unit) {
    val contentActive = LocalContentFocusActive.current && active
    val autoFocusEnabled = LocalAutoFocusOnLaunchEnabled.current && active
    CompositionLocalProvider(
        LocalContentFocusActive provides contentActive,
        LocalAutoFocusOnLaunchEnabled provides autoFocusEnabled,
        content = content,
    )
}

/**
 * How long [rememberFocusRequesterOnLaunch] lets a freshly composed screen settle before it asks
 * for focus, so the request lands on a node that is already attached, placed and in a window that
 * has itself finished taking focus.
 *
 * Named because screens that manage their own focus need the same settle time for the same reason,
 * and one of them must be able to say so in code rather than in a comment.
 */
internal const val FOCUS_ON_LAUNCH_DELAY_MILLIS = 100L

@Composable
fun rememberFocusRequesterOnLaunch(): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val isDrawerOpen = LocalDrawerState.current?.currentValue == DrawerValue.Open
    val autoFocusEnabled = LocalAutoFocusOnLaunchEnabled.current
    // Read again after the settle delay: a gate that closed meanwhile (a dialog opened) must stop
    // a request scheduled before it, and the flag stays unset so opening the gate re-arms it.
    val latestAutoFocusEnabled by rememberUpdatedState(autoFocusEnabled)
    // The screen key is an input, not a `key`: the flag belongs to this call site and is reset
    // when the site is reused for another navigation entry. A custom `key` is deprecated
    // because it shares one slot between every call site that names it.
    var isFocusRequested by rememberSaveable(LocalScreenKey.current) {
        mutableStateOf(false)
    }

    if (!isFocusRequested && !isDrawerOpen && autoFocusEnabled) {
        SideEffect {
            scope.launch {
                delay(FOCUS_ON_LAUNCH_DELAY_MILLIS)
                if (isFocusRequested.not() && latestAutoFocusEnabled) {
                    isFocusRequested = true
                    focusRequester.requestFocus()
                }
            }
        }
    }
    return focusRequester
}
