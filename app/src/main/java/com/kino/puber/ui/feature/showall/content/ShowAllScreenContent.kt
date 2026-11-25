package com.kino.puber.ui.feature.showall.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.LocalTvDialogFocusRestorer
import com.kino.puber.core.ui.uikit.component.TvCompactButton
import com.kino.puber.core.ui.uikit.component.TvDialogFocusRestorer
import com.kino.puber.core.ui.uikit.component.VideoItemContextMenuDialog
import com.kino.puber.core.ui.uikit.component.drawer.LocalContentFocusHandoff
import com.kino.puber.core.ui.uikit.component.modifier.ContentFocusGate
import com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemPoster
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.showall.model.ShowAllAction
import com.kino.puber.ui.feature.showall.model.ShowAllFilterState
import com.kino.puber.ui.feature.showall.model.ShowAllViewState

internal const val SHOW_ALL_GRID_COLUMNS = 6
private val GridContentPadding = 16.dp
private val GridTopPadding = 8.dp
private val GridColumnSpacing = 12.dp
private val GridRowSpacing = 16.dp
private const val LOAD_MORE_ROWS = 4
private const val LOAD_MORE_THRESHOLD = LOAD_MORE_ROWS * SHOW_ALL_GRID_COLUMNS
private val EmptyResetGap = 12.dp
private const val RESTORE_FOCUS_ATTEMPTS = 3

@Composable
internal fun ShowAllScreenContent(
    title: String,
    state: ShowAllViewState,
    onAction: (UIAction) -> Unit,
    filter: ShowAllFilterState = ShowAllFilterState.Unavailable,
) {
    val filtersFocus = remember { FocusRequester() }
    val rowOneFocus = remember { FocusRequester() }
    val emptyResetFocus = remember { FocusRequester() }
    // Whether focus last left the grid for the header. A side rail opened from «Фильтры» hands focus
    // back to whoever held it, and the grid must not answer that handoff with a card.
    val headerHoldsFocus = remember { mutableStateOf(false) }

    // «Фильтры» sits ahead of the grid, so on a cold open it would be the first focusable thing and
    // the grid's own first-focus request would never be the one that lands. It only becomes
    // focusable once the first list has landed, and stays so: a reset or a new filter shows the
    // loading state again with focus parked on it.
    var filtersCanFocus by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state is ShowAllViewState.Loading) {
        if (state !is ShowAllViewState.Loading) filtersCanFocus = true
    }
    val downTarget = when {
        state is ShowAllViewState.Content -> rowOneFocus
        state is ShowAllViewState.Empty && filter.isActive -> emptyResetFocus
        else -> FocusRequester.Default
    }
    val upTarget = filtersFocus.takeIf { filter.available && filtersCanFocus }

    // Closing the panel, by Back or by applying, puts focus back on «Фильтры»; without a restorer it
    // would fall to the side rail.
    val headerRestorer = remember(filtersFocus) {
        TvDialogFocusRestorer(
            onDialogOpening = {},
            onDialogClosed = { runCatching { filtersFocus.requestFocus() } },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ShowAllHeader(
                title = title,
                filter = filter,
                filtersFocus = filtersFocus,
                canFocus = filtersCanFocus,
                downTarget = downTarget,
                onAction = onAction,
                modifier = Modifier.onFocusChanged { if (it.hasFocus) headerHoldsFocus.value = true },
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // A list that lands behind the open panel must not run its first-focus request.
                ContentFocusGate(active = !filter.panelOpen) {
                    ShowAllList(
                        state = state,
                        filter = filter,
                        onAction = onAction,
                        filtersFocus = filtersFocus,
                        rowOneFocus = rowOneFocus,
                        emptyResetFocus = emptyResetFocus,
                        upTarget = upTarget,
                        headerHoldsFocus = headerHoldsFocus,
                    )
                }
            }
        }
        if (filter.panelOpen) {
            CompositionLocalProvider(LocalTvDialogFocusRestorer provides headerRestorer) {
                ShowAllFilterPanel(filter = filter, onAction = onAction)
            }
        }
    }
}

