# Research: cache-targeted-invalidation

## Request

Targeted cache invalidation: no markStale on each progress save, TV home refresh coalesced, CachedFeed epoch maps bounded.

Context from the conductor: split item B4 of `cache-layer-rework` (its `brief.md` B4 row, AC-7, AC-8, AC-9, Risks); absorbs the dropped run `cached-feed-key-maps`. Progress must still reach the next Details open (e.g. refresh once on player exit). Base master 40762767.

## Understanding

Every 30 s progress save in the player does two broad things: it rewrites the title's details record as stale (a superseding Room write), so the next `getItemDetails` - including the one that starts the next episode - waits for a network refetch; and it asks the TV home coordinator to reload the history page and republish the launcher row, which nobody can see while the player is in front. Separately, `CachedFeed` keeps per-key counters for its race guards and two of the four maps are never cleared, so they grow with every key the process ever touched. The run replaces the per-save effects with one effect at the moments that matter (player exit, player sent to background) and bounds the counter maps without weakening the race guards.

## Current state in code

Paths under `app/src/main/java/com/kino/puber/`; all re-read on 40762767. Parent research lines `PlayerInteractor.kt:273-279`, `PlayerVM.kt:1907,1952-1956` and `CachedFeed.kt:95-131` still hold; the parent's `CachedFeed.kt:408-438` is now `:409-442`.

### Progress save and markStale

| Location | What it does now |
|---|---|
| `ui/feature/player/vm/PlayerVM.kt:1878-1918` | `saveCurrentPosition` queues a save; the drain calls `interactor.saveWatchingTime` then `markContentChanged(PlaybackProgress)` (`:1907`). Called by the 30 s tracker (`:1543-1547`, `PROGRESS_SYNC_INTERVAL_MS` `:2024`), pause (`:692`), episode switch (`:1047`), background (`:1490`), exit (`:1999`). |
| `domain/interactor/player/PlayerInteractor.kt:273-279` | After the API write, `itemDetailsRepository.markStale(id)`. |
| `data/repository/ItemDetailsRepository.kt:84-86` -> `data/cache/ContentCacheRepository.kt:293-300` | Reads the item record, rewrites it with `detailsUpdatedAt = STALE_TIMESTAMP` (`0L`, `:547`) via `itemFeed.put(supersede = true)`: one decode, one encode, one Room write, and a destruction barrier that makes any in-flight details load drop its own row (`CachedFeed.kt:350-393`). |
| `data/cache/ContentCacheRepository.kt:191-201` | A record with `detailsUpdatedAt` older than `CacheTtl.ItemDetails` (10 min, `data/cache/CacheKeys.kt:51`) forces the load. |
| `data/cache/CachedFeed.kt:175-180` | A stale cached value is emitted, then the loader runs. |
| `data/repository/ItemDetailsRepository.kt:69-71,114-124` | `getItemDetails` collects the whole flow, so it returns only after the network refetch. |
| `ui/feature/player/vm/PlayerVM.kt:317-324`, `:1060-1070`, `:1040-1057` | Next episode: `playNextEpisode` -> `switchEpisode` -> `preparePlayback` -> `interactor.getItemDetails`, which therefore waits for the refetch after any save. |
| `ui/feature/player/vm/PlayerVM.kt:1656-1680` | `prefetchNextEpisode` also calls `getItemDetails` (refetch), but the next 30 s save marks the record stale again. |
| `ui/feature/player/vm/PlayerVM.kt:1985-2003` | `exitPlayer` saves, awaits pending mutations, then `router.back(RESULT_CONTENT_CHANGED, contentChanges)`. `onCleared` (`:2008-2018`) saves nothing. |
| `ui/feature/details/vm/DetailsVM.kt:552-560` | The Details screen that opened the player reloads with `force = true` on any returned change for its item, so it does not depend on `markStale`. |
| Player entry points without a Details below | `screens.player(` is also called from `HomeVM`, `HistoryVM`, `BookmarksVM`, `FavoriteVM`, `ShowAllVM`, `CollectionDetailVM`, `ContentListVM`, `SearchVM`, `core/contentlink/ContentTargetScreenMapper.kt`. A Details opened after those within 10 min relies on the stale mark today. |

### TV home refresh

