package com.kino.puber.ui.feature.details.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.UIAction

@Immutable
internal sealed interface DetailsScreenState {
    data object Loading : DetailsScreenState
    data class Error(val message: String) : DetailsScreenState
    data class Content(
        val details: VideoDetailsUIState,
        val info: DetailsInfoUIState,
        val buttons: List<DetailsButtonUIState>,
        val isInWatchlist: Boolean,
        val isWatched: Boolean,
        val episodes: VideoGridUIState? = null,
        val currentEpisode: VideoItemUIState? = null,
        val initialEpisodeFocusId: Int? = null,
        val similarItems: List<VideoItemUIState> = emptyList(),
        val trailerUrl: String? = null,
        /**
         * The trailer playing behind the description at the top of the screen, or null for the
         * still. Distinct from [trailerUrl], which is the full-screen trailer the user asked for.
         */
        val previewTrailerUrl: String? = null,
    ) : DetailsScreenState
}

@Immutable
internal sealed interface DetailsButtonUIState {
    data class TextButton(
        val textRes: Int,
        val icon: ImageVector,
        val action: DetailsAction,
        val textOverride: String? = null,
    ) : DetailsButtonUIState

    data class IconOnly(
        val icon: ImageVector,
        val contentDescription: Int,
        val action: DetailsAction,
    ) : DetailsButtonUIState

    data class WatchlistToggle(
        val contentDescription: Int,
        val action: DetailsAction,
    ) : DetailsButtonUIState

    data class WatchedToggle(
        val contentDescription: Int,
        val action: DetailsAction,
    ) : DetailsButtonUIState
}

@Immutable
internal data class DetailsInfoUIState(
    val ratings: List<RatingUIState>,
    /** Quality, sound, age rating, translation, track and subtitle counts, joined by " · ". */
    val factsLine: String,
    /** `Режиссёр: …` and `В ролях: …`, joined by " · ". */
    val creditsLine: String,
)


@Immutable
internal sealed interface DetailsAction : UIAction {
    data object PlayClicked : DetailsAction
    data object TrailerClicked : DetailsAction
    data object WatchlistToggleClicked : DetailsAction
    data object WatchedToggleClicked : DetailsAction
    data object ShareClicked : DetailsAction
    data class EpisodeSelected(val item: VideoItemUIState) : DetailsAction
    data class EpisodeWatchedChanged(val item: VideoItemUIState, val watched: Boolean) : DetailsAction
    data class SeasonWatchedChanged(val item: VideoItemUIState, val watched: Boolean) : DetailsAction
    data class SimilarSelected(val item: VideoItemUIState) : DetailsAction
    data object CloseTrailer : DetailsAction
    data object TrailerPreviewFinished : DetailsAction
    data object TrailerPreviewStopped : DetailsAction
}
