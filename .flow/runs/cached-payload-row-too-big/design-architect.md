# Design: cached-payload-row-too-big

Paths without a prefix are under `app/src/main/java/com/kino/puber/`; test paths are under `app/src/`. Line numbers are at base 8e005395.

## Decisions

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

## Changes by layer

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

## Alternatives rejected

| Alternative | Why not |
|---|---|
| Catch `SQLiteBlobTooBigException` and fall back to per-key reads | Q3; depends on how Room 3 surfaces the exception and costs N queries on a bad batch |
| Unconditional `deleteOversized` before every read | Turns every `peekAll` into a write transaction that contends with writers |
| One-off sweep of oversized rows in `CachedPayloadSweeper` (`data/cache/CachedPayloadSweeper.kt:60-71`) | Runs 60 s after start, so the first screen still fails; and scans every payload in the table |
| Size check in `CachedFeed.putAll` / `load` | Crosses the epoch and ownership logic for no gain; every store user needs the bound |
| Trim or split the details tree; `BundledSQLiteDriver`; chunked rows | research.md section 6 options B and C; Q2 leaves B as a follow-up idea |

## Risks

| Risk | Kind | Where | Mitigation |
|---|---|---|---|
| `CAST(payload AS BLOB)` yields bytes in the database's text encoding; if `puber.db` were UTF-16 (`PRAGMA encoding` unverified, Android default is UTF-8), every ASCII row between 0.5 and 1 MiB would be accepted on write and deleted on read, every read | correctness | `CachedPayloadDao.kt` predicates vs `utf8Size` | The instrumented test stores a row just under the limit (multi-byte text) and reads it back through the store on a real `AndroidSQLiteDriver` database |
| A delete must never remove a valid row a concurrent writer just put there | concurrency | `RoomPersistentPayloadStore.read/readAll`; refused branch of `RoomPersistentPayloadStore.write/writeAll` | Read: the `DELETE` carries the size predicate itself. Refused write: `dao.delete(key)` is by key alone, so a card write for the same key that lands just before it is lost from disk for the session (needs an oversized details load and a card merge of the same item at once); not mitigated here, deferred to run `cached-item-card-only-fallback` (triage-1 T5) |
| While an item's oversized details record is in memory, every card merge for it starts from that record (`mergeItems` reads `readItemRecords`, memory first), stays over the limit and is refused, so the item's card row is deleted instead of written. The card stays unpersisted for as long as the record is in memory; once the memory tier evicts it (`MEMORY_TIER_ENCODED_CHARS`) or after a cold start, each first read of a page naming the title refetches, and offline that page shows without the card | correctness, performance | `ContentCacheRepository.kt:327-347`, `:380-387`, `:414`, `:549` | Not mitigated in this run; persisting a card-only copy of an over-limit item record is deferred to run `cached-item-card-only-fallback` (triage-1 T6) |
| The length predicate reads each requested row's full payload pages | performance | `CachedPayloadDao.read/readAll` | Bounded by what today's `SELECT *` already reads for those keys |
| A `Timber.w` for every refused write repeats while browsing a long series' neighbours | performance | `RoomPersistentPayloadStore.write/writeAll` | One line per refused key, no payload; the tree is planted only where `PuberApp.kt:105` plants `LinkingDebugTree` |
| The window on the box may be smaller than 2048 kB | rollout | `MAX_ROW_BYTES` | Half the AOSP window; AC-6 on the box settles it |

## Open questions

None.
