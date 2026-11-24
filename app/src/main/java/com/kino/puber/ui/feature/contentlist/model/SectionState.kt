package com.kino.puber.ui.feature.contentlist.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.telemetry.StartupContentState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState

@Immutable
internal sealed interface SectionState {
    data object Loading : SectionState

    data class Content(
        val items: List<VideoItemUIState>,
        val isLoadingMore: Boolean = false,
    ) : SectionState, StartupContentState

    data class Error(val message: String) : SectionState

    /**
     * A finished load that found nothing: SectionVM publishes the paginator's initial Empty, replayed
     * before the first load, as [Loading]. Still not a startup content state, since an empty first
     * page is no content.
     */
    data object Empty : SectionState
}
