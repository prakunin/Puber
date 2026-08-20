package com.kino.puber.core.ui.uikit.component.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * The trailer that replaces the still in a detail panel once focus has rested on a card.
 *
 * Every way playback can stop — the end of the trailer, a player error, the app going to the
 * background — reports through [onFinished] rather than being handled here, so the panel and the
 * state that drives it never disagree about what is on screen.
 *
 * [onFirstFrameRendered] reports the one moment there is actually something to look at. Until it
 * fires the view is a black rectangle — media3's shutter, and behind it a `SurfaceView` that has
 * been given no frames yet — so the caller has to keep the still on top until then.
 */
@UnstableApi
@Composable
internal fun TrailerPreviewPlayer(
    url: String,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onFirstFrameRendered: () -> Unit = {},
    scaleToFit: Boolean = false,
) {
    val context = LocalContext.current
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnFirstFrameRendered by rememberUpdatedState(onFirstFrameRendered)
    val playerResizeMode = if (scaleToFit) {
        AspectRatioFrameLayout.RESIZE_MODE_FIT
    } else {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ true,
                )
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    currentOnFinished()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                currentOnFinished()
            }

            override fun onRenderedFirstFrame() {
                currentOnFirstFrameRendered()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    DisposableEffect(url) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url.toUri()))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        onDispose {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    // Coming back from the background into a running trailer is not what the user left.
    // `LifecycleAction` is not used here: it dispatches a `UIAction`, and `CommonAction` has no
    // no-op member to dispatch. The shape below is the one `AppForegroundReporter.kt:25-27` uses.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                currentOnFinished()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = playerResizeMode
                player = exoPlayer
            }
        },
        update = { playerView ->
            playerView.resizeMode = playerResizeMode
        },
        modifier = modifier,
    )
}
