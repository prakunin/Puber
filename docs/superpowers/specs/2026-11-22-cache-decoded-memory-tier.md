# Typed values decoded once in the memory tier, decode off Main, plain JSON under content:v2, no-op watch_state writes skipped

Status: approved
Date: 2026-11-22
Slug: cache-decoded-memory-tier
Kind: refactor
Size: M
Tracks: perf

## Problem / Why

### Request
research.md: Request. Parent seed: `.flow/runs/cache-layer-rework/brief.md` Split row 3, AC-5, AC-6, AC-8, AC-9.

### Current state
research.md: Current state in code (Representation and decode count, Thread, watch_state, Tests).

### Scope
Paths under `app/src/main/java/com/kino/puber/`.

| Item | Change | Where |
|---|---|---|
| Typed tier | Option A (research.md: Options): one `CachedFeed<V>` per record type (item, list/page, history, each payload type) on the one store; memory holds decoded values; weigher = encoded JSON byte length carried with the entry | `data/cache/ContentCacheRepository.kt:32-43`, `data/cache/CachedFeed.kt` (`Stamped` weight only) |
| Storage form | Room value is the record's plain JSON; namespace `content:v1:` -> `content:v2:`; `discardUndecodablePayload` stays only if a typed decode can still fail | `ContentCacheRepository.kt:268-281,423-427,586` |
| Decode off Main | Injected dispatcher (Default) via `flowOn` on public flows and `withContext` on one-shot reads and `getPayload`, covering the loader's `body<T>()` | `ContentCacheRepository.kt` public API; DI in `data/di/modules.kt` |
| watch_state no-op write | Server UPDATE skips a row whose every written column, `updated_at` and `generation` included, already equals the incoming value (`IS NOT` for nullable columns). Semantics of `>= updated_at` unchanged, so research.md Open question 2 does not arise | `data/db/WatchStateDao.kt:129-156` |
| clearAllPending | Runs after `StartupSettled.await()` instead of at construction; `markLocally` keeps waiting for it (Q2) | `data/repository/WatchStateRepository.kt:121-135` |

### Risks (analyst)
research.md: Risks holds the table; rows 1, 3, 5, 6 and 7 are guarded by AC-9, AC-6, AC-10, AC-4 and AC-9 here. Additions:

| Risk | Guard |
|---|---|
| `flowOn` adds a buffer: a Home section can emit a value the VM would have conflated before, shifting publish coalescing (e6b42269) | AC-9 (`HomeVMIncrementalPublishTest`); the `unit-suite-flaky` run lists HomeVM flakes, so a single red run is re-run before it is read as a regression |
| Deferring `clearAllPending` keeps inherited pending flags up to the settle point (fallback 5 s, `StartupSettled.kt:28`); server writes in the warm-up skip those rows until then | The WatchState sync slot (`MainVM.kt:92`) runs after settle, so the next sync corrects them; AC-8 |
| A no-op predicate that misses a column (nullable compared with `=`) silently drops real updates | AC-7 differing-column case covers each nullable column |
| B4 (`cache-targeted-invalidation`) edits `CachedFeed.kt:95-131,406-438` | Never run in parallel with this run |

## Acceptance criteria

Unit tests under `app/src/test/kotlin/com/kino/puber/` unless named otherwise.

