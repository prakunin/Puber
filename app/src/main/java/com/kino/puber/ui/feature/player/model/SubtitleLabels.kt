package com.kino.puber.ui.feature.player.model

import com.kino.puber.data.api.models.SubtitleLink
import java.util.Locale

private const val QUALIFIER_SEPARATOR = " · "

/**
 * Picker labels for the API subtitle list, one per entry and in the same order.
 *
 * Every row is named after its language ("rus" reads as "Русский"), machine-generated rows get
 * their own name, and forced rows a qualifier. Rows that would still read the same are told
 * apart by an ordinal.
 */
internal fun subtitleLabels(
    subtitles: List<SubtitleLink>,
    displayLocale: Locale,
    aiGeneratedLabel: String,
    forcedQualifier: String,
    variantLabel: (label: String, ordinal: Int) -> String,
    unknownLabel: (position: Int) -> String,
): List<String> {
    val labels = subtitles.mapIndexed { position, subtitle ->
        val base = if (canonicalSubtitleLanguage(subtitle.lang) == AI_GENERATED_SUBTITLE_LANGUAGE) {
            aiGeneratedLabel
        } else {
            subtitleLanguageDisplayName(subtitle.lang, displayLocale)
                ?: subtitle.lang.trim().takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT)
                ?: unknownLabel(position + 1)
        }
        if (subtitle.forced == true) "$base$QUALIFIER_SEPARATOR$forcedQualifier" else base
    }
    val collisions = labels.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    val ordinals = mutableMapOf<String, Int>()
    return labels.map { label ->
        if (label in collisions) variantLabel(label, ordinals.merge(label, 1, Int::plus) ?: 1) else label
    }
}
