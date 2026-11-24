package com.kino.puber.ui.feature.contentlist.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
internal data class BrowseEntry(
    val config: SectionConfig,
    @param:StringRes val labelRes: Int,
)
