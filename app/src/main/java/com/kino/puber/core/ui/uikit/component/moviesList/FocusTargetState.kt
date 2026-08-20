package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.lazy.LazyListState
import com.kino.puber.core.ui.navigation.component.LocalRootAnchorFocusRestored
import com.kino.puber.core.ui.navigation.component.LocalRootAnchorRestoreCompletion
import com.kino.puber.core.ui.navigation.component.LocalScreenKey
import com.kino.puber.core.ui.navigation.component.RootAnchorRestoreCompletion
import com.kino.puber.core.ui.uikit.component.drawer.LocalContentFocusHandoff
import com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive
import com.kino.puber.core.ui.uikit.state.sessionMutableStateSaver

internal class ReconciledItemFocusState(
    val targetItemId: Int?,
    val focusRequester: FocusRequester,
    val rowHasFocusRef: BooleanArray,
    val onItemFocused: (Int) -> Unit,
)

private data class PendingItemFocus(
    val itemId: Int? = null,
    val canRestoreInactiveContent: Boolean = false,
)

@Composable
internal fun rememberReconciledItemFocus(
    rowKey: String,
    items: List<VideoItemUIState>,
    isTargetRow: Boolean,
    initialFocusedItemId: Int? = null,
    requestAfterFrame: Boolean = false,
    restoreAcrossProcess: Boolean = true,
    onRowEmpty: () -> Unit,
): ReconciledItemFocusState {
    val initialItemId = initialFocusedItemId ?: items.firstOrNull()?.id
    val focusedItemId = if (restoreAcrossProcess) {
        rememberSaveable(rowKey) { mutableStateOf(initialItemId) }
    } else {
        rememberSaveable(rowKey, saver = sessionMutableStateSaver()) { mutableStateOf(initialItemId) }
    }
    val previousItems = remember(rowKey) { mutableStateOf(items) }
    val pendingFocus = remember(rowKey) { mutableStateOf(PendingItemFocus()) }
    val focusRequester = remember { FocusRequester() }
    val rowHasFocusRef = remember { booleanArrayOf(false) }
    val contentFocusActive = LocalContentFocusActive.current
    if (!contentFocusActive || !isTargetRow) {
        rowHasFocusRef[0] = false
    }
    val onRootFocusRestored = LocalRootAnchorFocusRestored.current
    val rootAnchorRestoreCompletion = LocalRootAnchorRestoreCompletion.current
    val handoffRequestId = LocalContentFocusHandoff.current?.pendingRequestId
    val screenKey = LocalScreenKey.current
    val targetItemId = resolveFocusedItemId(
        previousItems = previousItems.value,
        updatedItems = items,
        focusedItemId = focusedItemId.value,
    )
    val canRestoreInactiveContent =
        targetItemId != null &&
            targetItemId != focusedItemId.value &&
            contentFocusActive &&
            rowHasFocusRef[0]

    ReconcileInitialItemFocusEffect(
        initialFocusedItemId = initialFocusedItemId,
        items = items,
        focusedItemId = focusedItemId,
        pendingFocus = pendingFocus,
    )
    ReconcilePublishedItemsEffect(
        items = items,
        isTargetRow = isTargetRow,
        focusedItemId = focusedItemId,
        previousItems = previousItems,
        canRestoreInactiveContent = canRestoreInactiveContent,
        pendingFocus = pendingFocus,
        onRowEmpty = onRowEmpty,
    )
    RequestReconciledItemFocusEffects(
        isTargetRow = isTargetRow,
        targetItemId = targetItemId,
        focusRequester = focusRequester,
        rowHasFocusRef = rowHasFocusRef,
        pendingFocus = pendingFocus,
        requestAfterFrame = requestAfterFrame,
        contentFocusActive = contentFocusActive,
        rootAnchorRestoreCompletion = rootAnchorRestoreCompletion,
        screenKey = screenKey,
    )
    RestoreItemFocusOnTabReentryEffect(
        isTargetRow = isTargetRow,
        targetItemId = targetItemId,
        focusRequester = focusRequester,
        contentFocusActive = contentFocusActive,
        rowHasFocusRef = rowHasFocusRef,
        handoffRequestId = handoffRequestId,
    )

    return reconciledItemFocusState(
        targetItemId, focusRequester, rowHasFocusRef, focusedItemId, isTargetRow, onRootFocusRestored,
        rowKey, LocalDetailsPrefetchSurface.current,
    )
}

