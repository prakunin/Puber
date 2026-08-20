package com.kino.puber.ui.feature.player.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private const val PANEL_WIDTH_FRACTION = 0.34f
private const val PANEL_GRADIENT_START = 0.52f
private const val PANEL_GRADIENT_ALPHA = 0.42f
private const val PANEL_ENTER_DURATION_MS = 210
private const val PANEL_EXIT_DURATION_MS = 160
private const val PANEL_SLIDE_DIVISOR = 7

private val PanelMaxWidth = 420.dp
private val PanelCornerRadius = 20.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PlayerSidePanel(
    visible: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val panelWidth = minOf(maxWidth * PANEL_WIDTH_FRACTION, PanelMaxWidth)

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(PANEL_ENTER_DURATION_MS)),
            exit = fadeOut(tween(PANEL_EXIT_DURATION_MS)),
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(playerSidePanelScrim()),
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(panelWidth)
                .fillMaxHeight(),
            enter = fadeIn(tween(PANEL_ENTER_DURATION_MS)) + slideInHorizontally(
                animationSpec = tween(PANEL_ENTER_DURATION_MS, easing = FastOutSlowInEasing),
                initialOffsetX = { it / PANEL_SLIDE_DIVISOR },
            ),
            exit = fadeOut(tween(PANEL_EXIT_DURATION_MS)) + slideOutHorizontally(
                animationSpec = tween(PANEL_EXIT_DURATION_MS, easing = FastOutSlowInEasing),
                targetOffsetX = { it / PANEL_SLIDE_DIVISOR },
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .playerGlass(
                        shape = RoundedCornerShape(
                            topStart = PanelCornerRadius,
                            bottomStart = PanelCornerRadius,
                        ),
                        level = PlayerGlass.Strong,
                        elevation = 20.dp,
                    )
                    .focusProperties { onExit = { cancelFocusChange() } }
                    .focusGroup()
                    .padding(start = 28.dp, top = 32.dp, end = 32.dp, bottom = 28.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 18.dp),
                )
                content()
            }
        }
    }
}

@Composable
internal fun PlayerPanelSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 12.dp, top = 12.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
    )
}

@Composable
internal fun PlayerPanelItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    trailingText: String? = null,
    supportingText: String? = null,
    focusRequester: FocusRequester? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .semantics { this.selected = selected },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                Color.Transparent
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
            pressedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
            pressedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!supportingText.isNullOrBlank()) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.68f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!trailingText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.76f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun PlayerPanelReadOnlyRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun playerSidePanelScrim(): Brush {
    val scrim = MaterialTheme.colorScheme.scrim
    return Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            PANEL_GRADIENT_START to Color.Transparent,
            1f to scrim.copy(alpha = PANEL_GRADIENT_ALPHA),
        ),
    )
}
