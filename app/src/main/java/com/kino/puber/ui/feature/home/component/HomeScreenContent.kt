package com.kino.puber.ui.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.ApiDomainDialog
import com.kino.puber.core.ui.uikit.component.DpadScrollAxis
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.HeroCarousel
import com.kino.puber.core.ui.uikit.component.HeroItemState
import com.kino.puber.core.ui.uikit.component.PositionFocusedItemInLazyLayout
import com.kino.puber.core.ui.uikit.component.TvSafeButton
import com.kino.puber.core.ui.uikit.component.VideoItemContextMenuDialog
import com.kino.puber.core.ui.uikit.component.dpadScrollOptimization
import com.kino.puber.core.ui.uikit.component.moviesList.DetailsPrefetchRow
import com.kino.puber.core.ui.uikit.component.moviesList.DetailsPrefetchSurface
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemHorizontal
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.FocusableRow
import com.kino.puber.core.ui.uikit.component.moviesList.ReconciledRowFocusState
import com.kino.puber.core.ui.uikit.component.onTvContextMenuKey
import com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive
import com.kino.puber.core.ui.uikit.component.moviesList.rememberReconciledItemFocus
import com.kino.puber.core.ui.uikit.component.moviesList.rememberReconciledRowFocus
import com.kino.puber.core.ui.uikit.state.rememberSessionLazyListState
import com.kino.puber.core.ui.uikit.state.sessionMutableStateSaver
import com.kino.puber.core.ui.navigation.component.PreserveLazyListAnchorOnRootReturn
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.home.model.HomeAction
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeViewState

@Composable
internal fun HomeScreenContent(
    state: HomeViewState,
    onAction: (UIAction) -> Unit,
    onHeroClick: (Int) -> Unit,
    onCollectionClick: (Int, String) -> Unit,
    lazyListState: LazyListState = rememberSessionLazyListState(),
) {
    Box(Modifier.fillMaxSize()) {
        when (state) {
            is HomeViewState.Loading -> LoadingView(message = state.message)

            is HomeViewState.Error -> ErrorView(
                message = state.message,
                onRetry = { onAction(CommonAction.RetryClicked) },
                onConfigureApiDomain = { onAction(HomeAction.OpenApiDomainDialog) },
            )

            // The rows are the surface, so it opens with them and closes when the screen leaves —
            // including on the way to details or the player, where none of this is worth fetching.
            is HomeViewState.Content -> DetailsPrefetchSurface {
                HomeContent(
                    state = state,
                    onAction = onAction,
                    onHeroClick = onHeroClick,
                    onCollectionClick = onCollectionClick,
                    lazyListState = lazyListState,
                )
            }
        }

        ApiDomainDialog(
            state = state.apiDomainDialog,
            onSave = { onAction(HomeAction.SaveApiDomain(it)) },
            onReset = { onAction(HomeAction.ResetApiDomain) },
            onDetect = { onAction(HomeAction.DetectApiDomain) },
            onDismiss = { onAction(HomeAction.CloseApiDomainDialog) },
        )
    }
}

