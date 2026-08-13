package com.kino.puber.data.repository

import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.api.models.WatchingInfo
import com.kino.puber.data.db.DatabaseTransaction
import com.kino.puber.data.db.WatchStateDao
import com.kino.puber.data.db.WatchStateSyncDao
import com.kino.puber.data.db.WatchStateSyncEntity
import com.kino.puber.data.db.WatchStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchStateRepositoryTest {

    private val dao = FakeWatchStateDao()
    private var now = 42L

    private fun repository(scope: CoroutineScope) = WatchStateRepository(
        dao = dao,
        syncDao = FakeWatchStateSyncDao(),
        transaction = DatabaseTransaction.Direct,
        clock = { now },
        scope = scope,
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

        repository.recordFromServer(
            listOf(
                Item(
                    id = 3,
                    title = "Half seen movie",
                    type = ItemType.MOVIE,
                    watched = 0,
                    watching = WatchingInfo(time = 30, duration = 120),
                )
            )
        )

        val row = dao.rows.value.single()
        assertFalse(row.isFullyWatched)
        assertEquals(30, row.progressTime)
        assertEquals(120, row.progressDuration)
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

    override fun observeAll(): Flow<List<WatchStateEntity>> = rows

    override suspend fun get(itemId: Int): WatchStateEntity? = rows.value.find { it.itemId == itemId }

    override suspend fun count(): Int = rows.value.size

    override suspend fun upsert(entities: List<WatchStateEntity>) {
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
        upsert(
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
        isSeriesLike: Boolean,
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
        upsert(
            listOf(
                existing?.copy(
                    isFullyWatched = isFullyWatched,
                    progressTime = progressTime,
                    progressDuration = progressDuration,
                    updatedAt = updatedAt,
                    historySeenAt = historySeenAt,
                    generation = generation,
                ) ?: WatchStateEntity(
                    itemId = itemId,
                    isSeriesLike = isSeriesLike,
                    isFullyWatched = isFullyWatched,
                    progressTime = progressTime,
                    progressDuration = progressDuration,
                    updatedAt = updatedAt,
                    historySeenAt = historySeenAt,
                    generation = generation,
                )
            )
        )
    }

    override suspend fun markInProgress(
        generation: Long,
        itemId: Int,
        isSeriesLike: Boolean,
        updatedAt: Long,
    ) {
        val existing = rows.value.find { it.itemId == itemId }
        if (existing?.isLocalPending == true) return
        if (existing != null && updatedAt < existing.updatedAt) return
        upsert(
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

    override suspend fun clearAllPending() {
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

    private var row: WatchStateSyncEntity? = null

    override suspend fun get(id: Int): WatchStateSyncEntity? = row

    override suspend fun upsert(entity: WatchStateSyncEntity) {
        row = entity
    }

    override suspend fun clear() {
        row = null
    }
}
