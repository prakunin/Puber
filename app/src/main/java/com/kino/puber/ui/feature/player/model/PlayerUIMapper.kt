package com.kino.puber.ui.feature.player.model

import com.kino.puber.R
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.model.DurationLabel
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.data.api.models.Audio
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.data.api.models.VideoFile
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.domain.model.BufferPreset
import com.kino.puber.ui.feature.player.vm.sideLoadable

internal class PlayerUIMapper(
    private val resources: ResourceProvider,
    private val navigationPreferencesRepository: NavigationPreferencesRepository? = null,
) {

    fun mapAudioTracks(audios: List<Audio>?): List<AudioTrackUIState> {
        return audios?.mapIndexed { index, audio ->
            val label = buildString {
                append(audio.lang ?: "Unknown")
                audio.author?.title?.let { append(" · $it") }
                    ?: audio.type?.title?.let { append(" · $it") }
            }
            AudioTrackUIState(
                index = index,
                label = label,
                language = audio.lang ?: "",
            )
        } ?: emptyList()
    }

    fun mapSubtitleTracks(subtitles: List<SubtitleLink>?): List<SubtitleTrackUIState> {
        val offRow = SubtitleTrackUIState(
            label = resources.getString(R.string.player_subtitles_off),
            language = "",
            url = "",
        )
        val apiRows = subtitles.sideLoadable().map { sub ->
            SubtitleTrackUIState(
                label = "",
                language = sub.lang,
                url = sub.url,
                isForced = sub.forced,
                sourceFile = sub.file,
            )
        }
        return listOf(offRow) + subtitleLabeler(resources).apply(apiRows)
    }

    fun mapQualities(files: List<VideoFile>?): List<QualityUIState> {
        if (files.isNullOrEmpty()) return emptyList()

        // Deduplicate by quality string, keep first occurrence (prefer h265 over h264)
        val unique = files
            .filter { file ->
                file.url?.let { url ->
                    listOf(url.hls4, url.hls, url.http).any { !it.isNullOrBlank() }
                } == true
            }
            .distinctBy { it.quality ?: "${it.h}p" }
            .sortedByDescending { it.qualityId ?: 0 }
        if (unique.isEmpty()) return emptyList()

        val result = mutableListOf(
            QualityUIState(
                index = 0,
                label = resources.getString(R.string.player_aspect_auto),
                qualityId = null,
                width = null,
                height = null,
            )
        )
        unique.forEachIndexed { i, file ->
            result.add(
                QualityUIState(
                    index = i + 1,
                    label = file.quality ?: "${file.h}p",
                    qualityId = file.qualityId,
                    width = file.w,
                    height = file.h,
                )
            )
        }
        return result
    }

    fun mapEpisodes(item: Item): EpisodesPanelUIState? {
        val seasons = item.seasons ?: return null
        val mappedSeasons = seasons.mapNotNull { season ->
            val sourceEpisodes = season.episodes.orEmpty()
            if (sourceEpisodes.isEmpty()) return@mapNotNull null
            val isSeasonWatched = sourceEpisodes.all { it.watched == 1 }
            val episodes = sourceEpisodes.map { episode ->
                val thumbnailUrls = mapPosterUrls(episode.thumbnail)
                val title = buildString {
                    append(episode.number)
                    append(". ")
                    append(episode.title ?: resources.getString(R.string.player_episode_untitled))
                }
                VideoItemUIState(
                    id = episode.id,
                    title = title,
                    imageUrl = thumbnailUrls.firstOrNull().orEmpty(),
                    bigImageUrl = thumbnailUrls.firstOrNull().orEmpty(),
                    imageFallbackUrls = thumbnailUrls.drop(1),
                    showTitle = true,
                    isWatched = episode.watched == 1,
                    showWatchedIndicator = watchedIndicatorsEnabled(),
                    isSeriesLike = false,
                    seasonNumber = season.number,
                    episodeNumber = episode.number,
                    isSeasonWatched = isSeasonWatched,
                )
            }
            EpisodeSeasonUIState(
                number = season.number,
                title = resources.getString(
                    R.string.player_season_episodes_count,
                    season.number,
                    episodes.size,
                ),
                episodes = episodes,
            )
        }
        return mappedSeasons
            .takeIf { it.isNotEmpty() }
            ?.let(::EpisodesPanelUIState)
    }

    fun watchedIndicatorsEnabled(): Boolean {
        return navigationPreferencesRepository?.contentPreferences?.value?.showWatchedIndicators ?: true
    }

    private fun mapPosterUrls(url: String?): List<String> {
        return url.orEmpty().ensureHttps().takeIf { it.isNotBlank() }
            ?.let(::listOf)
            .orEmpty()
    }

    fun buildTitle(item: Item): String {
        return item.title
    }

    /**
     * The About panel's contents. [resolvedDurationSeconds] is the length of what is playing now,
     * which is not the same as the item's own duration once the item is a series.
     */
    fun buildAbout(item: Item, resolvedDurationSeconds: Int?): PlayerAboutUIState {
        val seconds = PlayerAboutFactory.durationSeconds(item, resolvedDurationSeconds)
        return PlayerAboutFactory.build(
            item = item,
            durationLabel = seconds?.let { total ->
                val template = DurationLabel.template(total)
                resources.getString(template.resId, *template.args.toTypedArray())
            },
            imdbLabel = resources.getString(R.string.player_about_rating_imdb),
            kinopoiskLabel = resources.getString(R.string.player_about_rating_kinopoisk),
        )
    }

    fun buildSubtitle(seasonNumber: Int?, episodeNumber: Int?, episodeTitle: String?): String? {
        if (seasonNumber == null || episodeNumber == null) return null
        return if (episodeTitle != null) {
            resources.getString(R.string.player_season_episode_title, seasonNumber, episodeNumber, episodeTitle)
        } else {
            resources.getString(R.string.player_season_episode, seasonNumber, episodeNumber)
        }
    }

    fun defaultSoundModeLabel(): String = resources.getString(R.string.player_sound_stereo)

    fun formatSeekOffset(isForward: Boolean, stepSeconds: Int): String {
        return if (isForward) {
            resources.getString(R.string.player_seek_forward, stepSeconds)
        } else {
            resources.getString(R.string.player_seek_backward, stepSeconds)
        }
    }

    fun mapSkipSegmentLabel(type: SkipSegmentType): String {
        return when (type) {
            SkipSegmentType.INTRO -> resources.getString(R.string.skip_intro)
            SkipSegmentType.RECAP -> resources.getString(R.string.skip_recap)
            SkipSegmentType.CREDITS -> resources.getString(R.string.skip_credits)
            SkipSegmentType.PREVIEW -> resources.getString(R.string.skip_preview)
        }
    }

    fun formatTime(positionMs: Long): String = formatPlaybackClock(positionMs)

    companion object {
        val SPEEDS = listOf(
            SpeedUIState(0, "0.25x", 0.25f),
            SpeedUIState(1, "0.5x", 0.5f),
            SpeedUIState(2, "0.75x", 0.75f),
            SpeedUIState(3, "1.0x", 1.0f),
            SpeedUIState(4, "1.25x", 1.25f),
            SpeedUIState(5, "1.5x", 1.5f),
            SpeedUIState(6, "1.75x", 1.75f),
            SpeedUIState(7, "2.0x", 2.0f),
        )

        val ASPECT_RATIOS = listOf(
            AspectRatioUIState(0, "Auto", AspectRatioMode.AUTO),
            AspectRatioUIState(1, "Stretch", AspectRatioMode.STRETCH),
            AspectRatioUIState(2, "Crop", AspectRatioMode.CROP),
        )

        const val DEFAULT_SPEED_INDEX = 3
        const val DEFAULT_ASPECT_RATIO_INDEX = 0
        const val DEFAULT_BUFFER_PRESET_INDEX = 0
    }

    fun mapBufferPresets(): List<BufferPresetUIState> = listOf(
        BufferPresetUIState(0, resources.getString(R.string.player_buffer_auto), BufferPreset.AUTO),
        BufferPresetUIState(1, resources.getString(R.string.player_buffer_small), BufferPreset.SMALL),
        BufferPresetUIState(2, resources.getString(R.string.player_buffer_medium), BufferPreset.MEDIUM),
        BufferPresetUIState(3, resources.getString(R.string.player_buffer_large), BufferPreset.LARGE),
        BufferPresetUIState(4, resources.getString(R.string.player_buffer_max), BufferPreset.MAX),
    )

    fun mapAspectRatios(): List<AspectRatioUIState> = listOf(
        AspectRatioUIState(0, resources.getString(R.string.player_aspect_auto), AspectRatioMode.AUTO),
        AspectRatioUIState(1, resources.getString(R.string.player_aspect_stretch), AspectRatioMode.STRETCH),
        AspectRatioUIState(2, resources.getString(R.string.player_aspect_crop), AspectRatioMode.CROP),
    )
}

private fun String.ensureHttps(): String {
    return if (startsWith("http://")) replaceFirst("http://", "https://") else this
}
