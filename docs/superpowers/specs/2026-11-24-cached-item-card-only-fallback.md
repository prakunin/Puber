# Persist a card-only copy of an item record over the row limit, and let only the owning writer delete a refused key

Status: approved
Date: 2026-11-24
Slug: cached-item-card-only-fallback
Kind: bug
Size: M
Tracks: none

## Problem / Why

### Request

Two defects left by the row-size cap (merge ba679aea), state.md Log 2026-11-24: (1) an item whose details record is over the limit never gets its card persisted; (2) a refused write deletes the key's row with no ownership check, so a concurrent small card write can be lost.

### Root cause

Both **confirmed by code trace** at 4db1b061; neither reproduced on a device. Problem 2 is a timing race that only a unit test with `FakePayloadStore` hooks can force (research.md: 7).

| # | Step | Where |
|---|---|---|
| 1 | A card merge reads the existing record memory first, so it starts from the full details record | `data/cache/ContentCacheRepository.kt:414-418` (`readItemRecords` via `peekAll`) |
| 1 | `mergeItemRecord` keeps the existing seasons/videos tree, so the merged card is as large as the details record | `ContentCacheRepository.kt:420-438` |
| 1 | `writeAll` refuses it over `MAX_ROW_BYTES` and deletes the key: the title has no row; after eviction or restart lists naming it refetch (`ContentCacheRepository.kt:364-387`), offline the card is dropped | `data/repository/PersistentPayloadStore.kt:47,110-134` |
| 2 | `refuse` calls `dao.delete(key)` unconditionally, below `CachedFeed`'s epoch checks | `PersistentPayloadStore.kt:131-134` |
| 2 | `CachedFeed.load` writes after its epoch check; a `putAll` of the same key can upsert a small card in between, which the refusal's delete then removes. `putAll` itself never deletes a row another writer owns | `data/cache/CachedFeed.kt:237-249`, `:388-434` |
| 2 | The store cannot report a refusal: `write`/`writeAll` return `Unit` | `PersistentPayloadStore.kt:28-31` |

### Chosen approach

research.md: 6, option A, with the recommended answers to Q1-Q3 below.

- The store stops deleting on refusal and reports it (`write` returns whether it stored, `writeAll` the refused keys).
- `CachedFeed` takes an optional fallback `(V) -> V`. When the store refuses the full payload, it writes the fallback's payload under the same epoch ticket; when that is refused too, it deletes the row only while it still owns the key (`load`: `epochOf(key) == epoch` after the write; `putAll`: `settled == epoch`). Supersede writes keep their existing delete. Memory keeps the full value and its full `encodedLength`.
- `ContentCacheRepository` gives `itemFeed` a card-only fallback: `seasons` mapped to skeletons with `episodes = null`, `videos = null`, `detailsUpdatedAt = null`. Other feeds pass none (research.md: 3, "Other feeds and the limit").

Size M: persistence and caches is a risk area (`.flow/roles/analyst.md`); touches `PersistentPayloadStore.kt`, `CachedFeed.kt`, `ContentCacheRepository.kt` and every `PersistentPayloadStore` implementation in tests (`app/src/test/kotlin/com/kino/puber/util/FakePayloadStore.kt`, `ItemDetailsRepositoryTest.kt:265`, `HomeInteractorCacheTest.kt:217`).

## Acceptance criteria

