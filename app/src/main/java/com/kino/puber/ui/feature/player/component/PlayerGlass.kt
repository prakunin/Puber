@file:Suppress("MagicNumber")

package com.kino.puber.ui.feature.player.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

/**
 * A lightweight glass-like material for overlays drawn above the player's SurfaceView.
 *
 * Compose backdrop blur cannot sample video rendered by SurfaceView, so this material builds
 * depth from translucent tonal layers and a directional highlight instead. It remains cheap
 * enough for TV hardware and keeps the moving picture visible below the controls.
 */
internal enum class PlayerGlass {
    Soft,
    Regular,
    Strong,
}

@Composable
internal fun Modifier.playerGlass(
    shape: Shape,
    level: PlayerGlass = PlayerGlass.Regular,
    elevation: Dp = 0.dp,
): Modifier = composed {
    val colors = MaterialTheme.colorScheme
    val surfaceAlpha = when (level) {
        PlayerGlass.Soft -> 0.42f
        PlayerGlass.Regular -> 0.58f
        PlayerGlass.Strong -> 0.72f
    }
    val highlightAlpha = when (level) {
        PlayerGlass.Soft -> 0.08f
        PlayerGlass.Regular -> 0.12f
        PlayerGlass.Strong -> 0.15f
    }
    val background = Brush.linearGradient(
        colorStops = arrayOf(
            0f to colors.onSurface.copy(alpha = highlightAlpha)
                .compositeOver(colors.surface.copy(alpha = surfaceAlpha)),
            0.38f to colors.primary.copy(alpha = 0.055f)
                .compositeOver(colors.surface.copy(alpha = surfaceAlpha + 0.04f)),
            1f to colors.surface.copy(alpha = (surfaceAlpha + 0.12f).coerceAtMost(0.88f)),
        ),
    )
    val rim = Brush.linearGradient(
        colorStops = arrayOf(
            0f to colors.onSurface.copy(alpha = 0.32f),
            0.42f to colors.primary.copy(alpha = 0.18f),
            1f to colors.onSurface.copy(alpha = 0.07f),
        ),
    )

    this
        .then(
            if (elevation > 0.dp) {
                Modifier.shadow(
                    elevation = elevation,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.60f),
                    spotColor = Color.Black.copy(alpha = 0.82f),
                )
            } else {
                Modifier
            },
        )
        .background(background, shape)
        .border(width = 1.dp, brush = rim, shape = shape)
}
