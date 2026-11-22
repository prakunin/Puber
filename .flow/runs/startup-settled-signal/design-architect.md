# Design (architect): startup-settled-signal

Base 7bcbfd83. Paths relative to `app/src/main/java/com/kino/puber/` unless they start with `app/`; line numbers are at base. Implements brief.md option A with Q1-Q3 as answered in state.md Decisions.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Where the latch lives | New `core/startup/StartupSettled.kt`: `internal class StartupSettled(scope: CoroutineScope, fallback: Duration = 5.seconds)` backed by a `CompletableDeferred<Unit>`; Koin `single` in `domain/di/modules.kt` beside `AppCoroutineScope` (`:80`), scope `get<AppCoroutineScope>()` | A Koin single, not an `object` like `StartupReveal`: VM tests drive a real instance on a `TestScope`, no global `resetForTests`; `CompletableDeferred.complete` is idempotent, which gives "once per process" (AC-1) |
| Latch inputs | `onActivityCreated(restored)`: restored settles at once (AC-2), otherwise arms the fallback once (`scope.launch { delay(fallback); settle() }`) unless already settled or armed (AC-3); `onSplashGone()` settles (AC-1). Nothing else settles it, so it is not complete before the first `onActivityCreated` | Mirrors `StartupReveal.onActivityCreated` (`core/startup/StartupReveal.kt:35`); not derived from `covered`, which is `false` before `begin()` (`core/startup/RevealGate.kt:16`, `:78`) |
| Who reports splash gone | `StartupSplashHost` gains `onGone: () -> Unit = {}`, called from the `finishedListener` next to `splashGone = true` (`ui/feature/root/component/StartupSplashHost.kt:36`); `MainActivity` passes `startupSettled::onSplashGone` (`MainActivity.kt:90`) and calls `startupSettled.onActivityCreated(restored)` right after `StartupReveal.onActivityCreated` (`MainActivity.kt:76`), resolving it with `getKoin().get()` as `acceptContentIntent` does (`MainActivity.kt:126`) | The fade end is the Q1 point; the default keeps `app/src/androidTest/.../StartupSplashHostTest.kt` compiling |
| Order and gaps (Q2) | `internal enum class StartupSlot { TvHome, UpdateCheck, DeviceInfo, WatchState }`, offset `(ordinal + 1) * STARTUP_SLOT_GAP`, `STARTUP_SLOT_GAP = 500.milliseconds`; `suspend fun awaitSlot(slot) { await(); delay(slot.offset) }`. Prefetch uses `await()` (offset 0) | The delay starts when `await()` returns, i.e. at max(settled, caller start): counted from settled on a cold start and from Main start after login (AC-8) with one code path; the order lives in one enum |
| Consumers' scope | Each awaits inside the coroutine it already launches, so it stays owned by `viewModelScope` or the prefetcher's scheduled job (AC-7) | Option A; no work moves to app scope |
| TV home refresh | `MainVM.onStart` wraps `tvHomeSyncCoordinator?.requestRefresh(immediate = true)` (`ui/feature/main/vm/MainVM.kt:54`) in `launch { startupSettled.awaitSlot(TvHome); ... }`; `TvHomeSyncCoordinator` unchanged | Its scope belongs to `cache-targeted-invalidation` (brief Out) |
| Watch-state first-run wait | `syncWatchState` (`MainVM.kt:79-89`) replaces `delay(StartupSyncDelay)` with `startupSettled.awaitSlot(WatchState)`, still once per VM (flag `startupSyncDelayServed` `:45` renamed `startupSlotServed`); `StartupSyncDelay` (`:190`) and its KDoc (`:182-189`, mention at `:72`) removed | AC-4; a later `MainAction.Resumed` run must not pay 2 s again |
| Device info | `reportDeviceInformation` (`MainVM.kt:95-101`) awaits `awaitSlot(DeviceInfo)` inside its `launch`, before `setDeviceInformation()` is called (not only before `collect`) | AC-4 asserts the call itself |
| Update auto check | `UpdatePromptVM` gets `startupSettled: StartupSettled`; the auto-check `launch` (`ui/feature/update/vm/UpdatePromptVM.kt:48-56`) awaits `awaitSlot(UpdateCheck)` first. Manual path (`:38-42`, `:129-155`) untouched | AC-5; resolved by `viewModelOf(::UpdatePromptVM)` (`ui/feature/root/component/App.kt:62`) from root |
| Prefetch gate | `DetailsPrefetcher` gets a required `awaitSettled: suspend () -> Unit`, wired `get<StartupSettled>()::await` (`domain/di/modules.kt:75-79`); `warm` (`domain/interactor/prefetch/DetailsPrefetcher.kt:120`) calls it first, before `foreground.awaitForeground()` (`:123`) and before a permit is taken (`:125`) | AC-6; a function, like `awaitForeground` for `WatchStateSyncInteractor` (`domain/di/modules.kt:65`), because the public `DetailsPrefetcher` cannot expose the internal latch type; required so a missed wiring fails to compile |
| MainVM injection | Required constructor parameter `startupSettled: StartupSettled` before `tvHomeSyncCoordinator` (`MainVM.kt:27-35`); `viewModelOf(::MainVM)` (`ui/feature/main/component/MainScreen.kt:32`) resolves it from root | No default: a default instance would hide missing wiring and never settle |
| Publish coalescing rule | Leading-edge by sequence: `publishSections()` takes `mine = ++requestedPublishes`, then inside `publishMutex` returns at once if `publishedThrough >= mine`; otherwise snapshots, captures `upTo = requestedPublishes` with the snapshot, maps, applies the epoch check (`ui/feature/home/vm/HomeVM.kt:505`) and sets `publishedThrough = upTo` on both the publish and the epoch-skip exit. Counters are `MutableStateFlow<Long>` | First request runs at once; requests queued behind an in-flight publish collapse into the first of them to get the lock, which reads the freshest snapshot; the rest skip without mapping (AC-9). Each caller still runs in its own coroutine, so a cancelled follow-up leaves `publishedThrough` unmoved and the next queued request publishes. `publishMutex` (`:91`) and `catalogueEpoch` (`:104`) stay (AC-10) |
| Test drain for coalesced publishes | `@VisibleForTesting internal suspend fun awaitPublishesDrained()` = `publishedThrough.first { it >= requestedPublishes.value }` replaces the exact-count drains `verify(exactly = 8, timeout = 2_000)` (`app/src/test/kotlin/com/kino/puber/ui/feature/home/vm/HomeVMIncrementalPublishTest.kt:592`) and `exactly = 6` (`:694`) | Those counts assume one mapping per arrival, which coalescing removes; once drained, no queued request can map again |

