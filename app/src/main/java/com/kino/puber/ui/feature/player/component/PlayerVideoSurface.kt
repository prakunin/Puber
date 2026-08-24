package com.kino.puber.ui.feature.player.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.player.model.AspectRatioMode
import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerContentState

@Composable
internal fun PlayerVideoSurface(
    content: PlayerContentState,
    exoPlayer: () -> ExoPlayer?,
    onAction: (UIAction) -> Unit,
    focusRequester: FocusRequester,
    playerViewFactory: (Context) -> PlayerView = { context -> PlayerView(context) },
) {
    val windowKeepScreenOnBinding = remember { WindowKeepScreenOnBinding() }
    AndroidView(
        factory = { context ->
            playerViewFactory(context).apply {
                useController = false
                updateKeepScreenOn(
                    shouldKeepScreenOn = content.isPlaying,
                    window = context.findActivity()?.window,
                    windowBinding = windowKeepScreenOnBinding,
                )
                player = exoPlayer()
            }
        },
        update = { view ->
            view.updateKeepScreenOn(
                shouldKeepScreenOn = content.isPlaying,
                window = view.context.findActivity()?.window,
                windowBinding = windowKeepScreenOnBinding,
            )
            val currentPlayer = exoPlayer()
            if (view.player != currentPlayer) {
                view.player = currentPlayer
            }
            currentPlayer?.let { view.resizeMode = content.resizeMode() }
            // Android TV, Fire TV in particular, starts its screen saver after a period without
            // remote input; playback alone does not count as user activity.
            view.keepScreenOn = content.isPlaying
        },
        onRelease = { view ->
            view.keepScreenOn = false
            windowKeepScreenOnBinding.release(view)
            view.player = null
        },
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                handlePlayerKeyEvent(
                    keyEvent = keyEvent.nativeKeyEvent,
                    hasResumeDialog = content.resumeDialog != null,
                    onAction = onAction,
                )
            },
    )
}

private fun PlayerView.updateKeepScreenOn(
    shouldKeepScreenOn: Boolean,
    window: Window?,
    windowBinding: WindowKeepScreenOnBinding,
) {
    keepScreenOn = shouldKeepScreenOn
    windowBinding.update(this, window, shouldKeepScreenOn)
}

private class WindowKeepScreenOnBinding : View.OnAttachStateChangeListener {
    private var boundView: View? = null
    private var requestedWindow: Window? = null
    private var shouldKeepScreenOn = false
    private var ownedWindow: Window? = null

    fun update(view: View, window: Window?, shouldKeepScreenOn: Boolean) {
        if (boundView !== view) {
            boundView?.removeOnAttachStateChangeListener(this)
            releaseOwnedWindow()
            boundView = view
            view.addOnAttachStateChangeListener(this)
        }

        requestedWindow = window
        this.shouldKeepScreenOn = shouldKeepScreenOn
        synchronizeOwnership()
    }

    fun release(view: View) {
        if (boundView === view) {
            view.removeOnAttachStateChangeListener(this)
            boundView = null
            requestedWindow = null
            shouldKeepScreenOn = false
            releaseOwnedWindow()
        }
    }

    override fun onViewAttachedToWindow(view: View) {
        if (boundView === view) synchronizeOwnership()
    }

    override fun onViewDetachedFromWindow(view: View) {
        if (boundView === view) releaseOwnedWindow()
    }

    private fun synchronizeOwnership() {
        val view = boundView
        val window = requestedWindow
        if (!shouldKeepScreenOn || view?.isAttachedToWindow != true || window == null) {
            releaseOwnedWindow()
            return
        }
        if (
            ownedWindow === window &&
            window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        ) {
            return
        }

        releaseOwnedWindow()
        if (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ownedWindow = window
        }
    }

    private fun releaseOwnedWindow() {
        ownedWindow?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ownedWindow = null
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun handlePlayerKeyEvent(
    keyEvent: KeyEvent,
    hasResumeDialog: Boolean,
    onAction: (UIAction) -> Unit,
): Boolean {
    val isOkKey = keyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyEvent.keyCode == KeyEvent.KEYCODE_ENTER
    return when {
        hasResumeDialog -> false
        isOkKey -> handlePlayerOkKeyEvent(keyEvent, onAction)
        else -> handlePlayerActionKeyEvent(keyEvent, onAction)
    }
}

private fun handlePlayerOkKeyEvent(keyEvent: KeyEvent, onAction: (UIAction) -> Unit): Boolean {
    return when (keyEvent.action) {
        KeyEvent.ACTION_DOWN -> {
            // Auto-repeat from a held button must not fire OK again. Arrow keys below keep
            // repeating on purpose, so the guard stays scoped to OK.
            if (keyEvent.repeatCount == 0) {
                onAction(PlayerAction.OkPressed)
            }
            true
        }
        KeyEvent.ACTION_UP -> {
            // Keep focus on the video until this release is consumed. Moving focus to
            // Play/Pause on key-down can make the same physical press pause playback.
            onAction(PlayerAction.OkReleased)
            true
        }
        else -> false
    }
}

private fun handlePlayerActionKeyEvent(
    keyEvent: KeyEvent,
    onAction: (UIAction) -> Unit,
): Boolean {
    val action = if (keyEvent.action == KeyEvent.ACTION_DOWN) {
        playerActionForKeyCode(keyEvent.keyCode)
    } else {
        null
    }
    action?.let(onAction)
    return action != null
}

internal fun playerActionForKeyCode(keyCode: Int): PlayerAction? {
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> PlayerAction.SeekBackward
        KeyEvent.KEYCODE_DPAD_RIGHT -> PlayerAction.SeekForward
        KeyEvent.KEYCODE_DPAD_UP -> PlayerAction.ShowControls(FocusTarget.SeekBar)
        KeyEvent.KEYCODE_DPAD_DOWN -> PlayerAction.ShowControls(FocusTarget.Buttons)
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE -> PlayerAction.TogglePlayPause
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> PlayerAction.SeekForward
        KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> PlayerAction.SeekBackward
        else -> null
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun PlayerContentState.resizeMode(): Int {
    return when (aspectRatios.getOrNull(selectedAspectRatioIndex)?.mode) {
        AspectRatioMode.AUTO -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        AspectRatioMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
}