| ID | Given | When | Then | Checked by |
|---|---|---|---|---|
| AC-1 | a store holding a small row for a key | `write` or `writeAll` is given an over-limit payload for it | the call reports the key as refused and the old row is still there (the store no longer deletes) | unit, `PersistentPayloadStoreTest.kt` |
| AC-2 | `itemFeed` holding a details record over the limit in memory (seasons with episodes and files) | a list merge for that item runs (`mergeItems`) | the store holds a row for the key within the limit, whose decoded record has the card fields of the merge, season skeletons with `episodes == null`, `videos == null`, `detailsUpdatedAt == null`; `peek` in the same session still returns the full record | unit, `ContentCacheRepositoryTest.kt` over a store with a small limit |
| AC-3 | the state of AC-2 | the memory tier is dropped (new repository over the same store) and the list page naming the item is read | the card is returned from the store with no loader call; `observeItemDetails` for it calls its loader (details refetched) | unit, `ContentCacheRepositoryTest.kt` |
| AC-4 | the state of AC-3 with a loader that fails (offline) | the list page is read | the card is in the result (today it is dropped) | unit |
| AC-5 | a details `load` of an over-limit record and a `putAll` of a small card for the same key, the card upsert landing between the load's epoch check and its refused write (`FakePayloadStore.onWrite`) | both finish | the key has a row within the limit; no write deletes a row another writer owns | unit, `CachedFeedTest.kt` |
| AC-6 | a fallback that is itself over the limit | `put`, then a supersede `put` (as `markItemDetailsStale`) | an owned refusal leaves no row; a non-owning writer leaves the other writer's row; the supersede write persists its fallback or removes the row, never leaves the superseded payload | unit, `CachedFeedTest.kt` |
| AC-7 | the box with the fixed dev APK installed by install-preserve, online | the details of a series whose record encodes over 1 MiB are opened (one from the 4K «Все» grid, research.md: 3, evidence `docs/superpowers/evidence/2026-11-24-cached-payload-row-too-big-device.md:10`), then the 4K «Все» grid is browsed, then `am force-stop` | a copy of `databases/puber.db` (with `-wal`/`-shm`) taken through `run-as com.kino.puber.stage` holds `content:v2:item:<id>` with `length(CAST(payload AS BLOB)) <= 1048576` and `"detailsUpdatedAt"` absent or null; logcat filtered to the app has no `Refused cached payload content:v2:item:<id>` line | observation on the box; only query results go into evidence, not the db copy |
| AC-8 | AC-7 after a cold start, online | the 4K «Все» grid is opened, then the series' details | the grid shows the card; the details screen loads and shows seasons and episodes | observation on the box |

The offline cold start is unit-only (AC-4): cutting the box's network drops adb and is not authorised.

### Risks (brief)

| Risk | Where |
|---|---|
| After eviction or restart the card-only row has `detailsUpdatedAt = null`: Details refetches before showing anything and throws offline, as today | `ContentCacheRepository.kt:197-200,229` |
| Memory and Room hold different shapes for one key; a reader of `seasons` beyond the count (hero label, `buildDuration`) sees skeletons after eviction | `core/ui/model/VideoItemUIMapper.kt:82-86,159-163` |
| The fallback write must reuse the original epoch ticket and generation check; a second write path outside them reopens the race of problem 2 | `CachedFeed.kt:99-195,474-487` |
| The check-then-delete window for an owned refusal stays, as for the existing `store.remove` calls | `CachedFeed.kt:249,422` |
| Two encodes for an oversized value on every list merge of that item while it is in memory | `CachedFeed.kt:235,397` |
| AC-7 needs a title whose record is over 1 MiB on the box; the 2.4 MB row was healed on read in the source run and its id was not recorded | evidence `2026-11-24-cached-payload-row-too-big-device.md:10` |

## Out of scope

- Splitting a details record across several rows or persisting episodes for an oversized series.
- Offline cold start on the TV box (network adb); covered by unit tests only.

## Design

Paths without a prefix are under `app/src/main/java/com/kino/puber/`; test paths under `app/src/test/kotlin/com/kino/puber/`. Lines at base 4db1b061.

### Decisions

