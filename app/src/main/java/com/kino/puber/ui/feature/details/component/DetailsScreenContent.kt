package com.kino.puber.ui.feature.details.component

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.duotone.BookmarkSimple
import com.adamglin.phosphoricons.duotone.Eye
import com.adamglin.phosphoricons.fill.BookmarkSimple
import com.adamglin.phosphoricons.fill.Eye
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.EpisodeContextMenuDialog
import com.kino.puber.core.ui.uikit.component.FullScreenError
import com.kino.puber.core.ui.uikit.component.Rating
import com.kino.puber.core.ui.uikit.component.details.MediaScrim
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsMedia
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemHorizontal
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.component.modifier.placeholder
import com.kino.puber.core.ui.uikit.component.onTvContextMenuKey
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsButtonUIState
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import com.kino.puber.ui.feature.details.model.DetailsSeasonUIState
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
internal fun DetailsScreenContent(
    state: DetailsScreenState,
    onAction: (UIAction) -> Unit,
) {
    when (state) {
        is DetailsScreenState.Loading -> DetailsContentSkeleton()
        is DetailsScreenState.Error -> FullScreenError(
            error = state.message,
            onClick = { onAction(CommonAction.RetryClicked) },
        )

        is DetailsScreenState.Content -> {
            var episodeContextMenuItem by remember { mutableStateOf<VideoItemUIState?>(null) }
            Box(modifier = Modifier.fillMaxSize()) {
                DetailsContentBody(
                    state = state,
                    onAction = onAction,
                    onEpisodeContextMenu = { episodeContextMenuItem = it },
                )
                EpisodeContextMenuDialog(
                    episode = episodeContextMenuItem,
                    onDismiss = { episodeContextMenuItem = null },
                    onPlay = { onAction(DetailsAction.EpisodeSelected(it)) },
                    onMarkEpisodeWatched = { item, watched ->
                        onAction(DetailsAction.EpisodeWatchedChanged(item, watched))
                    },
                    onMarkSeasonWatched = { item, watched ->
                        onAction(DetailsAction.SeasonWatchedChanged(item, watched))
                    },
                )
                TrailerOverlay(
                    url = state.trailerUrl,
                    title = state.details.title,
                )
            }
        }
    }
}

/**
 * Two rails. The thing on top -- still, title, facts, actions -- and what you do next below:
 * the episodes of the chosen season on a series, the similar items on a film. No hidden pages.
 */
