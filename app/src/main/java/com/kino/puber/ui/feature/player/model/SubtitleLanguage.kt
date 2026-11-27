package com.kino.puber.ui.feature.player.model

import java.util.Locale

/** KinoPub's language code for machine-generated subtitles. */
internal const val AI_GENERATED_SUBTITLE_LANGUAGE = "ai"

private const val UNDETERMINED_LANGUAGE = "und"

/**
 * [language] unchanged, or null when it names no language: blank, or the ISO 639-2 "und"
 * (undetermined) that Media3 reports for a track with no language tag, with or without a subtag.
 */
internal fun knownLanguageOrNull(language: String?): String? {
    val primary = language?.trim()?.lowercase(Locale.ROOT)?.substringBefore('-')?.substringBefore('_')
    return language.takeUnless { primary.isNullOrEmpty() || primary == UNDETERMINED_LANGUAGE }
}

/**
 * The language Media3 should prefer for a subtitle row: [knownLanguageOrNull], or "" when the row
 * names no language. "" prefers no language, so no track is picked by language match; Media3's
 * default or forced track selection may still apply.
 */
internal fun preferredTextLanguage(language: String?): String = knownLanguageOrNull(language).orEmpty()

internal fun sameSubtitleLanguage(first: String, second: String): Boolean {
    val firstKnown = knownLanguageOrNull(first) ?: return false
    val secondKnown = knownLanguageOrNull(second) ?: return false
    return canonicalSubtitleLanguage(firstKnown) == canonicalSubtitleLanguage(secondKnown)
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
        ?.takeIf { it.isNotBlank() && it != UNDETERMINED_LANGUAGE }
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
