package com.kino.puber.data.repository

import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.api.models.WatchingInfo
import com.kino.puber.data.db.DatabaseTransaction
import com.kino.puber.data.db.ItemLastWatched
import com.kino.puber.data.db.WatchStateDao
import com.kino.puber.data.db.WatchStateSyncDao
import com.kino.puber.data.db.WatchStateSyncEntity
import com.kino.puber.data.db.WatchStateEntity
import com.kino.puber.util.FakeResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Suppress("LargeClass")
class WatchStateRepositoryTest {

    /** Enough chunks that "one change per chunk" and "one per walk" cannot be confused. */
    private val chunksInAWalk = 3

    private val dao = FakeWatchStateDao()
    private val syncDao = FakeWatchStateSyncDao()
    private var now = 42L
    private var windowNow = 42L

    /** An item carrying watch fields, which is what makes the index actually record a row for it. */
    private fun watchingItem(id: Int) = Item(
        id = id,
        title = "Watching item $id",
        type = ItemType.SERIAL,
        watched = 3,
        new = 2,
        total = 10,
    )

    private fun repository(
        scope: CoroutineScope,
        awaitStartupSettled: suspend () -> Unit = {},
    ) = WatchStateRepository(
        dao = dao,
        syncDao = syncDao,
        transaction = DatabaseTransaction.Direct,
        clock = { now },
        windowClock = { windowNow },
        scope = scope,
        awaitStartupSettled = awaitStartupSettled,
    )

    /**
     * The repository shares its snapshot eagerly, so the collector has to be running before the
     * first read. Unconfined starts it at construction instead of on the next scheduler pass.
     */
    private fun TestScope.eagerScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    @Test
    fun recordFromServer_skipsItemsWithoutAnyWatchFields() = runTest {
        val repository = repository(eagerScope())

        repository.recordFromServer(
            listOf(
                Item(id = 1, title = "Catalogue item", type = ItemType.SERIAL),
                Item(id = 2, title = "Watching item", type = ItemType.SERIAL, watched = 3, new = 2, total = 10),
            )
        )

        assertEquals(listOf(2), dao.rows.value.map { it.itemId })
    }

    @Test
    fun syncCursorState_tracksCursorWritesAndClear() = runTest {
        val repository = repository(eagerScope())
        val cursor = WatchStateSyncCursor(
            lastSyncAt = 123L,
            fullHistoryWalkDone = true,
            historyResumePage = 7,
        )

        repository.saveSyncCursor(cursor)

        assertEquals(cursor, repository.syncCursorState.value)

        repository.clear()

        assertEquals(WatchStateSyncCursor(), repository.syncCursorState.value)
    }

    /**
     * A walk writes a chunk at a time with a long pause between them — far longer than the settle
     * debounce, so left to itself every chunk would settle separately and cost each open screen a
     * re-map or a re-page. Held to one change, a walk costs what a single sync always did.
     */
    @Test
    fun syncWindow_holdsAWalksOwnWritesToOneChange() = runTest {
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()
        val beforeWalk = repository.version.value

        repository.beginSyncWindow(SyncWindowKind.WALK)
        repeat(chunksInAWalk) { chunk ->
            repository.recordFromServer(listOf(watchingItem(id = chunk + 1)))
            testScheduler.advanceUntilIdle()
        }
        val duringWalk = repository.version.value
        repository.endSyncWindow(SyncWindowKind.WALK)
        testScheduler.advanceUntilIdle()

        assertEquals(beforeWalk, duringWalk)
        assertEquals(beforeWalk + 1, repository.version.value)
    }

    /** A catch-up inside a walk's window must not leave its change waiting for the walk's end. */
    @Test
    fun syncWindow_publishesACatchUpsChangeWhileAWalksWindowIsStillOpen() = runTest {
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()
        val beforeCatchUp = repository.version.value

        repository.beginSyncWindow(SyncWindowKind.WALK)
        repository.beginSyncWindow(SyncWindowKind.CATCH_UP)
        repository.recordCatchUpPage(
            listOf(historyEntry(itemId = 9, type = ItemType.MOVIE, time = 50, duration = 100)),
            emptySet(),
            now,
            newestSeen = 1_000L,
        )
        repository.endSyncWindow(SyncWindowKind.CATCH_UP)
        testScheduler.advanceUntilIdle()
        val whileWalkIsOpen = repository.version.value
        repository.endSyncWindow(SyncWindowKind.WALK)

        assertEquals(beforeCatchUp + 1, whileWalkIsOpen)
    }

    /**
     * Room publishes after the write commits, so a catch-up's window can close before the emission
     * that carries its rows. That emission must still go out at once, not wait for the walk.
     */
    @Test
    fun syncWindow_publishesACatchUpsChangeThatRoomEmitsAfterItsWindowClosed() = runTest {
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()
        val beforeCatchUp = repository.version.value

        repository.beginSyncWindow(SyncWindowKind.WALK)
        repository.beginSyncWindow(SyncWindowKind.CATCH_UP)
        dao.emissionsFlowing.value = false
        repository.recordCatchUpPage(
            listOf(historyEntry(itemId = 9, type = ItemType.MOVIE, time = 50, duration = 100)),
            emptySet(),
            now,
            newestSeen = 1_000L,
        )
        repository.endSyncWindow(SyncWindowKind.CATCH_UP)
        testScheduler.advanceUntilIdle()
        val beforeEmission = repository.version.value
        dao.emissionsFlowing.value = true
        testScheduler.advanceUntilIdle()
        val whileWalkIsOpen = repository.version.value
        repository.endSyncWindow(SyncWindowKind.WALK)

        assertEquals(beforeCatchUp, beforeEmission)
        assertEquals(beforeCatchUp + 1, whileWalkIsOpen)
    }