@Composable
private fun DetailsContentBody(
    state: DetailsScreenState.Content,
    onAction: (UIAction) -> Unit,
    onEpisodeContextMenu: (VideoItemUIState) -> Unit,
) {
    // The vertical hand-offs are named rather than searched for. Geometric focus search walked
    // past the season chips -- below the buttons, but far smaller than the cards under them --
    // and the season could not be chosen from the remote at all.
    val actionsFocus = remember { FocusRequester() }
    val seasonsFocus = remember { FocusRequester() }
    val railFocus = remember { FocusRequester() }
    val hasSeasons = state.selectedSeason != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = PAGE_EDGE, bottom = PAGE_EDGE),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1F),
        ) {
            DetailsHero(
                state = state,
                onAction = onAction,
                onEpisodeContextMenu = onEpisodeContextMenu,
                actionsFocus = actionsFocus,
                belowActions = if (hasSeasons) seasonsFocus else railFocus,
            )
        }
        DetailsRail(
            state = state,
            onAction = onAction,
            onEpisodeContextMenu = onEpisodeContextMenu,
            actionsFocus = actionsFocus,
            seasonsFocus = seasonsFocus,
            railFocus = railFocus,
            modifier = Modifier
                .fillMaxWidth()
                .height(RAIL_HEIGHT),
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun DetailsHero(
    state: DetailsScreenState.Content,
    onAction: (UIAction) -> Unit,
    onEpisodeContextMenu: (VideoItemUIState) -> Unit,
    actionsFocus: FocusRequester,
    belowActions: FocusRequester,
) {
    VideoDetailsMedia(
        modifier = Modifier.fillMaxSize(),
        state = state.details,
        trailerUrl = state.previewTrailerUrl,
        onTrailerFinished = { onAction(DetailsAction.TrailerPreviewFinished) },
        widthFraction = MEDIA_WIDTH_FRACTION,
        scrim = MediaScrim.Details,
    )

    // Drawn after the media: the trailer is a SurfaceView and clears whatever the window painted
    // before it, so only what comes later survives over a playing video.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = SIDE_EDGE, top = HERO_TOP),
    ) {
        HeroTitle(state)
        Spacer(modifier = Modifier.height(GAP_TITLE))
        Row(horizontalArrangement = Arrangement.spacedBy(GAP_RATINGS)) {
            state.info.ratings.forEach { rating -> Rating(rating) }
        }
        Spacer(modifier = Modifier.height(GAP_RATINGS_TO_CHIPS))
        ChipRow(state.info.chips)
        Spacer(modifier = Modifier.height(GAP_CHIPS_TO_PLOT))
        PlotBlock(
            text = state.details.description,
            modifier = Modifier.weight(1F, fill = false),
        )
        Spacer(modifier = Modifier.height(GAP_PLOT_TO_FACTS))
        Spacer(modifier = Modifier.weight(HERO_FACTS_ABOVE))
        HeroLine(state.info.genresLine)
        HeroLine(state.info.factsLine)
        HeroLine(state.info.directorLine)
        HeroLine(state.info.castLine)
        Spacer(modifier = Modifier.weight(1F - HERO_FACTS_ABOVE))
        ActionRow(
            state = state,
            onAction = onAction,
            onEpisodeContextMenu = onEpisodeContextMenu,
            actionsFocus = actionsFocus,
            belowActions = belowActions,
            modifier = Modifier.padding(bottom = HERO_BOTTOM),
        )
    }
}

@Composable
private fun HeroTitle(state: DetailsScreenState.Content) {
    // `VideoItemUIMapper.formatTitle` split `Русское / Original` onto two lines. They come back
    // onto one here: the original is a caption to the title, not a second heading.
    val lines = remember(state.details.title) { state.details.title.split("\n") }
    Row(
        horizontalArrangement = Arrangement.spacedBy(GAP_TITLE_PARTS),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = lines.first(),
            fontSize = TITLE_SIZE,
            lineHeight = TITLE_LINE,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1F, fill = false),
        )
        val original = lines.drop(1).joinToString(" ").trim()
        if (original.isNotEmpty()) {
            Text(
                text = original,
                fontSize = TITLE_ORIGINAL_SIZE,
                lineHeight = TITLE_LINE,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = TITLE_ORIGINAL_BASELINE),
            )
        }
    }
}

