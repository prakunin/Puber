package com.kino.puber.ui.feature.showall.model

import android.os.Parcelable
import androidx.annotation.StringRes
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class ShowAllTitle(
    @param:StringRes val titleRes: Int?,
    val title: String = "",
    @param:StringRes val prefixRes: Int? = null,
) : Parcelable