| Location | What it does now |
|---|---|
| `ui/feature/player/vm/PlayerVM.kt:1952-1956` | `Watched` or `PlaybackProgress` -> `tvHomeSyncCoordinator?.requestRefresh()`. |
| `core/tvhome/TvHomeSyncCoordinator.kt:21-27,55` | Cancel-and-restart debounce of 2 s; with saves 30 s apart each save becomes one refresh. |
| `core/tvhome/TvHomeSyncCoordinator.kt:29-41`, `core/tvhome/ContinueWatchingSource.kt:15` | A refresh is `api.getHistoryData(FIRST_PAGE)` plus `publisher.reconcile` (launcher provider writes). |
| `PuberApp.kt:58` | Singleton on `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. |
| Other callers | `ui/feature/main/vm/MainVM.kt:68` (`immediate = true`, after the startup slot from `startup-settled-signal`), `ui/feature/details/vm/DetailsVM.kt:570-575` (Watched only), `ui/feature/history/vm/HistoryVM.kt:588`. |
| `ui/feature/player/vm/PlayerVM.kt:1484-1491` | `pauseForBackground` (`PlayerAction.OnBackground`, `:683`) saves the position: the moment the launcher row becomes visible. |

### CachedFeed epoch maps

| Location | What it does now |
|---|---|
| `data/cache/CachedFeed.kt:95,106,120,131` | `keyEpochs`, `keyInvalidations`, `keyWrites`, `keySupersedeWrites`: `ConcurrentHashMap<String, Long>` each. |
| `data/cache/CachedFeed.kt:117-119` | `keyWrites` documented as never cleared, so a ticket stays comparable across a namespace drop. |
| `data/cache/CachedFeed.kt:436-442` | `supersedeNamespace` clears only `keyEpochs` and `keyInvalidations`. |
| `data/cache/CachedFeed.kt:143-166,409-416` | `Epoch` is a private data class built by `epochOf` and held only as a local of one operation. `Stamped` (`:587-593`) stores `generation`, not an `Epoch`; nothing outlives an operation. |
| `data/cache/CachedFeed.kt:202,207,218,302-304,321-327,354-378,517-546` | Every capture and comparison of an `Epoch` (load loader, `peek`, `peekAll`, `putAll`, `readCached`, `promoteToMemory`). |
| `data/cache/ContentCacheRepository.kt` | One `itemFeed` and per-serializer `payloadFeed`s after `cache-decoded-memory-tier`; maps grow per feed. |

### Tests around it (`app/src/test/kotlin/com/kino/puber/`)

| Test | Contract it pins |
|---|---|
| `domain/interactor/player/PlayerInteractorTest.kt:650-690` | `saveWatchingTime` marks details stale (three tests); these change with the contract. |
| `ui/feature/player/vm/PlayerVMPlaybackFlowTest.kt:99-165`, `PlayerVMMutationAndResultTest.kt:222-492` | `PlaybackProgress` in the returned change set; must stay. |
| `core/tvhome/TvHomeSyncCoordinatorTest.kt` | 3 tests (failed load, empty load, logout); no debounce or throttle test. |
| `data/cache/CachedFeedTest.kt` | 42 tests, the race invariants (AC-8 of the parent brief). |
| `ui/feature/details/vm/DetailsVMCachedLoadTest.kt`, `data/repository/ItemDetailsRepositoryTest.kt` | Details cache reads and refresh. |

No PlayerVM test references `tvHomeSyncCoordinator` (grep of `ui/feature/player` tests).

## Related work

| Item | Relation |
|---|---|
| `cache-layer-rework` (idea, split) | Parent; B4 row, AC-7, AC-9, risk "Removing markStale leaves Details showing old progress". |
| `cached-feed-key-maps` (dropped) | Folded in; only `state.md`. Origin: `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md:111` (PERF-4), which suggests one record per key dropped when the key has no row and no flight. |
| `player-cache-save-churn` (idea) | Its `state.md` hands markStale-per-save and TV-home-per-save to this run; keeps ON_STOP and subtitle side-loading. No duplicate. |
| `startup-settled-signal` (done, 7b7530da) | Spec `docs/superpowers/specs/2026-11-21-startup-settled-signal.md:63,78,111` leaves the coordinator unchanged and names this run as owner of the coalesced TV home refresh. |
| `cache-decoded-memory-tier` (done, 3d820f83) | Spec `docs/superpowers/specs/2026-11-22-cache-decoded-memory-tier.md:86` changed `CachedFeed` encode placement and weights, "no change to epochs, barriers or load control flow". |
| `resume-progress-anomaly` (idea) | Progress not resumed on emulator; same save path, separate defect, not touched here. |
| `player-vm-split` (idea) | Would extract the progress-save coordinator from `PlayerVM`; touches the same lines, merge-order risk only. |
| Commits | `03c2ca31` introduced `itemDetailsRepository.markStale` in `saveWatchingTime`; `46c9d9a1` the coordinator and its 2 s debounce; `016dde35` `keySupersedeWrites`; `6be75d28` the race fixes the maps guard. |

Duplicate: none.

## External findings

External findings: off (local research only). `.flow/config.yaml` has no `research.web` key.

## Options

Three independent parts: M (markStale), T (TV home), E (epoch maps).

| Part | Choice | Trade-offs |
|---|---|---|
| M1 (recommended) | `saveWatchingTime` stops marking stale; `PlayerVM` marks the item stale once when at least one save succeeded since the last mark, at `exitPlayer` (inside `closeJob`, before `router.back`) and at `pauseForBackground`. | Next episode and prefetch read the warm record; one superseding write per session instead of one per 30 s. Process death between background and exit is covered by the background mark. |
| M2 | Mark stale only on the first successful save of each media. | Simpler, but the prefetch refetch then caches mid-session progress for 10 min, so a later Details open can still show old progress. |
| T1 (recommended) | `PlayerVM` no longer requests a refresh per save; it requests one at exit and at background when progress or watched changed; `DetailsVM`, `HistoryVM`, `MainVM` unchanged. | Zero refreshes while the player is in front; the row is fresh when the launcher becomes visible. Coordinator untouched. |
| T2 | Coordinator throttle: at most one refresh per window (e.g. 5 min) with a trailing call. | Covers all callers, but still refreshes during playback and delays a refresh the user can see after exit unless exit passes `immediate`. |
| E1 (recommended) | Fold the four counters into one per-key record with a holder count; operations that capture an `Epoch` acquire and release it; the record is removed when the count reaches zero. | Bounds the maps by in-flight keys. Safe because an `Epoch` never outlives its operation (`CachedFeed.kt:143,409`, `Stamped` holds generation only); acquire/release and removal must be atomic (`compute`). Touches every capture site; `CachedFeedTest` is the guard. |
| E2 | Drop records whose key has no Room row and no flight (audit wording). | Needs a store read to decide; rows are the common case, so the maps stay large. |

| Run option | Content | Size |
|---|---|---|
| A (recommended) | M1 + T1 + E1 | M: player, domain and `CachedFeed` race code. |
| B | M1 + T1; E1 split to its own run | S for this run; leaves the absorbed PERF-4 open again. |
| C | M2 + T2 + E1 | M; simpler player change, weaker freshness and still refreshes during playback. |

## Risks

| Risk | Guard |
|---|---|
| A later Details open shows pre-playback progress if the exit or background mark is skipped (crash, process kill while in front) | Background mark at `pauseForBackground`; 10 min TTL bounds the rest; unit tests that each exit path marks once. |
| Exit mark delays `router.back` (decode + Room write on `decodeDispatcher`) | Mark after `awaitPendingMutations` like the other writes; measure exit time on device. |
| Removing the per-save supersede lets a details load started before a save write pre-save progress | Same outcome as a plain TTL hit; the exit mark supersedes it. |
| Holder-count bug reopens superseded-write races (6be75d28) | `CachedFeedTest` unchanged and green; new tests for record removal under concurrent load/put/invalidate. |
| `keyWrites` reset across a namespace drop (`CachedFeed.kt:117-119`) | Removal only at holder count zero, so no writer holding an old ticket exists. |
| Merge overlap with `player-vm-split` | Order: this run first, or rebase. |

## Open questions

| # | Question | Recommended answer |
|---|---|---|
| 1 | When does progress reach later Details opens? | Mark details stale once at player exit and once at background, only if a save succeeded since the last mark (M1). |
| 2 | TV home refresh during playback? | None per save; one request at exit and at background when progress or watched changed (T1); `Watched` during playback also deferred to those moments. |
| 3 | Keep the epoch-map bound in this run? | Yes, E1 (run option A); unit-level evidence only for that part. |
| 4 | How is AC-9 (refresh count per hour of playback) measured? | Temporary uncommitted log at `TvHomeSyncCoordinator.refreshNow` and `markItemDetailsStale`, 30 min emulator playback before and after, as `startup-settled-signal` did with its measure patch. |

## Sources

- Files: `AGENTS.md`; `.flow/config.yaml`; `.flow/runs/cache-layer-rework/{brief,research}.md`; `.flow/runs/{cached-feed-key-maps,player-cache-save-churn,startup-settled-signal,resume-progress-anomaly}/state.md`; `.flow/runs/startup-splash-preload/perf-audit-2026-11-21.md:21`; `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md:111`; `docs/superpowers/specs/2026-11-21-startup-settled-signal.md`; `docs/superpowers/specs/2026-11-22-cache-decoded-memory-tier.md`; the code and test paths above.
- Commits: 40762767, 3d820f83, 7b7530da, 03c2ca31, 46c9d9a1, 016dde35, 6be75d28.
- URLs: none.
