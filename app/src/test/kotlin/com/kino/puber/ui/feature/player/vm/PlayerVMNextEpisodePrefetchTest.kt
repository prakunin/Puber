package com.kino.puber.ui.feature.player.vm

import com.kino.puber.domain.interactor.player.StreamCandidate
import com.kino.puber.domain.interactor.player.StreamType
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.every
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * PlayerVM: pulling the next episode's stream into the media cache before the switch.
 *
 * No `runTest` — see the note on [PlayerVMPlaybackFlowTest].
 */
internal class PlayerVMNextEpisodePrefetchTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val nextEpisodeStream = StreamCandidate("https://test/next.m3u8", StreamType.HLS)

    private fun expectNextEpisode() {
        every { interactor.findNextEpisode(any(), any(), any()) } returns (1 to 2)
        every { interactor.selectStreamCandidates(any(), any()) } returns listOf(nextEpisodeStream)
    }

    @Test
    fun countdownStart_warmsUpTheNextEpisodeStream() {
        expectNextEpisode()
        startedVM()

        callbackSlot.captured.onPlaybackEnded()

        verify { playbackController.warmUpNext(nextEpisodeStream, any()) }
    }

    @Test
    fun warmsUpTheSameEpisodeOnlyOnce_evenWhenThePromptComesBack() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            expectNextEpisode()
            every { playbackController.currentPosition } returns 100_000L
            val vm = startedVM()
            callbackSlot.captured.onPlaybackEnded()
            vm.onAction(PlayerAction.CancelNextEpisodeCountdown)

            // Seeking puts the question back on the table, so the prompt returns...
            every { playbackController.currentPosition } returns 20_000L
            advanceTimeBy(1_200)
            callbackSlot.captured.onPlaybackEnded()
            assertNotNull(contentState(vm).nextEpisodeCountdown)

            // ...but the episode behind it is the one already in the cache.
            verify(exactly = 1) { playbackController.warmUpNext(any(), any()) }
            vm.testCancelScope()
        }

    @Test
    fun lastEpisode_doesNotWarmUpAnything() {
        every { interactor.findNextEpisode(any(), any(), any()) } returns null
        startedVM()

        callbackSlot.captured.onPlaybackEnded()

        verify(exactly = 0) { playbackController.warmUpNext(any(), any()) }
    }
}
