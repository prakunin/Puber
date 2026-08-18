package com.kino.puber.ui.feature.player.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerViewState
import com.kino.puber.ui.feature.player.model.ResumeDialogState
import com.kino.puber.ui.feature.player.model.SkipSegmentUIState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import timber.log.Timber

/**
 * PlayerVM: leaving the screen, coming back to it, and moving around inside one episode.
 *
 * No `runTest` — UnconfinedTestDispatcher makes all coroutines synchronous.
 * `runTest` adds `advanceUntilIdle()` at the end which spins PlayerVM's infinite
 * `startPositionUpdates()` loop forever → OOM.
 * Without `runTest`, the infinite loop stays suspended at its first `delay()` — harmless.
 */
internal class PlayerVMPlaybackFlowTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    // region Back navigation

    @Test
    fun backPressed_cancelsCountdown_whenActive() {
        val vm = startedVM()
        callbackSlot.captured.onPlaybackEnded()
        assertNotNull(contentState(vm).nextEpisodeCountdown)

        vm.onAction(PlayerAction.OnBackPressed)
        assertNull(contentState(vm).nextEpisodeCountdown)
    }

    @Test
    fun backPressed_beforeContent_consumesResultListenerWithEmptyChanges() {
        createVM().onAction(PlayerAction.OnBackPressed)

        verifyEmptyContentChangeResult()
    }

    @Test
    fun backPressed_afterProgressSave_returnsPlaybackProgressResult() {
        val vm = startedVM()

        vm.onAction(PlayerAction.OnBackPressed)

        verifyContentChangeResult(ContentChangeType.PlaybackProgress)
    }

    @Test
    fun backPressed_waitsForFinalProgressSave() {
        val releaseSave = CompletableDeferred<Unit>()
        coEvery { interactor.saveWatchingTime(42, 1, 0, 1) } coAnswers {
            releaseSave.await()
        }
        val vm = startedVM()

        vm.onAction(PlayerAction.OnBackPressed)

        verify(exactly = 0) { router.back(any(), any()) }
        releaseSave.complete(Unit)
        verifyContentChangeResult(ContentChangeType.PlaybackProgress)
    }

    @Test
    fun repeatedBackWhileFinalProgressSavePending_staysInterceptedAndReturnsOnce() {
        val releaseSave = CompletableDeferred<Unit>()
        coEvery { interactor.saveWatchingTime(42, 1, 0, 1) } coAnswers {
            releaseSave.await()
        }
        val vm = startedVM()

        vm.onBackPressed()
        vm.onBackPressed()

        verify(exactly = 2) { router.addBackDispatcher(vm) }
        verify(exactly = 0) { router.back(any(), any()) }
        releaseSave.complete(Unit)
        verify(exactly = 1) {
            router.back(
                RESULT_CONTENT_CHANGED,
                match { result ->
                    val changes = result as? ContentChangeSet ?: return@match false
                    changes.changes[42] == setOf(ContentChangeType.PlaybackProgress)
                },
            )
        }
    }

    @Test
    fun failedFinalProgressSave_returnsEmptyChanges() {
        coEvery { interactor.saveWatchingTime(42, 1, 0, 1) } throws IllegalStateException("save failed")
        val vm = startedVM()

        vm.onAction(PlayerAction.OnBackPressed)

        verifyEmptyContentChangeResult()
    }

    @Test
    fun failedFinalProgressSave_emitsIdentityFreeDiagnostic() {
        val privateItemId = 424_242
        val privateTitle = "Private watched title"
        val privateTime = "private-watching-time"
        val failure = IllegalStateException(
            "Failed to save $privateTitle for item=$privateItemId at time=$privateTime",
        )
        coEvery { interactor.saveWatchingTime(42, 1, 0, 1) } throws failure
        val logTree = CollectingLogTree()
        val vm = startedVM()

        Timber.plant(logTree)
        try {
            vm.onAction(PlayerAction.OnBackPressed)
        } finally {
            Timber.uproot(logTree)
        }

        val output = logTree.output()
        assertEquals(1, logTree.entryCount)
        assertTrue(output.contains(PROGRESS_SAVE_FAILURE_DIAGNOSTIC), output)
        assertFalse(output.contains(privateItemId.toString()), output)
        assertFalse(output.contains(privateTitle), output)
        assertFalse(output.contains(privateTime), output)
        assertFalse(output.contains(failure.message.orEmpty()), output)
        verifyEmptyContentChangeResult()
    }

    // endregion

    // region Background / Resume

    @Test
    fun pauseForBackground_pausesAndSavesPosition() {
        every { playbackController.isPlaying } returns true
        val vm = startedVM()

        vm.onAction(PlayerAction.OnBackground)

        verify { playbackController.pause() }
        verify(exactly = 0) { router.back(any(), any()) }
        assertEquals(false, contentState(vm).isPlaying)
    }

    @Test
    fun pauseForBackground_savesPosition_whenAlreadyPaused() {
        every { playbackController.isPlaying } returns false
        startedVM().onAction(PlayerAction.OnBackground)

        verify(exactly = 0) { playbackController.pause() }
        coVerify(exactly = 1) { interactor.saveWatchingTime(42, 1, 0, 1) }
    }

    @Test
    fun retryPlayback_transitionsToLoadingAndReloads() {
        val vm = startedVM()

        // Force error state
        callbackSlot.captured.onError("Error")
        assertTrue(vm.testStateValue is PlayerViewState.Error)

        vm.onAction(PlayerAction.RetryPlayback)

        verify { playbackController.release() }
        // After retry, preparePlayback runs again → Content
        assertTrue(vm.testStateValue is PlayerViewState.Content)
    }

    // endregion

    // region Seek

    @Test
    fun seekForward_updatesCurrentPosition() {
        val vm = startedVM()
        vm.onAction(PlayerAction.SeekForward)
        assertTrue(contentState(vm).currentPosition > 0)
    }

    @Test
    fun seekBackward_updatesCurrentPosition() {
        every { playbackController.currentPosition } returns 30_000L
        val vm = startedVM()
        vm.onAction(PlayerAction.SeekBackward)
        assertTrue(contentState(vm).currentPosition < 30_000L)
    }

    // endregion

    // region Resume dialog

    @Test
    fun resumeFromPosition_seeksToSavedPosition_clearsDialog() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            resumeDialog = ResumeDialogState(savedPosition = 120_000L, formattedTime = "2:00", episodeInfo = null),
            isPlaying = false,
        )
        val vm = startedVM()

        vm.onAction(PlayerAction.ResumeFromPosition)

        verify { playbackController.seekTo(120_000L) }
        verify { playbackController.play() }
        assertNull(contentState(vm).resumeDialog)
        assertTrue(contentState(vm).isPlaying)
    }

    @Test
    fun startFromBeginning_seeksToZero_clearsDialog() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            resumeDialog = ResumeDialogState(savedPosition = 120_000L, formattedTime = "2:00", episodeInfo = null),
            isPlaying = false,
        )
        val vm = startedVM()

        vm.onAction(PlayerAction.StartFromBeginning)

        verify { playbackController.seekTo(0) }
        verify { playbackController.play() }
        assertNull(contentState(vm).resumeDialog)
    }

    // endregion

    // region Skip segments

    @Test
    fun skipSegmentClicked_seeksToTarget() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            activeSkipSegment = SkipSegmentUIState("Skip Intro", 30_000L, SkipSegmentType.INTRO, 5),
        )
        val vm = startedVM()

        vm.onAction(PlayerAction.SkipSegmentClicked)

        verify { playbackController.seekTo(30_000L) }
        assertNull(contentState(vm).activeSkipSegment)
    }

    @Test
    fun cancelSkipSegment_clearsOverlay() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            activeSkipSegment = SkipSegmentUIState("Skip Intro", 30_000L, SkipSegmentType.INTRO, 5),
        )
        val vm = startedVM()

        vm.onAction(PlayerAction.CancelSkipSegment)

        assertNull(contentState(vm).activeSkipSegment)
    }

    // endregion
}
