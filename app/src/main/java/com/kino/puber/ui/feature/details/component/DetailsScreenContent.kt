package com.kino.puber.ui.feature.details.component

import androidx.annotation.OptIn
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Text
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.duotone.BookmarkSimple
import com.adamglin.phosphoricons.duotone.CaretDown
import com.adamglin.phosphoricons.duotone.CaretUp
import com.adamglin.phosphoricons.duotone.Eye
import com.adamglin.phosphoricons.fill.BookmarkSimple
import com.adamglin.phosphoricons.fill.Eye
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.EpisodeContextMenuDialog
import com.kino.puber.core.ui.uikit.component.FullScreenError
import com.kino.puber.core.ui.uikit.component.Rating
import com.kino.puber.core.ui.uikit.component.VideoItemContextMenuDialog
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsMedia
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGrid
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItem
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.component.modifier.placeholder
import com.kino.puber.core.ui.uikit.component.onTvContextMenuKey
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsButtonUIState
import com.kino.puber.ui.feature.details.model.DetailsInfoUIState
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DETAILS_BUTTONS_FOCUS_DELAY_MS = 100L
private const val DETAILS_PAGE_FOCUS_DELAY_MS = 50L
private const val CHEVRON_ALPHA = 0.5F
private val DETAILS_PAGE_PEEK_HEIGHT = 32.dp
private val PAGE_FOCUS_BRIDGE_HEIGHT = 56.dp

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

@Composable
private fun DetailsContentBody(
    state: DetailsScreenState.Content,
    onAction: (UIAction) -> Unit,
    onEpisodeContextMenu: (VideoItemUIState) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val pageCount = if (state.similarItems.isNotEmpty()) DETAILS_PAGES_WITH_SIMILAR else DETAILS_PAGES_BASE
        val pagerState = rememberPagerState(pageCount = { pageCount })
        val coroutineScope = rememberCoroutineScope()
        val similarFirstItemFocusRequester = remember { FocusRequester() }
        val hasSimilarItems = state.similarItems.isNotEmpty()
        val focusSimilarPage = remember {
            {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(SIMILAR_PAGE_INDEX)
                    delay(DETAILS_PAGE_FOCUS_DELAY_MS)
                    runCatching { similarFirstItemFocusRequester.requestFocus() }
                }
                Unit
            }
        }
        val focusMainPage = remember {
            {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(MAIN_PAGE_INDEX)
                }
                Unit
            }
        }
        val isMainPageVisible by remember {
            derivedStateOf {
                pagerState.currentPage == MAIN_PAGE_INDEX &&
                    pagerState.currentPageOffsetFraction == 0F
            }
        }
        val currentPage by remember {
            derivedStateOf { pagerState.currentPage }
        }
        // The panel lives on the main page alone, and the pager keeps its neighbour composed. Without
        // this the trailer would carry on playing, with sound, underneath a page the user scrolled to.
        LaunchedEffect(currentPage) {
            if (currentPage != MAIN_PAGE_INDEX) {
                onAction(DetailsAction.TrailerPreviewStopped)
            }
        }
        LaunchedEffect(pagerState.currentPage, hasSimilarItems) {
            delay(DETAILS_PAGE_FOCUS_DELAY_MS)
            if (pagerState.currentPage == SIMILAR_PAGE_INDEX && hasSimilarItems) {
                runCatching { similarFirstItemFocusRequester.requestFocus() }
            }
        }
        KeepFocusedChildVisibleWithoutRepositioning {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = DETAILS_PAGE_PEEK_HEIGHT),
                beyondViewportPageCount = 1,
            ) { page ->
                when (page) {
                    MAIN_PAGE_INDEX -> DetailsMainPage(
                        modifier = Modifier.fillMaxSize(),
                        state = state,
                        onAction = onAction,
                        onEpisodeContextMenu = onEpisodeContextMenu,
                        isPageVisible = isMainPageVisible,
                        // The chevron promises a page below. With the information page gone there
                        // is one only when the item has similar items, and this account's answers
                        // are routinely empty.
                        showPageChevron = currentPage == MAIN_PAGE_INDEX && hasSimilarItems,
                        hasSimilarItems = hasSimilarItems,
                        onNextPageRequested = focusSimilarPage,
                        scrollToMainPage = { pagerState.animateScrollToPage(MAIN_PAGE_INDEX) },
                    )
                    SIMILAR_PAGE_INDEX -> DetailsSimilarPage(
                        items = state.similarItems,
                        onAction = onAction,
                        firstItemFocusRequester = similarFirstItemFocusRequester,
                        onPreviousPageRequested = focusMainPage,
                        showPageChevron = currentPage == SIMILAR_PAGE_INDEX,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun KeepFocusedChildVisibleWithoutRepositioning(
    content: @Composable () -> Unit,
) {
    val bringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
                val isAlreadyVisible = offset >= 0F && offset + size <= containerSize
                if (isAlreadyVisible) {
                    return 0F
                }

                val targetOffset = when {
                    offset < 0F -> 0F
                    size > containerSize -> 0F
                    else -> containerSize - size
                }
                return offset - targetOffset
            }
        }
    }

    CompositionLocalProvider(
        LocalBringIntoViewSpec provides bringIntoViewSpec,
        content = content,
    )
}

