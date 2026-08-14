package com.kino.puber.data.db

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    @Test
    fun upsertInsertsThenReplaces() = runTest {
        dao.upsert(CachedPayloadEntity(key = "item:1", payload = "{\"a\":1}", updatedAt = 100))
        dao.upsert(CachedPayloadEntity(key = "item:1", payload = "{\"a\":2}", updatedAt = 200))

        val row = dao.read("item:1")

        assertEquals("{\"a\":2}", row?.payload)
        assertEquals(200L, row?.updatedAt)
    }

    @Test
    fun readReturnsNullForUnknownKey() = runTest {
        assertNull(dao.read("item:404"))
    }

    @Test
    fun touchMovesTheTimestampAndKeepsThePayload() = runTest {
        dao.upsert(CachedPayloadEntity(key = "item:1", payload = "{\"a\":1}", updatedAt = 500))

        dao.touch(key = "item:1", updatedAt = 42)

        val row = dao.read("item:1")
        assertEquals("{\"a\":1}", row?.payload)
        assertEquals(42L, row?.updatedAt)
    }

    @Test
    fun deleteRemovesOnlyTheGivenKey() = runTest {
        dao.upsert(CachedPayloadEntity(key = "item:1", payload = "{\"a\":1}", updatedAt = 1))
        dao.upsert(CachedPayloadEntity(key = "item:2", payload = "{\"a\":2}", updatedAt = 1))

        dao.delete("item:1")

        assertNull(dao.read("item:1"))
        assertEquals("{\"a\":2}", dao.read("item:2")?.payload)
    }

    @Test
    fun deleteByPrefixRemovesOnlyTheMatchingKeys() = runTest {
        dao.upsert(CachedPayloadEntity(key = "home:hot", payload = "[]", updatedAt = 1))
        dao.upsert(CachedPayloadEntity(key = "home:fresh", payload = "[]", updatedAt = 1))
        dao.upsert(CachedPayloadEntity(key = "item:1", payload = "{}", updatedAt = 1))

        dao.deleteByPrefix("home:")

        assertNull(dao.read("home:hot"))
        assertNull(dao.read("home:fresh"))
        assertEquals("{}", dao.read("item:1")?.payload)
    }

    @Test
    fun clearEmptiesTheTable() = runTest {
        dao.upsert(CachedPayloadEntity(key = "item:1", payload = "{}", updatedAt = 1))

        dao.clear()

        assertNull(dao.read("item:1"))
    }
}
