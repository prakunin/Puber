package com.kino.puber.ui.feature.player.model

import com.kino.puber.data.api.models.SubtitleLink
import java.util.Locale

/** KinoPub's language code for machine-generated subtitles. */
private const val AI_GENERATED_SUBTITLE_LANGUAGE = "ai"
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

internal fun canonicalSubtitleLanguage(language: String): String {
    val normalized = language
        .trim()
        .lowercase(Locale.ROOT)
        .substringBefore('-')
        .substringBefore('_')
    val code = bibliographicToTerminology[normalized] ?: normalized
    return runCatching { Locale.forLanguageTag(code).isO3Language }
        .getOrNull()
        ?.takeIf { it.isNotBlank() && it != "und" }
        ?: code
}

/** ISO 639-2 bibliographic codes that differ from the terminology codes `Locale` knows. */
private val bibliographicToTerminology = mapOf(
    "alb" to "sqi", "arm" to "hye", "baq" to "eus", "bur" to "mya", "chi" to "zho",
    "cze" to "ces", "dut" to "nld", "fre" to "fra", "geo" to "kat", "ger" to "deu",
    "gre" to "ell", "ice" to "isl", "mac" to "mkd", "mao" to "mri", "may" to "msa",
    "per" to "fas", "rum" to "ron", "slo" to "slk", "tib" to "bod", "wel" to "cym",
)

/**
 * Localized name of a subtitle language, or null when the code cannot be resolved.
 *
 * `Locale.forLanguageTag("rus")` does not resolve to a known locale, so its display name comes
 * back untranslated. Resolving the three-letter code against the available locales first is what
 * makes "rus" read as "Русский" rather than "Russian".
 */
internal fun subtitleLanguageDisplayName(language: String, displayLocale: Locale): String? {
    if (language.isBlank()) return null
    val canonical = canonicalSubtitleLanguage(language)
    if (canonical.isEmpty()) return null
    val locale = iso3Locales[canonical] ?: Locale.forLanguageTag(canonical)
    val name = runCatching { locale.getDisplayLanguage(displayLocale) }.getOrNull().orEmpty()
    return name
        .takeIf { it.isNotBlank() && !it.equals(canonical, ignoreCase = true) }
        ?.replaceFirstChar { it.titlecase(displayLocale) }
}

private val iso3Locales: Map<String, Locale> by lazy {
    Locale.getAvailableLocales()
        .filter { it.language.isNotEmpty() }
        .mapNotNull { locale ->
            runCatching { locale.isO3Language }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { iso3 -> iso3 to Locale.forLanguageTag(locale.language) }
        }
        .toMap()
}
