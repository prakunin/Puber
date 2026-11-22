# Test design: cache-decoded-memory-tier

Base e9c383d0. Inputs: `brief.md` (ACs), `design-architect.md` (Decisions, Risks), `state.md` Decisions. Test paths under `app/src/test/kotlin/com/kino/puber/` unless named; levels per `.flow/roles/test-designer.md`.

## Risk profile

| Risk (source) | P | I | Score | Guard |
|---|---|---|---|---|
| Typed feed misses a store wipe, serves the previous account (design Risks 5) | 1 | 3 | 3 | AC-6 tests, one per feed |
| No-op predicate misses a column, real server update dropped (brief Risks 3) | 2 | 3 | 6 | AC-7 per-column instrumented test |
| `flowOn` buffer shifts Home publish coalescing (brief Risks 1) | 2 | 2 | 4 | `HomeVMIncrementalPublishTest` (AC-9) |
| Decode or loader still on the collector's dispatcher at one entry point | 2 | 2 | 4 | AC-2 test per public entry |
| Query row promoted by `peekAll` answers a load that must revalidate (design Risks 3) | 2 | 3 | 6 | Existing `ContentCacheRepositoryTest` refetch tests plus AC-1 stale case |
| Upgrade over v1 rows crashes or serves string-in-string values (design Risks 8) | 2 | 3 | 6 | AC-4 unit plus manual check 1 |
| Ktor body decode in `KinoPubApiClient.apiCall` (`:1026`) stays on the caller's Main context: Details `getItem` from `viewModelScope`, watch-state sync from `MainVM` (readiness-1 fix 1) | 3 | 2 | 6 | `KinoPubApiClientDecodeDispatcherTest` (AC-10) |
| Decoded heap exceeds the 16 MB char cap (design Risks 1) | 2 | 2 | 4 | AC-10 heap metric |
| `invalidateQuery` before any payload feed exists leaves the row (design Risks 6) | 2 | 2 | 4 | P1 fresh-instance test |
| `clearAllPending` deferral not wired in DI (default `{}` clears at construction) or `markLocally` overtakes it | 2 | 2 | 4 | AC-8 plus P1 module test |
| Weigher fed the wrong length (value vs encoded), cap never reached or reached early | 2 | 2 | 4 | AC-5 |
| Baseline profile names removed methods (design Risks 9) | 1 | 1 | 1 | Accepted: stale rules are ignored at runtime |

## Traceability

