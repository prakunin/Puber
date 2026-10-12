package com.kino.puber.core.ui.uikit.model

import androidx.compose.runtime.Immutable
import com.kino.puber.domain.interactor.api.ApiDomainState

@Immutable
internal data class ApiDomainDialogState(
    val currentDomain: String,
    val customDomain: String?,
    val isDetecting: Boolean = false,
) {
    val initialInput: String get() = customDomain.orEmpty()
}

internal fun ApiDomainState.toDialogState(): ApiDomainDialogState =
    ApiDomainDialogState(
        currentDomain = domain,
        customDomain = customDomain,
    )