| Question | Decision | Why |
|---|---|---|
| How does the store report a refusal? (Q3) | `write(...): Boolean` (false: over the row limit, nothing written, no row touched); `writeAll(...): Set<String>` of refused keys. The generation-moved compensating delete stays and still returns true | Problem 2: only the feed knows who owns a key; the limit stays private to the store |
| Who deletes a refused key's old row? | `CachedFeed`, only while it owns the key: `load` when `epochOf(key) == epoch` after the refused write(s); `putAll` when `epochOf(key) == epochs[key]` | Same ownership rule as the existing `settled == epoch` paths; a stale row must still not outlive the value that replaced it |
| Where does the card-only copy come from? (Q1) | `CachedFeed` constructor parameter `persistFallback: ((V) -> V)? = null`; called only for a refused key, its payload written in the same `load`/`putAll` call under the original epoch | One encode extra only for oversized values; no new write path outside the ticket model |
| When is the fallback written? | Only if the key is still owned (`epochOf(key) == epoch`) after the refusal; otherwise the other writer's row stands and nothing is written or deleted | Closes AC-5: a refused write never overwrites or deletes a newer writer's row |
| Card-only shape (Q2) | `ItemRecord.cardOnly()`: `item.copy(seasons = item.seasons?.map { Season(it.id, it.number, it.title) }, videos = null)`, `detailsUpdatedAt = null`, `cardUpdatedAt` kept | Keeps the hero "N сезонов" label; `detailsUpdatedAt = null` makes Details refetch (`ContentCacheRepository.kt:197-198`) |
| What does memory hold? | The full value with the full payload's `encodedLength`, as today | `peek` in the session returns full details (AC-2); the 16 M char weigher stays honest |
| Where is the refusal logged? | The store no longer logs; `CachedFeed` logs `Refused cached payload %s ...` (`Timber.w`) only when nothing was persisted for an owned key (no fallback, or fallback refused) | A refused full record followed by a stored card-only copy is the normal path; AC-7 expects no such line for it |
| `markItemDetailsStale` over a card-only row | Write `detailsUpdatedAt = current.detailsUpdatedAt?.let { STALE_TIMESTAMP }` (null stays null) | After memory eviction the record read is the card-only row; stamping it 0 makes `observeItemDetails` emit an episode-less item as details (`ContentCacheRepository.kt:219-222`). See Open questions |

### Changes by layer

#### API / repository

| File | Change |
|---|---|
| `data/repository/PersistentPayloadStore.kt:29-32` | `write` returns `Boolean`; default `writeAll` returns the keys whose `write` returned false |
| `data/repository/PersistentPayloadStore.kt:99-109` | Over-limit: return false with no DAO call; otherwise upsert, generation check as now, return true |
| `data/repository/PersistentPayloadStore.kt:111-126` | Return the oversized keys; upsert and generation check of the kept rows unchanged |
| `data/repository/PersistentPayloadStore.kt:130-134` | `refuse` removed (no delete, no log) |
| `data/cache/CachedFeed.kt:46-56` | New constructor parameter `persistFallback: ((V) -> V)? = null` |
| `data/cache/CachedFeed.kt:238-250` (`load`) | Write via a private helper: `store.write` false -> if owned and fallback present, encode the fallback and `store.write` it; if still not stored and owned, `store.remove(key)` and log. The existing `settledAfterWrite.supersedesWrite(epoch)` remove and `discardValue` follow unchanged. `encodedLength` stays `payload.length` (:263) |
| `data/cache/CachedFeed.kt:396-400` (`putAll`) | Keep `refused = store.writeAll(...)`. For refused keys still owned (`epochOf(key) == epochs.getValue(key)`) with a fallback: one `store.writeAll` of the encoded fallbacks. Each owned key refused with no fallback stored: `store.remove(key)` and log. Then the settle loop at :401-432 unchanged (memory gets the full value, `encodedLength` from the full payload at :412; the supersede branch at :418-424 keeps its delete) |
| `data/cache/ContentCacheRepository.kt:389-403` (`newFeed`) | Optional `persistFallback` parameter passed through to `CachedFeed` |
| `data/cache/ContentCacheRepository.kt:37` | `itemFeed = newFeed(ItemRecord.serializer(), persistFallback = ItemRecord::cardOnly)`; other feeds unchanged |
| `data/cache/ContentCacheRepository.kt:491-496` (`ItemRecord`) | Add `fun cardOnly(): ItemRecord` (shape in Decisions); `Season` at `data/api/models/Models.kt:542-547` |
| `data/cache/ContentCacheRepository.kt:293-300` (`markItemDetailsStale`) | Null `detailsUpdatedAt` stays null (Decisions) |

#### Test doubles (interface ripple)

