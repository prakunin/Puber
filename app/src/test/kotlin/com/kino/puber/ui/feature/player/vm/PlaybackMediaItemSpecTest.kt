package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.domain.interactor.player.StreamCandidate
import com.kino.puber.domain.interactor.player.StreamType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class PlaybackMediaItemSpecTest {

    private val factory = PlaybackMediaItemFactory()

    private val links = listOf(
        SubtitleLink(lang = "rus", url = "https://api.test/subtitles/rus.srt?t=1", forced = true),
        SubtitleLink(lang = "eng", url = "https://api.test/subtitles/eng.vtt", forced = null),
        SubtitleLink(lang = "ukr", url = "https://api.test/subtitles/ukr.srt", embed = true),
    )

    private fun expectedSubtitles() = links.mapIndexed { index, link ->
        SubtitleSpec(
            uri = link.url,
            mimeType = subtitleMimeType(link.url),
            language = link.lang,
            selectionFlags = if (index == 0) C.SELECTION_FLAG_FORCED else 0,
            id = link.url.stableSubtitleKey(),
            label = link.url.stableSubtitleKey(),
        )
    }

    @Test
    fun hls_attachesEveryApiSubtitle_withStableKeyAsIdAndLabel() {
        val spec = factory.spec(StreamCandidate("https://cdn/v.m3u8", StreamType.HLS), links)

        assertEquals("https://cdn/v.m3u8", spec.uri)
        assertEquals(MimeTypes.APPLICATION_M3U8, spec.mimeType)
        assertEquals(expectedSubtitles(), spec.subtitles)
        assertEquals(listOf("rus.srt", "eng.vtt", "ukr.srt"), spec.subtitles.map { it.id })
        assertEquals(spec.subtitles.map { it.id }, spec.subtitles.map { it.label })
        assertEquals(listOf("rus", "eng", "ukr"), spec.subtitles.map { it.language })
    }

    @Test
    fun progressive_keepsAllApiSubtitles_includingEmbedTrue_withoutM3u8Mime() {
        val spec = factory.spec(StreamCandidate("https://cdn/v.mp4", StreamType.PROGRESSIVE), links)

        assertNull(spec.mimeType)
        assertEquals(expectedSubtitles(), spec.subtitles)
    }

    @Test
    fun forcedFalse_hasNoFlags() {
        val spec = factory.spec(
            StreamCandidate("https://cdn/v.mp4", StreamType.PROGRESSIVE),
            listOf(SubtitleLink(lang = "rus", url = "https://api.test/subtitles/a.srt", forced = false)),
        )

        assertEquals(0, spec.subtitles.single().selectionFlags)
    }

    @Test
    fun blankUrlLinks_areNotAttached() {
        val stream = StreamCandidate("https://cdn/v.m3u8", StreamType.HLS)
        val blank = listOf(SubtitleLink(lang = "", url = ""), SubtitleLink(lang = "rus", url = "  "))

        assertEquals(emptyList<SubtitleSpec>(), factory.spec(stream, blank).subtitles)
        assertEquals(emptyList<SubtitleLink>(), blank.sideLoadable())
        assertEquals(links, (links + blank).sideLoadable())
    }

    @Test
    fun emptyOrNullSubtitles_giveNoSpecs() {
        val stream = StreamCandidate("https://cdn/v.m3u8", StreamType.HLS)

        assertEquals(emptyList<SubtitleSpec>(), factory.spec(stream, null).subtitles)
        assertEquals(emptyList<SubtitleSpec>(), factory.spec(stream, emptyList()).subtitles)
    }

    @Test
    fun subtitleMimeType_byExtension() {
        assertEquals(MimeTypes.TEXT_VTT, subtitleMimeType("https://x/a.vtt?t=1#f"))
        assertEquals(MimeTypes.TEXT_VTT, subtitleMimeType("https://x/a.webvtt"))
        assertEquals(MimeTypes.TEXT_SSA, subtitleMimeType("https://x/a.ass?t=1"))
        assertEquals(MimeTypes.TEXT_SSA, subtitleMimeType("https://x/a.SSA"))
        assertEquals(MimeTypes.APPLICATION_TTML, subtitleMimeType("https://x/a.ttml#f"))
        assertEquals(MimeTypes.APPLICATION_TTML, subtitleMimeType("https://x/a.xml"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitleMimeType("https://x/a.srt?t=1"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitleMimeType("https://x/a.unknown"))
    }
}
