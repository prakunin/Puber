package com.kino.puber.data.db

import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
        ).build()
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
