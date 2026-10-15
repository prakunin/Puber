package com.kino.puber.ui.feature.player.vm

import com.kino.puber.domain.model.SubtitlePreference
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.clearMocks
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * PlayerVM: what is saved for the chosen subtitle and how it is restored, including after the
 * player is rebuilt. Same coroutine setup as [PlayerVMTrackSelectionTest]: no `runTest`.
 */
internal class PlayerVMSubtitlePreferenceTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    @Test
    fun pickLanguageLessManifestRow_savesIdentityNotOff() {
        val vm = startedVM()
        report(listOf(manifestRendition("", "Signs")))
        clearMocks(interactor, answers = false)

        vm.onAction(PlayerAction.SelectSubtitle(rowIndex(vm) { it.fromManifest }))

        verify(exactly = 1) {
            interactor.saveTrackPreferences(
                42, any(), any(), match { it.language == null && it.url == "subtitle:Signs" },
            )
        }
        verify(exactly = 0) {
            interactor.saveTrackPreferences(any(), any(), any(), match { it.language == null && it.url == null })
        }
    }

    @Test
    fun savedFullSubtitle_streamHasOnlyForcedRow_staysOff() {
        stubSavedSubtitle("rus", null, isForced = false)
        val vm = startedVM()

        report(listOf(manifestRendition("rus", "Russian Forced", forced = true)))

        assertEquals(0, contentState(vm).selectedSubtitleIndex)
        verify(exactly = 0) { playbackController.selectSubtitle(match { it.isForced == true }) }
    }

    @Test
    fun savedSubtitleRestoredBySubstitute_noPick_audioChangeKeepsStoredPreference() {
        stubSavedSubtitle("rus", "subtitle:Gone", isForced = false, descriptiveLabel = "Russian Old")
        val vm = startedVM()
        report(listOf(manifestRendition("rus", "Russian New")))
        assertEquals(1, contentState(vm).selectedSubtitleIndex)
        clearMocks(interactor, answers = false)

        vm.onAction(PlayerAction.SelectAudioTrack(1))

        verify(exactly = 1) {
            interactor.saveTrackPreferences(
                42, any(), any(), SubtitlePreference("rus", "subtitle:Gone", false, "Russian Old"),
            )
        }
    }

    @Test
    fun pickInFirstMedia_nextMediaSubstituteRestore_audioChangeKeepsStoredPreference() {
        val vm = startedVM()
        report(listOf(manifestRendition("rus", "Russian Old")))
        vm.onAction(PlayerAction.SelectSubtitle(rowIndex(vm) { it.fromManifest }))
        vm.onAction(PlayerAction.SelectEpisode(1, 2))
        stubSavedSubtitle("rus", "subtitle:Gone", isForced = false, descriptiveLabel = "Russian Old")
        report(listOf(manifestRendition("rus", "Russian New")))
        assertEquals(1, contentState(vm).selectedSubtitleIndex)
        clearMocks(interactor, answers = false)

        vm.onAction(PlayerAction.SelectAudioTrack(1))

        verify(exactly = 1) {
            interactor.saveTrackPreferences(
                42, any(), any(), SubtitlePreference("rus", "subtitle:Gone", false, "Russian Old"),
            )
        }
    }

    @Test
    fun pickThenRemapToSameLanguageRow_audioChangeSavesThePick() {
        val vm = startedVM()
        report(listOf(manifestRendition("eng", "English A")))
        vm.onAction(PlayerAction.SelectSubtitle(rowIndex(vm) { it.fromManifest }))
        report(listOf(manifestRendition("eng", "English B")))
        assertEquals(1, contentState(vm).selectedSubtitleIndex)
        clearMocks(interactor, answers = false)

        vm.onAction(PlayerAction.SelectAudioTrack(1))

        verify(exactly = 1) {
            interactor.saveTrackPreferences(
                42, any(), any(), SubtitlePreference("eng", "subtitle:English A", false, "English A"),
            )
        }
    }

    private fun unresolvedSavedSubtitleVM(): PlayerVM {
        stubSavedSubtitle("rus", "https://test/subtitles/gone.vtt", isForced = false, descriptiveLabel = "Russian Full")
        val vm = startedVM()
        report(hlsRowsForFixture())
        clearMocks(interactor, answers = false)
        return vm
    }

    @Test
    fun savedSubtitleUnresolved_noPick_audioChangeKeepsStoredPreference() {
        val vm = unresolvedSavedSubtitleVM()

        vm.onAction(PlayerAction.SelectAudioTrack(1))

        verify(exactly = 1) {
            interactor.saveTrackPreferences(
                42, any(), any(), SubtitlePreference("rus", "https://test/subtitles/gone.vtt", false, "Russian Full"),
            )
        }
    }

    @Test
    fun savedSubtitleUnresolved_pickAfterUnresolved_replacesStored() {
        val vm = unresolvedSavedSubtitleVM()

        vm.onAction(PlayerAction.SelectSubtitle(rowIndex(vm) { it.playerTrackId == "rus.vtt" }))

        verify(exactly = 1) {
            interactor.saveTrackPreferences(42, any(), any(), match { it.url == testSubtitleTracks[1].url })
        }
    }

    @Test
    fun savedSubtitleUnresolved_explicitOffAfterUnresolved_clearsStored() {
        val vm = unresolvedSavedSubtitleVM()

        vm.onAction(PlayerAction.SelectSubtitle(0))

        verify(exactly = 1) { interactor.saveTrackPreferences(42, any(), any(), SubtitlePreference.NONE) }

        vm.onAction(PlayerAction.SelectEpisode(1, 2))
        report(hlsRowsForFixture())
        clearMocks(interactor, answers = false)
        stubSavedSubtitle(null, null)
        vm.onAction(PlayerAction.SelectAudioTrack(1))

        verify(exactly = 1) { interactor.saveTrackPreferences(42, any(), any(), SubtitlePreference.NONE) }
    }

    @Test
    fun pickManifestOnlyRow_savesUnprefixedGroupIdWithLanguageForcedAndLabel() {
        val vm = startedVM()
        report(hlsRowsForFixture() + manifestRendition("eng", "English SDH"))
        clearMocks(interactor, answers = false)

        vm.onAction(PlayerAction.SelectSubtitle(rowIndex(vm) { it.language == "eng" }))

        verify(exactly = 1) {
            interactor.saveTrackPreferences(
                42, any(), any(), SubtitlePreference("eng", "subtitle:English SDH", false, "English SDH"),
            )
        }
    }

    @Test
    fun newSession_restoresManifestOnlyRowFromSavedPreference() {
        stubSavedSubtitle("eng", "subtitle:English SDH", false, null)
        val vm = startedVM()

        report(listOf(manifestRendition("eng", "English"), manifestRendition("eng", "English SDH")))

        verify(exactly = 1) {
            playbackController.selectSubtitle(match { it.playerTrackGroupId == "0:subtitle:English SDH" })
        }
        verify(exactly = 1) { playbackController.selectSubtitle(any()) }
        verify(exactly = 0) { interactor.saveTrackPreferences(any(), any(), any(), any()) }
        assertEquals(2, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun newSession_restoresLanguageLessManifestRowFromSavedPreference() {
        stubSavedSubtitle(null, "subtitle:Signs")
        val vm = startedVM()

        report(listOf(manifestRendition("", "Signs"), manifestRendition("eng", "English")))

        verify(exactly = 1) { playbackController.selectSubtitle(match { it.playerTrackId == "subs:Signs" }) }
        assertEquals(1, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun explicitOff_savesNoneAndClearsAllValues() {
        val vm = startedVM()
        report(hlsRowsForFixture())
        vm.onAction(PlayerAction.SelectSubtitle(rowIndex(vm) { it.playerTrackId == "rus.vtt" }))
        clearMocks(interactor, answers = false)

        vm.onAction(PlayerAction.SelectSubtitle(0))

        verify(exactly = 1) { interactor.saveTrackPreferences(42, any(), any(), SubtitlePreference.NONE) }
    }

    @Test
    fun rebuild_intentDiffersFromSaved_resolvesIntentFirst() {
        val vm = startedVM()
        val rows = hlsRowsForFixture() + manifestRendition("eng", "English")
        report(rows)
        vm.onAction(PlayerAction.SelectSubtitle(rowIndex(vm) { it.playerTrackId == "rus.vtt" }))
        stubSavedSubtitle("eng", "subtitle:English")

        vm.onAction(PlayerAction.ToggleFastDns)
        clearMocks(playbackController, interactor, answers = false)
        report(rows)

        verify(exactly = 1) { playbackController.selectSubtitle(match { it.playerTrackId == "rus.vtt" }) }
        verify(exactly = 0) { playbackController.selectSubtitle(match { it.language == "eng" }) }
        verify(exactly = 0) { interactor.saveTrackPreferences(any(), any(), any(), any()) }
    }

    @Test
    fun newMedia_intentIsNull_usesSavedPreferenceOnly() {
        val vm = startedVM()
        val rows = hlsRowsForFixture() + manifestRendition("eng", "English")
        report(rows)
        vm.onAction(PlayerAction.SelectSubtitle(rowIndex(vm) { it.playerTrackId == "rus.vtt" }))
        clearMocks(interactor, answers = false)
        vm.onAction(PlayerAction.SelectEpisode(1, 2))
        stubSavedSubtitle("eng", "subtitle:English")

        report(rows)

        val after = contentState(vm)
        assertEquals("eng", after.subtitleTracks[after.selectedSubtitleIndex].language)
        verify(exactly = 0) { interactor.saveTrackPreferences(any(), any(), any(), any()) }
    }

    @Test
    fun rebuild_intentRowMissing_fallsBackToSavedPreference() {
        val vm = startedVM()
        report(hlsRowsForFixture() + manifestRendition("eng", "English"))
        vm.onAction(PlayerAction.SelectSubtitle(rowIndex(vm) { it.playerTrackId == "rus.vtt" }))
        clearMocks(interactor, answers = false)
        stubSavedSubtitle("eng", "subtitle:English")

        vm.onAction(PlayerAction.ToggleFastDns)
        clearMocks(playbackController, answers = false)
        report(listOf(manifestRendition("eng", "English")))

        verify(exactly = 1) { playbackController.selectSubtitle(match { it.language == "eng" }) }
        verify(exactly = 0) { interactor.saveTrackPreferences(any(), any(), any(), any()) }
    }

    @Test
    fun rebuildFastDns_audioEmptyReportWithTextRows_reSendsIntent_andSavesNothing() {
        stubSavedSubtitle("rus", testSubtitleTracks[1].url)
        val vm = startedVM()
        val rows = hlsRowsForFixture() + manifestRendition("eng", "English")
        report(rows)
        vm.onAction(PlayerAction.SelectSubtitle(rowIndex(vm) { it.playerTrackId == "rus.vtt" }))
        clearMocks(playbackController, interactor, answers = false)

        vm.onAction(PlayerAction.ToggleFastDns)
        report(rows, audio = emptyList())

        verify(exactly = 1) { playbackController.selectSubtitle(any()) }
        verify(exactly = 1) { playbackController.selectSubtitle(match { it.playerTrackId == "rus.vtt" }) }
        verify(exactly = 0) { interactor.saveTrackPreferences(any(), any(), any(), any()) }
        assertEquals(testContentState.audioTracks, contentState(vm).audioTracks)
    }

    @Test
    fun pickSideLoadedRow_savesUrlNotGroupId() {
        val vm = startedVM()
        report(hlsRowsForFixture())
        clearMocks(interactor, answers = false)

        vm.onAction(PlayerAction.SelectSubtitle(rowIndex(vm) { it.playerTrackId == "rus.vtt" }))

        verify(exactly = 1) {
            interactor.saveTrackPreferences(42, any(), any(), match { it.url == testSubtitleTracks[1].url })
        }
        verify(exactly = 0) {
            interactor.saveTrackPreferences(
                any(), any(), any(), match { it.url?.matches(Regex("""\d+:0""")) == true },
            )
        }
    }
}
