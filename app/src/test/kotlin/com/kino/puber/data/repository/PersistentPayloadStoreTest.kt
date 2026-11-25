package com.kino.puber.data.repository

import com.kino.puber.data.db.CachedPayloadDao
import com.kino.puber.data.db.CachedPayloadEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PersistentPayloadStoreTest {

    private val dao = FakeCachedPayloadDao()
    private val store = RoomPersistentPayloadStore(dao)

    @Test
    fun writeThenReadRoundTrips() = runTest {
        store.write(key = "item:1", payload = "{\"a\":1}", updatedAt = 100)

        assertEquals(StoredPayload("{\"a\":1}", 100), store.read("item:1"))
    }

    @Test
    fun batchWriteThenBatchReadRoundTrips() = runTest {
        val payloads = mapOf(
            "item:1" to StoredPayload("{\"id\":1}", 100),
            "item:2" to StoredPayload("{\"id\":2}", 101),
        )

        store.writeAll(payloads)

        assertEquals(payloads, store.readAll(payloads.keys.toList()))
    }

    @Test
    fun readReturnsNullForUnknownKey() = runTest {
        assertNull(store.read("item:404"))
    }

    @Test
    fun clearDropsEverything() = runTest {
        store.write(key = "item:1", payload = "{}", updatedAt = 1)

        store.clear()

        assertNull(store.read("item:1"))
    }

    @Test
    fun clearChangesGenerationBeforeAndAfterTheDatabaseMutation() = runTest {
        val clearReached = CompletableDeferred<Unit>()
        val releaseClear = CompletableDeferred<Unit>()
        dao.onClear = {
            clearReached.complete(Unit)
            releaseClear.await()
        }
        val before = store.generation
        val clearing = async { store.clear() }
        clearReached.await()
        val during = store.generation

        assertNotEquals(before, during)
        releaseClear.complete(Unit)
        clearing.await()
        assertNotEquals(during, store.generation)
    }

    @Test
    fun writeStartedBeforeAClearDoesNotSurviveIt() = runTest {
        // A background revalidation begun under the previous session must not leave a row behind
        // for the next account to inherit.
        val writeReached = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        dao.onUpsert = {
            writeReached.complete(Unit)
            releaseWrite.await()
        }
        val write = async { store.write(key = "item:1", payload = "{}", updatedAt = 1) }
        writeReached.await()

        dao.onUpsert = null
        store.clear()
        releaseWrite.complete(Unit)
        write.await()

        assertNull(store.read("item:1"))
    }

    @Test
    fun writeReportsAnOversizedPayloadAsNotStored() = runTest {
        val limit = RoomPersistentPayloadStore.MAX_ROW_BYTES

        assertTrue(store.write(key = "exact", payload = "a".repeat(limit), updatedAt = 1))
        assertFalse(store.write(key = "over", payload = "a".repeat(limit + 1), updatedAt = 1))
    }

    @Test
    fun writeAllReturnsOnlyTheOversizedKeys() = runTest {
        val refused = store.writeAll(
            mapOf(
                "big" to StoredPayload("a".repeat(RoomPersistentPayloadStore.MAX_ROW_BYTES + 1), 1),
                "small" to StoredPayload("{}", 1),
            )
        )

        assertEquals(setOf("big"), refused)
    }

    @Test
    fun writeOfAnOversizedPayloadKeepsTheKeysOlderRow() = runTest {
        store.write(key = "k", payload = "{}", updatedAt = 1)

        store.write(key = "k", payload = "a".repeat(RoomPersistentPayloadStore.MAX_ROW_BYTES + 1), updatedAt = 2)

        assertEquals("{}", dao.rows["k"]?.payload)
    }

    @Test
    fun writeAllKeepsTheOlderRowOfAnOversizedKeyAndStoresTheSmallOne() = runTest {
        store.write(key = "old", payload = "{}", updatedAt = 1)

        store.writeAll(
            mapOf(
                "old" to StoredPayload("a".repeat(RoomPersistentPayloadStore.MAX_ROW_BYTES + 1), 2),
                "other" to StoredPayload("{}", 2),
            )
        )

        assertEquals(setOf("old", "other"), dao.rows.keys)
        assertEquals("{}", dao.rows["old"]?.payload)
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "ж", "中", "😀"])
    fun writeStoresAPayloadOfExactlyTheLimitAndRefusesOneByteMore(unit: String) = runTest {
        val head = unit.repeat(1_000)
        val exact = head + "a".repeat(RoomPersistentPayloadStore.MAX_ROW_BYTES - head.encodeToByteArray().size)

        store.write(key = "exact", payload = exact, updatedAt = 1)
        store.write(key = "over", payload = exact + "a", updatedAt = 1)

        assertEquals(setOf("exact"), dao.rows.keys)
    }

    @Test
    fun writeRefusesACyrillicPayloadUnderTheLimitInCharsButOverItInBytes() = runTest {
        store.write(key = "k", payload = "ж".repeat(RoomPersistentPayloadStore.MAX_ROW_BYTES / 2 + 1), updatedAt = 1)

        assertFalse("k" in dao.rows)
    }

    @Test
    fun writeAllStartedBeforeAClearDoesNotSurviveIt() = runTest {
        val writeReached = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        dao.onUpsert = {
            writeReached.complete(Unit)
            releaseWrite.await()
        }
        val write = async {
            store.writeAll(
                mapOf(
                    "big" to StoredPayload("a".repeat(RoomPersistentPayloadStore.MAX_ROW_BYTES + 1), 1),
                    "small" to StoredPayload("{}", 1),
                )
            )
        }
        writeReached.await()

        dao.onUpsert = null
        store.clear()
        releaseWrite.complete(Unit)
        write.await()

        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun readAllDropsAndDeletesOnlyTheRowsOverTheLimit() = runTest {
        dao.upsert(CachedPayloadEntity("small1", "{}", 1))
        dao.upsert(CachedPayloadEntity("big", "a".repeat(RoomPersistentPayloadStore.MAX_ROW_BYTES + 1), 2))
        dao.upsert(CachedPayloadEntity("small2", "[]", 3))

        val read = store.readAll(listOf("small1", "big", "small2"))

        assertEquals(setOf("small1", "small2"), read.keys)
        assertEquals(setOf("small1", "small2"), dao.rows.keys)
    }

    @Test
    fun readAllOfRowsWithinTheLimitDeletesNothing() = runTest {
        store.write(key = "a", payload = "{}", updatedAt = 1)
        store.write(key = "b", payload = "{}", updatedAt = 1)

        store.readAll(listOf("a", "b"))

        assertTrue(dao.calls.none { it.startsWith("deleteOversized") })
    }

    @Test
    fun aMissOnANeverStoredKeyRunsNoDelete() = runTest {
        assertNull(store.read("never"))
        assertTrue(store.readAll(listOf("never", "ever")).isEmpty())

        assertTrue(dao.calls.none { it.startsWith("deleteOversized") })
    }

    @Test
    fun aFailingCleanupStillReturnsTheReadResult() = runTest {
        store.write(key = "small", payload = "{}", updatedAt = 1)
        dao.onOversizedKeys = { error("disk full") }

        assertNull(store.read("never"))
        assertEquals(mapOf("small" to StoredPayload("{}", 1)), store.readAll(listOf("small", "never")))
    }

    @Test
    fun writeStoresAThreeByteCharPayloadOfExactlyTheLimitAndRefusesOneCharMore() = runTest {
        val limit = RoomPersistentPayloadStore.MAX_ROW_BYTES

        store.write(key = "exact", payload = "中".repeat(limit / 3), updatedAt = 1)
        store.write(key = "over", payload = "中".repeat(limit / 3 + 1), updatedAt = 1)

        assertEquals(setOf("exact"), dao.rows.keys)
    }

    @Test
    fun writeAfterAClearIsKept() = runTest {
        store.clear()

        store.write(key = "item:1", payload = "{}", updatedAt = 1)

        assertEquals(StoredPayload("{}", 1), store.read("item:1"))
    }

    @Test
    fun removeByPrefixLeavesOtherNamespaces() = runTest {
        store.write(key = "home:hot", payload = "[]", updatedAt = 1)
        store.write(key = "item:1", payload = "{}", updatedAt = 1)

        store.removeByPrefix("home:")

        assertNull(store.read("home:hot"))
        assertEquals(StoredPayload("{}", 1), store.read("item:1"))
    }

    @Test
    fun sweepDeletesByAgeThenTrimsToTheNewest() = runTest {
        store.write(key = "a", payload = "{}", updatedAt = 10)
        store.write(key = "b", payload = "{}", updatedAt = 20)
        store.write(key = "c", payload = "{}", updatedAt = 30)
        store.write(key = "d", payload = "{}", updatedAt = 40)

        store.sweep(olderThan = 15, keepNewest = 2)

        assertEquals(listOf("deleteOlderThan:15", "deleteBeyondNewest:2"), dao.calls)
        assertNull(store.read("a"))
        assertNull(store.read("b"))
        assertEquals(StoredPayload("{}", 30), store.read("c"))
        assertEquals(StoredPayload("{}", 40), store.read("d"))
    }

    private class FakeCachedPayloadDao : CachedPayloadDao {
        val rows = mutableMapOf<String, CachedPayloadEntity>()
        val calls = mutableListOf<String>()
        var onUpsert: (suspend () -> Unit)? = null
        var onClear: (suspend () -> Unit)? = null
        var onOversizedKeys: (suspend () -> Unit)? = null

        override suspend fun read(key: String, maxBytes: Int): CachedPayloadEntity? =
            rows[key]?.takeIf { it.fits(maxBytes) }

        override suspend fun readAll(keys: List<String>, maxBytes: Int): List<CachedPayloadEntity> =
            keys.mapNotNull(rows::get).filter { it.fits(maxBytes) }

        override suspend fun oversizedKeys(keys: List<String>, maxBytes: Int): List<String> {
            onOversizedKeys?.invoke()
            return keys.filter { key -> rows[key]?.fits(maxBytes) == false }
        }

        override suspend fun deleteOversized(keys: List<String>, maxBytes: Int) {
            calls += "deleteOversized:$keys"
            keys.filter { key -> rows[key]?.fits(maxBytes) == false }.forEach(rows::remove)
        }

        private fun CachedPayloadEntity.fits(maxBytes: Int) = payload.encodeToByteArray().size <= maxBytes

        override suspend fun upsert(entity: CachedPayloadEntity) {
            onUpsert?.invoke()
            rows[entity.key] = entity
        }

        override suspend fun upsertAll(entities: List<CachedPayloadEntity>) {
            entities.forEach { upsert(it) }
        }

        override suspend fun touch(key: String, updatedAt: Long) {
            rows[key]?.let { rows[key] = it.copy(updatedAt = updatedAt) }
        }

        override suspend fun delete(key: String) {
            rows.remove(key)
        }

        override suspend fun deleteByPrefix(prefix: String) {
            rows.keys.filter { it.startsWith(prefix) }.forEach(rows::remove)
        }

        override suspend fun deleteOlderThan(cutoff: Long) {
            calls += "deleteOlderThan:$cutoff"
            rows.values.filter { it.updatedAt < cutoff }.forEach { rows.remove(it.key) }
        }

        override suspend fun deleteBeyondNewest(keepNewest: Int) {
            calls += "deleteBeyondNewest:$keepNewest"
            rows.values.sortedByDescending { it.updatedAt }.drop(keepNewest).forEach { rows.remove(it.key) }
        }

        override suspend fun clear() {
            onClear?.invoke()
            rows.clear()
        }
    }
}
