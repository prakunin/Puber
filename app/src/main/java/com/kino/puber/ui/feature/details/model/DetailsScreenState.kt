package com.kino.puber.ui.feature.details.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
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
        /** Empty on a film. Seasons in order, each carrying its own episodes. */
        val seasons: List<DetailsSeasonUIState> = emptyList(),
        val selectedSeasonNumber: Int? = null,
        val currentEpisode: VideoItemUIState? = null,
        val initialEpisodeFocusId: Int? = null,
        val similarItems: List<VideoItemUIState> = emptyList(),
        val trailerUrl: String? = null,
        /**
         * The trailer playing behind the description at the top of the screen, or null for the
         * still. Distinct from [trailerUrl], which is the full-screen trailer the user asked for.
         */
        val previewTrailerUrl: String? = null,
    ) : DetailsScreenState {

        /** The season under the chosen chip; the last one before anything has been chosen. */
        val selectedSeason: DetailsSeasonUIState?
            get() = seasons.firstOrNull { season -> season.number == selectedSeasonNumber }
                ?: seasons.lastOrNull()
    }
}

@Immutable
internal data class DetailsSeasonUIState(
    val number: Int,
    val episodes: List<VideoItemUIState>,
    /** `3 сезон · 8 серий · просмотрено 4` -- the caption beside the chips. */
    val summary: String,
)

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
    /** Kind, season count or duration, status, year, country, quality, sound, age -- one chip each. */
    val chips: List<String>,
    /**
     * `Жанры: …`. A line rather than a chip: four genres run past the right edge of a row that
     * neither wraps nor scrolls, where a line ellipsizes instead. Empty when there are none.
     */
    val genresLine: String,
    /** Audio tracks and subtitles, joined by " · ". */
    val factsLine: String,
    /** `Режиссёр: …`. Empty when the payload carries no director. */
    val directorLine: String,
    /**
     * `В ролях: …` on a line of its own. Sharing one line with the director truncated the cast
     * at the first name, so it was never actually shown.
     */
    val castLine: String,
    /** `Остановились на 3 сезоне, 5 серии · осталось 22 мин`, or the duration when unstarted. */
    val resumeLine: String,
)

@Immutable
internal sealed interface DetailsAction : UIAction {
    data object PlayClicked : DetailsAction
    data object TrailerClicked : DetailsAction
    data object WatchlistToggleClicked : DetailsAction
    data object WatchedToggleClicked : DetailsAction
    data object ShareClicked : DetailsAction
    data class SeasonSelected(val number: Int) : DetailsAction
    data class EpisodeSelected(val item: VideoItemUIState) : DetailsAction
    data class EpisodeWatchedChanged(val item: VideoItemUIState, val watched: Boolean) : DetailsAction
    data class SeasonWatchedChanged(val item: VideoItemUIState, val watched: Boolean) : DetailsAction
    data class SimilarSelected(val item: VideoItemUIState) : DetailsAction
    data object ScheduleClicked : DetailsAction
    data object CloseTrailer : DetailsAction
    data object TrailerPreviewFinished : DetailsAction
    data object TrailerPreviewStopped : DetailsAction
}
