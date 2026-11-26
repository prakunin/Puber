package com.kino.puber.ui.feature.showall.content

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kino.puber.R
import com.kino.puber.domain.model.SortDirection
import com.kino.puber.domain.model.SortField
import com.kino.puber.ui.feature.showall.model.DECADE
import com.kino.puber.ui.feature.showall.model.ShowAllFilterState
import com.kino.puber.ui.feature.showall.model.YEARS_BEFORE_OLDEST_DECADE

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

/** «Любой год», a single year, a decade, «до 1980», or an off-preset span. */
@Composable
internal fun yearLabel(range: IntRange?): String = when {
    range == null -> stringResource(R.string.show_all_filters_year_any)
    range.first == range.last -> range.first.toString()
    range == YEARS_BEFORE_OLDEST_DECADE -> stringResource(R.string.show_all_filters_year_before_1980)
    range.first % DECADE == 0 && range.last == range.first + DECADE - 1 ->
        stringResource(R.string.show_all_filters_year_decade, range.first)
    else -> stringResource(R.string.show_all_filters_year_range, range.first, range.last)
}

@Composable
internal fun statusLabel(finishedOnly: Boolean): String = stringResource(
    if (finishedOnly) R.string.show_all_filters_status_finished else R.string.show_all_filters_status_any,
)

/**
 * What the applied filter says in one line: the genre, country, year and «Завершённые» in that
 * order, then the sort, which is left out while it is the default order.
 */
@Composable
internal fun filterSummary(filter: ShowAllFilterState): String {
    val parts = buildList {
        filter.genreTitle?.let(::add)
        filter.countryTitle?.let(::add)
        filter.yearRange?.let { add(yearLabel(it)) }
        if (filter.finishedOnly) add(statusLabel(true))
        if (filter.sort != SortField.UPDATED || filter.direction != SortDirection.DESC) {
            add(sortSummary(filter.sort, filter.direction))
        }
    }
    var summary = parts.firstOrNull().orEmpty()
    parts.drop(1).forEach { summary = stringResource(R.string.show_all_filters_summary_join, summary, it) }
    return summary
}
