package com.kino.puber.ui.feature.player.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
internal fun PlayerTitle(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = PlayerControlsMetrics.SideMargin,
                top = PlayerControlsMetrics.TopMargin,
                end = PlayerControlsMetrics.SideMargin,
            ),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = PlayerControlsMetrics.TitleTextSize,
                lineHeight = PlayerControlsMetrics.TitleLineHeight,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = PlayerControlsMetrics.SubtitleTextSize,
                    lineHeight = PlayerControlsMetrics.SubtitleLineHeight,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBTITLE_ALPHA),
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(top = PlayerControlsMetrics.TitleToSubtitleGap),
            )
        }
    }
}

private const val SUBTITLE_ALPHA = 0.7f
