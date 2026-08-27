package com.kino.puber.ui.feature.player.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.DpadScrollAxis
import com.kino.puber.core.ui.uikit.component.PositionFocusedItemInsideContentPadding
import com.kino.puber.core.ui.uikit.component.dpadScrollOptimization
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGrid
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemHorizontal
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.SectionTitleStyle
import com.kino.puber.ui.feature.player.model.EpisodeSeasonUIState
import com.kino.puber.ui.feature.player.model.EpisodesPanelUIState

private const val PANEL_ENTER_DURATION_MS = 240
private const val PANEL_EXIT_DURATION_MS = 180
private const val SCRIM_START = 0.55f

/** Whole cards the row shows at once. The card is measured from the screen, never fixed. */
private const val EPISODES_IN_ROW = 5
private const val EPISODE_ASPECT_WIDTH = 16f
private const val EPISODE_ASPECT_HEIGHT = 9f

/**
 * The padding at the ends of the row has to equal the spacing between cards. While it is wider,
 * no scroll of the list lands on a whole card and the neighbouring one keeps showing an edge.
 */
private val RowSpacing = 18.dp
private val RowEdgePadding = RowSpacing
private val HeaderGap = 12.dp
private val RowBottomPadding = 24.dp
private val HeaderFontSize = 14.sp
private val HeaderLineHeight = 18.sp
private val EpisodeCornerRadius = 12.dp
private val FullScreenPanelColor = Color(0xFF090B0F)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun EpisodesPanel(
    visible: Boolean,
    episodes: VideoGridUIState?,
    modifier: Modifier = Modifier,
    initialFocusedItemId: Int? = null,
    onEpisodeSelected: (VideoItemUIState) -> Unit,
    onEpisodeContextMenu: ((VideoItemUIState) -> Unit)? = null,
    allowFocusExit: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FullScreenPanelColor.copy(alpha = 0.90f))
                .padding(top = 32.dp)
                .focusProperties {
                    onExit = {
                        if (!allowFocusExit) cancelFocusChange()
                    }
                }
                .focusGroup(),
        ) {
            if (episodes != null) {
                VideoGrid(
                    state = episodes,
                    initialFocusedItemId = initialFocusedItemId,
                    onItemClick = onEpisodeSelected,
                    onItemContextMenu = onEpisodeContextMenu,
                    enableTopSideGradient = true,
                )
            }
        }
    }
}

