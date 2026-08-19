package com.kino.puber.ui.feature.contentlist.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.VideoItemContextMenuDialog
import com.kino.puber.core.ui.uikit.component.GenreChipBar
import com.kino.puber.core.ui.uikit.component.HeroCarousel
import com.kino.puber.core.ui.uikit.component.PositionFocusedItemInLazyLayout
import com.kino.puber.core.ui.uikit.component.details.VideoItemGridDetails
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.core.ui.uikit.theme.SectionTitleStyle
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.component.moviesList.DetailsPrefetchRow
import com.kino.puber.core.ui.uikit.component.moviesList.DetailsPrefetchSurface
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.FocusableRow
import com.kino.puber.core.ui.uikit.component.moviesList.nearestNonEmptyRowKey
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.contentlist.model.ContentListAction
import com.kino.puber.ui.feature.contentlist.model.ContentListViewState
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.model.SectionState
import com.kino.puber.ui.feature.contentlist.vm.SectionVM
import com.kino.puber.core.di.LocalPuberKoinScope
import com.kino.puber.core.ui.navigation.component.PreserveLazyListAnchorOnRootReturn
import org.koin.core.qualifier.named

@Composable
internal fun ContentListScreenContent(
    state: ContentListViewState,
    sections: List<SectionConfig>,
    onAction: (UIAction) -> Unit,
) {
    val mainContentFocus = rememberFocusRequesterOnLaunch()
    // Which *section* owns focus restoration and the empty-row handoff. Deliberately not the same
    // thing as the list-item index `rememberFocusedListItemScroller` is given: that one counts the
    // hero as an item, this one does not, and folding either into the other would push the hero
    // offset into a `rememberSaveable` that also drives `isTargetRow`. They answer different
    // questions; do not simplify one into the other.
    var focusedSectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var contextMenuTarget by remember { mutableStateOf<ContentListContextMenuTarget?>(null) }

    // Navigating away or switching tabs drops this composition while the view model lives on. The
    // player goes with the composition, but `previewTrailerUrl` would not, so coming back would
    // resume a trailer from zero with sound and none of the two-second pause that starts one.
    val currentOnAction by rememberUpdatedState(onAction)
    DisposableEffect(Unit) {
        onDispose { currentOnAction(ContentListAction.TrailerPreviewStopped) }
    }

    val scope = LocalPuberKoinScope.current ?: return
    val sectionVms = remember {
        sections.map { config -> scope.get<SectionVM>(named(config.id)) }
    }
    val sectionStates = sectionVms.mapIndexed { index, vm ->
        key(sections[index].id) {
            val s by vm.collectState()
            s
        }
    }

    androidx.compose.foundation.layout.Box {
        DetailsPrefetchSurface {
            ContentListLayout(
                state = state,
                sections = sections,
                sectionVms = sectionVms,
                sectionStates = sectionStates,
                focusedSectionIndex = focusedSectionIndex,
                onSectionFocused = { focusedSectionIndex = it },
                onContextMenu = { item, sectionVm ->
                    contextMenuTarget = ContentListContextMenuTarget(item, sectionVm)
                },
                onAction = onAction,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(mainContentFocus),
            )
        }

        val activeContextMenuTarget = contextMenuTarget
        VideoItemContextMenuDialog(
            item = activeContextMenuTarget?.item,
            onDismiss = { contextMenuTarget = null },
            onAction = { action ->
                if (action is CommonAction.ItemSavedChanged<*>) {
                    activeContextMenuTarget?.sectionVm?.onAction(action)
                } else {
                    onAction(action)
                }
            },
        )
    }
}

