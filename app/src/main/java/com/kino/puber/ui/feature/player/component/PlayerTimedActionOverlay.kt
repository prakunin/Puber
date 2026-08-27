package com.kino.puber.ui.feature.player.component

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.ui.feature.player.model.PlayerCountdowns

private const val TIMED_ACTION_ENTER_MS = 190
private const val TIMED_ACTION_EXIT_MS = 140

private const val TIMED_ACTION_LIFT_MS = 220
private val TimedActionMaxWidth = 300.dp
private val TimedActionBottomPadding = 48.dp

// The chip paints its own near-opaque bed: a translucent white pill washes out over bright frames.
private const val TIMED_ACTION_BED_ALPHA = 0.86f
// Flat, not a gradient: the leading edge IS the countdown, so it has to be a hard step against the
// bed. A fill that fades towards its edge has no readable end at all.
private const val TIMED_ACTION_PROGRESS_ALPHA = 0.55f
private const val TIMED_ACTION_ICON_IDLE_ALPHA = 0.82f
private const val TIMED_ACTION_HALO_ALPHA = 0.70f
/**
 * Mixed into the plate's own base colour once, so the stroke is opaque by the time it is drawn.
 * A translucent hairline takes the colour of whatever it lies over, and the fill crossing under it
 * turned one border into two: lilac and bright along the filled part, near-black over the bed.
 */
private const val TIMED_ACTION_BORDER_ALPHA = 0.45f

/**
 * Focus is the border going bright, not the plate going grey.
 *
 * A wash over the whole plate lands on the fill as well and takes the leading edge with it: the
 * step from painted to unpainted dropped from thirty-fold to under three. The border carries the
 * focus on its own now that it is one solid colour all the way round.
 */
private const val TIMED_ACTION_BORDER_FOCUS_ALPHA = 0.85f
private val TimedActionHaloOffset = 0.75.dp
private val TimedActionBorderWidth = 1.dp
private val TimedActionCornerRadius = 12.dp

/**
 * The gap between the label and its counter. Non-breaking: the plate is only as wide as its text,
 * and a number that wrapped onto a second line would take the plate's height with it.
 */
private const val TIMED_ACTION_LABEL_GAP = '\u00A0'

/**
 * A figure space, exactly the width of a digit. It holds the counter's column open without
 * printing anything, which a leading zero would also do - but a zero reads as part of the number.
 */
private const val TIMED_ACTION_DIGIT_SPACE = '\u2007'

/**
 * Label with the seconds left, in a column as wide as the counter will ever need.
 *
 * The row hangs off the right edge of the screen and the plate grows leftwards, so a column that
 * narrows halfway through a two-digit countdown would drag the label sideways under the eye that
 * is reading it. Padding the short values keeps the plate one width for the whole wait.
 */
internal fun timedActionLabel(label: String, countdown: Int, totalSeconds: Int): String {
    if (totalSeconds <= 0) return label
    val value = countdown.coerceIn(0, totalSeconds)
    val digits = value.toString().padStart(totalSeconds.toString().length, TIMED_ACTION_DIGIT_SPACE)
    return "$label$TIMED_ACTION_LABEL_GAP$digits"
}

private data class TimedActionPresentation(
    /**
     * Label and counter as one string, captured when the prompt goes up and updated on every tick.
     * Keeping it in the state means the copy that is animating out keeps the number it went out on.
     */
    val actionLabel: String,
)