    /**
     * A catch-up that changed nothing pays out nothing, and what the walk holds stays held for the
     * walk's own end.
     */
    @Test
    fun syncWindow_aCatchUpThatChangedNothingDoesNotPayOutTheWalksHeldChange() = runTest {
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()
        val beforeWalk = repository.version.value

        repository.beginSyncWindow(SyncWindowKind.WALK)
        repository.recordFromServer(listOf(watchingItem(id = 1)))
        testScheduler.advanceUntilIdle()
        repository.beginSyncWindow(SyncWindowKind.CATCH_UP)
        repository.endSyncWindow(SyncWindowKind.CATCH_UP)
        testScheduler.advanceUntilIdle()
        val afterEmptyCatchUp = repository.version.value
        repository.endSyncWindow(SyncWindowKind.WALK)
        testScheduler.advanceUntilIdle()

        assertEquals(beforeWalk, afterEmptyCatchUp)
        assertEquals(beforeWalk + 1, repository.version.value)
    }

    /** Ownership is by kind: a catch-up window opened before the walk's pays out its own change. */
    @Test
    fun syncWindow_publishesACatchUpsChangeWhenItsWindowOpenedBeforeTheWalks() = runTest {
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()
        val beforeCatchUp = repository.version.value

        repository.beginSyncWindow(SyncWindowKind.CATCH_UP)
        repository.recordCatchUpPage(
            listOf(historyEntry(itemId = 9, type = ItemType.MOVIE, time = 50, duration = 100)),
            emptySet(),
            now,
            newestSeen = 1_000L,
        )
        testScheduler.advanceUntilIdle()
        repository.beginSyncWindow(SyncWindowKind.WALK)
        repository.endSyncWindow(SyncWindowKind.CATCH_UP)
        testScheduler.advanceUntilIdle()
        val whileWalkIsOpen = repository.version.value
        repository.endSyncWindow(SyncWindowKind.WALK)

        assertEquals(beforeCatchUp + 1, whileWalkIsOpen)
    }

    /**
     * A catch-up writes more than once, so the emission of its last write can land after its window
     * closed and already paid a tick for the first. That one must not be held with the walk.
     */
    @Test
    fun syncWindow_publishesALateLastEmissionAfterTheCatchUpAlreadyPaidATick() = runTest {
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()
        val beforeCatchUp = repository.version.value

        repository.beginSyncWindow(SyncWindowKind.WALK)
        repository.beginSyncWindow(SyncWindowKind.CATCH_UP)
        repository.recordCatchUpPage(
            listOf(historyEntry(itemId = 9, type = ItemType.MOVIE, time = 50, duration = 100)),
            emptySet(),
            now,
            newestSeen = 1_000L,
        )
        testScheduler.advanceUntilIdle()
        dao.emissionsFlowing.value = false
        repository.recordFromServer(listOf(watchingItem(id = 2)))
        repository.endSyncWindow(SyncWindowKind.CATCH_UP)
        testScheduler.advanceUntilIdle()
        dao.emissionsFlowing.value = true
        testScheduler.advanceUntilIdle()
        val whileWalkIsOpen = repository.version.value
        repository.endSyncWindow(SyncWindowKind.WALK)

        assertEquals(beforeCatchUp + 2, whileWalkIsOpen)
    }

    @Test
    fun syncWindow_publishesTheCatchUpsLateEmissionWhenAWalkOpensRightAfterItClosed() = runTest {
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()
        val beforeCatchUp = repository.version.value

        repository.beginSyncWindow(SyncWindowKind.CATCH_UP)
        dao.emissionsFlowing.value = false
        repository.recordCatchUpPage(
            listOf(historyEntry(itemId = 9, type = ItemType.MOVIE, time = 50, duration = 100)),
            emptySet(),
            now,
            newestSeen = 1_000L,
        )
        repository.endSyncWindow(SyncWindowKind.CATCH_UP)
        repository.beginSyncWindow(SyncWindowKind.WALK)
        dao.emissionsFlowing.value = true
        testScheduler.advanceUntilIdle()
        val whileWalkIsOpen = repository.version.value
        repository.endSyncWindow(SyncWindowKind.WALK)

        assertEquals(beforeCatchUp + 1, whileWalkIsOpen)
    }

    /**
     * A catch-up that wrote nothing leaves no emission to own: once its window has passed, a walk's
     * first changing chunk is held like any other.
     */
    @Test
    fun syncWindow_aNoChangeCatchUpDoesNotLetAWalksFirstChunkThrough() = runTest {
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()
        val beforeCatchUp = repository.version.value

        repository.beginSyncWindow(SyncWindowKind.CATCH_UP)
        repository.endSyncWindow(SyncWindowKind.CATCH_UP)
        windowNow += 10_000L
        repository.beginSyncWindow(SyncWindowKind.WALK)
        repository.recordFromServer(listOf(watchingItem(id = 1)))
        testScheduler.advanceUntilIdle()
        val whileWalkIsOpen = repository.version.value
        repository.endSyncWindow(SyncWindowKind.WALK)
        testScheduler.advanceUntilIdle()

        assertEquals(beforeCatchUp, whileWalkIsOpen)
        assertEquals(beforeCatchUp + 1, repository.version.value)
    }

    /** The window is measured on its own monotonic clock: the wall clock stepping back must not stretch it. */
    @Test
    fun syncWindow_aWallClockStepBackDoesNotKeepTheCatchUpWindowOpen() = runTest {
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()
        val beforeCatchUp = repository.version.value

        repository.beginSyncWindow(SyncWindowKind.CATCH_UP)
        repository.endSyncWindow(SyncWindowKind.CATCH_UP)
        now -= 10_000L
        windowNow += 10_000L
        repository.beginSyncWindow(SyncWindowKind.WALK)
        repository.recordFromServer(listOf(watchingItem(id = 1)))
        testScheduler.advanceUntilIdle()
        val whileWalkIsOpen = repository.version.value
        repository.endSyncWindow(SyncWindowKind.WALK)
        testScheduler.advanceUntilIdle()

        assertEquals(beforeCatchUp, whileWalkIsOpen)
        assertEquals(beforeCatchUp + 1, repository.version.value)
    }

