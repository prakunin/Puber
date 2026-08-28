package com.kino.puber.ui.feature.details.model

import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.FilmSlate
import com.adamglin.phosphoricons.duotone.CalendarBlank
import com.adamglin.phosphoricons.duotone.Play
import com.adamglin.phosphoricons.duotone.ShareNetwork
import com.adamglin.phosphoricons.duotone.VideoCamera
import com.kino.puber.R
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.api.models.WatchingInfo
import com.kino.puber.data.api.models.isSeriesLike

internal class DetailsScreenUIMapper(
    private val resources: ResourceProvider,
    private val itemMapper: VideoItemUIMapper,
) {

    fun map(item: Item, isInWatchlist: Boolean = item.inWatchlist ?: false): DetailsScreenState.Content {
        return mapContent(item = item, isInWatchlist = isInWatchlist)
    }

    fun map(
        item: Item,
        isInWatchlist: Boolean,
        initialEpisode: DetailsEpisodeTarget,
    ): DetailsScreenState.Content {
        return mapContent(
            item = item,
            isInWatchlist = isInWatchlist,
            initialEpisode = initialEpisode,
        )
    }

    private fun mapContent(
        item: Item,
        isInWatchlist: Boolean,
        initialEpisode: DetailsEpisodeTarget? = null,
    ): DetailsScreenState.Content {
        val seasons = if (item.type.isSeriesLike()) mapSeasons(item) else emptyList()
        val requestedEpisode = seasons.findEpisode(initialEpisode)
        val currentEpisode = requestedEpisode
            ?: if (item.type.isSeriesLike()) mapCurrentEpisode(item) else null
        return DetailsScreenState.Content(
            details = itemMapper.mapDetailedItem(item),
            info = buildInfo(item),
            buttons = buildButtons(item),
            isInWatchlist = isInWatchlist,
            isWatched = itemMapper.isItemWatched(item),
            seasons = seasons,
            // Opens on the season the viewer is returning to rather than the first one: three
            // seasons in, the first is never the one they want.
            selectedSeasonNumber = currentEpisode?.seasonNumber ?: seasons.lastOrNull()?.number,
            currentEpisode = currentEpisode,
            initialEpisodeFocusId = requestedEpisode?.id,
        )
    }

    fun mapSimilarItems(items: List<Item>): List<VideoItemUIState> {
        return itemMapper.mapShortItemList(items)
            .map { item -> item.copy(showTitle = true) }
    }

    private fun mapSeasons(item: Item): List<DetailsSeasonUIState> {
        val seasons = item.seasons ?: return emptyList()
        return seasons.map { season ->
            val episodes = season.episodes.orEmpty()
            val watchedCount = episodes.count { episode -> episode.watched == 1 }
            val episodesLabel = resources.getQuantityString(
                R.plurals.video_details_episodes,
                episodes.size,
                episodes.size,
            )
            DetailsSeasonUIState(
                number = season.number,
                episodes = episodes.map { episode -> mapEpisode(season.number, episodes, episode) },
                summary = if (watchedCount > 0) {
                    resources.getString(
                        R.string.video_details_season_summary_watched,
                        season.number,
                        episodesLabel,
                        watchedCount,
                    )
                } else {
                    resources.getString(
                        R.string.video_details_season_summary,
                        season.number,
                        episodesLabel,
                    )
                },
            )
        }
    }

    private fun mapCurrentEpisode(item: Item): VideoItemUIState? {
        val (seasonNumber, episodes, episode) = findFirstUnwatchedEpisode(item) ?: return null
        return mapEpisode(seasonNumber, episodes, episode)
    }

    private fun List<DetailsSeasonUIState>.findEpisode(target: DetailsEpisodeTarget?): VideoItemUIState? {
        if (target == null) return null
        return asSequence()
            .flatMap { season -> season.episodes.asSequence() }
            .firstOrNull { item ->
                item.seasonNumber == target.seasonNumber &&
                    item.episodeNumber == target.episodeNumber
            }
    }

    private fun mapEpisode(
        seasonNumber: Int,
        seasonEpisodes: List<Episode>,
        episode: Episode,
    ): VideoItemUIState {
        val thumbnailUrls = itemMapper.mapPosterUrls(episode.thumbnail)
        val title = buildString {
            append(episode.number)
            append(". ")
            append(episode.title ?: resources.getString(R.string.player_episode_untitled))
        }
        return VideoItemUIState(
            id = episode.id,
            title = title,
            imageUrl = thumbnailUrls.firstOrNull().orEmpty(),
            bigImageUrl = thumbnailUrls.firstOrNull().orEmpty(),
            imageFallbackUrls = thumbnailUrls.drop(1),
            showTitle = true,
            isWatched = episode.watched == 1,
            showWatchedIndicator = itemMapper.watchedIndicatorsEnabled(),
            isSeriesLike = false,
            seasonNumber = seasonNumber,
            episodeNumber = episode.number,
            isSeasonWatched = seasonEpisodes.all { it.watched == 1 },
            progressPercent = episode.watching?.let { watching ->
                if (watching.duration > 0) {
                    watching.time.toFloat() / watching.duration.toFloat()
                } else {
                    null
                }
            },
        )
    }

    private fun buildButtons(item: Item): List<DetailsButtonUIState> {
        val isSeriesLike = item.type.isSeriesLike()
        return if (isSeriesLike) {
            buildSeriesButtons(item)
        } else {
            buildMovieButtons(item)
        } + buildShareButton() + buildStatusButtons(isSeriesLike)
    }

    private fun buildShareButton() = DetailsButtonUIState.IconOnly(
        icon = PhosphorIcons.Duotone.ShareNetwork,
        contentDescription = R.string.video_details_button_share,
        action = DetailsAction.ShareClicked,
    )

    private fun buildSeriesButtons(item: Item): List<DetailsButtonUIState> = buildList {
        val continueText = findFirstUnwatchedEpisode(item)?.let { (season, _, episode) ->
            resources.getString(R.string.player_season_episode, season, episode.number)
        }
        add(
            DetailsButtonUIState.TextButton(
                textRes = R.string.video_details_button_watch_series,
                icon = PhosphorIcons.Duotone.Play,
                action = DetailsAction.PlayClicked,
                textOverride = continueText,
            )
        )
        if (item.trailer != null) {
            add(
                DetailsButtonUIState.IconOnly(
                    icon = PhosphorIcons.Duotone.VideoCamera,
                    contentDescription = R.string.video_details_button_trailer,
                    action = DetailsAction.TrailerClicked,
                )
            )
        }
        // This is the only way into the schedule screen. A text button took too much of the row,
        // so it became an icon rather than disappearing.
        if (item.imdb?.isNotBlank() == true) {
            add(
                DetailsButtonUIState.IconOnly(
                    icon = PhosphorIcons.Duotone.CalendarBlank,
                    contentDescription = R.string.video_details_button_schedule,
                    action = DetailsAction.ScheduleClicked,
                )
            )
        }
    }

    private fun buildMovieButtons(item: Item): List<DetailsButtonUIState> = buildList {
        add(
            DetailsButtonUIState.TextButton(
                textRes = R.string.video_details_button_watch_movie,
                icon = PhosphorIcons.Duotone.Play,
                action = DetailsAction.PlayClicked,
            )
        )
        if (item.trailer != null) {
            add(
                DetailsButtonUIState.TextButton(
                    textRes = R.string.video_details_button_trailer,
                    icon = PhosphorIcons.Duotone.FilmSlate,
                    action = DetailsAction.TrailerClicked,
                )
            )
        }
    }

    private fun buildStatusButtons(isSeriesLike: Boolean): List<DetailsButtonUIState> = buildList {
        add(
            DetailsButtonUIState.WatchlistToggle(
                contentDescription = if (isSeriesLike) {
                    R.string.video_details_button_add_to_watchlist
                } else {
                    R.string.video_details_button_add_to_bookmarks
                },
                action = DetailsAction.WatchlistToggleClicked,
            )
        )
        if (!isSeriesLike) {
            add(
                DetailsButtonUIState.WatchedToggle(
                    contentDescription = R.string.video_details_button_mark_watched,
                    action = DetailsAction.WatchedToggleClicked,
                )
            )
        }
    }

    private fun findFirstUnwatchedEpisode(item: Item): FirstEpisode? {
        val seasons = item.seasons ?: return null
        for (season in seasons) {
            val episodes = season.episodes ?: continue
            for (episode in episodes) {
                if (episode.watched != 1) {
                    return FirstEpisode(season.number, episodes, episode)
                }
            }
        }
        return null
    }

    private data class FirstEpisode(
        val seasonNumber: Int,
        val episodes: List<Episode>,
        val episode: Episode,
    )

    private fun buildInfo(item: Item): DetailsInfoUIState {
        val details = itemMapper.mapDetailedItem(item)
        return DetailsInfoUIState(
            ratings = details.ratings,
            chips = buildChips(item),
            genresLine = buildGenresLine(item),
            factsLine = buildFactsLine(item),
            directorLine = buildDirectorLine(item),
            castLine = buildCastLine(item),
            resumeLine = buildResumeLine(item),
        )
    }

    private fun mapSeriesStatus(item: Item): String? {
        if (!item.type.isSeriesLike()) return null
        return resources.getString(
            if (item.finished == true) {
                R.string.video_details_series_status_finished
            } else {
                // KinoPub may omit `finished` for a series that is still being released. An absent
                // flag therefore carries the same user-facing meaning as an explicit false.
                R.string.video_details_series_status_ongoing
            }
        )
    }

    /**
     * What the thing is, not what is inside it: kind, size, production country, quality, sound,
     * age. Tracks and subtitles stay on the line below -- they are read only once the choice is
     * already made.
     */
    private fun buildChips(item: Item): List<String> = buildList {
        val isSeriesLike = item.type.isSeriesLike()
        add(
            resources.getString(
                if (isSeriesLike) R.string.video_details_chip_series else R.string.video_details_chip_movie
            )
        )
        if (isSeriesLike) {
            item.seasons?.size?.takeIf { count -> count > 0 }?.let { count ->
                add(resources.getQuantityString(R.plurals.video_details_chip_seasons, count, count))
            }
            mapSeriesStatus(item)?.let(::add)
        } else {
            item.duration?.total?.takeIf { total -> total > 0 }?.let { total ->
                add(with(itemMapper) { total.formatDurationWithResources() })
            }
        }
        item.year?.takeIf { year -> year > 0 }?.let { year -> add(year.toString()) }
        item.countries.orEmpty()
            .map { country -> country.title.trim() }
            .filter { country -> country.isNotEmpty() }
            .joinToString(", ")
            .takeIf(String::isNotEmpty)
            ?.let(::add)
        item.displayQuality()?.let(::add)
        if (item.ac3 == 1 || item.mediaItemsHaveSurroundSound()) {
            add(resources.getString(R.string.video_details_info_sound_surround))
        }
        item.ageRating?.takeIf(String::isNotBlank)?.let(::add)
    }

    private fun buildGenresLine(item: Item): String =
        item.genres.orEmpty()
            .map { genre -> genre.title.trim() }
            .filter { genre -> genre.isNotEmpty() }
            .joinToString(", ")
            .takeIf(String::isNotEmpty)
            ?.let { genres -> resources.getString(R.string.video_details_facts_genres, genres) }
            .orEmpty()

    private fun buildFactsLine(item: Item): String = buildList {
        item.voice?.takeIf(String::isNotBlank)?.let(::add)
        item.playbackAudioTrackCount().takeIf { it > 0 }?.let { count ->
            add(resources.getString(R.string.video_details_facts_audio_tracks, count))
        }
        item.subtitleCount().takeIf { it > 0 }?.let { count ->
            add(resources.getString(R.string.video_details_facts_subtitles, count))
        }
    }.joinToString(FACT_SEPARATOR)

    private fun buildDirectorLine(item: Item): String =
        item.director?.takeIf(String::isNotBlank)
            ?.let { director -> resources.getString(R.string.video_details_facts_director, director) }
            .orEmpty()

    private fun buildCastLine(item: Item): String =
        item.castMembers().takeIf { cast -> cast.isNotEmpty() }
            ?.let { cast -> resources.getString(R.string.video_details_facts_cast, cast.joinToString(", ")) }
            .orEmpty()

    private fun buildResumeLine(item: Item): String {
        return if (item.type.isSeriesLike()) buildSeriesResumeLine(item) else buildMovieResumeLine(item)
    }

    private fun buildSeriesResumeLine(item: Item): String {
        val next = findFirstUnwatchedEpisode(item)
            ?: return resources.getString(R.string.video_details_resume_finished)
        val left = next.episode.watching.timeLeft()
        return if (left != null) {
            resources.getString(
                R.string.video_details_resume_series,
                next.seasonNumber,
                next.episode.number,
                with(itemMapper) { left.formatDurationWithResources() },
            )
        } else {
            resources.getString(
                R.string.video_details_resume_series_next,
                next.seasonNumber,
                next.episode.number,
            )
        }
    }

    private fun buildMovieResumeLine(item: Item): String {
        val left = (item.watching ?: item.videos?.firstOrNull()?.watching).timeLeft()
        val total = item.duration?.total ?: 0
        return when {
            itemMapper.isItemWatched(item) -> resources.getString(R.string.video_details_resume_finished)
            left != null -> resources.getString(
                R.string.video_details_resume_movie,
                with(itemMapper) { left.formatDurationWithResources() },
            )
            total > 0 -> resources.getString(
                R.string.video_details_resume_not_started,
                with(itemMapper) { total.formatDurationWithResources() },
            )
            else -> ""
        }
    }

    /** What is left when playback has started. Null when it has not, and the line says so. */
    private fun WatchingInfo?.timeLeft(): Int? {
        if (this == null || time <= 0 || duration <= 0) return null
        return (duration - time).takeIf { left -> left > 0 }
    }

    private fun Item.subtitleCount(): Int {
        return videos.orEmpty().sumOf { video -> video.subtitles.orEmpty().size } +
            seasons.orEmpty()
                .flatMap { season -> season.episodes.orEmpty() }
                .sumOf { episode -> episode.subtitles.orEmpty().size }
    }

    private fun Item.playbackAudioTrackCount(): Int {
        return if (type.isSeriesLike()) {
            firstPlayableEpisode()?.audios.orEmpty().size
        } else {
            videos?.firstOrNull()?.audios.orEmpty().size
        }
    }

    private fun Item.firstPlayableEpisode(): Episode? {
        val seasons = seasons.orEmpty()
        for (season in seasons) {
            val firstUnwatched = season.episodes.orEmpty().firstOrNull { episode -> episode.watched != 1 }
            if (firstUnwatched != null) return firstUnwatched
        }
        return seasons.firstOrNull()?.episodes?.firstOrNull()
    }

    private fun Item.displayQuality(): String? {
        return videos.orEmpty()
            .flatMap { video -> video.files.orEmpty() }
            .mapNotNull { file ->
                file.quality
                    ?: file.h?.takeIf { it > 0 }?.let { "${it}p" }
                    ?: file.url?.hls4?.takeIf { it.isNotBlank() }?.let { "4K" }
            }
            .firstOrNull()
    }

    private fun Item.mediaItemsHaveSurroundSound(): Boolean {
        return videos.orEmpty().any { video -> video.hasSurroundSound() } ||
            seasons.orEmpty()
                .flatMap { it.episodes.orEmpty() }
                .any { episode -> episode.hasSurroundSound() }
    }

    private fun Item.castMembers(): List<String> {
        return cast.orEmpty()
            .split(",")
            .map { actor -> actor.trim() }
            .filter { actor -> actor.isNotBlank() }
    }

    private fun Video.hasSurroundSound(): Boolean {
        return ac3 == 1 || audios.orEmpty().any { audio -> (audio.channels ?: 0) >= SURROUND_CHANNELS }
    }

    private fun Episode.hasSurroundSound(): Boolean {
        return ac3 == 1 || audios.orEmpty().any { audio -> (audio.channels ?: 0) >= SURROUND_CHANNELS }
    }

    private companion object {
        const val SURROUND_CHANNELS = 6
        const val FACT_SEPARATOR = " · "
    }
}