@Composable
private fun ShowAllList(
    state: ShowAllViewState,
    filter: ShowAllFilterState,
    onAction: (UIAction) -> Unit,
    filtersFocus: FocusRequester,
    rowOneFocus: FocusRequester,
    emptyResetFocus: FocusRequester,
    upTarget: FocusRequester?,
    headerHoldsFocus: MutableState<Boolean>,
) {
    when (state) {
        ShowAllViewState.Loading -> FullScreenProgressIndicator()
        ShowAllViewState.Empty -> ShowAllEmpty(
            filter = filter,
            filtersFocus = filtersFocus,
            resetFocus = emptyResetFocus,
            headerHoldsFocus = headerHoldsFocus,
            onAction = onAction,
        )
        is ShowAllViewState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.message)
        }
        // Keyed on the query: a new list can land without a Loading frame in between (a cached base
        // page after «Сброс»), and without a remount the grid's first-focus request never fires
        // and focus stays on «Фильтры».
        is ShowAllViewState.Content -> key(state.queryGeneration) {
            ShowAllContentBody(
                state = state,
                onAction = onAction,
                upTarget = upTarget,
                rowOneFocus = rowOneFocus,
                headerHoldsFocus = headerHoldsFocus,
            )
        }
    }
}

/**
 * Nothing to show: the plain empty text, or while a filter is applied what it found and a way out.
 * On a cold open with a restored filter nothing holds focus (the header is gated while loading), so
 * «Сброс» takes it; after a panel close focus is already on «Фильтры» and is left there.
 */
@Composable
private fun ShowAllEmpty(
    filter: ShowAllFilterState,
    filtersFocus: FocusRequester,
    resetFocus: FocusRequester,
    headerHoldsFocus: MutableState<Boolean>,
    onAction: (UIAction) -> Unit,
) {
    val contentFocusActive = LocalContentFocusActive.current
    // A tab pick in flight hands focus to the arriving content; taking it here from the outgoing
    // content would steal it. Content born during the request is the arrival and keeps its request,
    // the same test as ContentFocusHandoffEffect.
    val handoff = LocalContentFocusHandoff.current
    val bornDuringRequest = remember { handoff?.pendingRequestId }
    val outgoingDuringTabPick = handoff?.expectsNewContent == true && bornDuringRequest != handoff.pendingRequestId
    LaunchedEffect(filter.isActive, contentFocusActive, outgoingDuringTabPick) {
        val mayTakeFocus = contentFocusActive && !outgoingDuringTabPick
        if (filter.isActive && mayTakeFocus && !headerHoldsFocus.value) {
            runCatching { resetFocus.requestFocus() }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (filter.isActive) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.show_all_filtered_empty, filterSummary(filter)))
                Spacer(Modifier.height(EmptyResetGap))
                TvCompactButton(
                    text = stringResource(R.string.show_all_filters_reset),
                    onClick = {
                        runCatching { filtersFocus.requestFocus() }
                        onAction(ShowAllAction.FilterReset)
                    },
                    modifier = Modifier
                        .focusRequester(resetFocus)
                        .focusProperties { up = filtersFocus },
                    outlined = true,
                )
            }
        } else {
            Text(stringResource(R.string.empty_state))
        }
    }
}

