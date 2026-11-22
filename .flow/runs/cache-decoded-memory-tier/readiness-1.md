# Readiness 1: cache-decoded-memory-tier

Base e9c383d0. Inputs: spec and plan (worktree `docs/superpowers/`), `test-design.md`, `state.md` Decisions. Paths under `app/src/main/java/com/kino/puber/` unless named.

Verdict: **NOT READY**

## AC coverage

| AC | Plan task | Test design row | Status |
|---|---|---|---|
| AC-1 | 4 | 4 CCR rows, P0 red | covered |
| AC-2 | 5 | 2 CCR rows, P0 red | covered for `ContentCacheRepository` only |
| AC-3 | 4 | `storedRecordsArePlainJsonUnderTheV2Namespace` | covered |
| AC-4 | 4, qa | `v1RowsAreIgnoredAndRefetchedIntoV2`, Manual check 1 | covered |
| AC-5 | 1, 4 | `aValuePastTheWeightCapEvictsTheOldestFromMemory`, `itemTierHoldsSixteenMegabytesOfEncodedJson` | covered |
| AC-6 | 4 | five `clearDrops...` rows | covered |
| AC-7 | 2, qa | DAO rows | covered |
| AC-8 | 3 | WSR rows, module test | covered |
| AC-9 | 1, 4, 5 | existing suites | covered |
| AC-10 | qa only | Perf track | **gap**, fix 1 |

## Citations sampled

| Citation | Claim | Resolves |
|---|---|---|
| `data/cache/CachedFeed.kt:54`, `:72` | `memoryWeigher: (V) -> Int`, tier weigher | yes |
| `data/cache/CachedFeed.kt:206-212`, `:357-374`, `:559-584` | epoch check and write in `load`, `putAll` write and stamp, `decodeUsable`/`Usable`/`Stamped` | yes |
| `data/cache/CachedFeed.kt:287-304`, `:307-332` | `peek` does not promote, `peekAll` promotes | yes |
| `data/cache/ContentCacheRepository.kt:32-43`, `:407-437`, `:586-593` | string feed, `queryNeedsRefetch`/`discardUndecodablePayload`/`decodeOrDiscard`, `content:v1:` and key builders | yes |
| `data/db/WatchStateDao.kt:63-80`, `:140-142` | `upsertAllFromServer`, server UPDATE WHERE | yes |
| `data/repository/WatchStateRepository.kt:41-47`, `:119-134`, `:304` | ctor, cleanup coroutine, `markLocally` await | yes |
| `data/di/modules.kt:97`, `:125`; `domain/di/modules.kt:82`, `:86`; `core/startup/StartupSettled.kt:50-52` | DI lines, `StartupSettled` single and `await` | yes |
| `core/collections/TypedTtlCache.kt:32-38`, `:313` | ctor weight params, weight eviction | yes |
| test: `CachedFeedTest.kt:27-33`, `FakePayloadStore.kt:10-11,22-27`, `WatchStateRepositoryTest.kt:51-57,271-293,806-808`, `WatchStateDaoTest.kt:52,60,145-152`, `EpisodeScheduleRepositoryModuleTest.kt:15-28`, `StartupSettledTestSupport.kt:6`, `MainDispatcherExtension.kt:12-14`, `AppStartupTest.kt:53`, `ContentCacheRepositoryTest.kt:235-267` | fixtures named in the plan | yes |
| `Makefile:23` | `GRADLE ?= ./gradlew`, so `GRADLE=./tools/agentw` overrides | yes |

## Other checks

| Check | Result |
|---|---|
| Build green per task, dependency order | Task 1 adjusts the only `CachedFeed` caller (`ContentCacheRepository.kt:42`) in the same commit; 4 consumes 1, 5 consumes 4; 2 and 3 independent |
| P0 rows name defect, mark, test | yes for every unit/instrumented row; AC-4 device and AC-10 rows: fix 2 |
| Out of scope | plan stays inside (no incremental snapshot, no schema bump, no tier-cap change) |
| Blocking questions | Q2 answered (`state.md` Decisions); none open |

## Fix list

1. AC-10 requires zero JSON decode on Main for cold start plus five Details opens, but the plan moves only `ContentCacheRepository` off Main. Ktor body decodes outside the cache stay in the caller's context (`data/api/KinoPubApiClient.kt:1026`, `:1173`; research.md Thread), and callers on Main remain in that sequence: every Details open of a movie without `bookmarks` runs `DetailsVM.kt:136-137` -> `DetailsInteractor.kt:59`, `:201` -> `KinoPubApiClient.kt:809-814` inside `viewModelScope` (`core/ui/PuberVM.kt:113`); the watch-state sync starts from `MainVM.kt:90-95` on Main inside the 15 s cold window, and `WatchStateSyncInteractor` has no dispatcher switch. `baseline.md` already counts Ktor converter decodes on Main. Either add a task that moves the decode in `apiCall` (`:1017-1033`) and `:1160-1177` onto an injected dispatcher, with a P0 red unit test and a Test design row, or narrow AC-10 to decodes reached through `ContentCacheRepository` (spec change, gate 1 again).
2. Test design: the AC-4 device row and the AC-10 row are P0 with no `red`/`guard` mark and no automated test. Move them to Manual checks / Perf track as manual items, or name the automated check.
