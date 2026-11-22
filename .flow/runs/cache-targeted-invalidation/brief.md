# Brief: cache-targeted-invalidation

Base 40762767. Analyst, 2026-11-22. Builds on `research.md`; the lines I rely on were re-read at base and hold: `PlayerInteractor.kt:273-279`, `PlayerVM.kt:1484-1491,1878-1918,1952-1956,1985-2018`, `ContentCacheRepository.kt:293-300`, `CachedFeed.kt:95-131,143-176,409-442`, `TvHomeSyncCoordinator.kt:21-27`. `markStale` has one production caller (`PlayerInteractor.kt:278`, `rg markStale app/src/main`).

kind: refactor. size: M. tracks: perf. Device verification: **yes** (playback, ViewModel state, persistence and caches, launcher publishing).

Size reason: one goal (invalidate at the moments that matter, not per save) across player VM, domain, cache race code and the TV home coordinator; caches and playback are risk areas in `.flow/roles/analyst.md`.

## Request
research.md: Request. Parent seed: `.flow/runs/cache-layer-rework/brief.md` Split row 4, AC-7, AC-8, AC-9. Absorbs `cached-feed-key-maps` (PERF-4, `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md:111`).

## Current state
research.md: Current state in code (Progress save and markStale, TV home refresh, CachedFeed epoch maps, Tests).

## Scope
Run option A (research.md: Options). Paths under `app/src/main/java/com/kino/puber/`.

| Item | Change | Where |
|---|---|---|
| M1 save | `saveWatchingTime` writes the API only; a separate interactor call marks the item's details stale | `domain/interactor/player/PlayerInteractor.kt:273-279` |
| M1 mark points | `PlayerVM` keeps a "saved since last mark" flag set by a successful drain; at `exitPlayer` (inside `closeJob`, after `awaitPendingMutations`, before `router.back`) and at `pauseForBackground` (after the queued save drains) it marks `params.itemId` stale once and clears the flag | `ui/feature/player/vm/PlayerVM.kt:1484-1491,1878-1918,1985-2003` |
| T1 | `markContentChanged` records the change and a "home refresh owed" flag instead of calling `requestRefresh`; the same two points issue one `requestRefresh()` when the flag is set. Coordinator, `DetailsVM`, `HistoryVM`, `MainVM` unchanged | `PlayerVM.kt:1952-1956` |
| E1 | The four per-key counters become one per-key record with a holder count; every operation that captures an `Epoch` acquires the record and releases it when done; the record is removed atomically (`compute`) at count zero. `namespaceEpoch` and `store.generation` unchanged | `data/cache/CachedFeed.kt:95-131,143-166,409-442` and the capture sites listed in research.md (CachedFeed epoch maps) |

## Acceptance criteria
Unit tests under `app/src/test/kotlin/com/kino/puber/` unless named otherwise.

- AC-1. Given a successful `saveWatchingTime`, When it returns, Then `ItemDetailsRepository.markStale` was not called (`PlayerInteractorTest.kt:650-690` rewritten to the new contract).
- AC-2. Given playback in front with several 30 s progress saves, a `prefetchNextEpisode` and a next-episode switch, When the player stays in front, Then details are marked stale zero times and `TvHomeSyncCoordinator.requestRefresh` is called zero times (PlayerVM test with a fake interactor and a mock coordinator).
- AC-3. Given at least one successful save since the last mark, When the player exits, Then the item is marked stale exactly once and `requestRefresh` is called exactly once, both before `router.back`, and the returned change set still contains `PlaybackProgress`; Given no successful save (all failed or none queued), Then neither happens (PlayerVM test).
- AC-4. Given at least one successful save, When `PlayerAction.OnBackground` arrives, Then after the background save drains the item is marked stale once and `requestRefresh` is called once; a following exit with no new successful save marks and refreshes nothing (PlayerVM test).
- AC-5. Given a `Watched` change during playback (auto or manual), When the player is in front, Then no `requestRefresh` is issued; the change makes exit or background issue one (PlayerVM test).
- AC-6. Given a title played from Home (no Details below) and the player exited after a progress save, When that title's Details opens within 10 min, Then it shows the saved progress (emulator observation, D-pad driven).
- AC-7. Given a feed that ran loads, puts, `putAll`, invalidations, `markStale` and peeks on many distinct keys to completion, When all have settled, Then the feed holds no per-key record; While an operation on a key is in flight, Then that key's record exists (`CachedFeedTest` through an `internal` size accessor).
- AC-8. Given concurrent load, put, invalidate and namespace drop on one key, When a record's holder count reaches zero and a new operation starts, Then the superseded-write outcomes are the same as today (new `CachedFeedTest` cases for record removal and recreation under contention); existing `CachedFeedTest` assertions unchanged.
- AC-9. `ContentCacheRepositoryTest`, `ItemDetailsRepositoryTest`, `DetailsVMCachedLoadTest`, `TvHomeSyncCoordinatorTest`, `PlayerVMPlaybackFlowTest`, `PlayerVMMutationAndResultTest` stay green with assertions changed only where AC-1 changes the contract; `check.full` passes.
- AC-10 (perf). On the emulator, before and after, 30 min of episode playback with one next-episode switch, then exit: count of `markItemDetailsStale`, `TvHomeSyncCoordinator.refreshNow` and details network loads, and the time from Back to the screen below (temporary uncommitted log, as in `startup-settled-signal`). After: zero of each while in front, one mark and one refresh at exit. Recorded in evidence.

## Risks
research.md: Risks holds the table; rows 1-5 are guarded by AC-4/AC-6, AC-10, AC-3, AC-8 and AC-7/AC-8 here. Additions:

| Risk | Guard |
|---|---|
| `PlayerVM` cleared without `exitPlayer` (process kill in front, a navigation that replaces the stack) skips the mark; a Details open from another screen shows pre-session progress up to the 10 min TTL | Background mark covers Home/app switch; accepted residue, Q1 |
| The background mark runs before the background save lands and marks nothing | AC-4 requires the mark after the drain |
| `player-vm-split` and `resume-progress-anomaly` edit the same save path | This run merges first; never in parallel with another `CachedFeed.kt` run |

## Open questions
1. When does progress reach later Details opens? Recommended: mark stale once at player exit and once at background, each only if a save succeeded since the last mark (M1); a process kill in front falls back to the 10 min TTL. Blocks: yes (AC-3, AC-4, AC-6).
2. TV home refresh for `Watched` during playback: defer it with progress to exit and background? Recommended: yes, the launcher row is invisible while the player is in front (T1). Blocks: yes (AC-5).
3. Keep the epoch-map bound (E1) in this run rather than splitting it out? Recommended: yes (run option A); evidence for that part is unit-level only. Blocks: yes (AC-7, AC-8).