## Changes by layer

| Layer | File | Change |
|---|---|---|
| API / repository | N/A: no API or repository change | |
| Core / DI | `core/startup/StartupSettled.kt` (new) | `StartupSettled`, `StartupSlot`, `STARTUP_SLOT_GAP` per Decisions |
| Core / DI | `domain/di/modules.kt:75-80` | `single { StartupSettled(scope = get<AppCoroutineScope>()) }`; `DetailsPrefetcher(..., awaitSettled = get<StartupSettled>()::await)` |
| Domain | `domain/interactor/prefetch/DetailsPrefetcher.kt:33-42`, `:120-123` | `awaitSettled` parameter; first call in `warm` |
| ViewModel | `ui/feature/main/vm/MainVM.kt:27-35`, `:44-45`, `:47-61`, `:72`, `:79-89`, `:95-101`, `:181-190` | Latch parameter; three slots; `StartupSyncDelay` removed |
| ViewModel | `ui/feature/update/vm/UpdatePromptVM.kt:21-27`, `:48-56` | Latch parameter; auto check awaits `UpdateCheck` |
| ViewModel | `ui/feature/home/vm/HomeVM.kt:79-91`, `:467-520` | Sequence counters, early return, `publishedThrough` update, test drain hook; `publishMutex` KDoc extended with the coalescing rule |
| Screen and DI module | `MainActivity.kt:76`, `:90` | `onActivityCreated` and `onGone` wiring |
| Screen and DI module | `ui/feature/main/component/MainScreen.kt:32`, `ui/feature/root/component/App.kt:62` | Unchanged; new parameters resolve from root |
| Compose content | `ui/feature/root/component/StartupSplashHost.kt:27`, `:36` | `onGone` parameter and call |
| Resources | N/A: no strings or resources | |

