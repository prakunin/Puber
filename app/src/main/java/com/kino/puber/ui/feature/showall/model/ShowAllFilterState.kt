package com.kino.puber.ui.feature.showall.model

import androidx.compose.runtime.Immutable
import com.kino.puber.data.api.models.Genre
import com.kino.puber.domain.model.ContentFilter
import com.kino.puber.domain.model.SortDirection
import com.kino.puber.domain.model.SortField

/** The sort fields the panel offers, in the order it lists them. */
internal val FILTER_SORT_OPTIONS = listOf(
    SortField.UPDATED,
    SortField.CREATED,
    SortField.YEAR,
    SortField.RATING,
    SortField.VIEWS,
)

/**
 * What the grid header and the filter panel draw. The summary fields ([isActive], [genreTitle],
 * [sort], [direction]) describe the filter the list was loaded with; [draft] is what the open panel
 * is editing and reaches the list only when the panel closes.
 */
@Immutable
internal data class ShowAllFilterState(
    val available: Boolean,
    val isActive: Boolean = false,
    val genreTitle: String? = null,
    val sort: SortField = SortField.UPDATED,
    val direction: SortDirection = SortDirection.DESC,
    val panelOpen: Boolean = false,
    val draft: ContentFilter = ContentFilter(),
    val sortOptions: List<SortField> = FILTER_SORT_OPTIONS,
    val genres: FilterGenres = FilterGenres.Loading,
) {
    companion object {
        val Unavailable = ShowAllFilterState(available = false)
    }
}

@Immutable
internal sealed interface FilterGenres {
    data object Loading : FilterGenres

    data class Loaded(val genres: List<Genre>) : FilterGenres

    data object Failed : FilterGenres
}