@Composable
private fun ContentListLayout(
    state: ContentListViewState,
    sections: List<SectionConfig>,
    sectionVms: List<SectionVM>,
    sectionStates: List<SectionState>,
    focusedSectionIndex: Int,
    onSectionFocused: (Int) -> Unit,
    onContextMenu: (VideoItemUIState, SectionVM) -> Unit,
    onAction: (UIAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    PreserveLazyListAnchorOnRootReturn(lazyListState)
    var rowsHaveFocus by remember { mutableStateOf(false) }
    val isHeroItemPresent = state.isHeroLoading || state.heroItems.isNotEmpty()
    val onListItemFocused = rememberFocusedListItemScroller(lazyListState)

    // One list item per screen is a rule about the space the detail panel leaves over, not about
    // catalogue tabs in general. With the panel present the list viewport is a hair taller than a
    // single section, so making each one a page removes the half-row of the next section that
    // would otherwise peek in. `NavigationMode.TopTabs` has no panel (`ContentListVM.onStart`
    // clears `showDetailPanel`), and its viewport is roughly twice as tall: pages there would
    // leave better than half of every screen blank and cost a full screen of scrolling per
    // section, where the same tab used to show two sections at once. So the page rule follows the
    // panel, and without it the hero and the sections keep their own heights.
    val itemsFillViewport = state.showDetailPanel

    // Declared here rather than from inside the rows, because a `DetailsPrefetchRow` inside the
    // `LazyColumn` only exists while the `LazyColumn` has composed it. `FocusNeighbourhood`
    // prefetches the card one row below the focused one, and under the page layout exactly one
    // section is composed at rest -- so the row below was never registered when it was asked for,
    // and moving Down into a new section went to the network for the detail panel instead of
    // finding it warm. The section list is the honest source of what rows exist; a section with
    // no content registers an empty row, which yields no candidate exactly as an unregistered
    // one did.
    sections.forEachIndexed { index, config ->
        DetailsPrefetchRow(
            rowOrder = index,
            rowKey = config.id,
            items = (sectionStates[index] as? SectionState.Content)?.items.orEmpty(),
        )
    }

    Column(modifier = modifier) {
        if (state.showDetailPanel) {
            VideoItemGridDetails(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(PuberTheme.Defaults.DetailsWeight),
                state = state.selectedItem,
                trailerUrl = state.previewTrailerUrl,
                onTrailerFinished = { onAction(ContentListAction.TrailerPreviewFinished) },
            )
        }

        if (state.showGenreChips && state.genres.isNotEmpty()) {
            GenreChipBar(
                genres = state.genres,
                selectedGenreId = state.selectedGenreId,
                onGenreSelected = { genreId -> onAction(ContentListAction.GenreSelected(genreId)) },
            )
        }

        PositionFocusedItemInLazyLayout(keepFullyVisibleItemInPlace = true) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (state.showDetailPanel) PuberTheme.Defaults.ContentWeight else 1f)
                    // Focus leaving the rows entirely — into the side rail, most often by pressing
                    // LEFT from the first card of a row — is not seen by `ItemFocused`, which only
                    // ever reports a card gaining focus. Without this the trailer keeps playing,
                    // with sound, over a screen the user has navigated out of.
                    .onFocusChanged { focusState ->
                        if (rowsHaveFocus && !focusState.hasFocus) {
                            onAction(ContentListAction.TrailerPreviewStopped)
                        }
                        rowsHaveFocus = focusState.hasFocus
                    }
                    .focusRestorer()
                    .focusGroup(),
            ) {
                heroItem(
                    state = state,
                    isHeroItemPresent = isHeroItemPresent,
                    fillsViewport = itemsFillViewport,
                    onAction = onAction,
                    onFocused = { onListItemFocused(0) },
                )
                sectionItems(
                    sections = sections,
                    sectionVms = sectionVms,
                    sectionStates = sectionStates,
                    focusedSectionIndex = focusedSectionIndex,
                    isHeroItemPresent = isHeroItemPresent,
                    fillsViewport = itemsFillViewport,
                    onSectionFocused = onSectionFocused,
                    onListItemFocused = onListItemFocused,
                    onContextMenu = onContextMenu,
                    onAction = onAction,
                )
            }
        }
    }
}

