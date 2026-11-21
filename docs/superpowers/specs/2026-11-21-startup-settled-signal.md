# One startup-settled signal that defers background work and coalesces Home publishes

Status: approved
Date: 2026-11-21
Slug: startup-settled-signal
Kind: improvement
Size: M
Tracks: perf

## Problem / Why

### Request

research.md: Request. Split out of `startup-splash-preload` (its `state.md` Decisions, Q1).

### Current state

Facts are in research.md: Current state in code. The ones the change rests on (paths relative to `app/src/main/java/com/kino/puber/`):

| Fact | Where |
|---|---|
| `covered` is `false` before `begin()` and latches after the first reveal; not a "revealed" signal alone | `core/startup/RevealGate.kt:144-150` |
| Restored activity reveals at once; open update prompt forces reveal | `core/startup/StartupReveal.kt:35`, `:45-47` |
| Splash leaves composition only through a local `splashGone`; no signal leaves the composable | `ui/feature/root/component/StartupSplashHost.kt:27-38` |
| Device info, watch-state sync and TV home `immediate` refresh start in `MainVM.onStart` | `ui/feature/main/vm/MainVM.kt:52-54` |
| Watch-state first run waits `StartupSyncDelay = 5.seconds` once | `MainVM.kt:79-89`, `:190` |
| TV home refresh cancels the previous pending call; `immediate` skips the 2 s debounce | `core/tvhome/TvHomeSyncCoordinator.kt:21-27` |
| Update auto check runs in `UpdatePromptVM.onStart`, composed under the splash | `ui/feature/update/vm/UpdatePromptVM.kt:46-57`, `ui/feature/root/component/App.kt:135-152` |
| Prefetch warm already gates on `foreground.awaitForeground()` | `domain/interactor/prefetch/DetailsPrefetcher.kt:75-91` |
| Every section arrival calls `publishSections()`, serialized by `publishMutex`, epoch-guarded | `ui/feature/home/vm/HomeVM.kt:178,341,368,379`, `:467-525` |
| Reveal waits for a publish carrying `heroSettled` -> `LaunchFocusSettled` | `ui/feature/home/component/HomeScreenContent.kt:224-230`, `HomeVM.kt:202` |

### Risks (analyst)

| Risk | Guard |
|---|---|
| Latch fires early (`covered` false before `begin()`) or never (no activity start) | Explicit latch with a begun flag and a fallback bound; AC-1, AC-3 |
| Coalescing delays the hero publish and so the reveal | Leading-edge only, no window; AC-9, AC-11 |
| A folded publish request lost, leaving a row unshown | Follow-up reads the snapshot under `publishMutex`; AC-9 |
| Deferred work escapes its scope and runs after logout | Await in the current owning scope; AC-7 |
| Post-login Main loses the 5 s cushion for watch-state | Q2 slot gaps apply from Main start; AC-8 |
| Emulator noise hides the gain (parent AC-13/AC-15) | Count-based measures (publishes, cap reveals) beside `key_lat`; real-TV left to `startup-first-input-on-tv` |

## Acceptance criteria

