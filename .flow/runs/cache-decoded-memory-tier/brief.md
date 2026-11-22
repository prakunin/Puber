# Brief: cache-decoded-memory-tier

Base e9c383d0. Analyst, 2026-11-22. Builds on `research.md`; the lines I rely on were re-read at base and hold: `ContentCacheRepository.kt:32-43,586-593`, `WatchStateRepository.kt:49-51,121-135,251-256,303-304`, `WatchStateDao.kt:63-80,129-156`, `ItemDetailsRepository.kt:17-28`, `StartupSettled.kt:14-58`, `MainVM.kt:88-98`. No `var` or mutable collection in `data/api/models` (`rg`), so shared decoded values are safe (research.md: Risks, row 2, now verified).

kind: refactor. size: M. tracks: perf. Device verification: **yes** (persistence and caches, networking, threading of VM collectors, startup).

Size reason: one goal (each cached value decoded once, off Main), crossing data, cache, DB and startup layers, all in risk areas of `.flow/roles/analyst.md`.

## Request
research.md: Request. Parent seed: `.flow/runs/cache-layer-rework/brief.md` Split row 3, AC-5, AC-6, AC-8, AC-9.

## Current state
research.md: Current state in code (Representation and decode count, Thread, watch_state, Tests).

## Scope
Paths under `app/src/main/java/com/kino/puber/`.

| Item | Change | Where |
|---|---|---|
| Typed tier | Option A (research.md: Options): one `CachedFeed<V>` per record type (item, list/page, history, each payload type) on the one store; memory holds decoded values; weigher = encoded JSON byte length carried with the entry | `data/cache/ContentCacheRepository.kt:32-43`, `data/cache/CachedFeed.kt` (`Stamped` weight only) |
| Storage form | Room value is the record's plain JSON; namespace `content:v1:` -> `content:v2:`; `discardUndecodablePayload` stays only if a typed decode can still fail | `ContentCacheRepository.kt:268-281,423-427,586` |
| Decode off Main | Injected dispatcher (Default) via `flowOn` on public flows and `withContext` on one-shot reads and `getPayload`, covering the loader's `body<T>()` | `ContentCacheRepository.kt` public API; DI in `data/di/modules.kt` |
| watch_state no-op write | Server UPDATE skips a row whose every written column, `updated_at` and `generation` included, already equals the incoming value (`IS NOT` for nullable columns). Semantics of `>= updated_at` unchanged, so research.md Open question 2 does not arise | `data/db/WatchStateDao.kt:129-156` |
| clearAllPending | Runs after `StartupSettled.await()` instead of at construction; `markLocally` keeps waiting for it (Q2) | `data/repository/WatchStateRepository.kt:121-135` |

The incremental watch_state snapshot (research.md: Options, C) is not in scope.

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

## Risks
research.md: Risks holds the table; rows 1, 3, 5, 6 and 7 are guarded by AC-9, AC-6, AC-10, AC-4 and AC-9 here. Additions:

| Risk | Guard |
|---|---|
| `flowOn` adds a buffer: a Home section can emit a value the VM would have conflated before, shifting publish coalescing (e6b42269) | AC-9 (`HomeVMIncrementalPublishTest`); the `unit-suite-flaky` run lists HomeVM flakes, so a single red run is re-run before it is read as a regression |
| Deferring `clearAllPending` keeps inherited pending flags up to the settle point (fallback 5 s, `StartupSettled.kt:28`); server writes in the warm-up skip those rows until then | The WatchState sync slot (`MainVM.kt:92`) runs after settle, so the next sync corrects them; AC-8 |
| A no-op predicate that misses a column (nullable compared with `=`) silently drops real updates | AC-7 differing-column case covers each nullable column |
| B4 (`cache-targeted-invalidation`) edits `CachedFeed.kt:95-131,406-438` | Never run in parallel with this run |

## Open questions
1. Typed tier shape: a feed per record type (A) or one feed with per-call serializers (B)? Recommended: A. Blocks: no.
2. Take startup-main-thread-io's WatchStateRepository item as `clearAllPending` after `StartupSettled.await()`, keeping the eager snapshot read Home needs (`HomeInteractor.kt:165`), and mark that item done in startup-main-thread-io? Recommended: yes. Blocks: yes (AC-8).
3. Remove v1 rows with a Room v5 migration or leave them to the sweeper (7 days, 2,000 rows, `CachedPayloadSweeper.kt:65-75`)? Recommended: sweeper, no schema bump. Blocks: no.