@Composable
private fun DetailsMainPage(
    modifier: Modifier,
    state: DetailsScreenState.Content,
    onAction: (UIAction) -> Unit,
    onEpisodeContextMenu: (VideoItemUIState) -> Unit,
    isPageVisible: Boolean,
    showPageChevron: Boolean,
    hasSimilarItems: Boolean,
    onNextPageRequested: () -> Unit,
    scrollToMainPage: suspend () -> Unit,
) {
    val episodes = state.episodes
    if (episodes == null) {
        Box(modifier = modifier) {
            DetailsHero(
                state = state,
                compact = false,
                onAction = onAction,
                onEpisodeContextMenu = onEpisodeContextMenu,
                isPageVisible = isPageVisible,
                showPageChevron = showPageChevron,
                hasSimilarItems = hasSimilarItems,
                onNextPageRequested = onNextPageRequested,
                scrollToMainPage = scrollToMainPage,
            )
        }
        return
    }

    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().weight(1F)) {
            DetailsHero(
                state = state,
                compact = true,
                onAction = onAction,
                onEpisodeContextMenu = onEpisodeContextMenu,
                isPageVisible = isPageVisible,
                showPageChevron = showPageChevron,
                hasSimilarItems = hasSimilarItems,
                onNextPageRequested = onNextPageRequested,
                scrollToMainPage = scrollToMainPage,
            )
        }
        VideoGrid(
            modifier = Modifier
                .fillMaxWidth()
                .height(SEASON_AREA_HEIGHT),
            state = episodes,
            rowsFillViewport = true,
            initialFocusedItemId = state.initialEpisodeFocusId ?: state.currentEpisode?.id,
            onItemClick = { episode -> onAction(DetailsAction.EpisodeSelected(episode)) },
            onItemContextMenu = onEpisodeContextMenu,
            onDownFromLastRow = onNextPageRequested.takeIf { hasSimilarItems },
        )
    }
}

/** The media still (or preview trailer) with the hero text drawn over it. */
@Composable
private fun DetailsHero(
    state: DetailsScreenState.Content,
    compact: Boolean,
    onAction: (UIAction) -> Unit,
    onEpisodeContextMenu: (VideoItemUIState) -> Unit,
    isPageVisible: Boolean,
    showPageChevron: Boolean,
    hasSimilarItems: Boolean,
    onNextPageRequested: () -> Unit,
    scrollToMainPage: suspend () -> Unit,
) {
    VideoDetailsMedia(
        modifier = Modifier.fillMaxSize(),
        state = state.details,
        trailerUrl = state.previewTrailerUrl,
        onTrailerFinished = { onAction(DetailsAction.TrailerPreviewFinished) },
    )

    // Drawn after the media: the trailer is a SurfaceView and clears whatever the window painted
    // before it, so only what comes later survives over a playing video.
    HeroColumn(
        state = state,
        compact = compact,
        onAction = onAction,
        onEpisodeContextMenu = onEpisodeContextMenu,
        isPageVisible = isPageVisible,
        showPageChevron = showPageChevron,
        hasSimilarItems = hasSimilarItems,
        onNextPageRequested = onNextPageRequested,
        scrollToMainPage = scrollToMainPage,
    )
}

