package com.kino.puber.data.repository

import com.kino.puber.data.db.CachedPayloadDao
import com.kino.puber.data.db.CachedPayloadEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PersistentPayloadStoreTest {

    private val dao = FakeCachedPayloadDao()
    private val store = RoomPersistentPayloadStore(dao)

    @Test
    fun writeThenReadRoundTrips() = runTest {
        store.write(key = "item:1", payload = "{\"a\":1}", updatedAt = 100)

        assertEquals(StoredPayload("{\"a\":1}", 100), store.read("item:1"))
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

    private class FakeCachedPayloadDao : CachedPayloadDao {
        private val rows = mutableMapOf<String, CachedPayloadEntity>()
        var onUpsert: (suspend () -> Unit)? = null
        var onClear: (suspend () -> Unit)? = null

        override suspend fun read(key: String): CachedPayloadEntity? = rows[key]

        override suspend fun upsert(entity: CachedPayloadEntity) {
            onUpsert?.invoke()
            rows[entity.key] = entity
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

        override suspend fun clear() {
            onClear?.invoke()
            rows.clear()
        }
    }
}
