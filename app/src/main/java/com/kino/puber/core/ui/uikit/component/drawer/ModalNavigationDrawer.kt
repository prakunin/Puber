/*
 * Forked from androidx.tv.material3 (tv-material:1.1.0-beta01)
 * Original: https://android.googlesource.com/platform/frameworks/support/+/refs/heads/main/tv/tv-material/src/main/java/androidx/tv/material3/NavigationDrawer.kt
 *
 * Reason: DrawerSheet derived the drawer's value from focus (onFocusChanged → setValue), so a focus
 * request that missed read as the user reopening the menu. The fork gives DrawerState an explicit
 * state machine and demotes focus to one input among several, with a transient HandingOff state
 * covering the window in which focus is travelling to freshly composed content.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.kino.puber.core.ui.uikit.component.drawer

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.compose.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerScope

val LocalDrawerState = staticCompositionLocalOf<DrawerState?> { null }

/** States that the drawer can exist in. */
enum class DrawerValue {
    Closed,
    Open,

    /**
     * The user has chosen — the rail is logically closed, but focus has not landed in the
     * content yet.
     *
     * It exists to tell apart "focus left the rail because the user decided so" from "focus came
     * back to the rail because there was nowhere to land". Without it the rail's value is a
     * projection of focus, and a `requestFocus()` that misses reads as the user reopening the menu.
     */
    HandingOff,
}

/**
 * State of the [ModalNavigationDrawer], and the authority on whether the rail is open.
 *
 * Transitions happen only through the intents below. Focus is an input to them, never the value
 * itself — that inversion is the whole point of the fork.
 *
 * The transient [DrawerValue.HandingOff] exists because the focus system cannot be trusted to hold
 * a handover on its own:
 * - `focusRestorer()` does not save focus state when focus jumps away, only on D-pad exit:
 *   [#296551299](https://issuetracker.google.com/issues/296551299)
 * - `saveFocusedChild`/`restoreFocusedChild` are unreliable with `LazyColumn`:
 *   [#290645002](https://issuetracker.google.com/issues/290645002)
 *
 * so focus may bounce between rail and content several times before settling, and every bounce
 * would otherwise be read as the user reopening the menu.
 */
class DrawerState(initialValue: DrawerValue = DrawerValue.Closed) {
    var currentValue by mutableStateOf(persistedValue(initialValue))
        private set

    /**
     * Identifies the handoff currently waiting on the content, or `null` when none is.
     *
     * Monotonic on purpose: the user can press again while a handoff is in flight, and a
     * confirmation belonging to the abandoned attempt must not close a rail that a newer intent
     * has just reopened.
     */
    var pendingHandoffId: Long? by mutableStateOf(null)
        private set

    /**
     * Whether the pending handoff is waiting on content that does not exist yet.
     *
     * Picking a different tab dispatches the swap asynchronously, so for a short window the
     * *outgoing* tab is still composed and still focusable. Without this flag it would take the
     * focus the handoff was aiming at the arriving tab, report the landing, and close the rail
     * over content that is about to be torn down — reproducing the very bug this state machine
     * exists to prevent. When `true`, only content composed after the handoff began may confirm it.
     */
    var handoffExpectsNewContent: Boolean by mutableStateOf(false)
        private set

    private var lastHandoffId = 0L

    /** Focus entered the rail, or Back was pressed in the content. */
    fun reveal() {
        if (currentValue == DrawerValue.Closed) {
            currentValue = DrawerValue.Open
        }
    }

    /**
     * A rail item was clicked, or Back was pressed while the rail was open — both change the rail
     * without moving focus, so focus has to be handed over explicitly.
     *
     * @param expectsNewContent `true` when the handoff accompanies a tab change, so the content
     *   that must confirm it is not composed yet. See [handoffExpectsNewContent].
     * @return the request id to confirm against, or `null` if the rail was not open.
     */
    fun beginHandoff(expectsNewContent: Boolean): Long? {
        if (currentValue != DrawerValue.Open) return null
        return startHandoff(expectsNewContent)
    }

    /** Starts the first content handoff before the user has interacted with the rail. */
    fun beginInitialHandoff(): Long? {
        if (currentValue != DrawerValue.Closed) return null
        return startHandoff(expectsNewContent = false)
    }

    private fun startHandoff(expectsNewContent: Boolean): Long {
        lastHandoffId += 1
        pendingHandoffId = lastHandoffId
        handoffExpectsNewContent = expectsNewContent
        currentValue = DrawerValue.HandingOff
        return lastHandoffId
    }

    /** D-pad right carried focus into the content, which needs no handoff. */
    fun focusExited() {
        if (currentValue == DrawerValue.Open) {
            currentValue = DrawerValue.Closed
        }
    }

    /** The content confirmed it holds focus. */
    fun settleHandoff(requestId: Long) {
        if (pendingHandoffId != requestId) return
        pendingHandoffId = null
        currentValue = DrawerValue.Closed
    }