@Composable
private fun HeroColumn(
    state: DetailsScreenState.Content,
    compact: Boolean,
    onAction: (UIAction) -> Unit,
    onEpisodeContextMenu: (VideoItemUIState) -> Unit,
    isPageVisible: Boolean,
    showPageChevron: Boolean,
    hasSimilarItems: Boolean,
    onNextPageRequested: () -> Unit,
    scrollToMainPage: suspend () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // The page below has nothing focusable while it is off screen -- its own focus bridge
            // only wakes once it is the current page -- so DOWN from a button had nowhere to go and
            // the similar items became unreachable. The page deleted from between the two used to
            // catch this key. On a series (`compact`) the hero is no longer the bottom of the page --
            // the season list sits below it -- so this handler steps aside and lets DOWN reach the
            // grid; `VideoGrid`'s own `onDownFromLastRow` picks the key back up once the last season
            // row has focus.
            .onDirectionKey(Key.DirectionDown, enabled = hasSimilarItems && !compact, onKey = onNextPageRequested)
            .padding(
                start = HERO_PADDING_START,
                // A series shares the page with its season list, so the block's own breathing room
                // is what gives way -- before the plot, which is the part worth reading.
                top = if (compact) HERO_COMPACT_PADDING_TOP else HERO_PADDING_TOP,
                bottom = if (compact) HERO_COMPACT_PADDING_BOTTOM else HERO_PADDING_BOTTOM,
            ),
    ) {
        // `VideoItemUIMapper.formatTitle` has already split `Русское / Original` onto two lines, so
        // this one Text carries both. It is left-aligned here, unlike the centred panel it replaces.
        Text(
            text = state.details.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(DESCRIPTION_MEASURE),
        )
        Spacer(modifier = Modifier.height(HERO_SPACING_XS))
        Row(horizontalArrangement = Arrangement.spacedBy(HERO_SPACING_XS)) {
            state.info.ratings.forEach { rating -> Rating(rating) }
        }
        Spacer(modifier = Modifier.height(HERO_SPACING_XS))
        HeroLine(text = metaLine(state.details))
        Spacer(modifier = Modifier.height(HERO_SPACING_SM))
        SelfScrollingText(
            text = state.details.description,
            style = MaterialTheme.typography.bodySmall,
            // The main page stays composed while the similar-items page is on screen, so without
            // `isPageVisible` the text would keep animating out of sight underneath it.
            enabled = isPageVisible && state.trailerUrl == null,
            // `fill = false`: the description may take everything that is left, but a short plot
            // takes only what it needs, and the facts follow the text instead of floating at the
            // bottom of a hole. A long one still gets the whole remainder, and scrolls inside it.
            modifier = Modifier
                .width(DESCRIPTION_MEASURE)
                .weight(1F, fill = false),
        )
        Spacer(modifier = Modifier.height(HERO_SPACING_SM))
        HeroLine(text = state.info.factsLine)
        HeroLine(text = state.info.creditsLine)
        Spacer(modifier = Modifier.height(HERO_SPACING_MD))
        ActionButtonsRow(
            buttons = state.buttons,
            isInWatchlist = state.isInWatchlist,
            isWatched = state.isWatched,
            onAction = onAction,
            currentEpisode = state.currentEpisode,
            onEpisodeContextMenu = onEpisodeContextMenu,
            trailerVisible = state.trailerUrl != null,
            recoverActionFocus = isPageVisible,
            scrollToMainPage = scrollToMainPage,
        )
        if (showPageChevron) {
            ChevronIndicator()
        }
    }
}

/**
 * A single line of facts. It runs over the artwork rather than wrapping inside the text column: a
 * list of countries folding onto a second line pushes everything below it down, and the scrim
 * reaches zero at the same fraction this stops at, so no part of it lands on an unmuted frame.
 */
@Composable
private fun HeroLine(text: String) {
    if (text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = HERO_LINE_ALPHA),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(SIDE_TEXT_WIDTH_FRACTION),
    )
}

private fun metaLine(details: VideoDetailsUIState): String = listOf(
    details.year,
    details.genres,
    details.country,
    details.duration,
).filter { it.isNotBlank() }.joinToString(" · ")

