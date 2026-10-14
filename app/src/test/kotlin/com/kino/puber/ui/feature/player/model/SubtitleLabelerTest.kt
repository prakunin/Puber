package com.kino.puber.ui.feature.player.model

import com.kino.puber.R
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.util.FakeResourceProvider
import java.io.File
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SubtitleLabelerTest {

    private fun labelerFor(locale: Locale) = SubtitleLabeler(
        displayLocale = locale,
        aiGeneratedLabel = "Созданные ИИ",
        forcedQualifier = "частичные",
        variantLabel = { label, ordinal -> "$label · вариант $ordinal" },
        unknownLabel = { position -> "Субтитры $position" },
    )

    private val labeler = labelerFor(Locale.forLanguageTag("ru"))

    private fun labels(vararg subtitles: SubtitleLink, locale: Locale = Locale.forLanguageTag("ru")) =
        labelerFor(locale).apply(
            subtitles.map {
                SubtitleTrackUIState(label = "", language = it.lang, url = it.url, isForced = it.forced)
            },
        ).map { it.label }

    private fun sub(lang: String, forced: Boolean? = null) =
        SubtitleLink(lang = lang, url = "https://cdn/subtitles/$lang.srt", forced = forced)

    @Test
    fun threeLetterCodes_readAsLanguageNames() {
        assertEquals(listOf("Русский", "Английский"), labels(sub("rus"), sub("eng")))
    }

    @Test
    fun bibliographicCodes_readAsLanguageNamesToo() {
        assertEquals(listOf("Немецкий", "Французский"), labels(sub("ger"), sub("fre")))
        assertEquals(listOf("German", "French"), labels(sub("ger"), sub("fre"), locale = Locale.ENGLISH))
    }

    @Test
    fun languageNames_followTheDisplayLocale() {
        assertEquals(listOf("Russian"), labels(sub("rus"), locale = Locale.ENGLISH))
    }

    @Test
    fun aiGeneratedSubtitles_getTheirOwnName() {
        assertEquals(listOf("Созданные ИИ"), labels(sub("ai")))
    }

    @Test
    fun forcedSubtitles_getAQualifier() {
        assertEquals(
            listOf("Русский", "Русский · частичные"),
            labels(sub("rus"), sub("rus", forced = true)),
        )
    }

    @Test
    fun rowsThatStillReadTheSame_getOrdinals() {
        assertEquals(
            listOf("Русский · вариант 1", "Английский", "Русский · вариант 2"),
            labels(sub("rus"), sub("eng"), sub("rus")),
        )
    }

    @Test
    fun unresolvableCodes_fallBackToTheCode_andMissingOnesToAPosition() {
        assertEquals(listOf("QQQ", "Субтитры 2"), labels(sub("qqq"), sub(" ")))
    }

    @Test
    fun composedLabel_usesTheSameSeparatorAsTheVariantString() {
        val ru = variantFormat("src/main/res/values/player.xml")
        val en = variantFormat("src/main/res/values-en/strings.xml")
        val forcedRows = listOf(
            SubtitleTrackUIState(label = "", language = "rus", url = "u1", isForced = true),
            SubtitleTrackUIState(label = "", language = "rus", url = "u2", isForced = true),
        )

        assertEquals(
            listOf("Русский · частичные · вариант 1", "Русский · частичные · вариант 2"),
            SubtitleLabeler(
                displayLocale = Locale.forLanguageTag("ru"),
                aiGeneratedLabel = "Созданные ИИ",
                forcedQualifier = "частичные",
                variantLabel = { label, ordinal -> String.format(Locale.ROOT, ru, label, ordinal) },
                unknownLabel = { position -> "Субтитры $position" },
            ).apply(forcedRows).map { it.label },
        )
        assertEquals(
            listOf("Russian · forced · variant 1", "Russian · forced · variant 2"),
            SubtitleLabeler(
                displayLocale = Locale.ENGLISH,
                aiGeneratedLabel = "AI-generated",
                forcedQualifier = "forced",
                variantLabel = { label, ordinal -> String.format(Locale.ROOT, en, label, ordinal) },
                unknownLabel = { position -> "Subtitles $position" },
            ).apply(forcedRows).map { it.label },
        )
    }

    @Test
    fun ordinals_countFinalLabels_notRawLanguages() {
        assertEquals(
            listOf("Русский · вариант 1", "Русский · вариант 2", "Русский · частичные"),
            labels(sub("rus"), sub("ru"), sub("rus", forced = true)),
        )
    }

    @Test
    fun subtitleLabeler_takesStringsAndLocaleFromResources() {
        val resources = FakeResourceProvider().apply { locale = Locale.ENGLISH }

        val labels = subtitleLabeler(resources).apply(
            listOf(
                SubtitleTrackUIState(label = "", language = "ai", url = "u1"),
                SubtitleTrackUIState(label = "", language = "rus", url = "u2", isForced = true),
            ),
        ).map { it.label }

        assertEquals(
            listOf(
                resources.getString(R.string.player_subtitle_ai_generated),
                "Russian · ${resources.getString(R.string.player_subtitle_forced)}",
            ),
            labels,
        )
    }

    @Test
    fun apply_namesRowsByLocalizedLanguage_forTwoAndThreeLetterCodes() {
        val labels = labeler.apply(
            listOf(track("rus"), track("en"), track("spa"), track("uk")),
        ).map { it.label }

        assertEquals(listOf("Русский", "Английский", "Испанский", "Украинский"), labels)
    }

    @Test
    fun apply_marksPartialTrack() {
        val labels = labeler.apply(listOf(track("rus"), track("rus", forced = true)))
            .map { it.label }

        assertEquals(listOf("Русский", "Русский · частичные"), labels)
    }

    @Test
    fun apply_namesAiGeneratedTrack_whenApiUsesAiLanguageCode() {
        val labels = labeler.apply(listOf(track("ai", descriptive = "AI #02")))
            .map { it.label }

        assertEquals(listOf("Созданные ИИ"), labels)
    }

    @Test
    fun apply_neverShowsSubtitleFileNames() {
        val labels = labeler.apply(
            listOf(
                track("spa", descriptive = "29725.srt"),
                track("spa", descriptive = "29727.srt"),
            ),
        ).map { it.label }

        assertEquals(listOf("Испанский · вариант 1", "Испанский · вариант 2"), labels)
    }

    @Test
    fun apply_neverShowsCdnChannelNumbering() {
        val labels = labeler.apply(
            listOf(
                track("rus", descriptive = "RUS #03"),
                track("rus", descriptive = "RUS #05"),
            ),
        ).map { it.label }

        assertEquals(listOf("Русский · вариант 1", "Русский · вариант 2"), labels)
    }

    @Test
    fun apply_usesManifestLabels_whenTheyReadAsNamesAndSeparateTheRows() {
        val labels = labeler.apply(
            listOf(
                track("rus", descriptive = "Русские полные"),
                track("rus", descriptive = "Русские SDH"),
            ),
        ).map { it.label }

        assertEquals(listOf("Русские полные", "Русские SDH"), labels)
    }

    @Test
    fun apply_keepsPartialMarker_whenAManifestLabelSeparatesTheRows() {
        val labels = labeler.apply(
            listOf(
                track("rus", descriptive = "Русские полные", forced = true),
                track("rus", descriptive = "Русские SDH", forced = true),
            ),
        ).map { it.label }

        assertEquals(
            listOf("Русские полные · частичные", "Русские SDH · частичные"),
            labels,
        )
    }

    @Test
    fun apply_doesNotQualifyRowsThatAlreadyReadDifferently() {
        val labels = labeler.apply(
            listOf(track("rus", descriptive = "RUS #03"), track("eng", descriptive = "ENG #01")),
        ).map { it.label }

        assertEquals(listOf("Русский", "Английский"), labels)
    }

    @Test
    fun apply_preservesUnknownLanguageInformation_beforePositionalFallback() {
        val labels = labeler.apply(
            listOf(
                track(""),
                track("", descriptive = "Комментарии режиссёра"),
                track("qqq"),
                track("xyz", descriptive = "ALT #04"),
            ),
        ).map { it.label }

        assertEquals(
            listOf("Субтитры 1", "Комментарии режиссёра", "QQQ", "ALT #04"),
            labels,
        )
    }

    private fun track(
        language: String,
        descriptive: String? = null,
        forced: Boolean = false,
    ) = SubtitleTrackUIState(
        label = "",
        language = language,
        url = "",
        isForced = forced,
        descriptiveLabel = descriptive,
    )

    private fun variantFormat(path: String): String {
        val text = File(path).readText()
        val match = Regex("""<string name="player_subtitle_variant_label">(.*?)</string>""").find(text)
        return requireNotNull(match) { "player_subtitle_variant_label missing in $path" }.groupValues[1]
    }
}
