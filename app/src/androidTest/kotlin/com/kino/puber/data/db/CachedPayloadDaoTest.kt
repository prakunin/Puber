package com.kino.puber.data.db

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kino.puber.data.repository.RoomPersistentPayloadStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The unit tests stand in for this DAO with a fake, so nothing there executes the SQL. Every
 * statement is exercised here against real SQLite, on both paths it has: insert and conflict.
 */
@RunWith(AndroidJUnit4::class)
class CachedPayloadDaoTest {

    private lateinit var database: PuberDatabase
    private lateinit var dao: CachedPayloadDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PuberDatabase::class.java,
        ).setDriver(AndroidSQLiteDriver()).build()
        dao = database.cachedPayloadDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun read(key: String) = dao.read(key, Int.MAX_VALUE)

    @Test
    fun upsertInsertsThenReplaces() = runTest {
        dao.upsert(CachedPayloadEntity(key = "item:1", payload = "{\"a\":1}", updatedAt = 100))
        dao.upsert(CachedPayloadEntity(key = "item:1", payload = "{\"a\":2}", updatedAt = 200))

        val row = read("item:1")

        assertEquals("{\"a\":2}", row?.payload)
        assertEquals(200L, row?.updatedAt)
    }

    @Test
    fun readReturnsNullForUnknownKey() = runTest {
        assertNull(read("item:404"))
    }

    @Test
    fun touchMovesTheTimestampAndKeepsThePayload() = runTest {
        dao.upsert(CachedPayloadEntity(key = "item:1", payload = "{\"a\":1}", updatedAt = 500))

        dao.touch(key = "item:1", updatedAt = 42)

        val row = read("item:1")
        assertEquals("{\"a\":1}", row?.payload)
        assertEquals(42L, row?.updatedAt)
    }

    @Test
    fun deleteRemovesOnlyTheGivenKey() = runTest {
        dao.upsert(CachedPayloadEntity(key = "item:1", payload = "{\"a\":1}", updatedAt = 1))
        dao.upsert(CachedPayloadEntity(key = "item:2", payload = "{\"a\":2}", updatedAt = 1))

        dao.delete("item:1")

        assertNull(read("item:1"))
        assertEquals("{\"a\":2}", read("item:2")?.payload)
    }

    @Test
    fun deleteByPrefixRemovesOnlyTheMatchingKeys() = runTest {
        dao.upsert(CachedPayloadEntity(key = "home:hot", payload = "[]", updatedAt = 1))
        dao.upsert(CachedPayloadEntity(key = "home:fresh", payload = "[]", updatedAt = 1))
        dao.upsert(CachedPayloadEntity(key = "item:1", payload = "{}", updatedAt = 1))

        dao.deleteByPrefix("home:")

        assertNull(read("home:hot"))
        assertNull(read("home:fresh"))
        assertEquals("{}", read("item:1")?.payload)
    }

    @Test
    fun clearEmptiesTheTable() = runTest {
        dao.upsert(CachedPayloadEntity(key = "item:1", payload = "{}", updatedAt = 1))

        dao.clear()

        assertNull(read("item:1"))
    }

    @Test
    fun deleteOlderThanRemovesOnlyRowsBeforeTheCutoff() = runTest {
        val cutoff = 1_000L
        dao.upsert(CachedPayloadEntity(key = "before", payload = "b", updatedAt = cutoff - 1))
        dao.upsert(CachedPayloadEntity(key = "at", payload = "a", updatedAt = cutoff))
        dao.upsert(CachedPayloadEntity(key = "after", payload = "c", updatedAt = cutoff + 1))

        dao.deleteOlderThan(cutoff)

        assertNull(read("before"))
        assertEquals(CachedPayloadEntity("at", "a", cutoff), read("at"))
        assertEquals(CachedPayloadEntity("after", "c", cutoff + 1), read("after"))
    }

    @Test
    fun deleteBeyondNewestKeepsTheNewestRows() = runTest {
        (1..5).forEach { n -> dao.upsert(CachedPayloadEntity("k$n", "p$n", updatedAt = n * 100L)) }

        dao.deleteBeyondNewest(keepNewest = 3)

        assertNull(read("k1"))
        assertNull(read("k2"))
        assertEquals("p3", read("k3")?.payload)
        assertEquals("p4", read("k4")?.payload)
        assertEquals("p5", read("k5")?.payload)
    }

    @Test
    fun deleteBeyondNewestWithFewerRowsThanTheCapDeletesNothing() = runTest {
        dao.upsert(CachedPayloadEntity("k1", "p1", updatedAt = 100))
        dao.upsert(CachedPayloadEntity("k2", "p2", updatedAt = 200))

        dao.deleteBeyondNewest(keepNewest = 3)

        assertEquals("p1", read("k1")?.payload)
        assertEquals("p2", read("k2")?.payload)
    }

    @Test
    fun deleteBeyondNewestKeepsARowWrittenAfterTheAgeStep() = runTest {
        dao.upsert(CachedPayloadEntity("k1", "p1", updatedAt = 100))
        dao.upsert(CachedPayloadEntity("k2", "p2", updatedAt = 200))
        dao.upsert(CachedPayloadEntity("k3", "p3", updatedAt = 300))

        dao.deleteOlderThan(50)
        dao.upsert(CachedPayloadEntity("fresh", "pf", updatedAt = 400))
        dao.deleteBeyondNewest(keepNewest = 3)

        assertNull(read("k1"))
        assertEquals("pf", read("fresh")?.payload)
        assertEquals("p2", read("k2")?.payload)
        assertEquals("p3", read("k3")?.payload)
    }

    @Test
    fun deleteOversizedKeepsARowWithinTheLimit() = runTest {
        dao.upsert(CachedPayloadEntity(key = "small", payload = "{}", updatedAt = 1))
        dao.upsert(CachedPayloadEntity(key = "big", payload = "a".repeat(3 shl 20), updatedAt = 1))

        dao.deleteOversized(listOf("small", "big"), RoomPersistentPayloadStore.MAX_ROW_BYTES)

        assertEquals("{}", read("small")?.payload)
        assertNull(read("big"))
    }
}