@Composable
private fun ChipRow(chips: List<String>) {
    if (chips.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(GAP_CHIPS)) {
        chips.forEach { chip ->
            Box(
                modifier = Modifier
                    .height(CHIP_HEIGHT)
                    .clip(RoundedCornerShape(CHIP_HEIGHT / 2))
                    .border(
                        width = CHIP_BORDER,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CHIP_BORDER_ALPHA),
                        shape = RoundedCornerShape(CHIP_HEIGHT / 2),
                    )
                    .padding(horizontal = CHIP_PADDING),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = chip,
                    fontSize = CHIP_TEXT_SIZE,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The whole plot. When it does not fit it scrolls from the remote: UP from the buttons hands the
 * block focus, the arrows move it, and DOWN at the end returns to the buttons. It never moves on
 * its own, unlike the auto-scrolling column it replaces.
 */
@Composable
private fun PlotBlock(
    text: String,
    modifier: Modifier,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val step = with(LocalDensity.current) { PLOT_LINE.toPx() }
    val scrollable = scrollState.canScrollForward || scrollState.canScrollBackward

    Box(
        modifier = modifier
            .width(PLOT_WIDTH)
            .heightIn(min = PLOT_MIN_HEIGHT)
            // The block ends on a line boundary rather than through one: a line cut in half
            // reads as breakage, not as "there is more below".
            .layout { measurable, constraints ->
                val line = PLOT_LINE.roundToPx().coerceAtLeast(1)
                val snapped = if (constraints.hasBoundedHeight) {
                    (constraints.maxHeight / line * line).coerceAtLeast(line)
                } else {
                    constraints.maxHeight
                }
                val placeable = measurable.measure(constraints.copy(maxHeight = snapped))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> scrollState.canScrollForward.also { canScroll ->
                        if (canScroll) scope.launch { scrollState.animateScrollBy(step) }
                    }
                    Key.DirectionUp -> scrollState.canScrollBackward.also { canScroll ->
                        if (canScroll) scope.launch { scrollState.animateScrollBy(-step) }
                    }
                    else -> false
                }
            }
            // Only a plot that actually scrolls takes focus. Otherwise UP from the buttons
            // would land on text that cannot move and look stuck.
            .focusable(enabled = scrollable, interactionSource = interactionSource)
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = PLOT_FOCUS_BORDER,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(PLOT_FOCUS_RADIUS),
                    )
                } else {
                    Modifier
                }
            )
            .padding(PLOT_FOCUS_BORDER),
    ) {
        Column(modifier = Modifier.verticalScroll(scrollState)) {
            Text(
                text = text,
                fontSize = PLOT_TEXT_SIZE,
                lineHeight = PLOT_LINE,
            )
        }
    }
}

@Composable
private fun HeroLine(text: String) {
    if (text.isBlank()) return
    Text(
        text = text,
        fontSize = HERO_LINE_SIZE,
        lineHeight = HERO_LINE_HEIGHT,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = HERO_LINE_ALPHA),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(HERO_LINE_WIDTH),
    )
}

@Composable
private fun ActionRow(
    state: DetailsScreenState.Content,
    onAction: (UIAction) -> Unit,
    onEpisodeContextMenu: (VideoItemUIState) -> Unit,
    actionsFocus: FocusRequester,
    belowActions: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val firstButtonFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state.trailerUrl, state.buttons.size) {
        if (state.trailerUrl == null) {
            runCatching { firstButtonFocusRequester.requestFocus() }
        }
    }
    Row(
        modifier = modifier
            .focusRequester(actionsFocus)
            .onDirectionKey(Key.DirectionDown, belowActions)
            .focusRestorer(firstButtonFocusRequester)
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.buttons.forEachIndexed { index, button ->
            DetailsActionButton(
                button = button,
                isInWatchlist = state.isInWatchlist,
                isWatched = state.isWatched,
                onAction = onAction,
                currentEpisode = state.currentEpisode,
                onEpisodeContextMenu = onEpisodeContextMenu,
                modifier = if (index == 0) Modifier.focusRequester(firstButtonFocusRequester) else Modifier,
            )
        }
        // A caption to the continue button rather than a block of its own, which is why it shares
        // the button's line.
        if (state.info.resumeLine.isNotBlank()) {
            Text(
                text = state.info.resumeLine,
                fontSize = RESUME_TEXT_SIZE,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = RESUME_GAP),
            )
        }
    }
}

@Composable
private fun DetailsActionButton(
    button: DetailsButtonUIState,
    isInWatchlist: Boolean,
    isWatched: Boolean,
    onAction: (UIAction) -> Unit,
    currentEpisode: VideoItemUIState?,
    onEpisodeContextMenu: (VideoItemUIState) -> Unit,
    modifier: Modifier,
) {
    when (button) {
        is DetailsButtonUIState.TextButton -> DetailsTextButton(
            button = button,
            onAction = onAction,
            currentEpisode = currentEpisode,
            onEpisodeContextMenu = onEpisodeContextMenu,
            modifier = modifier,
        )
        is DetailsButtonUIState.IconOnly -> DetailsIconButton(button, onAction, modifier)
        is DetailsButtonUIState.WatchlistToggle -> DetailsWatchlistButton(button, isInWatchlist, onAction, modifier)
        is DetailsButtonUIState.WatchedToggle -> DetailsWatchedButton(button, isWatched, onAction, modifier)
    }
}

