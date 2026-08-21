package com.kino.puber.core.ui.uikit.component.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * When `false`, [rememberFocusRequesterOnLaunch] will not auto-request focus.
 * Set to `false` in TopTabs mode so that tab bar keeps focus control.
 */
val LocalAutoFocusOnLaunchEnabled = staticCompositionLocalOf { true }

/**
 * Whether the surrounding navigation host currently owns focus for its content.
 * TopTabs keeps this false while the tab row owns focus so retained screens
 * cannot reclaim focus merely by becoming selected.
 */
val LocalContentFocusActive = staticCompositionLocalOf { true }

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
    var isFocusRequested by rememberSaveable(key = LocalScreenKey.current) {
        mutableStateOf(false)
    }

    if (!isFocusRequested && !isDrawerOpen && autoFocusEnabled) {
        SideEffect {
            scope.launch {
                delay(FOCUS_ON_LAUNCH_DELAY_MILLIS)
                if (isFocusRequested.not()) {
                    isFocusRequested = true
                    focusRequester.requestFocus()
                }
            }
        }
    }
    return focusRequester
}