- AC-1. Given a cold start with the splash, When the splash fade finishes (splash out of composition), Then the settled latch completes exactly once for the process; Given `StartupReveal` has not begun, Then the latch is not complete (unit test with a fake gate/clock).
- AC-2. Given a restored activity (`onActivityCreated(restored = true)`), When it starts, Then the latch completes without waiting for a splash (unit test).
- AC-3. Given `begin()` ran and the splash never reports gone, When the fallback bound (Q1) elapses, Then the latch completes (unit test on virtual time).
- AC-4. Given the latch is not complete, When `MainVM.onStart` runs, Then neither `setDeviceInformation`, `syncIfStale` nor `requestRefresh` is called; When it completes, Then they run in the Q2 order and gaps (`MainVMDeviceInfoTest`, `MainVMWatchStateSyncTest`, `MainVMRefreshLifecycleTest` on a fake latch; `StartupSyncDelay` no longer referenced).
- AC-5. Given the latch is not complete and auto check is enabled, When `UpdatePromptVM.onStart` runs, Then `checkForUpdate` is not called until its Q2 slot; the manual check path is not gated (`UpdatePromptVMTest`).
- AC-6. Given the latch is not complete, When focus dwells on a card, Then `DetailsPrefetcher` issues no details request; When it completes, Then pending warms proceed as today (`DetailsPrefetcherTest`).
- AC-7. Given the VM owning a deferred task is cleared (logout, domain switch) before its slot, Then that task never runs (unit test).
- AC-8. Given Main starts after login on Auth (latch already complete), Then the deferred work runs in Q2 order counted from Main start, with no extra delay (unit test).
- AC-9. Given several section arrivals while a publish is in flight, When it finishes, Then exactly one follow-up publish runs with the latest snapshot, and no arrived section is missing from the final `Content`; the first arrival still publishes at once (`HomeVMIncrementalPublishTest`, including `theFirstSectionToArrivePublishesContentWithoutWaitingForTheRest`).
- AC-10. Given a catalogue clear lands during a coalesced publish, Then the stale result is not published (existing epoch tests in `HomeVMTest` stay green).
- AC-11. Given 5 cold starts each before (7bcbfd83) and after on the emulator in devBenchmarkRelease, Then the evidence reports Home publishes per start, `revealed`, `key_lat` p95 for keys in the first 3 s after `revealed`, and starts revealed by the cap; publishes drop, `revealed` does not regress beyond noise, and no start is revealed by the cap that was not before (Q3).
- AC-12. Given a newer release is available, When the app cold-starts, Then the update prompt appears after the reveal with focus on its Update button and Back dismisses it (device, if a newer release is available; otherwise unit test of the slot plus `update-dialog-focus-trap` focus tests) (owner judges the timing).
- AC-13. The full check passes in the worktree.

## Out of scope

In: research.md option A (research.md: Options). A process-wide settled latch in `core/startup`; the five consumers await it at their current start point and keep their current owning scope; `StartupSyncDelay` removed. HomeVM publish requests become leading-edge conflated (first runs at once; requests arriving while a publish runs fold into one follow-up of the latest snapshot; no time window), keeping `publishMutex` and the epoch check.
Out: the `TvHomeSyncCoordinator` scope and coalesced TV home refresh (`cache-targeted-invalidation`); decode, JIT and main-thread I/O costs (`card-image-size-tier`, `release-startup-compilation`, `startup-main-thread-io`); real-TV measurement (`startup-first-input-on-tv`).

## Design

Base 7bcbfd83. Paths relative to `app/src/main/java/com/kino/puber/` unless they start with `app/`; line numbers are at base. Implements brief.md option A with Q1-Q3 as answered in state.md Decisions.

### Decisions

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

### Changes by layer

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

### Alternatives rejected

- Latch as a process `object` next to `StartupReveal`: global state shared across JVM tests, needs a reset hook in every VM test.
- Latch derived from `StartupReveal.covered`: fires before `begin()` (research.md Risks).
- Trailing time window for publishes: adds its length to the hero publish and so to every reveal (brief Risks).
- Conflated channel with one publisher coroutine: callers stop awaiting their publish, and cancelling that coroutine with `loadHomeJob` loses the pending follow-up.
- Gating inside `TvHomeSyncCoordinator.requestRefresh`: also gates the `DetailsVM`, `PlayerVM` and `HistoryVM` callers and crosses Out of scope.
- Central work queue (research.md option B): moves VM-owned work to app scope.

### Risks

| # | Area | Risk | Lives in | Guard |
|---|---|---|---|---|
| 1 | Focus / D-pad | The update prompt now opens over a revealed Home that already holds focus (about 1 s after settled), instead of under the splash; `screenFocusActive = !updatePromptOpen` must move focus to the prompt and Back must return it to Home | `ui/feature/root/component/App.kt:167`, `:180-185` | AC-12 on device; `update-dialog-focus-trap` focus tests |
| 2 | Correctness (publish) | A folded request left unpublished: a skipper returns only when `publishedThrough >= mine`, which a cancelled follow-up never sets, so the next request still maps | `ui/feature/home/vm/HomeVM.kt:467-520` | AC-9 test with arrivals queued behind a parked publish; AC-10 epoch tests |
| 3 | Lifecycle | Deferred work outliving its owner (logout, domain switch before the slot) | `MainVM.kt`, `UpdatePromptVM.kt` awaits inside `launch` (`core/ui/PuberVM.kt:108-113`, `viewModelScope`) | AC-7 |
| 4 | Lifecycle | Latch never completes: only `onActivityCreated(restored = false)` arms the fallback, so a process without an activity never settles; every consumer is activity-bound (Main, App root, focus-driven prefetch) | `core/startup/StartupSettled.kt` | AC-3 |
| 5 | Performance (network) | After login watch-state starts 2 s after Main start instead of 5 s, sharing OkHttp's five-per-host budget with Home's cold load | `MainVM.kt:79-89`; `domain/interactor/watchstate/WatchStateSyncInteractor.kt:96` | AC-8 accepts it; slot gap is one constant |
| 6 | Concurrency | Counters are written on the main dispatcher; the drain hook reads them from a test thread | `HomeVM.kt` counters as `MutableStateFlow<Long>` | Decisions: test drain |
| 7 | Rollout | The worktree carries uncommitted baseline instrumentation (`android.util.Log.i("SettledMeasure", "homePublish")` in `HomeVM.kt` after the epoch check, untracked `docs/superpowers/evidence/2026-11-21-startup-settled-signal-measure.patch`) that must not land in a task commit | worktree `git status` | Kept out of every task commit; applied only for AC-11 runs |

