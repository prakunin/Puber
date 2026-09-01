package com.kino.puber.ui.feature.player.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.ui.feature.player.model.PlayerAboutUIState

private val AboutTextSize = 13.sp
private val AboutLineHeight = 19.sp
private val AboutTitleSize = 17.sp
private val AboutTitleLineHeight = 22.sp
private val AboutFactSize = 12.sp
private val AboutFactLineHeight = 17.sp

private const val ABOUT_FACT_ALPHA = 0.72f

/** A press moves whole lines, so the prose never resumes halfway through one. */
private const val ABOUT_LINES_PER_PRESS = 4

/** Matches the start inset [PlayerPanelSectionHeader] carries, so headings and text share an edge. */
private val AboutTextInset = 12.dp

/**
 * What is playing, opened by the About button on the left of the controls row.
 *
 * The plot arrives with the item and used to have nowhere to go once playback started; the
 * viewer had to leave the player to read what they were watching. It is all here — the facts, the
 * plot, who made it and who is in it — and it scrolls, so nothing is dropped to keep the panel
 * short. Sections the API said nothing about are not drawn at all rather than printed empty.
 */
@Composable
internal fun PlayerAboutPanel(
    visible: Boolean,
    isMovie: Boolean,
    about: PlayerAboutUIState,
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
        visible = visible && !about.isEmpty,
        title = stringResource(aboutLabel(isMovie)),
        modifier = modifier,
    ) {
        PlayerPanelScrollBox(
            focusRequester = focusRequester,
            scrollStep = lineStep,
            scrollState = scrollState,
        ) {
            AboutHeader(about)
            about.description?.let { plot -> AboutProse(plot) }
            about.director?.let { director ->
                AboutSection(stringResource(R.string.player_about_section_director), director)
            }
            about.cast?.let { cast ->
                AboutSection(stringResource(R.string.player_about_section_cast), cast)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * The name of the thing and the three lines that place it.
 *
 * The controls print the same name at the far top-left corner, but the panel's own heading only
 * says whether this is a film or a series; naming it here is what makes the column readable as one
 * card rather than facts about something the reader has to look away to identify.
 */
@Composable
private fun AboutHeader(about: PlayerAboutUIState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AboutTextInset, end = AboutTextInset, bottom = 4.dp),
    ) {
        Text(
            text = about.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = AboutTitleSize,
                lineHeight = AboutTitleLineHeight,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        listOf(about.metaLine, about.genresLine, about.ratingsLine)
            .filter(String::isNotEmpty)
            .forEach { fact -> AboutFact(fact) }
    }
}

@Composable
private fun AboutFact(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = AboutFactSize,
            lineHeight = AboutFactLineHeight,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ABOUT_FACT_ALPHA),
        modifier = Modifier.padding(top = 3.dp),
    )
}

@Composable
private fun AboutProse(text: String, topPadding: Dp = 10.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = AboutTextSize,
            lineHeight = AboutLineHeight,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AboutTextInset, end = AboutTextInset, top = topPadding),
    )
}

/**
 * A credit under its own heading. The header's own 8 dp sat too close to the last line of the
 * prose above it — at this text size that gap read as another line break, not a new section.
 */
@Composable
private fun AboutSection(title: String, text: String) {
    Spacer(modifier = Modifier.height(6.dp))
    PlayerPanelSectionHeader(text = title)
    AboutProse(text = text, topPadding = 0.dp)
}
