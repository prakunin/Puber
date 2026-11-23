package com.kino.puber.data.di

import com.kino.puber.core.startup.unsettledStartup
import com.kino.puber.data.db.DatabaseTransaction
import com.kino.puber.data.db.WatchStateDao
import com.kino.puber.data.db.WatchStateEntity
import com.kino.puber.data.db.WatchStateSyncDao
import com.kino.puber.data.repository.WatchStateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal class WatchStateRepositoryModuleTest {

    @Test
    fun repositoryFromTheModuleDefersCleanupUntilStartupSettles() {
        val cleared = CompletableDeferred<Unit>()
        val dao = mockk<WatchStateDao>()
        every { dao.observeAll() } returns flowOf(emptyList())
        coEvery { dao.clearAllPending() } coAnswers { cleared.complete(Unit) }
        val syncDao = mockk<WatchStateSyncDao>()
        every { syncDao.observe(any()) } returns flowOf(null)
        val startupScope = CoroutineScope(SupervisorJob())
        val startup = unsettledStartup(startupScope)
        val application = koinApplication {
            modules(
                repositoryModule,
                module {
                    single { dao }
                    single { syncDao }
                    single<DatabaseTransaction> { DatabaseTransaction.Direct }
                    single { startup }
                },
            )
        }

        try {
            application.koin.get<WatchStateRepository>()

            // The repository's own scope runs on IO, so the absence of the call is observed over a
            // short real-time window rather than a scheduler pass.
            assertNull(runBlocking { withTimeoutOrNull(UNSETTLED_WINDOW_MILLIS) { cleared.await() } })
            coVerify(exactly = 0) { dao.clearAllPending() }

            startup.onActivityCreated(restored = true)

            coVerify(timeout = SETTLE_TIMEOUT_MILLIS) { dao.clearAllPending() }
        } finally {
            application.close()
            startupScope.cancel()
        }
    }

    @Test
    fun repositoryFromTheModuleAdvancesVersionWhenTheSnapshotChanges() {
        val rows = MutableStateFlow(emptyList<WatchStateEntity>())
        val dao = mockk<WatchStateDao>()
        every { dao.observeAll() } returns rows
        coEvery { dao.clearAllPending() } returns Unit
        val syncDao = mockk<WatchStateSyncDao>()
        every { syncDao.observe(any()) } returns flowOf(null)
        val startupScope = CoroutineScope(SupervisorJob())
        val application = koinApplication {
            modules(
                repositoryModule,
                module {
                    single { dao }
                    single { syncDao }
                    single<DatabaseTransaction> { DatabaseTransaction.Direct }
                    single { unsettledStartup(startupScope) }
                },
            )
        }

        try {
            val repository = application.koin.get<WatchStateRepository>()
            val before = runBlocking {
                withTimeoutOrNull(SETTLE_TIMEOUT_MILLIS) { repository.version.first { it > 0L } }
            }
            val start = repository.version.value

            // The default window clock is Android's SystemClock, a stub on the JVM: an index
            // collector that reads it on every emission dies on the first one and never ticks again.
            rows.value = listOf(
                WatchStateEntity(itemId = 1, isSeriesLike = false, isFullyWatched = true, updatedAt = 1L),
            )

            val after = runBlocking {
                withTimeoutOrNull(SETTLE_TIMEOUT_MILLIS) { repository.version.first { it > start } }
            }
            assertTrue(before != null && after != null) { "version stayed at $start after the snapshot changed" }
        } finally {
            application.close()
            startupScope.cancel()
        }
    }

    private companion object {
        const val UNSETTLED_WINDOW_MILLIS = 300L
        const val SETTLE_TIMEOUT_MILLIS = 5_000L
    }
}
