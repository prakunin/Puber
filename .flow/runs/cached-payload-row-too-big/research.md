# Research: cached-payload-row-too-big

## 1. Request

fix the bug recorded in /home/dev/Puber/.flow/runs/cached-payload-row-too-big/state.md (Log): on the TV box the 4K tab's «Все» shelf and the browse grid fail with SQLiteBlobTooBigException (Row too big to fit into CursorWindow) on SELECT * FROM cached_payload WHERE key IN (...). Find which payload key(s) can exceed the CursorWindow (~2 MB), why (what is cached, how big, any existing size caps such as the 16Mi encoded chars per feed mentioned in run typed-feeds-memory-budget), how the read path (CachedPayloadDao.readAll, PersistentPayloadStore, CachedFeed.peekAll) turns a single bad row into a failure of a whole screen, and the fix options (cap on write, chunked rows, per-key reads with failure isolation, skipping/deleting oversized rows, length() precheck query), with their trade-offs. Also look for related runs in /home/dev/Puber/.flow/runs (cache-layer-rework, typed-feeds-memory-budget, card-image-size-tier) and say whether this overlaps.

## 2. Understanding

The content cache writes each title's record, including the full seasons/episodes/files tree once details have been fetched, as one JSON TEXT row in `cached_payload`. Nothing limits a row's size on write. The platform SQLite driver reads rows through a CursorWindow (2048 kB in AOSP), so a row larger than the window can be written but never read again. Every list that names that title reads its card rows in one `IN (...)` query, so the one bad row fails the query, then the list, then the whole shelf or grid. The failure happens before any network request, and Retry repeats it. The fix needs a per-row size bound on write and a read path that drops an unreadable row instead of failing, so devices that already hold such a row (the TV box) heal themselves.

## 3. Current state in code

Paths are relative to `app/src/main/java/com/kino/puber/`.

**Storage and read path**

- `data/db/CachedPayloadEntity.kt:21-27`: one row = `key`, `payload: String` (TEXT), `updated_at`. There is no size column.
- `data/db/CachedPayloadDao.kt:10-14`: `read` and `readAll` are `SELECT *`. `readAll` reads every key in one `IN (:keys)` query. No query projects only some columns or checks a row's length.
- `data/db/PuberDatabase.kt:37-45`: Room 3 with `AndroidSQLiteDriver` (the platform `android.database.sqlite`), whose results go through a CursorWindow. The device stack trace confirms it (catalogue-browse-all evidence). Versions: room 3.0.3, sqlite 2.7.1 (`gradle/libs.versions.toml:33-34`), minSdk 24 (`buildSrc/src/main/kotlin/Versions.kt:6`).
- `data/repository/PersistentPayloadStore.kt:56-65`: `read` and `readAll` map rows directly. A DAO exception passes straight through, with no catch and no per-key isolation. `write` and `writeAll` (`:67-86`) upsert any length.
- `data/cache/CachedFeed.kt:346-373` (`peekAll`): one `store.readAll(missing)` for every key that is not in memory, so one bad row fails the batch. `decodeUsable` (`:619-634`) already deletes a row that is past the hard ceiling or does not decode, but it only sees rows the query actually returned.
- `data/cache/CachedFeed.kt:197-286` (`load`): a loader failure with nothing cached is rethrown (`:280-281`).

**What is stored and how big**

