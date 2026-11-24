package com.kino.puber.ui.feature.showall.content

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.TvDialogOverlay
import com.kino.puber.core.ui.uikit.component.panel.TvSidePanelItem
import com.kino.puber.core.ui.uikit.component.panel.TvSidePanelSectionHeader
import com.kino.puber.core.ui.uikit.component.panel.TvSidePanelSurface
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.model.SortDirection
import com.kino.puber.ui.feature.showall.model.FilterGenres
import com.kino.puber.ui.feature.showall.model.ShowAllAction
import com.kino.puber.ui.feature.showall.model.ShowAllFilterState
import kotlinx.coroutines.delay

private val PanelMaxHeight = 500.dp
private const val PANEL_FOCUS_DELAY_MS = 100L
private const val PANEL_FOCUS_ATTEMPTS = 4
private const val PANEL_FOCUS_RETRY_DELAY_MS = 50L

private enum class FilterPage { Root, Sort, Genre }

/**
 * The side panel behind «Фильтры»: a root page with two doors, «Сортировка» and «Жанр», each opening
 * a page of values. Picking a value changes the draft in the view model and stays on the page; the
 * list changes when the panel closes. Back goes page to root, then closes.
 */
@Composable
internal fun ShowAllFilterPanel(
    filter: ShowAllFilterState,
    onAction: (UIAction) -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(FilterPage.Root) }
    // The door the user last went through, which is where focus returns to from its page.
    var door by rememberSaveable { mutableStateOf(FilterPage.Sort) }
    val firstFocus = remember { FocusRequester() }

    // A page puts focus on the row it opens on: the door the user came from, or the selected value,
    // or Retry when the genres failed. The genre list landing is a new first focus of its page.
    val genresState = if (page == FilterPage.Genre) filter.genres::class else null
    LaunchedEffect(page, genresState) {
        delay(PANEL_FOCUS_DELAY_MS)
        repeat(PANEL_FOCUS_ATTEMPTS) {
            if (runCatching { firstFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            delay(PANEL_FOCUS_RETRY_DELAY_MS)
        }
    }

    TvDialogOverlay(
        onDismiss = { onAction(ShowAllAction.FilterPanelClosed) },
        contentAlignment = Alignment.CenterEnd,
    ) {
        TvSidePanelSurface(
            title = when (page) {
                FilterPage.Root -> stringResource(R.string.show_all_filters)
                FilterPage.Sort -> stringResource(R.string.show_all_filters_sort)
                FilterPage.Genre -> stringResource(R.string.show_all_filters_genre)
            },
            modifier = Modifier.heightIn(max = PanelMaxHeight),
        ) {
            // Registered after the overlay's own handler, so while a page is open it is the one
            // that answers Back.
            BackHandler(enabled = page != FilterPage.Root) { page = FilterPage.Root }
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when (page) {
                    FilterPage.Root -> RootPage(
                        filter = filter,
                        firstFocus = firstFocus,
                        door = door,
                        onOpen = {
                            door = it
                            page = it
                        },
                    )
                    FilterPage.Sort -> SortPage(filter, firstFocus, onAction)
                    FilterPage.Genre -> GenrePage(filter, firstFocus, onAction)
                }
            }
        }
    }
}

@Composable
private fun RootPage(
    filter: ShowAllFilterState,
    firstFocus: FocusRequester,
    door: FilterPage,
    onOpen: (FilterPage) -> Unit,
) {
    TvSidePanelItem(
        text = stringResource(R.string.show_all_filters_sort),
        trailingText = sortSummary(filter.draft.sort, filter.draft.sortDirection),
        onClick = { onOpen(FilterPage.Sort) },
        focusRequester = firstFocus.takeIf { door == FilterPage.Sort },
    )
    TvSidePanelItem(
        text = stringResource(R.string.show_all_filters_genre),
        trailingText = filter.draft.genre?.title ?: stringResource(R.string.show_all_filters_genre_all),
        onClick = { onOpen(FilterPage.Genre) },
        focusRequester = firstFocus.takeIf { door == FilterPage.Genre },
    )
}

@Composable
private fun SortPage(
    filter: ShowAllFilterState,
    firstFocus: FocusRequester,
    onAction: (UIAction) -> Unit,
) {
    TvSidePanelSectionHeader(stringResource(R.string.show_all_filters_sort_field))
    filter.sortOptions.forEach { field ->
        val selected = filter.draft.sort == field
        TvSidePanelItem(
            text = sortFieldLabel(field),
            selected = selected,
            onClick = { onAction(ShowAllAction.SortFieldChosen(field)) },
            focusRequester = firstFocus.takeIf { selected },
        )
    }
    TvSidePanelSectionHeader(stringResource(R.string.show_all_filters_sort_order))
    SortDirection.entries.forEach { direction ->
        TvSidePanelItem(
            text = sortDirectionLabel(direction),
            selected = filter.draft.sortDirection == direction,
            onClick = { onAction(ShowAllAction.SortDirectionChosen(direction)) },
        )
    }
}

@Composable
private fun GenrePage(
    filter: ShowAllFilterState,
    firstFocus: FocusRequester,
    onAction: (UIAction) -> Unit,
) {
    val chosen = filter.draft.genre
    val loaded = filter.genres as? FilterGenres.Loaded
    // Without a chosen genre, or while the list that holds it is not here, «Все жанры» is the
    // opening row; a failed load opens on Retry instead.
    val opensOnAll = filter.genres !is FilterGenres.Failed &&
        (chosen == null || loaded?.genres?.any { it.id == chosen.id } != true)
    TvSidePanelItem(
        text = stringResource(R.string.show_all_filters_genre_all),
        selected = chosen == null,
        onClick = { onAction(ShowAllAction.GenreChosen(null)) },
        focusRequester = firstFocus.takeIf { opensOnAll },
    )
    when (val genres = filter.genres) {
        FilterGenres.Loading -> Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        FilterGenres.Failed -> {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(
                    text = stringResource(R.string.show_all_filters_genres_error),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TvSidePanelItem(
                text = stringResource(R.string.show_all_filters_retry),
                onClick = { onAction(ShowAllAction.GenresRetry) },
                focusRequester = firstFocus,
            )
        }
        is FilterGenres.Loaded -> genres.genres.forEach { genre ->
            val selected = chosen?.id == genre.id
            TvSidePanelItem(
                text = genre.title,
                selected = selected,
                onClick = { onAction(ShowAllAction.GenreChosen(genre)) },
                focusRequester = firstFocus.takeIf { selected },
            )
        }
    }
}