- AC-1. Given a Home list load or a Details open whose records are in the memory tier, When it completes, Then no record is decoded; Given the same records only in Room, Then each record (list and every card) is decoded exactly once (counting serializer in `ContentCacheRepositoryTest`).
- AC-2. Given `ContentCacheRepository` flows and `getPayload` called from a Main-confined test dispatcher, When a decode or the loader runs, Then it runs on the injected dispatcher, never on the collector's (assertion inside the counting serializer and the fake loader).
- AC-3. Given a record written by the new build, When the Room row is read raw, Then its value decodes directly with the record's serializer (plain JSON, not a JSON string literal) and its key starts with `content:v2:`.
- AC-4. Given `content:v1:` rows in the store, When the new build reads the same items, queries and payloads, Then it ignores them without a crash, fetches from the API and writes v2 rows (unit test); and on the emulator an install-preserve upgrade over the current master APK opens Home and a Details screen without a crash.
- AC-5. Given decoded values whose summed encoded JSON length exceeds 16 MB, When another is put, Then the tier evicts down to the cap (weigher test on `CachedFeed` or the repository).
- AC-6. Given every typed feed holds values, When `clear()` runs (logout or domain switch, `store.generation` bump), Then no feed serves a value from before (one test per record type).
- AC-7. Given a `watch_state` row equal to an incoming server row in every written column, When `upsertAllFromServer` writes it, Then the row is not updated and `observeAll` emits no new value; Given a row differing in any column or a newer `updatedAt`, Then it is written as today (`app/src/androidTest/kotlin/com/kino/puber/data/db/WatchStateDaoTest.kt`, run with `make DEVICE=<serial> itest TESTS=com.kino.puber.data.db.WatchStateDaoTest`).
- AC-8. Given a repository constructed before startup settles, When `StartupSettled` completes, Then `clearAllPending` runs once, and a `markLocally` called earlier completes only after it (`WatchStateRepositoryTest` with a fake latch).
- AC-9. `CachedFeedTest` passes with assertions unchanged; `ContentCacheRepositoryTest`, `WatchStateRepositoryTest`, `HomeInteractorCacheTest`, `DetailsVMCachedLoadTest`, `ItemDetailsRepositoryTest`, `HomeVMIncrementalPublishTest` stay green with assertions changed only where the storage form changes (`ContentCacheRepositoryTest.kt:237-266`); `check.full` passes.
- AC-10 (perf). On the emulator, before and after, for a cold start to Home and for five Details opens: JSON decode count and Main-thread time spent in decode (method trace or a debug counter), plus app Java heap after the sequence. Recorded in evidence; decode on Main must be zero after.

## Out of scope

The incremental watch_state snapshot (research.md: Options, C) is not in scope. startup-main-thread-io keeps its prefs, StrictMode and largeHeap items; this run takes only its deferred `clearAllPending`. A smaller item tier cap, if AC-10 shows heap growth, is a follow-up run (design Q2).

## Design

Base e9c383d0. Paths under `app/src/main/java/com/kino/puber/` unless named otherwise. Inputs: `brief.md`, `research.md`, `state.md` Decisions (Q1 A, Q2 yes, Q3 sweeper).

### Decisions

