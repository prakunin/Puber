# Test design: cached-payload-row-too-big

Inputs: [brief.md](brief.md) (ACs, risks), [design-architect.md](design-architect.md) (risks). Test paths are under `app/src/`. Levels per `.flow/roles/test-designer.md`. perf track: N/A, off.

## Files

| Class | Path | Run |
|---|---|---|
| `PersistentPayloadStoreDbTest` (new; same setup as `CachedPayloadDaoTest`: in-memory `PuberDatabase`, `AndroidSQLiteDriver`, real `RoomPersistentPayloadStore` over the real DAO) | `androidTest/kotlin/com/kino/puber/data/db/` | `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.data.db.PersistentPayloadStoreDbTest` |
| `CachedPayloadDaoTest` (existing; its `dao.read(key)` calls take a limit) | `androidTest/kotlin/com/kino/puber/data/db/` | `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.data.db.CachedPayloadDaoTest` |
| `PersistentPayloadStoreTest` (existing; fake DAO filters by `encodeToByteArray().size`, exposes its rows) | `test/kotlin/com/kino/puber/data/repository/` | `./tools/agentw testDevDebugUnitTest` |
| `CachedFeedTest` (existing; `FakePayloadStore.refusedKeys`) | `test/kotlin/com/kino/puber/data/cache/` | same |

Oversized rows in instrumented tests are planted with `dao.upsert` directly, bypassing the store's write guard, as the box's row was written before the fix.

## Risk profile

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

## Traceability

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

## Bug: failing-first test

`PersistentPayloadStoreDbTest.readAllReturnsTheSmallRowsAndDeletesARowTooBigForTheCursorWindow`. Base `CachedPayloadDao.readAll` is `SELECT * FROM cached_payload WHERE key IN (:keys)` (`git show 8e005395:app/src/main/java/com/kino/puber/data/db/CachedPayloadDao.kt`, line 13) and `RoomPersistentPayloadStore.readAll` passes the exception through, so on base the test throws `SQLiteBlobTooBigException`. Written and run on the box first; the cheapest red unit test, `writeStoresAPayloadOfExactlyTheLimitAndRefusesOneByteMore`, goes next.

## Manual checks

Device `192.168.1.110:5555`, package `com.kino.puber.stage`, lease held, no data clear (brief AC-6).

1. Heal on the box.
   1. Before install, with the base build: open the 4K tab, focus the «Все» shelf. Expected: error state; logcat filtered to the app's pid shows `SQLiteBlobTooBigException`. Record once.
   2. Build the worktree dev APK, install with `tools/android-apk-install-preserve`.
   3. Launch, D-pad to the 4K tab, `DPAD_DOWN` to «Все». Expected: cards, no «Не удалось выполнить запрос».
   4. Open the browse grid from the shelf (`DPAD_CENTER` on «Показать все»). Expected: grid of cards, D-pad moves across rows.
   5. `am force-stop`, cold start, repeat steps 3-4. Expected: same.
   6. Logcat filtered to the app's pid over steps 3-5: no `SQLiteBlobTooBigException`; any refused-write warning names key and byte size only, no payload, one line per refused key.

   Must not happen: an error state on either screen, a crash, the app back at pairing, a Retry that fails without a request.

## UNTESTABLE

none
