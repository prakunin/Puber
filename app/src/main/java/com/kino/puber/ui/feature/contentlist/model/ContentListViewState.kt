package com.kino.puber.ui.feature.contentlist.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.HeroItemState
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState

@Immutable
internal data class ContentListViewState(
    val heroItems: List<HeroItemState> = emptyList(),
    val isHeroLoading: Boolean = false,
    val selectedItem: VideoDetailsUIState = VideoDetailsUIState.Loading,
    /**
     * The trailer to play over the still, or null for the still itself. Set only once focus has
     * rested on a card long enough, and cleared the moment focus moves or playback stops.
     */
    val previewTrailerUrl: String? = null,
)
