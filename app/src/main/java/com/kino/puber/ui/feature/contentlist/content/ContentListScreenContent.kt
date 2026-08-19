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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
                heroItem(state, onAction)
                sectionItems(
                    sections = sections,
                    sectionVms = sectionVms,
                    sectionStates = sectionStates,
                    focusedSectionIndex = focusedSectionIndex,
                    onSectionFocused = onSectionFocused,
                    onContextMenu = onContextMenu,
                    onAction = onAction,
                )
            }
        }
    }
}

private fun LazyListScope.heroItem(
    state: ContentListViewState,
    onAction: (UIAction) -> Unit,
) {
    if (state.isHeroLoading || state.heroItems.isNotEmpty()) {
        item(key = "hero", contentType = "hero") {
            if (state.heroItems.isEmpty()) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillParentMaxHeight(),
                )
            } else {
                HeroCarousel(
                    items = state.heroItems,
                    onItemClick = { itemId ->
                        onAction(ContentListAction.HeroSelected(itemId))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillParentMaxHeight(),
                    // D-pad up out of the top row lands here, inside the same focus group as the
                    // rows, so nothing else reports that the focused card is no longer focused.
                    onFocusedItemChanged = { onAction(ContentListAction.TrailerPreviewStopped) },
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
    onSectionFocused: (Int) -> Unit,
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
            onSectionFocused = onSectionFocused,
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
    onSectionFocused: (Int) -> Unit,
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
                rowOrder = index,
                onItemClick = rememberedOnItemClick,
                onItemContextMenu = { onContextMenu(it, sectionVm) },
                onItemFocused = rememberedOnItemFocused,
                onSectionFocused = rememberedOnSectionFocused,
                onRetry = { sectionVm.onAction(CommonAction.RetryClicked) },
                onLoadMore = { sectionVm.onAction(CommonAction.LoadMore) },
                onShowAll = rememberedOnShowAll,
                onRowEmpty = onRowEmpty,
            )
        }

        if (sectionState is SectionState.Empty) {
            // No heading, and no page: a section with nothing in it must not own a screen.
            // It is still composed rather than skipped, because SectionRowContent carries the
            // LaunchedEffect that reports the row empty and hands focus to the nearest
            // non-empty one.
            row()
        } else {
            Column(modifier = Modifier.fillParentMaxHeight()) {
                val title = config.titleRes?.let { stringResource(it) } ?: config.title
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
    }
}

private data class ContentListContextMenuTarget(
    val item: VideoItemUIState,
    val sectionVm: SectionVM,
)