@Composable
private fun ShowAllContentBody(
    state: ShowAllViewState.Content,
    onAction: (UIAction) -> Unit,
    upTarget: FocusRequester?,
    rowOneFocus: FocusRequester,
    headerHoldsFocus: MutableState<Boolean>,
) {
    val mainContentFocus = rememberFocusRequesterOnLaunch()
    val gridState = rememberLazyGridState()
    var contextMenuItem by remember { mutableStateOf<VideoItemUIState?>(null) }

    // The row-1 card focus last left from, which Down from the header returns to; the first card
    // until one has been focused.
    var lastRowOneId by remember { mutableStateOf<Int?>(null) }
    val rowOneTargetId = state.items.take(SHOW_ALL_GRID_COLUMNS).firstOrNull { it.id == lastRowOneId }?.id
        ?: state.items.firstOrNull()?.id

    val restoreFocus = remember { FocusRequester() }
    val focusMemory = rememberGridFocusMemory(state.items, restoreFocus, mainContentFocus, headerHoldsFocus)

    // Closing the menu puts focus back on the card that opened it; without a restorer focus falls
    // to the side rail.
    val dialogFocusRestorer = remember(mainContentFocus) {
        TvDialogFocusRestorer(
            onDialogOpening = { runCatching { mainContentFocus.saveFocusedChild() } },
            onDialogClosed = {
                val restored = runCatching { mainContentFocus.restoreFocusedChild() }.getOrDefault(false)
                if (!restored) runCatching { mainContentFocus.requestFocus() }
            },
        )
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItemsCount = gridState.layoutInfo.totalItemsCount
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            lastVisibleItem.index > (totalItemsCount - LOAD_MORE_THRESHOLD - 1)
        }
    }

    // Keyed on the size too: a page smaller than the threshold leaves the flag true after an append.
    LaunchedEffect(shouldLoadMore, state.items.size) {
        if (shouldLoadMore) {
            onAction(CommonAction.LoadMore)
        }
    }

    CompositionLocalProvider(LocalTvDialogFocusRestorer provides dialogFocusRestorer) {
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(SHOW_ALL_GRID_COLUMNS),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(mainContentFocus)
                    .onFocusChanged { focusMemory.onGridFocusChanged(it.hasFocus) },
                contentPadding = PaddingValues(
                    start = GridContentPadding,
                    top = GridTopPadding,
                    end = GridContentPadding,
                    bottom = GridContentPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(GridColumnSpacing),
                verticalArrangement = Arrangement.spacedBy(GridRowSpacing),
            ) {
                itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                    val clickCallback = remember(item.id) { { onAction(CommonAction.ItemSelected(item)) } }
                    VideoItemPoster(
                        state = item,
                        modifier = Modifier
                            .then(
                                if (index < SHOW_ALL_GRID_COLUMNS && upTarget != null) {
                                    Modifier.focusProperties { up = upTarget }
                                } else {
                                    Modifier
                                },
                            )
                            .then(
                                if (item.id == rowOneTargetId) Modifier.focusRequester(rowOneFocus) else Modifier,
                            )
                            .then(
                                if (item.id == focusMemory.restoreId) {
                                    Modifier.focusRequester(restoreFocus)
                                } else {
                                    Modifier
                                },
                            )
                            .onFocusChanged {
                                if (it.hasFocus) {
                                    focusMemory.onCardFocused(item.id, index)
                                    if (index < SHOW_ALL_GRID_COLUMNS) lastRowOneId = item.id
                                }
                            },
                        onClick = clickCallback,
                        onContextMenu = { contextMenuItem = item },
                    )
                }
            }
            VideoItemContextMenuDialog(
                item = contextMenuItem,
                onDismiss = { contextMenuItem = null },
                onAction = onAction,
            )
        }
    }
}

/**
 * Remembers the card that held focus when the screen was last left, with its position, and puts
 * focus back on it (or on its neighbour when a reload dropped it). The saved values are read once
 * on entry without observing, so focus moves never recompose the grid. A reload that lands before
 * the entry focus does re-targets the restore, and if no card takes focus the grid itself is focused.
 *
 * A side rail closing hands focus back to the content. The grid has no restorer of its own, so it
 * answers that handoff itself: the id is latched (read directly it is null again once the handoff
 * settles, which would cancel the effect) and each id is answered once.
 */