| File | Change |
|---|---|
| `util/FakePayloadStore.kt:38-50,62-67` | Return values per the new interface; a refusal no longer removes the row; add `maxPayloadLength: Int = Int.MAX_VALUE` refusing `payload.length > maxPayloadLength` next to `refusedKeys` (:12), so a fallback can fit where the full payload does not; hooks fire as now |
| `domain/interactor/home/HomeInteractorCacheTest.kt:225-227` | `write` returns true |
| `data/repository/ItemDetailsRepositoryTest.kt:273-275` | `write` returns true |
| `data/repository/PersistentPayloadStoreTest.kt:96-117` | The two tests asserting the delete are replaced by AC-1 tests (row kept, key reported) |

#### ViewModel state and actions; screen and DI module; Compose content; resources

N/A: the change ends at `ContentCacheRepository`; no public signature above it moves.

### Alternatives rejected

| Alternative | Reason |
|---|---|
| Repository trims before every write (research.md: 6, B) | Extra multi-megabyte encode on every details load, and memory would lose full details |
| Store trims the JSON (research.md: 6, C) | Store learns `ItemRecord`'s shape and parses 2+ MB on the write path |
| Store exposes `fits(payload)` / `MAX_ROW_BYTES` to the feed | Leaks the limit and the byte counting out of the store; a check-then-write pair instead of one call |
| `seasons = null` in the card-only copy | Hero "N сезонов" label lost after a cold start (`core/ui/model/VideoItemUIMapper.kt:82-86`) |
| Fallback write as a separate `put` from the repository | New write path outside the ticket and generation checks; reopens problem 2 |

### Risks

| Risk | Kind | Where |
|---|---|---|
| The fallback write must reuse the caller's epoch and re-check ownership before writing and before deleting; a write after another writer's `advanceKey` overwrites its newer row | concurrency | `CachedFeed.kt:232-250`, `:392-432`, `:474-487` |
| Memory (full) and Room (card-only) differ for one key; after eviction `peekAll` promotes the card-only row, so readers of `seasons`' episodes or `videos` see none until details reload | data shape | `CachedFeed.kt:581-602`, `ContentCacheRepository.kt:414-418` |
| A card-only row stamped `detailsUpdatedAt = 0` by `markItemDetailsStale` (called after playback, `ui/feature/player/vm/PlayerVM.kt:1986`) would be emitted as details without episodes; covered by the null-stays-null decision | correctness | `ContentCacheRepository.kt:219-222,293-300` |
| Check-then-delete window for an owned refusal, as for the existing `store.remove` calls | concurrency | `CachedFeed.kt:249,422` |
| `HomeInteractorTest` uses a relaxed `mockk<PersistentPayloadStore>`: `write` returns false, so every load takes the refusal path (fallback encode, `remove` on the mock); behaviour unchanged since the mock stores nothing | test | `domain/interactor/home/HomeInteractorTest.kt:50,275` |
| Detekt complexity/`ReturnCount` in `load`'s loader lambda and `putAll`; the fallback logic belongs in private helpers | rollout | `CachedFeed.kt:228-265,388-434` |
| Two encodes per write of an oversized value while it is in memory (each list merge of that item) | performance | `CachedFeed.kt:235,397` |
| After restart the card-only row forces a Details refetch; offline Details throws, as today | lifecycle | `ContentCacheRepository.kt:197-200,229` |

### Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| D1 | No AC covers the `markItemDetailsStale` null-stays-null change; add a unit row (card-only record from Room, `markItemDetailsStale`, `observeItemDetails` calls its loader and emits no episode-less value first)? | Yes: test-designer adds it under AC-3's fixture; no AC change | no |

## Test design

