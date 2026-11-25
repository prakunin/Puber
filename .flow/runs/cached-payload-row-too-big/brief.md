# Brief: cached-payload-row-too-big

| Field | Value |
|---|---|
| kind | bug |
| size | M |
| tracks | none |
| base | 8e005395 |
| device verification | yes: persistence and a screen that only fails at runtime; box `192.168.1.110:5555` (`com.kino.puber.stage`) holds the oversized row, install-preserve only, no data clear |

## Request

On the TV box the 4K tab's «Все» shelf and the browse grid fail with `SQLiteBlobTooBigException` on `SELECT * FROM cached_payload WHERE key IN (...)`; Retry fails with no request (`state.md` Log, 2026-11-24).

## Root cause

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

## Chosen approach

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

## Risks

- Deleting the key's older row on a refused write removes the small list version too, so after a cold start a page naming that title reads a missing record and refetches (`ContentCacheRepository.kt:385-387`) each time it is first read in a session. Network cost only; cards still show.
- The read guard must exclude the row in SQL (e.g. `length(CAST(payload AS BLOB)) <= :max`) and delete over-limit keys in the same store call. A catch-and-log without delete would refail every read.
- `length(CAST(payload AS BLOB))` reads every row's full payload pages; normal rows are small, so the cost is bounded by today's read. The TV box's `config_cursorWindowSize` is unverified (research.md: 5), hence the limit at half the AOSP window.
- The fake DAO in `PersistentPayloadStoreTest.kt:127-170` has no size semantics; any new DAO method needs a fake that mirrors the SQL, and only AC-1/AC-2 prove the SQL itself.

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Per-row limit and unit? | 1 MiB of UTF-8 bytes, one constant next to the store (research.md: 8 #1) | no |
| Q2 | An oversized details record: memory only, or a trimmed or split copy persisted? | Memory only in this run; a split or trimmed details tree (research.md: 6, option B) as a follow-up idea | no |
| Q3 | Read guard: SQL length filter, or catch `SQLiteBlobTooBigException` and fall back to per-key reads? | SQL filter plus delete of over-limit keys (research.md: 8 #3); it does not depend on how Room 3 wraps the exception | no |
