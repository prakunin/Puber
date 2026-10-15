package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AudioTrackPreferenceResolverTest {

    private val resolver = AudioTrackPreferenceResolver()

    private val off = SubtitleTrackUIState(label = "Off", language = "", url = "")

    private fun sideLoad(language: String, key: String, child: Int, url: String = "") = SubtitleTrackUIState(
        label = language,
        language = language,
        url = url,
        playerTrackId = key,
        playerTrackGroupId = "$child:0",
        playerTrackIndex = 0,
    )

    @Test
    fun findSameSubtitleTrackIndex_offStaysOff() {
        val tracks = listOf(off, sideLoad("rus", "a.srt", 1))

        assertEquals(0, resolver.findSameSubtitleTrackIndex(tracks, off))
    }

    @Test
    fun findSameSubtitleTrackIndex_bySourceIdentity() {
        val apiRow = SubtitleTrackUIState(label = "Russian", language = "rus", url = "https://api.test/subtitles/a.srt")
        val tracks = listOf(
            off,
            sideLoad("rus", "b.srt", 1, url = "https://api.test/subtitles/b.srt"),
            sideLoad("rus", "a.srt", 2, url = "https://api.test/subtitles/a.srt"),
        )

        assertEquals(2, resolver.findSameSubtitleTrackIndex(tracks, apiRow))
    }

    @Test
    fun findSameSubtitleTrackIndex_byPlayerTrackId() {
        val previous = sideLoad("rus", "b.srt", 1)
        val tracks = listOf(off, sideLoad("rus", "a.srt", 1), sideLoad("rus", "b.srt", 2))

        assertEquals(2, resolver.findSameSubtitleTrackIndex(tracks, previous))
    }

    @Test
    fun findSameSubtitleTrackIndex_byFullGroupIdAndIndex() {
        val previous = SubtitleTrackUIState(
            label = "",
            language = "rus",
            url = "",
            playerTrackGroupId = "2:0",
            playerTrackIndex = 0,
        )
        val tracks = listOf(
            off,
            sideLoad("rus", "a.srt", 1),
            sideLoad("rus", "b.srt", 2),
        )

        assertEquals(2, resolver.findSameSubtitleTrackIndex(tracks, previous))
    }

    @Test
    fun findSameSubtitleTrackIndex_groupAndIndexNowHoldAnotherLanguage_givesOff() {
        val previous = sideLoad("rus", "a.srt", 2)
        // The refresh dropped the Russian side-load; child 2 is now an English one.
        val tracks = listOf(off, sideLoad("en", "c.srt", 1), sideLoad("en", "d.srt", 2))

        assertEquals(0, resolver.findSameSubtitleTrackIndex(tracks, previous))
    }

    @Test
    fun findSameSubtitleTrackIndex_groupAndIndexNowHoldDifferentForcedFlag_doesNotMatch() {
        val previous = sideLoad("rus", "a.srt", 2).copy(isForced = false)
        val tracks = listOf(off, sideLoad("rus", "z.srt", 2).copy(isForced = true))

        assertEquals(0, resolver.findSameSubtitleTrackIndex(tracks, previous))
    }

    @Test
    fun findSameSubtitleTrackIndex_byUnambiguousCanonicalLanguage() {
        val previous = SubtitleTrackUIState(label = "Russian", language = "rus", url = "")
        val tracks = listOf(off, sideLoad("ru", "a.srt", 1), sideLoad("en", "b.srt", 2))

        assertEquals(1, resolver.findSameSubtitleTrackIndex(tracks, previous))
    }

    @Test
    fun findSameSubtitleTrackIndex_ambiguousLanguage_givesOff() {
        val previous = SubtitleTrackUIState(label = "Russian", language = "rus", url = "")
        val tracks = listOf(off, sideLoad("ru", "a.srt", 1), sideLoad("ru", "b.srt", 2))

        assertEquals(0, resolver.findSameSubtitleTrackIndex(tracks, previous))
    }

    @Test
    fun findSameSubtitleTrackIndex_doesNotMatchSameGroupIdWithDifferentIndex() {
        val previous = SubtitleTrackUIState(
            label = "",
            language = "",
            url = "",
            playerTrackGroupId = "1:0",
            playerTrackIndex = 1,
        )
        val tracks = listOf(off, sideLoad("rus", "a.srt", 1))

        assertEquals(0, resolver.findSameSubtitleTrackIndex(tracks, previous))
    }

    @Test
    fun findSubtitleTrackIndex_matchesRuAgainstRus_viaSameSubtitleLanguage() {
        val tracks = listOf(off, sideLoad("ru", "a.srt", 1))

        assertEquals(1, resolver.findSubtitleTrackIndex(tracks, preferredLang = "rus", preferredUrl = null))
    }

    @Test
    fun findSubtitleTrackIndex_stillAmbiguous_returnsNoMatch() {
        val tracks = listOf(off, sideLoad("ru", "a.srt", 1), sideLoad("rus", "b.srt", 2))

        assertEquals(-1, resolver.findSubtitleTrackIndex(tracks, preferredLang = "rus", preferredUrl = null))
    }

    private fun apiRow(language: String, url: String, forced: Boolean? = null) =
        SubtitleTrackUIState(label = language, language = language, url = url, isForced = forced)

    private fun languageLess(playerTrackId: String? = null, url: String = "") = SubtitleTrackUIState(
        label = "",
        language = "",
        url = url,
        playerTrackId = playerTrackId,
        playerTrackGroupId = playerTrackId?.let { "1:0" },
        playerTrackIndex = 0,
    )

    private fun find(
        tracks: List<SubtitleTrackUIState>,
        lang: String?,
        url: String?,
        forced: Boolean? = null,
        label: String? = null,
    ) = resolver.findSubtitleTrackIndex(tracks, lang, url, forced, label)

    @Test
    fun findSubtitleTrackIndex_matchesManifestRowByStoredGroupId() {
        val tracks = listOf(off, manifestRendition("eng", "English"), manifestRendition("eng", "English SDH"))

        assertEquals(2, find(tracks, "eng", "subtitle:English SDH"))
    }

    @Test
    fun findSubtitleTrackIndex_matchesPrefixedGroupIdAgainstStoredUnprefixedAndViceVersa() {
        val merged = listOf(off, manifestRendition("rus", "Rus"))
        val unmerged = listOf(off, manifestRendition("rus", "Rus", merged = false))

        assertEquals(1, find(merged, "rus", "subtitle:Rus"))
        assertEquals(1, find(unmerged, "rus", "subtitle:Rus"))
        assertEquals(1, find(merged, "rus", "0:subtitle:Rus"))
    }

    @Test
    fun findSubtitleTrackIndex_preservesLanguageLessTrackByIdentity() {
        val rows = listOf(
            languageLess(url = "https://test/unknown.vtt") to "https://test/unknown.vtt",
            languageLess(playerTrackId = "unknown") to "unknown",
            manifestRendition("", "Unknown") to "subtitle:Unknown",
        )

        rows.forEach { (row, identity) ->
            assertEquals(1, find(listOf(off, row), null, identity), identity)
            assertEquals(1, find(listOf(off, row), "", identity), identity)
        }
    }

    @Test
    fun findSubtitleTrackIndex_legacyPreference_resolvesToSameRowAsBefore() {
        val url = "https://api.test/subtitles/a.srt"
        val forcedEng = apiRow("eng", "https://api.test/subtitles/eng-forced.srt", forced = true)

        val both = listOf(off, apiRow("rus", url), apiRow("eng", "https://api.test/subtitles/b.srt"))
        val signed = "https://other.host/tok/subtitles/a.srt?e=1"

        assertEquals(1, find(both, "rus", url))
        assertEquals(1, find(listOf(off, apiRow("rus", url)), "rus", signed))
        assertEquals(1, find(listOf(off, sideLoad("rus", "a.srt", 1)), "rus", null))
        assertEquals(1, find(listOf(off, forcedEng), "rus", forcedEng.url))
    }

    @Test
    fun findSubtitleTrackIndex_legacyAmbiguousLanguage_nowResolvesToSingleManifestRow() {
        val single = listOf(off, sideLoad("rus", "a.srt", 1), manifestRendition("rus", "Rus"))
        val two = listOf(off, manifestRendition("rus", "Rus"), manifestRendition("rus", "Other"))

        assertEquals(2, find(single, "rus", null))
        assertEquals(-1, find(two, "rus", null))
    }

    @Test
    fun findSubtitleTrackIndex_rejectsForcedOnlyReplacementForFullPreference() {
        val tracks = listOf(off, manifestRendition("rus", "Forced", forced = true))

        assertEquals(-1, find(tracks, "rus", null, forced = false))
    }

    @Test
    fun findSubtitleTrackIndex_usesForcedFlagToChooseBetweenFullAndForcedSameLanguage() {
        val tracks = listOf(
            off,
            manifestRendition("rus", "Full", forced = false, label = null),
            manifestRendition("rus", "Forced", forced = true, label = null),
        )

        assertEquals(1, find(tracks, "rus", null, forced = false))
        assertEquals(2, find(tracks, "rus", null, forced = true))
    }

    private fun reusedEnglishRows() = listOf(
        off,
        sideLoad("eng", "reused", 1).copy(isForced = false, descriptiveLabel = "English SDH"),
        sideLoad("eng", "reused", 2).copy(isForced = false, descriptiveLabel = "English Commentary"),
    )

    @Test
    fun findSubtitleTrackIndex_usesMeaningfulLabelToResolveSameLanguageVariants() {
        assertEquals(2, find(reusedEnglishRows(), "eng", "reused", forced = false, label = "English Commentary"))
    }

    @Test
    fun findSubtitleTrackIndex_labelComparisonIsTrimmedAndCaseInsensitive() {
        assertEquals(2, find(reusedEnglishRows(), "eng", "reused", forced = false, label = "  english commentary "))
    }

    @Test
    fun findSubtitleTrackIndex_renamedLabel_stillRestoresSingleSameLanguageRow() {
        val tracks = listOf(off, manifestRendition("eng", "English Captions"))

        assertEquals(1, find(tracks, "eng", "subtitle:English", forced = false, label = "English"))
    }

    @Test
    fun findSubtitleTrackIndex_labelChoosesBetweenSameLanguageRowsWithoutIdentity() {
        val tracks = listOf(
            off,
            sideLoad("eng", "a", 1).copy(isForced = false, descriptiveLabel = "English SDH"),
            sideLoad("eng", "b", 2).copy(isForced = false, descriptiveLabel = "English Commentary"),
        )

        assertEquals(2, find(tracks, "eng", null, forced = false, label = "English Commentary"))
        assertEquals(-1, find(tracks, "eng", null, forced = false, label = "English Other"))
    }

    @Test
    fun findSubtitleTrackIndex_rowWithoutReadableLabelPassesLabelFilter() {
        val tracks = listOf(off, apiRow("rus", "https://api.test/subtitles/full.vtt"))

        assertEquals(1, find(tracks, "rus", "subtitle:Russian Full", forced = false, label = "Russian Full"))
    }

    @Test
    fun findSubtitleTrackIndex_rejectsReusedIdentityWithDifferentLanguage() {
        val tracks = listOf(off, sideLoad("spa", "reused-id", 1))

        assertEquals(-1, find(tracks, "eng", "reused-id"))
    }

    @Test
    fun findSubtitleTrackIndex_doesNotRestoreOffWhenLanguageLessIdentityDisappears() {
        val tracks = listOf(off, languageLess(playerTrackId = "other"))

        assertEquals(-1, find(tracks, null, "missing"))
        assertEquals(-1, find(tracks, "", "missing"))
    }

    @Test
    fun findSubtitleTrackIndex_emptyLanguageWithoutIdentity_meansOff() {
        val tracks = listOf(off, sideLoad("rus", "a.srt", 1))

        assertEquals(0, find(tracks, "", null))
        assertEquals(0, find(tracks, "", ""))
    }

    @Test
    fun findSubtitleTrackIndex_positionalSideLoadGroupIdNeverMatches() {
        val tracks = listOf(off, sideLoad("", "a.srt", 1))

        assertEquals(-1, find(tracks, null, "1:0"))
    }
}