@Composable
private fun ActionButtonsRow(
    buttons: List<DetailsButtonUIState>,
    isInWatchlist: Boolean,
    isWatched: Boolean,
    onAction: (UIAction) -> Unit,
    currentEpisode: VideoItemUIState?,
    onEpisodeContextMenu: (VideoItemUIState) -> Unit,
    trailerVisible: Boolean,
    recoverActionFocus: Boolean,
    scrollToMainPage: suspend () -> Unit,
) {
    val firstButtonFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Request a concrete child, not the Row container. TV focus can otherwise
    // stay on the previous card/details area and make OK look unresponsive.
    LaunchedEffect(trailerVisible, recoverActionFocus, buttons.size) {
        delay(DETAILS_BUTTONS_FOCUS_DELAY_MS)
        if (!trailerVisible && recoverActionFocus) {
            scrollToMainPage()
            runCatching { firstButtonFocusRequester.requestFocus() }
        }
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .onFocusChanged { focusState ->
                if (!trailerVisible && focusState.hasFocus) {
                    coroutineScope.launch { scrollToMainPage() }
                }
            }
            .focusRestorer(firstButtonFocusRequester)
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEachIndexed { index, button ->
            DetailsActionButton(
                button = button,
                isInWatchlist = isInWatchlist,
                isWatched = isWatched,
                onAction = onAction,
                currentEpisode = currentEpisode,
                onEpisodeContextMenu = onEpisodeContextMenu,
                modifier = if (index == 0) Modifier.focusRequester(firstButtonFocusRequester) else Modifier,
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
    Button(onClick = { onAction(button.action) }, modifier = buttonModifier) {
        Icon(
            imageVector = button.icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = button.textOverride ?: stringResource(button.textRes))
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
        modifier = modifier,
    ) {
        Icon(
            imageVector = button.icon,
            contentDescription = stringResource(button.contentDescription),
            modifier = Modifier.size(20.dp),
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
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (checked) PhosphorIcons.Fill.BookmarkSimple else PhosphorIcons.Duotone.BookmarkSimple,
            contentDescription = stringResource(button.contentDescription),
            modifier = Modifier.size(20.dp),
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
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (checked) PhosphorIcons.Fill.Eye else PhosphorIcons.Duotone.Eye,
            contentDescription = stringResource(button.contentDescription),
            modifier = Modifier.size(20.dp),
            tint = if (checked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

@Composable
private fun DetailsSimilarPage(
    items: List<VideoItemUIState>,
    onAction: (UIAction) -> Unit,
    firstItemFocusRequester: FocusRequester,
    onPreviousPageRequested: () -> Unit,
    showPageChevron: Boolean,
    modifier: Modifier = Modifier,
) {
    var contextMenuItem by remember { mutableStateOf<VideoItemUIState?>(null) }
    Box(modifier = modifier.fillMaxWidth()) {
        PageFocusBridge(
            enabled = showPageChevron,
            onFocused = onPreviousPageRequested,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(PAGE_FOCUS_BRIDGE_HEIGHT),
        )
        if (showPageChevron) {
            ChevronIndicator(
                direction = ChevronDirection.Up,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onDirectionKey(Key.DirectionUp, onKey = onPreviousPageRequested)
                .padding(horizontal = 64.dp, vertical = 96.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.video_details_similar_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(24.dp))
            LazyRow(
                modifier = Modifier
                    .focusRestorer(firstItemFocusRequester)
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(end = 64.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    VideoItem(
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        },
                        state = item,
                        onClick = { onAction(DetailsAction.SimilarSelected(item)) },
                        onContextMenu = { contextMenuItem = item },
                    )
                }
            }
        }
        VideoItemContextMenuDialog(
            item = contextMenuItem,
            onDismiss = { contextMenuItem = null },
            onAction = onAction,
        )
    }
}

@Composable
private fun PageFocusBridge(
    enabled: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled) {
        Box(modifier = modifier)
        return
    }

    // LazyColumn does not compose the next page until it scrolls into view, so
    // D-pad focus needs an already-composed target to trigger page transitions.
    Surface(
        onClick = onFocused,
        modifier = modifier
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused()
                }
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
            contentColor = Color.Transparent,
            focusedContentColor = Color.Transparent,
            pressedContentColor = Color.Transparent,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}

private fun Modifier.onDirectionKey(
    key: Key,
    enabled: Boolean = true,
    onKey: () -> Unit,
): Modifier {
    if (!enabled) {
        return this
    }
    return onPreviewKeyEvent { event ->
        if (event.key != key) {
            return@onPreviewKeyEvent false
        }
        when (event.type) {
            KeyEventType.KeyDown -> {
                onKey()
                true
            }
            KeyEventType.KeyUp -> true
            else -> false
        }
    }
}

private enum class ChevronDirection {
    Up,
    Down,
}

@Composable
private fun ChevronIndicator(
    modifier: Modifier = Modifier,
    direction: ChevronDirection = ChevronDirection.Down,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = if (direction == ChevronDirection.Up) 12.dp else 0.dp,
                bottom = if (direction == ChevronDirection.Down) 16.dp else 0.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = when (direction) {
                ChevronDirection.Up -> PhosphorIcons.Duotone.CaretUp
                ChevronDirection.Down -> PhosphorIcons.Duotone.CaretDown
            },
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .alpha(CHEVRON_ALPHA),
        )
    }
}

@Composable
private fun DetailsContentSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = HERO_PADDING_START, top = HERO_PADDING_TOP, bottom = HERO_PADDING_BOTTOM),
    ) {
        SkeletonBar(width = SKELETON_TITLE_BAR_WIDTH, height = SKELETON_TITLE_BAR_HEIGHT)
        Spacer(modifier = Modifier.height(HERO_SPACING_XS))
        SkeletonBar(width = SKELETON_META_BAR_WIDTH, height = SKELETON_META_BAR_HEIGHT)
        Spacer(modifier = Modifier.height(HERO_SPACING_SM))
        Column(verticalArrangement = Arrangement.spacedBy(HERO_SPACING_XS)) {
            repeat(SKELETON_DESCRIPTION_LINE_COUNT) {
                SkeletonBar(width = SKELETON_DESCRIPTION_BAR_WIDTH, height = SKELETON_LINE_HEIGHT)
            }
        }
        Spacer(modifier = Modifier.height(HERO_SPACING_SM))
        SkeletonBar(width = SKELETON_FACTS_BAR_WIDTH, height = SKELETON_LINE_HEIGHT)

        Spacer(modifier = Modifier.height(HERO_SPACING_XS))

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(40.dp)
                    .placeholder(visible = true, shape = RoundedCornerShape(8.dp)),
            )
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(40.dp)
                    .placeholder(visible = true, shape = RoundedCornerShape(8.dp)),
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .placeholder(visible = true, shape = RoundedCornerShape(8.dp)),
            )
        }

        Spacer(modifier = Modifier.weight(1F))

        ChevronIndicator()
    }
}

