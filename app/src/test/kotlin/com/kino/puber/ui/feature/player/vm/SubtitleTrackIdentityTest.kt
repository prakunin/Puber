package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.sameSubtitleLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SubtitleTrackIdentityTest {

    @Test
    fun stableSubtitleKey_excludesSignedQueryAndFragment() {
        assertEquals(
            "episode-1.vtt",
            "https://cdn.example/subtitles/episode-1.vtt?token=secret#captions".stableSubtitleKey(),
        )
    }

    @Test
    fun stableSubtitleKey_keepsDistinctSubtitlePaths() {
        assertEquals(
            "episode-1.vtt",
            "https://cdn.example/subtitles/episode-1.vtt?expires=1".stableSubtitleKey(),
        )
        assertEquals(
            "episode-1-forced.vtt",
            "https://cdn.example/subtitles/episode-1-forced.vtt?expires=1".stableSubtitleKey(),
        )
    }

    @Test
    fun stableSubtitleKey_handlesEmptyAndNonStandardUrls() {
        assertEquals("", "".stableSubtitleKey())
        assertEquals("captions.vtt", "captions.vtt?token=1#cue".stableSubtitleKey())
    }

    @Test
    fun withoutMergedSourcePrefix_stripsOneLeadingNumberColon() {
        assertEquals("abc.srt", "1:abc.srt".withoutMergedSourcePrefix())
        assertEquals("1:Russian", "0:1:Russian".withoutMergedSourcePrefix())
    }

    @Test
    fun withoutMergedSourcePrefix_keepsIdsWithoutPrefix() {
        assertEquals("subs:Russian", "subs:Russian".withoutMergedSourcePrefix())
        assertEquals("abc", "abc".withoutMergedSourcePrefix())
    }

    @Test
    fun withoutMergedSourcePrefix_doesNotStripEmbeddedColons() {
        assertEquals("a:1:b", "a:1:b".withoutMergedSourcePrefix())
    }

    @Test
    fun sameSubtitleIdentity_treatsFileAndIndexM3u8AsOne() {
        assertTrue(
            sameSubtitleIdentity("/1/92/2871463.srt", "https://cdn.test/hls/1/92/2871463.srt/index.m3u8"),
        )
    }

    @Test
    fun sameSubtitleLanguage_matchesRuAndRus_rejectsBlank() {
        assertTrue(sameSubtitleLanguage("ru", "rus"))
        assertFalse(sameSubtitleLanguage("ru", "en"))
        assertFalse(sameSubtitleLanguage("", ""))
    }

    @Test
    fun subtitleSourceId_whenMerged_stripsPrefixAndMarksChildZeroAsManifest() {
        assertEquals(
            SubtitleSourceId(trackId = "1:Russian", fromManifest = true),
            subtitleSourceId(formatId = "0:1:Russian", groupId = "0:subs", merged = true),
        )
        assertEquals(
            SubtitleSourceId(trackId = "k1.srt", fromManifest = false),
            subtitleSourceId(formatId = "1:k1.srt", groupId = "1:0", merged = true),
        )
    }

    @Test
    fun subtitleSourceId_whenNotMerged_keepsNumericGroupIdUntouched() {
        assertEquals(
            SubtitleSourceId(trackId = "1:Russian", fromManifest = true),
            subtitleSourceId(formatId = "1:Russian", groupId = "1:Russian", merged = false),
        )
        assertEquals(
            SubtitleSourceId(trackId = null, fromManifest = true),
            subtitleSourceId(formatId = null, groupId = "0", merged = false),
        )
    }
}
