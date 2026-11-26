package com.kino.puber.ui.feature.auth.model

import android.os.Parcelable
import com.kino.puber.core.session.SessionEndReason
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class AuthScreenParams(
    val sessionEndReason: SessionEndReason? = null,
) : Parcelable
