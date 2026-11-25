package com.kino.puber.data.db

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kino.puber.data.repository.RoomPersistentPayloadStore
import com.kino.puber.data.repository.StoredPayload
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The store over the real DAO and real SQLite: a row larger than the CursorWindow fails the whole
 * query, which a fake DAO cannot reproduce. Oversized rows are planted with the DAO, bypassing the
 * store's write guard, as the row on the box was written before it existed.
 */
@RunWith(AndroidJUnit4::class)
class PersistentPayloadStoreDbTest {

    private lateinit var database: PuberDatabase
    private lateinit var dao: CachedPayloadDao
    private lateinit var store: RoomPersistentPayloadStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PuberDatabase::class.java,
        ).setDriver(AndroidSQLiteDriver()).build()
        dao = database.cachedPayloadDao()
        store = RoomPersistentPayloadStore(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun readAllReturnsTheSmallRowsAndDeletesARowTooBigForTheCursorWindow() = runTest {
        dao.upsert(CachedPayloadEntity(key = "small1", payload = "{\"a\":1}", updatedAt = 1))
        dao.upsert(CachedPayloadEntity(key = "big", payload = "a".repeat(3 shl 20), updatedAt = 2))
        dao.upsert(CachedPayloadEntity(key = "small2", payload = "{\"a\":2}", updatedAt = 3))

        val read = store.readAll(listOf("small1", "big", "small2"))

        assertEquals(
            mapOf(
                "small1" to StoredPayload("{\"a\":1}", 1),
                "small2" to StoredPayload("{\"a\":2}", 3),
            ),
            read,
        )
        assertNull(dao.read("big", Int.MAX_VALUE))
    }

    @Test
    fun readReturnsNullAndDeletesARowTooBigForTheCursorWindow() = runTest {
        dao.upsert(CachedPayloadEntity(key = "big", payload = "a".repeat(3 shl 20), updatedAt = 2))

        assertNull(store.read("big"))

        assertNull(dao.read("big", Int.MAX_VALUE))
    }

    @Test
    fun aMultiByteRowJustUnderTheLimitRoundTrips() = runTest {
        val payload = "ж".repeat((RoomPersistentPayloadStore.MAX_ROW_BYTES - 2) / 2)

        store.write(key = "k", payload = payload, updatedAt = 1)

        assertEquals(StoredPayload(payload, 1), store.read("k"))
    }

    @Test
    fun anAsciiPayloadOfExactlyTheLimitRoundTripsThroughTheStore() = runTest {
        val payload = "a".repeat(RoomPersistentPayloadStore.MAX_ROW_BYTES)

        store.write(key = "k", payload = payload, updatedAt = 1)

        assertEquals(StoredPayload(payload, 1), store.read("k"))
    }

    @Test
    fun aStoredCyrillicRowOverTheLimitInBytesIsTreatedAsAbsent() = runTest {
        dao.upsert(CachedPayloadEntity(key = "k", payload = "ж".repeat(600_000), updatedAt = 1))

        assertNull(store.read("k"))

        assertNull(dao.read("k", Int.MAX_VALUE))
    }
}