private fun reconciledItemFocusState(
    targetItemId: Int?,
    focusRequester: FocusRequester,
    rowHasFocusRef: BooleanArray,
    focusedItemId: MutableState<Int?>,
    isTargetRow: Boolean,
    onRootFocusRestored: () -> Unit,
    rowKey: String,
    prefetchSurface: DetailsPrefetchSurfaceState?,
) = ReconciledItemFocusState(
    targetItemId = targetItemId,
    focusRequester = focusRequester,
    rowHasFocusRef = rowHasFocusRef,
    onItemFocused = { itemId ->
        rowHasFocusRef[0] = true
        focusedItemId.value = itemId
        if (isTargetRow && itemId == targetItemId) {
            onRootFocusRestored()
        }
        // Where a card announces itself as worth fetching. Without an opted-in surface and a
        // registered row this resolves to nothing, so no row pays for it that did not ask.
        prefetchSurface?.onItemFocused(rowKey, itemId)
    },
)

@Composable
private fun ReconcileInitialItemFocusEffect(
    initialFocusedItemId: Int?,
    items: List<VideoItemUIState>,
    focusedItemId: MutableState<Int?>,
    pendingFocus: MutableState<PendingItemFocus>,
) {
    LaunchedEffect(initialFocusedItemId) {
        if (
            initialFocusedItemId != null &&
            initialFocusedItemId != focusedItemId.value &&
            items.any { it.id == initialFocusedItemId }
        ) {
            focusedItemId.value = initialFocusedItemId
            pendingFocus.value = PendingItemFocus(itemId = initialFocusedItemId)
        }
    }
}

@Composable
private fun ReconcilePublishedItemsEffect(
    items: List<VideoItemUIState>,
    isTargetRow: Boolean,
    focusedItemId: MutableState<Int?>,
    previousItems: MutableState<List<VideoItemUIState>>,
    canRestoreInactiveContent: Boolean,
    pendingFocus: MutableState<PendingItemFocus>,
    onRowEmpty: () -> Unit,
) {
    LaunchedEffect(items) {
        val nextFocusedItemId = resolveFocusedItemId(
            previousItems = previousItems.value,
            updatedItems = items,
            focusedItemId = focusedItemId.value,
        )
        if (nextFocusedItemId != focusedItemId.value) {
            focusedItemId.value = nextFocusedItemId
            pendingFocus.value = PendingItemFocus(
                itemId = nextFocusedItemId,
                canRestoreInactiveContent = canRestoreInactiveContent,
            )
        }
        previousItems.value = items
        if (items.isEmpty() && isTargetRow) {
            onRowEmpty()
        }
    }
}

@Composable
private fun RequestReconciledItemFocusEffects(
    isTargetRow: Boolean,
    targetItemId: Int?,
    focusRequester: FocusRequester,
    rowHasFocusRef: BooleanArray,
    pendingFocus: MutableState<PendingItemFocus>,
    requestAfterFrame: Boolean,
    contentFocusActive: Boolean,
    rootAnchorRestoreCompletion: RootAnchorRestoreCompletion,
    screenKey: String?,
) {
    LaunchedEffect(isTargetRow, contentFocusActive) {
        val targetCanReceiveFocus = isTargetRow && contentFocusActive && targetItemId != null
        if (targetCanReceiveFocus && !rowHasFocusRef[0]) {
            focusRequester.requestAfterComposition(requestAfterFrame)
        }
    }
    LaunchedEffect(pendingFocus.value, contentFocusActive) {
        val request = pendingFocus.value
        val canRequestFocus = contentFocusActive || request.canRestoreInactiveContent
        if (isTargetRow && canRequestFocus && request.itemId != null) {
            focusRequester.requestAfterComposition(requestAfterFrame)
            pendingFocus.value = PendingItemFocus()
        }
    }
    LaunchedEffect(rootAnchorRestoreCompletion.version) {
        val matchingCompletedRestore =
            rootAnchorRestoreCompletion.screenKey == screenKey &&
                rootAnchorRestoreCompletion.version > 0
        val targetCanReceiveFocus = isTargetRow && contentFocusActive && targetItemId != null
        if (matchingCompletedRestore && targetCanReceiveFocus) {
            focusRequester.requestAfterAnchorRestore()
        }
    }
}

/**
 * Restores focus into the row and card the user left when a tab is re-entered, the way returning
 * from a details screen already does.
 *
 * The position itself was never lost — it is the `rememberSaveable` state the tab keeps across a
 * switch. Only the trigger was missing, and an in-flight focus handoff is it.
 *
 * The handoff id is latched rather than used directly. Read directly it is null again the moment
 * the handoff settles, which cancels this effect; and a tab whose first page has not arrived yet
 * has no `targetItemId` to aim at on the first pass, so the one attempt would be spent on nothing.
 * Latched, the effect re-runs when the target does appear. [rowHasFocusRef] is what stops it from
 * yanking focus back later: once this row holds focus the restore has either happened or been
 * overtaken by the user.
 */