Base 4db1b061. Test paths under `app/src/test/kotlin/com/kino/puber/`. ACs: [brief.md](brief.md#acceptance-criteria); design: [design-architect.md](design-architect.md). Level `unit` = JUnit 5 + coroutines-test, no device; perf track off.

### Fixture first

`util/FakePayloadStore.kt` gets `maxPayloadLength: Int = Int.MAX_VALUE` (refuse `payload.length > maxPayloadLength`, like `refusedKeys`) and `onBeforeWriteAll` (fires before a bulk write lands, mirror of `onBeforeWrite`) **before** any production change, with the base refusal semantics (row removed). Every `red` row below then runs against unchanged production code and must fail there. `red (compile)`: the test calls a new signature (`write` result, `writeAll` result, `persistFallback`), so on base it fails to compile; its Proves column is what it catches after the change.

### Risk profile

| # | Risk (source) | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Fallback or owned delete runs outside the epoch ticket and overwrites or deletes a newer writer's row (design Risks 1, brief problem 2) | 2 | 3 | 6 | AC-5, AC-6 rows |
| R2 | Store stops deleting but the feed does not take over: an owned refusal leaves the superseded smaller row behind | 2 | 3 | 6 | `anOwnedPutRefusalWithNoFallbackLeavesNoRow`, `anOwnedLoadRefusalWithNoFallbackLeavesNoRow` |
| R3 | `markItemDetailsStale` stamps a card-only record `detailsUpdatedAt = 0`, so Details emits an episode-less item (design D1) | 3 | 2 | 6 | D1 row |
| R4 | Card-only row loses card fields or season count (hero "N сезонов") after eviction | 2 | 2 | 4 | AC-2, AC-3 rows |
| R5 | Memory weigher counts the fallback's length, under-counting the 16 M char tier | 1 | 3 | 3 | `aRefusedValueStillWeighsItsFullPayloadInMemory` (P1) |
| R6 | Card-only row keeps `detailsUpdatedAt`, so Details serves it as details after restart | 2 | 3 | 6 | AC-3 details row |
| R7 | Offline Details over a card-only row throws, as today (brief Risks 1) | 3 | 1 | 3 | existing `detailsOverACardOnlyRecordReportTheFailureInsteadOfEmittingNothing` stays green |
| R8 | Check-then-delete window for an owned refusal | 1 | 2 | 2 | accepted: same window as existing `store.remove` calls |
| R9 | Two encodes per merge of an oversized in-memory item | 3 | 1 | 3 | accepted: no perf track; bounded to oversized values |
| R10 | Relaxed `mockk<PersistentPayloadStore>` in `HomeInteractorTest` returns `false`, so every write takes the refusal path | 1 | 1 | 1 | accepted: full unit suite stays green |
| R11 | No >1 MiB title found on the box for AC-7 | 2 | 2 | 4 | Manual step 2 |

### Traceability

| AC | Level | Priority | Base | Test | Proves |
|---|---|---|---|---|---|
| AC-1 | unit | P0 | red | `PersistentPayloadStoreTest.writeOfAnOversizedPayloadKeepsTheKeysOlderRow` | fails if `write` still deletes the key on refusal (replaces `writeOfAnOversizedPayloadDeletesTheKeysOlderRow`) |
| AC-1 | unit | P0 | red | `PersistentPayloadStoreTest.writeAllKeepsTheOlderRowOfAnOversizedKeyAndStoresTheSmallOne` | fails if `writeAll` deletes the oversized key or drops the small one (replaces `writeAllStoresTheSmallPayloadAndDropsTheOversizedKey`) |
| AC-1 | unit | P0 | red (compile) | `PersistentPayloadStoreTest.writeReportsAnOversizedPayloadAsNotStored` | `write` returns `false` over the limit, `true` at exactly the limit; fails if the result is inverted or always true |
| AC-1 | unit | P0 | red (compile) | `PersistentPayloadStoreTest.writeAllReturnsOnlyTheOversizedKeys` | fails if the refused set is empty or names a stored key |
| AC-1 | unit | P1 | guard | `PersistentPayloadStoreTest.writeStoresAPayloadOfExactlyTheLimitAndRefusesOneByteMore` (existing) | byte boundary unchanged |
| AC-2 | unit | P0 | red | `ContentCacheRepositoryTest.aListMergeOverAnOversizedDetailsRecordPersistsACardOnlyRow` | details series (seasons with episodes and files) loaded, `store.maxPayloadLength` between card-only and full length, then `mergeItems` of a card with a new title: row exists, length <= limit, decoded JSON has the merged title, each season with `id`/`number`/`title` and no `episodes`, no `videos`, no `detailsUpdatedAt`; fails if no row is written (base) or a field of the shape is wrong |
| AC-2 | unit | P0 | guard | `ContentCacheRepositoryTest.anOversizedDetailsRecordIsStillServedInFullInTheSameSession` | after the merge, `observeItemDetails(7) { error("memory") }` emits the full item with episodes; fails if memory is replaced by the fallback |
| AC-3 | unit | P0 | red | `ContentCacheRepositoryTest.aCardOnlyRowServesTheListAfterARestartWithoutTheLoader` | AC-2 state, list stored, new repository over the same store: list emits the card with the season count kept and the loader is not called; fails if the row is missing (base) or seasons are dropped |
| AC-3 | unit | P0 | guard | `ContentCacheRepositoryTest.detailsOverACardOnlyRowAfterARestartCallTheLoaderFirst` | emissions are exactly the loaded details; fails if the card-only row keeps `detailsUpdatedAt` and is emitted as details (R6). Passes on base only because no row exists |
| AC-4 | unit | P0 | red | `ContentCacheRepositoryTest.aCardOnlyRowKeepsTheCardInAnOfflineListAfterARestart` | AC-3 state with the list past `CacheTtl.HomeSection` and a throwing loader: the emitted value holds the card; fails if the card is dropped (base) |
| D1 | unit | P0 | red | `ContentCacheRepositoryTest.markingACardOnlyRecordStaleStillMakesDetailsLoadFirst` | list caches card 7, `markItemDetailsStale(7)`, then `observeItemDetails(7) { details }` emits only the loaded details; fails on base (emits the card stamped 0 first) and if null-stays-null is removed |
| AC-5 | unit | P0 | red | `CachedFeedTest.aRefusedLoadCrossedByABulkWriteKeepsTheNewerRow` | `maxPayloadLength` refuses the load's value; `store.onBeforeWrite = { subject.putAll(mapOf("k" to "card")) }`; afterwards the row is `"card"`; fails if the refusal deletes by key (base) or the feed deletes without checking `epochOf(key) == epoch`. Uses `onBeforeWrite`, not `onWrite`: with `onWrite` the card lands after the delete and the test passes on base |
| AC-5 | unit | P0 | red (compile) | `CachedFeedTest.aRefusedLoadCrossedByABulkWriteWritesNoFallbackOverTheNewerRow` | same crossing with `persistFallback` set: row stays `"card"`; fails if the fallback is written without the ownership re-check |
| AC-6 | unit | P0 | red (compile) | `CachedFeedTest.aRefusedPutPersistsItsFallbackAndServesTheFullValueFromMemory` | row holds the fallback payload, `peek` the full value; fails if no fallback is written or memory holds the fallback |
| AC-6 | unit | P0 | red (compile) | `CachedFeedTest.aRefusedLoadPersistsItsFallback` | same through `load`; fails if only `putAll` got the fallback path |
| AC-6 | unit | P0 | red (compile) | `CachedFeedTest.aPutWhoseFallbackIsAlsoRefusedLeavesNoRow` | old small row, put with an over-limit fallback: no row; fails if the owned delete after a refused fallback is missing |
| AC-6 | unit | P0 | guard | `CachedFeedTest.anOwnedPutRefusalWithNoFallbackLeavesNoRow` | old small row, over-limit put, no fallback: no row; passes on base via the store's delete, fails after the change if the feed does not delete (R2) |
| AC-6 | unit | P0 | guard | `CachedFeedTest.anOwnedLoadRefusalWithNoFallbackLeavesNoRow` | same through `load` |
| AC-6 | unit | P0 | guard | `CachedFeedTest.aRefusedBulkWriteCrossedByANewerWriteKeepsTheNewerRow` | `onWriteAll` runs a newer small `putAll`: row is the newer value; fails if the `putAll` owned-delete skips `epochOf(key) == epochs[key]` |
| AC-6 | unit | P0 | red (compile) | `CachedFeedTest.aSupersedingPutPersistsItsFallbackNeverTheSupersededPayload` | row holds a small payload A; `put(supersede = true)` of an over-limit value: with a fitting fallback the row is the fallback, with an over-limit fallback no row; never A |
| AC-6 | unit | P1 | red (compile) | `ContentCacheRepositoryTest.markingAnOversizedDetailsRecordStaleKeepsACardOnlyRow` | AC-2 state, `markItemDetailsStale(7)`: row is card-only without `detailsUpdatedAt`; fails if the supersede write leaves no row or the stale full record |
| R5 | unit | P1 | red (compile) | `CachedFeedMemoryWeightTest.aRefusedValueStillWeighsItsFullPayloadInMemory` | weight cap fits two fallbacks but not two full values: the older key is evicted; fails if `encodedLength` comes from the fallback |
| AC-7 | manual | P0 | red | Manual check 1 | card-only row on the device, no refused-write log line |
| AC-8 | manual | P0 | guard | Manual check 2 | grid card and details render after cold start |
| all | unit | P0 | guard | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` | interface ripple in `HomeInteractorCacheTest`, `ItemDetailsRepositoryTest`, `HomeInteractorTest` (R10), detekt complexity in `CachedFeed` |

### Failing-first test

`CachedFeedTest.aRefusedLoadCrossedByABulkWriteKeepsTheNewerRow` (problem 2) and `ContentCacheRepositoryTest.aListMergeOverAnOversizedDetailsRecordPersistsACardOnlyRow` (problem 1), written after the fixture and run on base before any production edit. Base trace: `git -C <wt> show 4db1b061:app/src/main/java/com/kino/puber/data/repository/PersistentPayloadStore.kt` lines 131-134 (`dao.delete(key)` on refusal) and `ContentCacheRepository.kt` 414-438 (merge keeps the seasons tree).

### Manual checks

Box `192.168.1.110:5555`, `com.kino.puber.stage` only, lease held, fixed dev APK installed with `tools/android-apk-install-preserve`, online.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| 1 | 1. `adb -s <serial> logcat -c`. 2. Rail to 4K, UP to «Всё в 4K», DPAD_CENTER; open a long series with many seasons (DPAD_CENTER on its card), wait for episodes; BACK. 3. Browse the grid (DPAD_DOWN x3). 4. `am force-stop com.kino.puber.stage`. 5. Copy `databases/puber.db` with `-wal`/`-shm` via `run-as`; query `SELECT key, length(CAST(payload AS BLOB)), instr(payload,'"episodes"'), instr(payload,'"detailsUpdatedAt"') FROM cached_payload WHERE key = 'content:v2:item:<id>'`. If the row still contains `"episodes"` the title is under 1 MiB: repeat step 2 with another series (R11); record the ids tried. 6. `logcat -d --pid` / app filter for `Refused cached payload content:v2:item:<id>` | row present, length <= 1048576, no `"episodes"`, `detailsUpdatedAt` absent or null; zero matching log lines | missing row; length > 1048576; a refused line for the id; db copy or raw rows in evidence |
| 2 | 1. Cold start (launch). 2. Rail to 4K, «Всё в 4K», DPAD_CENTER. 3. Focus the series from check 1, DPAD_CENTER | grid shows the card with its poster; details show seasons and episodes after load | error state on the grid; empty or spinning details; crash |

### UNTESTABLE

Offline cold start on the box (network cut drops adb, not authorised): covered by unit AC-4 only.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Where does the card-only fallback live? | no | answered | a CachedFeed fallback hook supplied by ContentCacheRepository for itemFeed |
| Q2 | Card-only shape | yes | answered | season skeletons (episodes = null), videos = null, detailsUpdatedAt = null |
| Q3 | How does the store report a refusal? | no | answered | write returns Boolean, writeAll returns the refused keys, the store no longer deletes; the feed applies the fallback |
| D1 | markItemDetailsStale keeping a null detailsUpdatedAt null has no AC | no | answered | test-designer adds a unit row; no AC change |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-24 | Q1-Q3 and D1 on the recommended answers | owner (standing instruction: recommended) |
| 2026-11-24 | Gate 1: approved as designed (size M, no tracks, AC-1..AC-8) | owner (standing instruction: recommended) |
