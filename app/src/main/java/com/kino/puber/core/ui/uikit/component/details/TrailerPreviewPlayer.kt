package com.kino.puber.core.ui.uikit.component.details

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.OptIn
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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3UiR

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
 *
 * The `SurfaceView` is redrawn once it gets a size, because a size that arrives after the window's
 * last pre-draw gets no surface otherwise.
 */
@UnstableApi
@Composable
internal fun TrailerPreviewPlayer(
    url: String,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onFirstFrameRendered: () -> Unit = {},
    scaleToFit: Boolean = false,
    alignContentToTop: Boolean = false,
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
            .setLoadControl(buildTrailerPreviewLoadControl())
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

    // Stop the player directly before clearing the state. Once the app is in the background,
    // Compose may defer the recomposition that removes this composable; waiting for onDispose in
    // that case leaves ExoPlayer audible over the device launcher.
    // Coming back from the background into a running trailer is not what the user left.
    // `LifecycleAction` is not used here: it dispatches a `UIAction`, and `CommonAction` has no
    // no-op member to dispatch. The shape below is the one `AppForegroundReporter.kt:25-27` uses.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                exoPlayer.stop()
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
                updateContentGravity(alignContentToTop)
                player = exoPlayer
                videoSurfaceView?.addOnLayoutChangeListener(surfaceRedrawOnSizeChange)
            }
        },
        onRelease = { playerView ->
            playerView.videoSurfaceView?.removeOnLayoutChangeListener(surfaceRedrawOnSizeChange)
        },
        update = { playerView ->
            playerView.resizeMode = playerResizeMode
            playerView.updateContentGravity(alignContentToTop)
        },
        modifier = modifier,
    )
}

/** A redraw is needed only for a change to a non-zero size. */
internal fun surfaceNeedsRedraw(oldWidth: Int, oldHeight: Int, newWidth: Int, newHeight: Int): Boolean =
    newWidth > 0 && newHeight > 0 && (newWidth != oldWidth || newHeight != oldHeight)

private val surfaceRedrawOnSizeChange = View.OnLayoutChangeListener {
        v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
    if (surfaceNeedsRedraw(oldRight - oldLeft, oldBottom - oldTop, right - left, bottom - top)) {
        v.post { v.invalidate() }
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.updateContentGravity(alignContentToTop: Boolean) {
    val contentFrame = findViewById<AspectRatioFrameLayout>(Media3UiR.id.exo_content_frame) ?: return
    val params = contentFrame.layoutParams as? FrameLayout.LayoutParams ?: return
    val targetGravity = if (alignContentToTop) {
        Gravity.TOP or Gravity.CENTER_HORIZONTAL
    } else {
        Gravity.CENTER
    }
    if (params.gravity != targetGravity) {
        contentFrame.layoutParams = params.apply { gravity = targetGravity }
    }
}

/** Buffer sizes of the panel trailer preview: a short clip needs far less than the streaming defaults. */
internal data class TrailerPreviewBufferParams(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val targetBufferBytes: Int,
    val prioritizeTimeOverSize: Boolean,
)

internal val TRAILER_PREVIEW_BUFFER = TrailerPreviewBufferParams(
    minBufferMs = 10_000,
    maxBufferMs = 15_000,
    bufferForPlaybackMs = 1_000,
    bufferForPlaybackAfterRebufferMs = 2_000,
    targetBufferBytes = 12 * 1024 * 1024,
    prioritizeTimeOverSize = false,
)

@OptIn(UnstableApi::class)
internal fun buildTrailerPreviewLoadControl(
    params: TrailerPreviewBufferParams = TRAILER_PREVIEW_BUFFER,
): DefaultLoadControl = DefaultLoadControl.Builder()
    .setAllocator(DefaultAllocator(/* trimOnReset = */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
    .setBufferDurationsMs(
        params.minBufferMs,
        params.maxBufferMs,
        params.bufferForPlaybackMs,
        params.bufferForPlaybackAfterRebufferMs,
    )
    .setTargetBufferBytes(params.targetBufferBytes)
    .setPrioritizeTimeOverSizeThresholds(params.prioritizeTimeOverSize)
    .build()
