package com.kino.puber.ui.feature.showall.model

import com.kino.puber.domain.model.ContentFilter
import com.kino.puber.ui.feature.contentlist.model.SectionConfig

internal fun ContentFilter.applyTo(base: SectionConfig): SectionConfig =
    if (!isActive) {
        base
    } else {
        base.copy(
            sort = sort.apiValue + sortDirection.apiValue,
            genre = genre?.id?.toString() ?: base.genre,
            country = country?.id,
            year = yearRange?.toYearParam(),
            finished = finished,
        )
    }

/** The `year` query value: one year as is, a span as `from-to`; the server reads `-1979` as an error. */
internal fun IntRange.toYearParam(): String = if (first == last) "$first" else "$first-$last"
