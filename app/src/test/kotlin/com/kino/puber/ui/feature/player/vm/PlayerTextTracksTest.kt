package com.kino.puber.ui.feature.player.vm

import android.net.Uri
import android.text.TextUtils
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist.Rendition
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PlayerTextTracksTest {

    /** `Format` normalises a language through `Util`, which calls the android-only `TextUtils`. */
    @BeforeEach
    fun stubTextUtils() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }
    }

    @AfterEach
    fun unstubTextUtils() {
        unmockkStatic(TextUtils::class)
    }

    private fun format(
        id: String?,
        language: String? = null,
        label: String? = null,
        flags: Int = 0,
    ): Format = Format.Builder()
        .setId(id)
        .setSampleMimeType(MimeTypes.TEXT_VTT)
        .setLanguage(language)
        .setLabel(label)
        .setSelectionFlags(flags)
        .build()

    private fun group(groupId: String, vararg formats: Format): Tracks.Group {
        val trackGroup = TrackGroup(groupId, *formats)
        return Tracks.Group(trackGroup, false, IntArray(formats.size) { C.FORMAT_HANDLED }, BooleanArray(formats.size))
    }

    private fun rendition(url: String, format: Format): Rendition {
        val uri = mockk<Uri>()
        every { uri.toString() } returns url
        return Rendition(uri, format, "subs", format.label.orEmpty(), "subs:${format.id}")
    }

    @Test
    fun merged_manifestGroupIsFromManifest_withPrefixStrippedId() {
        val rows = buildSubtitleRows(
            listOf(group("0:subs:Russian", format("0:subs:Russian", language = "ru"))),
            emptyList(),
            merged = true,
        )

        val row = rows.single()
        assertTrue(row.fromManifest)
        assertEquals("subs:Russian", row.playerTrackId)
        assertEquals("0:subs:Russian", row.playerTrackGroupId)
        assertEquals(0, row.playerTrackIndex)
        assertEquals("ru", row.language)
    }

    @Test
    fun merged_sideLoadedChildIsNotFromManifest_andIsNotMatchedToRenditions() {
        val rendition = rendition(
            "https://cdn.test/subtitles/rus.srt/index.m3u8",
            format("rus", language = "ru", label = "rus.srt"),
        )
        val rows = buildSubtitleRows(
            listOf(group("1:", format("1:rus.srt", language = "ru", label = "rus.srt"))),
            listOf(rendition),
            merged = true,
        )

        val row = rows.single()
        assertFalse(row.fromManifest)
        assertEquals("rus.srt", row.playerTrackId)
        assertNull(row.playerTrackUri)
    }

    @Test
    fun unmerged_idIsKeptWhole_evenWhenItStartsWithADigit() {
        val rows = buildSubtitleRows(
            listOf(group("1:Russian", format("1:Russian", language = "ru"))),
            emptyList(),
            merged = false,
        )

        assertEquals("1:Russian", rows.single().playerTrackId)
        assertTrue(rows.single().fromManifest)
    }

    @Test
    fun manifestRow_matchesRenditionById_andTakesItsUri() {
        val rendition = rendition(
            "https://cdn.test/subtitles/rus.srt/index.m3u8",
            format("subs:Russian", language = "ru", label = "Russian"),
        )
        val rows = buildSubtitleRows(
            listOf(group("0:subs:Russian", format("0:subs:Russian", language = "ru"))),
            listOf(rendition),
            merged = true,
        )

        assertEquals("https://cdn.test/subtitles/rus.srt/index.m3u8", rows.single().playerTrackUri)
    }

    @Test
    fun manifestRow_fallsBackToLabelAndLanguage_butNotWhenAmbiguous() {
        val first = rendition("https://cdn.test/a/index.m3u8", format("a", language = "ru", label = "Russian"))
        val second = rendition("https://cdn.test/b/index.m3u8", format("b", language = "ru", label = "Russian"))
        val playerGroup = group("0:x", format("0:x", language = "rus", label = "Russian"))

        val unique = buildSubtitleRows(listOf(playerGroup), listOf(first), merged = true).single()
        val ambiguous = buildSubtitleRows(listOf(playerGroup), listOf(first, second), merged = true).single()

        assertEquals("https://cdn.test/a/index.m3u8", unique.playerTrackUri)
        assertNull(ambiguous.playerTrackUri)
    }

    @Test
    fun forcedFlag_isOrOfPlayerAndRenditionFormats() {
        val renditions = listOf(
            rendition(
                "https://cdn.test/forced/index.m3u8",
                format("subs:Forced", language = "ru", flags = C.SELECTION_FLAG_FORCED),
            ),
            rendition("https://cdn.test/full/index.m3u8", format("subs:Full", language = "ru")),
        )
        val rows = buildSubtitleRows(
            listOf(
                group("0:subs:Forced", format("0:subs:Forced", language = "ru")),
                group("0:subs:Full", format("0:subs:Full", language = "ru")),
            ),
            renditions,
            merged = true,
        )

        assertEquals(listOf(true, false), rows.map { it.isForced })
    }

    @Test
    fun rowsKeepGroupOrderAndTrackIndexAcrossMultiTrackGroups() {
        val rows = buildSubtitleRows(
            listOf(group("g", format("a", language = "en"), format("b", language = "en"))),
            emptyList(),
            merged = false,
        )

        assertEquals(listOf(0, 1), rows.map { it.playerTrackIndex })
        assertEquals(listOf("a", "b"), rows.map { it.playerTrackId })
    }

    @Test
    fun toPlayerTextTracks_flattensWithGroupIndexAndForcedFlag() {
        val tracks = listOf(
            group("g0", format("a", language = "en", label = "English")),
            group("g1", format("b", language = "ru", flags = C.SELECTION_FLAG_FORCED)),
        ).toPlayerTextTracks()

        assertEquals(listOf("g0", "g1"), tracks.map { it.groupId })
        assertEquals(listOf(0, 1), tracks.map { it.groupIndex })
        assertEquals(listOf(false, true), tracks.map { it.isForced })
        assertEquals("English", tracks[0].formatLabel)
    }
}