@Composable
private fun DetailsTextButton(
    button: DetailsButtonUIState.TextButton,
    onAction: (UIAction) -> Unit,
    currentEpisode: VideoItemUIState?,
    onEpisodeContextMenu: (VideoItemUIState) -> Unit,
    modifier: Modifier,
) {
    val buttonModifier = if (button.action == DetailsAction.PlayClicked && currentEpisode != null) {
        modifier.onTvContextMenuKey(onOpen = { onEpisodeContextMenu(currentEpisode) })
    } else {
        modifier
    }
    Button(
        onClick = { onAction(button.action) },
        modifier = buttonModifier.height(BUTTON_HEIGHT),
        contentPadding = PaddingValues(horizontal = BUTTON_PADDING),
    ) {
        Icon(
            imageVector = button.icon,
            contentDescription = null,
            modifier = Modifier.size(BUTTON_ICON),
        )
        Spacer(Modifier.width(BUTTON_ICON_GAP))
        Text(
            text = button.textOverride ?: stringResource(button.textRes),
            fontSize = BUTTON_TEXT_SIZE,
        )
    }
}

@Composable
private fun DetailsIconButton(
    button: DetailsButtonUIState.IconOnly,
    onAction: (UIAction) -> Unit,
    modifier: Modifier,
) {
    IconButton(
        onClick = { onAction(button.action) },
        modifier = modifier.size(BUTTON_HEIGHT),
    ) {
        Icon(
            imageVector = button.icon,
            contentDescription = stringResource(button.contentDescription),
            modifier = Modifier.size(BUTTON_ICON),
        )
    }
}

