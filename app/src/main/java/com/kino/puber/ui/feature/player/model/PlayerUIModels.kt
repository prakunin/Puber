package com.kino.puber.ui.feature.player.model

import androidx.compose.runtime.Immutable
import com.kino.puber.domain.model.BufferPreset
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState

@Immutable
internal data class EpisodesPanelUIState(
    val seasons: List<EpisodeSeasonUIState>,
)

@Immutable
internal data class EpisodeSeasonUIState(
    val number: Int,
    val title: String,
    val episodes: List<VideoItemUIState>,
)

@Immutable
internal data class AudioTrackUIState(
    val index: Int,
    val label: String,
    val language: String,
)

@Immutable
internal data class SubtitleTrackUIState(
    val index: Int,
    val label: String,
    val language: String,
    val url: String,
)

@Immutable
internal data class SoundModeUIState(
    val index: Int,
    val label: String,
)

@Immutable
internal data class QualityUIState(
    val index: Int,
    val label: String,
    val qualityId: Int?,
    val width: Int?,
    val height: Int?,
)

@Immutable
internal data class SpeedUIState(
    val index: Int,
    val label: String,
    val speed: Float,
)

@Immutable
internal data class AspectRatioUIState(
    val index: Int,
    val label: String,
    val mode: AspectRatioMode,
)

internal enum class AspectRatioMode {
    AUTO,
    STRETCH,
    CROP,
}

@Immutable
internal data class BufferPresetUIState(
    val index: Int,
    val label: String,
    val preset: BufferPreset,
)

@Immutable
internal data class SeekIndicatorState(
    val isForward: Boolean,
    val offsetText: String,
    val targetTimeText: String,
)

@Immutable
internal data class PlayPauseIndicatorState(
    val isPlaying: Boolean,
)

@Immutable
internal data class ResumeDialogState(
    val savedPosition: Long,
    val formattedTime: String,
    val episodeInfo: String? = null,
)

/**
 * Everything the About panel knows about what is playing.
 *
 * Each line is already joined and cleaned by [PlayerAboutFactory]: an empty string or a null means
 * the API sent nothing, and the panel leaves that row out rather than printing a dash.
 */
@Immutable
internal data class PlayerAboutUIState(
    val title: String,
    /** `2010 · 2 ч 28 мин · США · 16+` — what the thing is, in one line. */
    val metaLine: String,
    val genresLine: String,
    /** `IMDb 8.8 · Кинопоиск 8.7`. */
    val ratingsLine: String,
    val description: String?,
    val director: String?,
    val cast: String?,
) {
    /**
     * The title does not count towards this: the player already prints it above the progress bar,
     * so a panel carrying nothing else would open on what the viewer can read behind it.
     */
    val isEmpty: Boolean
        get() = metaLine.isEmpty() &&
            genresLine.isEmpty() &&
            ratingsLine.isEmpty() &&
            description == null &&
            director == null &&
            cast == null
}
