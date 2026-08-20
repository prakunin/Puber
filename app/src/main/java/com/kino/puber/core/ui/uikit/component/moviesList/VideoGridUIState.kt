package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.DpadScrollAxis
import com.kino.puber.core.ui.uikit.component.FadeGradient
import com.kino.puber.core.ui.uikit.component.PositionFocusedItemInLazyLayout
import com.kino.puber.core.ui.uikit.component.dpadScrollOptimization
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.contentlist.content.rememberFocusedListItemScroller

@Immutable
data class VideoGridUIState(
    val list: List<VideoGridItemUIState>,
)

/** The position of a grid entry that is not a row of cards, and so cannot be a focus neighbour. */
private const val NOT_A_ROW = -1

@Immutable
sealed interface VideoGridItemUIState {
    data class Title(val title: String) : VideoGridItemUIState
    data class Items(
        val items: List<VideoItemUIState>,
        val rowKey: String,
    ) : VideoGridItemUIState
}

/**
 * @param detailsPrefetchEnabled whether this grid's cards open `DetailsScreen`. Off by default
 * because the grid is also the player's episode list, whose ids are episode ids: prefetching those
 * would call the item-details endpoint with an id that means something else entirely.
 * @param rowsFillViewport when true, each [VideoGridItemUIState.Title] and the
 * [VideoGridItemUIState.Items] that follows it are drawn as one list item that fills the viewport
 * height, and focus landing in that row scrolls it to the top. Off by default, which is also the
 * player's episode-list case: there rows keep their own height and sit one after another.
 */
@Composable
fun VideoGrid(
    modifier: Modifier = Modifier,
    state: VideoGridUIState,
    onItemClick: (VideoItemUIState) -> Unit = {},
    onItemFocused: (VideoItemUIState) -> Unit = {},
    onItemContextMenu: ((VideoItemUIState) -> Unit)? = null,
    enableTopSideGradient: Boolean = true,
    initialFocusedItemId: Int? = null,
    detailsPrefetchEnabled: Boolean = false,
    rowsFillViewport: Boolean = false,
) {
    DetailsPrefetchSurface(enabled = detailsPrefetchEnabled) {
        VideoGridContent(
            modifier = modifier,
            state = state,
            onItemClick = onItemClick,
            onItemFocused = onItemFocused,
            onItemContextMenu = onItemContextMenu,
            enableTopSideGradient = enableTopSideGradient,
            initialFocusedItemId = initialFocusedItemId,
            detailsPrefetchEnabled = detailsPrefetchEnabled,
            rowsFillViewport = rowsFillViewport,
        )
    }
}

