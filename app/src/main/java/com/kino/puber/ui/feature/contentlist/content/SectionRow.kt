package com.kino.puber.ui.feature.contentlist.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.DpadScrollAxis
import com.kino.puber.core.ui.uikit.component.FadeGradient
import com.kino.puber.core.ui.uikit.component.LoadMoreHandler
import com.kino.puber.core.ui.uikit.component.dpadScrollOptimization
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemHorizontal
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.rememberReconciledItemFocus
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.model.SectionState

@Composable
internal fun SectionRowContent(
    state: SectionState,
    config: SectionConfig,
    isTargetRow: Boolean,
    onItemClick: (VideoItemUIState) -> Unit,
    onItemContextMenu: (VideoItemUIState) -> Unit,
    onItemFocused: (VideoItemUIState) -> Unit,
    onSectionFocused: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onShowAll: (() -> Unit)? = null,
    loadsMore: Boolean = true,
    onRowEmpty: () -> Unit = {},
    // Fires for real focus entering or leaving this row's content, in *any* section state --
    // Content cards, the Loading shimmer, or the Error row's Retry button -- unlike
    // `onSectionFocused`, which only the Content-card path invokes.
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val contentFocusRequester = remember { FocusRequester() }
    val hasFocusRef = remember { booleanArrayOf(false) }

    LaunchedEffect(state, isTargetRow) {
        if (state is SectionState.Empty && isTargetRow) {
            onRowEmpty()
        }
    }

    Box(
        modifier = Modifier.onFocusChanged { focusState ->
            hasFocusRef[0] = focusState.hasFocus
            onFocusChanged(focusState.hasFocus)
        },
    ) {
        when (val s = state) {
            is SectionState.Loading -> ShimmerSectionCards()
            is SectionState.Empty -> { /* hidden */ }
            is SectionState.Error -> ErrorSectionContent(
                message = s.message,
                onRetry = onRetry,
            )
            is SectionState.Content -> ContentSectionCards(
                state = s,
                isTargetRow = isTargetRow,
                shouldRequestInitialFocus = hasFocusRef[0],
                contentFocusRequester = contentFocusRequester,
                rowKey = config.id,
                onItemClick = onItemClick,
                onItemContextMenu = onItemContextMenu,
                onItemFocused = onItemFocused,
                onSectionFocused = onSectionFocused,
                onLoadMore = onLoadMore,
                onShowAll = onShowAll,
                loadsMore = loadsMore,
                onRowEmpty = onRowEmpty,
            )
        }
    }
}

@Composable
private fun ContentSectionCards(
    state: SectionState.Content,
    isTargetRow: Boolean,
    shouldRequestInitialFocus: Boolean,
    contentFocusRequester: FocusRequester,
    rowKey: String,
    onItemClick: (VideoItemUIState) -> Unit,
    onItemContextMenu: (VideoItemUIState) -> Unit,
    onItemFocused: (VideoItemUIState) -> Unit,
    onSectionFocused: () -> Unit,
    onLoadMore: () -> Unit,
    onShowAll: (() -> Unit)?,
    loadsMore: Boolean,
    onRowEmpty: () -> Unit,
) {
    val listState = rememberLazyListState()
    val emptyRowHandler = onRowEmpty.takeIf { onShowAll == null } ?: {}
    val itemFocus = rememberReconciledItemFocus(
        rowKey = rowKey,
        items = state.items,
        isTargetRow = isTargetRow,
        onRowEmpty = emptyRowHandler,
    )

    Box(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
    ) {
        LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { clip = false }
                    .focusRequester(contentFocusRequester)
                    .dpadScrollOptimization(axis = DpadScrollAxis.Horizontal)
                    .focusRestorer(itemFocus.focusRequester),
                horizontalArrangement = Arrangement.spacedBy(CardSpacing),
                contentPadding = PaddingValues(RowPadding),
            ) {
                itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                    val isFallbackTarget = item.id == itemFocus.targetItemId
                    val focusModifier = Modifier.onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            onSectionFocused()
                            // False means the row bounced focus on to the card it remembers, and
                            // the event for *that* card is already coming. Reporting this one
                            // would put the wrong film in the panel for a frame and fetch it.
                            if (itemFocus.onItemFocused(item.id)) {
                                onItemFocused(item)
                            }
                        }
                    }
                    val clickCallback = remember(item.id) { { onItemClick(item) } }
                    VideoItemHorizontal(
                        modifier = Modifier
                            .then(
                                if (isFallbackTarget) {
                                    Modifier.focusRequester(itemFocus.focusRequester)
                                } else {
                                    Modifier
                                }
                            )
                            .then(focusModifier),
                        state = item,
                        onClick = clickCallback,
                        itemHeight = PuberTheme.Defaults.CatalogueRowItemHeight,
                        titleMaxLines = 1,
                        titleStyle = MaterialTheme.typography.titleSmall.copy(
                            fontSize = CardTitleSize,
                            lineHeight = CardTitleLineHeight,
                        ),
                        contentPadding = CardContentPadding,
                        metaSpacing = CardMetaSpacing,
                        onContextMenu = { onItemContextMenu(item) },
                    )
                }
                if (onShowAll != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .height(PuberTheme.Defaults.CatalogueRowItemHeight)
                                .aspectRatio(PuberTheme.Defaults.HorizontalVideoItemAspectRatio),
                            contentAlignment = Alignment.Center,
                        ) {
                            Button(
                                onClick = { onShowAll() },
                                modifier = Modifier.height(ShowAllButtonHeight),
                                contentPadding = ShowAllButtonPadding,
                            ) {
                                Text(
                                    stringResource(R.string.show_all),
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
        }
        FadeGradient(listState)
    }

    LaunchedEffect(state.items.firstOrNull()?.id) {
        if (shouldRequestInitialFocus) {
            runCatching { itemFocus.focusRequester.requestFocus() }
                .recoverCatching { contentFocusRequester.requestFocus() }
        }
    }

    if (loadsMore) {
        LoadMoreHandler(
            lazyListState = listState,
            loadMoreAtEnd = onLoadMore,
        )
    }
}

/**
 * The catalogue row's own numbers, dialled on `docs/design/catalogue`. The card is 115 dp here
 * against the 150 the shared component defaults to, so its caption and padding come down with it.
 *
 * `RowPadding` is zero on purpose: the cards run to the very edge of the content, closer in than
 * Google's 48 dp safe zone, and the section heading above keeps its own 16 dp instead.
 */
private val RowPadding = 0.dp
private val CardSpacing = 10.dp
private val CardContentPadding = PaddingValues(horizontal = 7.dp, vertical = 10.dp)
private val CardMetaSpacing = 9.dp
private val CardTitleSize = 10.sp

/**
 * Below the caption's own 10 sp, and that is the point: `titleSmall` names a 20 sp line and the
 * box keeps that height whatever the letters do, so the line has to be named too or a one-line
 * caption still costs 20 dp of a 115 dp card.
 */
private val CardTitleLineHeight = 9.sp

/** Two lines of `labelLarge` at 20 sp, and nothing else — the button's own minimum is 40 dp. */
private val ShowAllButtonHeight = 43.dp
private val ShowAllButtonPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)

@Composable
private fun ErrorSectionContent(
    message: String,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.error_button_retry))
        }
    }
}