`CCR` = `data/cache/ContentCacheRepositoryTest.kt`; `WSR` = `data/repository/WatchStateRepositoryTest.kt`; `DAO` = `app/src/androidTest/kotlin/com/kino/puber/data/db/WatchStateDaoTest.kt`; `KDD` = `data/api/KinoPubApiClientDecodeDispatcherTest.kt`. Rows marked `manual` are carried out under Manual checks or Perf track, not as automated tests. Record decodes are counted as rows the fake store returns from `read`/`readAll` (design Q1); memory hits are proved by instance identity (`assertSame`), since the base re-decodes a string on every hit.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | CCR `aListServedFromTheMemoryTierReturnsTheSameDecodedCards` | Two `observeItems` of one fresh key emit the same list and card instances, store read count unchanged; fails if memory holds strings or the emit re-decodes |
| AC-1 | unit | P0 red | CCR `detailsServedFromTheMemoryTierAreNotDecodedAgain` | Second `observeItemDetails` emits the same `Item` instance with no store row read; fails on any decode per emit |
| AC-1 | unit | P0 red | CCR `aListOnlyInRoomReadsEachRowExactlyOnce` | Fresh repository over a populated store: list row and each card row returned once by `FakePayloadStore`; fails if `queryNeedsRefetch` keeps `peek` (list row read twice) |
| AC-1 | unit | P0 red | CCR `aPayloadInTheMemoryTierIsNotDecodedAgain` | Counting `KSerializer` sees one `deserialize` across two `getPayload` calls after a Room read, zero after a load; fails if the payload feed stores strings |
| AC-1 | unit | P1 guard | CCR `aStaleListRefetchedAfterARoomReadStillRevalidates` | A Room-only list past its TTL emits the stored value then the loader's; fails if the `peekAll` promotion pins it as fresh |
| AC-2 | unit | P0 red | CCR `flowsDecodeAndLoadOnTheInjectedDispatcher` | `decodeDispatcher` = single-thread executor named `decode`; collector on Main (`MainDispatcherExtension` with `StandardTestDispatcher`); thread name asserted in counting serializer, fake loader and `FakePayloadStore.onRead` for `observeItems`, `observeItemPage`, `historyFirstPage`, `observeItemDetails`, `observePayload`; fails if any `flowOn` is removed |
| AC-2 | unit | P0 red | CCR `getPayloadAndOneShotWritesRunOnTheInjectedDispatcher` | Same assertion for `getPayload` loader and decode, and `onRead` during `mergeItems`, `markItemDetailsStale`, `invalidateItemDetails`; fails if a `withContext` is removed |
| AC-3 | unit | P0 red | CCR `storedRecordsArePlainJsonUnderTheV2Namespace` | Raw `store.read` rows for an item, a list, a page, a history page and a payload decode with `Item.serializer()` / the payload serializer and keys start with `content:v2:`; fails on string-in-string or the v1 prefix |
| AC-4 | unit | P0 red | CCR `v1RowsAreIgnoredAndRefetchedIntoV2` | Fresh v1-form rows (`content:v1:` keys, string-literal values) for item, query and payload: loader called, loader values emitted, v2 rows written, v1 rows untouched, no exception; fails if the prefix stays v1 (base serves the v1 row without calling the loader) |
| AC-4 | device | manual | Manual check 1 | Install-preserve upgrade over the master APK opens Home and Details without a crash |
| AC-5 | unit | P0 red | `data/cache/CachedFeedMemoryWeightTest.kt` `aValuePastTheWeightCapEvictsTheOldestFromMemory` | `CachedFeed` with `maximumMemoryWeight` = N and `memoryWeigher = { it }`; puts whose encoded lengths sum past N: next `load` of the oldest reads the store again, the newest does not; fails if the weigher gets anything but the encoded length |
| AC-5 | unit | P1 red | CCR `itemTierHoldsSixteenMegabytesOfEncodedJson` | Items with ~1 M-char plots: 12 stay in memory (`assertSame`, no store read), the 17th put evicts the first; fails on the base weigher (`length * 2` evicts at 8 M chars) or a missing cap |
| AC-6 | unit | P0 guard | CCR `clearDropsEveryItemFromMemory`, `clearDropsEveryListFromMemory`, `clearDropsEveryPageFromMemory`, `clearDropsEveryHistoryPageFromMemory`, `clearDropsEveryPayloadFromMemory` | After `clear()` each feed calls its loader and emits the new value, never the pre-wipe one; fails if a feed skips the generation check |
| AC-7 | instrumented | P0 red | DAO `aServerRowEqualToTheStoredOneIsNotRewritten` | Equal row (nullable columns both null and both set) through `upsertAllFromServer`: `observeAll` emits no second value within 1 s (real-time `withTimeoutOrNull` on `Dispatchers.Default`); fails on the base UPDATE (`>=` rewrites) |
| AC-7 | instrumented | P0 guard | DAO `aServerRowDifferingInAnyWrittenColumnLands` | One case per column (`is_series_like`, `is_fully_watched`, `watched_episodes`, `total_episodes`, `progress_time`, `progress_duration`, `updated_at`, `generation`), nullable columns null-to-value and value-to-null: `get` returns the incoming row; fails if any column is compared with `=` or left out |
| AC-7 | instrumented | P1 guard | DAO `aServerRowReadBeforeTheStoredOneDoesNotLand`, `aPendingRowIsLeftAloneByASync` (existing) | `>= updated_at` and pending guards unchanged |
| AC-8 | unit | P0 red | WSR `clearAllPending_waitsForStartupToSettle` | `awaitStartupSettled` = `CompletableDeferred::await`; pending row survives `advanceUntilIdle`; after `complete`, flag cleared and fake DAO counted one `clearAllPending`; fails if the clear runs at construction |
| AC-8 | unit | P0 red | WSR `markLocally_completesOnlyAfterTheDeferredCleanup` | `markLocally` launched before settle is not complete after `advanceUntilIdle`; completes after `complete` and its row stays pending; fails if `markLocally` stops awaiting `inheritedFlagsCleared` |
| AC-8 | unit | P0 guard | WSR `markLocally_isNotStrippedByTheCleanupQueuedAtStartup` (existing) | Default `awaitStartupSettled` keeps the old ordering |
| AC-8 | unit | P1 red | `data/di/WatchStateRepositoryModuleTest.kt` `repositoryFromTheModuleDefersCleanupUntilStartupSettles` | Koin module with fake DAO and `unsettledStartup(scope)`: no `clearAllPending` until `onActivityCreated`; fails if `modules.kt:125` does not pass `StartupSettled::await` |
| AC-9 | unit | P0 guard | `CachedFeedTest`, `ContentCacheRepositoryTest`, `WatchStateRepositoryTest`, `HomeInteractorCacheTest`, `DetailsVMCachedLoadTest`, `ItemDetailsRepositoryTest`, `HomeVMIncrementalPublishTest` | Existing contracts; only `aPayloadWrittenByAnIncompatibleBuildIsDroppedAndFetchedAgain` changes its comment and `firstUseLeavesLegacyRowsToTheMigration` its probe; `check.full` green. A single HomeVM red is re-run once (`unit-suite-flaky`) |
| AC-9 | unit | P1 guard | CCR `invalidateQueryOnAFreshRepositoryRemovesThePayloadRow` | New instance, no payload feed yet: row gone and next `getPayload` calls the loader; fails without the `store.remove` fallback |
| AC-10 | unit | P0 red | KDD `responseBodyIsDecodedOnTheInjectedDispatcher` | Client built as in `KinoPubApiClientFailureTest` (local `HttpServer` via `mainApiBaseUrl`) with the injected decode dispatcher = recording wrapper over a single-thread executor named `decode`; a content call through `apiCall` (`getItemDetails`) from `runTest` returns the server's value and the wrapper counted at least one dispatch; fails if the decode in `apiCall` stays in the caller's context (base: zero dispatches) |
| AC-10 | device | manual | Perf track rows 1-2 | Zero Main-thread decode episodes in the measured sequence, including `handleApiResponse` (`:1173`, OAuth host fixed by `KinoPubConfig.OAUTH_BASE_URL`, not reachable from a local server) |