### Open questions

None.

## Test design

Base 7bcbfd83. Implements brief.md ACs against design-architect.md. Test paths are under `app/src/test/kotlin/com/kino/puber/` (unit) and `app/src/androidTest/kotlin/com/kino/puber/` (instrumented, run only via `make DEVICE=<serial> itest TESTS=<fqcn>`).

### Fixtures

| Fixture | Rule |
|---|---|
| Latch in VM and prefetcher tests | A real `StartupSettled(scope = backgroundScope)` on the test's `StandardTestDispatcher`; settle it through its public inputs (`onActivityCreated(restored = true)` or `onSplashGone()`), never a mock |
| Existing tests that construct the changed classes (design-architect.md: Changes by layer, fixture list) | Get a latch settled before `testOnStart()` and advance past the relevant slot, so their current assertions keep their meaning |
| `StartupSyncDelay` advances in `MainVMWatchStateSyncTest` | Replaced by settling the latch and advancing `StartupSlot.WatchState` offset + 1 ms |
| Exact-count drains (`HomeVMIncrementalPublishTest.kt:592`, `:694`) | Replaced by `awaitPublishesDrained()` (design-architect.md: Test drain) |
| Parking a publish | Reuse the `CountDownLatch` + `AtomicBoolean` gate on `mapper.mapItemSection` and the real thread pool from `publishSectionsSerializesConcurrentSectionArrivals` |

### Risk profile