| Question | Decision | Why |
|---|---|---|
| Feed set (Q1 option A) | `ContentCacheRepository` holds: one `CachedFeed<ItemRecord>`; three query feeds `CachedFeed<ItemListRecord>`, `<ItemPageRecord>`, `<HistoryPageRecord>`; payload feeds created lazily, one per payload store key, in a `ConcurrentHashMap<String, CachedFeed<*>>` | Each key has exactly one type; a per-key payload feed needs no serializer-equality reasoning, and payload keys are few (`genres:*`, `bookmark-folders`, `collections:1`, `home:collections`) |
| Prefix | `CONTENT_PREFIX = "content:v2:"` (`ContentCacheRepository.kt:586`); key builders `:591-593` unchanged otherwise | AC-3, AC-4; v1 rows never read, left to the sweeper (Q3) |
| Weight carried with the entry | `CachedFeed` encodes once per write and once per Room read already; `Stamped` and `Usable` gain `encodedLength: Int` (the JSON string's `length`). Constructor param `memoryWeigher: (V) -> Int` becomes `memoryWeigher: (encodedLength: Int) -> Int = { 1 }` | No re-encode to weigh; `CachedFeedTest` never passes a weigher (`rg memoryWeigher app/src/test`: no hit), so its assertions stay unchanged (AC-9) |
| Weigher and cap | Every content feed: `memoryWeigher = { it }`, `maximumMemoryWeight = MEMORY_TIER_BYTES` (16 MB, `:589`), default count cap 1024 | AC-5. Only the item feed holds large values (details trees) |
| Encode in `load` | Move `json.encodeToString(serializer, value)` (`CachedFeed.kt:210`) above the epoch check (`:206-207`), so a discarded flight also knows its weight | One encode per flight, as today in the common path |
| Query-row read before a load | `queryNeedsRefetch` (`ContentCacheRepository.kt:408-421`) reads the query row with `peekAll(listOf(key))` instead of `peek` (`:413`) | `peek` does not promote (`CachedFeed.kt:287-293`), so the following `load` would decode the row a second time; `peekAll` promotes pinned to `HardCeiling`, which `readCached` (`:505-512`) and `loadFresh` (`:486-499`) already judge by the stamp, the same path card rows take today. Gives AC-1's exactly-once without touching `CachedFeed` logic |
| Undecodable rows | Delete `discardUndecodablePayload` (`:423-427`), `decodeOrDiscard` (`:430-437`), `decode` (`:529`), `encode` (`:527`) and the `runCatching` around record decodes (`:414-417`, `:449-451`) | With typed feeds `CachedFeed.decodeUsable` (`CachedFeed.kt:559-574`) is the only decode site and already drops an undecodable row |
| Decode dispatcher | Constructor param `decodeDispatcher: CoroutineDispatcher = Dispatchers.Default`; `.flowOn(decodeDispatcher)` on `observeItems`, `observeItemPage`, `historyFirstPage`, `observeItemDetails`, `observePayload`; `withContext(decodeDispatcher)` around public `mergeItems` (`:309-320`), `markItemDetailsStale` (`:328-335`), `invalidateItemDetails` (`:337-344`) | `sectionPage`, `watchlist`, `observeSimilarItems` delegate to a flowOn'd flow; `getPayload` (`:291-307`) collects `observePayload`, so the loader and its `body<T>()` (`KinoPubApiClient.kt:1017-1035`) run on the dispatcher (AC-2). Precedent: `HomeVM.kt:62` |
| DI for the dispatcher | `data/di/modules.kt:97` unchanged (default is Default) | Smallest change; tests inject |
| `invalidateQuery` (`:346-349`) | `invalidate(queryKey(key))` on each of the three query feeds; payload: `payloadFeeds[payloadKey(key)]?.invalidate(...) ?: store.remove(payloadKey(key))` | A key's owner feed is unknown at this call; a non-owner's `invalidate` only bumps its own counters and repeats a no-op delete |
| `clear()` (`:351-353`) | Unchanged (`store.clear()`) | Every feed drops its tier on a generation move (`CachedFeed.kt:467-473`, `:545-550`); AC-6 tests each feed |
| watch_state no-op predicate | Add to `upsertFromServer`'s WHERE (`WatchStateDao.kt:140-142`): `AND (is_series_like IS NOT :isSeriesLike OR is_fully_watched IS NOT :isFullyWatched OR watched_episodes IS NOT :watchedEpisodes OR total_episodes IS NOT :totalEpisodes OR progress_time IS NOT :progressTime OR progress_duration IS NOT :progressDuration OR updated_at IS NOT :updatedAt OR generation IS NOT :generation)` | `IS NOT` on every column, nullable or not, so no comparison can yield NULL; supported by the older SQLite on Fire OS. `upsertFromHistory` and `updateInProgress` unchanged (brief) |
| `clearAllPending` deferral (Q2) | `WatchStateRepository` gains `awaitStartupSettled: suspend () -> Unit = {}`; the coroutine at `WatchStateRepository.kt:125-134` calls it before `dao.clearAllPending()`, inside the same `try`/`finally`. DI `data/di/modules.kt:125` passes `get<StartupSettled>()::await` | Same wiring as `DetailsPrefetcher` (`domain/di/modules.kt:82`); `markLocally` keeps awaiting `inheritedFlagsCleared` (`:304`); the default keeps `WatchStateRepositoryTest.kt:51` construction valid |

### Changes by layer

#### API / repository

| File | Change |
|---|---|
| `data/cache/CachedFeed.kt` | `:54` weigher signature; `:72` `weigher = { stamped -> memoryWeigher(stamped.encodedLength) }`; `:203-232` encode before the epoch check, write that payload, `Stamped(encodedLength = payload.length)`; `:358-372` `putAll` encodes each value once into a map, writes it, and stamps the memory entry with its length; `:573` `Usable(encodedLength = stored.payload.length)`; `:532` and `:540` carry `encodedLength` through `promoteToMemory`; `:576`, `:579-584` add the field. No change to epochs, barriers or `load` control flow |
| `data/cache/ContentCacheRepository.kt` | `:32-43` replaced by the feed set and `payloadFeed(key, serializer)` (`computeIfAbsent`, unchecked cast); every feed gets `json = json` (today `:32` does not pass `:29`); `:56-65`, `:92-101`, `:156-176`, `:216-226`, `:273-277` load typed records and loaders return records, not strings; emits read the typed `cached.value` (`:69`, `:105`, `:180`, `:230`, `:281`); `:272` call removed; `:330-342` `put` the copied record; `:365-377` `putAll` of `ItemRecord`s; `:443-454` `readItemRecords` maps `peekAll` values directly; `:586` v2; flowOn/withContext per Decisions |
| `data/db/WatchStateDao.kt` | `:140-142` predicate per Decisions; KDoc on the class (`:18-29`) gains the no-op rule |
| `data/repository/WatchStateRepository.kt` | `:41-47` constructor param; `:125-134` await before clearing; KDoc `:114-118` names the settle point |
| `data/di/modules.kt` | `:125` passes `awaitStartupSettled` |

#### ViewModel state and actions

N/A: VMs keep collecting the same flows; only the upstream dispatcher changes.

#### Screen and DI module, Compose content, resources

N/A: no screen, composable or resource change.

#### Tests (owned by test-design; seams this design provides)

| Seam | Use |
|---|---|
| `ContentCacheRepository(decodeDispatcher = ...)` | AC-2 with a named `StandardTestDispatcher(testScheduler)` distinct from the collector's; fixtures whose loaders use virtual time or that assert after `advanceUntilIdle` pass a dispatcher on the test scheduler |
| `CachedFeed.decodeUsable` is the only decode site | Record decodes equal rows `FakePayloadStore` returns from `read`/`readAll` (`app/src/test/kotlin/com/kino/puber/util/FakePayloadStore.kt`); payload decodes via a counting `KSerializer` passed to `getPayload` |
| `WatchStateRepository(awaitStartupSettled = ...)` | AC-8 fake latch (`CompletableDeferred`) |
| `ContentCacheRepositoryTest.kt:236-247` | Still passes: `"\"not a genre list\""` fails the typed decode and is dropped; its comment changes |

### Alternatives rejected

- Option B, one `CachedFeed<Any>` with per-call serializers: changes every `CachedFeedTest` call (owner chose A).
- Payload feeds keyed by `serializer.descriptor`: relies on descriptor equality across `ListSerializer` instances; per-key is simpler.
- `load(refetchIf = ...)` inside `CachedFeed` for the incomplete-list check: edits `load` control flow that B4 and `CachedFeedTest` guard; `peekAll` achieves the same.
- Making `peek` promote: changes a documented `CachedFeed` contract (`:287-293`) under test at `CachedFeedTest.kt:46-135`.
- Weighing by re-encoding the decoded value: an extra encode per memory put.
- Moving decode off Main in HomeVM/DetailsVM collectors: seven call sites instead of one boundary.

### Risks

| Risk | Kind | Lives in | Guard |
|---|---|---|---|
| Decoded objects cost more heap than their JSON length; a 16 MB (chars) item tier can hold several times that, where the string tier held 16 MB of heap (`length * 2`, `:42`) | memory growth | `ContentCacheRepository.kt:41-42` | AC-10 heap after the sequence; open question 2 |
| `flowOn` buffers emissions and runs the loader on Default: tests that relied on Main-confined virtual time see work finish on real threads, and Home publish coalescing sees values it used to conflate (e6b42269) | concurrency | public flows of `ContentCacheRepository`; `HomeVM.kt:331-391` | AC-9; fixtures inject a test-scheduler dispatcher; `unit-suite-flaky` HomeVM flakes are re-run once before being read as a regression |
| Query row promoted by `peekAll` answers a load that should revalidate | cache invalidation | `ContentCacheRepository.kt:413`; `CachedFeed.kt:486-499`, `:505-512` | Same stamp checks already serve card rows; AC-1 stale-list case and existing `ContentCacheRepositoryTest` refetch tests |
| A no-op predicate that misses a column drops real server updates | correctness | `WatchStateDao.kt:129-156` | `IS NOT` per column; AC-7 differing-column case per column |
| A typed feed misses a wipe and serves the previous account | security / lifecycle | `CachedFeed.kt:83`, `:467-473` | AC-6, one test per record type including a payload feed |
| `invalidateQuery` before any payload feed exists must still delete the row | cache invalidation | `ContentCacheRepository.kt:346-349` | `store.remove` fallback; test on a fresh repository instance |
| Pending flags inherited from a dead process stay until settle (fallback 5 s, `StartupSettled.kt:28`); a process with no activity never settles, and `markLocally` would wait forever there | lifecycle | `WatchStateRepository.kt:125-134`, `:304`; `MainActivity.kt:79` | No worker or receiver builds the repository (`rg` for Worker/Receiver: none); `markLocally` is reached only from UI; AC-8 |
| Upgrade over an installed v1 build | rollout | `ContentCacheRepository.kt:586`; `CachedPayloadSweeper.kt:65-75` | AC-4 unit and device install-preserve |
| Baseline profile names `ContentCacheRepository` methods that change shape | performance | `app/src/main/generated/baselineProfiles/` | Profile rules on removed methods are ignored; regeneration not required for correctness |

### Open questions

1. AC-1 and AC-2 name a "counting serializer" for records, but the record serializers are private to `ContentCacheRepository` and `Json` cannot be wrapped. Accept counting rows returned by `FakePayloadStore` (each decoded once by `CachedFeed.decodeUsable`) for records, the counting serializer for payloads, and the dispatcher assertion in the fake store's `onRead` and the fake loader? Recommended: yes. Blocks: no.
2. If AC-10 shows heap growth over the current build, lower the item tier's cap (for example 8 MB) in a follow-up rather than in this run? Recommended: yes, follow-up. Blocks: no.

## Test design

Base e9c383d0. Inputs: `brief.md` (ACs), `design-architect.md` (Decisions, Risks), `state.md` Decisions. Test paths under `app/src/test/kotlin/com/kino/puber/` unless named; levels per `.flow/roles/test-designer.md`.

### Risk profile

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

### Traceability

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

### Failing-first tests

New behaviour, not a bug: each P0 `red` row is run on the unchanged code first. Rows that need the new constructor parameters (`decodeDispatcher` of `ContentCacheRepository` and `KinoPubApiClient`, `awaitStartupSettled`, the encoded-length weigher) cannot compile on base; the implementer adds the parameter with base behaviour first (no `flowOn`, clear at construction), runs the test red, then implements.

### Manual checks

Emulator, lease and install per `AGENTS.md` (Runtime); qa-device records results in the evidence file.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| 1 | Install the master APK (e9c383d0 build), open Home, open three Details (D-pad `DPAD_CENTER`, `BACK`); install the worktree dev APK with `tools/android-apk-install-preserve`; cold start; Home; open the same Details | Home rows and Details render, logged in, no new login | Crash, empty Home, Details spinner with no error, `SerializationException` in the app's logcat lines |
| 2 | On the upgraded app, mark an episode watched in Details, `BACK` to Home within 5 s of a cold start | Watched mark shows on the card and survives the next sync | Mark reverted, `markLocally` hang (toggle does nothing) |
| 3 | Open the same Details five times (`DPAD_CENTER`/`BACK`) | Watched badges unchanged; Home does not re-publish on each return | Focus loss on return, stale watched state |

### Perf track

| Metric | Method | Baseline | Pass |
|---|---|---|---|
| JSON decodes per sequence (cold start to Home, then five Details opens) | Debug counter or method trace on `decodeFromString`/`KSerializer.deserialize`, same emulator, dev debug build | `baseline.md` (qa-device, in progress) | Fewer than baseline |
| Main-thread time in JSON decode, same sequence | Method trace (`am profile` / Perfetto), sum of decode frames on `main` | `baseline.md` | 0 ms on Main (brief AC-10) |
| App Java heap after the sequence | `dumpsys meminfo <package of the installed dev APK>` Java Heap, median of 3 runs | `baseline.md` | Recorded; growth over 25 % opens the follow-up of design Q2, not a block |

### Baseline (qa-device, unchanged build)

Build: worktree at base e9c383d0, `./tools/agentw :app:assembleDevDebug` (AGENT_GRADLE_SERIAL=1), dev debug, version 1.12.3-dev. APK sha256 6cec249b...8c39b16, matched on device after install-preserve (outcome `installed`, `compatible_replace`, signer unchanged).
Device: emulator-5554 (AVD puber_tv_36, API 36 TV, booted without wipe, software GPU), lease held and released. On-device Room user_version before install: 4 (header read), equal to the build's `PuberDatabase` version 4.
Account: existing pairing; no data touched, no uninstall or clear.

### Method (no production changes)

Sequence: force-stop, cold start (MainActivity), 15 s settle, D-pad into the "Continue watching" row, then five times: DPAD_CENTER (Details), 5 s, BACK, 3 s, DPAD_RIGHT (next card).
Decode = `kotlinx.serialization.json.Json.decodeFromString` (all record and ktor body decodes go through it; ktor `KotlinxSerializationConverter.deserialize` listed separately). ART method traces (`am start --start-profiler ... --streaming`, parsed with a script of mine from the streaming v3 format; thread 1 = main).

Limits found:
- Exact call counts need full method tracing. It was stable for the cold start only (500-670 MB trace). The same process dies when a Details screen opens under full tracing (twice), so Details counts cannot be exact.
- For the whole sequence a sampling trace (100 us requested, ~355 us median gap on Main) was used: it gives decode "episodes" (contiguous presence on the stack; back-to-back decodes merge, so a lower bound on count) and inclusive Main time. Sampling and tracing slow the app, so milliseconds are inflated and are comparable only with the same method after the change.

### Results

#### Cold start to Home, exact count (full trace, two runs)
| Run | decodeFromString total | on Main | on workers |
|---|---|---|---|
| c | 165 | 66 | 99 |
| c2 | 101 | 31 | 70 |
Run-to-run spread is large (cache vs network refresh timing); Main decode is non-zero in both.

#### Whole sequence (cold start + five Details opens), sampling, three runs
Main thread, cold window (first 15 s) and opens window (after 15 s):
| Run | cold episodes | cold ms in decode | opens episodes | opens ms in decode | worker episodes |
|---|---|---|---|---|---|
| s1 | 72 | 2620 | 213 | 9321 | 79 |
| s2 | 87 | 1763 | 197 | 9477 | 50 |
| s3 | 95 | 2698 | 240 | 10181 | 49 |
Main-thread decode inside the ktor converter is a part of these (10-15 episodes, 0.2-0.4 s per run in the opens window). Total trace length 81-86 s. After the change decode on Main must be 0 episodes in both windows (AC-10).

#### App heap after the sequence (no profiler, three runs, dumpsys meminfo)
| Run | Dalvik Heap Alloc (KB) | Java Heap PSS summary (KB) | Native Heap PSS (KB) |
|---|---|---|---|
| h1 | 26347 | 40648 | 60904 |
| h2 | 26219 | 52344 | 55076 |
| h3 | 26203 | 58728 | 52248 |
Sampling runs (profiler on): Dalvik Heap Alloc 26935, 26210, 27183 KB. Alloc is stable near 26.2-26.3 MB; the PSS figure varies with GC timing, compare Alloc and use at least three runs.

Re-measure after the change with the same sequence, the same emulator and a dev debug build.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Typed tier shape: feed per record type (A) or one feed with per-call serializers (B)? | no | answered | A |
| Q2 | Take startup-main-thread-io's clearAllPending item after StartupSettled.await()? | yes | answered | yes |
| Q3 | Remove v1 rows with Room v5 migration or leave them to the sweeper? | no | answered | sweeper |
| D1 | Count decodes via fake store rows (records) and a counting KSerializer (payloads)? | no | answered | yes |
| D2 | Heap growth in AC-10 lowers the item tier cap in a follow-up run? | no | answered | yes |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-22 | Q1 A, Q2 yes, Q3 sweeper, D1 yes, D2 yes | owner (standing instruction: recommended) |
| 2026-11-22 | codex: required; round caps lifted | owner |
| 2026-11-22 | Gate 1: approve (size M, track perf, 10 ACs) | owner (standing instruction: recommended) |
