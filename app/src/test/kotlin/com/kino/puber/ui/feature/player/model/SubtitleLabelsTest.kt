package com.kino.puber.ui.feature.player.model

import com.kino.puber.data.api.models.SubtitleLink
import java.io.File
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SubtitleLabelsTest {

    private fun labels(vararg subtitles: SubtitleLink, locale: Locale = Locale.forLanguageTag("ru")) =
        subtitleLabels(
            subtitles = subtitles.toList(),
            displayLocale = locale,
            aiGeneratedLabel = "Созданные ИИ",
            forcedQualifier = "частичные",
            variantLabel = { label, ordinal -> "$label · вариант $ordinal" },
            unknownLabel = { position -> "Субтитры $position" },
        )

    private fun sub(lang: String, forced: Boolean? = null) =
        SubtitleLink(lang = lang, url = "https://cdn/subtitles/$lang.srt", forced = forced)

    @Test
    fun threeLetterCodes_readAsLanguageNames() {
        assertEquals(listOf("Русский", "Английский"), labels(sub("rus"), sub("eng")))
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

        assertEquals(
            listOf("Русский · частичные · вариант 1", "Русский · частичные · вариант 2"),
            subtitleLabels(
                subtitles = listOf(sub("rus", true), sub("rus", true)),
                displayLocale = Locale.forLanguageTag("ru"),
                aiGeneratedLabel = "Созданные ИИ",
                forcedQualifier = "частичные",
                variantLabel = { label, ordinal -> String.format(ru, label, ordinal) },
                unknownLabel = { position -> "Субтитры $position" },
            ),
        )
        assertEquals(
            listOf("Russian · forced · variant 1", "Russian · forced · variant 2"),
            subtitleLabels(
                subtitles = listOf(sub("rus", true), sub("rus", true)),
                displayLocale = Locale.ENGLISH,
                aiGeneratedLabel = "AI-generated",
                forcedQualifier = "forced",
                variantLabel = { label, ordinal -> String.format(en, label, ordinal) },
                unknownLabel = { position -> "Subtitles $position" },
            ),
        )
    }

    @Test
    fun ordinals_countFinalLabels_notRawLanguages() {
        assertEquals(
            listOf("Русский · вариант 1", "Русский · вариант 2", "Русский · частичные"),
            labels(sub("rus"), sub("ru"), sub("rus", forced = true)),
        )
    }

    private fun variantFormat(path: String): String {
        val text = File(path).readText()
        val match = Regex("""<string name="player_subtitle_variant_label">(.*?)</string>""").find(text)
        return requireNotNull(match) { "player_subtitle_variant_label missing in $path" }.groupValues[1]
    }
}
