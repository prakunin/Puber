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
}
