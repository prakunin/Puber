package com.kino.puber.ui.feature.bookmarks.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.LocalTvDialogFocusRestorer
import com.kino.puber.core.ui.uikit.component.TvDialogFocusRestorer
import com.kino.puber.core.ui.uikit.component.VideoItemContextMenuDialog
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemHorizontal
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.rememberReconciledItemFocus
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.ui.feature.bookmarks.model.BookmarksViewState

@Composable
internal fun BookmarksScreenContent(
    state: BookmarksViewState,
    onAction: (UIAction) -> Unit,
    onFolderSelected: (Int) -> Unit,
) {
    when (state) {
        is BookmarksViewState.Loading -> FullScreenProgressIndicator()
        is BookmarksViewState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = state.message)
        }
        is BookmarksViewState.Content -> BookmarksContent(
            state = state,
            onAction = onAction,
            onFolderSelected = onFolderSelected,
        )
    }
}

@Composable
private fun BookmarksContent(
    state: BookmarksViewState.Content,
    onAction: (UIAction) -> Unit,
    onFolderSelected: (Int) -> Unit,
) {
    var contextMenuItem by remember { mutableStateOf<VideoItemUIState?>(null) }
    val gridState = rememberLazyGridState()
    val gridFocusRequester = remember { FocusRequester() }
    val itemFocus = rememberReconciledItemFocus(
        rowKey = "bookmarks_${state.selectedFolderId}",
        items = state.items,
        isTargetRow = true,
        requestAfterFrame = true,
        onRowEmpty = {},
    )
    val dialogFocusRestorer = remember(gridFocusRequester, itemFocus.focusRequester) {
        TvDialogFocusRestorer(
            onDialogOpening = {
                runCatching { gridFocusRequester.saveFocusedChild() }
            },
            onDialogClosed = {
                val restored = runCatching {
                    gridFocusRequester.restoreFocusedChild()
                }.getOrDefault(false)
                if (!restored) {
                    runCatching { itemFocus.focusRequester.requestFocus() }
                }
            },
        )
    }
    val selectedFolderTitle = state.folders
        .firstOrNull { folder -> folder.id == state.selectedFolderId }
        ?.title
    CompositionLocalProvider(LocalTvDialogFocusRestorer provides dialogFocusRestorer) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (state.folders.size > 1) {
                    FolderChips(
                        folders = state.folders,
                        selectedFolderId = state.selectedFolderId,
                        onFolderSelected = onFolderSelected,
                    )
                }

                if (state.isLoadingItems) {
                    FullScreenProgressIndicator()
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(32.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(gridFocusRequester)
                            .focusRestorer(itemFocus.focusRequester)
                            .onFocusChanged { focusState ->
                                itemFocus.rowHasFocusRef[0] = focusState.hasFocus
                            },
                    ) {
                        itemsIndexed(state.items, key = { _, item -> item.id }) { _, item ->
                            val isFallbackTarget = item.id == itemFocus.targetItemId
                            val clickCallback = remember(item.id) {
                                {
                                    runCatching { gridFocusRequester.saveFocusedChild() }
                                    onAction(CommonAction.ItemSelected(item))
                                }
                            }
                            VideoItemHorizontal(
                                modifier = Modifier
                                    .then(
                                        if (isFallbackTarget) {
                                            Modifier.focusRequester(itemFocus.focusRequester)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            itemFocus.onItemFocused(item.id)
                                        }
                                    },
                                state = item,
                                onClick = clickCallback,
                                onContextMenu = { contextMenuItem = item },
                            )
                        }
                    }
                }
            }
            VideoItemContextMenuDialog(
                item = contextMenuItem,
                onDismiss = { contextMenuItem = null },
                onAction = onAction,
                removeFromFolderTitle = selectedFolderTitle,
                onWatchedChanged = { item, watched ->
                    onAction(CommonAction.ItemWatchedChanged(item, watched))
                },
            )
        }
    }
}

@Composable
private fun FolderChips(
    folders: List<Bookmark>,
    selectedFolderId: Int?,
    onFolderSelected: (Int) -> Unit,
) {
    val chipShape = RoundedCornerShape(16.dp)
    LazyRow(
        modifier = Modifier.focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(items = folders, key = { it.id }) { folder ->
            val isSelected = folder.id == selectedFolderId
            Surface(
                onClick = { onFolderSelected(folder.id) },
                shape = ClickableSurfaceDefaults.shape(chipShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                    focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = folder.title,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
