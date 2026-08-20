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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.DpadScrollAxis
import com.kino.puber.core.ui.uikit.component.dpadScrollOptimization
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGrid
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemHorizontal
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.ui.feature.player.model.EpisodeSeasonUIState
import com.kino.puber.ui.feature.player.model.EpisodesPanelUIState

private const val PANEL_HEIGHT_FRACTION = 0.64f
private const val PANEL_ENTER_DURATION_MS = 240
private const val PANEL_EXIT_DURATION_MS = 180
private const val SCRIM_START = 0.55f

private val PanelMinHeight = 300.dp
private val PanelMaxHeight = 400.dp
private val EpisodeCornerRadius = 12.dp
private val FullScreenPanelColor = Color(0xFF090B0F)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun EpisodesPanel(
    visible: Boolean,
    episodes: VideoGridUIState?,
    initialFocusedItemId: Int? = null,
    onEpisodeSelected: (VideoItemUIState) -> Unit,
    onEpisodeContextMenu: ((VideoItemUIState) -> Unit)? = null,
    allowFocusExit: Boolean = false,
    modifier: Modifier = Modifier,
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

/** Bottom sheet used inside the player while playback remains active behind it. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PlayerEpisodesPanel(
    visible: Boolean,
    episodes: EpisodesPanelUIState?,
    initialFocusedItemId: Int? = null,
    onEpisodeSelected: (VideoItemUIState) -> Unit,
    onEpisodeContextMenu: ((VideoItemUIState) -> Unit)? = null,
    onDismiss: () -> Unit,
    allowFocusExit: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val currentSeason = episodes?.seasons
        ?.firstOrNull { season -> season.episodes.any { it.id == initialFocusedItemId } }
        ?: episodes?.seasons?.firstOrNull()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val preferredPanelHeight = maxHeight * PANEL_HEIGHT_FRACTION
        val panelHeight = minOf(
            maxHeight,
            preferredPanelHeight.coerceIn(PanelMinHeight, PanelMaxHeight),
        )

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
                .fillMaxWidth()
                .height(panelHeight),
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
                    .fillMaxSize()
                    .playerGlass(
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        level = PlayerGlass.Strong,
                        elevation = 20.dp,
                    )
                    .focusProperties {
                        onExit = {
                            if (!allowFocusExit) cancelFocusChange()
                        }
                    }
                    .focusGroup()
                    .padding(top = 46.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                EpisodesHeader(season = currentSeason)

                currentSeason?.let { season ->
                    EpisodeRow(
                        visible = visible,
                        season = season,
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
            .padding(horizontal = 36.dp),
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun EpisodeRow(
    visible: Boolean,
    season: EpisodeSeasonUIState,
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

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .dpadScrollOptimization(axis = DpadScrollAxis.Horizontal),
        contentPadding = PaddingValues(horizontal = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(season.episodes, key = { _, item -> item.id }) { index, item ->
            val isCurrentEpisode = item.id == currentEpisodeId
            VideoItemHorizontal(
                state = item,
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
