# A cached_payload row over the CursorWindow breaks the 4K «Все» shelf and grid

Status: approved
Date: 2026-11-24
Slug: cached-payload-row-too-big
Kind: bug
Size: M
Tracks: none

## Problem / Why

### Request

On the TV box the 4K tab's «Все» shelf and the browse grid fail with `SQLiteBlobTooBigException` on `SELECT * FROM cached_payload WHERE key IN (...)`; Retry fails with no request (`state.md` Log, 2026-11-24).

### Root cause

**Confirmed** (device logcat in `docs/superpowers/evidence/2026-11-24-catalogue-filters-device.md:61` plus the trace in research.md: 3). Which key is oversized is **plausible**, not confirmed: a `content:v2:item:<id>` details record of a long series (research.md: 3, "What is stored").

| Step | Where |
|---|---|
| A row of any size is upserted; no per-row bound exists | `app/src/main/java/com/kino/puber/data/repository/PersistentPayloadStore.kt:67-86` |
| A details record carries the whole seasons/episodes/files tree and list merges keep it | `data/cache/ContentCacheRepository.kt:433-437`, `:491-496`; written by prefetch, `domain/interactor/prefetch/DetailsPrefetcher.kt:141` |
| All of a page's card rows are read in one `IN` query through the platform CursorWindow (2048 kB AOSP) | `data/db/CachedPayloadDao.kt:13-14`, `data/db/PuberDatabase.kt:37-45` |
| The store passes the exception through; `peekAll` reads the batch in one call, so one row fails all | `PersistentPayloadStore.kt:60-65`, `data/cache/CachedFeed.kt:360` |
| `queryNeedsRefetch` reads the page's records before any network call; the error ends the section | `ContentCacheRepository.kt:380-387`, `:414-418`; `ui/feature/contentlist/vm/ContentListPagingVM.kt:174` |
| `decodeUsable` deletes bad rows only for rows the query returned, so the row never heals until the 7-day sweep | `CachedFeed.kt:619-634`, `data/cache/CachedPayloadSweeper.kt:65-70` |

Paths without a prefix are under `app/src/main/java/com/kino/puber/`.

### Chosen approach