@Composable
private fun DetailsWatchlistButton(
    button: DetailsButtonUIState.WatchlistToggle,
    checked: Boolean,
    onAction: (UIAction) -> Unit,
    modifier: Modifier,
) {
    IconButton(
        onClick = { onAction(button.action) },
        modifier = modifier.size(BUTTON_HEIGHT),
    ) {
        Icon(
            imageVector = if (checked) PhosphorIcons.Fill.BookmarkSimple else PhosphorIcons.Duotone.BookmarkSimple,
            contentDescription = stringResource(button.contentDescription),
            modifier = Modifier.size(BUTTON_ICON),
            tint = if (checked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

@Composable
private fun DetailsWatchedButton(
    button: DetailsButtonUIState.WatchedToggle,
    checked: Boolean,
    onAction: (UIAction) -> Unit,
    modifier: Modifier,
) {
    IconButton(
        onClick = { onAction(button.action) },
        modifier = modifier.size(BUTTON_HEIGHT),
    ) {
        Icon(
            imageVector = if (checked) PhosphorIcons.Fill.Eye else PhosphorIcons.Duotone.Eye,
            contentDescription = stringResource(button.contentDescription),
            modifier = Modifier.size(BUTTON_ICON),
            tint = if (checked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

/** The lower rail: the chosen season's episodes on a series, the similar items on a film. */
@Composable
private fun DetailsRail(
    state: DetailsScreenState.Content,
    onAction: (UIAction) -> Unit,
    onEpisodeContextMenu: (VideoItemUIState) -> Unit,
    actionsFocus: FocusRequester,
    seasonsFocus: FocusRequester,
    railFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val season = state.selectedSeason
    when {
        season != null -> Column(modifier = modifier) {
            SeasonChipRow(
                seasons = state.seasons,
                selected = season,
                onAction = onAction,
                seasonsFocus = seasonsFocus,
                above = actionsFocus,
                below = railFocus,
            )
            Spacer(modifier = Modifier.height(GAP_CHIPS_TO_CARDS))
            EpisodeRow(
                episodes = season.episodes,
                initialFocusedItemId = state.initialEpisodeFocusId ?: state.currentEpisode?.id,
                onItemClick = { episode -> onAction(DetailsAction.EpisodeSelected(episode)) },
                onItemContextMenu = onEpisodeContextMenu,
                railFocus = railFocus,
                above = seasonsFocus,
            )
        }

        state.similarItems.isNotEmpty() -> Column(modifier = modifier) {
            Text(
                text = stringResource(R.string.video_details_similar_title),
                fontSize = RAIL_LABEL_SIZE,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = SIDE_EDGE, top = RAIL_TOP),
            )
            Spacer(modifier = Modifier.height(GAP_CHIPS_TO_CARDS))
            EpisodeRow(
                episodes = state.similarItems,
                initialFocusedItemId = null,
                onItemClick = { item -> onAction(DetailsAction.SimilarSelected(item)) },
                onItemContextMenu = null,
                railFocus = railFocus,
                above = actionsFocus,
            )
        }

        else -> Box(modifier = modifier)
    }
}

@Composable
private fun SeasonChipRow(
    seasons: List<DetailsSeasonUIState>,
    selected: DetailsSeasonUIState,
    onAction: (UIAction) -> Unit,
    seasonsFocus: FocusRequester,
    above: FocusRequester,
    below: FocusRequester,
) {
    Row(
        modifier = Modifier
            .padding(start = SIDE_EDGE, top = RAIL_TOP)
            .focusRequester(seasonsFocus)
            .onDirectionKey(Key.DirectionUp, above)
            .onDirectionKey(Key.DirectionDown, below)
            .focusRestorer()
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(GAP_SEASON_CHIPS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        seasons.forEach { season ->
            val isSelected = season.number == selected.number
            Button(
                onClick = { onAction(DetailsAction.SeasonSelected(season.number)) },
                modifier = Modifier.height(SEASON_CHIP_HEIGHT),
                contentPadding = PaddingValues(horizontal = SEASON_CHIP_PADDING),
                colors = if (isSelected) {
                    ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primary
                            .copy(alpha = SEASON_CHIP_SELECTED_ALPHA),
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    ButtonDefaults.colors()
                },
            ) {
                Text(
                    text = season.number.toString(),
                    fontSize = SEASON_CHIP_TEXT_SIZE,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Text(
            text = selected.summary,
            fontSize = HERO_LINE_SIZE,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SEASON_SUMMARY_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = SEASON_SUMMARY_GAP),
        )
    }
}

@Composable
private fun EpisodeRow(
    episodes: List<VideoItemUIState>,
    initialFocusedItemId: Int?,
    onItemClick: (VideoItemUIState) -> Unit,
    onItemContextMenu: ((VideoItemUIState) -> Unit)?,
    railFocus: FocusRequester,
    above: FocusRequester,
) {
    val listState = rememberLazyListState()
    val rowFocusRequester = remember { FocusRequester() }
    val titleSmall = MaterialTheme.typography.titleSmall
    val cardTitleStyle = remember(titleSmall) {
        titleSmall.copy(fontSize = CARD_TITLE_SIZE, lineHeight = CARD_TITLE_LINE)
    }
    val initialIndex = remember(episodes, initialFocusedItemId) {
        episodes.indexOfFirst { episode -> episode.id == initialFocusedItemId }.takeIf { it >= 0 }
    }
    LaunchedEffect(initialIndex) {
        initialIndex?.let { index -> listState.scrollToItem(index) }
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(railFocus)
            .onDirectionKey(Key.DirectionUp, above)
            .focusRestorer(rowFocusRequester)
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
        contentPadding = PaddingValues(start = SIDE_EDGE, end = SIDE_EDGE),
    ) {
        itemsIndexed(episodes, key = { _, item -> item.id }) { index, item ->
            VideoItemHorizontal(
                modifier = if (index == (initialIndex ?: 0)) {
                    Modifier.focusRequester(rowFocusRequester)
                } else {
                    Modifier
                },
                state = item,
                onClick = { onItemClick(item) },
                itemHeight = CARD_HEIGHT,
                // A second line lifts the text, and captions across the row read from different
                // heights.
                titleMaxLines = 1,
                titleStyle = cardTitleStyle,
                onContextMenu = onItemContextMenu?.let { handler -> { handler(item) } },
            )
        }
    }
}

/**
 * Hands focus to a named row on an arrow. Geometric search misses here: the chip row sits below
 * the buttons but is far smaller than the cards under it, and the season became unreachable.
 */
private fun Modifier.onDirectionKey(key: Key, target: FocusRequester): Modifier =
    onPreviewKeyEvent { event ->
        if (event.key != key) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> runCatching { target.requestFocus() }.isSuccess
            KeyEventType.KeyUp -> true
            else -> false
        }
    }

@Composable
private fun DetailsContentSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = SIDE_EDGE, top = PAGE_EDGE + HERO_TOP, bottom = PAGE_EDGE),
    ) {
        SkeletonBar(width = SKELETON_TITLE_WIDTH, height = TITLE_LINE.value.dp)
        Spacer(modifier = Modifier.height(GAP_TITLE))
        Row(horizontalArrangement = Arrangement.spacedBy(GAP_RATINGS)) {
            repeat(SKELETON_RATING_COUNT) { SkeletonBar(width = SKELETON_RATING_WIDTH, height = CHIP_HEIGHT) }
        }
        Spacer(modifier = Modifier.height(GAP_RATINGS_TO_CHIPS))
        Row(horizontalArrangement = Arrangement.spacedBy(GAP_CHIPS)) {
            SKELETON_CHIP_WIDTHS.forEach { width -> SkeletonBar(width = width, height = CHIP_HEIGHT) }
        }
        Spacer(modifier = Modifier.height(GAP_CHIPS_TO_PLOT))
        Column(verticalArrangement = Arrangement.spacedBy(SKELETON_LINE_GAP)) {
            repeat(SKELETON_PLOT_LINE_COUNT) { SkeletonBar(width = PLOT_WIDTH, height = SKELETON_LINE_HEIGHT) }
        }
        Spacer(modifier = Modifier.height(GAP_PLOT_TO_FACTS))
        // Split the same way the loaded hero splits, or the bars sit high and the real lines drop
        // 84 % of the way down the moment the content arrives.
        Spacer(modifier = Modifier.weight(HERO_FACTS_ABOVE))
        Column(verticalArrangement = Arrangement.spacedBy(SKELETON_LINE_GAP)) {
            SKELETON_FACT_WIDTHS.forEach { width -> SkeletonBar(width = width, height = SKELETON_LINE_HEIGHT) }
        }
        Spacer(modifier = Modifier.weight(1F - HERO_FACTS_ABOVE))
        Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
            SkeletonBar(width = SKELETON_PLAY_WIDTH, height = BUTTON_HEIGHT, radius = BUTTON_HEIGHT / 2)
            repeat(SKELETON_ICON_BUTTON_COUNT) {
                SkeletonBar(width = BUTTON_HEIGHT, height = BUTTON_HEIGHT, radius = BUTTON_HEIGHT / 2)
            }
        }
        Spacer(modifier = Modifier.height(HERO_BOTTOM + RAIL_TOP))
        Row(horizontalArrangement = Arrangement.spacedBy(GAP_SEASON_CHIPS)) {
            repeat(SKELETON_SEASON_CHIP_COUNT) {
                SkeletonBar(
                    width = SEASON_CHIP_HEIGHT + SEASON_CHIP_PADDING,
                    height = SEASON_CHIP_HEIGHT,
                    radius = SEASON_CHIP_HEIGHT / 2,
                )
            }
        }
        Spacer(modifier = Modifier.height(GAP_CHIPS_TO_CARDS))
        Row(horizontalArrangement = Arrangement.spacedBy(CARD_GAP)) {
            repeat(SKELETON_CARD_COUNT) {
                Box(
                    modifier = Modifier
                        .height(CARD_HEIGHT)
                        .aspectRatio(CARD_ASPECT_RATIO)
                        .placeholder(visible = true, shape = RoundedCornerShape(CARD_RADIUS)),
                )
            }
        }
    }
}

@Composable
private fun SkeletonBar(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp = SKELETON_RADIUS,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .placeholder(visible = true, shape = RoundedCornerShape(radius)),
    )
}

// region Sizes
// Taken from the 1920 x 1080 screen emulator and divided by two: a 1080p television is density 2.0.

/**
 * How far the page stands off the top and bottom edges of the screen. Zero on purpose: the hero
 * starts at the physical top edge and the rail ends at the physical bottom one.
 *
 * That is further out than anything else in the fork -- the player's overlay still keeps
 * 10 dp top and bottom and 20 dp at the sides -- and well outside Google's 48 dp TV safe zone,
 * which exists because a television may overscan. It was dialled on the stand and chosen with
 * that known; it is not a default that leaked in, and not a mistake to be quietly reverted.
 */
private val PAGE_EDGE = 0.dp
private val SIDE_EDGE = 11.dp
private val HERO_TOP = 0.dp
private val HERO_BOTTOM = 0.dp
/**
 * What the rail holds, with two dp over: the season chip row, [GAP_CHIPS_TO_CARDS] and a
 * [CARD_HEIGHT] card. The row stands 24 dp rather than [SEASON_CHIP_HEIGHT] -- the summary text
 * beside the chips inherits `bodyLarge`'s 24 sp line -- so 24 + 4 + 92 = 120.
 *
 * The two dp are slack, not spare: the rail is a fixed height and a card's `Modifier.height` is
 * bounded by what is left of it, so a rail one dp short does not clip the card, it squashes it.
 * Anything in that sum growing means this grows with it.
 */
private val RAIL_HEIGHT = 122.dp

/**
 * 1258 of 1920. Still narrower than the catalogue's 0.75, but the hero's text no longer stands
 * beside the picture: the plot runs 270 dp under it and the fact lines cross it entirely, over
 * [MediaScrim.Details] rather than over a margin.
 *
 * The scrim covers almost none of that run. It fades out by 10 % of the frame, so a fact line
 * reaching [HERO_LINE_WIDTH] ends at 951 dp -- 98 % across -- on bare picture. That is the trade
 * the width buys: the whole cast rather than the first four names. It reads on a dark still and
 * has not been judged on a bright one.
 */
private const val MEDIA_WIDTH_FRACTION = 0.655F
private val RAIL_TOP = 0.dp

private val TITLE_SIZE = 28.sp
private val TITLE_ORIGINAL_SIZE = 15.sp

/**
 * Not below the font's own line. Roboto-Medium on the television reports ascent 1900 and
 * descent -500 on a 2048 unit em -- 1.1719 em, so 32.8 sp at a 28 sp title. The 25 sp this was
 * dialled to left the box 7.8 sp short, and a Cyrillic descender (у, р, ф, д) reaches 6.8 sp
 * below the baseline: it would either be cut or land in the ratings row 4 dp under it.
 */
private val TITLE_LINE = 33.sp
private val TITLE_ORIGINAL_BASELINE = 4.dp
private val GAP_TITLE_PARTS = 10.dp

private val GAP_TITLE = 4.dp
private val GAP_RATINGS = 15.dp
private val GAP_RATINGS_TO_CHIPS = 3.dp
private val GAP_CHIPS = 5.dp
private val GAP_CHIPS_TO_PLOT = 3.dp
private val GAP_PLOT_TO_FACTS = 13.dp

private val CHIP_HEIGHT = 16.dp
private val CHIP_PADDING = 7.dp
private val CHIP_BORDER = 1.dp
private val CHIP_TEXT_SIZE = 10.sp
private const val CHIP_BORDER_ALPHA = 0.38F

private val PLOT_WIDTH = 590.dp
private val PLOT_TEXT_SIZE = 13.sp
private val PLOT_LINE = 18.sp
private val PLOT_MIN_HEIGHT = 110.dp
private val PLOT_FOCUS_BORDER = 1.dp
private val PLOT_FOCUS_RADIUS = 4.dp

private val HERO_LINE_SIZE = 9.sp
private val HERO_LINE_HEIGHT = 13.sp

/**
 * Nearly the whole screen: 940 of the 949 dp left of [SIDE_EDGE]. Wider than the plot on purpose
 * -- the plot is a block to read and stays at [PLOT_WIDTH], while these are one line each and
 * every character they gain is a name that no longer falls off the end.
 */
private val HERO_LINE_WIDTH = 940.dp
private const val HERO_LINE_ALPHA = 0.70F

/**
 * How the hero's free height splits around the fact lines: 84 % above them, the rest below. Both
 * shares come out of the spacer's half of the remainder, which does not depend on how long the
 * description is, so they are the same on every item: the lines hang a fixed gap above the action
 * row rather than sitting straight under the plot.
 *
 * That is a place, not a pin. A short description still lifts the fact lines and the action row
 * together, because [PlotBlock] takes `weight(1F, fill = false)` and hands back nothing of the
 * half it did not use. Where the bottom of the hero ends up is the older open question the
 * stand's «низ героя» switch is for; this only decides where in the tail the lines sit.
 *
 * The two spacers still weigh 1F between them, so [PlotBlock] keeps the half it had -- adding a
 * third weighted child at full weight would have cut the plot to a third of the remainder.
 */
private const val HERO_FACTS_ABOVE = 0.95F

private val BUTTON_HEIGHT = 30.dp
private val BUTTON_PADDING = 15.dp
private val BUTTON_ICON = 15.dp
private val BUTTON_ICON_GAP = 5.dp
private val BUTTON_GAP = 10.dp
private val BUTTON_TEXT_SIZE = 11.sp
private val RESUME_TEXT_SIZE = 10.sp
private val RESUME_GAP = 5.dp

/**
 * Below the 24 dp the row actually stands: the season summary beside the chips names only a font
 * size, so it inherits `bodyLarge`'s 24 sp line and floors the row there. Twenty makes the chips
 * smaller without making the row shorter -- [RAIL_HEIGHT] is sized off the 24, not off this.
 */
private val SEASON_CHIP_HEIGHT = 20.dp
private val SEASON_CHIP_PADDING = 15.dp
private val SEASON_CHIP_TEXT_SIZE = 10.sp
private const val SEASON_CHIP_SELECTED_ALPHA = 0.20F
private val SEASON_SUMMARY_GAP = 12.dp
private const val SEASON_SUMMARY_ALPHA = 0.62F
private val GAP_SEASON_CHIPS = 5.dp
private val GAP_CHIPS_TO_CARDS = 4.dp

private val RAIL_LABEL_SIZE = 14.sp

private val CARD_HEIGHT = 92.dp
private val CARD_GAP = 5.dp

/**
 * The episode caption, shrunk for a card this size. Both halves are needed: `titleSmall` names a
 * 20 sp line, so dropping the font alone would leave the caption in a box two thirds taller than
 * its letters. Passed to [VideoItemHorizontal] rather than set on `Typography`, which the
 * catalogue's own cards read.
 */
private val CARD_TITLE_SIZE = 8.sp
private val CARD_TITLE_LINE = 14.sp
private val CARD_RADIUS = 8.dp
private const val CARD_ASPECT_RATIO = 16F / 9F

private val SKELETON_RADIUS = 2.dp
private val SKELETON_TITLE_WIDTH = 280.dp
private val SKELETON_RATING_WIDTH = 52.dp
private val SKELETON_LINE_HEIGHT = 11.dp
private val SKELETON_LINE_GAP = 4.dp
private val SKELETON_PLAY_WIDTH = 150.dp
private const val SKELETON_RATING_COUNT = 3
private const val SKELETON_PLOT_LINE_COUNT = 3
private const val SKELETON_ICON_BUTTON_COUNT = 3
private const val SKELETON_SEASON_CHIP_COUNT = 3
private const val SKELETON_CARD_COUNT = 4
private val SKELETON_CHIP_WIDTHS = listOf(75.dp, 95.dp, 80.dp, 60.dp)
private val SKELETON_FACT_WIDTHS = listOf(350.dp, 420.dp, 380.dp)
// endregion
