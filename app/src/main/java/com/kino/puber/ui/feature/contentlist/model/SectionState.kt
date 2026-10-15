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
     * Not a startup content state: the paginator's store starts in its own Empty state and replays
     * it before the first load, so marking this one would stamp first content before any data.
     */
    data object Empty : SectionState
}
