package com.kino.puber.ui.feature.player.model

import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.isSeriesLike

private const val FACT_SEPARATOR = " · "
private const val LIST_SEPARATOR = ", "

/**
 * Turns an [Item] into the lines the About panel prints.
 *
 * Nothing here needs a `Context`: the two labels that are localised arrive already resolved, which
 * keeps the joining rules — which facts are worth printing, and how a missing one disappears
 * without leaving its separator behind — testable on their own.
 */
internal object PlayerAboutFactory {

    fun build(
        item: Item,
        durationLabel: String?,
        imdbLabel: String,
        kinopoiskLabel: String,
    ): PlayerAboutUIState = PlayerAboutUIState(
        title = item.title.trim(),
        metaLine = joinFacts(
            item.year?.takeIf { year -> year > 0 }?.toString(),
            durationLabel,
            item.countries.orEmpty().map { country -> country.title }.joinNames(),
            item.ageRating,
        ),
        genresLine = item.genres.orEmpty().map { genre -> genre.title }.joinNames().orEmpty(),
        ratingsLine = joinFacts(
            rating(imdbLabel, item.imdbRating),
            rating(kinopoiskLabel, item.kinopoiskRating),
        ),
        description = item.plot.cleaned(),
        director = item.director.splitNames(),
        cast = item.cast.splitNames(),
    )

    /**
     * How long what is playing runs, in seconds.
     *
     * A series item's own duration is every episode added together, which beside the progress bar
     * would read as the length of the episode on screen. So the item's figure is trusted only for
     * a film; an episode's length comes from the resolved media or goes unmentioned.
     */
    fun durationSeconds(item: Item, resolvedDurationSeconds: Int?): Int? {
        resolvedDurationSeconds?.takeIf { seconds -> seconds > 0 }?.let { seconds -> return seconds }
        if (item.type.isSeriesLike()) return null
        return item.duration?.total?.takeIf { total -> total > 0 }
    }

    private fun rating(label: String, value: String?): String? {
        val score = value?.trim()?.takeIf { raw -> (raw.toFloatOrNull() ?: 0f) > 0f } ?: return null
        return "$label $score"
    }

    private fun joinFacts(vararg facts: String?): String =
        facts.mapNotNull { fact -> fact.cleaned() }.joinToString(FACT_SEPARATOR)

    /** A comma-separated payload field: the API is free with stray commas and padding. */
    private fun String?.splitNames(): String? =
        this?.split(",").orEmpty().joinNames()

    private fun List<String>.joinNames(): String? =
        mapNotNull { name -> name.cleaned() }
            .joinToString(LIST_SEPARATOR)
            .takeIf(String::isNotEmpty)

    private fun String?.cleaned(): String? = this?.trim()?.takeIf(String::isNotEmpty)
}