// The vertical BringIntoViewSpec set up by PositionFocusedItemInLazyLayout settles whatever rect
// asked to be brought into view — which, by default, is the focused card, not the list item
// around it (a section, or the hero).
//
// The returned function names whichever list item currently holds real focus — the hero, or a
// section in any state (Content, Loading, Error) — and is meant to be invoked from every
// focusable region in [lazyListState]'s content, not only from Content-card focus. While a real
// target is named, this explicitly scrolls so that item — not the card inside it — settles at the
// viewport top. The *target index* this computes is exact, independent of heading size, gap, row
// padding or card height. It races the automatic per-card bring-into-view request triggered by
// the same focus change, but `LazyListState` serializes scroll mutations and the most recently
// issued one wins, so this is what the list settles on.
//
// Naming the list item rather than "which section" also survives leaving a section for the hero
// and coming straight back: the hero and the section are different list indices, so the named
// index changes on that round trip even though the section index itself does not, and the
// correction re-fires.
//
// Once settled, the automatic per-card request degrades to a no-op only while the section's
// heading + gap + row padding + card height together fit inside the viewport — that is what makes
// the card "fully visible" and lets `keepFullyVisibleItemInPlace` short-circuit it to a
// zero-distance scroll. That holds for today's numbers; it is not guaranteed by anything here, and
// this fix's correctness does not depend on it — only avoiding a second, redundant scroll pass
// does.
//
// The index alone is not a safe `LaunchedEffect` key: callers compute it as an offset (hero
// present or not) plus a position, and that offset can change without any new focus event —
// `ContentListLayout`'s hero can disappear (a failed load) while focus sits still on what was
// already the target section, shifting every section index below it by one. If the next real
// focus event then happens to name the same integer the stale index already held (a different
// section, coincidentally at the same list position under the new offset), keying on the index
// alone would treat it as a no-op and skip the correction it actually needs. `focusEventToken`
// makes every call distinct regardless of the index repeating, so the effect always restarts on
// a real focus event.
@Composable
internal fun rememberFocusedListItemScroller(lazyListState: LazyListState): (Int) -> Unit {
    var focusedListItemIndex by remember { mutableStateOf<Int?>(null) }
    var focusEventToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusedListItemIndex, focusEventToken) {
        val targetItemIndex = focusedListItemIndex ?: return@LaunchedEffect
        lazyListState.animateScrollToItem(index = targetItemIndex, scrollOffset = 0)
    }
    return { index ->
        focusedListItemIndex = index
        focusEventToken++
    }
}

private fun LazyListScope.heroItem(
    state: ContentListViewState,
    isHeroItemPresent: Boolean,
    fillsViewport: Boolean,
    onAction: (UIAction) -> Unit,
    onFocused: () -> Unit,
) {
    if (isHeroItemPresent) {
        item(key = "hero", contentType = "hero") {
            // Off the page layout the carousel keeps the fixed height it sets on itself, and the
            // still-loading placeholder has to name the same number to reserve the same space.
            val heightModifier = if (fillsViewport) {
                Modifier.fillParentMaxHeight()
            } else {
                Modifier.height(HeroCarouselHeight)
            }
            if (state.heroItems.isEmpty()) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(heightModifier),
                )
            } else {
                HeroCarousel(
                    items = state.heroItems,
                    onItemClick = { itemId ->
                        onAction(ContentListAction.HeroSelected(itemId))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (fillsViewport) Modifier.fillParentMaxHeight() else Modifier,
                        ),
                    // D-pad up out of the top row lands here, inside the same focus group as the
                    // rows, so nothing else reports that the focused card is no longer focused.
                    onFocusedItemChanged = {
                        onAction(ContentListAction.TrailerPreviewStopped)
                        onFocused()
                    },
                )
            }
        }
    }
}

private fun LazyListScope.sectionItems(
    sections: List<SectionConfig>,
    sectionVms: List<SectionVM>,
    sectionStates: List<SectionState>,
    focusedSectionIndex: Int,
    isHeroItemPresent: Boolean,
    fillsViewport: Boolean,
    onSectionFocused: (Int) -> Unit,
    onListItemFocused: (Int) -> Unit,
    onContextMenu: (VideoItemUIState, SectionVM) -> Unit,
    onAction: (UIAction) -> Unit,
) {
    sections.forEachIndexed { index, config ->
        sectionItem(
            index = index,
            config = config,
            sectionVm = sectionVms[index],
            sectionState = sectionStates[index],
            isLastSection = index == sections.lastIndex,
            isTargetRow = index == focusedSectionIndex,
            fillsViewport = fillsViewport,
            onSectionFocused = onSectionFocused,
            onRowFocused = { onListItemFocused(sectionListItemIndex(index, isHeroItemPresent)) },
            onContextMenu = onContextMenu,
            onAction = onAction,
            onRowEmpty = {
                val rows = sections.mapIndexed { rowIndex, section ->
                    val itemCount = (sectionStates[rowIndex] as? SectionState.Content)
                        ?.items
                        ?.size
                        ?: 0
                    FocusableRow(section.id, itemCount)
                }
                val targetKey = nearestNonEmptyRowKey(
                    rows = rows,
                    emptyRowIndex = index,
                )
                if (targetKey != null) {
                    onSectionFocused(sections.indexOfFirst { it.id == targetKey })
                }
            },
        )
    }
}