@Composable
internal fun PlayerTimedActionOverlay(
    visible: Boolean,
    actionLabel: String,
    cancelLabel: String,
    countdown: Int,
    totalSeconds: Int,
    focusPrimaryAction: Boolean,
    /** Changes when a different prompt takes over without the plate ever going away. */
    modifier: Modifier = Modifier,
    promptKey: Any? = null,
    debugName: String = "timed_action",
    compactCancel: Boolean = false,
    onAction: () -> Unit,
    onCancel: () -> Unit,
    bottomInset: Dp = 0.dp,
) {
    BackHandler(enabled = visible, onBack = onCancel)

    val lift by animateDpAsState(
        targetValue = bottomInset,
        animationSpec = tween(TIMED_ACTION_LIFT_MS),
        label = "timed_action_lift",
    )

    val primaryFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) {
            runCatching {
                if (focusPrimaryAction) primaryFocusRequester.requestFocus() else cancelFocusRequester.requestFocus()
            }
        }
    }

    // One run of the bar per prompt, not one per tick: the countdown only says where to start it.
    val fill = remember(promptKey) { Animatable(0f) }
    LaunchedEffect(visible, promptKey) {
        if (!visible) return@LaunchedEffect
        // Ticks, not seconds: zero gets one of its own before the action runs, and the bar measures
        // the whole wait, so that tick is part of it.
        val totalTicks = totalSeconds + PlayerCountdowns.ZERO_TICK_SEC
        val remainingTicks = countdown.coerceIn(0, totalSeconds) + PlayerCountdowns.ZERO_TICK_SEC
        // Where the bar would already be. A prompt that goes up part-way through its countdown —
        // or a recomposition arriving late — starts from there instead of replaying the beginning.
        fill.snapTo((totalTicks - remainingTicks).toFloat() / totalTicks)
        fill.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = (remainingTicks * PlayerCountdowns.TICK_MS).toInt(),
                easing = LinearEasing,
            ),
        )
    }
    val context = LocalContext.current
    val debugState = remember(context, promptKey, debugName) {
        TimedActionDrawDebugState(
            context = context,
            prompt = debugName,
            instance = SystemClock.elapsedRealtime(),
        )
    }
    LaunchedEffect(visible, promptKey) {
        debugState.recordLifecycle(
            event = if (visible) "shown" else "hidden",
            countdown = countdown,
            totalSeconds = totalSeconds,
        )
    }

    // The plate now prints the seconds, but a printed number is no use to a screen reader either,
    // and the cancel button never carried one. The seconds stay in the state on both buttons,
    // because either of them can hold the focus, and state changes are what get announced.
    val secondsLeft = pluralStringResource(
        R.plurals.player_timed_action_seconds_left,
        countdown.coerceAtLeast(0),
        countdown.coerceAtLeast(0),
    )

    val presentation = if (visible) {
        TimedActionPresentation(timedActionLabel(actionLabel, countdown, totalSeconds))
    } else {
        null
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = presentation,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = TimedActionBottomPadding, bottom = TimedActionBottomPadding + lift)
                .widthIn(max = TimedActionMaxWidth),
            transitionSpec = {
                (fadeIn(tween(TIMED_ACTION_ENTER_MS)) + slideInVertically(
                    animationSpec = tween(TIMED_ACTION_ENTER_MS),
                    initialOffsetY = { it / 3 },
                )).togetherWith(
                    fadeOut(tween(TIMED_ACTION_EXIT_MS)) + slideOutVertically(
                        animationSpec = tween(TIMED_ACTION_EXIT_MS),
                        targetOffsetY = { it / 3 },
                    )
                )
            },
            contentKey = { it != null },
            label = "player_timed_action",
        ) { displayedPresentation ->
            displayedPresentation?.let { displayed ->
                Row(
                    modifier = Modifier.wrapContentWidth(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimedActionButton(
                        label = cancelLabel,
                        onClick = onCancel,
                        focusRequester = cancelFocusRequester,
                        emphasized = false,
                        iconOnly = compactCancel,
                        timeLeft = secondsLeft,
                    )
                    TimedActionButton(
                        label = displayed.actionLabel,
                        onClick = onAction,
                        focusRequester = primaryFocusRequester,
                        emphasized = true,
                        timeLeft = secondsLeft,
                        progress = fill::value,
                        onBoundsChanged = debugState::updateBounds,
                        onProgressDrawn = { width, height, progressValue, cornerRadiusPx ->
                            debugState.recordDraw(
                                countdown = countdown,
                                totalSeconds = totalSeconds,
                                width = width,
                                height = height,
                                progress = progressValue,
                                cornerRadiusPx = cornerRadiusPx,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimedActionButton(
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    emphasized: Boolean,
    /** What the fill shows and cannot say: the time left, for the screen reader. */
    timeLeft: String,
    iconOnly: Boolean = false,
    progress: (() -> Float)? = null,
    onBoundsChanged: ((Rect) -> Unit)? = null,
    onProgressDrawn: ((width: Float, height: Float, progress: Float, cornerRadiusPx: Float) -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TimedActionCornerRadius)
    val bedColor = MaterialTheme.colorScheme.surface.copy(alpha = TIMED_ACTION_BED_ALPHA)
    // The bed is near-black, so a hairline keeps the pill's shape on a dark frame too.
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    fun hairline(alpha: Float) = Border(
        border = BorderStroke(
            width = TimedActionBorderWidth,
            color = onSurface.copy(alpha = alpha).compositeOver(surface),
        ),
        shape = shape,
    )
    val bedBorder = hairline(TIMED_ACTION_BORDER_ALPHA)
    val focusedBedBorder = hairline(TIMED_ACTION_BORDER_FOCUS_ALPHA)
    val progressFill = MaterialTheme.colorScheme.primary.copy(alpha = TIMED_ACTION_PROGRESS_ALPHA)

    Surface(
        onClick = onClick,
        modifier = Modifier
            // The emphasized chip has no minimum: it is exactly as wide as its label, so the padding
            // reads the same on both sides. A minimum wider than the text pushed the label left and
            // left dead space against the right edge — and the fill had to cross that dead space too.
            .then(
                when {
                    iconOnly -> Modifier.widthIn(min = 48.dp)
                    emphasized -> Modifier
                    else -> Modifier.widthIn(min = 88.dp)
                },
            )
            .then(
                if (emphasized) {
                    Modifier.shadow(
                        elevation = 14.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.58f),
                        spotColor = Color.Black.copy(alpha = 0.76f),
                    )
                } else {
                    Modifier
                },
            )
            .semantics { stateDescription = timeLeft }
            .onFocusChanged { focused = it.isFocused }
            .onGloballyPositioned { coordinates ->
                onBoundsChanged?.invoke(coordinates.boundsInWindow())
            }
            .focusRequester(focusRequester),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        border = if (iconOnly) {
            ClickableSurfaceDefaults.border()
        } else {
            ClickableSurfaceDefaults.border(
                border = bedBorder,
                focusedBorder = focusedBedBorder,
                pressedBorder = focusedBedBorder,
            )
        },
        // Every state is painted by the content below, so the chip reads the same over any frame.
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
            pressedContainerColor = Color.Transparent,
            pressedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        if (iconOnly) {
            // Bare glyph, no plate: a dark halo keeps it legible over bright frames.
            HaloCloseIcon(
                label = label,
                tint = if (focused) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = TIMED_ACTION_ICON_IDLE_ALPHA)
                },
            )
        } else {
            Box(
                // Read in the draw phase: the fill advances every frame and must not drag a
                // recomposition of the row along with it.
                modifier = Modifier.drawBehind {
                    drawRect(color = bedColor)
                    if (progress != null) {
                        val progressValue = progress().coerceIn(0f, 1f)
                        drawRect(
                            color = progressFill,
                            size = Size(
                                width = size.width * progressValue,
                                height = size.height,
                            ),
                        )
                        onProgressDrawn?.invoke(
                            size.width,
                            size.height,
                            progressValue,
                            TimedActionCornerRadius.toPx(),
                        )
                    }
                },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                    // 10 dp, not 12: the plate is 75 px tall on a 1080p screen instead of 83, which
                    // sits better against the video without crowding the label.
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private class TimedActionDrawDebugState(
    private val context: android.content.Context,
    private val prompt: String,
    private val instance: Long,
) {
    private var bounds: Rect? = null
    private var latestProgress: Float? = null
    private var latestCountdown: Int = -1
    private var latestTotalSeconds: Int = -1
    private var latestCornerRadiusPx: Float? = null
    private var lastProgressBucket: Int = -1

    fun updateBounds(value: Rect) {
        bounds = value
    }

    fun recordDraw(
        countdown: Int,
        totalSeconds: Int,
        width: Float,
        height: Float,
        progress: Float,
        cornerRadiusPx: Float,
    ) {
        latestProgress = progress
        latestCountdown = countdown
        latestTotalSeconds = totalSeconds
        latestCornerRadiusPx = cornerRadiusPx
        val bucket = (progress * DEBUG_PROGRESS_BUCKETS).toInt()
        if (bucket == lastProgressBucket) return
        lastProgressBucket = bucket
        record(
            event = "draw",
            countdown = countdown,
            totalSeconds = totalSeconds,
            progress = progress,
            fallbackWidth = width,
            fallbackHeight = height,
        )
    }

    fun recordLifecycle(event: String, countdown: Int, totalSeconds: Int) {
        record(
            event = event,
            countdown = countdown,
            totalSeconds = totalSeconds,
            progress = latestProgress,
        )
    }

    private fun record(
        event: String,
        countdown: Int,
        totalSeconds: Int,
        progress: Float?,
        fallbackWidth: Float? = null,
        fallbackHeight: Float? = null,
    ) {
        val currentBounds = bounds
        val left = currentBounds?.left
        val top = currentBounds?.top
        val right = currentBounds?.right ?: left?.plus(fallbackWidth ?: 0f)
        val bottom = currentBounds?.bottom ?: top?.plus(fallbackHeight ?: 0f)
        TimedActionDebugTrace.record(
            context = context,
            snapshot = TimedActionDebugSnapshot(
                event = event,
                prompt = prompt,
                instance = instance,
                countdown = countdown.takeIf { it >= 0 } ?: latestCountdown,
                totalSeconds = totalSeconds.takeIf { it >= 0 } ?: latestTotalSeconds,
                progress = progress,
                buttonLeft = left,
                buttonTop = top,
                buttonRight = right,
                buttonBottom = bottom,
                fillLeft = left,
                fillTop = top,
                fillRight = if (left != null && right != null && progress != null) {
                    left + (right - left) * progress
                } else {
                    null
                },
                fillBottom = bottom,
                cornerRadiusPx = latestCornerRadiusPx,
            ),
        )
    }

    private companion object {
        const val DEBUG_PROGRESS_BUCKETS = 20
    }
}

@Composable
private fun HaloCloseIcon(label: String, tint: Color) {
    Box(
        modifier = Modifier
            .padding(12.dp)
            .size(24.dp),
    ) {
        val halo = Color.Black.copy(alpha = TIMED_ACTION_HALO_ALPHA)
        listOf(
            -TimedActionHaloOffset to -TimedActionHaloOffset,
            TimedActionHaloOffset to -TimedActionHaloOffset,
            -TimedActionHaloOffset to TimedActionHaloOffset,
            TimedActionHaloOffset to TimedActionHaloOffset,
        ).forEach { (dx, dy) ->
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = halo,
                modifier = Modifier
                    .offset(x = dx, y = dy)
                    .fillMaxSize(),
            )
        }
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