    /**
     * A catch-up that already paid a tick for what it held still owns its last in-flight emission
     * when a walk opens right after it closed.
     */
    @Test
    fun syncWindow_publishesTheLastEmissionOfACatchUpThatHeldAChangeWhenAWalkOpensRightAfter() = runTest {
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()
        val beforeCatchUp = repository.version.value

        repository.beginSyncWindow(SyncWindowKind.CATCH_UP)
        repository.recordCatchUpPage(
            listOf(historyEntry(itemId = 9, type = ItemType.MOVIE, time = 50, duration = 100)),
            emptySet(),
            now,
            newestSeen = 1_000L,
        )
        testScheduler.advanceUntilIdle()
        dao.emissionsFlowing.value = false
        repository.recordFromServer(listOf(watchingItem(id = 2)))
        repository.endSyncWindow(SyncWindowKind.CATCH_UP)
        repository.beginSyncWindow(SyncWindowKind.WALK)
        dao.emissionsFlowing.value = true
        testScheduler.advanceUntilIdle()
        val whileWalkIsOpen = repository.version.value
        repository.endSyncWindow(SyncWindowKind.WALK)

        assertEquals(beforeCatchUp + 2, whileWalkIsOpen)
    }

    /** A catch-up that found nothing new has nothing to announce, whatever window it ran in. */
    @Test
    fun syncWindow_announcesNothingForACatchUpThatChangedNothing() = runTest {
        val repository = repository(eagerScope())
        val entries = listOf(historyEntry(itemId = 9, type = ItemType.MOVIE, time = 50, duration = 100))
        repository.recordCatchUpPage(entries, emptySet(), now, newestSeen = 1_000L)
        testScheduler.advanceUntilIdle()
        val beforeCatchUp = repository.version.value

        repository.beginSyncWindow(SyncWindowKind.CATCH_UP)
        repository.recordCatchUpPage(entries, emptySet(), now, newestSeen = 1_000L)
        repository.endSyncWindow(SyncWindowKind.CATCH_UP)
        testScheduler.advanceUntilIdle()

        assertEquals(beforeCatchUp, repository.version.value)
    }

    @Test
    fun recordCatchUpPage_writesTheRowsAndRaisesTheMarkTogether() = runTest {
        val repository = repository(eagerScope())
        syncDao.upsert(WatchStateSyncEntity(historyNewestSeen = 500L))

        repository.recordCatchUpPage(
            listOf(historyEntry(itemId = 9, type = ItemType.MOVIE, time = 50, duration = 100)),
            emptySet(),
            now,
            newestSeen = 1_000L,
        )

        assertEquals(listOf(9), dao.rows.value.map { it.itemId })
        assertEquals(1_000L, repository.syncCursor().historyNewestSeen)
    }

    @Test
    fun recordCatchUpPage_rowsCarryTheCurrentGenerationAndSurviveAPrune() = runTest {
        val repository = repository(eagerScope())
        syncDao.upsert(WatchStateSyncEntity(generation = 3L))

        repository.recordCatchUpPage(
            listOf(historyEntry(itemId = 9, type = ItemType.MOVIE, time = 50, duration = 100)),
            emptySet(),
            now,
            newestSeen = 1_000L,
        )
        repository.pruneStaleRows(3L)

        assertEquals(listOf(9), dao.rows.value.map { it.itemId })
        assertEquals(3L, dao.rows.value.single().generation)
    }

    @Test
    fun saveSyncCursor_neverLowersTheHistoryMark() = runTest {
        val repository = repository(eagerScope())
        repository.saveSyncCursor(WatchStateSyncCursor(historyNewestSeen = 900L))

        repository.saveSyncCursor(WatchStateSyncCursor(historyNewestSeen = 800L, historyResumePage = 4))

        val stored = repository.syncCursor()
        assertEquals(900L, stored.historyNewestSeen)
        assertEquals(4, stored.historyResumePage)
    }

    /** A walk that wrote nothing has nothing to announce when it ends. */
    @Test
    fun syncWindow_announcesNothingForAWalkThatWroteNothing() = runTest {
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()
        val beforeWalk = repository.version.value

        repository.beginSyncWindow(SyncWindowKind.WALK)
        repository.endSyncWindow(SyncWindowKind.WALK)
        testScheduler.advanceUntilIdle()

        assertEquals(beforeWalk, repository.version.value)
    }

    /**
     * The window is for the server rebuilding the index behind the user's back. What the user does
     * to it is something they are watching for, and a walk in flight can last minutes — so their own
     * mark must not be held behind it.
     */
    @Test
    fun syncWindow_stillPublishesTheUsersOwnMarkAtOnce() = runTest {
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()
        repository.beginSyncWindow(SyncWindowKind.WALK)
        repository.recordFromServer(listOf(watchingItem(id = 1)))
        testScheduler.advanceUntilIdle()
        val beforeMark = repository.version.value

        repository.markLocally(itemId = 7, isSeriesLike = false, isFullyWatched = true)
        testScheduler.advanceUntilIdle()

        assertTrue(repository.version.value > beforeMark)
        repository.endSyncWindow(SyncWindowKind.WALK)
    }

