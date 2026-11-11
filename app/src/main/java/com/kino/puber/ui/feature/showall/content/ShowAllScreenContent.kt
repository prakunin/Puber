package com.kino.puber.ui.feature.showall.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.VideoItemContextMenuDialog
import com.kino.puber.core.ui.uikit.component.drawer.LocalContentFocusHandoff
import com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemHorizontal
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.showall.model.ShowAllViewState

private const val GRID_COLUMNS = 3
private const val LOAD_MORE_THRESHOLD = 12
private const val RESTORE_FOCUS_ATTEMPTS = 3

@Composable
internal fun ShowAllScreenContent(
    state: ShowAllViewState,
    onAction: (UIAction) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            ShowAllViewState.Loading -> FullScreenProgressIndicator()
            ShowAllViewState.Empty -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.empty_state))
            }
            is ShowAllViewState.Error -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.message)
            }
            is ShowAllViewState.Content -> ShowAllContentBody(
                state = state,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun ShowAllContentBody(
    state: ShowAllViewState.Content,
    onAction: (UIAction) -> Unit,
) {
    val mainContentFocus = rememberFocusRequesterOnLaunch()
    val gridState = rememberLazyGridState()
    var contextMenuItem by remember { mutableStateOf<VideoItemUIState?>(null) }

    val restoreFocus = remember { FocusRequester() }
    val focusMemory = rememberGridFocusMemory(state.items, restoreFocus, mainContentFocus)

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItemsCount = gridState.layoutInfo.totalItemsCount
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            lastVisibleItem.index > (totalItemsCount - LOAD_MORE_THRESHOLD - 1)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onAction(CommonAction.LoadMore)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(mainContentFocus)
                .onFocusChanged { focusMemory.onGridFocusChanged(it.hasFocus) },
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                val clickCallback = remember(item.id) { { onAction(CommonAction.ItemSelected(item)) } }
                VideoItemHorizontal(
                    state = item,
                    modifier = Modifier
                        .then(
                            if (item.id == focusMemory.restoreId) {
                                Modifier.focusRequester(restoreFocus)
                            } else {
                                Modifier
                            },
                        )
                        .onFocusChanged { if (it.hasFocus) focusMemory.onCardFocused(item.id, index) },
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
        // Last resort for a pending entry only: never leave the re-entered grid without focus.
        if (pendingEntry && contentFocusActive()) requestGrid()
    }

    /**
     * Answers a focus handoff from the side rail by focusing the remembered card again. Without a
     * remembered card, with focus already in the grid, or with the content inactive it does nothing,
     * and an id that was already answered is ignored, so a later recomposition never takes focus back.
     */
    suspend fun onHandoff(requestId: Long) {
        if (requestId == answeredHandoffId || !contentFocusActive()) return
        answeredHandoffId = requestId
        val focusedId = lastFocusedId.value
        if (inGrid || focusedId == null) return
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