@Composable
private fun rememberGridFocusMemory(
    items: List<VideoItemUIState>,
    restoreFocus: FocusRequester,
    gridFocus: FocusRequester,
    headerHoldsFocus: MutableState<Boolean>,
): GridFocusMemory {
    val lastFocusedId = rememberSaveable { mutableStateOf<Int?>(null) }
    val lastFocusedIndex = rememberSaveable { mutableStateOf(0) }
    val currentItems by rememberUpdatedState(items)
    val contentFocusActive by rememberUpdatedState(LocalContentFocusActive.current)
    val memory = remember {
        GridFocusMemory(
            lastFocusedId = lastFocusedId,
            lastFocusedIndex = lastFocusedIndex,
            items = { currentItems },
            contentFocusActive = { contentFocusActive },
            requestRestore = { restoreFocus.tryRequestFocus() },
            requestGrid = { gridFocus.tryRequestFocus() },
            headerHoldsFocus = headerHoldsFocus,
        )
    }
    var latchedHandoffId by remember { mutableStateOf<Long?>(null) }
    val handoffId = LocalContentFocusHandoff.current?.pendingRequestId
    if (handoffId != null && handoffId != latchedHandoffId) latchedHandoffId = handoffId
    LaunchedEffect(items, contentFocusActive) { memory.reconcile() }
    LaunchedEffect(latchedHandoffId, contentFocusActive) {
        latchedHandoffId?.let { memory.onHandoff(it) }
    }
    return memory
}

internal class GridFocusMemory(
    private val lastFocusedId: MutableState<Int?>,
    private val lastFocusedIndex: MutableState<Int>,
    private val items: () -> List<VideoItemUIState>,
    private val contentFocusActive: () -> Boolean,
    private val requestRestore: () -> Boolean,
    private val requestGrid: () -> Boolean,
    private val headerHoldsFocus: MutableState<Boolean> = mutableStateOf(false),
) {
    var restoreId by mutableStateOf(
        Snapshot.withoutReadObservation {
            resolveRestoreId(lastFocusedId.value, lastFocusedIndex.value, items())
        },
    )
        private set

    // True from a re-entry with a saved card until focus first lands in the grid.
    private var pendingEntry = Snapshot.withoutReadObservation { lastFocusedId.value != null }

    // True while focus sits in the grid and was not taken away by the focused card leaving the list.
    private var inGrid = false

    private var answeredHandoffId: Long? = null

    fun onCardFocused(id: Int, index: Int) {
        lastFocusedId.value = id
        lastFocusedIndex.value = index
    }

    fun onGridFocusChanged(hasFocus: Boolean) {
        // A lost focus with the focused card gone from the list is not a user move.
        if (hasFocus) {
            inGrid = true
            headerHoldsFocus.value = false
            pendingEntry = false
        } else if (items().any { it.id == lastFocusedId.value }) {
            inGrid = false
        }
    }

    suspend fun reconcile() {
        val focusedId = lastFocusedId.value
        val focusedCardLeft = inGrid && focusedId != null && items().none { it.id == focusedId }
        if (!pendingEntry && !focusedCardLeft) return
        restoreId = resolveRestoreId(focusedId, lastFocusedIndex.value, items())
        if (restoreId == null || requestRestoreFocus()) return
        // Last resort for a pending entry or a focused card that left the list: never leave the grid without focus.
        if ((pendingEntry || focusedCardLeft) && contentFocusActive()) requestGrid()
    }

    /**
     * Answers a focus handoff from the side rail by focusing the remembered card again. Without a
     * remembered card, with focus already in the grid, with focus last held by the header or with the
     * content inactive it does nothing,
     * and an id that was already answered is ignored, so a later recomposition never takes focus back.
     */
    suspend fun onHandoff(requestId: Long) {
        if (requestId == answeredHandoffId || !contentFocusActive()) return
        answeredHandoffId = requestId
        val focusedId = lastFocusedId.value
        if (inGrid || focusedId == null || headerHoldsFocus.value) return
        restoreId = resolveRestoreId(focusedId, lastFocusedIndex.value, items())
        if (restoreId != null) requestRestoreFocus()
    }

    private suspend fun requestRestoreFocus(): Boolean {
        repeat(RESTORE_FOCUS_ATTEMPTS) {
            withFrameNanos { }
            if (!contentFocusActive()) return false
            if (requestRestore()) return true
        }
        return false
    }
}

// The opener card, or its neighbour at the same position when a reload dropped it.
internal fun resolveRestoreId(id: Int?, index: Int, items: List<VideoItemUIState>): Int? = when {
    id == null || items.isEmpty() -> null
    items.any { it.id == id } -> id
    else -> items[index.coerceIn(0, items.lastIndex)].id
}

private fun FocusRequester.tryRequestFocus(): Boolean = runCatching { requestFocus() }.getOrDefault(false)
