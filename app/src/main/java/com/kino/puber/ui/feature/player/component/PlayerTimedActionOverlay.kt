package com.kino.puber.ui.feature.player.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private const val TIMED_ACTION_ENTER_MS = 190
private const val TIMED_ACTION_EXIT_MS = 140
private val TimedActionMaxWidth = 300.dp

private data class TimedActionPresentation(
    val actionLabel: String,
    val countdown: Int,
)

@Composable
internal fun PlayerTimedActionOverlay(
    visible: Boolean,
    actionLabel: String,
    cancelLabel: String,
    countdown: Int,
    totalSeconds: Int,
    focusPrimaryAction: Boolean,
    compactCancel: Boolean = false,
    onAction: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = visible, onBack = onCancel)

    val primaryFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) {
            runCatching {
                if (focusPrimaryAction) primaryFocusRequester.requestFocus() else cancelFocusRequester.requestFocus()
            }
        }
    }

    val presentation = if (visible) TimedActionPresentation(actionLabel, countdown) else null

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = presentation,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 48.dp, bottom = 48.dp)
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
                    )
                    TimedActionButton(
                        label = displayed.actionLabel,
                        onClick = onAction,
                        focusRequester = primaryFocusRequester,
                        emphasized = true,
                        progress = (1f - displayed.countdown.toFloat() / totalSeconds).coerceIn(0f, 1f),
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
    iconOnly: Boolean = false,
    progress: Float? = null,
) {
    val animatedProgress = progress?.let {
        animateFloatAsState(
            targetValue = it,
            animationSpec = tween(1000, easing = LinearEasing),
            label = "player_timed_action_progress",
        )
    }
    val shape = RoundedCornerShape(12.dp)
    val progressFill = Brush.horizontalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
        ),
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = if (emphasized) 144.dp else if (iconOnly) 48.dp else 88.dp)
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
            .focusRequester(focusRequester),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            } else {
                Color.Transparent
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        if (iconOnly) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = label,
                modifier = Modifier
                    .padding(12.dp)
                    .size(24.dp),
            )
        } else {
            Box(
                modifier = Modifier.drawBehind {
                    animatedProgress?.value?.let { progressValue ->
                        drawRect(
                            brush = progressFill,
                            size = Size(
                                width = size.width * progressValue.coerceIn(0f, 1f),
                                height = size.height,
                            ),
                        )
                    }
                },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
    }
}
