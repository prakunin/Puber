package com.kino.puber.ui.feature.player.vm

import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.tvhome.TvHomeSyncCoordinator
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * PlayerVM: the details stale mark and the Home row refresh happen once, at exit and at background,
 * not on every progress save.
 *
 * Cases that need the 30 s progress tracker use `runTest` on the shared scheduler and never
 * `advanceUntilIdle` (see [PlayerVMNextEpisodePrefetchTest]); the rest run without it.
 */
internal class PlayerVMInvalidationTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val coordinator = mockk<TvHomeSyncCoordinator>(relaxed = true)

    private fun startedWithCoordinator(): PlayerVM =
        createVM(tvHomeSyncCoordinator = coordinator).also { it.testOnStart() }

    private fun exit(vm: PlayerVM) {
        // The first back hides the controls the media starts with; the second leaves.
        vm.onAction(PlayerAction.OnBackPressed)
        vm.onAction(PlayerAction.OnBackPressed)
    }

    private fun TestScope.trackerSave() {
        advanceTimeBy(30_000)
        runCurrent()
    }

    private fun failEverySave() {
        coEvery { interactor.saveWatchingTime(any(), any(), any(), any()) } throws IllegalStateException("save failed")
    }

    private fun succeedOnceThenFail() {
        coEvery { interactor.saveWatchingTime(any(), any(), any(), any()) } returns Unit andThenThrows
            IllegalStateException("save failed")
    }

    private fun verifyMarks(count: Int) = coVerify(exactly = count) { interactor.markItemDetailsStale(42) }

    private fun verifyRefreshes(count: Int) = verify(exactly = count) { coordinator.requestRefresh(any()) }

    @Test
    fun playbackInFront_savesPrefetchAndEpisodeSwitch_markAndRefreshNothing() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            every { interactor.findNextEpisode(any(), any(), any()) } returns (1 to 2)
            val vm = startedWithCoordinator()

            try {
                trackerSave()
                trackerSave()
                trackerSave()
                callbackSlot.captured.onPlaybackEnded()
                vm.onAction(PlayerAction.SelectEpisode(1, 2))

                coVerify(atLeast = 3) { interactor.saveWatchingTime(any(), any(), any(), any()) }
                verifyMarks(0)
                verifyRefreshes(0)
            } finally {
                vm.testCancelScope()
            }
        }

    @Test
    fun backPressed_afterSuccessfulSaves_marksAndRefreshesOnceBeforeBack() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            val vm = startedWithCoordinator()

            try {
                trackerSave()
                trackerSave()

                exit(vm)

                verifyMarks(1)
                verifyRefreshes(1)
                coVerifyOrder {
                    interactor.markItemDetailsStale(42)
                    coordinator.requestRefresh(any())
                    router.back(RESULT_CONTENT_CHANGED, any())
                }
                verifyContentChangeResult(ContentChangeType.PlaybackProgress)
            } finally {
                vm.testCancelScope()
            }
        }

    @Test
    fun backPressed_requestsAWatchStateCatchUpAfterTheFlushBeforeBack() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            val vm = startedWithCoordinator()

            try {
                trackerSave()

                exit(vm)

                coVerifyOrder {
                    interactor.markItemDetailsStale(42)
                    interactor.requestWatchStateCatchUp()
                    router.back(RESULT_CONTENT_CHANGED, any())
                }
            } finally {
                vm.testCancelScope()
            }
        }

    @Test
    fun backPressed_withoutSuccessfulSave_stillRequestsACatchUp() {
        failEverySave()
        exit(startedWithCoordinator())

        verify(exactly = 1) { interactor.requestWatchStateCatchUp() }
    }

    @Test
    fun backPressed_withoutSuccessfulSave_marksAndRefreshesNothing() {
        failEverySave()
        exit(startedWithCoordinator())

        // The exit ran its flush and left; it just had nothing owed.
        verify(exactly = 1) { router.back(any(), any()) }
        verifyMarks(0)
        verifyRefreshes(0)
    }

    @Test
    fun clearedDuringBackgroundFlush_stillMarksAndRefreshes() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            val releaseSave = CompletableDeferred<Unit>()
            var saves = 0
            coEvery { interactor.saveWatchingTime(any(), any(), any(), any()) } coAnswers {
                // A request already on the wire finishes after the clear; it is not cancelled with the scope.
                if (saves++ > 0) withContext(NonCancellable) { releaseSave.await() }
            }
            val vm = startedWithCoordinator()

            trackerSave()
            vm.onAction(PlayerAction.OnBackground)
            verifyMarks(0)

            // A launcher row replaces the stack: the VM is cleared with the final save still out.
            vm.testCancelScope()
            releaseSave.complete(Unit)

            verifyMarks(1)
            verifyRefreshes(1)
        }

    @Test
    fun backPressed_markFailure_stillReturnsResult() =
        runTest(mainDispatcher.dispatcher.scheduler) {
            coEvery { interactor.markItemDetailsStale(any()) } throws IllegalStateException("mark failed")
            val vm = startedWithCoordinator()

            try {
                trackerSave()

                exit(vm)

                verify(exactly = 1) { router.back(any(), any()) }
                verifyContentChangeResult(ContentChangeType.PlaybackProgress)
                verifyRefreshes(1)
            } finally {
                vm.testCancelScope()
            }
        }

    @Test
    fun onBackground_marksOnlyAfterTheHeldSaveDrains() {
        val releaseSave = CompletableDeferred<Unit>()
        coEvery { interactor.saveWatchingTime(any(), any(), any(), any()) } coAnswers { releaseSave.await() }
        val vm = startedWithCoordinator()

        vm.onAction(PlayerAction.OnBackground)

        verifyMarks(0)
        verifyRefreshes(0)
        releaseSave.complete(Unit)
        verifyMarks(1)
        verifyRefreshes(1)
    }

    @Test
    fun backPressed_afterBackgroundFlush_withNoNewSuccessfulSave_marksAndRefreshesNothingMore() {
        succeedOnceThenFail()
        val vm = startedWithCoordinator()

        vm.onAction(PlayerAction.OnBackground)
        exit(vm)

        verifyMarks(1)
        verifyRefreshes(1)
        verify(exactly = 1) { router.back(any(), any()) }
    }

    @Test
    fun backPressed_whileBackgroundFlushPending_marksOnceBeforeBack() {
        succeedOnceThenFail()
        val releaseMark = CompletableDeferred<Unit>()
        coEvery { interactor.markItemDetailsStale(any()) } coAnswers { releaseMark.await() }
        val vm = startedWithCoordinator()

        vm.onAction(PlayerAction.OnBackground)
        exit(vm)

        verify(exactly = 0) { router.back(any(), any()) }
        releaseMark.complete(Unit)
        verify(exactly = 1) { router.back(any(), any()) }
        verifyMarks(1)
        verifyRefreshes(1)
    }

    @Test
    fun repeatedOnBackground_marksOnce() {
        succeedOnceThenFail()
        val vm = startedWithCoordinator()

        vm.onAction(PlayerAction.OnBackground)
        vm.onAction(PlayerAction.OnBackground)

        verifyMarks(1)
    }

    @Test
    fun watchedInFront_requestsNoRefreshUntilExit() {
        failEverySave()
        val manual = startedWithCoordinator()
        manual.onAction(PlayerAction.MarkCurrentWatched)
        verifyRefreshes(0)

        exit(manual)

        verifyRefreshes(1)

        val auto = startedWithCoordinator()
        callbackSlot.captured.onPlaybackEnded()
        verifyRefreshes(1)

        // The first back cancels the next-episode countdown the ended media started, the second
        // hides the controls, the third leaves; further backs while closing are swallowed.
        repeat(3) { auto.onAction(PlayerAction.OnBackPressed) }

        verifyRefreshes(2)
        verifyMarks(0)
    }

    @Test
    fun watchedInFront_backgroundRequestsOneRefresh() {
        failEverySave()
        val vm = startedWithCoordinator()
        vm.onAction(PlayerAction.MarkCurrentWatched)

        vm.onAction(PlayerAction.OnBackground)

        verifyRefreshes(1)
        verifyMarks(0)
    }
}