- `data/cache/ContentCacheRepository.kt:491-496`: `ItemRecord` wraps the whole `Item`. `data/api/models/Models.kt:46-99`: `Item` carries `seasons` → `episodes` → `files` (`VideoFile` with four URLs: http, hls, hls2, hls4, `:579-595`), plus `audios`, `subtitles` and `videos`.
- `ContentCacheRepository.kt:396-400` (comment): a details record for a long series is "a megabyte of JSON". Kotlin counts `length` in UTF-16 chars, while SQLite stores the TEXT as UTF-8, so Cyrillic titles and audio names take 2 bytes a char. A long 4K series (more qualities, more files per episode) can pass 2 MB. The actual size on the box is `unverified`.
- `ContentCacheRepository.kt:433-437` (`mergeSparse` keeps `seasons`/`videos` from details): once a details tree is in a row, list merges keep it there.
- A details tree gets written without the user opening the title: `domain/interactor/prefetch/DetailsPrefetcher.kt:141` warms details of the focused card and its neighbours through `data/repository/ItemDetailsRepository.kt:43-60` → `ContentCacheRepository.observeItemDetails` (`:191-232`). Browsing the 4K grid is enough to write a large series record.
- List and page rows (`ItemListRecord`/`ItemPageRecord`, `:498-505`) hold only ids and pagination, so they stay small. History rows (`:507-541`) hold one `Video` per entry and are small (`unverified` for a page of long videos). Payload rows (`observePayload`: `HomeInteractor.kt:101`, `CollectionInteractor.kt:18`, `BookmarkFoldersInteractor.kt:30`, `GenreInteractor.kt:18`) are small lists (`unverified` by measurement).
- Existing caps, none of them on a row's size:

| Cap | Where | Bounds |
|---|---|---|
| `MEMORY_TIER_ENCODED_CHARS` = 16Mi chars per feed | `ContentCacheRepository.kt:549`, `:401-402` | memory tier only (typed-feeds-memory-budget) |
| `MAX_ROWS` = 2,000 | `data/cache/CachedPayloadSweeper.kt:75` | row count |
| `HardCeiling` = 7 days | `CachedFeed.kt:652`, sweep `CachedPayloadSweeper.kt:65-70` | row age |

**How one row fails the whole screen (4K «Все»: `ui/feature/contentlist/model/TabTypeConfig.kt:213-218`, quality 4k, sort `updated-`)**

1. `domain/interactor/contentlist/ContentListInteractor.kt:81-96` → `ContentCacheRepository.sectionPage` → `observeItemPage` (`:80-112`).
2. `queryNeedsRefetch` (`:380-387`) runs before the load. It reads the stored page's ids, then `readItemRecords(ids)` (`:414-418`) → `itemFeed.peekAll` → one `IN` query over the page's `content:v2:item:<id>` keys → `SQLiteBlobTooBigException`. Nothing catches it, and no network request has been made yet, which matches "Retry fails in 0.25 s with no request".
3. If the page row is gone, the loader's `mergeItems` (`:327-349`) reads `existing` with the same `readItemRecords` and fails the same way after the fetch. With nothing cached, `CachedFeed.load` rethrows (`CachedFeed.kt:281`).
4. `ui/feature/contentlist/vm/ContentListPagingVM.kt:174` collects `observeFirstPage`, and the error ends the section in Error.
5. A filtered query is `cacheable = false` (`ContentListInteractor.kt:67-71`). It never calls `mergeItems` or `readItemRecords`, which is why the filtered 4K grid works (catalogue-filters evidence, note 1).
6. The same item fails anywhere it is read: any list or page containing its id, and its own Details screen (`observeItemDetails` → `readItemRecord`, `:196`, then `peekAll` of one key).
7. Self-healing exists but is slow. Writes to the bad key fail first on the read in `mergeItems`, so `updated_at` stops moving, and the sweep (`deleteOlderThan`, which does not read `payload`) deletes the row up to 7 days after its last write. The 2,000-row cap may delete it sooner.

**Tests around it**

- `app/src/test/kotlin/com/kino/puber/data/repository/PersistentPayloadStoreTest.kt` (JVM, fake DAO at `:133-170`), `.../data/cache/CachedFeedTest.kt`, `CachedFeedMemoryWeightTest.kt`, `ContentCacheRepositoryTest.kt`, `util/FakePayloadStore.kt`. None of these can reproduce a CursorWindow failure: the fakes have no window.
- `app/src/androidTest/kotlin/com/kino/puber/data/db/CachedPayloadDaoTest.kt` is instrumented against a real Room database and can hold a >2 MB row test. It runs only through `make DEVICE=<serial> itest`.

