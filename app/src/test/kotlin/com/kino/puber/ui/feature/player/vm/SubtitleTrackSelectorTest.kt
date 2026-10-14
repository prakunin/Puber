package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class SubtitleTrackSelectorTest {

    private val selector = SubtitleTrackSelector()

    /**
     * The regression that made merged HLS playback unusable before: the picker row is
     * backed by a manifest rendition, but after merging it also carries the API url, whose
     * stable key is the id of the hidden side-loaded copy of the same subtitle.
     */
    @Test
    fun select_prefersManifestRendition_overSideLoadedCopyOfTheSameSubtitle() {
        val manifestRow = manifestTrack(
            groupId = "0:rus-rendition",
            trackIndex = 0,
            formatId = "rus-rendition",
            url = "https://api.test/subtitles/29725.srt",
        )
        val candidates = listOf(
            PlayerTextTrack("0:rus-rendition", 0, 0, formatId = "rus-rendition", language = "rus"),
            PlayerTextTrack("1:", 1, 0, formatId = "29725.srt", formatLabel = "29725.srt", language = "rus"),
        )

        assertEquals(candidates[0], selector.select(manifestRow, candidates))
    }

    @Test
    fun select_survivesTrackGroupsBeingReordered() {
        val row = manifestTrack(
            groupId = "0:rus-rendition",
            trackIndex = 0,
            formatId = "rus-rendition",
            url = "",
        )
        // A later tracks update exposes the same groups in a different order.
        val candidates = listOf(
            PlayerTextTrack("2:eng-rendition", 0, 0, formatId = "eng-rendition", language = "eng"),
            PlayerTextTrack("0:rus-rendition", 1, 0, formatId = "rus-rendition", language = "rus"),
        )

        assertEquals(candidates[1], selector.select(row, candidates))
    }

    @Test
    fun select_picksCorrectVariant_whenGroupIdsAreBlankAndLanguagesCollide() {
        val row = manifestTrack(groupId = "", trackIndex = 0, formatId = "forced-rus", url = "")
        val candidates = listOf(
            PlayerTextTrack("", 0, 0, formatId = "full-rus", language = "rus"),
            PlayerTextTrack("", 1, 0, formatId = "forced-rus", language = "rus"),
        )

        assertEquals(candidates[1], selector.select(row, candidates))
    }

    @Test
    fun select_neverPicksByPosition() {
        val row = SubtitleTrackUIState(
            label = "",
            language = "rus",
            url = "",
            playerTrackIndex = 0,
        )
        val candidates = listOf(
            PlayerTextTrack("", 0, 0, language = "rus"),
            PlayerTextTrack("", 1, 0, language = "rus"),
        )

        assertNull(selector.select(row, candidates))
    }

    @Test
    fun select_doesNotFallBackToCoordinates_whenNothingIdentifiesTheTrack() {
        val row = SubtitleTrackUIState(label = "rus", language = "", url = "")
        val candidates = listOf(
            PlayerTextTrack("", 0, 0, language = "rus"),
            PlayerTextTrack("", 1, 0, language = "rus"),
        )

        assertNull(selector.select(row, candidates))
    }

    @Test
    fun select_matchesByLanguage_onlyWhenUnambiguous() {
        val row = SubtitleTrackUIState(label = "ukr", language = "ukr", url = "")
        val candidates = listOf(
            PlayerTextTrack("", 0, 0, language = "rus"),
            PlayerTextTrack("", 1, 0, language = "uk"),
        )

        assertEquals(candidates[1], selector.select(row, candidates))
        assertNull(
            selector.select(
                SubtitleTrackUIState(label = "rus", language = "rus", url = ""),
                listOf(
                    PlayerTextTrack("", 0, 0, language = "rus"),
                    PlayerTextTrack("", 1, 0, language = "ru"),
                ),
            ),
        )
    }

    @Test
    fun select_rejectsReusedCoordinatesAndIds_withDifferentLanguage() {
        val row = manifestTrack(
            groupId = "reused-group",
            trackIndex = 0,
            formatId = "reused-format",
            url = "",
        ).copy(language = "en", isForced = false)
        val candidates = listOf(
            PlayerTextTrack(
                groupId = "reused-group",
                groupIndex = 0,
                trackIndex = 0,
                formatId = "reused-format",
                language = "es",
                isForced = false,
            ),
        )

        assertNull(selector.select(row, candidates))
    }

    @Test
    fun select_rejectsForcedTrack_forFullSubtitleRow() {
        val row = manifestTrack(
            groupId = "reused-group",
            trackIndex = 0,
            formatId = "reused-format",
            url = "",
        ).copy(isForced = false)
        val candidates = listOf(
            PlayerTextTrack(
                groupId = "reused-group",
                groupIndex = 0,
                trackIndex = 0,
                formatId = "reused-format",
                language = "rus",
                isForced = true,
            ),
        )

        assertNull(selector.select(row, candidates))
    }

    private fun sideLoads() = listOf(
        PlayerTextTrack("1:0", 0, 0, formatId = "1:k1.srt", formatLabel = "k1.srt", language = "rus"),
        PlayerTextTrack("2:0", 1, 0, formatId = "2:k2.srt", formatLabel = "k2.srt", language = "rus"),
        PlayerTextTrack("3:0", 2, 0, formatId = "3:k3.srt", formatLabel = "k3.srt", language = "rus"),
    )

    @Test
    fun select_picksEachSideLoadedTrackByStrippedFormatId_whenAllStrippedGroupIdsAreZero() {
        val candidates = sideLoads()

        assertEquals("0", "1:0".withoutMergedSourcePrefix())
        assertEquals("1:0".withoutMergedSourcePrefix(), "2:0".withoutMergedSourcePrefix())
        listOf("k1.srt", "k2.srt", "k3.srt").forEachIndexed { index, key ->
            val row = SubtitleTrackUIState(label = "", language = "rus", url = "", playerTrackId = key)

            assertEquals(candidates[index], selector.select(row, candidates))
        }
    }

    @Test
    fun select_prefersIdentityOverFullGroupId_whenChildOrderChanged() {
        val row = SubtitleTrackUIState(
            label = "",
            language = "rus",
            url = "",
            playerTrackId = "k2.srt",
            playerTrackGroupId = "1:0",
            playerTrackIndex = 0,
        )
        val candidates = sideLoads()

        assertEquals(candidates[1], selector.select(row, candidates))
    }

    @Test
    fun select_usesFullGroupIdAndIndex_whenIdentityIsAbsent() {
        val row = SubtitleTrackUIState(
            label = "",
            language = "rus",
            url = "",
            playerTrackGroupId = "2:0",
            playerTrackIndex = 0,
        )
        val candidates = listOf(
            PlayerTextTrack("1:0", 0, 0, language = "rus"),
            PlayerTextTrack("2:0", 1, 0, language = "rus"),
        )

        assertEquals(candidates[1], selector.select(row, candidates))
    }

    @Test
    fun select_matchesSideLoadByApiUrl_whenRowHasNoPlayerId() {
        val row = SubtitleTrackUIState(label = "", language = "", url = "https://api.test/subtitles/k2.srt?t=1")
        val candidates = sideLoads()

        assertEquals(candidates[1], selector.select(row, candidates))
    }

    @Test
    fun select_keepsUnmergedNumericGroupIdAsIdentity() {
        val row = SubtitleTrackUIState(label = "", language = "rus", url = "", playerTrackId = "1:Russian")
        val candidates = listOf(
            PlayerTextTrack("0", 0, 0, formatId = "1:Russian", language = "rus"),
            PlayerTextTrack("0", 1, 0, formatId = "1:Russian forced", language = "rus"),
        )

        assertEquals(candidates[0], selector.select(row, candidates))
    }

    private fun manifestTrack(
        groupId: String,
        trackIndex: Int,
        formatId: String?,
        url: String,
    ) = SubtitleTrackUIState(
        label = "rus",
        language = "rus",
        url = url,
        playerTrackGroupId = groupId,
        playerTrackId = formatId,
        playerTrackUri = "https://cdn.test/rendition.m3u8",
        playerTrackIndex = trackIndex,
    )
}
