package com.kino.puber.core.ui.model

import com.kino.puber.R
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.uikit.component.HeroItemState
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoQualityBadge
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.isSeriesLike
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.WatchState
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.data.repository.toWatchStateOrNull
import java.util.Locale

/** KinoPub reports its own rating as a 0..100 percentage; the UI shows it on a 0..10 scale. */
private const val RATING_PERCENT_TO_TEN_SCALE = 10

private const val UHD_MIN_QUALITY = 2160
private const val HD_MIN_QUALITY = 720
private const val TITLE_SEPARATOR = " / "

class VideoItemUIMapper(
    private val resources: ResourceProvider,
    private val navigationPreferencesRepository: NavigationPreferencesRepository? = null,
    private val watchStateRepository: WatchStateRepository? = null,
) {

    fun mapShortItemList(items: List<Item>): List<VideoItemUIState> {
        return items.map { mapShortItem(it) }
    }

    fun mapShortItem(item: Item): VideoItemUIState {
        val mediumPosterUrls = mapPosterUrls(item.posters?.medium)
        val bigPosterUrls = mapPosterUrls(item.posters?.big)
        val widePosterUrls = mapPosterUrls(item.posters?.wide)
        val (localTitle, originalTitle) = splitItemTitle(item.title)
        return VideoItemUIState(
            id = item.id,
            title = item.title,
            imageUrl = mediumPosterUrls.firstOrEmpty(),
            bigImageUrl = bigPosterUrls.firstOrEmpty(),
            wideImageUrl = widePosterUrls.firstOrEmpty(),
            imageFallbackUrls = (mediumPosterUrls.drop(1) + bigPosterUrls.drop(1) + widePosterUrls.drop(1))
                .distinct(),
            unwatchedCount = item.new,
            ratings = buildRatings(item),
            isWatched = isItemWatched(item),
            progressPercent = if (watchedIndicatorsEnabled()) watchProgress(item) else null,
            showWatchedIndicator = watchedIndicatorsEnabled(),
            isSeriesLike = item.type.isSeriesLike(),
            isSaved = if (item.type.isSeriesLike()) {
                item.inWatchlist == true || item.subscribed == true
            } else {
                item.bookmarks.orEmpty().isNotEmpty()
            },
            year = item.year?.takeIf { it > 0 }?.toString().orEmpty(),
            qualityBadge = qualityBadge(item.quality),
            localTitle = localTitle,
            originalTitle = originalTitle,
            pillRatings = buildPillRatings(item),
        )
    }

    fun mapHeroItems(items: List<Item>): List<HeroItemState> {
        return items.map { item ->
            val widePosterUrls = mapPosterUrls(item.posters?.wide)
            val bigPosterUrls = mapPosterUrls(item.posters?.big)
            val heroPosterUrls = (widePosterUrls + bigPosterUrls).distinct()
            HeroItemState(
                id = item.id,
                title = item.title,
                wideImageUrl = heroPosterUrls.firstOrNull().orEmpty(),
                fallbackImageUrl = heroPosterUrls.drop(1).firstOrNull().orEmpty(),
                fallbackImageUrls = heroPosterUrls.drop(2),
                year = item.year?.toString().orEmpty(),
                ratings = buildRatings(item),
                genres = item.genres?.joinToString(", ") { it.title }.orEmpty(),
                country = item.countries?.joinToString(", ") { it.title }.orEmpty(),
                duration = if (item.type.isSeriesLike()) {
                    item.seasons?.size?.let {
                        resources.getString(R.string.video_details_label_seasons, it)
                    }.orEmpty()
                } else {
                    buildMovieDuration(item)
                },
            )
        }
    }

    fun mapDetailedItem(item: Item): VideoDetailsUIState {
        val widePosterUrls = mapPosterUrls(item.posters?.wide)
        val bigPosterUrls = mapPosterUrls(item.posters?.big)
        return VideoDetailsUIState(
            id = item.id,
            title = item.title.formatTitle(),
            description = item.plot.orEmpty(),
            imageUrl = widePosterUrls.firstOrEmpty(),
            imageFallbackUrls = (widePosterUrls.drop(1) + bigPosterUrls).distinct(),
            trailerUrl = item.trailer?.url.orEmpty(),
            ratings = buildRatings(item),
            genres = item.genres.orEmpty().joinToString(", ") { it.title },
            country = item.countries.orEmpty().joinToString(", ") { it.title },
            year = item.year?.toString().orEmpty(),
            duration = buildDuration(item),
        )
    }

    /**
     * Partial-watch progress for a list card, or null when the item is untouched, fully watched, or
     * nothing is known about it yet.
     *
     * Catalogue payloads carry no watch fields at all, so this comes from the local watch-state
     * index; the item's own fields win when it has them (details and watching endpoints do).
     */
    fun watchProgress(item: Item): Float? = watchState(item)?.progressPercent

    fun isItemWatched(item: Item): Boolean = watchState(item)?.isFullyWatched == true

    private fun watchState(item: Item): WatchState? =
        watchStateRepository?.resolve(item) ?: item.toWatchStateOrNull()

    fun buildRatings(item: Item): List<RatingUIState> = buildList {
        item.kinopoiskRating?.takeIf { it.isValidRating() }?.let { add(RatingUIState.KP(it)) }

        item.imdbRating?.takeIf { it.isValidRating() }?.let { add(RatingUIState.IMDB(it)) }

        item.ratingPercentage?.takeIf { it > 0 }?.let { rating ->
            add(RatingUIState.PUB((rating.toFloat() / RATING_PERCENT_TO_TEN_SCALE).toString()))
        }
    }

    /** Ratings for the poster card pills: PUB first; every text has at most one decimal (`8.3`, `10`). */
    fun buildPillRatings(item: Item): List<RatingUIState> = buildList {
        item.ratingPercentage?.takeIf { it > 0 }?.let { percent ->
            val whole = percent / RATING_PERCENT_TO_TEN_SCALE
            val tenth = percent % RATING_PERCENT_TO_TEN_SCALE
            add(RatingUIState.PUB(if (tenth == 0) "$whole" else "$whole.$tenth"))
        }
        item.kinopoiskRating?.takeIf { it.isValidRating() }?.let { add(RatingUIState.KP(it.toPillRating())) }
        item.imdbRating?.takeIf { it.isValidRating() }?.let { add(RatingUIState.IMDB(it.toPillRating())) }
    }

    private fun qualityBadge(quality: Int?): VideoQualityBadge? = when {
        quality == null -> null
        quality >= UHD_MIN_QUALITY -> VideoQualityBadge.UHD
        quality >= HD_MIN_QUALITY -> VideoQualityBadge.HD
        else -> null
    }

    private fun String?.isValidRating(): Boolean {
        if (this == null) return false
        val value = toFloatOrNull() ?: return false
        return value > 0f
    }

    fun buildDuration(item: Item): String {
        return item.seasons?.let { seasons ->
            resources.getString(R.string.video_details_label_seasons, seasons.size)
        } ?: buildMovieDuration(item)
    }

    private fun buildMovieDuration(item: Item): String {
        return resources.getString(
            R.string.video_details_label_duration,
            item.duration?.total?.formatDurationWithResources().orEmpty(),
        )
    }

    fun Int.formatDurationWithResources(): String {
        val template = DurationLabel.template(this)
        return resources.getString(template.resId, *template.args.toTypedArray())
    }

    private fun String.formatTitle(): String {
        return split("/").joinToString(separator = "\n") { it.trim() }
    }

    fun mapPosterUrl(url: String?): String = mapPosterUrls(url).firstOrEmpty()

    fun mapPosterUrls(url: String?): List<String> {
        return url.orEmpty().ensureHttps().takeIf { it.isNotBlank() }
            ?.let(::listOf)
            .orEmpty()
    }

    fun watchedIndicatorsEnabled(): Boolean {
        return navigationPreferencesRepository?.contentPreferences?.value?.showWatchedIndicators ?: true
    }
}

/**
 * Splits `Local / Original` once; a bare `/` (`Fate/Zero`) is part of the local title. With an empty
 * local part the original is the title and there is no original line.
 */
internal fun splitItemTitle(raw: String): Pair<String, String> {
    val parts = raw.split(TITLE_SEPARATOR, limit = 2)
    val local = parts[0].trim()
    val original = parts.getOrNull(1)?.trim().orEmpty()
    return if (local.isEmpty()) original to "" else local to original
}

/** `5.978` becomes `6`, `7.25` becomes `7.3`; the dot never follows the device locale. */
private fun String.toPillRating(): String {
    val value = toFloatOrNull() ?: return this
    val text = String.format(Locale.ROOT, "%.1f", value)
    return text.removeSuffix(".0")
}

private fun List<String>.firstOrEmpty(): String = firstOrNull().orEmpty()

private fun String.ensureHttps(): String {
    return if (startsWith("http://")) replaceFirst("http://", "https://") else this
}
