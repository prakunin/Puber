package com.kino.puber.ui.feature.player.vm

import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.ui.feature.player.model.PlayerCountdowns
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * A prompt to skip the credits is only worth putting up when skipping them leads somewhere.
 *
 * On an episode with another one behind it the credits belong to the next-episode countdown, and
 * the skip prompt deliberately stands aside. With nothing behind them — a film, or the last episode
 * of a series — the prompt used to appear anyway, on the same plate in the same place as the
 * next-episode countdown, and skipping landed the viewer on the closing frame.
 */
internal class PlayerVMCreditsPromptTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    /** The fixture's media runs forty minutes. */
    private val duration = 2_400_000L

    private var position = 0L

    private fun playingInto(segment: SkipSegment, from: Long) {
        position = from
        every { playbackController.currentPosition } answers { position }
        every { skipSegmentInteractor.findActiveSegment(any(), any(), any()) } answers {
            segment.takeIf {
                position >= it.startMs - PlayerCountdowns.PROMPT_LEAD_IN_MS && position <= it.endMs!!
            }
        }
    }

    private fun watching(isMovie: Boolean, hasNextEpisode: Boolean) {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns
            testContentState.copy(isMovie = isMovie, hasNextEpisode = hasNextEpisode)
    }

    /** Playback as the position tick sees it, half a second of media per half second. */
    private fun TestScope.playFor(seconds: Double) {
        repeat((seconds * 2).toInt()) {
            position += PlayerCountdowns.TICK_MS / 2
            advanceTimeBy(PlayerCountdowns.TICK_MS / 2)
            runCurrent()
        }
    }

    /** Credits that run to the last frame: there is nothing on the other side of them. */
    private fun creditsToTheEnd() = SkipSegment(
        type = SkipSegmentType.CREDITS,
        startMs = 2_350_000,
        endMs = duration,
    )

    @Test
    fun theLastEpisodeOfASeriesIsOfferedNoCreditsSkip() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            watching(isMovie = false, hasNextEpisode = false)
            val credits = creditsToTheEnd()
            playingInto(credits, from = credits.startMs - 2_500)
            val vm = startedVM()

            playFor(1.0)

            assertNull(contentState(vm).activeSkipSegment)
            vm.testCancelScope()
        }

    @Test
    fun aFilmIsOfferedNoCreditsSkip() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            watching(isMovie = true, hasNextEpisode = false)
            val credits = creditsToTheEnd()
            playingInto(credits, from = credits.startMs - 2_500)
            val vm = startedVM()

            playFor(1.0)

            assertNull(contentState(vm).activeSkipSegment)
            vm.testCancelScope()
        }

    @Test
    fun anEpisodeWithAnotherBehindItLeavesTheCreditsToTheNextEpisodeCountdown() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            watching(isMovie = false, hasNextEpisode = true)
            val credits = creditsToTheEnd()
            playingInto(credits, from = credits.startMs - 2_500)
            val vm = startedVM()

            playFor(1.0)

            assertNull(contentState(vm).activeSkipSegment)
            vm.testCancelScope()
        }

    @Test
    fun creditsWithASceneAfterThemKeepTheirSkip() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            watching(isMovie = true, hasNextEpisode = false)
            // Five minutes of credits, then a minute and a half of film left after them.
            val credits = SkipSegment(
                type = SkipSegmentType.CREDITS,
                startMs = 2_000_000,
                endMs = 2_300_000,
            )
            playingInto(credits, from = credits.startMs - 2_500)
            val vm = startedVM()

            playFor(1.0)

            assertNotNull(contentState(vm).activeSkipSegment)
            vm.testCancelScope()
        }

    @Test
    fun anIntroOnAFilmKeepsItsSkip() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            watching(isMovie = true, hasNextEpisode = false)
            val intro = SkipSegment(type = SkipSegmentType.INTRO, startMs = 10_000, endMs = 40_000)
            playingInto(intro, from = intro.startMs - 2_500)
            val vm = startedVM()

            playFor(1.0)

            assertNotNull(contentState(vm).activeSkipSegment)
            vm.testCancelScope()
        }
}
