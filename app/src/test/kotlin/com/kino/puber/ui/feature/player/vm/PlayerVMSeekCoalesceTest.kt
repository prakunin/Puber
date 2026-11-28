package com.kino.puber.ui.feature.player.vm

import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.domain.interactor.player.StreamCandidate
import com.kino.puber.domain.interactor.player.StreamType
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerCountdowns
import com.kino.puber.util.MainDispatcherExtension
import com.kino.puber.ui.feature.player.model.PlayerContentState
import com.kino.puber.ui.feature.player.model.ResumeDialogState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * A held Left/Right key seeks at most once per throttle window plus a trailing seek to the final
 * target, while the indicator, the thumb, saves and stream switches follow the pending target.
 */
internal class PlayerVMSeekCoalesceTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()

        private const val P = 100_000L
        private const val STEP_10_S = 10_000L
    }

    private var position = 0L

    /** Seeks the VM issued: (scheduler time, target). */
    private val seeks = mutableListOf<Pair<Long, Long>>()

    private fun TestScope.playingAt(
        from: Long,
        segments: List<SkipSegment> = emptyList(),
        notifySeeks: Boolean = true,
    ) {
        position = from
        if (segments.isNotEmpty()) {
            every { skipSegmentInteractor.findActiveSegment(any(), any(), any()) } answers {
                segments.firstOrNull {
                    position >= it.startMs - PlayerCountdowns.PROMPT_LEAD_IN_MS && position < it.endMs!!
                }
            }
        }
        every { mapper.formatTime(any()) } answers { "t${firstArg<Long>()}" }
        every { playbackController.currentPosition } answers { position }
        every { playbackController.seekTo(any()) } answers {
            val target = firstArg<Long>()
            seeks += testScheduler.currentTime to target
            position = target
            if (notifySeeks) callbackSlot.captured.onSeekPerformed()
        }
    }

    private fun TestScope.press(vm: PlayerVM, action: PlayerAction = PlayerAction.SeekForward, gapMs: Long = 50) {
        advanceTimeBy(gapMs)
        vm.onAction(action)
    }

    /** Cancels the VM scope even when an assertion fails: its position loop would keep runTest from ending. */
    private fun holdTest(
        from: Long = P,
        segments: List<SkipSegment> = emptyList(),
        notifySeeks: Boolean = true,
        body: suspend TestScope.(PlayerVM) -> Unit,
    ) =
        runTest(mainDispatcher.dispatcher.scheduler) {
            playingAt(from, segments, notifySeeks)
            val vm = startedVM()
            runCurrent()
            try {
                body(vm)
            } finally {
                vm.testCancelScope()
            }
        }

    /** Seek times relative to the first seek, and the targets. */
    private fun seekTimes() = seeks.map { it.first - seeks.first().first }

    private fun seekTargets() = seeks.map { it.second }

    private val PlayerContentState.indicatorTime get() = seekIndicator?.targetTimeText

    @Test
    fun everyPressInAHoldShowsTheCumulativeTarget() = holdTest { vm ->
        val expected = listOf(10_000L, 20_000, 40_000, 70_000, 130_000, 190_000).map { P + it }
        expected.forEachIndexed { index, target ->
            press(vm)
            assertEquals(target, contentState(vm).currentPosition)
            assertEquals("t$target", contentState(vm).indicatorTime)
            if (index == 0) assertEquals(listOf(P + 10_000), seekTargets())
        }
    }

    @Test
    fun aHoldNearTheEndClampsTheTargetToTheDuration() = holdTest(from = 2_400_000L - 15_000) { vm ->
        press(vm)
        press(vm)
        advanceTimeBy(1_000)

        assertEquals(2_400_000L, contentState(vm).currentPosition)
        assertEquals(2_400_000L, seeks.last().second)
    }

    @Test
    fun aHeldKeySeeksOncePerWindowAndEndsOnTheFinalTarget() = holdTest { vm ->
        repeat(6) { press(vm) }
        advanceTimeBy(1_000)

        assertEquals(listOf(P + 10_000, P + 190_000), seekTargets())
        assertEquals(250L, seekTimes()[1])
    }

    @Test
    fun aLongHoldKeepsAtLeastOneWindowBetweenSeeks() = holdTest { vm ->
        repeat(20) { press(vm) }
        val lastPress = testScheduler.currentTime
        advanceTimeBy(1_000)

        seekTimes().zipWithNext().forEach { (a, b) -> assertTrue(b - a >= 250, "seeks at $a and $b") }
        assertEquals(contentState(vm).currentPosition, seeks.last().second)
        val lastSeek = seeks.last().first
        assertTrue(lastSeek - lastPress <= 250, "last seek at $lastSeek, last press at $lastPress")
    }

    @Test
    fun aSingleBackwardPressSeeksOnceToZero() = holdTest(from = 5_000) { vm ->
        press(vm, PlayerAction.SeekBackward)
        advanceTimeBy(1_000)

        assertEquals(listOf(0L), seekTargets())
    }

    @Test
    fun thePositionTickKeepsThePendingTarget() = holdTest { vm ->
        advanceTimeBy(400)
        vm.onAction(PlayerAction.SeekForward)
        press(vm)
        position = P + 10_000
        advanceTimeBy(100)
        runCurrent()

        assertEquals(P + 20_000, contentState(vm).currentPosition)
    }

    private fun withResumeDialog() {
        every { interactor.resolveMedia(any(), any(), any(), any()) } returns
            testResolvedMedia.copy(watchingTime = 60)
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns
            testContentState.copy(resumeDialog = ResumeDialogState(savedPosition = 60_000L, formattedTime = "1:00"))
    }

    /** Asserts nothing seeks after `lastTarget` was the final seek. */
    private suspend fun TestScope.assertNoSeekAfter(lastTarget: Long) {
        assertEquals(lastTarget, seeks.last().second)
        val count = seeks.size
        advanceTimeBy(1_000)
        assertEquals(count, seeks.size)
    }

    @Test
    fun startOverDropsThePendingSeek() {
        withResumeDialog()
        holdTest { vm ->
            repeat(2) { press(vm) }
            vm.onAction(PlayerAction.StartFromBeginning)

            assertNoSeekAfter(0L)
        }
    }

    @Test
    fun resumeDropsThePendingSeek() {
        withResumeDialog()
        holdTest { vm ->
            repeat(2) { press(vm) }
            vm.onAction(PlayerAction.ResumeFromPosition)

            assertNoSeekAfter(60_000L)
        }
    }

    @Test
    fun skipSegmentDropsThePendingSeek() = holdTest(
        from = 7_500,
        segments = listOf(SkipSegment(SkipSegmentType.INTRO, startMs = 10_000, endMs = 400_000)),
        notifySeeks = false,
    ) { vm ->
        repeat(2) {
            position += PlayerCountdowns.TICK_MS / 2
            advanceTimeBy(PlayerCountdowns.TICK_MS / 2)
            runCurrent()
        }
        val prompt = contentState(vm).activeSkipSegment
        assertEquals(SkipSegmentType.INTRO, prompt?.type)
        repeat(2) { press(vm) }

        vm.onAction(PlayerAction.SkipSegmentClicked)

        assertNoSeekAfter(prompt!!.targetPositionMs)
    }

    @Test
    fun backgroundSavesThePendingTarget() = holdTest { vm ->
        repeat(2) { press(vm) }

        vm.onAction(PlayerAction.OnBackground)
        runCurrent()

        coVerify { interactor.saveWatchingTime(42, 1, ((P + 20_000) / 1000).toInt(), 1) }
    }

    @Test
    fun backPressedSavesThePendingTarget() = holdTest { vm ->
        repeat(2) { press(vm) }

        vm.onAction(PlayerAction.HideControls)
        vm.onAction(PlayerAction.OnBackPressed)
        runCurrent()

        coVerify { interactor.saveWatchingTime(42, 1, ((P + 20_000) / 1000).toInt(), 1) }
        verify { router.back(any(), any()) }
        assertNoSeekAfter(P + 10_000)
    }

    @Test
    fun aQualitySwitchCommitsThePendingTargetFirst() = holdTest { vm ->
        repeat(2) { press(vm) }

        vm.onAction(PlayerAction.SelectQuality(1))

        verifyOrder {
            playbackController.seekTo(P + 20_000)
            playbackController.switchStream(any(), any())
        }
        assertNoSeekAfter(P + 20_000)
    }

    @Test
    fun aStreamFailureCommitsThePendingTargetBeforeTheNextCandidate() {
        val primary = StreamCandidate("https://cdn/primary.m3u8", StreamType.HLS)
        val fallback = StreamCandidate("https://cdn/fallback.m3u8", StreamType.HLS)
        every { interactor.selectStreamCandidates(any(), any()) } returns listOf(primary, fallback)
        holdTest { vm ->
            repeat(2) { press(vm) }

            callbackSlot.captured.onStreamFailure("network error", PlaybackControl.StreamRecovery.NEXT_URL, primary.url)

            verifyOrder {
                playbackController.seekTo(P + 20_000)
                playbackController.switchStream(fallback, any())
            }
            assertNoSeekAfter(P + 20_000)
        }
    }

    @Test
    fun aLinkRefreshCommitsThePendingTargetBeforeTheNewStream() {
        val expired = StreamCandidate("https://cdn/expired.m3u8", StreamType.HLS)
        val refreshed = StreamCandidate("https://cdn/refreshed.m3u8", StreamType.HLS)
        every { interactor.selectStreamCandidates(any(), any()) } returnsMany listOf(listOf(expired), listOf(refreshed))
        coEvery { interactor.refreshItemDetails(42) } returns testItem
        every { interactor.resolveMedia(testItem, 1, 1, 1) } returns testResolvedMedia
        holdTest { vm ->
            repeat(2) { press(vm) }

            callbackSlot.captured.onStreamFailure("expired", PlaybackControl.StreamRecovery.NEXT_URL, expired.url)
            runCurrent()

            verifyOrder {
                playbackController.seekTo(P + 20_000)
                playbackController.switchStream(refreshed, any())
            }
            assertNoSeekAfter(P + 20_000)
        }
    }

    @Test
    fun aFilmEndingWithAPendingTargetSeeksThereInsteadOfExiting() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            isMovie = true,
            hasNextEpisode = false,
            hasPreviousEpisode = false,
        )
        holdTest(from = 2_400_000L - 5_000) { vm ->
            press(vm)
            press(vm, PlayerAction.SeekBackward)

            callbackSlot.captured.onPlaybackEnded()

            verify(exactly = 0) { router.back(any(), any()) }
            assertEquals(2_400_000L - 10_000, seeks.last().second)
            assertNoSeekAfter(2_400_000L - 10_000)
        }
    }

    @Test
    fun aPendingSeekDoesNotLandOnTheNextEpisode() = holdTest { vm ->
        repeat(2) { press(vm) }

        vm.onAction(PlayerAction.SelectEpisode(1, 2))
        advanceTimeBy(1_000)

        assertEquals(listOf(P + 10_000), seekTargets())
    }

    @Test
    fun theStepLadderRestartsAfterTheBurstEnds() = holdTest { vm ->
        repeat(3) { press(vm) }
        val before = contentState(vm).currentPosition
        advanceTimeBy(1_600)
        press(vm)

        assertEquals(before + STEP_10_S, contentState(vm).currentPosition)
    }

    @Test
    fun theStepLadderKeepsClimbingInsideTheBurst() = holdTest { vm ->
        press(vm, gapMs = 1_400)
        assertEquals(P + 10_000, contentState(vm).currentPosition)
        press(vm, gapMs = 1_400)
        assertEquals(P + 20_000, contentState(vm).currentPosition)
        press(vm, gapMs = 1_400)
        assertEquals(P + 40_000, contentState(vm).currentPosition)
    }
}
