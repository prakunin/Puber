# Test design: cached-item-card-only-fallback

Base 4db1b061. Test paths under `app/src/test/kotlin/com/kino/puber/`. ACs: [brief.md](brief.md#acceptance-criteria); design: [design-architect.md](design-architect.md). Level `unit` = JUnit 5 + coroutines-test, no device; perf track off.

## Fixture first

`util/FakePayloadStore.kt` gets `maxPayloadLength: Int = Int.MAX_VALUE` (refuse `payload.length > maxPayloadLength`, like `refusedKeys`) and `onBeforeWriteAll` (fires before a bulk write lands, mirror of `onBeforeWrite`) **before** any production change, with the base refusal semantics (row removed). Every `red` row below then runs against unchanged production code and must fail there. `red (compile)`: the test calls a new signature (`write` result, `writeAll` result, `persistFallback`), so on base it fails to compile; its Proves column is what it catches after the change.

## Risk profile

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

## Traceability

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

## Failing-first test

`CachedFeedTest.aRefusedLoadCrossedByABulkWriteKeepsTheNewerRow` (problem 2) and `ContentCacheRepositoryTest.aListMergeOverAnOversizedDetailsRecordPersistsACardOnlyRow` (problem 1), written after the fixture and run on base before any production edit. Base trace: `git -C <wt> show 4db1b061:app/src/main/java/com/kino/puber/data/repository/PersistentPayloadStore.kt` lines 131-134 (`dao.delete(key)` on refusal) and `ContentCacheRepository.kt` 414-438 (merge keeps the seasons tree).

## Manual checks

Box `192.168.1.110:5555`, `com.kino.puber.stage` only, lease held, fixed dev APK installed with `tools/android-apk-install-preserve`, online.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| 1 | 1. `adb -s <serial> logcat -c`. 2. Rail to 4K, UP to «Всё в 4K», DPAD_CENTER; open a long series with many seasons (DPAD_CENTER on its card), wait for episodes; BACK. 3. Browse the grid (DPAD_DOWN x3). 4. `am force-stop com.kino.puber.stage`. 5. Copy `databases/puber.db` with `-wal`/`-shm` via `run-as`; query `SELECT key, length(CAST(payload AS BLOB)), instr(payload,'"episodes"'), instr(payload,'"detailsUpdatedAt"') FROM cached_payload WHERE key = 'content:v2:item:<id>'`. If the row still contains `"episodes"` the title is under 1 MiB: repeat step 2 with another series (R11); record the ids tried. 6. `logcat -d --pid` / app filter for `Refused cached payload content:v2:item:<id>` | row present, length <= 1048576, no `"episodes"`, `detailsUpdatedAt` absent or null; zero matching log lines | missing row; length > 1048576; a refused line for the id; db copy or raw rows in evidence |
| 2 | 1. Cold start (launch). 2. Rail to 4K, «Всё в 4K», DPAD_CENTER. 3. Focus the series from check 1, DPAD_CENTER | grid shows the card with its poster; details show seasons and episodes after load | error state on the grid; empty or spinning details; crash |

## UNTESTABLE

Offline cold start on the box (network cut drops adb, not authorised): covered by unit AC-4 only.