private fun LazyListScope.sectionItem(
    index: Int,
    config: SectionConfig,
    sectionVm: SectionVM,
    sectionState: SectionState,
    isLastSection: Boolean,
    isTargetRow: Boolean,
    fillsViewport: Boolean,
    onSectionFocused: (Int) -> Unit,
    onRowFocused: () -> Unit,
    onContextMenu: (VideoItemUIState, SectionVM) -> Unit,
    onAction: (UIAction) -> Unit,
    onRowEmpty: () -> Unit,
) {
    item(key = "section_${config.id}", contentType = "section") {
        val rememberedOnItemClick = remember(config.id) {
            { item: VideoItemUIState -> onAction(CommonAction.ItemSelected(item)) }
        }
        val rememberedOnItemFocused = remember(config.id) {
            { item: VideoItemUIState -> onAction(CommonAction.ItemFocused(item)) }
        }
        val rememberedOnSectionFocused = remember(index) {
            { onSectionFocused(index) }
        }
        val rememberedOnShowAll = remember(config.id, isLastSection) {
            if (isLastSection) {
                { onAction(ContentListAction.ShowAll(config)) }
            } else {
                null
            }
        }
        val row: @Composable () -> Unit = {
            SectionRowContent(
                state = sectionState,
                config = config,
                isTargetRow = isTargetRow,
                onItemClick = rememberedOnItemClick,
                onItemContextMenu = { onContextMenu(it, sectionVm) },
                onItemFocused = rememberedOnItemFocused,
                onSectionFocused = rememberedOnSectionFocused,
                onFocusChanged = { hasFocus -> if (hasFocus) onRowFocused() },
                onRetry = { sectionVm.onAction(CommonAction.RetryClicked) },
                onLoadMore = { sectionVm.onAction(CommonAction.LoadMore) },
                onShowAll = rememberedOnShowAll,
                onRowEmpty = onRowEmpty,
            )
        }

        SectionListItem(
            title = config.titleRes?.let { stringResource(it) } ?: config.title,
            isEmpty = sectionState is SectionState.Empty,
            fillsViewport = fillsViewport,
            row = row,
        )
    }
}

/**
 * Where section [sectionIndex] sits in the `LazyColumn`, which is what
 * [rememberFocusedListItemScroller] has to be told: the hero, when there is one, is list item 0
 * and pushes every section down by one.
 *
 * Shared with `SectionRowFocusTraversalTest` so the tests exercise this arithmetic rather than a
 * copy of it — the offset changing without the tests noticing is exactly the regression they exist
 * to catch.
 */
internal fun sectionListItemIndex(sectionIndex: Int, isHeroItemPresent: Boolean): Int =
    sectionIndex + if (isHeroItemPresent) 1 else 0

/**
 * The two shapes a section's list item can take, kept in one place because both are load-bearing
 * and neither is obvious from the call site.
 *
 * A section with nothing in it collapses to nothing: no heading, and no page, because a section
 * with no cards must not own a screen. It is still composed rather than skipped, because
 * `SectionRowContent` carries the `LaunchedEffect` that reports the row empty and hands focus to
 * the nearest non-empty one.
 *
 * Everything else is a page when [fillsViewport] — see `ContentListLayout`, where that follows the
 * detail panel.
 *
 * Shared with `SectionRowFocusTraversalTest` for the same reason as [sectionListItemIndex].
 */
@Composable
internal fun LazyItemScope.SectionListItem(
    title: String,
    isEmpty: Boolean,
    fillsViewport: Boolean,
    modifier: Modifier = Modifier,
    row: @Composable () -> Unit,
) {
    if (isEmpty) {
        Column(modifier = modifier) { row() }
        return
    }
    Column(
        modifier = modifier.then(
            if (fillsViewport) Modifier.fillParentMaxHeight() else Modifier,
        ),
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            text = title,
            style = SectionTitleStyle,
        )
        Spacer(modifier = Modifier.height(8.dp))
        row()
    }
}

// `HeroCarousel` sets this height on itself and takes no height parameter, so a hero that is still
// loading has to name the same number to reserve the space the loaded carousel will take. It only
// applies off the page layout: `fillParentMaxHeight` imposes fixed constraints that coerce both
// this and the carousel's own height away.
private val HeroCarouselHeight = 280.dp

private data class ContentListContextMenuTarget(
    val item: VideoItemUIState,
    val sectionVm: SectionVM,
)