@Composable
private fun LoadingView(message: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        FullScreenProgressIndicator()
        if (message != null) {
            Text(
                text = message,
                modifier = Modifier.padding(top = 160.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    onConfigureApiDomain: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        TvSafeButton(
            text = stringResource(R.string.error_button_retry),
            onClick = onRetry,
            primary = true,
        )
        Spacer(Modifier.height(8.dp))
        TvSafeButton(
            text = stringResource(R.string.api_domain_open_action),
            onClick = onConfigureApiDomain,
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeViewState.Content,
    onAction: (UIAction) -> Unit,
    onHeroClick: (Int) -> Unit,
    onCollectionClick: (Int, String) -> Unit,
    lazyListState: LazyListState,
) {
    // What Select would act on. Held for the composition rather than re-derived per publish: a
    // refresh republishes both lists without moving the focus, and re-deriving there pointed Select
    // back at the carousel while the user was still looking at the card they had walked to — OK
    // then opened the hero instead of that card.
    var focusedTarget by remember { mutableStateOf<HomeFocusedTarget?>(null) }
    // Until something reports itself focused there is nothing to have been focused, so the screen
    // falls back to whatever would hold focus if the user acted right now.
    val selectTarget = remember(focusedTarget, state) {
        focusedTarget?.reconciledWith(state) ?: state.defaultFocusedTarget()
    }
    // Which of the two regions — the carousel or the rows — the user was last in. Kept for the
    // session rather than for the composition, because a screen that is returned to has lost its
    // composition but not the position the user left, and that position is what the restore aims at.
    var rowsHeldFocus by rememberSaveable(saver = sessionMutableStateSaver()) { mutableStateOf(false) }
    var contextMenuItem by remember { mutableStateOf<VideoItemUIState?>(null) }
    // Whether the user has pressed anything yet. Focus arriving in a row is not the same fact: with
    // the carousel still in flight the rows are the only focusable thing on screen, so an ordinary
    // focus search drops focus into one the moment it publishes, with nobody having asked for it.
    var userDroveFocus by remember { mutableStateOf(false) }
    val heroFocusRequester = remember { FocusRequester() }
    // Asked once and answered once: the carousel either took the focus Home opens with or proved it
    // cannot, and asking again on a later publish would drag focus off wherever the user has since
    // gone.
    var heroFocusResolved by remember { mutableStateOf(false) }
    var heroRefusedFocus by remember { mutableStateOf(false) }
    val contentFocusActive = LocalContentFocusActive.current
    ClaimHeroFocusEffect(
        heroItems = state.heroItems,
        lazyListState = lazyListState,
        enabled = !heroFocusResolved && !rowsHeldFocus && !userDroveFocus && contentFocusActive,
        focusRequester = heroFocusRequester,
        onOutcome = { claimed ->
            heroFocusResolved = true
            heroRefusedFocus = !claimed
        },
    )
    val rows = remember(state.sections) {
        state.sections.map { row ->
            FocusableRow(row.type.name, row.items.size)
        }
    }
    val rowFocus = rememberReconciledRowFocus(rows, restoreAcrossProcess = false)
    PreserveLazyListAnchorOnRootReturn(lazyListState)

    PositionFocusedItemInLazyLayout(keepFullyVisibleItemInPlace = true) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    // Declared first so it sees every key the screen gets, including the ones the
                    // handlers below consume.
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            userDroveFocus = true
                            // Select on a card the user never moved to still counts as leaving from
                            // the rows, and a return has to restore to it rather than to the hero.
                            if (selectTarget != null && selectTarget !is HomeFocusedTarget.Hero) {
                                rowsHeldFocus = true
                            }
                        }
                        false
                    }
                    .focusRestorer()
                    .focusGroup()
                    .onTvContextMenuKey(
                        enabled = selectTarget is HomeFocusedTarget.Video,
                        onOpen = {
                            contextMenuItem = (selectTarget as? HomeFocusedTarget.Video)?.item
                        },
                    )
                    .onSelectKeyClick(
                        canHandle = { selectTarget != null },
                        onClick = {
                            when (val target = selectTarget) {
                                is HomeFocusedTarget.Collection -> onCollectionClick(target.id, target.title)
                                is HomeFocusedTarget.Hero -> onHeroClick(target.id)
                                is HomeFocusedTarget.Video -> onAction(CommonAction.ItemSelected(target.item))
                                null -> Unit
                            }
                        },
                    ),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                if (state.heroItems.isNotEmpty()) {
                    item(key = "hero") {
                        HeroCarousel(
                            items = state.heroItems,
                            onItemClick = onHeroClick,
                            onFocusedItemChanged = { id ->
                                focusedTarget = HomeFocusedTarget.Hero(id)
                                rowsHeldFocus = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(heroFocusRequester),
                        )
                    }
                }
                homeSections(
                    state = state,
                    rowFocus = rowFocus,
                    // The hero owns the focus a fresh Home opens with, and no row may pull focus off
                    // it — that request would scroll the carousel out of the viewport the moment Home
                    // loads, or the moment a refresh lands under a user who is looking at it. A user
                    // who left from the rows is a different matter: the row they left is exactly what
                    // a return has to restore to.
                    //
                    // Asked of a *settled* carousel rather than an empty one. Sections publish as
                    // they answer, so on a fresh Home the carousel is empty for as long as its own
                    // request is in flight — and every row that landed first read that emptiness as
                    // its cue to take focus, leaving Home opened somewhere down the middle of
                    // itself with the carousel scrolled off the top.
                    rowsMayTakeFocus = (state.heroSettled && state.heroItems.isEmpty()) ||
                        rowsHeldFocus ||
                        heroRefusedFocus,
                    onAction = onAction,
                    onCollectionClick = onCollectionClick,
                    onFocusedTarget = {
                        focusedTarget = it
                        // Only a row the user steered into means "the user was last in the rows",
                        // which is what a return restores to and what keeps the hero from claiming
                        // the focus it opens with. A row that focus merely landed in means nothing.
                        if (userDroveFocus) {
                            rowsHeldFocus = true
                        }
                    },
                    onContextMenuItem = { contextMenuItem = it },
                )
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
 * Hands the carousel the focus a fresh Home opens with.
 *
 * The rows ask for theirs, the carousel never did, and the one auto-request the navigation host
 * makes is spent 100ms after launch — while Home is still on its spinner — and never repeats. So
 * whoever asked was who got it. Answered once either way: [heroItems] changes on every published
 * section, and re-asking there would drag focus back off whatever the user had already moved to.
 *
 * Retried across a few frames rather than asked once, because the carousel is published in the same
 * frame as the request and is not laid out yet when the effect first runs.
 */
@Composable
private fun ClaimHeroFocusEffect(
    heroItems: List<HeroItemState>,
    lazyListState: LazyListState,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onOutcome: (claimed: Boolean) -> Unit,
) {
    LaunchedEffect(heroItems, enabled) {
        if (!enabled || heroItems.isEmpty()) return@LaunchedEffect
        // Focus landing in a row pulls that row up the screen, which leaves the carousel — the
        // first item — outside the composed window, and a requester attached to nothing cannot be
        // asked. Nobody has steered anything yet, so the list is free to go back to its top.
        lazyListState.scrollToItem(0)
        repeat(HERO_FOCUS_REQUEST_ATTEMPTS) {
            withFrameNanos { }
            if (runCatching { focusRequester.requestFocus() }.getOrDefault(false)) {
                onOutcome(true)
                return@LaunchedEffect
            }
        }
        // Every attempt spent without the carousel taking it. Something has to be focusable on a TV
        // screen, so the rows get their claim back rather than the screen sitting unreachable.
        onOutcome(false)
    }
}

private const val HERO_FOCUS_REQUEST_ATTEMPTS = 5

private fun LazyListScope.homeSections(
    state: HomeViewState.Content,
    rowFocus: ReconciledRowFocusState,
    rowsMayTakeFocus: Boolean,
    onAction: (UIAction) -> Unit,
    onCollectionClick: (Int, String) -> Unit,
    onFocusedTarget: (HomeFocusedTarget) -> Unit,
    onContextMenuItem: (VideoItemUIState) -> Unit,
) {
    state.sections.forEachIndexed { index, section ->
        item(key = "section_${section.type.name}") {
            Column {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HomeSectionRow(
                    rowKey = section.type.name,
                    items = section.items,
                    isTargetRow = rowsMayTakeFocus && section.type.name == rowFocus.focusedRowKey,
                    rowOrder = index,
                    // Collections open a list, not a details screen, and their ids are collection
                    // ids. The row keeps its absolute position all the same, so the rows either
                    // side of it are not mistaken for neighbours of each other.
                    detailsPrefetchEnabled = section.type != HomeSectionType.Collections,
                    onSectionFocused = { rowFocus.onRowFocused(section.type.name) },
                    onItemClick = { item ->
                        if (section.type == HomeSectionType.Collections) {
                            onCollectionClick(item.id, item.title)
                        } else {
                            onAction(CommonAction.ItemSelected(item))
                        }
                    },
                    onItemContextMenu = if (section.type == HomeSectionType.Collections) {
                        null
                    } else {
                        onContextMenuItem
                    },
                    onItemFocused = { item ->
                        onFocusedTarget(
                            if (section.type == HomeSectionType.Collections) {
                                HomeFocusedTarget.Collection(id = item.id, title = item.title)
                            } else {
                                HomeFocusedTarget.Video(item, section.type)
                            }
                        )
                    },
                    onRowEmpty = { rowFocus.onRowEmpty(index) },
                )
            }
        }
    }
}

@Composable
internal fun HomeSectionRow(
    rowKey: String,
    items: List<VideoItemUIState>,
    isTargetRow: Boolean,
    onSectionFocused: () -> Unit,
    onItemClick: (VideoItemUIState) -> Unit,
    onItemContextMenu: ((VideoItemUIState) -> Unit)?,
    onItemFocused: (VideoItemUIState) -> Unit,
    onRowEmpty: () -> Unit,
    rowOrder: Int = 0,
    detailsPrefetchEnabled: Boolean = false,
) {
    DetailsPrefetchRow(
        rowOrder = rowOrder,
        rowKey = rowKey,
        items = items,
        enabled = detailsPrefetchEnabled,
    )
    val listState = rememberSessionLazyListState()
    val itemFocus = rememberReconciledItemFocus(
        rowKey = rowKey,
        items = items,
        isTargetRow = isTargetRow,
        restoreAcrossProcess = false,
        onRowEmpty = onRowEmpty,
    )

    LazyRow(
        state = listState,
        modifier = Modifier
            .graphicsLayer { clip = false }
            .dpadScrollOptimization(axis = DpadScrollAxis.Horizontal)
            .focusRestorer(itemFocus.focusRequester),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        itemsIndexed(items = items, key = { _, item -> item.id }) { index, item ->
            val isFocusTarget = item.id == itemFocus.targetItemId
            VideoItemHorizontal(
                modifier = Modifier
                    .then(
                        if (isFocusTarget) {
                            Modifier.focusRequester(itemFocus.focusRequester)
                        } else {
                            Modifier
                        }
                    )
                    .onFocusChanged {
                        if (it.isFocused) {
                            onSectionFocused()
                            itemFocus.onItemFocused(item.id)
                            onItemFocused(item)
                        }
                    },
                state = item,
                onClick = { onItemClick(item) },
                onContextMenu = onItemContextMenu?.let { callback -> { callback(item) } },
            )
        }
    }
}

private sealed interface HomeFocusedTarget {
    data class Hero(val id: Int) : HomeFocusedTarget

    /**
     * [sectionType] is carried because the same title appears in several rows at once, drawn
     * differently in each — the personal rows mark everything saved — and a card looked up by id
     * alone would answer with whichever row happened to come first.
     */
    data class Video(val item: VideoItemUIState, val sectionType: HomeSectionType) : HomeFocusedTarget
    data class Collection(val id: Int, val title: String) : HomeFocusedTarget
}

/**
 * Points a target held across publishes back at the state now on screen.
 *
 * The target is kept rather than re-derived so a refresh cannot move what Select acts on. The cost
 * is that it goes on describing the payload it was made from — a heart toggled elsewhere, a title
 * that has left the row — so what it names is looked up again each time the state changes, and a
 * target whose subject is gone gives way to the default.
 */
private fun HomeFocusedTarget.reconciledWith(state: HomeViewState.Content): HomeFocusedTarget? {
    return when (this) {
        is HomeFocusedTarget.Hero -> takeIf { state.heroItems.any { it.id == id } }

        is HomeFocusedTarget.Video -> state.sections
            .firstOrNull { it.type == sectionType }
            ?.items
            ?.firstOrNull { it.id == item.id }
            ?.let { HomeFocusedTarget.Video(it, sectionType) }

        is HomeFocusedTarget.Collection -> state.sections
            .firstOrNull { it.type == HomeSectionType.Collections }
            ?.items
            ?.firstOrNull { it.id == id }
            ?.let { HomeFocusedTarget.Collection(id = it.id, title = it.title) }
    }
}

private fun HomeViewState.Content.defaultFocusedTarget(): HomeFocusedTarget? {
    val hero = heroItems.firstOrNull()
    if (hero != null) {
        return HomeFocusedTarget.Hero(hero.id)
    }
    // A carousel still in flight owns the focus nobody holds yet, so Select has nothing to act on:
    // seeding the first row's first card here would open a title the user never focused.
    val section = if (heroSettled) sections.firstOrNull { it.items.isNotEmpty() } else null
    val item = section?.items?.first() ?: return null
    return if (section.type == HomeSectionType.Collections) {
        HomeFocusedTarget.Collection(id = item.id, title = item.title)
    } else {
        HomeFocusedTarget.Video(item, section.type)
    }
}

private fun Modifier.onSelectKeyClick(
    canHandle: () -> Boolean,
    onClick: () -> Unit,
): Modifier {
    return onPreviewKeyEvent { event ->
        if (!event.key.isSelectKey()) {
            return@onPreviewKeyEvent false
        }
        when (event.type) {
            KeyEventType.KeyDown -> canHandle()
            KeyEventType.KeyUp -> if (canHandle()) {
                onClick()
                true
            } else {
                false
            }
            else -> false
        }
    }
}

private fun Key.isSelectKey(): Boolean {
    return this == Key.DirectionCenter || this == Key.Enter
}