## 4. Related work

| Run / doc | Relation |
|---|---|
| catalogue-browse-all (done) | Source. qa round 1 defect 2, triage-1 #12 deferred here; `docs/superpowers/plans/2026-11-24-catalogue-browse-all.md:268` |
| catalogue-filters (done) | Same failure on the unfiltered 4K grid; `docs/superpowers/evidence/2026-11-24-catalogue-filters-device.md:61` |
| cache-storage-bounds (done, d52b5800) | Added the age and row-count sweep. Q2 chose a row cap over a byte cap, and no per-row size bound came out of it |
| cache-decoded-memory-tier (done, 5b627683) | Introduced the 16Mi-char per-feed memory weight. Memory only |
| typed-feeds-memory-budget (idea) | Memory-tier budget across feeds. A different layer (heap, not SQLite rows); could share the size measure. Not a duplicate |
| cache-layer-rework (idea, umbrella, split into B1-B4) | No item bounds row size. Not a duplicate |
| card-image-size-tier (idea) | Image disk tier for 4K posters. Unrelated despite "4K" |
| persistent-content-cache (2026-08, `docs/superpowers/specs/2026-08-13-persistent-content-cache-design.md`) | Origin of the one-row-per-item design |

Commits touching the path: ce32f8a2, 5b627683, d52b5800, 264748e7, d8682b3c (`git log -- CachedPayloadDao.kt PersistentPayloadStore.kt CachedFeed.kt ContentCacheRepository.kt`). `git log -S CursorWindow` finds only run records (caffed82, b7a7d99e, 2f245bef, 8e005395), not code.

DUPLICATE_OF: none.

## 5. External findings

| Finding | Source |
|---|---|
| The default window comes from `config_cursorWindowSize` (kB) × 1024. `CursorWindow(String, long windowSizeBytes)` exists, but Room/`AndroidSQLiteDriver` exposes no way to pass it | AOSP `CursorWindow.java` (main), https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/database/CursorWindow.java ; https://developer.android.com/reference/android/database/CursorWindow |
| AOSP sets `config_cursorWindowSize` to 2048. An OEM overlay can change it, and the TV box's value is `unverified` | https://android.googlesource.com/platform/frameworks/base/+/android-8.1.0_r1/core/res/res/values/config.xml (search result; file not read in full) |
| `SQLiteBlobTooBigException` (API 11) is a `SQLiteException` subclass | https://developer.android.com/reference/android/database/sqlite/SQLiteBlobTooBigException |
| `AndroidSQLiteDriver` (sqlite-framework 2.5.0+) is built on the SDK's `android.database` APIs. `BundledSQLiteDriver` ships its own native SQLite (`sqlite-bundled`) | https://developer.android.com/reference/kotlin/androidx/sqlite/driver/AndroidSQLiteDriver ; https://developer.android.com/reference/kotlin/androidx/sqlite/driver/bundled/BundledSQLiteDriver |
| SQLite `length(X)` on TEXT counts characters and `substr` counts characters. Byte length needs `length(CAST(X AS BLOB))` or `octet_length` (newer SQLite; the version that added it is `unverified`, so do not rely on it with minSdk 24) | https://www.sqlite.org/lang_corefunc.html |
| Common practice: keep rows small, split large values or store them outside the row. Enlarging the window by reflection on `sCursorWindowSize` is an unofficial hack | community reports (search 2026-11-24), e.g. https://github.com/100nandoo/shelfdroid/pull/342 ; not primary |

Whether the framework's window stores TEXT as UTF-8 or UTF-16 is `unverified`. A limit measured in UTF-8 bytes with a wide margin holds either way.

## 6. Options