@Composable
private fun SkeletonBar(width: Dp, height: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .placeholder(visible = true, shape = RoundedCornerShape(2.dp)),
    )
}

private const val MAIN_PAGE_INDEX = 0
private const val SIMILAR_PAGE_INDEX = 1
private const val DETAILS_PAGES_BASE = 1
private const val DETAILS_PAGES_WITH_SIMILAR = 2

private val HERO_PADDING_START = 48.dp
private val HERO_PADDING_TOP = 40.dp
private val HERO_PADDING_BOTTOM = 24.dp
private val HERO_COMPACT_PADDING_TOP = 20.dp
private val HERO_COMPACT_PADDING_BOTTOM = 8.dp
private val DESCRIPTION_MEASURE = 460.dp

/** The heading and one card, which is what leaves the hero its 306 dp on a 540 dp screen. */
// The heading, then the row: 16 dp of padding above and below a 180 dp card. Measured on the
// television at 234 dp, the row had 176 dp to give and the cards were clipped by four.
private val SEASON_AREA_HEIGHT = 240.dp
private const val SIDE_TEXT_WIDTH_FRACTION = 0.62F
private const val HERO_LINE_ALPHA = 0.72F
private const val TITLE_MAX_LINES = 3

/** Gaps between the hero's own lines: title-to-ratings, the ratings row itself, ratings-to-meta. */
private val HERO_SPACING_XS = 8.dp

/** Gaps either side of the description: meta-to-description, description-to-facts. */
private val HERO_SPACING_SM = 12.dp

/** Gap between the last text line and the button row. */
private val HERO_SPACING_MD = 16.dp

private val SKELETON_TITLE_BAR_WIDTH = 320.dp
private val SKELETON_TITLE_BAR_HEIGHT = 24.dp
private val SKELETON_META_BAR_WIDTH = 280.dp
private val SKELETON_META_BAR_HEIGHT = 16.dp
private val SKELETON_DESCRIPTION_BAR_WIDTH = 440.dp

/** Shared by the description bars and the facts bar; both stand for a `labelSmall`/`bodySmall` line. */
private val SKELETON_LINE_HEIGHT = 12.dp
private val SKELETON_FACTS_BAR_WIDTH = 380.dp
private const val SKELETON_DESCRIPTION_LINE_COUNT = 4
