package com.kino.puber.ui.feature.player.vm

import com.kino.puber.domain.interactor.player.StreamCandidate
import com.kino.puber.domain.interactor.player.StreamType
import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import com.kino.puber.ui.feature.player.model.isOff
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * PlayerVM: choosing an audio track, a subtitle, a subtitle size or a quality, and
 * restoring those choices on the next episode.
 *
 * No `runTest` — UnconfinedTestDispatcher makes all coroutines synchronous.
 * `runTest` adds `advanceUntilIdle()` at the end which spins PlayerVM's infinite
 * `startPositionUpdates()` loop forever → OOM.
 * Without `runTest`, the infinite loop stays suspended at its first `delay()` — harmless.
 */
internal class PlayerVMTrackSelectionTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    // region Bug 2: Audio track restore by language

    @Test
    fun tracksUpdated_restoresPreferredLang() {
        every { interactor.getPreferredAudioLang(42) } returns "rus"
        val vm = startedVM()

        callbackSlot.captured.onTracksUpdated(
            listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus")),
            selectedIndex = 0,
        )

        verify { playbackController.selectAudioTrack(1) }
        assertEquals(1, contentState(vm).selectedAudioTrackIndex)
    }

    @Test
    fun tracksUpdated_keepsDefault_whenNoSavedPreference() {
        val vm = startedVM()

        callbackSlot.captured.onTracksUpdated(
            listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus")),
            selectedIndex = 0,
        )

        verify(exactly = 0) { playbackController.selectAudioTrack(any()) }
        assertEquals(0, contentState(vm).selectedAudioTrackIndex)
    }

    @Test
    fun tracksUpdated_keepsDefault_whenSavedLangNotFound() {
        every { interactor.getPreferredAudioLang(42) } returns "deu"
        startedVM()

        callbackSlot.captured.onTracksUpdated(
            listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus")),
            selectedIndex = 0,
        )

        verify(exactly = 0) { playbackController.selectAudioTrack(any()) }
    }

    @Test
    fun tracksUpdated_restoresOnlyOnce_perEpisode() {
        every { interactor.getPreferredAudioLang(42) } returns "rus"
        startedVM()

        val tracks = listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus"))
        callbackSlot.captured.onTracksUpdated(tracks, 0)
        callbackSlot.captured.onTracksUpdated(tracks, 1)

        verify(exactly = 1) { playbackController.selectAudioTrack(1) }
    }

    @Test
    fun selectTrack_savesLangToPrefs() {
        startedVM().onAction(PlayerAction.SelectAudioTrack(1))
        verify { interactor.saveTrackPreferences(42, "rus", any(), any(), any()) }
    }

    // endregion

    // region Subtitle selection

    @Test
    fun selectSubtitle_updatesStateAndDelegates() {
        val vm = startedVM()
        vm.onAction(PlayerAction.SelectSubtitle(1))
        assertEquals(1, contentState(vm).selectedSubtitleIndex)
        verify { playbackController.selectSubtitle(testSubtitleTracks[1]) }
    }

    @Test
    fun selectSubtitleOff_disablesSubtitleTrack() {
        val vm = startedVM()
        vm.onAction(PlayerAction.SelectSubtitle(0))
        assertEquals(0, contentState(vm).selectedSubtitleIndex)
        verify { playbackController.selectSubtitle(testSubtitleTracks[0]) }
    }

    @Test
    fun tracksUpdated_restoresPreferredSubtitleByUrl_beforeLanguage() {
        every { interactor.getPreferredSubtitleLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleUrl(42) } returns "https://test/subtitles/rus-forced.vtt"
        val vm = startedVM()

        val tracks = listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus"))
        callbackSlot.captured.onTracksUpdated(tracks, 0)

        verify { playbackController.selectSubtitle(testSubtitleTracks[2]) }
        assertEquals(2, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun tracksUpdated_restoresPreferredSubtitleByStableUrl_whenSignedUrlChanges() {
        every { interactor.getPreferredSubtitleLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleUrl(42) } returns
                "https://old-cdn.example/pd/expired-token/subtitles/rus-forced.vtt?e=1"
        val vm = startedVM()

        val tracks = listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus"))
        callbackSlot.captured.onTracksUpdated(tracks, 0)

        verify { playbackController.selectSubtitle(testSubtitleTracks[2]) }
        assertEquals(2, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun tracksUpdated_ambiguousSubtitleLanguage_withoutUrl_reappliesOffRow() {
        every { interactor.getPreferredSubtitleLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleUrl(42) } returns null
        val vm = startedVM()

        val tracks = listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus"))
        callbackSlot.captured.onTracksUpdated(tracks, 0)

        verify(exactly = 1) { playbackController.selectSubtitle(testSubtitleTracks[0]) }
        verify(exactly = 0) { interactor.saveTrackPreferences(any(), any(), any(), any(), any()) }
        assertEquals(0, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun tracksUpdated_reappliesOffRow_whenSavedSubtitleDoesNotResolve() {
        every { interactor.getPreferredSubtitleLang(42) } returns null
        every { interactor.getPreferredSubtitleUrl(42) } returns "https://test/subtitles/gone.vtt"
        val vm = startedVM()

        val tracks = listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus"))
        callbackSlot.captured.onTracksUpdated(tracks, 0)

        verify(exactly = 1) { playbackController.selectSubtitle(testSubtitleTracks[0]) }
        verify(exactly = 0) { playbackController.selectSubtitle(testSubtitleTracks[1]) }
        verify(exactly = 0) { playbackController.selectSubtitle(testSubtitleTracks[2]) }
        verify(exactly = 0) { interactor.saveTrackPreferences(any(), any(), any(), any(), any()) }
        assertEquals(0, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun tracksUpdated_nextMediaWithUnresolvedChoice_sendsOffRow() {
        val vm = startedVM()
        vm.onAction(PlayerAction.SelectSubtitle(2))
        every { interactor.getPreferredSubtitleLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleUrl(42) } returns null

        vm.onAction(PlayerAction.SelectEpisode(1, 2))
        val tracks = listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus"))
        callbackSlot.captured.onTracksUpdated(tracks, 0)

        verifyOrder {
            playbackController.selectSubtitle(testSubtitleTracks[2])
            playbackController.selectSubtitle(testSubtitleTracks[0])
        }
        assertEquals(0, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun tracksUpdated_restoreDoesNotRewritePreferencesFromIntermediateState() {
        every { interactor.getPreferredAudioLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleUrl(42) } returns "https://test/subtitles/rus-forced.vtt"
        val vm = startedVM()

        val tracks = listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus"))
        callbackSlot.captured.onTracksUpdated(tracks, 0)

        verify { playbackController.selectAudioTrack(1) }
        verify { playbackController.selectSubtitle(testSubtitleTracks[2]) }
        verify(exactly = 0) { interactor.saveTrackPreferences(any(), any(), any(), any(), any()) }
        assertEquals(1, contentState(vm).selectedAudioTrackIndex)
        assertEquals(2, contentState(vm).selectedSubtitleIndex)
    }

    // endregion

    // region Subtitle rows reported by the player (HLS merge)

    private val audioReport = listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus"))

    private fun sideLoad(language: String, url: String, child: Int) = SubtitleTrackUIState(
        label = "",
        language = language,
        url = "",
        playerTrackId = url.stableSubtitleKey(),
        playerTrackGroupId = "$child:0",
        playerTrackIndex = 0,
        fromManifest = false,
    )

    private fun manifest(language: String, id: String, uri: String? = null) = SubtitleTrackUIState(
        label = "",
        language = language,
        url = "",
        playerTrackId = id,
        playerTrackGroupId = "0:$id",
        playerTrackIndex = 0,
        playerTrackUri = uri,
        fromManifest = true,
    )

    private fun hlsRowsForFixture() = listOf(
        sideLoad("rus", testSubtitleTracks[1].url, 1),
        sideLoad("rus", testSubtitleTracks[2].url, 2),
    )

    private fun report(subtitles: List<SubtitleTrackUIState>?, audio: List<AudioTrackUIState> = audioReport) {
        callbackSlot.captured.onTracksUpdated(audio, 0, subtitles)
    }

    private fun rowIndex(vm: PlayerVM, predicate: (SubtitleTrackUIState) -> Boolean) =
        contentState(vm).subtitleTracks.indexOfFirst(predicate)

    @Test
    fun tracksUpdated_hlsApiOnlyLanguage_appearsInRowsAndIsSelectable() {
        val vm = startedVM()

        report(hlsRowsForFixture() + manifest("en", "subs:English"))

        val rows = contentState(vm).subtitleTracks
        assertTrue(rows.any { it.url == testSubtitleTracks[1].url })
        assertTrue(rows.any { it.language == "en" })
        assertTrue(rows.none { it.label.contains("subs:") || it.label.contains(".vtt") })

        vm.onAction(PlayerAction.SelectSubtitle(rowIndex(vm) { it.playerTrackId == "rus.vtt" }))

        verify {
            playbackController.selectSubtitle(
                match { it.url == testSubtitleTracks[1].url && it.playerTrackId == "rus.vtt" },
            )
        }
    }

    @Test
    fun selectSubtitle_manifestOnlyRow_isDelegatedNotTreatedAsOff() {
        val vm = startedVM()
        report(hlsRowsForFixture() + manifest("en", "subs:English"))
        val enIndex = rowIndex(vm) { it.language == "en" }

        vm.onAction(PlayerAction.SelectSubtitle(enIndex))

        verify { playbackController.selectSubtitle(match { it.language == "en" && !it.isOff }) }
        assertEquals(enIndex, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun tracksUpdated_progressive_nullSubtitleTracks_keepsApiRows() {
        val vm = startedVM()

        report(null)

        assertEquals(testSubtitleTracks, contentState(vm).subtitleTracks)
    }

    @Test
    fun tracksUpdated_keepsSelectedRow_whenSameMergedRowStillExists() {
        val vm = startedVM()
        val rows = hlsRowsForFixture() + manifest("en", "subs:English")
        report(rows)
        vm.onAction(PlayerAction.SelectSubtitle(rowIndex(vm) { it.playerTrackId == "rus.vtt" }))
        val selected = contentState(vm).subtitleTracks[contentState(vm).selectedSubtitleIndex]

        report(rows)

        val after = contentState(vm)
        assertEquals(selected, after.subtitleTracks[after.selectedSubtitleIndex])
        assertEquals("rus.vtt", after.subtitleTracks[after.selectedSubtitleIndex].playerTrackId)
    }

    @Test
    fun tracksUpdated_selectedRowReordered_followsRowToNewIndex() {
        val vm = startedVM()
        report(hlsRowsForFixture() + manifest("en", "subs:English"))
        vm.onAction(PlayerAction.SelectSubtitle(rowIndex(vm) { it.playerTrackId == "rus.vtt" }))

        report(
            listOf(
                manifest("en", "subs:English"),
                sideLoad("rus", testSubtitleTracks[1].url, 2),
                sideLoad("rus", testSubtitleTracks[2].url, 1),
            ),
        )

        val after = contentState(vm)
        val selected = after.subtitleTracks[after.selectedSubtitleIndex]
        assertEquals(testSubtitleTracks[1].url, selected.url)
        assertEquals("2:0", selected.playerTrackGroupId)
        verify(exactly = 1) {
            playbackController.selectSubtitle(
                match { it.url == testSubtitleTracks[1].url && it.playerTrackGroupId == "2:0" },
            )
        }
    }

    @Test
    fun tracksUpdated_selectedRowDisappears_goesOffAndDisablesText() {
        val vm = startedVM()
        report(hlsRowsForFixture() + manifest("en", "subs:English"))
        vm.onAction(PlayerAction.SelectSubtitle(rowIndex(vm) { it.playerTrackId == "rus.vtt" }))
        clearMocks(playbackController, answers = false)

        // Every Russian row is gone, so no other row of that language can stand in for the pick.
        report(listOf(manifest("en", "subs:English")))

        assertEquals(0, contentState(vm).selectedSubtitleIndex)
        verify(exactly = 1) { playbackController.selectSubtitle(match { it.isOff }) }
    }

    @Test
    fun tracksUpdated_textlessReportAfterQualitySwitch_keepsRowsAndPick() {
        val vm = startedVM()
        val rows = hlsRowsForFixture() + manifest("en", "subs:English")
        report(rows)
        vm.onAction(PlayerAction.SelectSubtitle(rowIndex(vm) { it.playerTrackId == "rus.vtt" }))
        val pickedBefore = contentState(vm).subtitleTracks[contentState(vm).selectedSubtitleIndex]
        val rowsBefore = contentState(vm).subtitleTracks
        clearMocks(playbackController, answers = false)

        // After switchStream the player briefly reports audio groups and no text groups.
        report(emptyList())

        assertEquals(rowsBefore, contentState(vm).subtitleTracks)
        assertEquals(pickedBefore, contentState(vm).subtitleTracks[contentState(vm).selectedSubtitleIndex])
        verify(exactly = 0) { playbackController.selectSubtitle(match { it.isOff }) }

        report(rows)

        val after = contentState(vm)
        assertEquals("rus.vtt", after.subtitleTracks[after.selectedSubtitleIndex].playerTrackId)
        verify(exactly = 0) { playbackController.selectSubtitle(match { it.isOff }) }
        verify(exactly = 0) { playbackController.selectSubtitle(any()) }
    }

    @Test
    fun tracksUpdated_identicalRepeatedReports_sendSingleReSelect() {
        val vm = startedVM()
        val rows = hlsRowsForFixture() + manifest("en", "subs:English")

        report(rows)
        val stateBefore = vm.testStateValue
        report(rows)

        verify(exactly = 1) { playbackController.selectSubtitle(any()) }
        assertSame(stateBefore, vm.testStateValue)
    }

    @Test
    fun tracksUpdated_restoresSavedUrl_onApiLinkedManifestRow() {
        every { interactor.getPreferredSubtitleLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleUrl(42) } returns testSubtitleTracks[1].url
        val vm = startedVM()

        report(listOf(manifest("rus", "subs:Russian", uri = "https://cdn.test/hls/subtitles/rus.vtt/index.m3u8")))

        verify {
            playbackController.selectSubtitle(match { it.url == testSubtitleTracks[1].url && it.fromManifest })
        }
        val state = contentState(vm)
        assertEquals(testSubtitleTracks[1].url, state.subtitleTracks[state.selectedSubtitleIndex].url)
    }

    @Test
    fun tracksUpdated_mergedRows_restoresPreferredSubtitleByUrl_beforeLanguage() {
        every { interactor.getPreferredSubtitleLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleUrl(42) } returns "https://test/subtitles/rus-forced.vtt"
        val vm = startedVM()

        report(hlsRowsForFixture())

        verify { playbackController.selectSubtitle(match { it.url == testSubtitleTracks[2].url }) }
        assertEquals(2, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun tracksUpdated_mergedRows_restoresPreferredSubtitleByStableUrl_whenSignedUrlChanges() {
        every { interactor.getPreferredSubtitleLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleUrl(42) } returns
                "https://old-cdn.example/pd/expired-token/subtitles/rus-forced.vtt?e=1"
        val vm = startedVM()

        report(hlsRowsForFixture())

        verify { playbackController.selectSubtitle(match { it.url == testSubtitleTracks[2].url }) }
        assertEquals(2, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun tracksUpdated_mergedRows_ambiguousSubtitleLanguage_withoutUrl_reappliesOffRow() {
        every { interactor.getPreferredSubtitleLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleUrl(42) } returns null
        val vm = startedVM()

        report(hlsRowsForFixture())

        verify(exactly = 1) { playbackController.selectSubtitle(match { it.isOff }) }
        verify(exactly = 0) { interactor.saveTrackPreferences(any(), any(), any(), any(), any()) }
        assertEquals(0, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun tracksUpdated_mergedRows_reappliesOffRow_whenSavedSubtitleDoesNotResolve() {
        every { interactor.getPreferredSubtitleLang(42) } returns null
        every { interactor.getPreferredSubtitleUrl(42) } returns "https://test/subtitles/gone.vtt"
        val vm = startedVM()

        report(hlsRowsForFixture())

        verify(exactly = 1) { playbackController.selectSubtitle(any()) }
        verify(exactly = 1) { playbackController.selectSubtitle(match { it.isOff }) }
        verify(exactly = 0) { interactor.saveTrackPreferences(any(), any(), any(), any(), any()) }
        assertEquals(0, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun tracksUpdated_mergedRows_restoreDoesNotRewritePreferencesFromIntermediateState() {
        every { interactor.getPreferredAudioLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleUrl(42) } returns "https://test/subtitles/rus-forced.vtt"
        val vm = startedVM()

        report(hlsRowsForFixture())

        verify { playbackController.selectAudioTrack(1) }
        verify { playbackController.selectSubtitle(match { it.url == testSubtitleTracks[2].url }) }
        verify(exactly = 0) { interactor.saveTrackPreferences(any(), any(), any(), any(), any()) }
        assertEquals(1, contentState(vm).selectedAudioTrackIndex)
        assertEquals(2, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun tracksUpdated_audioEmptyUpdate_keepsAudioRowsAndIndex() {
        val vm = startedVM()
        callbackSlot.captured.onTracksUpdated(audioReport, 1)

        report(hlsRowsForFixture(), audio = emptyList())

        assertEquals(testContentState.audioTracks, contentState(vm).audioTracks)
        assertEquals(1, contentState(vm).selectedAudioTrackIndex)
    }

    @Test
    fun tracksUpdated_textOnlyUpdate_updatesSubtitleRows() {
        val vm = startedVM()

        report(hlsRowsForFixture() + manifest("en", "subs:English"), audio = emptyList())

        assertTrue(contentState(vm).subtitleTracks.any { it.language == "en" })
        assertEquals(testContentState.audioTracks, contentState(vm).audioTracks)
    }

    @Test
    fun refreshStreamLinks_recomputesApiRows_andMergesWithPlayerRows() {
        val expired = StreamCandidate("https://cdn/expired.m3u8", StreamType.HLS)
        val refreshed = StreamCandidate("https://cdn/refreshed.m3u8", StreamType.HLS)
        every { interactor.selectStreamCandidates(any(), any()) } returnsMany listOf(
            listOf(expired),
            listOf(refreshed),
        )
        coEvery { interactor.refreshItemDetails(42) } returns testItem
        val newUrl = "https://new-cdn.test/subtitles/eng.srt"
        every { mapper.mapSubtitleTracks(any()) } returns listOf(
            testSubtitleTracks[0],
            SubtitleTrackUIState(label = "English", language = "eng", url = newUrl),
        )
        val vm = startedVM()
        callbackSlot.captured.onStreamFailure(
            message = "expired",
            recovery = PlaybackControl.StreamRecovery.REFRESH_LINKS,
            streamUrl = expired.url,
        )

        report(listOf(sideLoad("eng", newUrl, 1)))

        assertEquals(listOf(true, false), contentState(vm).subtitleTracks.map { it.isOff })
        assertEquals(newUrl, contentState(vm).subtitleTracks[1].url)
    }

    // endregion

    // region Subtitle size

    @Test
    fun cycleSubtitleSize_cyclesThrough() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            subtitleSize = SubtitleSize.SMALL,
        )
        val vm = startedVM()

        vm.onAction(PlayerAction.CycleSubtitleSize)
        assertEquals(SubtitleSize.MEDIUM, contentState(vm).subtitleSize)

        vm.onAction(PlayerAction.CycleSubtitleSize)
        assertEquals(SubtitleSize.LARGE, contentState(vm).subtitleSize)

        vm.onAction(PlayerAction.CycleSubtitleSize)
        assertEquals(SubtitleSize.SMALL, contentState(vm).subtitleSize)

        verify(exactly = 3) { interactor.saveSubtitleSize(any()) }
    }

    // endregion

    // region Quality

    @Test
    fun selectQuality_switchesStreamUrl() {
        val vm = startedVM()
        vm.onAction(PlayerAction.SelectQuality(1))
        assertEquals(1, contentState(vm).selectedQualityIndex)
        verify { playbackController.switchStream(any(), any()) }
    }

    @Test
    fun selectQuality_doesNothing_whenSameIndex() {
        val vm = startedVM()
        vm.onAction(PlayerAction.SelectQuality(0))
        verify(exactly = 0) { playbackController.switchStream(any(), any()) }
    }

    // endregion
}