/**
 * Row of episodes that hangs over the picture while playback continues behind it.
 *
 * There is no sheet under the cards: the row is only as tall as it needs to be, and everything the
 * player used to draw behind it - the glass, its rim, the height taken from the screen rather than
 * from the content - is gone. What separates the cards from the picture is the scrim alone.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PlayerEpisodesPanel(
    visible: Boolean,
    episodes: EpisodesPanelUIState?,
    modifier: Modifier = Modifier,
    initialFocusedItemId: Int? = null,
    onEpisodeSelected: (VideoItemUIState) -> Unit,
    onEpisodeContextMenu: ((VideoItemUIState) -> Unit)? = null,
    onDismiss: () -> Unit,
    allowFocusExit: Boolean = false,
) {
    val currentSeason = episodes?.seasons
        ?.firstOrNull { season -> season.episodes.any { it.id == initialFocusedItemId } }
        ?: episodes?.seasons?.firstOrNull()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // The card is measured from the width that is left, so the row always holds whole cards.
        val episodeWidth =
            (maxWidth - RowEdgePadding * 2 - RowSpacing * (EPISODES_IN_ROW - 1)) / EPISODES_IN_ROW
        val episodeHeight = episodeWidth * (EPISODE_ASPECT_HEIGHT / EPISODE_ASPECT_WIDTH)

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(PANEL_ENTER_DURATION_MS)),
            exit = fadeOut(tween(PANEL_EXIT_DURATION_MS)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(episodesPanelScrim()),
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            enter = fadeIn(tween(PANEL_ENTER_DURATION_MS)) + slideInVertically(
                animationSpec = tween(PANEL_ENTER_DURATION_MS, easing = FastOutSlowInEasing),
                initialOffsetY = { it },
            ),
            exit = fadeOut(tween(PANEL_EXIT_DURATION_MS)) + slideOutVertically(
                animationSpec = tween(PANEL_EXIT_DURATION_MS, easing = FastOutSlowInEasing),
                targetOffsetY = { it },
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusProperties {
                        onExit = {
                            if (!allowFocusExit) cancelFocusChange()
                        }
                    }
                    .focusGroup()
                    .padding(bottom = RowBottomPadding),
                verticalArrangement = Arrangement.spacedBy(HeaderGap),
            ) {
                EpisodesHeader(season = currentSeason)

                currentSeason?.let { season ->
                    EpisodeRow(
                        visible = visible,
                        season = season,
                        episodeHeight = episodeHeight,
                        currentEpisodeId = initialFocusedItemId,
                        onUp = onDismiss,
                        onEpisodeSelected = onEpisodeSelected,
                        onEpisodeContextMenu = onEpisodeContextMenu,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodesHeader(
    season: EpisodeSeasonUIState?,
) {
    val episodesTitle = androidx.compose.ui.res.stringResource(R.string.player_episodes_title)
    Text(
        text = season?.title?.let {
            androidx.compose.ui.res.stringResource(R.string.player_episodes_season_title, episodesTitle, it)
        } ?: episodesTitle,
        modifier = Modifier
            // Follows the row padding: a heading off the card grid reads as a mistake.
            .padding(horizontal = RowEdgePadding),
        style = SectionTitleStyle.copy(
            fontSize = HeaderFontSize,
            lineHeight = HeaderLineHeight,
        ),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun EpisodeRow(
    visible: Boolean,
    season: EpisodeSeasonUIState,
    episodeHeight: Dp,
    currentEpisodeId: Int?,
    onUp: () -> Unit,
    onEpisodeSelected: (VideoItemUIState) -> Unit,
    onEpisodeContextMenu: ((VideoItemUIState) -> Unit)?,
) {
    val targetIndex = season.episodes.indexOfFirst { it.id == currentEpisodeId }
        .takeIf { it >= 0 }
        ?: 0
    val targetItemId = season.episodes.getOrNull(targetIndex)?.id
    val targetFocusRequester = remember(season.number, targetItemId) { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(visible, season.number, targetItemId) {
        if (visible && targetItemId != null) {
            listState.scrollToItem(targetIndex)
            withFrameNanos { frameTimeNanos -> frameTimeNanos }
            runCatching { targetFocusRequester.requestFocus() }
        }
    }

    PositionFocusedItemInsideContentPadding(padding = RowEdgePadding) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .dpadScrollOptimization(axis = DpadScrollAxis.Horizontal),
            contentPadding = PaddingValues(horizontal = RowEdgePadding),
            horizontalArrangement = Arrangement.spacedBy(RowSpacing),
        ) {
            itemsIndexed(season.episodes, key = { _, item -> item.id }) { index, item ->
                val isCurrentEpisode = item.id == currentEpisodeId
                VideoItemHorizontal(
                    state = item,
                    itemHeight = episodeHeight,
                    titleMaxLines = 1,
                    onClick = { onEpisodeSelected(item) },
                    onContextMenu = onEpisodeContextMenu?.let { callback -> { callback(item) } },
                    modifier = Modifier
                        .onPreviewKeyEvent { event ->
                            if (event.key != Key.DirectionUp) {
                                false
                            } else {
                                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                    onUp()
                                }
                                true
                            }
                        }
                        .then(
                            if (index == targetIndex) {
                                Modifier.focusRequester(targetFocusRequester)
                            } else {
                                Modifier
                            },
                        )
                        .semantics { selected = isCurrentEpisode }
                        .then(
                            if (isCurrentEpisode) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(EpisodeCornerRadius),
                                )
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun episodesPanelScrim(): Brush {
    val scrim = MaterialTheme.colorScheme.scrim
    return remember(scrim) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to scrim.copy(alpha = 0f),
                SCRIM_START to scrim.copy(alpha = 0.04f),
                1f to scrim.copy(alpha = 0.30f),
            ),
        )
    }
}
