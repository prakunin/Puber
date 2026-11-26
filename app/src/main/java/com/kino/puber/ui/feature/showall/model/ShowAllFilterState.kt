package com.kino.puber.ui.feature.showall.model

import androidx.compose.runtime.Immutable
import com.kino.puber.data.api.models.Country
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

/** The earliest year the year presets reach; `before 1980` starts here. */
internal const val YEAR_FLOOR = 1900

/** The first decade the year presets list on its own. */
internal const val OLDEST_DECADE = 1980

internal const val DECADE = 10

/** The years the last preset covers: everything before [OLDEST_DECADE]. */
internal val YEARS_BEFORE_OLDEST_DECADE = YEAR_FLOOR until OLDEST_DECADE

/**
 * The year presets the panel offers, in the order it lists them: this year, last year, each decade
 * from the current one down to [OLDEST_DECADE], then [YEARS_BEFORE_OLDEST_DECADE].
 */
internal fun yearOptions(currentYear: Int): List<IntRange> {
    val newestDecade = currentYear / DECADE * DECADE
    val decades = (newestDecade downTo OLDEST_DECADE step DECADE).map { it..it + DECADE - 1 }
    return listOf(currentYear..currentYear, (currentYear - 1)..(currentYear - 1)) +
        decades +
        listOf(YEARS_BEFORE_OLDEST_DECADE)
}

/**
 * What the grid header and the filter panel draw. The summary fields ([isActive], [genreTitle],
 * [countryTitle], [yearRange], [finishedOnly], [sort], [direction]) describe the filter the list was loaded with; [draft] is what the open panel
 * is editing and reaches the list only when the panel closes.
 */
@Immutable
internal data class ShowAllFilterState(
    val available: Boolean,
    val isActive: Boolean = false,
    val genreTitle: String? = null,
    val countryTitle: String? = null,
    val yearRange: IntRange? = null,
    val finishedOnly: Boolean = false,
    /** Whether the grid's type has a running/finished status at all; the panel hides the door otherwise. */
    val statusAvailable: Boolean = false,
    val sort: SortField = SortField.UPDATED,
    val direction: SortDirection = SortDirection.DESC,
    val panelOpen: Boolean = false,
    val draft: ContentFilter = ContentFilter(),
    val sortOptions: List<SortField> = FILTER_SORT_OPTIONS,
    val genres: FilterGenres = FilterGenres.Loading,
    val yearOptions: List<IntRange> = emptyList(),
    val countries: FilterCountries = FilterCountries.Loading,
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

@Immutable
internal sealed interface FilterCountries {
    data object Loading : FilterCountries

    data class Loaded(val countries: List<Country>) : FilterCountries

    data object Failed : FilterCountries
}