    /** The content never took focus. Reopening beats stranding focus in nothing. */
    fun failHandoff(requestId: Long) {
        if (pendingHandoffId != requestId) return
        pendingHandoffId = null
        currentValue = DrawerValue.Open
    }

    companion object {
        /** [DrawerValue.HandingOff] is transient and must not survive process death. */
        fun persistedValue(value: DrawerValue): DrawerValue =
            if (value == DrawerValue.HandingOff) DrawerValue.Closed else value

        val Saver =
            Saver<DrawerState, DrawerValue>(
                save = { persistedValue(it.currentValue) },
                restore = { DrawerState(it) },
            )
    }
}

@Composable
fun rememberDrawerState(initialValue: DrawerValue): DrawerState {
    return rememberSaveable(saver = DrawerState.Saver) { DrawerState(initialValue) }
}

@Composable
fun ModalNavigationDrawer(
    drawerContent: @Composable NavigationDrawerScope.(DrawerValue) -> Unit,
    modifier: Modifier = Modifier,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    handoff: ContentFocusHandoff? = null,
    scrimBrush: Brush = SolidColor(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
    content: @Composable () -> Unit,
) {
    val localDensity = LocalDensity.current
    val closedDrawerWidth: MutableState<Dp?> = remember { mutableStateOf(null) }
    LaunchedEffect(drawerState, handoff) {
        if (handoff != null) drawerState.beginInitialHandoff()
    }
    val internalDrawerModifier =
        Modifier.zIndex(Float.MAX_VALUE).onSizeChanged {
            if (closedDrawerWidth.value == null && drawerState.currentValue == DrawerValue.Closed) {
                with(localDensity) { closedDrawerWidth.value = it.width.toDp() }
            }
        }

    Box(modifier = modifier) {
        DrawerSheet(
            modifier = internalDrawerModifier.align(Alignment.CenterStart),
            drawerState = drawerState,
            handoff = handoff,
            sizeAnimationFinishedListener = { _, targetSize ->
                if (drawerState.currentValue == DrawerValue.Closed) {
                    with(localDensity) { closedDrawerWidth.value = targetSize.width.toDp() }
                }
            },
            content = drawerContent,
        )

        content()

        if (drawerState.currentValue == DrawerValue.Open) {
            Canvas(Modifier.fillMaxSize()) { drawRect(scrimBrush) }
        }
    }
}

@Composable
private fun DrawerSheet(
    modifier: Modifier = Modifier,
    drawerState: DrawerState = remember { DrawerState() },
    handoff: ContentFocusHandoff? = null,
    sizeAnimationFinishedListener: ((initialValue: IntSize, targetValue: IntSize) -> Unit)? = null,
    content: @Composable NavigationDrawerScope.(DrawerValue) -> Unit,
) {
    var initializationComplete: Boolean by remember { mutableStateOf(false) }
    var focusState by remember { mutableStateOf<FocusState?>(null) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(key1 = drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open && focusState?.hasFocus == false) {
            focusRequester.requestFocus()
        }
        initializationComplete = true
    }

    val internalModifier =
        Modifier.focusRequester(focusRequester)
            .animateContentSize(finishedListener = sizeAnimationFinishedListener)
            .fillMaxHeight()
            .then(modifier)
            // Right is the only way out of the rail, and left to the focus system it is resolved as
            // a plain two-dimensional search: focus lands on whatever happens to sit nearest the
            // rail, and the card the user left is not merely skipped but overwritten, because the
            // row records whatever card it just gave focus to. Turning the key into the same
            // handoff Back already performs puts the content in charge of where focus goes, so it
            // can answer with the position it remembers.
            //
            // Swallowed while the handoff travels, too. It takes several frames to land, and focus
            // sits in the rail for all of them, so auto-repeat from a held press would otherwise
            // reach the focus search after the first press had already flipped the state — losing
            // the position to the very search this exists to prevent.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionRight) {
                    return@onPreviewKeyEvent false
                }
                when (drawerState.currentValue) {
                    DrawerValue.Open -> drawerState.beginHandoff(expectsNewContent = false) != null
                    DrawerValue.HandingOff -> true
                    DrawerValue.Closed -> false
                }
            }
            .onFocusChanged {
                focusState = it

                if (!initializationComplete) return@onFocusChanged

                when {
                    // A handoff is in flight: focus arriving here is the bounce we are guarding
                    // against, not the user coming back. Send it where it was headed.
                    drawerState.currentValue == DrawerValue.HandingOff ->
                        if (it.hasFocus) handoff?.redirectFocusToContent() else Unit

                    it.hasFocus -> drawerState.reveal()
                    else -> drawerState.focusExited()
                }
            }
            .focusGroup()

    Box(modifier = internalModifier) {
        NavigationDrawerScopeImpl(drawerState.currentValue == DrawerValue.Open).apply {
            content(drawerState.currentValue)
        }
    }
}

private class NavigationDrawerScopeImpl(override val hasFocus: Boolean) : NavigationDrawerScope
