package com.kino.puber.ui.feature.details.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import com.kino.puber.ui.feature.player.component.PlayPauseIndicator
import com.kino.puber.ui.feature.player.component.PlayerProgressBar
import com.kino.puber.ui.feature.player.component.PlayerTitle
import com.kino.puber.ui.feature.player.model.PlayPauseIndicatorState
import kotlinx.coroutines.delay

/** Matches `PlayerVM.CONTROLS_HIDE_DELAY_MS`, so the trailer settles like the player does. */
private const val CONTROLS_HIDE_DELAY_MS = 4_500L

/** Matches `PlayerVM.PLAY_PAUSE_INDICATOR_HIDE_DELAY_MS`. */
private const val PLAY_PAUSE_INDICATOR_HIDE_DELAY_MS = 1_500L

private const val PROGRESS_POLL_MS = 250L
private const val SEEK_STEP_MS = 10_000L

/**
 * The progress bar can be asked for focus before it has been placed, and a request that arrives
 * then throws rather than queueing. Everything the remote does depends on that focus landing, so
 * the request is retried rather than attempted once.
 */
private const val FOCUS_ATTEMPTS = 20
private const val FOCUS_RETRY_MS = 50L

/**
 * The trailer at full screen, with the controls the player screen has: elapsed and remaining time,
 * a progress bar, seek on LEFT/RIGHT and play/pause on OK. They fade out on their own and any key
 * brings them back.
 *
 * Focus lives on the progress bar for as long as the trailer is up, visible or not. That is what
 * keeps the remote working: the details screen underneath is still composed, and whatever holds
 * focus is what the keys reach.
 */
@UnstableApi
@Composable
internal fun TrailerOverlay(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = url != null,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val context = LocalContext.current
        val exoPlayer = remember {
            ExoPlayer.Builder(context).build()
        }

        var isBuffering by remember { mutableStateOf(true) }
        var position by remember { mutableLongStateOf(0L) }
        var bufferedPosition by remember { mutableLongStateOf(0L) }
        var duration by remember { mutableLongStateOf(0L) }
        var playPauseIndicator by remember { mutableStateOf<PlayPauseIndicatorState?>(null) }

        // Bumped by every keypress. The hide timer keys off it, so each one restarts the countdown
        // rather than stacking another timer on top.
        var interactions by remember { mutableIntStateOf(0) }
        var controlsVisible by remember { mutableStateOf(true) }
        val controlsAlpha by animateFloatAsState(
            targetValue = if (controlsVisible) 1F else 0F,
            label = "trailer_controls_alpha",
        )
        val progressFocusRequester = remember { FocusRequester() }

        DisposableEffect(exoPlayer) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                }
            }
            exoPlayer.addListener(listener)
            onDispose { exoPlayer.removeListener(listener) }
        }

        DisposableEffect(url) {
            if (url != null) {
                exoPlayer.setMediaItem(MediaItem.fromUri(url.toUri()))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
            onDispose {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                exoPlayer.release()
            }
        }

        // Leaving the app does not take the trailer's sound with it: the overlay stays composed and
        // the player goes on playing, over the television's home screen. The panel preview has
        // always reported ON_STOP for the same reason; this one pauses, so coming back resumes
        // where the user left rather than starting over.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    exoPlayer.playWhenReady = false
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(exoPlayer) {
            while (true) {
                position = exoPlayer.currentPosition
                bufferedPosition = exoPlayer.bufferedPosition
                duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
                delay(PROGRESS_POLL_MS)
            }
        }

        LaunchedEffect(interactions) {
            controlsVisible = true
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }

        LaunchedEffect(playPauseIndicator) {
            if (playPauseIndicator != null) {
                delay(PLAY_PAUSE_INDICATOR_HIDE_DELAY_MS)
                playPauseIndicator = null
            }
        }

        LaunchedEffect(Unit) {
            repeat(FOCUS_ATTEMPTS) {
                if (runCatching { progressFocusRequester.requestFocus() }.isSuccess) {
                    return@LaunchedEffect
                }
                delay(FOCUS_RETRY_MS)
            }
        }

        val togglePlayPause: () -> Unit = {
            val resuming = !exoPlayer.playWhenReady
            exoPlayer.playWhenReady = resuming
            playPauseIndicator = PlayPauseIndicatorState(isPlaying = resuming)
            interactions++
        }

        val seekBy: (Long) -> Unit = { deltaMs ->
            val end = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
            val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, end.coerceAtLeast(0L))
            exoPlayer.seekTo(target)
            position = target
            interactions++
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
                // Any key wakes the controls, including the ones the progress bar goes on to
                // handle itself. Nothing is consumed here, so it never competes with it.
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        interactions++
                    }
                    false
                },
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Everything below is drawn after the `SurfaceView`, which clears whatever the window
            // painted before it. Only what comes later survives on top of the video.
            PlayerTitle(
                title = title,
                subtitle = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .alpha(controlsAlpha),
            )

            PlayPauseIndicator(
                state = playPauseIndicator,
                modifier = Modifier.align(Alignment.Center),
            )

            PlayerProgressBar(
                currentPosition = position,
                duration = duration,
                bufferedPosition = bufferedPosition,
                isBuffering = isBuffering,
                onSeekForward = { seekBy(SEEK_STEP_MS) },
                onSeekBackward = { seekBy(-SEEK_STEP_MS) },
                onOkPressed = togglePlayPause,
                focusRequester = progressFocusRequester,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .alpha(controlsAlpha),
            )
        }
    }
}
