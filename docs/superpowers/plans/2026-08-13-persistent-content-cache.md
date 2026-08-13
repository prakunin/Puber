# Persistent Content Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the home sections and a title's details screen from on-device storage immediately, then refresh them in the background, so opening something already being watched costs no visible network wait.

**Architecture:** One Room table stores JSON of the existing `@Serializable` API models under string keys. A `CachedFeed` primitive reads that table, emits what it finds at once, and revalidates over the network behind the emission. `HomeInteractor` and `ItemDetailsRepository` are the only owners of the key space; `HomeVM` and `DetailsVM` consume flows and paint as values arrive.

**Tech Stack:** Kotlin, Room, kotlinx.serialization, kotlinx.coroutines Flow, Koin, JUnit 5 + MockK (unit), AndroidJUnit4 (DAO).

**Spec:** `docs/superpowers/specs/2026-08-13-persistent-content-cache-design.md`

## Global Constraints

- JDK 17 and Android SDK 36. Use `./gradlew` in the main checkout, `./tools/agentw <task>` in a git worktree.
- Fastest correctness check: `./gradlew :app:compileDevDebugKotlin`. Unit tests: `./gradlew testDevDebugUnitTest`. Lint: `./gradlew :app:detektAll`.
- Dependency versions come from `gradle/libs.versions.toml`; SDK/Java versions from `buildSrc/src/main/kotlin/Versions.kt`. Never hardcode either. This plan adds no new dependencies.
- Generated file content and commit messages are English.
- Detekt runs with `buildUponDefaultConfig = false` against `config/detekt/detekt.yml`. `LongParameterList` is suppressed per-declaration where the codebase already does so.
- No new user-visible strings are introduced. If one becomes necessary it goes in `res/values/strings.xml` and is read through `ResourceProvider`.
- Room DAO tests live in `app/src/androidTest/kotlin`; everything above the DAO is unit-tested in `app/src/test/kotlin` against a fake store. There is no Robolectric in this project.
- Payloads contain parsed models only. No tokens, no credential-bearing URLs.

---

### Task 1: Cached payload table and DAO

**Files:**
- Create: `app/src/main/java/com/kino/puber/data/db/CachedPayloadEntity.kt`
- Create: `app/src/main/java/com/kino/puber/data/db/CachedPayloadDao.kt`
- Modify: `app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt:8-17`
- Modify: `app/src/main/java/com/kino/puber/data/di/modules.kt:83-86`
- Test: `app/src/androidTest/kotlin/com/kino/puber/data/db/CachedPayloadDaoTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `CachedPayloadEntity(key: String, payload: String, updatedAt: Long)`; `CachedPayloadDao` with `suspend fun read(key: String): CachedPayloadEntity?`, `suspend fun upsert(entity: CachedPayloadEntity)`, `suspend fun touch(key: String, updatedAt: Long)`, `suspend fun delete(key: String)`, `suspend fun deleteByPrefix(prefix: String)`, `suspend fun clear()`; `PuberDatabase.cachedPayloadDao(): CachedPayloadDao`.

- [ ] **Step 1: Write the failing DAO test**

Create `app/src/androidTest/kotlin/com/kino/puber/data/db/CachedPayloadDaoTest.kt`:

```kotlin
package com.kino.puber.data.db

import androidx.room.Room
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
        ).build()
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:compileDevDebugAndroidTestKotlin`
Expected: FAIL — unresolved references `CachedPayloadEntity`, `CachedPayloadDao`, `cachedPayloadDao`.

- [ ] **Step 3: Write the entity**

Create `app/src/main/java/com/kino/puber/data/db/CachedPayloadEntity.kt`:

```kotlin
package com.kino.puber.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One cached server response, as JSON of the model the API layer already parses into.
 *
 * The table is a convenience copy of what the server would return, held so a screen can draw before
 * the network answers. Nothing here is authoritative and nothing here is user data the app created:
 * it is dropped wholesale on logout, on a domain switch, and on any schema change.
 *
 * [updatedAt] is when the payload was *read from the server*, in epoch milliseconds. Freshness is
 * judged against it, and marking an entry stale is done by moving it backwards rather than by
 * deleting the row — a readable stale payload is worth more than a spinner.
 */
