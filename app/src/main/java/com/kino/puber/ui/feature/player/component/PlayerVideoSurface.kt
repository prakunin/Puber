package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                player = exoPlayer()
            }
        },
        update = { view ->
            val currentPlayer = exoPlayer()
            if (view.player != currentPlayer) {
                view.player = currentPlayer
            }
            currentPlayer?.let { view.resizeMode = content.resizeMode() }
            // Android TV, Fire TV in particular, starts its screen saver after a period without
            // remote input; playback alone does not count as user activity.
            view.keepScreenOn = content.isPlaying
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
