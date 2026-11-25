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
        )
    }
