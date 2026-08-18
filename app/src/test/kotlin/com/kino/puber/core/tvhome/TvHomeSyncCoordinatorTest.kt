package com.kino.puber.core.tvhome

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class TvHomeSyncCoordinatorTest {
    @Test
    fun failedLoadDoesNotReplacePublishedPrograms() = runTest {
        val source = mockk<ContinueWatchingSource>()
        val publisher = mockk<TvHomePublisher>(relaxed = true)
        coEvery { source.load() } returns Result.failure(IllegalStateException("offline"))
        val coordinator = TvHomeSyncCoordinator(source, publisher, this)

        coordinator.refreshNow()

        coVerify(exactly = 0) { publisher.reconcile(any()) }
    }

    @Test
    fun successfulEmptyLoadClearsProgramsThroughReconciliation() = runTest {
        val source = mockk<ContinueWatchingSource>()
        val publisher = mockk<TvHomePublisher>(relaxed = true)
        coEvery { source.load() } returns Result.success(emptyList())
        val coordinator = TvHomeSyncCoordinator(source, publisher, this)

        coordinator.refreshNow()

        coVerify(exactly = 1) { publisher.reconcile(emptyList()) }
    }

    @Test
    fun logoutClearsAccountPrograms() = runTest {
        val source = mockk<ContinueWatchingSource>()
        val publisher = mockk<TvHomePublisher>(relaxed = true)
        val coordinator = TvHomeSyncCoordinator(source, publisher, this)

        coordinator.clearAccountPrograms()

        coVerify(exactly = 1) { publisher.clearAccountPrograms() }
    }
}