@Composable
private fun VideoGridContent(
    modifier: Modifier,
    state: VideoGridUIState,
    onItemClick: (VideoItemUIState) -> Unit,
    onItemFocused: (VideoItemUIState) -> Unit,
    onItemContextMenu: ((VideoItemUIState) -> Unit)?,
    enableTopSideGradient: Boolean,
    initialFocusedItemId: Int?,
    detailsPrefetchEnabled: Boolean,
    rowsFillViewport: Boolean,
) {
    val lazyListState = rememberLazyListState()
    val gridFocus = rememberVideoGridFocusState(
        list = state.list,
        initialFocusedItemId = initialFocusedItemId,
        lazyListState = lazyListState,
    )

    val showTopGradient by remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset > 0 } }

    // The absolute position of each card row, counted over the whole grid rather than over what the
    // LazyColumn happens to have composed. Titles are not focusable and so hold no position: a press
    // down from the row above a title lands in the row below it.
    val rowOrders = remember(state.list) {
        var order = 0
        state.list.map { entry -> if (entry is VideoGridItemUIState.Items) order++ else NOT_A_ROW }
    }

    // Sections are only drawn under rowsFillViewport, but the lookup they need — a row's order,
    // keyed by rowKey rather than by its position in `sections` — is cheap to keep around
    // unconditionally rather than worth guarding.
    val sections = remember(state.list) { state.list.asSections() }
    val rowOrderByKey = remember(state.list, rowOrders) {
        buildMap {
            state.list.forEachIndexed { index, entry ->
                if (entry is VideoGridItemUIState.Items) put(entry.rowKey, rowOrders[index])
            }
        }
    }
    val onSectionFocused = rememberFocusedListItemScroller(lazyListState)

    PositionFocusedItemInLazyLayout {
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                // The player's list is taller than the screen and needs room under its last row.
                // A viewport-paged list is exactly as tall as the screen, and that padding would be
                // subtracted from every item's height instead: with a 234 dp area and 180 dp of it
                // spoken for, `fillParentMaxHeight` hands each season 54 dp and the cards are
                // squeezed from 180 dp to 48.
                contentPadding = if (rowsFillViewport) {
                    PaddingValues()
                } else {
                    PaddingValues(bottom = PuberTheme.Defaults.VideoItemHeight)
                },
            ) {
                if (rowsFillViewport) {
                    itemsIndexed(
                        sections,
                        key = { _, s -> "section_${s.items?.rowKey ?: s.title?.title}" },
                    ) { sectionIndex, section ->
                        VideoGridSection(
                            section = section,
                            sectionIndex = sectionIndex,
                            rowOrder = section.items?.let { rowOrderByKey[it.rowKey] } ?: NOT_A_ROW,
                            detailsPrefetchEnabled = detailsPrefetchEnabled,
                            isTargetRow = section.items?.rowKey == gridFocus.rowFocus.focusedRowKey,
                            initialFocusedItemId = initialFocusedItemId?.takeIf { itemId ->
                                section.items?.items?.any { it.id == itemId } == true
                            },
                            gridFocus = gridFocus,
                            onItemClick = onItemClick,
                            onItemContextMenu = onItemContextMenu,
                            onItemFocused = onItemFocused,
                            onSectionFocused = onSectionFocused,
                        )
                    }
                } else {
                    itemsIndexed(state.list, key = { _, item ->
                        when (item) {
                            is VideoGridItemUIState.Title -> "title_${item.title}"
                            is VideoGridItemUIState.Items -> "items_${item.rowKey}"
                        }
                    }) { index, columnItem ->
                        when (columnItem) {

                            is VideoGridItemUIState.Title -> Text(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                text = columnItem.title,
                                style = MaterialTheme.typography.titleLarge,
                            )

                            is VideoGridItemUIState.Items -> VideoGridItems(
                                items = columnItem,
                                rowOrder = rowOrders[index],
                                detailsPrefetchEnabled = detailsPrefetchEnabled,
                                isTargetRow = columnItem.rowKey == gridFocus.rowFocus.focusedRowKey,
                                initialFocusedItemId = initialFocusedItemId?.takeIf { itemId ->
                                    columnItem.items.any { it.id == itemId }
                                },
                                onItemClick = onItemClick,
                                onItemContextMenu = onItemContextMenu,
                                onItemFocused = { item ->
                                    gridFocus.rowFocus.onRowFocused(columnItem.rowKey)
                                    onItemFocused(item)
                                },
                                onRowEmpty = {
                                    gridFocus.rowFocus.onRowEmpty(
                                        gridFocus.rows.indexOfFirst { it.key == columnItem.rowKey }
                                    )
                                },
                            )
                        }
                    }
                }
            }

            VideoGridTopGradient(visible = enableTopSideGradient && showTopGradient)
        }
    }
}

/** A season heading and the episodes under it, drawn as one item so the pair cannot be split. */
private data class GridSection(
    val title: VideoGridItemUIState.Title?,
    val items: VideoGridItemUIState.Items?,
)

private fun List<VideoGridItemUIState>.asSections(): List<GridSection> = buildList {
    var pendingTitle: VideoGridItemUIState.Title? = null
    this@asSections.forEach { entry ->
        when (entry) {
            is VideoGridItemUIState.Title -> {
                if (pendingTitle != null) add(GridSection(pendingTitle, items = null))
                pendingTitle = entry
            }
            is VideoGridItemUIState.Items -> {
                add(GridSection(pendingTitle, entry))
                pendingTitle = null
            }
        }
    }
    pendingTitle?.let { add(GridSection(it, items = null)) }
}

@Composable
private fun LazyItemScope.VideoGridSection(
    section: GridSection,
    sectionIndex: Int,
    rowOrder: Int,
    detailsPrefetchEnabled: Boolean,
    isTargetRow: Boolean,
    initialFocusedItemId: Int?,
    gridFocus: VideoGridFocusState,
    onItemClick: (VideoItemUIState) -> Unit,
    onItemContextMenu: ((VideoItemUIState) -> Unit)?,
    onItemFocused: (VideoItemUIState) -> Unit,
    onSectionFocused: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillParentMaxHeight()) {
        section.title?.let { title ->
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                text = title.title,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        val items = section.items
        if (items != null) {
            VideoGridItems(
                items = items,
                rowOrder = rowOrder,
                detailsPrefetchEnabled = detailsPrefetchEnabled,
                isTargetRow = isTargetRow,
                initialFocusedItemId = initialFocusedItemId,
                onItemClick = onItemClick,
                onItemContextMenu = onItemContextMenu,
                onItemFocused = { item ->
                    gridFocus.rowFocus.onRowFocused(items.rowKey)
                    onSectionFocused(sectionIndex)
                    onItemFocused(item)
                },
                onRowEmpty = {
                    gridFocus.rowFocus.onRowEmpty(
                        gridFocus.rows.indexOfFirst { it.key == items.rowKey },
                    )
                },
            )
        }
    }
}