| | Option | Size | Trade-offs |
|---|---|---|---|
| A (recommended) | **Bound on write, heal on read in `RoomPersistentPayloadStore`/DAO.** Write: a payload whose UTF-8 size is over a limit (e.g. 1 MiB) is not stored, and any older row for the key is deleted; the value still lives in the memory tier for the session. Read: `readAll`/`read` select only rows with `length(CAST(payload AS BLOB)) <= :max` and delete the rest by key (or catch `SQLiteBlobTooBigException` and fall back to per-key reads that delete the failing key) | S (DAO, store, store tests and instrumented DAO test; no schema change) | Heals the box with no data clear. A screen shows every other card. An oversized series is re-fetched after a restart (no persistent details for it). The length precheck costs a full read of each big row's pages; the catch fallback costs nothing on the normal path but depends on the exception type reaching the store unwrapped (`unverified` under Room 3) |
| B | **Store less per item.** Persist the details tree outside `ItemRecord` (per-season rows) or strip file URLs from the persisted copy and fetch links when playback starts (`items/media-links`, `KinoPubApiClient.kt:707`) | M (data shape of the cache, merge rules, playback link path) | Keeps offline details for long series, but changes the record contract, the `mergeSparse` policy and possibly playback start latency. Still needs A's read guard for rows already on devices |
| C | **Remove the window.** Switch to `BundledSQLiteDriver`, or generic chunked rows (`key#n`, Room schema bump) | M | Bundled: no CursorWindow, but a native library in the APK, a different SQLite for `watch_state` too, and threading rules change; large rows still cost heap. Chunking: atomic multi-row writes, sweep and prefix-delete semantics, a migration. Both are bigger than the bug |

Rejected inside these options: enlarging the window by reflection (hidden API, OEM-dependent).

## 7. Risks

- Isolation that only catches and logs, without deleting, makes every read retry the failing query. The row has to go, or be excluded from the query.
- A write skipped for an oversized key must also remove the key's older row and keep the `CachedFeed` epoch/ownership logic (`CachedFeed.kt:388-434`) consistent. Doing it in the store below the feed keeps the feed's barriers untouched, but `putAll` then puts in memory a value the store refused (acceptable: it is session-only).
- A missing row is already a handled case (`readItems` drops it, `queryNeedsRefetch` asks for a refetch, `ContentCacheRepository.kt:355-387`), so excluding a row reuses tested behaviour.
- The device check needs the box's existing bad row. An install-preserve keeps it, so the box reproduces the bug before the fix and shows the heal after. Clearing data is not needed and stays unauthorized.
- Which key is oversized on the box is unconfirmed. A read-only `run-as` query of the largest `length(CAST(payload AS BLOB))` rows needs a lease and is the qa's call.

## 8. Open questions

| # | Question | Recommended answer |
|---|---|---|
| 1 | Per-row limit, and in what unit? | 1 MiB of UTF-8 bytes, a constant next to the store. Half the AOSP window, which leaves room for OEM windows and row overhead |
| 2 | An oversized details record: memory only, or a trimmed copy persisted? | Memory only in this run (option A); trimming or splitting the tree (option B) as a follow-up idea if long series must open offline |
| 3 | Read-side guard: `length(CAST(...))` precheck in SQL, or catch-and-fall-back to per-key reads? | SQL filter plus delete of over-limit keys in the same store call. Deterministic, no reliance on the exception type, testable on the instrumented DAO test |

## 9. Sources

- Files: those cited in section 3; `.flow/runs/{catalogue-browse-all,catalogue-filters,cache-storage-bounds,cache-layer-rework,typed-feeds-memory-budget,card-image-size-tier}/state.md`; `docs/superpowers/evidence/2026-11-24-catalogue-filters-device.md`; `docs/superpowers/plans/2026-11-24-catalogue-browse-all.md`.
- Commits: ce32f8a2, 5b627683, d52b5800, 264748e7, d8682b3c; base 8e005395.
- URLs: listed in section 5 (fetched 2026-11-24).