@Entity(tableName = "cached_payload")
data class CachedPayloadEntity(
    @PrimaryKey
    @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
```

- [ ] **Step 4: Write the DAO**

Create `app/src/main/java/com/kino/puber/data/db/CachedPayloadDao.kt`:

```kotlin
package com.kino.puber.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CachedPayloadDao {

    @Query("SELECT * FROM cached_payload WHERE key = :key")
    suspend fun read(key: String): CachedPayloadEntity?

    @Upsert
    suspend fun upsert(entity: CachedPayloadEntity)

    @Query("UPDATE cached_payload SET updated_at = :updatedAt WHERE key = :key")
    suspend fun touch(key: String, updatedAt: Long)

    @Query("DELETE FROM cached_payload WHERE key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM cached_payload WHERE key LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)

    @Query("DELETE FROM cached_payload")
    suspend fun clear()
}
```

- [ ] **Step 5: Register the entity and bump the schema version**

In `app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt`, replace the `@Database` annotation and add the accessor:

```kotlin
@Database(
    entities = [WatchStateEntity::class, WatchStateSyncEntity::class, CachedPayloadEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class PuberDatabase : RoomDatabase() {

    abstract fun watchStateDao(): WatchStateDao

    abstract fun watchStateSyncDao(): WatchStateSyncDao

    abstract fun cachedPayloadDao(): CachedPayloadDao
```

Leave the `create` function and its `fallbackToDestructiveMigration(dropAllTables = true)` untouched — the existing comment already covers why version 2 needs no hand-written migration.

- [ ] **Step 6: Register the DAO in Koin**

In `app/src/main/java/com/kino/puber/data/di/modules.kt`, after the line `single { get<PuberDatabase>().watchStateSyncDao() }`, add:

```kotlin
    single { get<PuberDatabase>().cachedPayloadDao() }
```

- [ ] **Step 7: Run the DAO test on a device or emulator**

Run: `./gradlew :app:connectedDevDebugAndroidTest --tests "com.kino.puber.data.db.CachedPayloadDaoTest"`
Expected: PASS, 5 tests.

If no device is attached, run `./gradlew :app:compileDevDebugAndroidTestKotlin` (expected: BUILD SUCCESSFUL) and record in the commit body that the DAO test still needs a device run.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/kino/puber/data/db/CachedPayloadEntity.kt \
        app/src/main/java/com/kino/puber/data/db/CachedPayloadDao.kt \
        app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt \
        app/src/main/java/com/kino/puber/data/di/modules.kt \
        app/src/androidTest/kotlin/com/kino/puber/data/db/CachedPayloadDaoTest.kt \
        app/schemas
git commit -m "$(cat <<'EOF'
Add a table for cached server payloads

Holds one JSON payload per key with the time it was read from the server,
so a screen can draw before the network answers.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Payload store with a session generation guard

**Files:**
- Create: `app/src/main/java/com/kino/puber/data/repository/PersistentPayloadStore.kt`
- Modify: `app/src/main/java/com/kino/puber/data/di/modules.kt` (repositoryModule)
- Test: `app/src/test/kotlin/com/kino/puber/data/repository/PersistentPayloadStoreTest.kt`

**Interfaces:**
- Consumes: `CachedPayloadDao` from Task 1.
- Produces:

```kotlin
interface PersistentPayloadStore {
    suspend fun read(key: String): StoredPayload?
    suspend fun write(key: String, payload: String, updatedAt: Long)
    suspend fun touch(key: String, updatedAt: Long)
    suspend fun remove(key: String)
    suspend fun removeByPrefix(prefix: String)
    suspend fun clear()
}

data class StoredPayload(val payload: String, val updatedAt: Long)
```

`RoomPersistentPayloadStore(dao: CachedPayloadDao)` implements it.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/kino/puber/data/repository/PersistentPayloadStoreTest.kt`:

```kotlin
package com.kino.puber.data.repository

import com.kino.puber.data.db.CachedPayloadDao
import com.kino.puber.data.db.CachedPayloadEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
            rows.clear()
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.data.repository.PersistentPayloadStoreTest"`
Expected: FAIL — unresolved references `RoomPersistentPayloadStore`, `StoredPayload`.

- [ ] **Step 3: Write the store**

Create `app/src/main/java/com/kino/puber/data/repository/PersistentPayloadStore.kt`:

```kotlin
package com.kino.puber.data.repository

import com.kino.puber.data.db.CachedPayloadDao
import com.kino.puber.data.db.CachedPayloadEntity

/** A stored payload and the moment its content was read from the server, in epoch milliseconds. */
data class StoredPayload(
    val payload: String,
    val updatedAt: Long,
)

interface PersistentPayloadStore {
    suspend fun read(key: String): StoredPayload?
    suspend fun write(key: String, payload: String, updatedAt: Long)
    suspend fun touch(key: String, updatedAt: Long)
    suspend fun remove(key: String)
    suspend fun removeByPrefix(prefix: String)
    suspend fun clear()
}

class RoomPersistentPayloadStore(
    private val dao: CachedPayloadDao,
) : PersistentPayloadStore {

    /**
     * Bumped by every [clear], so a write that began under the previous session can notice the
     * session ended and take its row back out.
     *
     * The cache holds one account's viewing history. A revalidation already in flight when the user
     * signs out would otherwise land after the wipe and hand that history to the next account. Same
     * hazard, and the same remedy, as the watch-state sync.
     */
    @Volatile
    private var generation = 0L

    override suspend fun read(key: String): StoredPayload? {
        return dao.read(key)?.let { row -> StoredPayload(payload = row.payload, updatedAt = row.updatedAt) }
    }

    override suspend fun write(key: String, payload: String, updatedAt: Long) {
        val generation = this.generation
        dao.upsert(CachedPayloadEntity(key = key, payload = payload, updatedAt = updatedAt))
        if (generation != this.generation) {
            dao.delete(key)
        }
    }

    override suspend fun touch(key: String, updatedAt: Long) {
        dao.touch(key = key, updatedAt = updatedAt)
    }

    override suspend fun remove(key: String) {
        dao.delete(key)
    }

    override suspend fun removeByPrefix(prefix: String) {
        dao.deleteByPrefix(prefix)
    }

    override suspend fun clear() {
        generation += 1
        dao.clear()
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.data.repository.PersistentPayloadStoreTest"`
Expected: PASS, 6 tests.

- [ ] **Step 5: Register the store in Koin**

In `app/src/main/java/com/kino/puber/data/di/modules.kt`, inside `repositoryModule`, below the `cachedPayloadDao` line from Task 1, add:

```kotlin
    single<PersistentPayloadStore> { RoomPersistentPayloadStore(dao = get()) }
```

Add the imports `com.kino.puber.data.repository.PersistentPayloadStore` and `com.kino.puber.data.repository.RoomPersistentPayloadStore`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kino/puber/data/repository/PersistentPayloadStore.kt \
        app/src/main/java/com/kino/puber/data/di/modules.kt \
        app/src/test/kotlin/com/kino/puber/data/repository/PersistentPayloadStoreTest.kt
git commit -m "$(cat <<'EOF'
Store cached payloads behind a session generation guard

A revalidation begun before a logout would otherwise land after the wipe
and hand the previous account's history to the next one.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: The `CachedFeed` stale-while-revalidate primitive

**Files:**
- Create: `app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt`
- Test: `app/src/test/kotlin/com/kino/puber/data/cache/CachedFeedTest.kt`

**Interfaces:**
- Consumes: `PersistentPayloadStore`, `StoredPayload` from Task 2.
- Produces:

```kotlin
sealed interface Cached<out V> {
    data class Value<V>(val value: V, val isStale: Boolean) : Cached<V>
    data class RefreshFailed(val error: Throwable) : Cached<Nothing>
}

class CachedFeed<V : Any>(
    store: PersistentPayloadStore,
    serializer: KSerializer<V>,
    ttl: Duration,
    keyPrefix: String,
    json: Json = CachedFeed.DefaultJson,
    clock: () -> Long = System::currentTimeMillis,
) {
    fun load(key: String, force: Boolean = false, loader: suspend () -> V): Flow<Cached<V>>
    suspend fun markStale(key: String)
    suspend fun invalidate(key: String)
    suspend fun invalidateNamespace()
    companion object { val DefaultJson: Json; val HardCeiling: Duration }
}
```

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/kino/puber/data/cache/CachedFeedTest.kt`:

```kotlin
package com.kino.puber.data.cache

import com.kino.puber.data.repository.PersistentPayloadStore
import com.kino.puber.data.repository.StoredPayload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class CachedFeedTest {

    private val store = FakePayloadStore()
    private var now = 1_000_000L

    private fun feed(ttl: kotlin.time.Duration = 10.minutes) = CachedFeed(
        store = store,
        serializer = String.serializer(),
        ttl = ttl,
        keyPrefix = "",
        clock = { now },
    )

    @Test
    fun emptyKeyEmitsOnceFromTheLoader() = runTest {
        val emissions = feed().load("k") { "fresh" }.toList()

        assertEquals(listOf(Cached.Value("fresh", isStale = false)), emissions)
    }

    @Test
    fun theLoaderResultIsStoredWithTheCurrentTime() = runTest {
        feed().load("k") { "fresh" }.toList()

        assertEquals(now, store.read("k")?.updatedAt)
    }

    @Test
    fun freshStoredValueEmitsOnceWithoutCallingTheLoader() = runTest {
        feed().load("k") { "first" }.toList()
        var loaderCalls = 0

        val emissions = feed().load("k") { loaderCalls += 1; "second" }.toList()

        assertEquals(listOf(Cached.Value("first", isStale = false)), emissions)
        assertEquals(0, loaderCalls)
    }

    @Test
    fun staleStoredValueEmitsTheCachedValueThenTheFreshOne() = runTest {
        feed().load("k") { "first" }.toList()
        now += 11.minutes.inWholeMilliseconds

        val emissions = feed().load("k") { "second" }.toList()

        assertEquals(
            listOf(
                Cached.Value("first", isStale = true),
                Cached.Value("second", isStale = false),
            ),
            emissions,
        )
    }

    @Test
    fun forceRevalidatesEvenWhenTheStoredValueIsFresh() = runTest {
        feed().load("k") { "first" }.toList()

        val emissions = feed().load("k", force = true) { "second" }.toList()

        assertEquals(
            listOf(
                Cached.Value("first", isStale = true),
                Cached.Value("second", isStale = false),
            ),
            emissions,
        )
    }

    @Test
    fun aLoaderFailureAfterACachedEmissionIsReportedAndKeepsTheValue() = runTest {
        feed().load("k") { "first" }.toList()
        now += 11.minutes.inWholeMilliseconds
        val failure = IllegalStateException("offline")

        val emissions = feed().load("k") { throw failure }.toList()

        assertEquals(Cached.Value("first", isStale = true), emissions[0])
        assertEquals(Cached.RefreshFailed(failure), emissions[1])
        assertEquals("\"first\"", store.read("k")?.payload)
    }

    @Test
    fun aLoaderFailureWithNothingCachedThrows() = runTest {
        val failure = IllegalStateException("offline")

        assertThrows<IllegalStateException> {
            feed().load("k") { throw failure }.toList()
        }
    }

    @Test
    fun anEntryPastTheHardCeilingCountsAsAbsent() = runTest {
        feed().load("k") { "ancient" }.toList()
        now += 8.days.inWholeMilliseconds

        val emissions = feed().load("k") { "fresh" }.toList()

        assertEquals(listOf(Cached.Value("fresh", isStale = false)), emissions)
    }

    @Test
    fun anUndecodablePayloadCountsAsAbsentAndIsDropped() = runTest {
        // A model field can change without a schema version changing, so a payload written by an
        // older build must not be able to break the screen it feeds.
        store.write(key = "k", payload = "{not json", updatedAt = now)

        val emissions = CachedFeed(
            store = store,
            serializer = Boxed.serializer(),
            ttl = 10.minutes,
            keyPrefix = "",
            clock = { now },
        ).load("k") { Boxed(7) }.toList()

        assertEquals(listOf(Cached.Value(Boxed(7), isStale = false)), emissions)
    }

    @Test
    fun invalidateNamespaceDropsEveryKeyUnderThePrefix() = runTest {
        val subject = CachedFeed(
            store = store,
            serializer = String.serializer(),
            ttl = 10.minutes,
            keyPrefix = "home:",
            clock = { now },
        )
        subject.load("home:hot") { "a" }.toList()
        subject.load("home:fresh") { "b" }.toList()

        subject.invalidateNamespace()

        assertNull(store.read("home:hot"))
        assertNull(store.read("home:fresh"))
    }

    @Test
    fun concurrentLoadsOfOneKeyShareASingleLoaderCall() = runTest {
        val gate = CompletableDeferred<Unit>()
        var loaderCalls = 0
        val subject = feed()
        val loader: suspend () -> String = {
            loaderCalls += 1
            gate.await()
            "fresh"
        }

        val first = async { subject.load("k", loader = loader).toList() }
        val second = async { subject.load("k", loader = loader).toList() }
        gate.complete(Unit)
        first.await()
        second.await()

        assertEquals(1, loaderCalls)
    }

    @Test
    fun markStaleKeepsThePayloadReadableAndForcesTheNextLoadToRevalidate() = runTest {
        val subject = feed()
        subject.load("k") { "first" }.toList()

        subject.markStale("k")

        assertEquals("\"first\"", store.read("k")?.payload)
        val emissions = subject.load("k") { "second" }.toList()
        assertEquals(
            listOf(
                Cached.Value("first", isStale = true),
                Cached.Value("second", isStale = false),
            ),
            emissions,
        )
    }

    @Test
    fun markStaleLeavesTheEntryInsideTheHardCeiling() = runTest {
        val subject = feed()
        subject.load("k") { "first" }.toList()

        subject.markStale("k")

        val age = now - (store.read("k")?.updatedAt ?: 0L)
        assertTrue(age < CachedFeed.HardCeiling.inWholeMilliseconds)
    }

    @Test
    fun invalidateRemovesTheEntryOutright() = runTest {
        val subject = feed()
        subject.load("k") { "first" }.toList()

        subject.invalidate("k")

        assertNull(store.read("k"))
        val emissions = subject.load("k") { "second" }.toList()
        assertEquals(listOf(Cached.Value("second", isStale = false)), emissions)
    }

    @Serializable
    private data class Boxed(val value: Int)

    private class FakePayloadStore : PersistentPayloadStore {
        private val rows = mutableMapOf<String, StoredPayload>()

        override suspend fun read(key: String): StoredPayload? = rows[key]

        override suspend fun write(key: String, payload: String, updatedAt: Long) {
            rows[key] = StoredPayload(payload = payload, updatedAt = updatedAt)
        }

        override suspend fun touch(key: String, updatedAt: Long) {
            rows[key]?.let { rows[key] = it.copy(updatedAt = updatedAt) }
        }

        override suspend fun remove(key: String) {
            rows.remove(key)
        }

        override suspend fun removeByPrefix(prefix: String) {
            rows.keys.filter { it.startsWith(prefix) }.forEach(rows::remove)
        }

        override suspend fun clear() {
            rows.clear()
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.data.cache.CachedFeedTest"`
Expected: FAIL — unresolved references `CachedFeed`, `Cached`.

- [ ] **Step 3: Write the primitive**

Create `app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt`:

```kotlin
package com.kino.puber.data.cache

import com.kino.puber.core.collections.TypedTtlCacheImpl
import com.kino.puber.data.repository.PersistentPayloadStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** What a [CachedFeed] hands its caller. */
sealed interface Cached<out V> {
    /** A usable value. [isStale] means a revalidation is on its way and a second value will follow. */
    data class Value<V>(val value: V, val isStale: Boolean) : Cached<V>

    /**
     * The revalidation failed after a value was already emitted. The caller keeps showing what it
     * has; this exists so it can say so quietly rather than replace content with an error.
     */
    data class RefreshFailed(val error: Throwable) : Cached<Nothing>
}

/**
 * Serves one namespace of cached values: whatever is stored is emitted at once, and the network is
 * consulted behind that emission.
 *
 * A failure only ever surfaces as an exception when there was nothing to show. Once a value has been
 * emitted, a failed refresh is reported as [Cached.RefreshFailed] and the value stands.
 */
class CachedFeed<V : Any>(
    private val store: PersistentPayloadStore,
    private val serializer: KSerializer<V>,
    private val ttl: Duration,
    private val keyPrefix: String,
    private val json: Json = DefaultJson,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Deduplicates loader calls so two screens asking for the same key at the same moment cost one
     * request. Its own TTL is the feed's, so a value it still holds is by definition fresh.
     */
    private val inFlight = TypedTtlCacheImpl<String, V>(defaultTtl = ttl)

    fun load(
        key: String,
        force: Boolean = false,
        loader: suspend () -> V,
    ): Flow<Cached<V>> = flow {
        val stored = readUsable(key)
        var emitted = false
        if (stored != null) {
            val isStale = force || clock() - stored.updatedAt > ttl.inWholeMilliseconds
            emit(Cached.Value(stored.value, isStale = isStale))
            emitted = true
            if (!isStale) return@flow
        }
        if (force) {
            inFlight.remove(key)
        }
        try {
            val fresh = inFlight.getOrPut(key) {
                loader().also { value ->
                    store.write(
                        key = key,
                        payload = json.encodeToString(serializer, value),
                        updatedAt = clock(),
                    )
                }
            }
            emit(Cached.Value(fresh, isStale = false))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            if (!emitted) throw error
            emit(Cached.RefreshFailed(error))
        }
    }

    /**
     * Keeps the payload readable but guarantees the next [load] revalidates it.
     *
     * Used where something happened that probably changed the server's answer but the entry is still
     * worth drawing — a saved playback position, for instance. Deleting the row instead would trade a
     * slightly stale screen for a spinner.
     */
    suspend fun markStale(key: String) {
        inFlight.remove(key)
        store.touch(key = key, updatedAt = clock() - ttl.inWholeMilliseconds - 1)
    }

    /** Drops the entry, so the next [load] has nothing to emit before the network answers. */
    suspend fun invalidate(key: String) {
        inFlight.remove(key)
        store.remove(key)
    }

    /** Drops every entry in this feed's namespace. */
    suspend fun invalidateNamespace() {
        inFlight.clear()
        store.removeByPrefix(keyPrefix)
    }

    private suspend fun readUsable(key: String): Usable<V>? {
        val stored = store.read(key) ?: return null
        if (clock() - stored.updatedAt > HardCeiling.inWholeMilliseconds) return null
        val value = runCatching { json.decodeFromString(serializer, stored.payload) }.getOrElse {
            // Written by a build whose model differed. Nothing to salvage, and keeping it would make
            // every future read pay the same failed decode.
            store.remove(key)
            return null
        }
        return Usable(value = value, updatedAt = stored.updatedAt)
    }

    private class Usable<V>(val value: V, val updatedAt: Long)

    companion object {
        /**
         * Past this, a stored payload is treated as absent. A screen drawn from week-old data is
         * worse than one that admits it is loading.
         */
        val HardCeiling: Duration = 7.days

        val DefaultJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.data.cache.CachedFeedTest"`
Expected: PASS, 14 tests.

- [ ] **Step 5: Run Detekt**

Run: `./gradlew :app:detektAll`
Expected: BUILD SUCCESSFUL. If `ReturnCount` or `TooGenericExceptionCaught` fires on `load`, add the same targeted `@Suppress` the codebase already uses at the failing declaration rather than editing `config/detekt/detekt.yml`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt \
        app/src/test/kotlin/com/kino/puber/data/cache/CachedFeedTest.kt
git commit -m "$(cat <<'EOF'
Add a stale-while-revalidate feed over the payload store

Emits what is stored at once and consults the network behind it. A failed
refresh only throws when there was nothing to show.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Cache keys and TTL policy

**Files:**
- Create: `app/src/main/java/com/kino/puber/data/cache/CacheKeys.kt`
- Test: `app/src/test/kotlin/com/kino/puber/data/cache/CacheKeysTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `CacheKeys` with `HomePrefix`, `ItemPrefix`, `SimilarPrefix`, `fun item(id: Int): String`, `fun similar(id: Int): String`, `fun home(section: String): String`; `CacheTtl` with `ContinueWatching`, `HomeSection`, `ItemDetails`, `SimilarItems`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/kino/puber/data/cache/CacheKeysTest.kt`:

```kotlin
package com.kino.puber.data.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class CacheKeysTest {

    @Test
    fun itemKeysCarryTheItemNamespace() {
        assertEquals("item:42", CacheKeys.item(42))
        assertTrue(CacheKeys.item(42).startsWith(CacheKeys.ItemPrefix))
    }

    @Test
    fun similarKeysAreDistinctFromItemKeys() {
        assertEquals("similar:42", CacheKeys.similar(42))
    }

    @Test
    fun homeKeysCarryTheHomeNamespace() {
        assertEquals("home:hot", CacheKeys.home("hot"))
        assertTrue(CacheKeys.home("hot").startsWith(CacheKeys.HomePrefix))
    }

    @Test
    fun continueWatchingIsRefreshedFarSoonerThanTheRestOfHome() {
        // It is the one row a finished episode makes wrong immediately.
        assertEquals(2.minutes, CacheTtl.ContinueWatching)
        assertTrue(CacheTtl.ContinueWatching < CacheTtl.HomeSection)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.data.cache.CacheKeysTest"`
Expected: FAIL — unresolved references `CacheKeys`, `CacheTtl`.

- [ ] **Step 3: Write the keys and policy**

Create `app/src/main/java/com/kino/puber/data/cache/CacheKeys.kt`:

```kotlin
package com.kino.puber.data.cache

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The whole key space of the payload cache.
 *
 * Keys are built here and nowhere else. The store takes plain strings, so the only thing keeping two
 * namespaces from colliding — or a wipe by prefix from taking more than it meant to — is that every
 * key in the app comes from this object.
 */
object CacheKeys {

    const val HomePrefix = "home:"
    const val ItemPrefix = "item:"
    const val SimilarPrefix = "similar:"

    fun home(section: String): String = HomePrefix + section

    fun item(id: Int): String = ItemPrefix + id

    fun similar(id: Int): String = SimilarPrefix + id
}

/** How long each kind of payload is served without consulting the server. */
object CacheTtl {

    /** The row a finished episode makes wrong at once, so it is barely cached at all. */
    val ContinueWatching: Duration = 2.minutes

    /** Editorial rows. They move on the server's schedule, not the user's. */
    val HomeSection: Duration = 30.minutes

    val ItemDetails: Duration = 10.minutes

    val SimilarItems: Duration = 30.minutes
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.data.cache.CacheKeysTest"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kino/puber/data/cache/CacheKeys.kt \
        app/src/test/kotlin/com/kino/puber/data/cache/CacheKeysTest.kt
git commit -m "$(cat <<'EOF'
Collect the cache key space and TTL policy in one place

Every key in the app is built here, so two namespaces cannot collide and a
wipe by prefix cannot take more than it meant to.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Disk tier for item details

**Files:**
- Modify: `app/src/main/java/com/kino/puber/data/repository/ItemDetailsRepository.kt` (whole file)
- Modify: `app/src/main/java/com/kino/puber/data/di/modules.kt` (the `singleOf(::ItemDetailsRepository)` line)
- Test: `app/src/test/kotlin/com/kino/puber/data/repository/ItemDetailsRepositoryTest.kt`

**Interfaces:**
- Consumes: `CachedFeed`, `Cached`, `CacheKeys`, `CacheTtl` from Tasks 3-4; `PersistentPayloadStore` from Task 2.
- Produces, on `ItemDetailsRepository`:

```kotlin
fun observeItemDetails(id: Int, force: Boolean = false): Flow<Cached<Item>>
suspend fun getItemDetails(id: Int): Item          // unchanged signature
suspend fun refresh(id: Int): Item                  // unchanged signature
suspend fun invalidate(itemId: Int)                 // now suspend
suspend fun markStale(itemId: Int)                  // new
suspend fun clear()                                 // now suspend
fun observeSimilarItems(id: Int): Flow<Cached<List<Item>>>
```

Note for the implementer: `invalidate` and `clear` become `suspend`. Every existing caller
(`DetailsInteractor`, `SavedItemInteractor`, `HistoryInteractor`, `BookmarkInteractor`,
`PlayerInteractor`, `ApiDomainInteractor`) already calls them from a `suspend` function except
`ApiDomainInteractor.clearDomainSensitiveCaches()`, which Task 9 converts.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/kino/puber/data/repository/ItemDetailsRepositoryTest.kt`:

```kotlin
package com.kino.puber.data.repository

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.ApiResponse
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.cache.Cached
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ItemDetailsRepositoryTest {

    private val api = mockk<KinoPubApiClient>(relaxed = true)
    private val watchStateRepository = mockk<WatchStateRepository>(relaxed = true)
    private val store = InMemoryPayloadStore()
    private val repository = ItemDetailsRepository(
        api = api,
        watchStateRepository = watchStateRepository,
        store = store,
    )

    @Test
    fun observeItemDetailsEmitsOnceWhenNothingIsStored() = runTest {
        givenApiReturns(item(42, "Fresh"))

        val emissions = repository.observeItemDetails(42).toList()

        assertEquals(listOf(Cached.Value(item(42, "Fresh"), isStale = false)), emissions)
    }

    @Test
    fun observeItemDetailsServesTheStoredItemOnASecondOpen() = runTest {
        givenApiReturns(item(42, "Fresh"))
        repository.observeItemDetails(42).toList()

        val emissions = repository.observeItemDetails(42).toList()

        assertEquals(listOf(Cached.Value(item(42, "Fresh"), isStale = false)), emissions)
        coVerify(exactly = 1) { api.getItemDetails(42) }
    }

    @Test
    fun markStaleKeepsServingTheItemButRevalidates() = runTest {
        givenApiReturns(item(42, "Fresh"))
        repository.observeItemDetails(42).toList()
        givenApiReturns(item(42, "Newer"))

        repository.markStale(42)
        val emissions = repository.observeItemDetails(42).toList()

        assertEquals(Cached.Value(item(42, "Fresh"), isStale = true), emissions[0])
        assertEquals(Cached.Value(item(42, "Newer"), isStale = false), emissions[1])
    }

    @Test
    fun invalidateRemovesTheItemSoTheNextOpenWaits() = runTest {
        givenApiReturns(item(42, "Fresh"))
        repository.observeItemDetails(42).toList()
        givenApiReturns(item(42, "Newer"))

        repository.invalidate(42)
        val emissions = repository.observeItemDetails(42).toList()

        assertEquals(listOf(Cached.Value(item(42, "Newer"), isStale = false)), emissions)
    }

    @Test
    fun everyItemReadSharpensTheWatchStateIndex() = runTest {
        val fetched = item(42, "Fresh")
        givenApiReturns(fetched)

        repository.observeItemDetails(42).toList()

        coVerify(exactly = 1) { watchStateRepository.recordFromServer(listOf(fetched)) }
    }

    @Test
    fun getItemDetailsStillReturnsASingleValue() = runTest {
        givenApiReturns(item(42, "Fresh"))

        assertEquals(item(42, "Fresh"), repository.getItemDetails(42))
    }

    @Test
    fun clearDropsBothNamespaces() = runTest {
        givenApiReturns(item(42, "Fresh"))
        repository.observeItemDetails(42).toList()

        repository.clear()

        assertEquals(null, store.read("item:42"))
    }

    private fun givenApiReturns(value: Item) {
        coEvery { api.getItemDetails(value.id) } returns Result.success(ApiResponse(item = value))
    }

    private fun item(id: Int, title: String) = Item(id = id, title = title, type = ItemType.MOVIE)

    private class InMemoryPayloadStore : PersistentPayloadStore {
        private val rows = mutableMapOf<String, StoredPayload>()

        override suspend fun read(key: String): StoredPayload? = rows[key]

        override suspend fun write(key: String, payload: String, updatedAt: Long) {
            rows[key] = StoredPayload(payload = payload, updatedAt = updatedAt)
        }

        override suspend fun touch(key: String, updatedAt: Long) {
            rows[key]?.let { rows[key] = it.copy(updatedAt = updatedAt) }
        }

        override suspend fun remove(key: String) {
            rows.remove(key)
        }

        override suspend fun removeByPrefix(prefix: String) {
            rows.keys.filter { it.startsWith(prefix) }.forEach(rows::remove)
        }

        override suspend fun clear() {
            rows.clear()
        }
    }
}
```

Before running, confirm the wrapper type returned by `api.getItemDetails` — the current
implementation reads `api.getItemDetails(id).getOrThrow().item!!`. If the wrapper is not named
`ApiResponse`, use the actual name from `app/src/main/java/com/kino/puber/data/api/models/Models.kt`
in `givenApiReturns` and in the import list.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.data.repository.ItemDetailsRepositoryTest"`
Expected: FAIL — `ItemDetailsRepository` has no `store` parameter and no `observeItemDetails`.

- [ ] **Step 3: Rewrite the repository**

Replace the whole of `app/src/main/java/com/kino/puber/data/repository/ItemDetailsRepository.kt`:

```kotlin
package com.kino.puber.data.repository

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.cache.Cached
import com.kino.puber.data.cache.CachedFeed
import com.kino.puber.data.cache.CacheKeys
import com.kino.puber.data.cache.CacheTtl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.last
import kotlinx.serialization.builtins.ListSerializer

class ItemDetailsRepository(
    private val api: KinoPubApiClient,
    private val watchStateRepository: WatchStateRepository,
    store: PersistentPayloadStore,
) {

    private val details = CachedFeed(
        store = store,
        serializer = Item.serializer(),
        ttl = CacheTtl.ItemDetails,
        keyPrefix = CacheKeys.ItemPrefix,
    )

    private val similar = CachedFeed(
        store = store,
        serializer = ListSerializer(Item.serializer()),
        ttl = CacheTtl.SimilarItems,
        keyPrefix = CacheKeys.SimilarPrefix,
    )

    fun observeItemDetails(id: Int, force: Boolean = false): Flow<Cached<Item>> {
        return details.load(CacheKeys.item(id), force = force) { fetchItem(id) }
    }

    fun observeSimilarItems(id: Int): Flow<Cached<List<Item>>> {
        return similar.load(CacheKeys.similar(id)) {
            api.getSimilarItems(id).getOrThrow().items.orEmpty()
        }
    }

    /** The one-shot read, for callers that need a value rather than a screen that repaints. */
    suspend fun getItemDetails(id: Int): Item {
        return observeItemDetails(id).lastValue()
    }

    suspend fun refresh(id: Int): Item {
        return observeItemDetails(id, force = true).lastValue()
    }

    suspend fun markStale(itemId: Int) {
        details.markStale(CacheKeys.item(itemId))
    }

    suspend fun invalidate(itemId: Int) {
        details.invalidate(CacheKeys.item(itemId))
    }

    suspend fun clear() {
        // Both namespaces belong to this repository, and both describe the same catalogue.
        details.invalidateNamespace()
        similar.invalidateNamespace()
    }

    private suspend fun fetchItem(id: Int): Item {
        return api.getItemDetails(id).getOrThrow().item!!.also { item ->
            // Details do carry watch fields, unlike the catalogue. Every opened title is a free
            // chance to sharpen the local index.
            watchStateRepository.recordFromServer(listOf(item))
        }
    }

    private suspend fun Flow<Cached<Item>>.lastValue(): Item {
        return when (val terminal = last()) {
            is Cached.Value -> terminal.value
            is Cached.RefreshFailed -> throw terminal.error
        }
    }
}
```

- [ ] **Step 4: Update the Koin registration**

In `app/src/main/java/com/kino/puber/data/di/modules.kt`, replace `singleOf(::ItemDetailsRepository)` with:

```kotlin
    single { ItemDetailsRepository(api = get(), watchStateRepository = get(), store = get()) }
```

- [ ] **Step 5: Make the now-suspend calls compile**

`invalidate` and `clear` became `suspend`. Run `./gradlew :app:compileDevDebugKotlin` and fix each
reported call site. All of them except one are already inside `suspend` functions and need no
change; `ApiDomainInteractor.clearDomainSensitiveCaches()` is not, and Task 9 rewrites it. For now,
make `clearDomainSensitiveCaches` a `suspend fun` and mark its two callers (`resetToDefault`,
`applyEndpoint`) `suspend` as well, following the compiler until the module builds.

- [ ] **Step 6: Run the tests**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.data.repository.ItemDetailsRepositoryTest" --tests "com.kino.puber.data.cache.CachedFeedTest"`
Expected: PASS, 7 + 14 tests.

Then run the suites that mock this repository:
Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.domain.interactor.*" --tests "com.kino.puber.ui.feature.history.vm.*"`
Expected: PASS. MockK relaxed mocks absorb the added methods; a failure here means a call site changed behaviour rather than shape, and must be investigated before continuing.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/kino/puber/data/repository/ItemDetailsRepository.kt \
        app/src/main/java/com/kino/puber/data/di/modules.kt \
        app/src/main/java/com/kino/puber/domain/interactor/api/ApiDomainInteractor.kt \
        app/src/test/kotlin/com/kino/puber/data/repository/ItemDetailsRepositoryTest.kt \
        app/src/test/kotlin/com/kino/puber/data/cache/CachedFeedTest.kt
git commit -m "$(cat <<'EOF'
Back item details with the persistent cache

Details and similar items now survive a restart, and the repository can
mark an entry stale instead of only being able to drop it.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Stop playback progress from evicting the details cache

**Files:**
- Modify: `app/src/main/java/com/kino/puber/domain/interactor/player/PlayerInteractor.kt:241-245`
- Test: `app/src/test/kotlin/com/kino/puber/domain/interactor/player/PlayerInteractorTest.kt`

**Interfaces:**
- Consumes: `ItemDetailsRepository.markStale(itemId: Int)` from Task 5.
- Produces: no new API.

- [ ] **Step 1: Write the failing test**

Append to `app/src/test/kotlin/com/kino/puber/domain/interactor/player/PlayerInteractorTest.kt`, inside the existing class, matching its existing mock field names:

```kotlin
    @Test
    fun saveWatchingTimeMarksTheDetailsStaleInsteadOfDroppingThem() = runTest {
        // A position is saved every few seconds while playing. Dropping the entry each time is why
        // the details cache never survived long enough to spare the user a spinner.
        coEvery { api.setWatchingTime(any(), any(), any(), any()) } returns Result.success(Unit)

        interactor.saveWatchingTime(id = 42, videoNumber = 1, time = 30)

        coVerify(exactly = 1) { itemDetailsRepository.markStale(42) }
        coVerify(exactly = 0) { itemDetailsRepository.invalidate(42) }
    }

    @Test
    fun markAsWatchedStillDropsTheDetails() = runTest {
        // Unlike a position, a watched mark changes what the server would return in ways the cached
        // payload cannot be patched into agreeing with.
        coEvery { api.toggleWatchingStatus(any(), any(), any(), any()) } returns
            Result.success(WatchingToggleResponse(watched = 1))

        interactor.markAsWatched(id = 42)

        coVerify(exactly = 1) { itemDetailsRepository.invalidate(42) }
    }
```

Check the existing file's imports and the exact signature of `api.setWatchingTime` and
`api.toggleWatchingStatus` before running; add `io.mockk.coVerify` and any missing model import if
they are not already there. If `setWatchingTime` returns something other than `Result<Unit>`, mirror
whatever the other tests in the file stub it with.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.domain.interactor.player.PlayerInteractorTest"`
Expected: FAIL — `saveWatchingTimeMarksTheDetailsStaleInsteadOfDroppingThem` fails on the `markStale` verification because the code still calls `invalidate`.

- [ ] **Step 3: Change the call**

In `app/src/main/java/com/kino/puber/domain/interactor/player/PlayerInteractor.kt`, replace the body of `saveWatchingTime`:

```kotlin
    suspend fun saveWatchingTime(id: Int, videoNumber: Int, time: Int, season: Int? = null) {
        api.setWatchingTime(id, videoNumber, time, season).getOrThrow()
        // A position write happens every few seconds of playback. Dropping the entry each time is
        // what kept the details cache from ever being warm for the titles watched most; marking it
        // stale still guarantees the next open revalidates, but leaves something to draw meanwhile.
        itemDetailsRepository.markStale(id)
    }
```

Leave `markAsWatched`, `setEpisodeWatched` and `setSeasonWatched` calling `invalidate` — a watched
mark changes the payload in ways it cannot be patched into agreeing with.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.domain.interactor.player.PlayerInteractorTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kino/puber/domain/interactor/player/PlayerInteractor.kt \
        app/src/test/kotlin/com/kino/puber/domain/interactor/player/PlayerInteractorTest.kt
git commit -m "$(cat <<'EOF'
Mark details stale on a position write instead of dropping them

A position is saved every few seconds of playback, so evicting the entry
each time is what kept the cache cold for exactly the titles watched most.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Details screen draws from cache and stops blocking on the bookmark lookup

**Files:**
- Modify: `app/src/main/java/com/kino/puber/domain/interactor/details/DetailsInteractor.kt:20-30`
- Modify: `app/src/main/java/com/kino/puber/ui/feature/details/vm/DetailsVM.kt:59-97,410-422`
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/details/vm/DetailsVMCachedLoadTest.kt`

**Interfaces:**
- Consumes: `ItemDetailsRepository.observeItemDetails`, `observeSimilarItems` from Task 5.
- Produces, on `DetailsInteractor`:

```kotlin
fun observeItemDetails(id: Int, force: Boolean = false): Flow<Cached<Item>>
fun observeSimilarItems(id: Int): Flow<Cached<List<Item>>>
fun seededWatchlistFlag(item: Item): Boolean
```

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/kino/puber/ui/feature/details/vm/DetailsVMCachedLoadTest.kt`:

```kotlin
package com.kino.puber.ui.feature.details.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.cache.Cached
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.details.DetailsInteractor
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsScreenParams
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import com.kino.puber.ui.feature.details.model.DetailsScreenUIMapper
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class DetailsVMCachedLoadTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var interactor: DetailsInteractor
    private lateinit var savedItemInteractor: SavedItemInteractor
    private lateinit var mapper: DetailsScreenUIMapper
    private lateinit var errorHandler: ErrorHandler

    private val movie = Item(id = 42, title = "Movie", type = ItemType.MOVIE)

    @BeforeEach
    fun setup() {
        router = mockk(relaxed = true)
        every { router.screens } returns mockk<Screens>(relaxed = true)
        interactor = mockk(relaxed = true)
        savedItemInteractor = mockk(relaxed = true)
        errorHandler = mockk { every { proceed(any()) } returns { } }
        mapper = mockk(relaxed = true)
        every { mapper.map(any(), any()) } returns content()
        every { mapper.mapSimilarItems(any()) } returns emptyList()
        every { interactor.observeSimilarItems(any()) } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.seededWatchlistFlag(any()) } returns false
        coEvery { interactor.isInWatchLaterFolder(any()) } returns false
    }

    @Test
    fun contentIsRenderedBeforeTheBookmarkFolderLookupAnswers() = runTest {
        // For a movie with no inline bookmarks the folder lookup is always a network call. Waiting
        // for it is what made a cached title still show a spinner.
        val lookupGate = CompletableDeferred<Boolean>()
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        coEvery { interactor.isInWatchLaterFolder(movie) } coAnswers { lookupGate.await() }

        val vm = createVM().also { it.testOnStart() }
        runCurrent()

        assertTrue(vm.testStateValue is DetailsScreenState.Content)
    }

    @Test
    fun theResolvedWatchlistFlagPatchesTheContent() = runTest {
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        coEvery { interactor.isInWatchLaterFolder(movie) } returns true

        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, (vm.testStateValue as DetailsScreenState.Content).isInWatchlist)
    }

    @Test
    fun aLateWatchlistPatchIsDroppedAfterTheUserToggled() = runTest {
        // The lookup started before the user pressed the button; letting it land would silently undo
        // what they just did.
        val lookupGate = CompletableDeferred<Boolean>()
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        coEvery { interactor.isInWatchLaterFolder(movie) } coAnswers { lookupGate.await() }
        coEvery { interactor.setMovieBookmarked(42, true) } coAnswers {
            com.kino.puber.domain.interactor.details.MovieBookmarkUpdate(
                isBookmarked = true,
                folderTitle = "Later",
            )
        }
        val vm = createVM().also { it.testOnStart() }
        runCurrent()

        vm.onAction(DetailsAction.WatchlistToggleClicked)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        lookupGate.complete(false)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, (vm.testStateValue as DetailsScreenState.Content).isInWatchlist)
    }

    @Test
    fun aStaleCachedItemIsShownAndThenReplacedByTheFreshOne() = runTest {
        val newer = movie.copy(title = "Movie, updated")
        every { interactor.observeItemDetails(42) } returns flowOf(
            Cached.Value(movie, isStale = true),
            Cached.Value(newer, isStale = false),
        )
        val seen = mutableListOf<Item>()
        every { mapper.map(any(), any()) } answers {
            seen += firstArg<Item>()
            content()
        }

        createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(movie, newer), seen.distinct())
    }

    @Test
    fun aFailedRefreshLeavesTheCachedContentOnScreen() = runTest {
        every { interactor.observeItemDetails(42) } returns flowOf(
            Cached.Value(movie, isStale = true),
            Cached.RefreshFailed(IllegalStateException("offline")),
        )

        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.testStateValue is DetailsScreenState.Content)
    }

    private fun content() = DetailsScreenState.Content(
        details = mockk(relaxed = true),
        info = mockk(relaxed = true),
        buttons = emptyList(),
        isInWatchlist = false,
        isWatched = false,
    )

    private fun createVM() = DetailsVM(
        router = router,
        params = DetailsScreenParams(itemId = 42),
        mapper = mapper,
        interactor = interactor,
        savedItemInteractor = savedItemInteractor,
        resources = FakeResourceProvider(),
        errorHandler = errorHandler,
    )
}
```

Before running, open `app/src/main/java/com/kino/puber/ui/feature/details/model/DetailsScreenParams.kt`
and match the real constructor of `DetailsScreenParams`, and confirm `testOnStart()` and
`testStateValue` are the helpers this codebase's VM tests use (they are, per `HomeVMTest`).

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.ui.feature.details.vm.DetailsVMCachedLoadTest"`
Expected: FAIL — `DetailsInteractor` has no `observeItemDetails`, `observeSimilarItems` or `seededWatchlistFlag`.

- [ ] **Step 3: Extend the interactor**

In `app/src/main/java/com/kino/puber/domain/interactor/details/DetailsInteractor.kt`, replace the
first three functions with:

```kotlin
    fun observeItemDetails(id: Int, force: Boolean = false): Flow<Cached<Item>> {
        return itemDetailsRepository.observeItemDetails(id, force = force)
    }

    fun observeSimilarItems(id: Int): Flow<Cached<List<Item>>> {
        return itemDetailsRepository.observeSimilarItems(id)
    }

    suspend fun getItemDetails(id: Int): Item {
        return itemDetailsRepository.getItemDetails(id)
    }

    suspend fun refreshItemDetails(id: Int): Item {
        return itemDetailsRepository.refresh(id)
    }

    suspend fun getSimilarItems(id: Int): List<Item> {
        return api.getSimilarItems(id).getOrThrow().items.orEmpty()
    }

    /**
     * What the item payload itself says about the watchlist, with no second request.
     *
     * Enough to draw the screen with. For a movie whose payload lists no bookmarks this can be a
     * false negative — [isInWatchLaterFolder] is the authoritative answer, and the caller patches it
     * in once it arrives rather than holding the screen for it.
     */
    fun seededWatchlistFlag(item: Item): Boolean {
        if (item.type.isSeriesLike()) return item.inWatchlist ?: false
        return item.bookmarks.orEmpty().isNotEmpty()
    }
```

Add the imports `com.kino.puber.data.cache.Cached` and `kotlinx.coroutines.flow.Flow`.

- [ ] **Step 4: Rewrite the VM's load path**

In `app/src/main/java/com/kino/puber/ui/feature/details/vm/DetailsVM.kt`, replace `loadData` and
`loadSimilarItems` (lines 63-97) with:

```kotlin
    private var rendered = false
    private var watchlistTouched = false

    private fun loadData(forceRefresh: Boolean = false) {
        launch {
            interactor.observeItemDetails(params.itemId, force = forceRefresh).collect { cached ->
                when (cached) {
                    is Cached.Value -> renderItem(cached.value)
                    is Cached.RefreshFailed -> log(cached.error, "Failed to refresh item details")
                }
            }
        }
    }

    /**
     * Draws the item at once and settles the watchlist flag behind it.
     *
     * The flag the item carries is right for a series and can be a false negative for a movie, but
     * the authoritative answer costs a request. Holding the screen for it is what made an already
     * cached title still show a spinner, so it is patched in instead.
     */
    private fun renderItem(item: Item) {
        currentItem = item
        val seeded = interactor.seededWatchlistFlag(item)
        if (rendered) {
            updateCurrentItem(
                item = item,
                isInWatchlist = (stateValue as? DetailsScreenState.Content)?.isInWatchlist ?: seeded,
            )
            return
        }
        rendered = true
        val mapped = mapDetails(item = item, isInWatchlist = seeded)
        updateViewState(
            mapped.copy(
                seasonsPanelVisible = mapped.initialEpisodeFocusId != null,
            ),
        )
        loadSimilarItems()
        resolveWatchlistFlag(item)
    }

    private fun resolveWatchlistFlag(item: Item) {
        launch {
            val resolved = runCatching { interactor.isInWatchLaterFolder(item) }.getOrNull() ?: return@launch
            // The user may have pressed the button while the lookup was in the air. Their action is
            // the newer fact.
            if (watchlistTouched) return@launch
            updateViewState<DetailsScreenState.Content> {
                copy(isInWatchlist = resolved)
            }
        }
    }

    private fun loadSimilarItems() {
        launch {
            interactor.observeSimilarItems(params.itemId).collect { cached ->
                if (cached !is Cached.Value) return@collect
                updateViewState<DetailsScreenState.Content> {
                    copy(
                        similarItems = mapper.mapSimilarItems(
                            cached.value.filterNot { item -> item.id == params.itemId }
                        )
                    )
                }
            }
        }
    }
```

Add `watchlistTouched = true` as the first statement of `onWatchlistToggle()` (line 255), and reset
`rendered = false` inside `onReturnedContentChanges` immediately before its
`loadData(forceRefresh = true)` call (line 414) so a returned change repaints from scratch.

Add the imports `com.kino.puber.data.cache.Cached` and `com.kino.puber.core.logger.log`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.ui.feature.details.vm.*" --tests "com.kino.puber.domain.interactor.details.DetailsInteractorTest"`
Expected: PASS, including the 5 new tests.

- [ ] **Step 6: Run Detekt and the compile check**

Run: `./gradlew :app:compileDevDebugKotlin :app:detektAll`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/kino/puber/domain/interactor/details/DetailsInteractor.kt \
        app/src/main/java/com/kino/puber/ui/feature/details/vm/DetailsVM.kt \
        app/src/test/kotlin/com/kino/puber/ui/feature/details/vm/DetailsVMCachedLoadTest.kt
git commit -m "$(cat <<'EOF'
Draw the details screen before the bookmark lookup answers

The lookup is a network call for any movie whose payload lists no
bookmarks, so waiting for it made even a cached title show a spinner.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Cached home sections on the interactor

**Files:**
- Modify: `app/src/main/java/com/kino/puber/domain/interactor/home/HomeInteractor.kt` (whole file)
- Modify: `app/src/main/java/com/kino/puber/domain/di/modules.kt`
- Test: `app/src/test/kotlin/com/kino/puber/domain/interactor/home/HomeInteractorCacheTest.kt`

**Interfaces:**
- Consumes: `CachedFeed`, `Cached`, `CacheKeys`, `CacheTtl`, `PersistentPayloadStore`.
- Produces, on `HomeInteractor` (the existing `Result`-returning methods stay, unchanged, as the loaders):

```kotlin
fun observeWatchingItems(force: Boolean = false): Flow<Cached<List<Item>>>
fun observeHotItems(): Flow<Cached<List<Item>>>
fun observeFreshItems(): Flow<Cached<List<Item>>>
fun observePopularMovies(): Flow<Cached<List<Item>>>
fun observePopularSeries(): Flow<Cached<List<Item>>>
fun observeWatchLaterItems(): Flow<Cached<List<Item>>>
fun observeBookmarkItems(): Flow<Cached<List<Item>>>
fun observeCollections(): Flow<Cached<List<KCollection>>>
```

`observeHotItems` and `observeFreshItems` merge their two requests and sort before storing, so one
key holds one rendered row. Hot is sorted by `ratingPercentage` descending, fresh by `updatedAt`
descending — the orderings currently applied in `HomeVM.loadContentSections`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/kino/puber/domain/interactor/home/HomeInteractorCacheTest.kt`:

```kotlin
package com.kino.puber.domain.interactor.home

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.cache.Cached
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.PersistentPayloadStore
import com.kino.puber.data.repository.StoredPayload
import com.kino.puber.domain.interactor.bookmarks.WatchLaterBookmarkInteractor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeInteractorCacheTest {

    private val api = mockk<KinoPubApiClient>(relaxed = true)
    private val watchLaterBookmarkInteractor = mockk<WatchLaterBookmarkInteractor>(relaxed = true)
    private val navigationPreferencesRepository = mockk<NavigationPreferencesRepository>(relaxed = true)
    private val store = InMemoryPayloadStore()

    private val interactor = HomeInteractor(
        api = api,
        watchLaterBookmarkInteractor = watchLaterBookmarkInteractor,
        navigationPreferencesRepository = navigationPreferencesRepository,
        store = store,
    )

    @Test
    fun theWatchingRowIsServedFromStorageOnASecondCall() = runTest {
        coEvery { api.getWatchingList(onlySubscribed = true) } returns Result.success(
            com.kino.puber.data.api.models.ApiResponseList(items = listOf(item(1)))
        )
        interactor.observeWatchingItems().toList()

        val emissions = interactor.observeWatchingItems().toList()

        assertEquals(listOf(Cached.Value(listOf(item(1)), isStale = false)), emissions)
        coVerify(exactly = 1) { api.getWatchingList(onlySubscribed = true) }
    }

    @Test
    fun forcingTheWatchingRowServesStorageThenRevalidates() = runTest {
        coEvery { api.getWatchingList(onlySubscribed = true) } returns Result.success(
            com.kino.puber.data.api.models.ApiResponseList(items = listOf(item(1)))
        )
        interactor.observeWatchingItems().toList()

        val emissions = interactor.observeWatchingItems(force = true).toList()

        assertEquals(Cached.Value(listOf(item(1)), isStale = true), emissions[0])
        assertEquals(Cached.Value(listOf(item(1)), isStale = false), emissions[1])
        coVerify(exactly = 2) { api.getWatchingList(onlySubscribed = true) }
    }

    @Test
    fun theWatchingRowAndTheHotRowUseSeparateKeys() = runTest {
        coEvery { api.getWatchingList(onlySubscribed = true) } returns Result.success(
            com.kino.puber.data.api.models.ApiResponseList(items = listOf(item(1)))
        )

        interactor.observeWatchingItems().toList()

        assertEquals(null, store.read("home:hot"))
        assertEquals(true, store.read("home:continue_watching") != null)
    }

    private fun item(id: Int) = Item(id = id, title = "Item $id", type = ItemType.MOVIE)

    private class InMemoryPayloadStore : PersistentPayloadStore {
        private val rows = mutableMapOf<String, StoredPayload>()

        override suspend fun read(key: String): StoredPayload? = rows[key]

        override suspend fun write(key: String, payload: String, updatedAt: Long) {
            rows[key] = StoredPayload(payload = payload, updatedAt = updatedAt)
        }

        override suspend fun touch(key: String, updatedAt: Long) {
            rows[key]?.let { rows[key] = it.copy(updatedAt = updatedAt) }
        }

        override suspend fun remove(key: String) {
            rows.remove(key)
        }

        override suspend fun removeByPrefix(prefix: String) {
            rows.keys.filter { it.startsWith(prefix) }.forEach(rows::remove)
        }

        override suspend fun clear() {
            rows.clear()
        }
    }
}
```

Confirm the wrapper name and shape returned by `api.getWatchingList` against
`app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt` before running, and adjust the two
`coEvery` blocks if it differs from `ApiResponseList`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.domain.interactor.home.HomeInteractorCacheTest"`
Expected: FAIL — `HomeInteractor` has no `store` parameter and no `observe*` methods.

- [ ] **Step 3: Add the feeds to the interactor**

In `app/src/main/java/com/kino/puber/domain/interactor/home/HomeInteractor.kt`, add the constructor
parameter and the feeds, keeping every existing method exactly as it is:

```kotlin
class HomeInteractor(
    private val api: KinoPubApiClient,
    private val watchLaterBookmarkInteractor: WatchLaterBookmarkInteractor,
    private val navigationPreferencesRepository: NavigationPreferencesRepository,
    store: PersistentPayloadStore,
) {

    private val items = CachedFeed(
        store = store,
        serializer = ListSerializer(Item.serializer()),
        ttl = CacheTtl.HomeSection,
        keyPrefix = CacheKeys.HomePrefix,
    )

    /**
     * A row a finished episode makes wrong at once, so it gets a TTL of its own rather than the
     * half-hour the editorial rows are happy with.
     */
    private val watching = CachedFeed(
        store = store,
        serializer = ListSerializer(Item.serializer()),
        ttl = CacheTtl.ContinueWatching,
        keyPrefix = CacheKeys.HomePrefix,
    )

    private val collections = CachedFeed(
        store = store,
        serializer = ListSerializer(KCollection.serializer()),
        ttl = CacheTtl.HomeSection,
        keyPrefix = CacheKeys.HomePrefix,
    )

    fun observeWatchingItems(force: Boolean = false): Flow<Cached<List<Item>>> {
        return watching.load(CacheKeys.home(CONTINUE_WATCHING_KEY), force = force) {
            getWatchingItems().getOrThrow()
        }
    }

    /**
     * One key holds one rendered row, so the two requests behind hot items are merged and sorted
     * before they are stored rather than after they are read.
     */
    fun observeHotItems(): Flow<Cached<List<Item>>> {
        return items.load(CacheKeys.home(HOT_KEY)) {
            val movies = getHotItems("movie", HOT_ITEMS_COUNT).getOrThrow()
            val series = getHotItems("serial", HOT_ITEMS_COUNT).getOrThrow()
            (movies + series).sortedByDescending { it.ratingPercentage ?: 0 }
        }
    }

    fun observeFreshItems(): Flow<Cached<List<Item>>> {
        return items.load(CacheKeys.home(FRESH_KEY)) {
            val movies = getFreshItems("movie").getOrThrow()
            val series = getFreshItems("serial").getOrThrow()
            (movies + series).sortedByDescending { it.updatedAt.orEmpty() }
        }
    }

    fun observePopularMovies(): Flow<Cached<List<Item>>> {
        return items.load(CacheKeys.home(POPULAR_MOVIES_KEY)) { getPopularByType("movie").getOrThrow() }
    }

    fun observePopularSeries(): Flow<Cached<List<Item>>> {
        return items.load(CacheKeys.home(POPULAR_SERIES_KEY)) { getPopularByType("serial").getOrThrow() }
    }

    fun observeWatchLaterItems(): Flow<Cached<List<Item>>> {
        return items.load(CacheKeys.home(WATCH_LATER_KEY)) { getWatchLaterItems().getOrThrow() }
    }

    fun observeBookmarkItems(): Flow<Cached<List<Item>>> {
        return items.load(CacheKeys.home(BOOKMARKS_KEY)) { getGenericBookmarkItems().getOrThrow() }
    }

    fun observeCollections(): Flow<Cached<List<KCollection>>> {
        return collections.load(CacheKeys.home(COLLECTIONS_KEY)) { getCollections().getOrThrow() }
    }
```

Extend the existing `companion object` at the bottom of the file:

```kotlin
    companion object {
        private const val FIRST_PAGE = 1
        private const val HOT_ITEMS_COUNT = 20
        private const val CONTINUE_WATCHING_KEY = "continue_watching"
        private const val HOT_KEY = "hot"
        private const val FRESH_KEY = "fresh"
        private const val POPULAR_MOVIES_KEY = "popular_movies"
        private const val POPULAR_SERIES_KEY = "popular_series"
        private const val WATCH_LATER_KEY = "watch_later"
        private const val BOOKMARKS_KEY = "bookmarks"
        private const val COLLECTIONS_KEY = "collections"
    }
```

Add imports: `com.kino.puber.data.cache.Cached`, `com.kino.puber.data.cache.CachedFeed`,
`com.kino.puber.data.cache.CacheKeys`, `com.kino.puber.data.cache.CacheTtl`,
`com.kino.puber.data.repository.PersistentPayloadStore`, `kotlinx.coroutines.flow.Flow`,
`kotlinx.serialization.builtins.ListSerializer`.

`HOT_ITEMS_COUNT = 20` moves here from `HomeVM.kt:49`; delete it there in Task 9.

- [ ] **Step 4: Register the new dependency**

`HomeInteractor` is constructed by the home screen's own Koin module, not `interactorModule`. Find it
with:

```bash
grep -rn "HomeInteractor(" --include="*.kt" app/src/main/java
```

Add `store = get()` to that construction.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.domain.interactor.home.HomeInteractorCacheTest"`
Expected: PASS, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kino/puber/domain/interactor/home/HomeInteractor.kt \
        app/src/test/kotlin/com/kino/puber/domain/interactor/home/HomeInteractorCacheTest.kt
git commit -m "$(cat <<'EOF'
Serve each home section from the persistent cache

One key per rendered row, so a failing section cannot poison its
neighbours and the watching row can revalidate on its own schedule.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: Home publishes sections as they arrive

**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/home/vm/HomeVM.kt:48-51,143-237,363-367`
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/home/vm/HomeVMTest.kt` (update)
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/home/vm/HomeVMIncrementalPublishTest.kt` (create)

**Interfaces:**
- Consumes: the `observe*` methods from Task 8.
- Produces: no new public API.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/kino/puber/ui/feature/home/vm/HomeVMIncrementalPublishTest.kt`:

```kotlin
package com.kino.puber.ui.feature.home.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.cache.Cached
import com.kino.puber.domain.interactor.api.ApiDomainAutoResolveResult
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.home.HomeInteractor
import com.kino.puber.domain.interactor.watchstate.CardDisplayChanges
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeUIMapper
import com.kino.puber.ui.feature.home.model.HomeViewState
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class HomeVMIncrementalPublishTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var interactor: HomeInteractor
    private lateinit var mapper: HomeUIMapper
    private lateinit var videoItemMapper: VideoItemUIMapper
    private lateinit var apiDomainInteractor: ApiDomainInteractor
    private lateinit var savedItemInteractor: SavedItemInteractor
    private lateinit var errorHandler: ErrorHandler

    private val displayChanges = MutableSharedFlow<Unit>()
    private val cardDisplayChanges = mockk<CardDisplayChanges> {
        every { changes } returns this@HomeVMIncrementalPublishTest.displayChanges
    }

    @BeforeEach
    fun setup() {
        router = mockk(relaxed = true)
        every { router.screens } returns mockk<Screens>(relaxed = true)
        interactor = mockk(relaxed = true)
        mapper = mockk(relaxed = true)
        videoItemMapper = mockk(relaxed = true)
        apiDomainInteractor = mockk(relaxed = true)
        savedItemInteractor = mockk(relaxed = true)
        errorHandler = mockk { every { proceed(any()) } returns { } }

        coEvery { apiDomainInteractor.autoResolveWorkingDomain() } returns ApiDomainAutoResolveResult.Success(
            state = ApiDomainState(domain = "api.example", customDomain = null),
            changed = false,
        )
        every { interactor.observeWatchingItems(any()) } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.observeHotItems() } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.observeFreshItems() } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.observePopularMovies() } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.observePopularSeries() } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.observeWatchLaterItems() } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.observeBookmarkItems() } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.observeCollections() } returns flowOf(Cached.Value(emptyList(), false))
        every { mapper.mapItemSection(any(), any()) } returns null
        every { mapper.mapCollectionSection(any()) } returns null
        every { videoItemMapper.mapHeroItems(any()) } returns emptyList()
    }

    @Test
    fun theFirstSectionToArrivePublishesContentWithoutWaitingForTheRest() = runTest {
        // The whole point: a stored watching row must not be held behind nine other requests.
        val slowGate = CompletableDeferred<Unit>()
        every { interactor.observeWatchingItems(any()) } returns flowOf(
            Cached.Value(listOf(item(1)), isStale = false)
        )
        every { interactor.observeHotItems() } returns flow {
            slowGate.await()
            emit(Cached.Value(emptyList(), isStale = false))
        }

        val vm = createVM().also { it.testOnStart() }
        runCurrent()

        assertTrue(vm.testStateValue is HomeViewState.Content)
        verify { mapper.mapItemSection(listOf(item(1)), HomeSectionType.ContinueWatching) }
    }

    @Test
    fun aSectionThatFailsLeavesTheOthersOnScreen() = runTest {
        every { interactor.observeHotItems() } returns flow { throw IllegalStateException("offline") }
        every { interactor.observeWatchingItems(any()) } returns flowOf(
            Cached.Value(listOf(item(1)), isStale = false)
        )

        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.testStateValue is HomeViewState.Content)
        verify { mapper.mapItemSection(listOf(item(1)), HomeSectionType.ContinueWatching) }
    }

    @Test
    fun everySectionFailingWithNothingStoredShowsTheErrorScreen() = runTest {
        listOf<() -> Unit>(
            { every { interactor.observeWatchingItems(any()) } returns failing() },
            { every { interactor.observeHotItems() } returns failing() },
            { every { interactor.observeFreshItems() } returns failing() },
            { every { interactor.observePopularMovies() } returns failing() },
            { every { interactor.observePopularSeries() } returns failing() },
            { every { interactor.observeWatchLaterItems() } returns failing() },
            { every { interactor.observeBookmarkItems() } returns failing() },
            { every { interactor.observeCollections() } returns failingCollections() },
        ).forEach { it() }

        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.testStateValue is HomeViewState.Error)
    }

    @Test
    fun resumeForcesOnlyTheWatchingRow() = runTest {
        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        vm.onAction(CommonAction.OnResume)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { interactor.observeWatchingItems(true) }
        verify(exactly = 2) { interactor.observeHotItems() }
    }

    @Test
    fun aFailedRefreshOfOneSectionKeepsTheContentItAlreadyPublished() = runTest {
        every { interactor.observeWatchingItems(any()) } returns flowOf(
            Cached.Value(listOf(item(1)), isStale = true),
            Cached.RefreshFailed(IllegalStateException("offline")),
        )

        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.testStateValue is HomeViewState.Content)
        verify { mapper.mapItemSection(listOf(item(1)), HomeSectionType.ContinueWatching) }
    }

    private fun failing() = flow<Cached<List<Item>>> { throw IllegalStateException("offline") }

    private fun failingCollections() =
        flow<Cached<List<com.kino.puber.data.api.models.KCollection>>> {
            throw IllegalStateException("offline")
        }

    private fun item(id: Int) = Item(id = id, title = "Item $id", type = ItemType.MOVIE)

    private fun createVM() = HomeVM(
        router = router,
        interactor = interactor,
        mapper = mapper,
        videoItemMapper = videoItemMapper,
        apiDomainInteractor = apiDomainInteractor,
        savedItemInteractor = savedItemInteractor,
        cardDisplayChanges = cardDisplayChanges,
        resources = FakeResourceProvider(),
        errorHandler = errorHandler,
    )
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.ui.feature.home.vm.HomeVMIncrementalPublishTest"`
Expected: FAIL — `HomeInteractor` mock has no `observe*` members used by `HomeVM`, which still calls the `Result` methods.

- [ ] **Step 3: Rewrite the section loading**

In `app/src/main/java/com/kino/puber/ui/feature/home/vm/HomeVM.kt`:

Replace the companion object (lines 48-51) with:

```kotlin
    companion object {
        private const val HERO_ITEMS_COUNT = 10
    }
```

Replace `loadedSections` (line 62) and add the neighbouring state:

```kotlin
    /**
     * What each section last returned, keyed by the row it draws.
     *
     * A watched mark landing changes how a card is *drawn*, not what the server would return, so it
     * is re-mapped from this instead of costing another round of section requests. Sections also
     * arrive independently now, so this is what a partial screen is published from.
     */
    private val loadedSections = linkedMapOf<HomeSectionType, List<Item>>()
    private var loadedCollections: List<KCollection>? = null
    private var loadedHotItems: List<Item> = emptyList()
    private var finishedSections = 0
```

Replace `loadContentSections`, `publishSections`, `loadBookmarkSection` and the private
`HomeSections` class (lines 182-237 and 363-367) with:

```kotlin
    private suspend fun loadContentSections(forceWatching: Boolean) = supervisorScope {
        loadedSections.clear()
        loadedCollections = null
        finishedSections = 0

        val sections = listOf(
            HomeSectionType.ContinueWatching to interactor.observeWatchingItems(force = forceWatching),
            HomeSectionType.Hot to interactor.observeHotItems(),
            HomeSectionType.Fresh to interactor.observeFreshItems(),
            HomeSectionType.PopularMovies to interactor.observePopularMovies(),
            HomeSectionType.PopularSeries to interactor.observePopularSeries(),
            HomeSectionType.WatchLater to interactor.observeWatchLaterItems(),
            HomeSectionType.Bookmarks to interactor.observeBookmarkItems(),
        )
        sections.forEach { (type, flow) ->
            launch { collectSection(type, flow) }
        }
        launch { collectCollections() }
    }

    private suspend fun collectSection(type: HomeSectionType, flow: Flow<Cached<List<Item>>>) {
        try {
            flow.collect { cached ->
                when (cached) {
                    is Cached.Value -> {
                        loadedSections[type] = cached.value
                        if (type == HomeSectionType.Hot) loadedHotItems = cached.value
                        publishSections()
                    }
                    is Cached.RefreshFailed -> log(cached.error, "Failed to refresh $type")
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log(error, "Failed to load $type")
        } finally {
            onSectionFinished()
        }
    }

    private suspend fun collectCollections() {
        try {
            interactor.observeCollections().collect { cached ->
                when (cached) {
                    is Cached.Value -> {
                        loadedCollections = cached.value
                        publishSections()
                    }
                    is Cached.RefreshFailed -> log(cached.error, "Failed to refresh collections")
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log(error, "Failed to load collections")
        } finally {
            onSectionFinished()
        }
    }

    /**
     * Shows the error screen only once every section has given up with nothing to show.
     *
     * A single failing row is not worth replacing a working screen over, but a screen that would
     * stay empty forever has to say so rather than spin.
     */
    private fun onSectionFinished() {
        finishedSections += 1
        if (finishedSections < TOTAL_SECTIONS) return
        if (loadedSections.isNotEmpty() || loadedCollections != null) return
        if (stateValue is HomeViewState.Content) return
        updateViewState(
            HomeViewState.Error(
                message = resources.getString(R.string.error_unknown),
                apiDomainDialog = currentDialogState(),
            )
        )
    }

    /** Maps what the sections returned into cards, against whatever the index and settings say now. */
    private fun publishSections() {
        val mapped = listOfNotNull(
            *loadedSections
                .map { (type, items) -> mapper.mapItemSection(items, type) }
                .toTypedArray(),
            loadedCollections?.let { mapper.mapCollectionSection(it) },
        ).sortedBy { it.type.ordinal }

        updateViewState(
            HomeViewState.Content(
                heroItems = videoItemMapper.mapHeroItems(loadedHotItems.take(HERO_ITEMS_COUNT)),
                sections = mapped,
                apiDomainDialog = currentDialogState(),
            )
        )
    }
```

Add `private const val TOTAL_SECTIONS = 8` to the companion object. Change `remapLoadedSections`
(line 79) to call `publishSections()` directly when the state is `Content`. Change the single call
site of `loadContentSections()` inside `loadHome` (line 178) to
`loadContentSections(forceWatching = !showDomainSearch)`.

Add imports: `com.kino.puber.data.cache.Cached`, `kotlinx.coroutines.CancellationException`,
`kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.launch`. Remove the now-unused `async` import
and the `logFailure` helper if nothing else uses it.

Confirm `R.string.error_unknown` exists in `app/src/main/res/values/strings.xml`; if it does not,
use whichever generic error string `dispatchError` paths already rely on rather than adding one.

- [ ] **Step 4: Update the existing `HomeVMTest`**

`HomeVMTest.setup()` stubs the old `Result`-returning methods. Replace those seven `coEvery` lines
with the eight `every { interactor.observe...() } returns flowOf(Cached.Value(emptyList(), false))`
lines from the new test's `setup()`. Then fix the three affected assertions:

- `watchStateSettling_remapsTheCardsWithoutAskingTheServerAgain`: replace
  `coVerify(exactly = 1) { interactor.getWatchingItems() }` with
  `verify(exactly = 1) { interactor.observeWatchingItems(false) }`.
- `returnedChangesAndResume_keepOnlyLatestRefreshRunning`: replace
  `coVerify(exactly = 2) { interactor.getWatchingItems() }` with
  `verify(exactly = 2) { interactor.observeWatchingItems(any()) }`.
- `filteredHotFeedsHeroAndHotSectionWhilePersonalWatchingItemsRemainUnchanged`: the hot/watching
  stubs become `every { interactor.observeHotItems() } returns flowOf(Cached.Value(listOf(filteredHotItem), false))`
  and the watching equivalent. The assertions on `mapHeroItems` and `mapItemSection` stay as they are.

- [ ] **Step 5: Run the tests**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.ui.feature.home.vm.*"`
Expected: PASS — 5 new tests plus the 5 updated ones.

- [ ] **Step 6: Run the focus tests and Detekt**

Run: `./gradlew :app:detektAll :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :app:connectedDevDebugAndroidTest --tests "com.kino.puber.ui.feature.home.component.*"`
Expected: PASS — `HomeFocusTraversalTest` and `HomeSectionRemovalFocusTest` are exactly the guard
against rows appearing under a focused element. If no device is attached, record that these still
need a device run and flag it in the handoff.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/kino/puber/ui/feature/home/vm/HomeVM.kt \
        app/src/test/kotlin/com/kino/puber/ui/feature/home/vm/HomeVMTest.kt \
        app/src/test/kotlin/com/kino/puber/ui/feature/home/vm/HomeVMIncrementalPublishTest.kt
git commit -m "$(cat <<'EOF'
Publish home sections as they arrive

The screen was as slow as its slowest section because it waited for all
ten before drawing any. Rows now paint independently, cached ones first.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 10: Wipe the cache on logout and on a domain switch

**Files:**
- Modify: `app/src/main/java/com/kino/puber/ui/feature/root/component/App.kt:54-70`
- Modify: `app/src/main/java/com/kino/puber/domain/interactor/api/ApiDomainInteractor.kt:126-145`
- Test: `app/src/test/kotlin/com/kino/puber/domain/interactor/api/ApiDomainInteractorCacheWipeTest.kt`

**Interfaces:**
- Consumes: `PersistentPayloadStore.clear()`, `ItemDetailsRepository.clear()`.
- Produces: no new public API.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/kino/puber/domain/interactor/api/ApiDomainInteractorCacheWipeTest.kt`.
Open `ApiDomainInteractor.kt` first and mirror its real constructor parameter list; the test below
names only the collaborators it asserts on and passes `mockk(relaxed = true)` for the rest.

```kotlin
package com.kino.puber.domain.interactor.api

import com.kino.puber.data.repository.PersistentPayloadStore
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ApiDomainInteractorCacheWipeTest {

    private val store = mockk<PersistentPayloadStore>(relaxed = true)

    @Test
    fun resettingTheDomainDropsEveryCachedPayload() = runTest {
        // Payloads describe one domain's catalogue. Keeping them across a switch shows the previous
        // domain's content under the new one.
        createInteractor().resetToDefault()

        coVerify(exactly = 1) { store.clear() }
    }

    private fun createInteractor(): ApiDomainInteractor = ApiDomainInteractor(
        // Fill in from the real constructor; `store = store` is the parameter under test.
        store = store,
    )
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.domain.interactor.api.ApiDomainInteractorCacheWipeTest"`
Expected: FAIL — `ApiDomainInteractor` has no `store` parameter.

- [ ] **Step 3: Wipe on a domain switch**

In `app/src/main/java/com/kino/puber/domain/interactor/api/ApiDomainInteractor.kt`, add
`private val store: PersistentPayloadStore` to the constructor and extend the existing helper:

```kotlin
    private suspend fun clearDomainSensitiveCaches() {
        itemDetailsRepository.clear()
        genreInteractor.clearCache()
        // Every payload describes one domain's catalogue; a switch makes all of them wrong at once.
        store.clear()
    }
```

Task 5 already made this function and its two callers `suspend`.

- [ ] **Step 4: Wipe on logout**

In `app/src/main/java/com/kino/puber/ui/feature/root/component/App.kt`, extend
`SessionExpiredHandler`:

```kotlin
@Composable
private fun SessionExpiredHandler() {
    val router by LocalPuberKoinScope.current!!.inject<AppRouter>()
    val sessionEventBus = getKoin().get<SessionEventBus>()
    val watchStateSyncInteractor = getKoin().get<WatchStateSyncInteractor>()
    val payloadStore = getKoin().get<PersistentPayloadStore>()
    LaunchedEffect(Unit) {
        sessionEventBus.events.collect { event ->
            when (event) {
                SessionEvent.Unauthorized -> {
                    // The index is one account's viewing history; it must not outlive the session.
                    watchStateSyncInteractor.invalidate()
                    // Neither may the cached payloads it was built from.
                    payloadStore.clear()
                    router.newRootScreen(router.screens.auth())
                }
            }
        }
    }
}
```

Add the import `com.kino.puber.data.repository.PersistentPayloadStore`.

- [ ] **Step 5: Register the new dependency**

In `app/src/main/java/com/kino/puber/domain/di/modules.kt`, `ApiDomainInteractor` is registered with
`singleOf(::ApiDomainInteractor)`, which resolves the added parameter automatically because
`PersistentPayloadStore` is bound in `repositoryModule`. No change should be needed; confirm with a
build.

- [ ] **Step 6: Run the tests**

Run: `./gradlew testDevDebugUnitTest --tests "com.kino.puber.domain.interactor.api.*"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/kino/puber/domain/interactor/api/ApiDomainInteractor.kt \
        app/src/main/java/com/kino/puber/ui/feature/root/component/App.kt \
        app/src/test/kotlin/com/kino/puber/domain/interactor/api/ApiDomainInteractorCacheWipeTest.kt
git commit -m "$(cat <<'EOF'
Drop cached payloads on logout and on a domain switch

The table holds one account's viewing history against one domain's
catalogue, and must outlive neither.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 11: Full verification

**Files:** none modified unless a check fails.

- [ ] **Step 1: Run the whole unit suite**

Run: `./gradlew testDevDebugUnitTest`
Expected: PASS. CI runs `testProdDebugUnitTest`; run that too if anything looks flavour-sensitive.

- [ ] **Step 2: Run Detekt over the module**

Run: `./gradlew :app:detektAll`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the instrumented suites**

Run: `./gradlew :app:connectedDevDebugAndroidTest`
Expected: PASS, including `CachedPayloadDaoTest` and the two home focus tests.

- [ ] **Step 4: Confirm the schema export was committed**

Run: `git status --short app/schemas`
Expected: clean. If a version-2 schema JSON is untracked, commit it — `exportSchema = true` means a
missing file breaks the next schema diff.

- [ ] **Step 5: Verify on a device**

Bump the version so the settings screen shows which build is running, install, and check:

```bash
./gradlew installDevDebug
```

1. Open the app, let the home screen load, then force-stop it and reopen. The continue-watching row
   must be on screen before the network could have answered.
2. Open a title from that row. No spinner.
3. Play roughly thirty seconds, press back twice, reopen the same title. Still no spinner.
4. Navigate the rows with the D-pad while the screen is still filling in. Focus must not jump to a
   row that appears late.
5. Turn off the network and reopen the app. Cached rows must draw, and no error screen may replace
   them.

- [ ] **Step 6: Commit anything the verification changed**

If steps 1-5 required a fix, commit it with a message describing the defect rather than the task.

---

## Self-Review

**Spec coverage.** Storage → Task 1. `CachedFeed` and its emission rules → Task 3. Freshness policy
table and the 7-day ceiling → Tasks 3-4. Home per-section keys and the hot/fresh merge → Task 8.
Incremental publishing and `force` on resume only for the watching row → Task 9. Details disk tier →
Task 5. `markStale` on position writes → Task 6. Bookmark lookup off the critical path and the
late-patch guard → Task 7. `similar:<id>` → Task 5 (repository) and Task 7 (consumption). Logout and
domain-switch wipes plus the generation guard → Tasks 2 and 10. TV focus risk → Task 9 step 6 and
Task 11 step 5. Every spec testing bullet appears in the task it belongs to.

**Known rough edges the executor must resolve rather than guess at.** Three places name types this
plan could not verify from the spec alone, and each carries an instruction to check the real
declaration first: the API wrapper returned by `getItemDetails` and `getWatchingList` (Tasks 5 and
8), the real constructor of `ApiDomainInteractor` (Task 10), and the exact `DetailsScreenParams`
shape (Task 7). Task 8 step 4 likewise locates the `HomeInteractor` registration with a `grep`
rather than asserting a file.

**Type consistency.** `markStale`/`invalidate`/`invalidateNamespace` keep those names from Task 3
through Tasks 5, 6 and 8. `Cached.Value`/`Cached.RefreshFailed` are used identically in Tasks 5, 7,
8 and 9. `PersistentPayloadStore`'s six methods are implemented by the Room store in Task 2 and by
three test fakes with the same signatures. `CachedFeed`'s constructor takes `keyPrefix` from its
definition in Task 3 onward, and every construction in Tasks 5 and 8 passes it.