    /**
     * The write commits before the query behind the snapshot re-runs, so announcing from the write
     * itself wakes every open list to re-read an index that still holds the old value — and the
     * emission that does carry the change is then held by the window for the rest of the walk.
     */
    @Test
    fun syncWindow_waitsForTheIndexToShowTheMarkBeforeAnnouncingIt() = runTest {
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()
        repository.beginSyncWindow(SyncWindowKind.WALK)
        val beforeMark = repository.version.value

        dao.emissionsFlowing.value = false
        repository.markLocally(itemId = 7, isSeriesLike = false, isFullyWatched = true)
        testScheduler.advanceUntilIdle()

        // Durable, but nothing can read it yet — so nothing has been told.
        assertEquals(beforeMark, repository.version.value)

        dao.emissionsFlowing.value = true
        testScheduler.advanceUntilIdle()

        assertTrue(repository.version.value > beforeMark)
        assertTrue(repository.snapshot.value.getValue(7).isFullyWatched)
        repository.endSyncWindow(SyncWindowKind.WALK)
    }

    /**
     * The gap between a mark being registered and its row landing is real — the repository has two
     * database reads to do in between — and a walk writes chunks throughout. An emission from one of
     * those chunks carries the walk's rows and not the user's, so taking it for the mark would
     * announce nothing new and leave the emission that does carry the mark to be held with the rest
     * of the walk, invisible for minutes.
     */
    @Test
    fun syncWindow_doesNotMistakeAWalksEmissionForTheUsersMark() = runTest {
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()
        repository.beginSyncWindow(SyncWindowKind.WALK)

        // The mark registers what it will write, then stalls before the row lands.
        dao.holdUserWrites()
        // On the test's own scope, not backgroundScope: advanceUntilIdle drives this one, which is
        // what parks it precisely at the gate.
        launch {
            repository.markLocally(itemId = 7, isSeriesLike = false, isFullyWatched = true)
        }
        testScheduler.advanceUntilIdle()
        val beforeChunk = repository.version.value

        // A chunk of the walk lands in that gap. Its emission does not carry item 7.
        repository.recordFromServer(listOf(watchingItem(id = 1)))
        testScheduler.advanceUntilIdle()

        assertEquals(beforeChunk, repository.version.value)

        dao.releaseUserWrites()
        testScheduler.advanceUntilIdle()

        assertTrue(repository.version.value > beforeChunk)
        assertTrue(repository.snapshot.value.getValue(7).isFullyWatched)
        repository.endSyncWindow(SyncWindowKind.WALK)
    }

    @Test
    fun resolve_prefersTheItemsOwnFieldsOverTheIndex() = runTest {
        dao.rows.value = listOf(entity(itemId = 1, isFullyWatched = true))
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()

        val fromIndex = repository.resolve(Item(id = 1, title = "No fields", type = ItemType.SERIAL))
        val fromItem = repository.resolve(
            Item(id = 1, title = "With fields", type = ItemType.SERIAL, watched = 3, new = 2, total = 10)
        )

        assertTrue(fromIndex!!.isFullyWatched)
        assertFalse(fromItem!!.isFullyWatched)
    }

    @Test
    fun isFullyWatched_fallsBackToTheIndexForCatalogueItems() = runTest {
        dao.rows.value = listOf(entity(itemId = 7, isFullyWatched = true))
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()

        val catalogueItem = Item(id = 7, title = "Catalogue item", type = ItemType.SERIAL)

        assertTrue(repository.isFullyWatched(catalogueItem))
        assertFalse(repository.isFullyWatched(catalogueItem.copy(id = 8)))
    }

    @Test
    fun markLocally_writesAPendingRow() = runTest {
        val repository = repository(eagerScope())

        repository.markLocally(itemId = 5, isSeriesLike = false, isFullyWatched = true)

        val row = dao.rows.value.single()
        assertEquals(5, row.itemId)
        assertTrue(row.isFullyWatched)
        assertTrue(row.isLocalPending)
    }

    @Test
    fun confirmLocalMark_dropsThePendingFlag() = runTest {
        val repository = repository(eagerScope())
        repository.markLocally(itemId = 5, isSeriesLike = false, isFullyWatched = true)

        repository.confirmLocalMark(5)

        assertFalse(dao.rows.value.single().isLocalPending)
    }