@Composable
private fun BoxScope.VideoGridTopGradient(visible: Boolean) {
    if (!visible) return
    val gradientHeight = 48.dp
    val surfaceColor = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val gradientBrush = remember(surfaceColor) {
        Brush.verticalGradient(
            colors = listOf(surfaceColor, surfaceColor.copy(alpha = 0F)),
            endY = with(density) { gradientHeight.toPx() },
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(gradientHeight)
            .align(Alignment.TopCenter)
            .background(brush = gradientBrush),
    )
}

@Composable
private fun VideoGridItems(
    items: VideoGridItemUIState.Items,
    rowOrder: Int,
    detailsPrefetchEnabled: Boolean,
    isTargetRow: Boolean,
    initialFocusedItemId: Int?,
    onItemClick: (VideoItemUIState) -> Unit,
    onItemContextMenu: ((VideoItemUIState) -> Unit)?,
    onItemFocused: (VideoItemUIState) -> Unit,
    onRowEmpty: () -> Unit,
) {
    DetailsPrefetchRow(
        rowOrder = rowOrder,
        rowKey = items.rowKey,
        items = items.items,
        enabled = detailsPrefetchEnabled,
    )
    val itemFocus = rememberReconciledItemFocus(
        rowKey = items.rowKey,
        items = items.items,
        isTargetRow = isTargetRow,
        initialFocusedItemId = initialFocusedItemId,
        requestAfterFrame = true,
        onRowEmpty = onRowEmpty,
    )
    Box(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
    ) {
        val listState = rememberLazyListState()
        val rowFocusRequester = remember { FocusRequester() }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(rowFocusRequester)
                .dpadScrollOptimization(axis = DpadScrollAxis.Horizontal)
                .focusRestorer(itemFocus.focusRequester)
                .onFocusChanged { itemFocus.rowHasFocusRef[0] = it.hasFocus },
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            itemsIndexed(items.items, key = { _, item -> item.id }) { indexR, item ->
                val isFallbackTarget = item.id == itemFocus.targetItemId
                VideoGridRowItem(
                    item = item,
                    itemIndex = indexR,
                    isFallbackTarget = isFallbackTarget,
                    rowFocusRequester = rowFocusRequester,
                    savedItemFocusRequester = itemFocus.focusRequester,
                    onItemClick = onItemClick,
                    onItemContextMenu = onItemContextMenu,
                    onItemFocused = { _, focusedItem ->
                        itemFocus.onItemFocused(focusedItem.id)
                        onItemFocused(focusedItem)
                    },
                )
            }
        }
        FadeGradient(listState)
    }
}

@Composable
private fun VideoGridRowItem(
    item: VideoItemUIState,
    itemIndex: Int,
    isFallbackTarget: Boolean,
    rowFocusRequester: FocusRequester,
    savedItemFocusRequester: FocusRequester,
    onItemClick: (VideoItemUIState) -> Unit,
    onItemContextMenu: ((VideoItemUIState) -> Unit)?,
    onItemFocused: (Int, VideoItemUIState) -> Unit,
) {
    val focusModifier = remember(itemIndex, item.id) {
        Modifier.onFocusChanged { state ->
            if (state.isFocused) {
                onItemFocused(itemIndex, item)
            }
        }
    }
    val clickCallback = remember(item.id) {
        {
            runCatching { rowFocusRequester.saveFocusedChild() }
            onItemClick(item)
        }
    }
    VideoItem(
        modifier = Modifier
            .then(
                if (isFallbackTarget) {
                    Modifier.focusRequester(savedItemFocusRequester)
                } else {
                    Modifier
                },
            )
            .then(focusModifier),
        state = item,
        onClick = clickCallback,
        onContextMenu = onItemContextMenu?.let { callback -> { callback(item) } },
    )
}
