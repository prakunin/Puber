package com.kino.puber.ui.feature.player.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private val AboutTextSize = 13.sp
private val AboutLineHeight = 19.sp

/** A press moves whole lines, so the prose never resumes halfway through one. */
private const val ABOUT_LINES_PER_PRESS = 4

/**
 * The item's own description, opened by the About button on the left of the controls row.
 *
 * The plot arrives with the item and used to have nowhere to go once playback started; the
 * viewer had to leave the player to read what they were watching. The whole text is here and it
 * scrolls — nothing is dropped to keep the panel short.
 */
@Composable
internal fun PlayerAboutPanel(
    visible: Boolean,
    isMovie: Boolean,
    description: String?,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val lineStep = with(LocalDensity.current) {
        (AboutLineHeight * ABOUT_LINES_PER_PRESS).toDp()
    }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        // The panel is still composed while it fades out, so an opening that follows one closely
        // would otherwise resume where the last reader stopped rather than at the first line.
        scrollState.scrollTo(0)
        withFrameNanos { frameTimeNanos -> frameTimeNanos }
        runCatching { focusRequester.requestFocus() }
    }

    PlayerSidePanel(
        visible = visible && !description.isNullOrBlank(),
        title = stringResource(aboutLabel(isMovie)),
        modifier = modifier,
    ) {
        PlayerPanelScrollBox(
            focusRequester = focusRequester,
            scrollStep = lineStep,
            scrollState = scrollState,
        ) {
            Text(
                text = description.orEmpty(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = AboutTextSize,
                    lineHeight = AboutLineHeight,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
