package com.kino.puber.ui.feature.player.vm

import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun tracksUpdated_doesNotRestoreAmbiguousSubtitleLanguage_whenUrlIsMissing() {
        every { interactor.getPreferredSubtitleLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleUrl(42) } returns null
        val vm = startedVM()

        val tracks = listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus"))
        callbackSlot.captured.onTracksUpdated(tracks, 0)

        verify(exactly = 0) { playbackController.selectSubtitle(any()) }
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
