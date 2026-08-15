package com.kino.puber.core.lifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Keeps [state] in step with whether the app is on screen.
 *
 * Belongs at the root of the composition and nowhere else. A lifecycle observer only lives as long
 * as the composable that registered it, and every screen here is mounted through Voyager's
 * `CurrentScreen`, which composes the top of the stack alone — so a screen-level observer stops
 * hearing anything the moment a fullscreen screen is pushed over it. The player is exactly that,
 * which made it the one case this has to get right: watching something and then leaving the app is
 * precisely when background work must stand down, and a report from the main screen would never
 * arrive because the main screen is not composed.
 *
 * ON_START/ON_STOP rather than ON_RESUME/ON_PAUSE: the question is whether the app is visible at
 * all, not whether something is drawn over it.
 */
@Composable
fun ReportAppForeground(state: AppForegroundState) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> state.onEnteredForeground()
                Lifecycle.Event.ON_STOP -> state.onLeftForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
