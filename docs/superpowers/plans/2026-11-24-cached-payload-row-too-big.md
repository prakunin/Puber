# A cached_payload row over the CursorWindow breaks the 4K «Все» shelf and grid - plan

**Spec:** `docs/superpowers/specs/2026-11-24-cached-payload-row-too-big.md`
**Goal:** `RoomPersistentPayloadStore` refuses rows over 1 MiB of UTF-8 on write and reads only rows within that bound, deleting any older oversized row, so one big `cached_payload` row can no longer fail a batch read.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Run in the worktree `/home/dev/orca/workspaces/Puber/cached-payload-row-too-big`, never `./gradlew`; prefix `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- Instrumented sources are not compiled by the fast gate: a task that touches `app/src/androidTest` also runs `./tools/agentw :app:compileDevDebugAndroidTestKotlin`.
- One task = green fast gate + green tests of the touched code + one commit, message one imperative English sentence with no prefix, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- The implementer never touches a device. Instrumented runs (red and green) and AC-6 are done by `qa-device` with a lease on `192.168.1.110:5555`: `make DEVICE=192.168.1.110:5555 itest TESTS=<fully.qualified.Class>`. See Device work.
- Design decisions (limit, `utf8Size`, SQL predicates, delete-on-miss) are in the spec's Design section; this plan does not restate their reasons.
- No schema change: `PuberDatabase` stays `version = 5` (`app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt:13`). `CachedFeed`, `ContentCacheRepository`, the `PersistentPayloadStore` interface (`PersistentPayloadStore.kt:12-38`) and the DI line `data/di/modules.kt:115` are not edited.
- Out of scope (spec): splitting or trimming the details record; any schema or driver change.

Paths: `main/` = `app/src/main/java/com/kino/puber/`; `test/` = `app/src/test/kotlin/com/kino/puber/`; `androidTest/` = `app/src/androidTest/kotlin/com/kino/puber/`. Line numbers are at base `8e005395`.

---

## Tasks

### Task 1: Add the failing-first instrumented test for an oversized row

**Covers:** AC-1, AC-2 (red on base)
**Files:**
- Create: `androidTest/data/db/PersistentPayloadStoreDbTest.kt` - setup copied from `androidTest/data/db/CachedPayloadDaoTest.kt:25-37` (in-memory `PuberDatabase`, `AndroidSQLiteDriver`, close in `@After`), plus `store = RoomPersistentPayloadStore(dao)` (`main/data/repository/PersistentPayloadStore.kt:40-42`). JUnit 4, `@RunWith(AndroidJUnit4::class)`, `runTest`.

**Consumes:** `CachedPayloadDao.upsert` (`main/data/db/CachedPayloadDao.kt:16-17`), base `dao.read(key)` (`:10-11`), `store.read`/`store.readAll` (`PersistentPayloadStore.kt:56-65`), `CachedPayloadEntity` (`main/data/db/CachedPayloadEntity.kt:22-27`).
**Produces:** the test class Task 3 extends.

- [x] Write `readAllReturnsTheSmallRowsAndDeletesARowTooBigForTheCursorWindow`: plant `big` = `"a".repeat(3 shl 20)` and two small rows with `dao.upsert` (bypassing the store); `store.readAll(listOf(small1, big, small2))` equals exactly the two small `StoredPayload`s; then `assertNull(dao.read(bigKey))`.
- [x] Write `readReturnsNullAndDeletesARowTooBigForTheCursorWindow`: plant the same big row; `assertNull(store.read(bigKey))`; then `assertNull(dao.read(bigKey))`.
- [x] Run the fast gate and `./tools/agentw :app:compileDevDebugAndroidTestKotlin`; commit. Both tests are red on this commit by design (`SQLiteBlobTooBigException`); `qa-device` records the red run at this sha (Device work, row 1).

**Done when:** both gates compile; the commit exists; the red run is recorded in the Implementation record.

---

### Task 2: Refuse a cached payload write over 1 MiB of UTF-8

**Covers:** AC-3, AC-4; R5, R6 (test-design risk IDs)
**Files:**
- Modify: `main/data/repository/PersistentPayloadStore.kt`
  - add `companion object { const val MAX_ROW_BYTES = 1 shl 20 }` to `RoomPersistentPayloadStore` (`:40-115`);
  - add a private top-level `utf8Size(text: String, stopAfter: Int): Int`: exact UTF-8 byte count while it is `<= stopAfter`, any value `> stopAfter` once past it. 1 byte for `< 0x80`, 2 for `< 0x800`, 4 for a high surrogate followed by a low surrogate (consume both), 3 otherwise (lone surrogates included). Fast paths: `text.length > stopAfter` returns `text.length`; `text.length.toLong() * 3 <= stopAfter` returns without counting (any value `<= stopAfter`). Does not allocate;
  - `write` (`:67-73`): when `utf8Size(payload, MAX_ROW_BYTES) > MAX_ROW_BYTES`, log, `dao.delete(key)` (`CachedPayloadDao.kt:25-26`), return; otherwise unchanged;
  - `writeAll` (`:75-86`): partition by the same check; `upsertAll` only the within-limit rows when non-empty, keep the generation check (`:83-85`) over those keys; then log and `dao.delete` each oversized key;
  - log: `Timber.w("Refused cached payload %s: %d UTF-8 bytes over %d", key, utf8Size(payload, Int.MAX_VALUE), MAX_ROW_BYTES)` (import `timber.log.Timber` as in `main/data/cache/CachedPayloadSweeper.kt:17`). Never the payload.
- Modify: `test/data/repository/PersistentPayloadStoreTest.kt` - fake DAO (`:127-172`): make its `rows` map (`:128`) readable by the tests (e.g. `val rows`). New tests below.

**Consumes:** `dao.upsert`, `dao.upsertAll`, `dao.delete` (`CachedPayloadDao.kt:16-26`); fake `onUpsert` hook (`PersistentPayloadStoreTest.kt:130`, fired per entity by `upsertAll` at `:142-144`).
**Produces:** `RoomPersistentPayloadStore.MAX_ROW_BYTES` (used by Tasks 3 and the tests); private `utf8Size`.

- [x] Add only `MAX_ROW_BYTES` first, then write the tests and run them red on the unchanged `write`/`writeAll`:
  | Test | Asserts |
  |---|---|
  | `writeOfAnOversizedPayloadDeletesTheKeysOlderRow` | `write(k, "{}")`, then `write(k, "a".repeat(MAX_ROW_BYTES + 1))`: `dao.rows` has no `k` |
  | `writeAllStoresTheSmallPayloadAndDropsTheOversizedKey` | seed `old` small; `writeAll(old -> MAX+1 bytes, other -> small)`: `dao.rows` holds `other` only |
  | `writeStoresAPayloadOfExactlyTheLimitAndRefusesOneByteMore` | `@ParameterizedTest @ValueSource(strings = ["a", "ж", "中", "😀"])`: `head = unit.repeat(1_000)`, `exact = head + "a".repeat(MAX_ROW_BYTES - head.encodeToByteArray().size)` is stored; `exact + "a"` written to another key is refused (no row) |
  | `writeRefusesACyrillicPayloadUnderTheLimitInCharsButOverItInBytes` | `"ж".repeat(MAX_ROW_BYTES / 2 + 1)` is refused |
  | `writeAllStartedBeforeAClearDoesNotSurviveIt` | mirror of `writeStartedBeforeAClearDoesNotSurviveIt` (`:70-89`) through `writeAll` of one oversized and one small key, `clear()` crossing the upsert: neither key in `dao.rows` |
  Expected red: the first four (oversized rows stored). `writeAllStartedBeforeAClearDoesNotSurviveIt` may pass on base; it guards the partition.
- [x] Implement `utf8Size`, the refused branches and the log.
- [x] All `PersistentPayloadStoreTest` cases green, including the existing `writeStartedBeforeAClearDoesNotSurviveIt` (guard).
- [x] Run the fast gate and `./tools/agentw testDevDebugUnitTest --tests 'com.kino.puber.data.repository.PersistentPayloadStoreTest'`; commit.

**Done when:** that test command passes and the red output of the first four tests is recorded.

---

### Task 3: Read only cached payload rows within the limit and delete the rest

**Covers:** AC-1, AC-2 (green), R1, R2, R3, R7, R8
**Files:**
- Modify: `main/data/db/CachedPayloadDao.kt`
  - `read` (`:10-11`) becomes `read(key: String, maxBytes: Int)` with `SELECT * FROM cached_payload WHERE key = :key AND length(CAST(payload AS BLOB)) <= :maxBytes`;
  - `readAll` (`:13-14`) becomes `readAll(keys: List<String>, maxBytes: Int)` with the same predicate after `key IN (:keys)`;
  - add `@Query("DELETE FROM cached_payload WHERE key IN (:keys) AND length(CAST(payload AS BLOB)) > :maxBytes") suspend fun deleteOversized(keys: List<String>, maxBytes: Int)`.
- Modify: `main/data/repository/PersistentPayloadStore.kt`
  - `read` (`:56-58`): `dao.read(key, MAX_ROW_BYTES)`; when null, `dao.deleteOversized(listOf(key), MAX_ROW_BYTES)` and return null;
  - `readAll` (`:60-65`): `dao.readAll(keys, MAX_ROW_BYTES)`; `missing = keys - returned keys`; call `dao.deleteOversized(missing, MAX_ROW_BYTES)` only when `missing` is non-empty. Never a delete without the size predicate.
- Modify: `test/data/repository/PersistentPayloadStoreTest.kt` fake DAO (`:127-172`): `read`/`readAll` (`:133-135`) take `maxBytes` and return only rows with `payload.encodeToByteArray().size <= maxBytes`; implement `deleteOversized` the same way and append `"deleteOversized:<keys>"` to `calls` (`:129`). Existing `sweepDeletesByAgeThenTrimsToTheNewest` (`:111-125`) asserts the whole `calls` list at `:120`, before its reads of deleted keys, so the new entries do not reach it.
- Modify: `androidTest/data/db/CachedPayloadDaoTest.kt` - every `dao.read(key)` (`:44` to `:150`) goes through a private helper `read(key) = dao.read(key, Int.MAX_VALUE)`; add `deleteOversizedKeepsARowWithinTheLimit`.
- Modify: `androidTest/data/db/PersistentPayloadStoreDbTest.kt` (Task 1) - `dao.read(bigKey)` becomes `dao.read(bigKey, Int.MAX_VALUE)`; add the two R2 tests.

**Consumes:** `MAX_ROW_BYTES` (Task 2). Callers of the DAO's `read`/`readAll` are only `PersistentPayloadStore.kt:57`, `:62`, the unit fake and the two instrumented classes (`rg "dao.read|readAll\(" app/src`).
**Produces:** `CachedPayloadDao.read(key, maxBytes)`, `readAll(keys, maxBytes)`, `deleteOversized(keys, maxBytes)`.

- [x] Change the DAO and the fake first with the store passing `Int.MAX_VALUE` and no `deleteOversized` call; write the unit tests and run them red:
  | Test | Asserts |
  |---|---|
  | `readAllDropsAndDeletesOnlyTheRowsOverTheLimit` | fake seeded (via `dao.upsert`) with one `MAX_ROW_BYTES + 1` row and two small: `store.readAll` returns the two small; `dao.rows` no longer holds the big key, still holds the small ones. Red at this step |
  | `readAllOfRowsWithinTheLimitDeletesNothing` | two small rows; after `store.readAll` of both, `calls` has no `deleteOversized` entry. P2 guard |
- [x] Switch the store to `MAX_ROW_BYTES` and the delete-on-miss calls; all `PersistentPayloadStoreTest` cases green.
- [x] Add the instrumented tests:
  | Test | Class | Asserts |
  |---|---|---|
  | `aMultiByteRowJustUnderTheLimitRoundTrips` | `PersistentPayloadStoreDbTest` | Cyrillic payload of exactly `MAX_ROW_BYTES - 2` UTF-8 bytes (`"ж".repeat((MAX_ROW_BYTES - 2) / 2)`) written by `store.write`, read back equal by `store.read` |
  | `aStoredCyrillicRowOverTheLimitInBytesIsTreatedAsAbsent` | `PersistentPayloadStoreDbTest` | `"ж".repeat(600_000)` planted with `dao.upsert`: `store.read` is null; `dao.read(key, Int.MAX_VALUE)` is null |
  | `deleteOversizedKeepsARowWithinTheLimit` | `CachedPayloadDaoTest` | small row and planted `"a".repeat(3 shl 20)` row; `dao.deleteOversized(listOf(small, big), MAX_ROW_BYTES)`: `read(small)` present, `read(big)` null |
- [x] Run the fast gate, `./tools/agentw :app:compileDevDebugAndroidTestKotlin` and `./tools/agentw testDevDebugUnitTest --tests 'com.kino.puber.data.repository.PersistentPayloadStoreTest'`; commit.

**Done when:** those commands pass; `rg -n 'SELECT \* FROM cached_payload' app/src/main` shows only the two queries carrying `length(CAST(payload AS BLOB)) <= :maxBytes`; `qa-device` reports both instrumented classes green (Device work, row 2).

---

### Task 4: Pin that a value the store refuses is still served from memory

**Covers:** AC-5 (guard: green on base)
**Files:**
- Modify: `test/util/FakePayloadStore.kt` - add `var refusedKeys: Set<String> = emptySet()`; `write` (`:35-43`) and `writeAll` (`:55-60`) remove the row of a refused key (`rows`, `:9`) instead of storing it; hooks keep firing as today.
- Modify: `test/data/cache/CachedFeedTest.kt` - two tests using `feed()` (`:31-37`) and `store` (`:28`).

**Consumes:** `CachedFeed.put` (`main/data/cache/CachedFeed.kt:376-378`), `putAll` (`:388-434`, memory put after `store.writeAll` at `:398-417`), `peekAll` (`:346-373`), `load` (`:197-202`, `store.write` at `:239-243`), `peek` (`:332-343`).
**Produces:** `FakePayloadStore.refusedKeys`.

- [x] `aValueTheStoreRefusesIsStillServedFromMemoryInTheSameSession`: `store.refusedKeys = setOf("k")`; `subject.put("k", "v")`; `subject.peekAll(listOf("k"))["k"]?.value == "v"`; `store.read("k")` is null.
- [x] `aLoadedValueTheStoreRefusesIsStillServedFromMemoryInTheSameSession`: same refusal; collect `subject.load("k") { "v" }` to completion; `subject.peek("k")?.value == "v"`, and the loader ran once (counter in the lambda).
- [x] Both green with no production change; run the fast gate and `./tools/agentw testDevDebugUnitTest --tests 'com.kino.puber.data.cache.CachedFeedTest'`; then the full gate; commit.

**Done when:** the full gate passes on this commit.

---

## Device work (`qa-device`, not the implementer)

| # | When | Action | Expected |
|---|---|---|---|
| 1 | after Task 1's commit | `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.data.db.PersistentPayloadStoreDbTest` | both tests fail with `SQLiteBlobTooBigException` (red recorded) |
| 2 | after Task 3's commit | same for `PersistentPayloadStoreDbTest` and `com.kino.puber.data.db.CachedPayloadDaoTest` | all green |
| 3 | after Task 4 | AC-6: test-design.md Manual checks 1 (base-build error recorded before install, install-preserve of the worktree dev APK, 4K «Все» shelf and browse grid, cold start, logcat) | as listed there |

## Coverage

| AC / risk | Task | Test |
|---|---|---|
| AC-1 | 1, 3 | `PersistentPayloadStoreDbTest.readAllReturnsTheSmallRowsAndDeletesARowTooBigForTheCursorWindow` |
| AC-2 | 1, 3 | `PersistentPayloadStoreDbTest.readReturnsNullAndDeletesARowTooBigForTheCursorWindow` |
| AC-3 | 2 | `writeOfAnOversizedPayloadDeletesTheKeysOlderRow`, `writeAllStoresTheSmallPayloadAndDropsTheOversizedKey` |
| AC-4 | 2 | `writeStoresAPayloadOfExactlyTheLimitAndRefusesOneByteMore`, `writeRefusesACyrillicPayloadUnderTheLimitInCharsButOverItInBytes` |
| AC-5 | 4 | `CachedFeedTest.aValueTheStoreRefusesIsStillServedFromMemoryInTheSameSession`, `aLoadedValueTheStoreRefusesIsStillServedFromMemoryInTheSameSession` |
| AC-6 | Device work 3 | manual check |
| R2 | 3 | `aMultiByteRowJustUnderTheLimitRoundTrips`, `aStoredCyrillicRowOverTheLimitInBytesIsTreatedAsAbsent` |
| R3 | 3 | `CachedPayloadDaoTest.deleteOversizedKeepsARowWithinTheLimit` |
| R6 | 2 | `writeAllStartedBeforeAClearDoesNotSurviveIt`, existing `writeStartedBeforeAClearDoesNotSurviveIt` |
| R7 | 3 | `readAllDropsAndDeletesOnlyTheRowsOverTheLimit` |
| R8 | 3 | `readAllOfRowsWithinTheLimitDeletesNothing` |

---

## Implementation record

<!-- owner: implementer -->

**Commits:** Task 1 `fd6e8233`, Task 2 `6b9594ad`, Task 3 `a0c0648a`, Task 4 (see `git log`).

**Red run** (P0 `red` tests on the unchanged code):

- red: `PersistentPayloadStoreDbTest.readAllReturns...` and `readReturnsNull...` - instrumented on the box by qa-device at `fd6e8233`: 2 tests, 2 failures, both `SQLiteBlobTooBigException`; green at `af7d459a` (4/4).
- red: `writeOfAnOversizedPayloadDeletesTheKeysOlderRow` - AssertionFailedError at PersistentPayloadStoreTest.kt:96 (oversized row stored).
- red: `writeAllStoresTheSmallPayloadAndDropsTheOversizedKey` - AssertionFailedError at :105.
- red: `writeStoresAPayloadOfExactlyTheLimitAndRefusesOneByteMore` [a, ж, 中, 😀] - AssertionFailedError at :120 for all four.
- red: `writeRefusesACyrillicPayloadUnderTheLimitInCharsButOverItInBytes` - AssertionFailedError at :131.
- green on base: `writeAllStartedBeforeAClearDoesNotSurviveIt` - P1 guard of the partition, as the plan expects.
- red: `readAllDropsAndDeletesOnlyTheRowsOverTheLimit` (P1) - AssertionFailedError at :164 with the store passing `Int.MAX_VALUE`.
- AC-5 `CachedFeedTest` pair: green on base by design (guard), no production change.

**Deviations from the plan:**

- The refused-write log counts bytes with `payload.encodeToByteArray().size`, not `utf8Size(payload, Int.MAX_VALUE)`: `utf8Size`'s second fast path returns 0 for any text under `stopAfter / 3` chars, so that call would log 0. It runs only on refusal.
- Detekt (`MagicNumber`, `MaxLineLength`) fixes in `PersistentPayloadStore.kt` (two named constants, a local for the log) landed in the Task 4 commit, found by the full gate.

**Fix round 1 (triage T1-T4):**

- T1: `CachedPayloadDao.oversizedKeys` selects the over-limit keys; `read`/`readAll` delete only those and log a cleanup failure instead of failing the read. Unit tests `aMissOnANeverStoredKeyRunsNoDelete`, `aFailingCleanupStillReturnsTheReadResult`.
- T2: the refusal log prints the char length, no encoding.
- T3: instrumented `PersistentPayloadStoreDbTest.anAsciiPayloadOfExactlyTheLimitRoundTripsThroughTheStore` (compiled only; qa-device runs it).
- T4: unit `writeStoresAThreeByteCharPayloadOfExactlyTheLimitAndRefusesOneCharMore` (green on the fixed code by design: a pin of the fast path).

**Files touched outside the plan:**

**For the reviewers:**

- `utf8Size` boundaries (exact limit, surrogate pairs, the `length * 3` fast path).
- Delete-on-miss in `readAll`/`read`: the delete carries the size predicate, so a within-limit row is never removed.
- The instrumented tests were compiled only; qa-device runs them (Device work rows 1 and 2).

**Blocked:**

## Readiness

# Readiness 1: cached-payload-row-too-big

Verdict: **READY**

Inputs: spec and plan in `/home/dev/orca/workspaces/Puber/cached-payload-row-too-big/docs/superpowers/` (worktree HEAD `f1ff4a13`, plan uncommitted), `test-design.md`. Base `8e005395`.

## AC coverage

| AC | Task | Test design row |
|---|---|---|
| AC-1 | 1 (red), 3 (green) | `PersistentPayloadStoreDbTest.readAllReturnsTheSmallRowsAndDeletesARowTooBigForTheCursorWindow` (P0 red) |
| AC-2 | 1, 3 | `PersistentPayloadStoreDbTest.readReturnsNullAndDeletesARowTooBigForTheCursorWindow` (P0 red) |
| AC-3 | 2 | `writeOfAnOversizedPayloadDeletesTheKeysOlderRow`, `writeAllStoresTheSmallPayloadAndDropsTheOversizedKey` (P0 red) |
| AC-4 | 2 | `writeStoresAPayloadOfExactlyTheLimitAndRefusesOneByteMore`, `writeRefusesACyrillicPayloadUnderTheLimitInCharsButOverItInBytes` (P0 red) |
| AC-5 | 4 | `CachedFeedTest.aValueTheStoreRefusesIsStillServedFromMemoryInTheSameSession` (P0 guard), load variant (P1) |
| AC-6 | Device work 3 | Manual check 1 |

## Citations sampled (at worktree HEAD, base code unchanged)

| Citation | Resolves to |
|---|---|
| `PersistentPayloadStore.kt:40-42`, `:56-65`, `:67-73`, `:75-86`, `:83-85` | store class, `read`/`readAll`, `write`, `writeAll`, generation check |
| `CachedPayloadDao.kt:10-11`, `:13-14`, `:16-26` | `read`, `readAll` (`SELECT *`), upsert/upsertAll/touch/delete |
| `PuberDatabase.kt:13`, `:37-45` | `version = 5`; builder with `AndroidSQLiteDriver` |
| `PersistentPayloadStoreTest.kt:70-89`, `:111-125`, `:120`, `:127-172`, `:128-130`, `:133-135`, `:142-144` | clear-crossing test, sweep test, its `calls` assert, fake DAO, `rows`/`calls`/`onUpsert`, fake reads, fake `upsertAll` |
| `FakePayloadStore.kt:9`, `:35-43`, `:55-60` | `rows`, `write`, `writeAll` |
| `CachedFeed.kt:197-202`, `:239-243`, `:332-343`, `:346-373`, `:360`, `:388-434`, `:619-634` | `load`, its `store.write`, `peek`, `peekAll`, `store.readAll`, `putAll` (memory put after `writeAll`), `decodeUsable` |
| `ContentCacheRepository.kt:380-387` | `queryNeedsRefetch` |
| `CachedPayloadSweeper.kt:17`, `data/di/modules.kt:115`, `PuberApp.kt:105`, `CachedPayloadDaoTest.kt:25-37` | Timber import, store DI line, `LinkingDebugTree` plant, in-memory db setup |

All resolve. Every `dao.read`/`dao.readAll` caller (`rg`) is in the plan's Task 3 list; the only `SELECT *` on `cached_payload` are the two queries Task 3 rewrites.

## Order and build

| Task | Depends on | Build after commit |
|---|---|---|
| 1 | - | compiles; its two instrumented tests red by design (not part of `check.full`) |
| 2 | - | green; unit tests of `PersistentPayloadStoreTest` |
| 3 | 1, 2 (`MAX_ROW_BYTES`) | green; DAO signature, fake and both androidTest classes change in the same commit |
| 4 | - | full gate |

## Scope and questions

Plan stays inside Out of scope (no schema, driver or details-split change). Q1-Q3 answered; none blocking.

## Notes (not blocking)

1. The P0 guard row `writeStartedBeforeAClearDoesNotSurviveIt` names its defect only through its R6 ID; the refused branch of `write` must keep returning before the upsert so this guard stays meaningful.
2. AC-1/AC-2 green and AC-6 depend on `qa-device` runs on `192.168.1.110:5555` (Device work rows 1-3); Task 3 is not done until row 2 is reported.