Test fixtures that construct the changed classes and must pass a latch or `awaitSettled`: `MainVMWatchStateSyncTest.kt:161` (and the `StartupSyncDelay` advances at `:66`, `:103`, `:117`), `MainVMDeviceInfoTest.kt:102`, `MainVMRefreshLifecycleTest.kt:71`, `:132`, `:172`, `UpdatePromptVMTest.kt:48`, `:61`, `DetailsPrefetcherTest.kt:33` (all under `app/src/test/kotlin/com/kino/puber/`); which tests are added is test-design.md's.

## Alternatives rejected

- Latch as a process `object` next to `StartupReveal`: global state shared across JVM tests, needs a reset hook in every VM test.
- Latch derived from `StartupReveal.covered`: fires before `begin()` (research.md Risks).
- Trailing time window for publishes: adds its length to the hero publish and so to every reveal (brief Risks).
- Conflated channel with one publisher coroutine: callers stop awaiting their publish, and cancelling that coroutine with `loadHomeJob` loses the pending follow-up.
- Gating inside `TvHomeSyncCoordinator.requestRefresh`: also gates the `DetailsVM`, `PlayerVM` and `HistoryVM` callers and crosses Out of scope.
- Central work queue (research.md option B): moves VM-owned work to app scope.

## Risks

| # | Area | Risk | Lives in | Guard |
|---|---|---|---|---|
| 1 | Focus / D-pad | The update prompt now opens over a revealed Home that already holds focus (about 1 s after settled), instead of under the splash; `screenFocusActive = !updatePromptOpen` must move focus to the prompt and Back must return it to Home | `ui/feature/root/component/App.kt:167`, `:180-185` | AC-12 on device; `update-dialog-focus-trap` focus tests |
| 2 | Correctness (publish) | A folded request left unpublished: a skipper returns only when `publishedThrough >= mine`, which a cancelled follow-up never sets, so the next request still maps | `ui/feature/home/vm/HomeVM.kt:467-520` | AC-9 test with arrivals queued behind a parked publish; AC-10 epoch tests |
| 3 | Lifecycle | Deferred work outliving its owner (logout, domain switch before the slot) | `MainVM.kt`, `UpdatePromptVM.kt` awaits inside `launch` (`core/ui/PuberVM.kt:108-113`, `viewModelScope`) | AC-7 |
| 4 | Lifecycle | Latch never completes: only `onActivityCreated(restored = false)` arms the fallback, so a process without an activity never settles; every consumer is activity-bound (Main, App root, focus-driven prefetch) | `core/startup/StartupSettled.kt` | AC-3 |
| 5 | Performance (network) | After login watch-state starts 2 s after Main start instead of 5 s, sharing OkHttp's five-per-host budget with Home's cold load | `MainVM.kt:79-89`; `domain/interactor/watchstate/WatchStateSyncInteractor.kt:96` | AC-8 accepts it; slot gap is one constant |
| 6 | Concurrency | Counters are written on the main dispatcher; the drain hook reads them from a test thread | `HomeVM.kt` counters as `MutableStateFlow<Long>` | Decisions: test drain |
| 7 | Rollout | The worktree carries uncommitted baseline instrumentation (`android.util.Log.i("SettledMeasure", "homePublish")` in `HomeVM.kt` after the epoch check, untracked `docs/superpowers/evidence/2026-11-21-startup-settled-signal-measure.patch`) that must not land in a task commit | worktree `git status` | Kept out of every task commit; applied only for AC-11 runs |

## Open questions

None.
