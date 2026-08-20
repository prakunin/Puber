package com.kino.puber.ui.feature.details.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Still at the top before anything moves, so the opening can be read. */
private const val SCROLL_START_DELAY_MS = 3_000L

/** Still at the bottom before the snap back. */
private const val SCROLL_END_PAUSE_MS = 2_000L

/** Slow enough to read along with. */
private const val SCROLL_SPEED_DP_PER_SECOND = 18F

private const val MILLIS_PER_SECOND = 1_000F

/** Enough to soften a clipped line without eating one. */
private val EDGE_FADE_HEIGHT = 10.dp

/**
 * A block that scrolls itself when it does not fit, and loops.
 *
 * The remote never drives this: nothing inside is focusable, and on a screen where LEFT and RIGHT
 * move between buttons there is nothing to spare for scrolling text. So it either fits, or it shows
 * itself in turn.
 *
 * It holds a column rather than one string because the plot is not the only thing worth reading and
 * not the only thing that runs out of room. A series' screen shares its height with a season list,
 * and the facts and the credits ride along inside here rather than being cut from the screen.
 */
@Composable
internal fun SelfScrollingColumn(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // `maxValue` is how much of the text is out of sight: zero means it fits, and nothing runs.
    val overflow = scrollState.maxValue
    LaunchedEffect(enabled, overflow) {
        if (!enabled || overflow <= 0) {
            scrollState.scrollTo(0)
            return@LaunchedEffect
        }
        while (true) {
            scrollState.scrollTo(0)
            delay(SCROLL_START_DELAY_MS)
            val distanceDp = with(density) { overflow.toDp().value }
            val durationMs = (distanceDp / SCROLL_SPEED_DP_PER_SECOND * MILLIS_PER_SECOND).toInt()
            scrollState.animateScrollTo(
                value = overflow,
                animationSpec = tween(durationMillis = durationMs, easing = LinearEasing),
            )
            delay(SCROLL_END_PAUSE_MS)
        }
    }

    Box(modifier = modifier.clipToBounds()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Faded by taking the text's own alpha away at the edges rather than by painting a
                // band of surface colour over it: the block sits on top of the artwork, and a band
                // would be a rectangle of flat colour across the picture.
                .verticalFadingEdges(enabled = overflow > 0)
                .verticalScroll(scrollState, enabled = false),
            content = content,
        )
    }
}

/** A hard edge across the middle of a line reads as a rendering fault; this says it continues. */
private fun Modifier.verticalFadingEdges(enabled: Boolean): Modifier {
    if (!enabled) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = EDGE_FADE_HEIGHT.toPx()
            if (size.height <= fade * 2) return@drawWithContent
            val stop = fade / size.height
            drawRect(
                brush = Brush.verticalGradient(
                    0F to Color.Transparent,
                    stop to Color.Black,
                    1F - stop to Color.Black,
                    1F to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}