| # | Risk (source) | P | I | Score | Covered by |
|---|---|---|---|---|---|
| 1 | Latch complete before `begin()` (brief) | 2 | 3 | 6 | `isNotSettledBeforeAnyActivityIsCreated` |
| 2 | Latch never completes (brief; design 4) | 2 | 3 | 6 | `freshActivitySettlesAtTheFallbackWhenTheSplashNeverReportsGone` |
| 3 | `onGone` not wired in `MainActivity`, so every start settles only at the 5 s fallback | 2 | 2 | 4 | `splashReportsGoneOnceAfterTheFade`; perf metric 5 |
| 4 | Coalescing delays the hero publish and so the reveal (brief) | 2 | 3 | 6 | `theFirstSectionToArrivePublishesContentWithoutWaitingForTheRest`; perf metric 2 |
| 5 | A folded publish request lost, a row unshown (brief; design 2) | 2 | 3 | 6 | `arrivalsQueuedBehindAnInFlightPublishFoldIntoOneFollowUp`, `aCancelledFollowUpLeavesTheNextQueuedRequestToPublish`, `aSectionArrivingAfterAClearPublishesEvenWhenAStalePublishWasSkipped` |
| 6 | Deferred work runs after logout / domain switch (brief; design 3) | 2 | 2 | 4 | `deferredStartupWorkNeverRunsAfterTheViewModelIsCleared` |
| 7 | Post-login watch-state starts 2 s after Main start (brief; design 5) | 2 | 1 | 2 | Accepted (Q2); timing pinned by `onStartAfterStartupSettled_countsSlotsFromMainStart` |
| 8 | Emulator noise hides the gain (brief) | 3 | 1 | 3 | Count-based perf metrics 1 and 3 gate; `key_lat` reported only |
| 9 | Update prompt over a revealed, focused Home does not take focus or Back does not return it (design 1) | 2 | 3 | 6 | `updatePromptShownOverFocusedContentTakesFocusWithinOneSecond`; manual 2 |
| 10 | Counter reads from the test thread race main-dispatcher writes (design 6) | 1 | 2 | 2 | Accepted: counters are `MutableStateFlow` |
| 11 | Baseline instrumentation (`SettledMeasure`) lands in a task commit (design 7) | 2 | 2 | 4 | Manual 4 |
| 12 | `StartupSettled` missing from Koin: MainVM/UpdatePromptVM crash at resolution | 1 | 3 | 3 | `aScreenScopeResolvesTheGlobalPrefetcher`; manual 1 |

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `StartupSettledTest.isNotSettledBeforeAnyActivityIsCreated` | Fails if the latch is derived from `covered` or completes at construction |
| AC-1 | unit | P0 red | `StartupSettledTest.splashGoneSettlesTheLatch` | Fails if `onSplashGone()` does not resume a waiter on `await()` |
| AC-1 | unit | P1 | `StartupSettledTest.settlingTwiceResumesEachWaiterOnce` | Two waiters each resume exactly once after `onSplashGone()` twice plus the fallback elapsing; fails if a settle re-arms or resets the latch |
| AC-1 | instrumented | P1 | `ui/feature/root/component/StartupSplashHostTest.splashReportsGoneOnceAfterTheFade` | `onGone` count is 0 while covered, 1 after the fade, still 1 after further frames; fails if not called from the `finishedListener` or called per recomposition |
| AC-2 | unit | P0 red | `StartupSettledTest.restoredActivitySettlesAtOnce` | `await()` returns with no virtual time advanced; fails if a restored activity waits for a splash or the fallback |
| AC-3 | unit | P0 red | `StartupSettledTest.freshActivitySettlesAtTheFallbackWhenTheSplashNeverReportsGone` | Not settled at fallback - 1 ms, settled at fallback; fails if the fallback is not armed or uses another bound |
| AC-3 | unit | P2 | `StartupSettledTest.aSecondFreshActivityDoesNotPostponeTheFallback` | Fails if each `onActivityCreated(false)` restarts the timer |
| AC-4 | unit | P0 red | `MainVMDeviceInfoTest.onStart_doesNotReportDeviceInformationBeforeStartupSettles` | Unsettled latch, 10 s advanced: `setDeviceInformation` not called; fails if the await sits after the call or is missing |
| AC-4 | unit | P0 red | `MainVMRefreshLifecycleTest.onStart_doesNotRequestTvHomeRefreshBeforeStartupSettles` | Unsettled latch, 10 s advanced: `requestRefresh` not called |
| AC-4 | unit | P0 red | `MainVMWatchStateSyncTest.onStart_doesNotSyncWatchStateBeforeStartupSettles` | Unsettled latch, 10 s advanced: `syncIfStale` not called; fails while a fixed 5 s delay remains |
| AC-4 | unit | P0 red | `MainVMStartupSlotsTest.onStart_runsDeferredWorkInSlotOrderAfterStartupSettles` | Settled at t0: TV home at +500 ms, device info at +1500 ms, watch-state at +2000 ms, each absent 1 ms earlier; fails if the order, the gap or the reference point changes |
| AC-4 | unit | P0 guard | `MainVMWatchStateSyncTest.startupAndFirstResume_syncWatchStateOnceBetweenThem` | Fails if `MainAction.Resumed` during the slot wait starts its own run |
| AC-4 | unit | P1 guard | `MainVMWatchStateSyncTest.resumed_syncsWatchStateWithoutWaitingOnceTheStartupBurstHasPassed` | Fails if a later resume pays the slot again (`startupSlotServed` lost) |
| AC-4 | unit | P1 guard | `MainVMDeviceInfoTest.onStart_keepsMainScreenUsableWhenDeviceReportFails` | Fails if a failed report after the slot breaks Main state |
| AC-5 | unit | P0 red | `UpdatePromptVMTest.onStart_doesNotCheckForUpdateBeforeItsStartupSlot` | Unsettled: no `checkForUpdate`; settled + 999 ms: none; fails if the auto check is not gated |
| AC-5 | unit | P0 guard | `UpdatePromptVMTest.manualCheck_runsBeforeStartupSettles` | Unsettled latch: manual check reaches `Available`; fails if the gate is put on the shared check path |
| AC-5 | unit | P1 guard | `UpdatePromptVMTest.onStart_showsAvailable_whenUpdateExists` | With a settled latch and the slot advanced, an update still shows `Available` |
| AC-5 | unit | P1 guard | `UpdatePromptVMTest.onStart_keepsHiddenAndSkipsCheck_whenAutoCheckDisabled` | Fails if the slot path ignores the auto-check preference |
| AC-6 | unit | P0 red | `DetailsPrefetcherTest.noWarmStartsBeforeStartupSettles` | Unsettled gate, dwell elapsed: no details request; fails if `warm` does not call `awaitSettled` first |
| AC-6 | unit | P1 | `DetailsPrefetcherTest.aWarmHeldForStartupProceedsOnceItSettles` | The focused card is warmed after settle; fails if a held warm is dropped |
| AC-6 | unit | P2 | (dropped in review round 1) | The gate precedes the permit in `warm`; no test can observe the order, since every warm is held by the same gate |
| AC-6 | unit | P1 guard | `domain/di/DetailsPrefetcherWiringTest.aScreenScopeResolvesTheGlobalPrefetcher` | Fails if `StartupSettled` is not bound in the global module |
| AC-7 | unit | P0 red | `MainVMStartupSlotsTest.deferredStartupWorkNeverRunsAfterTheViewModelIsCleared` | Cancel the VM scope before settle, then settle and advance 10 s: none of the three calls; fails if any await moves to app scope or a call precedes its await |
| AC-7 | unit | P1 | `UpdatePromptVMTest.onStart_skipsTheAutoCheckWhenClearedBeforeItsSlot` | Same for the update check |
| AC-8 | unit | P0 red | `MainVMStartupSlotsTest.onStartAfterStartupSettled_countsSlotsFromMainStart` | Latch settled, 10 s advanced, then Main starts: TV home at +500 ms and watch-state at +2000 ms from Main start; fails if offsets count from settle time or a 5 s wait remains |
| AC-9 | unit | P0 guard | `HomeVMIncrementalPublishTest.theFirstSectionToArrivePublishesContentWithoutWaitingForTheRest` | Fails if coalescing adds a window before the leading publish |
| AC-9 | unit | P0 red | `HomeVMIncrementalPublishTest.arrivalsQueuedBehindAnInFlightPublishFoldIntoOneFollowUp` | First publish parked, three sections arrive, release, drain: `mapHeroItems` runs twice from the park on and the final `Content` holds all four section types; base maps four times |
| AC-9 | unit | P0 guard | `HomeVMIncrementalPublishTest.publishSectionsSerializesConcurrentSectionArrivals` | Fails if `publishMutex` stops covering snapshot through state update |
| AC-9 | unit | P1 | `HomeVMIncrementalPublishTest.aCancelledFollowUpLeavesTheNextQueuedRequestToPublish` | A queued request under `loadHomeJob` cancelled (via `CommonAction.OnResume`) while a `cardDisplayChanges` request queues behind it: the latter still publishes; fails if `publishedThrough` moves on cancel or the skip compares the live counter instead of `mine` |
| AC-9 | unit | P1 guard | `HomeVMIncrementalPublishTest.heroSettledIsSnapshottedWithTheRestOfThePublishSoAStalePublishCannotPairItWithEmptyHeroItems` | Fails if `upTo` or `heroSettled` is read outside the snapshot |
| AC-10 | unit | P0 guard | `HomeVMIncrementalPublishTest.aPublishParkedAtTheHopCannotResurrectRowsAClearAlreadyDropped` | Fails if the epoch check is dropped or moved before mapping |
| AC-10 | unit | P0 guard | `HomeVMIncrementalPublishTest.aCacheWipeFromAnotherScreenDropsTheRowsFromThePreviousCatalogue` | Fails if a folded publish carries pre-wipe rows |
| AC-10 | unit | P1 | `HomeVMIncrementalPublishTest.aSectionArrivingAfterAClearPublishesEvenWhenAStalePublishWasSkipped` | Fails if the epoch-skip exit sets `publishedThrough` to the current `requestedPublishes` instead of the captured `upTo` |
| AC-11 | device | P0 red | Perf track below | Fails if publishes do not drop or reveal/cap regress |
| AC-12 | instrumented | P0 guard | `ui/feature/update/component/UpdatePromptOverlayFocusTest.updatePromptShownOverFocusedContentTakesFocusWithinOneSecond` | Fails if a prompt opened over focused content leaves focus beneath |
| AC-12 | device | P1 | Manual 2 | Prompt after reveal, focus on Update, Back returns to Home |
| AC-13 | manual | P0 guard | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` in the worktree | Exit 0 |

### Bugs

N/A: kind improvement.

### Manual checks

Emulator, lease held, worktree devBenchmarkRelease APK installed with `tools/android-apk-install-preserve`.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| 1 | `am force-stop`, cold start; on reveal press `DPAD_RIGHT` x3, `DPAD_DOWN` x2 within 3 s | Splash fades, Home focus moves with each key | Crash, `NoDefinitionFoundException`, focus loss, Home left on the splash past the 2.5 s cap |
| 2 | Only if a newer release than the installed build exists: cold start, wait | Prompt opens after the reveal (about 1 s), focus on Update; `DPAD_LEFT/RIGHT/UP/DOWN` stay inside; `BACK` closes it and focus is back on Home | Prompt under the splash, focus on Home behind the prompt, Back exiting the app |
| 3 | Restored start: `HOME`, `am kill <installed package>` (background kill, no data clear), relaunch from recents | Content shown with no splash; first `DPAD` keys respond | Splash shown, 5 s stall before Home responds |
| 4 | `git -C <wt> log -p 7bcbfd83..HEAD \| grep -c SettledMeasure`; `git -C <wt> grep -n StartupSyncDelay` | `0`; no match | Instrumentation or the old constant in a task commit |

### Perf track

Device and build: the same emulator and devBenchmarkRelease, 5 cold starts (`am force-stop` then launch) at 7bcbfd83 (baseline, captured by qa-device before implementation with the measure patch) and at the task head with the same patch.

| # | Metric | Measured by | Pass |
|---|---|---|---|
| 1 | Home publishes per start | Count of `SettledMeasure homePublish` lines per start | Median after < median before |
| 2 | `revealed` | Existing startup telemetry mark, ms from process start | Median after <= median before + max(100 ms, baseline max - min) |
| 3 | Starts revealed by the cap | Telemetry cap reason per start | After count <= before count |
| 4 | `key_lat` p95, keys in the first 3 s after `revealed` | Parent run's key-latency method (startup-splash-preload `qa-report.md`) | Reported only (emulator noise, Q3) |
| 5 | Settle reason | A `SettledMeasure settled` line with `splash`/`restored`/`fallback` in the after patch only | 5 of 5 cold starts settle by `splash` |

### Baseline (qa-device, mode baseline)

See `.flow/runs/startup-settled-signal/baseline.md` and `docs/superpowers/evidence/2026-11-21-startup-settled-signal-baseline-device.md`: at 7bcbfd83 on emulator-5554, devBenchmarkRelease, n = 8: Home publishes per start median 8 (8-9); `revealed` median 2857 ms (2709-3093); `first_content` 1124 ms; 8 of 8 starts revealed by the cap (inferred); key_lat p95 in the first 3 s after reveal 1060 ms (39 keys).

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | When is startup settled? | yes | answered | When the splash leaves composition after the fade, or at once for a restored activity; fallback 5 s after `begin()`. Once per process. |
| Q2 | Order and gaps, and after login? | yes | answered | Prefetch gate opens at settled; then TV home refresh, update check, device info, watch-state sync, 500 ms apart (tunable constant). After login the same order counted from Main start. |
| Q3 | What proves the gain? | yes | answered | AC-11 on the emulator: fewer publishes per start, no `revealed` or cap regression, `key_lat` p95 reported (not gated, emulator noise); real TV stays with `startup-first-input-on-tv`. |
| Q4 | Baseline: every start at base is revealed by the 2.5 s cap although Home is ready 1.3-1.5 s earlier. Fix it here? | no | answered | No: a defect of startup-splash-preload, folded into the existing run `startup-first-input-on-tv` (Log line added); AC-11's cap clause stays and is reported as vacuous if the after build is still capped. |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-21 | Q1-Q3: recommended answers | owner (standing instruction: recommended) |
| 2026-11-21 | Q4: reveal held to the cap folded into startup-first-input-on-tv | owner (standing instruction: recommended) |
| 2026-11-21 | codex: required; caps lifted | owner |
| 2026-11-21 | Gate 1: approve (size M, tracks perf, 13 ACs, design option A) | owner (standing instruction: recommended) |
