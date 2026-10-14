package com.kino.puber.ui.feature.player.model

import com.kino.puber.data.api.models.SubtitleLink
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
}
