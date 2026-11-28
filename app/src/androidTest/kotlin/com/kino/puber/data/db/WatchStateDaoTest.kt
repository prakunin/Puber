package com.kino.puber.data.db

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Runs the hand-written statements against real SQLite.
 *
 * The unit tests stand in for the DAO with a fake, so nothing there ever executes this SQL — which
 * is how an INSERT that skipped a NOT NULL column reached a device. Every statement here is exercised
 * on both paths it has: the insert, and the conflict.
 */
@RunWith(AndroidJUnit4::class)
class WatchStateDaoTest {

    private lateinit var database: PuberDatabase
    private lateinit var dao: WatchStateDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PuberDatabase::class.java,
        ).setDriver(AndroidSQLiteDriver()).build()
        dao = database.watchStateDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun serverRowsInsertAndThenUpdate() = runTest {
        dao.upsertAllFromServer(listOf(serverRow(itemId = 1, isFullyWatched = false, updatedAt = 100)))
        dao.upsertAllFromServer(listOf(serverRow(itemId = 1, isFullyWatched = true, updatedAt = 200)))

        assertTrue(dao.get(1)!!.isFullyWatched)
    }

    @Test
    fun aServerRowReadBeforeTheStoredOneDoesNotLand() = runTest {
        dao.upsertAllFromServer(listOf(serverRow(itemId = 2, isFullyWatched = true, updatedAt = 200)))
        dao.upsertAllFromServer(listOf(serverRow(itemId = 2, isFullyWatched = false, updatedAt = 100)))

        assertTrue(dao.get(2)!!.isFullyWatched)
    }

    @Test
    fun aPendingRowIsLeftAloneByASync() = runTest {
        dao.upsert(
            listOf(
                WatchStateEntity(
                    itemId = 3,
                    isSeriesLike = false,
                    isFullyWatched = true,
                    updatedAt = 100,
                    isLocalPending = true,
                )
            )
        )

        dao.upsertAllFromServer(listOf(serverRow(itemId = 3, isFullyWatched = false, updatedAt = 200)))

        assertTrue(dao.get(3)!!.isFullyWatched)
    }

    @Test
    fun aServerRowEqualToTheStoredOneIsNotRewritten() = runTest {
        val withValues = serverRow(itemId = 11, isFullyWatched = false, updatedAt = 100).copy(
            progressTime = 30,
            progressDuration = 120,
        )
        val withNulls = serverRow(itemId = 12, isFullyWatched = false, updatedAt = 100).copy(
            watchedEpisodes = null,
            totalEpisodes = null,
        )
        dao.upsertAllFromServer(listOf(withValues, withNulls))

        val emissions = withContext(Dispatchers.Default) {
            var seen = 0
            val first = CompletableDeferred<Unit>()
            val collector = async {
                dao.observeAll().collect {
                    seen += 1
                    first.complete(Unit)
                }
            }
            first.await()
            dao.upsertAllFromServer(listOf(withValues, withNulls))
            delay(SETTLE_MILLIS)
            collector.cancel()
            seen
        }

        assertEquals(1, emissions)
    }

    @Test
    fun aServerRowDifferingInAnyWrittenColumnLands() = runTest {
        val stored = serverRow(itemId = 13, isFullyWatched = false, updatedAt = 100).copy(
            progressTime = 30,
            progressDuration = 120,
        )
        val variants = listOf(
            "is_series_like" to stored.copy(isSeriesLike = !stored.isSeriesLike),
            "is_fully_watched" to stored.copy(isFullyWatched = !stored.isFullyWatched),
            "watched_episodes" to stored.copy(watchedEpisodes = 4),
            "watched_episodes to null" to stored.copy(watchedEpisodes = null),
            "total_episodes" to stored.copy(totalEpisodes = 11),
            "total_episodes to null" to stored.copy(totalEpisodes = null),
            "progress_time" to stored.copy(progressTime = 31),
            "progress_time to null" to stored.copy(progressTime = null),
            "progress_duration" to stored.copy(progressDuration = 121),
            "progress_duration to null" to stored.copy(progressDuration = null),
            "updated_at" to stored.copy(updatedAt = 101),
            "generation" to stored.copy(generation = stored.generation + 1),
        )
        variants.forEach { (column, incoming) ->
            dao.upsert(listOf(stored))
            dao.upsertAllFromServer(listOf(incoming))
            assertEquals(column, incoming, dao.get(stored.itemId))
        }
        val fromNull = stored.copy(itemId = 14, progressTime = null, progressDuration = null)
        dao.upsert(listOf(fromNull))
        val filled = fromNull.copy(progressTime = 30, progressDuration = 120)
        dao.upsertAllFromServer(listOf(filled))
        assertEquals("null to value", filled, dao.get(14))
    }

    @Test
    fun historyRowsInsertAndKeepTheNewestEntry() = runTest {
        dao.upsertAllFromHistory(listOf(historyRow(itemId = 4, isFullyWatched = true, historySeenAt = 900)))
        dao.upsertAllFromHistory(listOf(historyRow(itemId = 4, isFullyWatched = false, historySeenAt = 100)))

        assertTrue(dao.get(4)!!.isFullyWatched)
    }

    @Test
    fun aServerWriteKeepsTheHistoryBookmarkOfTheRow() = runTest {
        dao.upsertAllFromHistory(listOf(historyRow(itemId = 5, isFullyWatched = true, historySeenAt = 900)))

        dao.upsertAllFromServer(listOf(serverRow(itemId = 5, isFullyWatched = false, updatedAt = 200)))

        assertEquals(900L, dao.get(5)!!.historySeenAt)
    }

    @Test
    fun markInProgressInsertsAndThenClearsTheFinishedFlag() = runTest {
        dao.markInProgress(generation = 1, itemId = 6, isSeriesLike = false, updatedAt = 100)
        assertFalse(dao.get(6)!!.isFullyWatched)

        dao.upsertAllFromServer(listOf(serverRow(itemId = 6, isFullyWatched = true, updatedAt = 200)))
        dao.markInProgress(generation = 1, itemId = 6, isSeriesLike = false, updatedAt = 300)

        assertFalse(dao.get(6)!!.isFullyWatched)
    }

    @Test
    fun markInProgressKeepsTheProgressAlreadyStored() = runTest {
        dao.upsertAllFromHistory(
            listOf(historyRow(itemId = 7, isFullyWatched = false, historySeenAt = 100, progressTime = 30))
        )

        dao.markInProgress(generation = 1, itemId = 7, isSeriesLike = false, updatedAt = 200)

        assertEquals(30, dao.get(7)!!.progressTime)
    }

    @Test
    fun markInProgressInOneTransactionEmitsNoPartialList() = runTest {
        val ids = setOf(21, 22, 23)

        val emissions = collectWhile {
            database.transactions().run {
                ids.forEach { markInProgress(it) }
            }
        }

        assertTrue(emissions.none { it.isNotEmpty() && ids.containsAll(it) && it != ids })
        assertEquals(ids, emissions.last())
    }

    /**
     * Control case of the test above: the same writes as separate commits, paused between, must show
     * a partial list. Without it a pass of the test above would not prove that the outer transaction
     * is what holds the invalidation back.
     */
    @Test
    fun markInProgressInSeparateCommitsEmitsAPartialList() = runTest {
        val ids = setOf(31, 32, 33)

        val emissions = collectWhile {
            ids.forEach {
                markInProgress(it)
                delay(PAUSE_MILLIS)
            }
        }

        assertTrue(emissions.any { it.isNotEmpty() && ids.containsAll(it) && it != ids })
    }

    @Test
    fun pruningDropsOlderPassesButKeepsPendingMarks() = runTest {
        dao.upsertAllFromServer(
            listOf(serverRow(itemId = 8, isFullyWatched = true, updatedAt = 100).copy(generation = 1))
        )
        dao.upsertAllFromServer(
            listOf(serverRow(itemId = 9, isFullyWatched = true, updatedAt = 100).copy(generation = 2))
        )
        dao.upsert(
            listOf(
                WatchStateEntity(
                    itemId = 10,
                    isSeriesLike = false,
                    isFullyWatched = true,
                    updatedAt = 100,
                    isLocalPending = true,
                    generation = 1,
                )
            )
        )

        dao.pruneOlderThan(generation = 2)

        assertEquals(null, dao.get(8))
        assertEquals(9, dao.get(9)!!.itemId)
        assertEquals(10, dao.get(10)!!.itemId)
    }

    private companion object {
        const val SETTLE_MILLIS = 1_000L
        const val PAUSE_MILLIS = 200L
    }

    private suspend fun markInProgress(itemId: Int) =
        dao.markInProgress(generation = 1, itemId = itemId, isSeriesLike = false, updatedAt = 100)

    /** Runs [writes] while collecting every item-id set `observeAll` emits, then lets the flow settle. */
    private suspend fun collectWhile(writes: suspend () -> Unit): List<Set<Int>> =
        withContext(Dispatchers.Default) {
            val seen = CopyOnWriteArrayList<Set<Int>>()
            val first = CompletableDeferred<Unit>()
            val collector = async {
                dao.observeAll().collect { rows ->
                    seen += rows.map { it.itemId }.toSet()
                    first.complete(Unit)
                }
            }
            first.await()
            writes()
            delay(SETTLE_MILLIS)
            collector.cancel()
            seen.toList()
        }

    @Test
    fun raiseHistoryNewestSeenNeverLowersTheMark() = runTest {
        val syncDao = database.watchStateSyncDao()
        syncDao.upsert(WatchStateSyncEntity(historyNewestSeen = 900))

        syncDao.raiseHistoryNewestSeen(800)
        assertEquals(900L, syncDao.get()!!.historyNewestSeen)

        syncDao.raiseHistoryNewestSeen(950)
        assertEquals(950L, syncDao.get()!!.historyNewestSeen)
    }

    private fun serverRow(itemId: Int, isFullyWatched: Boolean, updatedAt: Long) = WatchStateEntity(
        itemId = itemId,
        isSeriesLike = true,
        isFullyWatched = isFullyWatched,
        watchedEpisodes = 3,
        totalEpisodes = 10,
        updatedAt = updatedAt,
    )

    private fun historyRow(
        itemId: Int,
        isFullyWatched: Boolean,
        historySeenAt: Long,
        progressTime: Int? = null,
    ) = WatchStateEntity(
        itemId = itemId,
        isSeriesLike = false,
        isFullyWatched = isFullyWatched,
        progressTime = progressTime,
        progressDuration = progressTime?.let { 120 },
        updatedAt = historySeenAt,
        historySeenAt = historySeenAt,
    )
}