## Failing-first tests

New behaviour, not a bug: each P0 `red` row is run on the unchanged code first. Rows that need the new constructor parameters (`decodeDispatcher` of `ContentCacheRepository` and `KinoPubApiClient`, `awaitStartupSettled`, the encoded-length weigher) cannot compile on base; the implementer adds the parameter with base behaviour first (no `flowOn`, clear at construction), runs the test red, then implements.

## Manual checks

Emulator, lease and install per `AGENTS.md` (Runtime); qa-device records results in the evidence file.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| 1 | Install the master APK (e9c383d0 build), open Home, open three Details (D-pad `DPAD_CENTER`, `BACK`); install the worktree dev APK with `tools/android-apk-install-preserve`; cold start; Home; open the same Details | Home rows and Details render, logged in, no new login | Crash, empty Home, Details spinner with no error, `SerializationException` in the app's logcat lines |
| 2 | On the upgraded app, mark an episode watched in Details, `BACK` to Home within 5 s of a cold start | Watched mark shows on the card and survives the next sync | Mark reverted, `markLocally` hang (toggle does nothing) |
| 3 | Open the same Details five times (`DPAD_CENTER`/`BACK`) | Watched badges unchanged; Home does not re-publish on each return | Focus loss on return, stale watched state |

## Perf track

| Metric | Method | Baseline | Pass |
|---|---|---|---|
| JSON decodes per sequence (cold start to Home, then five Details opens) | Debug counter or method trace on `decodeFromString`/`KSerializer.deserialize`, same emulator, dev debug build | `baseline.md` (qa-device, in progress) | Fewer than baseline |
| Main-thread time in JSON decode, same sequence | Method trace (`am profile` / Perfetto), sum of decode frames on `main` | `baseline.md` | 0 ms on Main (brief AC-10) |
| App Java heap after the sequence | `dumpsys meminfo <package of the installed dev APK>` Java Heap, median of 3 runs | `baseline.md` | Recorded; growth over 25 % opens the follow-up of design Q2, not a block |