    @Test
    fun markLocally_isNotStrippedByTheCleanupQueuedAtStartup() = runTest {
        // The cleanup clears every pending flag it finds, on the assumption that they all outlived
        // the process that set them. A toggle made before it ran would be caught by it and lose
        // the protection that keeps a concurrent sync from rolling the mark back.
        dao.rows.value = listOf(
            WatchStateEntity(itemId = 5, isSeriesLike = false, isFullyWatched = false, updatedAt = 0L)
        )
        // A plain scope on the test scheduler, so the init coroutines are queued rather than run
        // at construction — which is what lets the mark go first.
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        try {
            val repository = repository(scope)

            repository.markLocally(itemId = 5, isSeriesLike = false, isFullyWatched = true)
            // Lets the queued cleanup run. It has to have gone before the mark or not at all —
            // reaching the row afterwards is what strips the flag.
            testScheduler.advanceUntilIdle()

            assertTrue(dao.rows.value.single().isLocalPending)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun clearAllPending_waitsForStartupToSettle() = runTest {
        dao.rows.value = listOf(
            WatchStateEntity(
                itemId = 5,
                isSeriesLike = false,
                isFullyWatched = true,
                updatedAt = 0L,
                isLocalPending = true,
            )
        )
        val settled = CompletableDeferred<Unit>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        try {
            repository(scope, awaitStartupSettled = settled::await)

            testScheduler.advanceUntilIdle()
            assertTrue(dao.rows.value.single().isLocalPending)
            assertEquals(0, dao.clearAllPendingCalls)

            settled.complete(Unit)
            testScheduler.advanceUntilIdle()

            assertFalse(dao.rows.value.single().isLocalPending)
            assertEquals(1, dao.clearAllPendingCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun markLocally_completesOnlyAfterTheDeferredCleanup() = runTest {
        val settled = CompletableDeferred<Unit>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        try {
            val repository = repository(scope, awaitStartupSettled = settled::await)

            val mark = launch { repository.markLocally(itemId = 5, isSeriesLike = false, isFullyWatched = true) }
            testScheduler.advanceUntilIdle()
            assertFalse(mark.isCompleted)

            settled.complete(Unit)
            testScheduler.advanceUntilIdle()

            assertTrue(mark.isCompleted)
            assertTrue(dao.rows.value.single().isLocalPending)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun revertLocalMark_dropsTheRowWhenThereWasNothingBefore() = runTest {
        val repository = repository(eagerScope())
        repository.markLocally(itemId = 5, isSeriesLike = false, isFullyWatched = true)

        repository.revertLocalMark(5)

        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun episodeCountAloneIsNotTreatedAsWatchState() = runTest {
        // `total` is an episode count. Treating it as watch state would shadow the index row and
        // silently disable both hiding and the watched badge for that item.
        dao.rows.value = listOf(entity(itemId = 9, isFullyWatched = true))
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()

        val item = Item(id = 9, title = "Series with an episode count", type = ItemType.SERIAL, total = 10)

        assertTrue(repository.isFullyWatched(item))
    }

    @Test
    fun recordInProgress_marksBareItemsUnfinishedWithoutLosingProgress() = runTest {
        // `/watching/movies` returns id/title/type/posters and nothing else, so it can only say
        // "started, not finished" — it must not wipe a position learned from the history.
        dao.rows.value = listOf(
            WatchStateEntity(
                itemId = 4,
                isSeriesLike = false,
                isFullyWatched = true,
                progressTime = 30,
                progressDuration = 120,
                updatedAt = 0L,
            )
        )
        val repository = repository(eagerScope())

        repository.recordInProgress(listOf(Item(id = 4, title = "Bare movie", type = ItemType.MOVIE)))

        val row = dao.rows.value.single()
        assertFalse(row.isFullyWatched)
        assertEquals(30, row.progressTime)
        assertEquals(120, row.progressDuration)
    }

    @Test
    fun recordFromHistory_marksAFinishedMovieAndKeepsAPartialOne() = runTest {
        val repository = repository(eagerScope())

        repository.recordFromHistory(
            entries = listOf(
                historyEntry(itemId = 10, type = ItemType.MOVIE, time = 120, duration = 120),
                historyEntry(itemId = 11, type = ItemType.MOVIE, time = 30, duration = 120),
            ),
            seriesStillInProgress = emptySet(),
        )

        val byId = dao.rows.value.associateBy { it.itemId }
        assertTrue(byId.getValue(10).isFullyWatched)
        assertFalse(byId.getValue(11).isFullyWatched)
        assertEquals(30, byId.getValue(11).progressTime)
    }

    @Test
    fun recordFromHistory_callsASeriesFinishedOnlyWhenItLeftTheInProgressList() = runTest {
        val repository = repository(eagerScope())

        repository.recordFromHistory(
            entries = listOf(
                historyEntry(itemId = 20, type = ItemType.SERIAL, time = 100, duration = 100),
                historyEntry(itemId = 21, type = ItemType.SERIAL, time = 100, duration = 100),
            ),
            seriesStillInProgress = setOf(21),
        )

        val byId = dao.rows.value.associateBy { it.itemId }
        assertTrue(byId.getValue(20).isFullyWatched)
        assertFalse(byId.getValue(21).isFullyWatched)
    }

    @Test
    fun recordFromHistory_doesNotLetAnOlderEntryOverwriteANewerOne() = runTest {
        // History is walked newest-first across several launches, so an older replay read later
        // must not resurrect a finished state the newest entry already replaced.
        val repository = repository(eagerScope())

        repository.recordFromHistory(
            entries = listOf(
                historyEntry(itemId = 30, type = ItemType.MOVIE, time = 30, duration = 120, lastSeen = 900)
            ),
            seriesStillInProgress = emptySet(),
        )
        repository.recordFromHistory(
            entries = listOf(
                historyEntry(itemId = 30, type = ItemType.MOVIE, time = 120, duration = 120, lastSeen = 100)
            ),
            seriesStillInProgress = emptySet(),
        )

        val row = dao.rows.value.single()
        assertFalse(row.isFullyWatched)
        assertEquals(30, row.progressTime)
    }

    @Test
    fun recordFromHistory_letsARecentCompleteViewingBeatAnOldPartialOne() = runTest {
        // Two rows for one movie are the same film played twice, not a quorum: the newest play
        // decides, or a half-watched attempt from years ago would veto a recent full one.
        val repository = repository(eagerScope())

        repository.recordFromHistory(
            entries = listOf(
                historyEntry(itemId = 60, type = ItemType.MOVIE, time = 20, duration = 120, lastSeen = 100),
                historyEntry(itemId = 60, type = ItemType.MOVIE, time = 120, duration = 120, lastSeen = 900),
            ),
            seriesStillInProgress = emptySet(),
        )

        assertTrue(dao.rows.value.single().isFullyWatched)
    }

    @Test
    fun recordFromHistory_doesNotCallASeriesFinishedWhileAnEpisodeIsHalfWatched() = runTest {
        // Leaving the watching list is only an absence. An episode the account stopped part-way
        // through is direct evidence the series is not done, and has to outrank it.
        val repository = repository(eagerScope())

        repository.recordFromHistory(
            entries = listOf(
                historyEntry(itemId = 61, type = ItemType.SERIAL, time = 20, duration = 120, lastSeen = 900)
            ),
            seriesStillInProgress = emptySet(),
        )

        assertFalse(dao.rows.value.single().isFullyWatched)
    }

    @Test
    fun recordFromHistory_letsTheNewestEpisodeDecideASeries() = runTest {
        // An attempt abandoned long ago must not veto a later complete viewing — the walk crosses
        // pages newest-first and the database resolves those the same way.
        val repository = repository(eagerScope())

        repository.recordFromHistory(
            entries = listOf(
                historyEntry(itemId = 62, type = ItemType.SERIAL, time = 20, duration = 120, lastSeen = 100),
                historyEntry(itemId = 62, type = ItemType.SERIAL, time = 120, duration = 120, lastSeen = 900),
            ),
            seriesStillInProgress = emptySet(),
        )

        assertTrue(dao.rows.value.single().isFullyWatched)
    }

    @Test
    fun recordFromHistory_keepsEpisodeCountersLearnedFromTheWatchingList() = runTest {
        // History rows carry no counters; writing them would blank the ones the watching list gave.
        dao.rows.value = listOf(
            WatchStateEntity(
                itemId = 40,
                isSeriesLike = true,
                isFullyWatched = false,
                watchedEpisodes = 3,
                totalEpisodes = 10,
                updatedAt = 0L,
            )
        )
        val repository = repository(eagerScope())

        repository.recordFromHistory(
            entries = listOf(historyEntry(itemId = 40, type = ItemType.SERIAL, time = 100, duration = 100)),
            seriesStillInProgress = setOf(40),
        )

        val row = dao.rows.value.single()
        assertEquals(3, row.watchedEpisodes)
        assertEquals(10, row.totalEpisodes)
    }

    @Test
    fun revertLocalMark_putsBackWhatTheMarkReplaced() = runTest {
        dao.rows.value = listOf(
            WatchStateEntity(
                itemId = 50,
                isSeriesLike = false,
                isFullyWatched = false,
                progressTime = 45,
                progressDuration = 120,
                updatedAt = 0L,
            )
        )
        val repository = repository(eagerScope())
        repository.markLocally(itemId = 50, isSeriesLike = false, isFullyWatched = true)

        repository.revertLocalMark(50)

        val row = dao.rows.value.single()
        assertFalse(row.isFullyWatched)
        assertFalse(row.isLocalPending)
        assertEquals(45, row.progressTime)
    }

    @Test
    fun progressPercent_forSeriesUsesWatchedOverTotal() {
        val state = WatchState(
            itemId = 1,
            isSeriesLike = true,
            isFullyWatched = false,
            watchedEpisodes = 3,
            totalEpisodes = 12,
        )

        assertEquals(0.25f, state.progressPercent)
    }

    @Test
    fun progressPercent_forMoviesUsesPlaybackPosition() {
        val state = WatchState(
            itemId = 1,
            isSeriesLike = false,
            isFullyWatched = false,
            progressTime = 30,
            progressDuration = 120,
        )

        assertEquals(0.25f, state.progressPercent)
    }

    @Test
    fun progressPercent_isNullWhenFinishedOrUntouched() {
        val finished = WatchState(
            itemId = 1,
            isSeriesLike = true,
            isFullyWatched = true,
            watchedEpisodes = 12,
            totalEpisodes = 12,
        )
        val untouched = WatchState(itemId = 2, isSeriesLike = true, isFullyWatched = false)

        assertNull(finished.progressPercent)
        assertNull(untouched.progressPercent)
    }

    @Test
    fun movieWithPlaybackProgress_isRecordedAsPartiallyWatched() = runTest {
        val repository = repository(eagerScope())

        repository.recordFromServer(listOf(halfSeenMovie()))

        val row = dao.rows.value.single()
        assertFalse(row.isFullyWatched)
        assertEquals(30, row.progressTime)
        assertEquals(120, row.progressDuration)
    }

    @Test
    fun resolve_readsAMoviesPositionAndLengthFromItsFirstVideo() = runTest {
        val repository = repository(eagerScope())
        val item = halfSeenMovie()

        assertEquals(0.25f, repository.resolve(item)?.progressPercent)
        val mapper = VideoItemUIMapper(FakeResourceProvider(), watchStateRepository = repository)
        assertEquals(0.25f, mapper.mapShortItem(item).progressPercent)
    }

    @Test
    fun recordFromServer_keepsAMoviesBarAfterADetailsReadWithoutALength() = runTest {
        val repository = repository(eagerScope())
        repository.recordFromHistory(
            entries = listOf(historyEntry(itemId = 5, type = ItemType.MOVIE, time = 30, duration = 120)),
            seriesStillInProgress = emptySet(),
        )
        now = 100L
        val item = Item(
            id = 5,
            title = "Details read",
            type = ItemType.MOVIE,
            watching = WatchingInfo(time = 30),
            videos = listOf(Video(id = 1, duration = 120)),
        )

        repository.recordFromServer(listOf(item))

        assertEquals(120, dao.rows.value.single().progressDuration)
        assertEquals(0.25f, repository.resolve(item)?.progressPercent)
    }

    @Test
    fun recordFromServer_takesAMoviesWatchedVerdictFromItsFirstVideo() = runTest {
        val repository = repository(eagerScope())

        repository.recordFromServer(
            listOf(
                Item(
                    id = 6,
                    title = "Finished movie",
                    type = ItemType.MOVIE,
                    videos = listOf(Video(id = 1, duration = 120, watched = 1)),
                )
            )
        )

        assertTrue(dao.rows.value.single().isFullyWatched)
    }

    @Test
    fun resolve_fallsBackToTheIndexForAMovieWhoseVideoCarriesNoWatchFields() = runTest {
        dao.rows.value = listOf(
            entity(1, false).copy(isSeriesLike = false, progressTime = 30, progressDuration = 120)
        )
        val repository = repository(eagerScope())
        testScheduler.advanceUntilIdle()

        val resolved = repository.resolve(
            Item(id = 1, title = "Movie", type = ItemType.MOVIE, videos = listOf(Video(id = 1, duration = 120)))
        )

        assertEquals(repository.get(1), resolved)
        assertEquals(0.25f, resolved?.progressPercent)
    }

    @Test
    fun pruneStaleRows_dropsWhatTheLatestPassNeverRestamped() = runTest {
        dao.rows.value = listOf(
            entity(itemId = 1, isFullyWatched = true).copy(generation = 1),
            entity(itemId = 2, isFullyWatched = true).copy(generation = 2),
        )
        val repository = repository(eagerScope())

        repository.pruneStaleRows(generation = 2)

        assertEquals(listOf(2), dao.rows.value.map { it.itemId })
    }

    @Test
    fun pruneStaleRows_leavesAnUnconfirmedMarkAlone() = runTest {
        // The user made it a moment ago and the server has not acknowledged it, so no pass could
        // have seen it — deleting it would undo the mark in front of them.
        val repository = repository(eagerScope())
        repository.markLocally(itemId = 3, isSeriesLike = false, isFullyWatched = true)

        repository.pruneStaleRows(generation = 2)

        assertEquals(listOf(3), dao.rows.value.map { it.itemId })
    }

    @Test
    fun aSyncThatReadItsDataBeforeAToggleDoesNotUndoIt() = runTest {
        // The pending flag is dropped as soon as the server confirms the toggle. A sync that
        // fetched before the toggle can still be writing after that, and would put back the very
        // state the user just changed.
        val repository = repository(eagerScope())
        val readBeforeTheToggle = now
        now += 10
        repository.markLocally(itemId = 70, isSeriesLike = false, isFullyWatched = true)
        repository.confirmLocalMark(70)

        repository.recordFromServer(
            listOf(Item(id = 70, title = "Movie", type = ItemType.MOVIE, watched = 0)),
            observedAt = readBeforeTheToggle,
        )

        assertTrue(dao.rows.value.single().isFullyWatched)
    }

    @Test
    fun aSyncThatReadItsDataAfterAToggleStillWins() = runTest {
        // The guard is about ordering, not about making local marks permanent: once the server has
        // been read again, what it says is newer than the toggle and has to land.
        val repository = repository(eagerScope())
        repository.markLocally(itemId = 71, isSeriesLike = false, isFullyWatched = true)
        repository.confirmLocalMark(71)
        now += 10

        repository.recordFromServer(
            listOf(Item(id = 71, title = "Movie", type = ItemType.MOVIE, watched = 0)),
            observedAt = now,
        )

        assertFalse(dao.rows.value.single().isFullyWatched)
    }

    @Test
    fun recordFromServer_aNotWatchedFirstVideoReplacesAWatchedHistoryRow() = runTest {
        val repository = repository(eagerScope())
        repository.recordFromHistory(
            entries = listOf(historyEntry(itemId = 7, type = ItemType.MOVIE, time = 120, duration = 120)),
            seriesStillInProgress = emptySet(),
        )
        assertTrue(dao.rows.value.single().isFullyWatched)
        now = 100L

        repository.recordFromServer(
            listOf(
                Item(
                    id = 7,
                    title = "Details read",
                    type = ItemType.MOVIE,
                    videos = listOf(Video(id = 1, duration = 120, watching = WatchingInfo(status = 0, time = 0))),
                )
            )
        )

        val row = dao.rows.value.single()
        assertFalse(row.isFullyWatched)
        assertEquals(0, row.progressTime)
        assertEquals(120, row.progressDuration)
    }

    private fun halfSeenMovie() = Item(
        id = 3,
        title = "Half seen movie",
        type = ItemType.MOVIE,
        videos = listOf(Video(id = 1, duration = 120, watching = WatchingInfo(time = 30))),
    )

    private fun historyEntry(
        itemId: Int,
        type: ItemType,
        time: Int,
        duration: Int,
        lastSeen: Long = 1_000L,
    ) = History(
        item = Item(id = itemId, title = "Item $itemId", type = type),
        video = Video(
            id = itemId * 100,
            duration = duration,
            watched = if (time >= duration) 1 else 0,
            watching = WatchingInfo(time = time, duration = duration),
        ),
        time = time,
        updated = lastSeen.toString(),
    )

    private fun entity(itemId: Int, isFullyWatched: Boolean) = WatchStateEntity(
        itemId = itemId,
        isSeriesLike = true,
        isFullyWatched = isFullyWatched,
        updatedAt = 0L,
    )
}

/**
 * In-memory stand-in for the Room DAO. The `is_local_pending` guard lives in SQL and is exercised
 * by the instrumented database test, not here.
 */
private class FakeWatchStateDao : WatchStateDao() {

    val rows = MutableStateFlow<List<WatchStateEntity>>(emptyList())

    /**
     * Whether the query behind [observeAll] is keeping up with the writes.
     *
     * Room's write returns once it commits; the flow's query re-runs afterwards, so there is a
     * moment where the row is durable and the snapshot still shows the old one. Set this to false to
     * hold the flow there, and back to true to let the re-run land — a gap a flow that is simply the
     * write target cannot reproduce.
     */
    val emissionsFlowing = MutableStateFlow(true)

    override fun observeAll(): Flow<List<WatchStateEntity>> =
        combine(rows, emissionsFlowing) { rows, flowing -> rows to flowing }
            .filter { (_, flowing) -> flowing }
            .map { (rows, _) -> rows }

    override suspend fun get(itemId: Int): WatchStateEntity? = rows.value.find { it.itemId == itemId }

    override suspend fun count(): Int = rows.value.size

    override suspend fun lastWatchedAt(): List<ItemLastWatched> = rows.value
        .filter { row -> row.historySeenAt > 0 }
        .map { row -> ItemLastWatched(itemId = row.itemId, historySeenAt = row.historySeenAt) }

    /**
     * Holds the user-facing write, so a test can stand between the point the repository registers
     * what a mark will produce and the point that row actually lands. Only [upsert] waits on it —
     * the sync paths write through [applyUpsert] and are free to land meanwhile, which is the whole
     * interleaving being reproduced.
     */
    private var heldWrite: CompletableDeferred<Unit>? = null

    fun holdUserWrites() {
        heldWrite = CompletableDeferred()
    }

    fun releaseUserWrites() {
        heldWrite?.complete(Unit)
        heldWrite = null
    }

    override suspend fun upsert(entities: List<WatchStateEntity>) {
        heldWrite?.await()
        applyUpsert(entities)
    }

    override suspend fun insertIfAbsent(entity: WatchStateEntity): Long {
        if (rows.value.any { it.itemId == entity.itemId }) return -1L
        applyUpsert(listOf(entity))
        return entity.itemId.toLong()
    }

    private fun applyUpsert(entities: List<WatchStateEntity>) {
        val byId = rows.value.associateBy { it.itemId }.toMutableMap()
        entities.forEach { entity -> byId[entity.itemId] = entity }
        rows.value = byId.values.toList()
    }

    @Suppress("LongParameterList")
    override suspend fun upsertFromServer(
        generation: Long,
        itemId: Int,
        isSeriesLike: Boolean,
        isFullyWatched: Boolean,
        watchedEpisodes: Int?,
        totalEpisodes: Int?,
        progressTime: Int?,
        progressDuration: Int?,
        updatedAt: Long,
    ) {
        val existing = rows.value.find { it.itemId == itemId }
        if (existing?.isLocalPending == true) return
        if (existing != null && updatedAt < existing.updatedAt) return
        applyUpsert(
            listOf(
                WatchStateEntity(
                    itemId = itemId,
                    isSeriesLike = isSeriesLike,
                    isFullyWatched = isFullyWatched,
                    watchedEpisodes = watchedEpisodes,
                    totalEpisodes = totalEpisodes,
                    progressTime = progressTime,
                    progressDuration = progressDuration,
                    updatedAt = updatedAt,
                    // The real statement leaves history_seen_at out of its DO UPDATE SET, so a
                    // server write keeps whatever the history walk recorded.
                    historySeenAt = existing?.historySeenAt ?: 0L,
                    generation = generation,
                )
            )
        )
    }

    @Suppress("LongParameterList")
    override suspend fun upsertFromHistory(
        generation: Long,
        itemId: Int,
        isFullyWatched: Boolean,
        progressTime: Int?,
        progressDuration: Int?,
        updatedAt: Long,
        historySeenAt: Long,
    ) {
        val existing = rows.value.find { it.itemId == itemId }
        if (existing?.isLocalPending == true) return
        if (existing != null && historySeenAt < existing.historySeenAt) return
        if (existing != null && updatedAt < existing.updatedAt) return
        applyUpsert(
            listOf(
                existing?.copy(
                    isFullyWatched = isFullyWatched,
                    progressTime = progressTime,
                    progressDuration = progressDuration,
                    updatedAt = updatedAt,
                    historySeenAt = historySeenAt,
                    generation = generation,
                ) ?: error("Missing row must be inserted before it is updated")
            )
        )
    }

    override suspend fun updateInProgress(generation: Long, itemId: Int, updatedAt: Long) = Unit

    override suspend fun markInProgress(
        generation: Long,
        itemId: Int,
        isSeriesLike: Boolean,
        updatedAt: Long,
    ) {
        val existing = rows.value.find { it.itemId == itemId }
        if (existing?.isLocalPending == true) return
        if (existing != null && updatedAt < existing.updatedAt) return
        applyUpsert(
            listOf(
                existing?.copy(isFullyWatched = false, updatedAt = updatedAt, generation = generation)
                    ?: WatchStateEntity(
                        itemId = itemId,
                        isSeriesLike = isSeriesLike,
                        isFullyWatched = false,
                        updatedAt = updatedAt,
                        generation = generation,
                    )
            )
        )
    }

    override suspend fun delete(itemId: Int) {
        rows.value = rows.value.filterNot { it.itemId == itemId }
    }

    var clearAllPendingCalls = 0

    override suspend fun clearAllPending() {
        clearAllPendingCalls += 1
        rows.value = rows.value.map { row -> row.copy(isLocalPending = false) }
    }

    override suspend fun clearPending(itemId: Int) {
        rows.value = rows.value.map { row ->
            if (row.itemId == itemId) row.copy(isLocalPending = false) else row
        }
    }

    override suspend fun pruneOlderThan(generation: Long) {
        rows.value = rows.value.filterNot { it.generation < generation && !it.isLocalPending }
    }

    override suspend fun clear() {
        rows.value = emptyList()
    }
}

private class FakeWatchStateSyncDao : WatchStateSyncDao {

    private val row = MutableStateFlow<WatchStateSyncEntity?>(null)

    override fun observe(id: Int): Flow<WatchStateSyncEntity?> = row

    override suspend fun get(id: Int): WatchStateSyncEntity? = row.value

    override suspend fun upsert(entity: WatchStateSyncEntity) {
        row.value = entity
    }

    override suspend fun raiseHistoryNewestSeen(seen: Long, id: Int) {
        val stored = row.value ?: return
        if (stored.historyNewestSeen < seen) row.value = stored.copy(historyNewestSeen = seen)
    }

    override suspend fun clear() {
        row.value = null
    }
}