research.md: 6, option A: in `RoomPersistentPayloadStore` and `CachedPayloadDao`, refuse a write whose UTF-8 size is over a limit (deleting the key's older row), and make `read`/`readAll` return only rows within the limit while deleting the rest. No schema change (`PuberDatabase` version 5 stays). `CachedFeed` keeps the refused value in its memory tier for the session (`CachedFeed.kt:388-434`, `putAll` puts in memory after `writeAll` regardless of what the store kept).

Size M: persistence and caches is a risk area (`.flow/roles/analyst.md`), although the change is two production files plus tests.

## Acceptance criteria

| ID | Given | When | Then | Checked by |
|---|---|---|---|---|
| AC-1 | a real `AndroidSQLiteDriver` database holding one row above the CursorWindow (e.g. 3 MB) and two small rows | `readAll` is called for all three keys through the store | the two small rows come back, no exception, and the large row is gone from the table | instrumented test next to `app/src/androidTest/kotlin/com/kino/puber/data/db/CachedPayloadDaoTest.kt` (`make DEVICE=<serial> itest`) |
| AC-2 | the same oversized row | `read(key)` is called for it | it returns null without an exception and the row is deleted | same instrumented test |
| AC-3 | a key with a stored small row | `write` or `writeAll` is given a payload over the limit for it, together with a small payload for another key | no row exists for the oversized key afterwards; the other key is stored | unit test in `app/src/test/kotlin/com/kino/puber/data/repository/PersistentPayloadStoreTest.kt` |
| AC-4 | a payload whose `String.length` is under the limit but whose UTF-8 size is over it (Cyrillic) | it is written | it is refused as in AC-3; a payload just under the limit in UTF-8 bytes is stored | unit test |
| AC-5 | a `CachedFeed` over a store that refuses a key's write | `put` then `peekAll` for that key in the same session | the value comes back from the memory tier | unit test in `app/src/test/kotlin/com/kino/puber/data/cache/CachedFeedTest.kt` |
| AC-6 | the box with its existing data, before the fix showing the error (recorded once before install) | the fixed dev APK is installed with `tools/android-apk-install-preserve` and the 4K tab's «Все» shelf, then its browse grid, are opened, then the app is cold-started and both are opened again | both show cards with no error state, and logcat (filtered to the app) has no `SQLiteBlobTooBigException` | observation on `192.168.1.110:5555` |

### Risks (brief)

- Deleting the key's older row on a refused write removes the small list version too, so after a cold start a page naming that title reads a missing record and refetches (`ContentCacheRepository.kt:385-387`) each time it is first read in a session. Network cost only; cards still show.
- The read guard must exclude the row in SQL (e.g. `length(CAST(payload AS BLOB)) <= :max`) and delete over-limit keys in the same store call. A catch-and-log without delete would refail every read.
- `length(CAST(payload AS BLOB))` reads every row's full payload pages; normal rows are small, so the cost is bounded by today's read. The TV box's `config_cursorWindowSize` is unverified (research.md: 5), hence the limit at half the AOSP window.
- The fake DAO in `PersistentPayloadStoreTest.kt:127-170` has no size semantics; any new DAO method needs a fake that mirrors the SQL, and only AC-1/AC-2 prove the SQL itself.

## Out of scope

- Splitting or trimming the details record so an oversized one can be persisted (Q2: follow-up idea).
- Any schema change or a change of SQLite driver.

## Design

Paths without a prefix are under `app/src/main/java/com/kino/puber/`; test paths are under `app/src/`. Line numbers are at base 8e005395.

### Decisions

| Question | Decision | Why |
|---|---|---|
| Where the bound lives | `RoomPersistentPayloadStore` (`data/repository/PersistentPayloadStore.kt:40-115`) and `CachedPayloadDao` (`data/db/CachedPayloadDao.kt:8-42`); the `PersistentPayloadStore` interface (`:12-38`), `CachedFeed` and `ContentCacheRepository` are unchanged | Below the feed's epoch barriers (`data/cache/CachedFeed.kt:388-434`), so no ownership logic moves; a missing row is already handled upstream (`data/cache/ContentCacheRepository.kt:380-387`) |
| Limit | `const val MAX_ROW_BYTES = 1 shl 20` (1 MiB of UTF-8 bytes) in a `companion object` of `RoomPersistentPayloadStore`; a payload is stored when its size is `<= MAX_ROW_BYTES` | Q1 (state.md Decisions); half the AOSP 2048 kB window |
| Measuring a payload | A private non-allocating `utf8Size(String): Int` in `PersistentPayloadStore.kt`: 1/2/3 bytes by char range, 4 for a surrogate pair, 3 for a lone surrogate; it stops counting once past the limit. Fast paths: `length > MAX_ROW_BYTES` is over, `length * 3 <= MAX_ROW_BYTES` is within | `encodeToByteArray()` would copy up to megabytes on every write; UTF-8 size is never below `length` and never above `3 * length` |
| Refused write | `write`: an oversized payload is not upserted; `dao.delete(key)` runs instead and the call returns. `writeAll`: partition; `upsertAll` the within-limit rows (generation check `:83-85` unchanged for them), then `dao.delete` each oversized key. One `Timber.w` per refused key with key and byte size, never the payload | brief AC-3; the older small row must go, or a stale record outlives the newer one the feed holds in memory |
| Read guard (SQL) | `read(key, maxBytes)` and `readAll(keys, maxBytes)` add `AND length(CAST(payload AS BLOB)) <= :maxBytes`; the old unfiltered signatures are replaced, so no unguarded `SELECT *` on the table remains | Q3; SQLite applies `WHERE` before the row is copied into the CursorWindow, so an oversized row never reaches it |
| Healing on read | New `deleteOversized(keys: List<String>, maxBytes: Int)`: `DELETE FROM cached_payload WHERE key IN (:keys) AND length(CAST(payload AS BLOB)) > :maxBytes`. The store calls it only for requested keys the filtered read did not return (`read`: when it returned null) | The hit path stays a pure read with no write transaction; the size condition in the `DELETE` means a within-limit row written between the two statements is never deleted |
| `CachedFeed` behaviour for a refused key | None to change: `putAll` puts the value in memory after `store.writeAll` (`CachedFeed.kt:398-417`), `load` returns the loader's value after `store.write` (`:239-263`); `peekAll` reads memory first (`:351-357`) | AC-5 is pinned by a test, not by new code; Q2 keeps oversized records memory-only |
| Schema | None; `PuberDatabase` stays version 5 (`data/db/PuberDatabase.kt:13`) | The bound is a query predicate and a write check, not a column |

### Changes by layer

**API / repository**

| File | Change |
|---|---|
| `data/db/CachedPayloadDao.kt:10-14` | `read` and `readAll` take `maxBytes: Int` and add the length predicate; add `deleteOversized(keys, maxBytes)` |
| `data/repository/PersistentPayloadStore.kt:56-65` | `read`: `dao.read(key, MAX_ROW_BYTES)`; on null, `dao.deleteOversized(listOf(key), MAX_ROW_BYTES)`. `readAll`: filtered read, then `deleteOversized(keys - returned)` when that set is non-empty |
| `data/repository/PersistentPayloadStore.kt:67-86` | Refused-write branch in `write` and `writeAll`, the `utf8Size` helper and the `MAX_ROW_BYTES` constant, as in Decisions |
| `test/kotlin/com/kino/puber/data/repository/PersistentPayloadStoreTest.kt:127-172` | The fake DAO's `read`/`readAll` filter by `payload.encodeToByteArray().size <= maxBytes` and it implements `deleteOversized` the same way, so the store's helper is cross-checked against a plain encoder |
| `test/kotlin/com/kino/puber/util/FakePayloadStore.kt:35-60` | A way to make the fake refuse a key (e.g. `refusedKeys: Set<String>`: `write`/`writeAll` remove the row instead of storing), for the `CachedFeed` test of AC-5 |
| `androidTest/kotlin/com/kino/puber/data/db/CachedPayloadDaoTest.kt` | Existing `dao.read(key)` calls pass a limit (e.g. a private helper with `Int.MAX_VALUE`); new cases for AC-1/AC-2 per test design |

ViewModel state and actions, screen and DI module, Compose content, resources: N/A, no change (`data/di/modules.kt:115` constructs the store with `dao = get()` and keeps doing so).

### Alternatives rejected

| Alternative | Why not |
|---|---|
| Catch `SQLiteBlobTooBigException` and fall back to per-key reads | Q3; depends on how Room 3 surfaces the exception and costs N queries on a bad batch |
| Unconditional `deleteOversized` before every read | Turns every `peekAll` into a write transaction that contends with writers |
| One-off sweep of oversized rows in `CachedPayloadSweeper` (`data/cache/CachedPayloadSweeper.kt:60-71`) | Runs 60 s after start, so the first screen still fails; and scans every payload in the table |
| Size check in `CachedFeed.putAll` / `load` | Crosses the epoch and ownership logic for no gain; every store user needs the bound |
| Trim or split the details tree; `BundledSQLiteDriver`; chunked rows | research.md section 6 options B and C; Q2 leaves B as a follow-up idea |

### Risks

| Risk | Kind | Where | Mitigation |
|---|---|---|---|
| `CAST(payload AS BLOB)` yields bytes in the database's text encoding; if `puber.db` were UTF-16 (`PRAGMA encoding` unverified, Android default is UTF-8), every ASCII row between 0.5 and 1 MiB would be accepted on write and deleted on read, every read | correctness | `CachedPayloadDao.kt` predicates vs `utf8Size` | The instrumented test stores a row just under the limit (multi-byte text) and reads it back through the store on a real `AndroidSQLiteDriver` database |
| A delete must never remove a valid row a concurrent writer just put there | concurrency | `RoomPersistentPayloadStore.read/readAll`; refused branch of `RoomPersistentPayloadStore.write/writeAll` | Read: the `DELETE` carries the size predicate itself. Refused write: `dao.delete(key)` is by key alone, so a card write for the same key that lands just before it is lost from disk for the session (needs an oversized details load and a card merge of the same item at once); not mitigated here, deferred to run `cached-item-card-only-fallback` (triage-1 T5) |
| While an item's oversized details record is in memory, every card merge for it starts from that record (`mergeItems` reads `readItemRecords`, memory first), stays over the limit and is refused, so the item's card row is deleted instead of written. The card stays unpersisted for as long as the record is in memory; once the memory tier evicts it (`MEMORY_TIER_ENCODED_CHARS`) or after a cold start, each first read of a page naming the title refetches, and offline that page shows without the card | correctness, performance | `ContentCacheRepository.kt:327-347`, `:380-387`, `:414`, `:549` | Not mitigated in this run; persisting a card-only copy of an over-limit item record is deferred to run `cached-item-card-only-fallback` (triage-1 T6) |
| The length predicate reads each requested row's full payload pages | performance | `CachedPayloadDao.read/readAll` | Bounded by what today's `SELECT *` already reads for those keys |
| A `Timber.w` for every refused write repeats while browsing a long series' neighbours | performance | `RoomPersistentPayloadStore.write/writeAll` | One line per refused key, no payload; the tree is planted only where `PuberApp.kt:105` plants `LinkingDebugTree` |
| The window on the box may be smaller than 2048 kB | rollout | `MAX_ROW_BYTES` | Half the AOSP window; AC-6 on the box settles it |

### Open questions

None.

## Test design

Inputs: [brief.md](brief.md) (ACs, risks), [design-architect.md](design-architect.md) (risks). Test paths are under `app/src/`. Levels per `.flow/roles/test-designer.md`. perf track: N/A, off.

### Files

| Class | Path | Run |
|---|---|---|
| `PersistentPayloadStoreDbTest` (new; same setup as `CachedPayloadDaoTest`: in-memory `PuberDatabase`, `AndroidSQLiteDriver`, real `RoomPersistentPayloadStore` over the real DAO) | `androidTest/kotlin/com/kino/puber/data/db/` | `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.data.db.PersistentPayloadStoreDbTest` |
| `CachedPayloadDaoTest` (existing; its `dao.read(key)` calls take a limit) | `androidTest/kotlin/com/kino/puber/data/db/` | `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.data.db.CachedPayloadDaoTest` |
| `PersistentPayloadStoreTest` (existing; fake DAO filters by `encodeToByteArray().size`, exposes its rows) | `test/kotlin/com/kino/puber/data/repository/` | `./tools/agentw testDevDebugUnitTest` |
| `CachedFeedTest` (existing; `FakePayloadStore.refusedKeys`) | `test/kotlin/com/kino/puber/data/cache/` | same |

Oversized rows in instrumented tests are planted with `dao.upsert` directly, bypassing the store's write guard, as the box's row was written before the fix.

### Risk profile

| ID | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Read guard excludes the row but does not delete it, or catches without deleting: every read refails or the row never heals | 2 | 3 | 6 | AC-1, AC-2 rows assert the row is gone |
| R2 | `CAST(payload AS BLOB)` measures in a different unit than `utf8Size` (UTF-16 db, or `length(payload)` in chars): valid rows deleted on read, or multi-byte oversized rows kept | 1 | 3 | 3 | R2 rows (instrumented) |
| R3 | `deleteOversized` removes a within-limit row a concurrent writer just put there | 1 | 3 | 3 | R3 row |
| R4 | `utf8Size` miscounts (fast paths, 2/3/4-byte chars, off-by-one at the limit) | 2 | 2 | 4 | AC-4 rows |
| R5 | Refused write leaves the key's older small row, a stale record outliving the newer one in memory | 2 | 2 | 4 | AC-3 rows |
| R6 | `writeAll` partition skips the generation check for the kept rows | 1 | 3 | 3 | R6 rows |
| R7 | Fake DAO diverges from the SQL | 2 | 2 | 4 | AC-1, AC-2, R2 rows run the real SQL |
| R8 | Read hit path turns into a write transaction | 1 | 2 | 2 | R8 row (P2) |
| R9 | Box `config_cursorWindowSize` below 1 MiB | 1 | 3 | 3 | AC-6 |
| R10 | Oversized title refetched after each cold start | 3 | 1 | 3 | Accepted: network only, cards still show (brief Risks) |
| R11 | `length(CAST ...)` reads full payload pages | 1 | 1 | 1 | Accepted: bounded by today's `SELECT *` |
| R12 | Refused-write log carries the payload or floods logcat | 1 | 2 | 2 | Manual check 1, step 6 |

### Traceability

| AC | Level | Priority | Base | Test | Proves |
|---|---|---|---|---|---|
| AC-1 | instrumented | P0 | red | `PersistentPayloadStoreDbTest.readAllReturnsTheSmallRowsAndDeletesARowTooBigForTheCursorWindow` | 3 MiB ASCII row plus two small rows: `store.readAll` of all three returns exactly the two small ones, then `dao.read(bigKey, Int.MAX_VALUE)` is null. Fails with `SQLiteBlobTooBigException` on base; fails if the SQL predicate or the delete is removed |
| AC-2 | instrumented | P0 | red | `PersistentPayloadStoreDbTest.readReturnsNullAndDeletesARowTooBigForTheCursorWindow` | `store.read(bigKey)` is null without throwing and the row is gone afterwards. Fails on base with the exception; fails if `read` does not call `deleteOversized` on a miss |
| AC-3 | unit | P0 | red | `PersistentPayloadStoreTest.writeOfAnOversizedPayloadDeletesTheKeysOlderRow` | Small row stored, then `write` of MAX+1 bytes: fake DAO holds no row for the key. Fails on base (oversized row upserted); fails if the refused branch returns without `dao.delete` |
| AC-3 | unit | P0 | red | `PersistentPayloadStoreTest.writeAllStoresTheSmallPayloadAndDropsTheOversizedKey` | `writeAll` of {old small key -> oversized, other -> small}: only `other` is stored. Fails if `writeAll` upserts all, or drops the whole batch |
| AC-4 | unit | P0 | red | `PersistentPayloadStoreTest.writeStoresAPayloadOfExactlyTheLimitAndRefusesOneByteMore` | `@ParameterizedTest` over ASCII, Cyrillic (2 B), CJK (3 B), emoji surrogate pair (4 B), each padded with ASCII to exactly `MAX_ROW_BYTES` and to +1. Exact is stored, +1 is refused. Fails on base (+1 stored); fails on off-by-one, on a fast path that compares `length` to the limit, or on a surrogate pair counted as 6 or 2 |
| AC-4 | unit | P0 | red | `PersistentPayloadStoreTest.writeRefusesACyrillicPayloadUnderTheLimitInCharsButOverItInBytes` | Cyrillic payload of `MAX_ROW_BYTES / 2 + 1` chars is refused. Fails if the check uses `String.length` |
| AC-5 | unit | P0 | guard | `CachedFeedTest.aValueTheStoreRefusesIsStillServedFromMemoryInTheSameSession` | `FakePayloadStore.refusedKeys = {k}`; `put(k, v)` then `peekAll(listOf(k))` returns `v` and `store.read(k)` is null. Fails if `putAll` keeps memory only for keys the store stored |
| AC-5 | unit | P1 | - | `CachedFeedTest.aLoadedValueTheStoreRefusesIsStillServedFromMemoryInTheSameSession` | Same through `load(k) { v }`; next `peek(k)` returns `v` without calling the loader. Fails if `load` re-reads the store for its result |
| R2 | instrumented | P1 | - | `PersistentPayloadStoreDbTest.aMultiByteRowJustUnderTheLimitRoundTrips` | Cyrillic payload of `MAX_ROW_BYTES - 2` UTF-8 bytes written and read through the store comes back equal. Fails if the db measures in UTF-16 or the SQL limit is stricter than `utf8Size` |
| R2 | instrumented | P1 | - | `PersistentPayloadStoreDbTest.aStoredCyrillicRowOverTheLimitInBytesIsTreatedAsAbsent` | Planted Cyrillic row of 600 000 chars (about 1.2 MB, fits the window): `store.read` is null and the row is gone. Fails if the SQL uses `length(payload)` (chars) |
| R3 | instrumented | P1 | - | `CachedPayloadDaoTest.deleteOversizedKeepsARowWithinTheLimit` | Small row and planted 3 MiB row; `dao.deleteOversized(listOf(small, big), MAX_ROW_BYTES)` leaves the small row and removes the big one. Fails if the `DELETE` lacks the size predicate |
| R7 | unit | P1 | - | `PersistentPayloadStoreTest.readAllDropsAndDeletesOnlyTheRowsOverTheLimit` | Fake DAO seeded with an over-limit row: `readAll` omits it and the fake no longer holds it; small rows stay. Fails if `readAll` does not call `deleteOversized` for the missing keys |
| R6 | unit | P1 | - | `PersistentPayloadStoreTest.writeAllStartedBeforeAClearDoesNotSurviveIt` | Mirror of `writeStartedBeforeAClearDoesNotSurviveIt` for a batch holding one oversized and one small key: after the crossing `clear` neither key has a row. Fails if the partition drops the generation check |
| R6 | unit | P0 | guard | `PersistentPayloadStoreTest.writeStartedBeforeAClearDoesNotSurviveIt` | Existing; stays green |
| R8 | unit | P2 | - | `PersistentPayloadStoreTest.readAllOfRowsWithinTheLimitDeletesNothing` | Fake DAO records calls; a full hit issues no `deleteOversized`. Fails if the delete runs unconditionally |

The existing `CachedPayloadDaoTest` and `PersistentPayloadStoreTest` cases stay green with the new signatures (guard).

Red on base: the unit rows observe the fake DAO's row map; the implementer runs them on the base store with the fake in its base shape before adding `maxBytes` and `deleteOversized`. The instrumented AC-1/AC-2 rows are red through the exception.

### Bug: failing-first test

`PersistentPayloadStoreDbTest.readAllReturnsTheSmallRowsAndDeletesARowTooBigForTheCursorWindow`. Base `CachedPayloadDao.readAll` is `SELECT * FROM cached_payload WHERE key IN (:keys)` (`git show 8e005395:app/src/main/java/com/kino/puber/data/db/CachedPayloadDao.kt`, line 13) and `RoomPersistentPayloadStore.readAll` passes the exception through, so on base the test throws `SQLiteBlobTooBigException`. Written and run on the box first; the cheapest red unit test, `writeStoresAPayloadOfExactlyTheLimitAndRefusesOneByteMore`, goes next.

### Manual checks

Device `192.168.1.110:5555`, package `com.kino.puber.stage`, lease held, no data clear (brief AC-6).

1. Heal on the box.
   1. Before install, with the base build: open the 4K tab, focus the «Все» shelf. Expected: error state; logcat filtered to the app's pid shows `SQLiteBlobTooBigException`. Record once.
   2. Build the worktree dev APK, install with `tools/android-apk-install-preserve`.
   3. Launch, D-pad to the 4K tab, `DPAD_DOWN` to «Все». Expected: cards, no «Не удалось выполнить запрос».
   4. Open the browse grid from the shelf (`DPAD_CENTER` on «Показать все»). Expected: grid of cards, D-pad moves across rows.
   5. `am force-stop`, cold start, repeat steps 3-4. Expected: same.
   6. Logcat filtered to the app's pid over steps 3-5: no `SQLiteBlobTooBigException`; any refused-write warning names key and byte size only, no payload, one line per refused key.

   Must not happen: an error state on either screen, a crash, the app back at pairing, a Retry that fails without a request.

### UNTESTABLE

none

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Per-row limit and unit? | no | answered | 1 MiB of UTF-8 bytes, one constant next to the store |
| Q2 | Oversized details record: memory only or a trimmed/split copy persisted? | no | answered | memory only this run; split/trim as a follow-up idea |
| Q3 | Read guard: SQL length filter or catch-and-fall-back? | no | answered | SQL length(CAST(payload AS BLOB)) filter plus delete of over-limit keys in the same store call |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-24 | Q1-Q3 on the recommended answers | owner (standing instruction: recommended) |
| 2026-11-24 | Gate 1: approved as designed (size M, no tracks, AC-1..AC-6) | owner (standing instruction: recommended) |
