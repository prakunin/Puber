package com.kino.puber.ui.feature.showall.content

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kino.puber.R
import com.kino.puber.domain.model.SortDirection
import com.kino.puber.domain.model.SortField
import com.kino.puber.ui.feature.showall.model.ShowAllFilterState

@Composable
internal fun sortFieldLabel(field: SortField): String = when (field) {
    SortField.UPDATED -> stringResource(R.string.show_all_sort_updated)
    SortField.CREATED -> stringResource(R.string.show_all_sort_created)
    SortField.YEAR -> stringResource(R.string.show_all_sort_year)
    SortField.RATING -> stringResource(R.string.show_all_sort_rating)
    SortField.VIEWS -> stringResource(R.string.show_all_sort_views)
    SortField.KINOPOISK, SortField.IMDB -> field.apiValue
}

@Composable
internal fun sortDirectionLabel(direction: SortDirection): String = when (direction) {
    SortDirection.DESC -> stringResource(R.string.show_all_sort_desc)
    SortDirection.ASC -> stringResource(R.string.show_all_sort_asc)
}

/** «по году ↑»: the field and an arrow for the direction. */
@Composable
internal fun sortSummary(field: SortField, direction: SortDirection): String {
    val arrow = when (direction) {
        SortDirection.DESC -> stringResource(R.string.show_all_sort_arrow_desc)
        SortDirection.ASC -> stringResource(R.string.show_all_sort_arrow_asc)
    }
    return stringResource(R.string.show_all_sort_summary, sortFieldLabel(field), arrow)
}

/**
 * What the applied filter says in one line: the genre first, then the sort, which is left out while
 * it is the default order.
 */
@Composable
internal fun filterSummary(filter: ShowAllFilterState): String {
    val parts = buildList {
        filter.genreTitle?.let(::add)
        if (filter.sort != SortField.UPDATED || filter.direction != SortDirection.DESC) {
            add(sortSummary(filter.sort, filter.direction))
        }
    }
    var summary = parts.firstOrNull().orEmpty()
    parts.drop(1).forEach { summary = stringResource(R.string.show_all_filters_summary_join, summary, it) }
    return summary
}
