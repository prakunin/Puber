package com.kino.puber.ui.feature.player.vm

import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.domain.interactor.player.PlayerBehaviourPreferences
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.ResumeDialogState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Which overlays leave on their own and which wait for the viewer: the controls bar and the
 * Episodes row go after six idle seconds, everything opened on purpose stays.
 */
internal class PlayerVMOverlayAutoHideTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    /**
     * The position loop never ends, so a failed assertion must still cancel the scope or the
     * test idles until the JVM runs out of memory.
     */
    private fun vmTest(block: suspend TestScope.(PlayerVM) -> Unit) =
        runTest(mainDispatcher.dispatcher.scheduler) {
            val vm = startedVM()
            try {
                block(vm)
            } finally {
                vm.testCancelScope()
            }
        }

    @Test
    fun openEpisodesPanel_closesItself_after6s() =
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenEpisodesPanel)
            advanceTimeBy(5_900)
            assertEquals(ActivePanel.Episodes, contentState(vm).activePanel)

            advanceTimeBy(200)
            assertEquals(ActivePanel.None, contentState(vm).activePanel)
            assertFalse(contentState(vm).controlsVisible)
            assertNull(contentState(vm).controlsFocusTarget)
        }

    @Test
    fun controlsBar_staysAt5900ms_hidesAfter6000ms() =
        vmTest { vm ->
            advanceTimeBy(5_900)
            assertTrue(contentState(vm).controlsVisible)

            advanceTimeBy(200)
            assertFalse(contentState(vm).controlsVisible)
        }

    @Test
    fun resetControlsTimer_at4s_restartsTheSixSeconds() =
        vmTest { vm ->
            advanceTimeBy(4_000)
            vm.onAction(PlayerAction.ResetControlsTimer)

            advanceTimeBy(5_900)
            assertTrue(contentState(vm).controlsVisible)

            advanceTimeBy(200)
            assertFalse(contentState(vm).controlsVisible)
        }

    @Test
    fun resetControlsTimer_withEpisodesOpen_restartsTheSixSeconds() =
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenEpisodesPanel)
            advanceTimeBy(5_000)
            vm.onAction(PlayerAction.ResetControlsTimer)

            advanceTimeBy(5_900)
            assertEquals(ActivePanel.Episodes, contentState(vm).activePanel)

            advanceTimeBy(200)
            assertEquals(ActivePanel.None, contentState(vm).activePanel)
        }

    @Test
    fun settingsPanel_staysOpenFor60s() =
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenSettingsPanel)

            advanceTimeBy(60_000)

            assertEquals(ActivePanel.Settings, contentState(vm).activePanel)
        }

    @Test
    fun aboutPanel_staysOpenFor60s() =
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenAboutPanel)

            advanceTimeBy(60_000)

            assertEquals(ActivePanel.About, contentState(vm).activePanel)
        }

    @Test
    fun closingSettingsPanel_bringsBackControlsThatHideAfter6s() =
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenSettingsPanel)
            vm.onAction(PlayerAction.ClosePanel)
            assertTrue(contentState(vm).controlsVisible)

            advanceTimeBy(5_900)
            assertTrue(contentState(vm).controlsVisible)

            advanceTimeBy(200)
            assertFalse(contentState(vm).controlsVisible)
        }

    @Test
    fun staleClosePanelAfterEpisodesTimeout_doesNotReshowControls() =
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenEpisodesPanel)
            advanceTimeBy(6_100)

            vm.onAction(PlayerAction.ClosePanel)

            assertFalse(contentState(vm).controlsVisible)
            assertEquals(ActivePanel.None, contentState(vm).activePanel)
        }

    @Test
    fun staleResetControlsTimerAfterEpisodesTimeout_doesNotReopenAnything() =
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenEpisodesPanel)
            advanceTimeBy(6_100)

            vm.onAction(PlayerAction.ResetControlsTimer)
            advanceTimeBy(6_100)

            assertEquals(ActivePanel.None, contentState(vm).activePanel)
            assertFalse(contentState(vm).controlsVisible)
        }

    @Test
    fun backAfterEpisodesTimeout_exitsOnTheFirstPress() =
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenEpisodesPanel)
            advanceTimeBy(6_100)

            vm.onAction(PlayerAction.OnBackPressed)

            verify(exactly = 1) { router.back(RESULT_CONTENT_CHANGED, any()) }
        }

    @Test
    fun resumeDialog_noHideTimerRunsWhileItIsUp() {
        every { interactor.resolveMedia(any(), any(), any(), any()) } returns
            testResolvedMedia.copy(watchingTime = 1_200)
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns
            testContentState.copy(
                resumeDialog = ResumeDialogState(savedPosition = 60_000L, formattedTime = "1:00"),
            )
        vmTest { vm ->
            assertFalse(contentState(vm).controlsVisible)
            assertNull(contentState(vm).controlsFocusTarget)

            advanceTimeBy(60_000)
            assertNotNull(contentState(vm).resumeDialog)
            assertFalse(contentState(vm).controlsVisible)
            assertNull(contentState(vm).controlsFocusTarget)

            vm.onAction(PlayerAction.ResumeFromPosition)
            vm.onAction(PlayerAction.ShowControls(FocusTarget.Buttons))
            advanceTimeBy(5_900)
            assertTrue(contentState(vm).controlsVisible)

            advanceTimeBy(200)
            assertFalse(contentState(vm).controlsVisible)
        }
    }

    @Test
    fun publishPreparedContent_resumePromptAfterRetry_staysAloneThroughTheOldHideDeadline() {
        every { interactor.resolveMedia(any(), any(), any(), any()) } returnsMany
            listOf(testResolvedMedia, testResolvedMedia.copy(watchingTime = 1_200))
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returnsMany
            listOf(
                testContentState,
                testContentState.copy(
                    resumeDialog = ResumeDialogState(savedPosition = 60_000L, formattedTime = "1:00"),
                ),
            )
        vmTest { vm ->
            assertTrue(contentState(vm).controlsVisible)
            advanceTimeBy(3_000)

            vm.onAction(PlayerAction.RetryPlayback)

            assertPromptAlone(vm)
            advanceTimeBy(3_100)
            assertPromptAlone(vm)
            advanceTimeBy(60_000)
            assertPromptAlone(vm)
        }
    }

    private fun assertPromptAlone(vm: PlayerVM) {
        assertNotNull(contentState(vm).resumeDialog)
        assertFalse(contentState(vm).controlsVisible)
        assertNull(contentState(vm).controlsFocusTarget)
    }

    @Test
    fun controlsHideAfter6s_whilePaused() {
        every { playbackController.isPlaying } returns false
        vmTest { vm ->
            callbackSlot.captured.onPlaybackStateChanged(false, false, 0L, 2_400_000L, 0L)
            vm.onAction(PlayerAction.ShowControls(FocusTarget.Buttons))
            assertTrue(contentState(vm).controlsVisible)

            advanceTimeBy(6_100)

            assertFalse(contentState(vm).controlsVisible)
        }
    }

    private val debugInfo = PlaybackControl.DebugInfo(
        videoResolution = "1920x1080",
        videoCodec = "avc1",
        videoBitrate = "8.0 Mbps",
        videoFrameRate = "24 fps",
        audioCodec = "mp4a",
        audioChannels = "stereo",
        droppedFrames = "0",
        bufferedDuration = "12.0s",
        bufferedBytes = "38.0 / 64 MB",
        streamSource = "MSK01",
        renderRate = "24.0 / 24 fps",
        frameDrops = "0 (keyframe 0, run 0)",
        frameReleaseOffset = "+0.0 ms",
        videoSwitch = "—",
    )

    @Test
    fun debugReadings_dropWhenEpisodesPanelTimesOut() {
        every { playbackController.getDebugInfo() } returns debugInfo
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenSettingsPanel)
            assertNotNull(contentState(vm).debugInfo)
            vm.onAction(PlayerAction.OpenEpisodesPanel)

            advanceTimeBy(6_100)

            assertNull(contentState(vm).debugInfo)
        }
    }

    @Test
    fun debugReadings_stayWhenEpisodesPanelTimesOut_withDebugOverlayEnabled() {
        every { interactor.getBehaviourPreferences() } returns PlayerBehaviourPreferences(
            debugOverlayEnabled = true,
            okTogglesPlayPause = false,
        )
        every { playbackController.getDebugInfo() } returns debugInfo
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenSettingsPanel)
            vm.onAction(PlayerAction.OpenEpisodesPanel)

            advanceTimeBy(6_100)

            assertEquals(ActivePanel.None, contentState(vm).activePanel)
            assertEquals(debugInfo, contentState(vm).debugInfo)
        }
    }

    @Test
    fun controlsTimerDuringExit_changesNothing() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns
            testContentState.copy(hasNextEpisode = false)
        val releaseSave = CompletableDeferred<Unit>()
        coEvery { interactor.saveWatchingTime(42, 1, 0, 1) } coAnswers {
            releaseSave.await()
        }
        vmTest { vm ->
            assertTrue(contentState(vm).controlsVisible)
            advanceTimeBy(3_000)
            callbackSlot.captured.onPlaybackEnded()
            val before = contentState(vm)

            advanceTimeBy(3_100)

            assertEquals(before, contentState(vm))
            assertTrue(contentState(vm).controlsVisible)
            verify(exactly = 0) { router.back(any(), any()) }
            releaseSave.complete(Unit)
        }
    }

    @Test
    fun lastEpisode_backOutOfEpisodesAfterEnd_exitsOnce_andLaterTimeChangesNothing() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns
            testContentState.copy(hasNextEpisode = false)
        val releaseSave = CompletableDeferred<Unit>()
        coEvery { interactor.saveWatchingTime(42, 1, 0, 1) } coAnswers {
            releaseSave.await()
        }
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenEpisodesPanel)
            callbackSlot.captured.onPlaybackEnded()
            vm.onAction(PlayerAction.OnBackPressed)
            val before = contentState(vm)

            advanceTimeBy(6_100)

            assertEquals(before, contentState(vm))
            assertNull(contentState(vm).nextEpisodeCountdown)
            verify(exactly = 0) { router.back(any(), any()) }
            releaseSave.complete(Unit)
        }
    }

    private suspend fun TestScope.bufferingBlip(playingAfter: Boolean = true) {
        callbackSlot.captured.onPlaybackStateChanged(false, true, 0L, 2_400_000L, 0L)
        advanceTimeBy(1_000)
        callbackSlot.captured.onPlaybackStateChanged(playingAfter, false, 0L, 2_400_000L, 0L)
    }

    @Test
    fun episodesPanel_stillClosesAfterBufferingBlip() =
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenEpisodesPanel)
            advanceTimeBy(2_000)
            bufferingBlip()

            advanceTimeBy(5_900)
            assertEquals(ActivePanel.Episodes, contentState(vm).activePanel)

            advanceTimeBy(200)
            assertEquals(ActivePanel.None, contentState(vm).activePanel)
        }

    @Test
    fun lastEpisode_episodesTimeoutAfterEnd_landsOnEndedControlsAndStays() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns
            testContentState.copy(hasNextEpisode = false)
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenEpisodesPanel)
            callbackSlot.captured.onPlaybackEnded()
            verify(exactly = 0) { router.back(any(), any()) }

            advanceTimeBy(6_100)
            assertEquals(ActivePanel.None, contentState(vm).activePanel)
            assertTrue(contentState(vm).controlsVisible)
            assertEquals(FocusTarget.EpisodesButton, contentState(vm).controlsFocusTarget)
            verify(exactly = 0) { router.back(any(), any()) }

            advanceTimeBy(60_000)
            assertTrue(contentState(vm).controlsVisible)
            verify(exactly = 0) { router.back(any(), any()) }

            vm.onAction(PlayerAction.OnBackPressed)
            verify(exactly = 1) { router.back(RESULT_CONTENT_CHANGED, any()) }
        }
    }

    @Test
    fun settingsPanel_staysOpenAcrossBufferingStartAndEnd() =
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenSettingsPanel)
            bufferingBlip()

            advanceTimeBy(60_000)

            assertEquals(ActivePanel.Settings, contentState(vm).activePanel)
        }

    @Test
    fun endedControls_stayUpAfterBufferingEndAndAfterAKeyAndTime() =
        vmTest { vm ->
            callbackSlot.captured.onPlaybackEnded()
            vm.onAction(PlayerAction.CancelNextEpisodeCountdown)
            callbackSlot.captured.onPlaybackEnded()
            assertTrue(contentState(vm).controlsVisible)

            bufferingBlip(playingAfter = false)
            vm.onAction(PlayerAction.ResetControlsTimer)
            advanceTimeBy(60_000)
            assertTrue(contentState(vm).controlsVisible)

            vm.onAction(PlayerAction.OnBackPressed)
            verify(exactly = 1) { router.back(any(), any()) }
        }

    @Test
    fun episodesPanelHeldByContextMenu_staysFor30s_thenClosesSixSecondsAfterRelease() =
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenEpisodesPanel)
            vm.onAction(PlayerAction.SetEpisodesPanelHeld(true))

            advanceTimeBy(30_000)
            assertEquals(ActivePanel.Episodes, contentState(vm).activePanel)

            vm.onAction(PlayerAction.SetEpisodesPanelHeld(false))
            advanceTimeBy(5_900)
            assertEquals(ActivePanel.Episodes, contentState(vm).activePanel)

            advanceTimeBy(200)
            assertEquals(ActivePanel.None, contentState(vm).activePanel)
        }

    @Test
    fun heldEpisodesPanel_staysThroughBufferingBlip() =
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenEpisodesPanel)
            vm.onAction(PlayerAction.SetEpisodesPanelHeld(true))
            bufferingBlip()

            advanceTimeBy(30_000)

            assertEquals(ActivePanel.Episodes, contentState(vm).activePanel)
        }

    @Test
    fun nonLastEpisode_episodesTimeoutAfterEnd_landsOnEndedControlsAndStays() =
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenEpisodesPanel)
            callbackSlot.captured.onPlaybackEnded()
            vm.onAction(PlayerAction.CancelNextEpisodeCountdown)

            advanceTimeBy(6_100)
            assertEquals(ActivePanel.None, contentState(vm).activePanel)
            assertTrue(contentState(vm).controlsVisible)
            assertEquals(FocusTarget.EpisodesButton, contentState(vm).controlsFocusTarget)

            advanceTimeBy(60_000)
            assertTrue(contentState(vm).controlsVisible)
            verify(exactly = 0) { router.back(any(), any()) }

            vm.onAction(PlayerAction.OnBackPressed)
            verify(exactly = 1) { router.back(RESULT_CONTENT_CHANGED, any()) }
        }

    private fun endedUnderPanel_closedByViewer_keepsControlsUntilBack(panel: PlayerAction) =
        vmTest { vm ->
            vm.onAction(panel)
            callbackSlot.captured.onPlaybackEnded()
            vm.onAction(PlayerAction.CancelNextEpisodeCountdown)

            vm.onAction(PlayerAction.ClosePanel)
            assertEquals(ActivePanel.None, contentState(vm).activePanel)
            assertTrue(contentState(vm).controlsVisible)

            advanceTimeBy(60_000)
            assertTrue(contentState(vm).controlsVisible)
            verify(exactly = 0) { router.back(any(), any()) }

            vm.onAction(PlayerAction.OnBackPressed)
            verify(exactly = 1) { router.back(RESULT_CONTENT_CHANGED, any()) }
        }

    @Test
    fun settingsClosedAfterEnd_keepsControlsUp_andBackExitsOnce() =
        endedUnderPanel_closedByViewer_keepsControlsUntilBack(PlayerAction.OpenSettingsPanel)

    @Test
    fun aboutClosedAfterEnd_keepsControlsUp_andBackExitsOnce() =
        endedUnderPanel_closedByViewer_keepsControlsUntilBack(PlayerAction.OpenAboutPanel)

    @Test
    fun resetControlsTimer_whileEpisodesPanelHeld_doesNotCloseIt() =
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenEpisodesPanel)
            vm.onAction(PlayerAction.SetEpisodesPanelHeld(true))
            advanceTimeBy(5_000)

            vm.onAction(PlayerAction.ResetControlsTimer)
            advanceTimeBy(30_000)

            assertEquals(ActivePanel.Episodes, contentState(vm).activePanel)
        }

    @Test
    fun reopeningEpisodesAfterTimeout_armsTheTimerAgain() =
        vmTest { vm ->
            vm.onAction(PlayerAction.OpenEpisodesPanel)
            advanceTimeBy(6_100)
            assertEquals(ActivePanel.None, contentState(vm).activePanel)

            vm.onAction(PlayerAction.OpenEpisodesPanel)
            advanceTimeBy(5_900)
            assertEquals(ActivePanel.Episodes, contentState(vm).activePanel)

            advanceTimeBy(200)
            assertEquals(ActivePanel.None, contentState(vm).activePanel)
        }

    @Test
    fun pausing_setsIsPlayingFalse_andResumingSetsItTrue() =
        vmTest { vm ->
            every { playbackController.isPlaying } returns true
            vm.onAction(PlayerAction.TogglePlayPause)
            assertFalse(contentState(vm).isPlaying)

            callbackSlot.captured.onPlaybackStateChanged(true, false, 0L, 2_400_000L, 0L)
            assertTrue(contentState(vm).isPlaying)
        }

    @Test
    fun playbackStateChange_toNotPlaying_setsIsPlayingFalse_withoutAPauseBefore() =
        vmTest { vm ->
            assertTrue(contentState(vm).isPlaying)

            callbackSlot.captured.onPlaybackStateChanged(false, false, 0L, 2_400_000L, 0L)

            assertFalse(contentState(vm).isPlaying)
        }
}