@Composable
private fun RestoreItemFocusOnTabReentryEffect(
    isTargetRow: Boolean,
    targetItemId: Int?,
    focusRequester: FocusRequester,
    contentFocusActive: Boolean,
    rowHasFocusRef: BooleanArray,
    handoffRequestId: Long?,
) {
    var latchedRequestId by remember { mutableStateOf<Long?>(null) }
    if (handoffRequestId != null && handoffRequestId != latchedRequestId) {
        latchedRequestId = handoffRequestId
    }

    LaunchedEffect(latchedRequestId, targetItemId, isTargetRow, contentFocusActive) {
        val targetCanReceiveFocus = isTargetRow && contentFocusActive && targetItemId != null
        if (latchedRequestId != null && targetCanReceiveFocus && !rowHasFocusRef[0]) {
            focusRequester.requestAfterAnchorRestore()
        }
    }
}

private suspend fun FocusRequester.requestAfterAnchorRestore() {
    repeat(ROOT_ANCHOR_FOCUS_REQUEST_ATTEMPTS) {
        withFrameNanos { }
        if (requestFocus()) return
    }
}

private suspend fun FocusRequester.requestAfterComposition(awaitFrame: Boolean) {
    if (awaitFrame) {
        withFrameNanos { }
    }
    requestFocus()
}

private const val ROOT_ANCHOR_FOCUS_REQUEST_ATTEMPTS = 3

internal class ReconciledRowFocusState(
    val focusedRowKey: String?,
    val onRowFocused: (String) -> Unit,
    val onRowEmpty: (Int) -> Unit,
)

@Composable
internal fun rememberReconciledRowFocus(
    rows: List<FocusableRow>,
    initialRowKey: String? = null,
    resetKey: Any? = Unit,
    restoreAcrossProcess: Boolean = true,
): ReconciledRowFocusState {
    val focusedRowKey = if (restoreAcrossProcess) {
        rememberSaveable(resetKey) { mutableStateOf(initialRowKey) }
    } else {
        rememberSaveable(resetKey, saver = sessionMutableStateSaver()) { mutableStateOf(initialRowKey) }
    }
    val previousRows = remember { mutableStateOf(rows) }
    val resolvedRowKey = resolveFocusedRowKey(
        previousRows = previousRows.value,
        updatedRows = rows,
        focusedRowKey = focusedRowKey.value,
    )
    LaunchedEffect(rows) {
        focusedRowKey.value = resolvedRowKey
        previousRows.value = rows
    }
    return ReconciledRowFocusState(
        focusedRowKey = resolvedRowKey,
        onRowFocused = { rowKey -> focusedRowKey.value = rowKey },
        onRowEmpty = { rowIndex ->
            focusedRowKey.value = nearestNonEmptyRowKey(
                rows = rows,
                emptyRowIndex = rowIndex,
            )
        },
    )
}

internal class VideoGridFocusState(
    val rows: List<FocusableRow>,
    val rowFocus: ReconciledRowFocusState,
    /**
     * The row the grid should be scrolled to when it first appears, named rather than numbered.
     *
     * An index would have to mean a position in whatever list the caller actually draws, and the
     * grid draws two different ones: the entries as they come, or a season and its episodes merged
     * into a single item. A key means the same row in both.
     */
    val initialRowKey: String?,
)

@Composable
internal fun rememberVideoGridFocusState(
    list: List<VideoGridItemUIState>,
    initialFocusedItemId: Int?,
): VideoGridFocusState {
    val initialColumnIndex = remember(list, initialFocusedItemId) {
        list.indexOfFirst { gridItem ->
            gridItem is VideoGridItemUIState.Items &&
                gridItem.items.any { it.id == initialFocusedItemId }
        }
    }
    val initialRowKey = remember(list, initialColumnIndex) {
        (list.getOrNull(initialColumnIndex) as? VideoGridItemUIState.Items)?.rowKey
    }
    val rows = remember(list) {
        list.filterIsInstance<VideoGridItemUIState.Items>()
            .map { row -> FocusableRow(row.rowKey, row.items.size) }
    }
    val rowFocus = rememberReconciledRowFocus(
        rows = rows,
        initialRowKey = initialRowKey,
        resetKey = initialFocusedItemId,
    )
    return VideoGridFocusState(rows = rows, rowFocus = rowFocus, initialRowKey = initialRowKey)
}
