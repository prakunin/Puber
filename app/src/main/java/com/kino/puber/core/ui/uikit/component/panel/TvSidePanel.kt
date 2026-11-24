package com.kino.puber.core.ui.uikit.component.panel

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private const val PANEL_ENTER_DURATION_MS = 210

/**
 * The glass card of a right-edge side panel with its title.
 *
 * The card is measured by what is in it, not by the screen: [IntrinsicSize.Max] takes the width
 * of its longest row and the height wraps the content, clamped to [minWidth] and [maxWidth] so a
 * long title cannot make it absurdly narrow or wide. Content therefore cannot be a lazy list,
 * which has no intrinsic width. Focus is trapped in the card: the arrows do not leave it.
 */
@Composable
internal fun TvSidePanelSurface(
    title: String,
    modifier: Modifier = Modifier,
    minWidth: Dp = 240.dp,
    maxWidth: Dp = 420.dp,
    cornerRadius: Dp = 20.dp,
    padding: PaddingValues = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .widthIn(min = minWidth, max = maxWidth)
            .width(IntrinsicSize.Max)
            // Pages differ in height, and without this the panel snaps on every step in and out
            // of a page.
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = PANEL_ENTER_DURATION_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
            .panelGlass(
                shape = RoundedCornerShape(topStart = cornerRadius, bottomStart = cornerRadius),
                level = PanelGlass.Strong,
                elevation = 20.dp,
            )
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup()
            .padding(padding),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        content()
    }
}

@Composable
internal fun TvSidePanelSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
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
internal fun TvSidePanelItem(
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
                .heightIn(min = 40.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
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
                        color = LocalContentColor.current.copy(alpha = 0.68f),
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
                    color = LocalContentColor.current.copy(alpha = 0.76f),
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
