package com.kino.puber.ui.feature.player.vm

import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerScreenParams
import com.kino.puber.ui.feature.player.model.PlayerViewState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * PlayerVM: moving between episodes — the countdown that offers the next one, the switch
 * itself, and the stale completions a switch leaves behind.
 *
 * No `runTest` — UnconfinedTestDispatcher makes all coroutines synchronous.
 * `runTest` adds `advanceUntilIdle()` at the end which spins PlayerVM's infinite
 * `startPositionUpdates()` loop forever → OOM.
 * Without `runTest`, the infinite loop stays suspended at its first `delay()` — harmless.
 */
internal class PlayerVMEpisodeFlowTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    // region Bug 3: Countdown

    @Test
    fun cancelCountdown_setsNull() {
        val vm = startedVM()
        vm.onAction(PlayerAction.CancelNextEpisodeCountdown)
        assertNull(contentState(vm).nextEpisodeCountdown)
    }

    @Test
    fun playbackEnded_startsCountdown_forSeries() {
        val vm = startedVM()
        callbackSlot.captured.onPlaybackEnded()
        assertEquals(15, contentState(vm).nextEpisodeCountdown)
    }

    @Test
    fun playbackEnded_doesNotStartCountdown_whenPanelIsOpen() {
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenAudioSubtitlesPanel)

        callbackSlot.captured.onPlaybackEnded()

        assertNull(contentState(vm).nextEpisodeCountdown)
        assertEquals(ActivePanel.AudioSubtitles, contentState(vm).activePanel)
    }

    @Test
    fun closePanel_startsCountdownDeferredWhilePanelWasOpen() {
        every { playbackController.currentPosition } returns 2_399_000L
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenAudioSubtitlesPanel)
        callbackSlot.captured.onPlaybackEnded()

        vm.onAction(PlayerAction.ClosePanel)

        assertEquals(15, contentState(vm).nextEpisodeCountdown)
    }

    // endregion

    // region Episode switching

    @Test
    fun switchEpisode_releasesPlayer() {
        startedVM().onAction(PlayerAction.SelectEpisode(1, 2))
        verify { playbackController.release() }
    }

    @Test
    fun switchEpisode_doesNotReuseInitialMovieVideoNumber() {
        val vm = createVM(PlayerScreenParams(itemId = 42, seasonNumber = 1, episodeNumber = 1, videoNumber = 7))
            .also { it.testOnStart() }

        vm.onAction(PlayerAction.SelectEpisode(1, 2))

        verify {
            interactor.resolveMedia(
                item = testItem,
                seasonNumber = 1,
                episodeNumber = 2,
                videoNumber = null,
            )
        }
    }

    @Test
    fun switchEpisode_resetsTracksRestoredFlag() {
        // After episode switch, track restoration should run again for the new episode.
        // Regression: without reset, tracksRestoredForCurrentMedia stays true → tracks not restored.
        every { interactor.getPreferredAudioLang(42) } returns "rus"
        val vm = startedVM()

        // First episode: tracks restored
        val tracks = listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus"))
        callbackSlot.captured.onTracksUpdated(tracks, 0)
        verify(exactly = 1) { playbackController.selectAudioTrack(1) }

        // Switch episode → flag should reset
        vm.onAction(PlayerAction.SelectEpisode(1, 2))

        // Second episode: tracks restored again
        callbackSlot.captured.onTracksUpdated(tracks, 0)
        verify(exactly = 2) { playbackController.selectAudioTrack(1) }
    }

    @Test
    fun switchEpisode_resetsCountdownDismissedFlag() {
        // After episode switch, user should see next-episode countdown again.
        // Regression: without reset, countdownDismissed stays true → countdown never shown.
        val vm = startedVM()

        // Dismiss countdown on current episode
        callbackSlot.captured.onPlaybackEnded()
        vm.onAction(PlayerAction.CancelNextEpisodeCountdown)
        assertNull(contentState(vm).nextEpisodeCountdown)

        // Switch episode — re-triggers preparePlayback which re-calls setCallback
        vm.onAction(PlayerAction.SelectEpisode(1, 2))
        // After switch, preparePlayback runs → Content state restored
        assertTrue(vm.testStateValue is PlayerViewState.Content)

        // New episode: playback ends → countdown should start again (dismissed flag was reset)
        callbackSlot.captured.onPlaybackEnded()
        assertNotNull(contentState(vm).nextEpisodeCountdown)
    }

    @Test
    fun stalePrepareCompletion_cannotOverwriteNewEpisode() {
        val releaseFirstLoad = CompletableDeferred<Unit>()
        var detailsCalls = 0
        coEvery { interactor.getItemDetails(42) } coAnswers {
            detailsCalls += 1
            if (detailsCalls == 1) {
                releaseFirstLoad.await()
            }
            testItem
        }
        val nextEpisode = testResolvedMedia.copy(
            videoNumber = 2,
            episodeId = 102,
            episodeNumber = 2,
        )
        every { interactor.resolveMedia(any(), any(), any(), any()) } returns nextEpisode
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns
            testContentState.copy(currentEpisodeId = 102)
        val vm = startedVM()

        vm.onAction(PlayerAction.SelectEpisode(1, 2))
        assertEquals(102, contentState(vm).currentEpisodeId)

        releaseFirstLoad.complete(Unit)

        assertEquals(102, contentState(vm).currentEpisodeId)
        verify(exactly = 1) { playbackController.prepare(any(), any(), any()) }
    }

    @Test
    fun staleSkipSegmentsCompletion_cannotAffectNewEpisode() {
        val releaseFirstSegments = CompletableDeferred<Unit>()
        val staleSegments = listOf(SkipSegment(SkipSegmentType.INTRO, startMs = 0, endMs = 10_000))
        var segmentLoads = 0
        coEvery { skipSegmentInteractor.loadSegments(any(), any(), any()) } coAnswers {
            segmentLoads += 1
            if (segmentLoads == 1) {
                withContext(NonCancellable) {
                    releaseFirstSegments.await()
                }
                staleSegments
            } else {
                emptyList()
            }
        }
        every { interactor.resolveMedia(any(), any(), any(), any()) } returns testResolvedMedia andThen
            testResolvedMedia.copy(videoNumber = 2, episodeId = 102, episodeNumber = 2)
        val vm = startedVM()

        vm.onAction(PlayerAction.SelectEpisode(1, 2))
        releaseFirstSegments.complete(Unit)

        verify(exactly = 0) { skipSegmentInteractor.findCreditsSegment(staleSegments) }
    }

    // endregion

    // region Movie-specific behavior

    @Test
    fun playbackEnded_doesNotStartCountdown_forMovies() {
        // Movie content should never show next-episode countdown.
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            isMovie = true,
            hasNextEpisode = false,
        )
        val vm = startedVM()

        callbackSlot.captured.onPlaybackEnded()

        assertNull(contentState(vm).nextEpisodeCountdown)
    }

    // endregion

    // region Race condition

    @Test
    fun nextEpisode_cancelsCountdown_and_switches() {
        every { interactor.findNextEpisode(any(), any(), any()) } returns (1 to 2)
        val vm = startedVM()

        // Playback ends → starts countdown
        callbackSlot.captured.onPlaybackEnded()
        assertNotNull(contentState(vm).nextEpisodeCountdown)

        // User manually triggers next episode during countdown
        vm.onAction(PlayerAction.NextEpisode)

        verify { playbackController.release() }
        assertTrue(vm.testStateValue is PlayerViewState.Content)
    }

    @Test
    fun nextEpisode_doesNothing_whenNoNextEpisode() {
        val vm = startedVM()
        vm.onAction(PlayerAction.NextEpisode)
        verify(exactly = 0) { playbackController.release() }
    }

    // endregion

    // region Previous episode

    @Test
    fun previousEpisode_switchesEpisode() {
        every { interactor.findPreviousEpisode(any(), any(), any()) } returns (1 to 1)
        val vm = startedVM()

        vm.onAction(PlayerAction.PreviousEpisode)

        verify { playbackController.release() }
    }

    @Test
    fun previousEpisode_doesNothing_whenNoPrevious() {
        val vm = startedVM()

        vm.onAction(PlayerAction.PreviousEpisode)

        verify(exactly = 0) { playbackController.release() }
    }

    // endregion
}
