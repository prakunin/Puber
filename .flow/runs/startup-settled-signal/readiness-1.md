# Readiness 1: startup-settled-signal

Verdict: **READY**

Spec `docs/superpowers/specs/2026-11-21-startup-settled-signal.md`, plan `docs/superpowers/plans/2026-11-21-startup-settled-signal.md` (worktree, untracked), test-design.md; base 7bcbfd83.

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | 1 (unit), 2 (instrumented) | `StartupSettledTest` x3, `splashReportsGoneOnceAfterTheFade` |
| AC-2 | 1, 2 (wiring) | `restoredActivitySettlesAtOnce` |
| AC-3 | 1 | `freshActivitySettlesAtTheFallbackWhenTheSplashNeverReportsGone`, `aSecondFreshActivityDoesNotPostponeTheFallback` |
| AC-4 | 4 | three `...BeforeStartupSettles` rows, `onStart_runsDeferredWorkInSlotOrderAfterStartupSettles`, guards |
| AC-5 | 5 | `onStart_doesNotCheckForUpdateBeforeItsStartupSlot`, `manualCheck_runsBeforeStartupSettles` |
| AC-6 | 3 | `noWarmStartsBeforeStartupSettles` + 2, wiring guard |
| AC-7 | 4, 5 | `deferredStartupWorkNeverRunsAfterTheViewModelIsCleared`, `onStart_skipsTheAutoCheckWhenClearedBeforeItsSlot` |
| AC-8 | 4 | `onStartAfterStartupSettled_countsSlotsFromMainStart` |
| AC-9 | 6 | `arrivalsQueuedBehindAnInFlightPublishFoldIntoOneFollowUp` + guards |
| AC-10 | 6 | `aPublishParkedAtTheHop...`, `aCacheWipeFromAnotherScreen...`, `aSectionArrivingAfterAClear...` |
| AC-11 | 7 (hand-off to qa-device) | Perf track metrics 1-5 |
| AC-12 | 5 (unit slot), 7 (hand-off) | `updatePromptShownOverFocusedContentTakesFocusWithinOneSecond`; Manual 2 |
| AC-13 | 7 | full gate command |

## Citations sampled (at 7bcbfd83)

| Citation | Resolves to |
|---|---|
| `core/startup/RevealGate.kt:16`, `:78` | `state = MutableStateFlow(false)`; `covers = begun && ...` |
| `core/startup/StartupReveal.kt:35` | `onActivityCreated(restored)` |
| `ui/feature/root/component/StartupSplashHost.kt:27`, `:36` | signature; `finishedListener = { if (it == 0f) splashGone = true }` |
| `MainActivity.kt:76`, `:90`, `:126` | `StartupReveal.onActivityCreated`; `StartupSplashHost(covered)`; `getKoin().get<ContentLaunchCoordinator>()` |
| `domain/di/modules.kt:65`, `:74-79`, `:80` | `awaitForeground` reference; `DetailsPrefetcher` single; `single { AppCoroutineScope() }` (same `interactorModule`, so the wiring guard covers it) |
| `ui/feature/main/vm/MainVM.kt:27-35`, `:45`, `:54`, `:79-89`, `:95-101`, `:190` | constructor; `startupSyncDelayServed`; `requestRefresh(immediate = true)`; `syncWatchState`; `reportDeviceInformation`; `StartupSyncDelay = 5.seconds` |
| `ui/feature/update/vm/UpdatePromptVM.kt:48-56` | auto-check `launch` |
| `domain/interactor/prefetch/DetailsPrefetcher.kt:120`, `:123`, `:125` | `warm`; `awaitForeground()`; `permits.acquire()` |
| `ui/feature/home/vm/HomeVM.kt:91`, `:104`, `:467`, `:505`; callers `:178,:341,:368,:379` | `publishMutex`; `catalogueEpoch`; `publishSections`; epoch check; four `publishSections()` calls |
| `HomeVMIncrementalPublishTest.kt:592`, `:694`, `:112`, `:466`, `:562`, `:668`, `:265` | `verify(exactly = 8 ...)`, `exactly = 6`, and the five named guards |
| `MainVMWatchStateSyncTest.kt:66,:103,:117,:161`; `MainVMDeviceInfoTest.kt:102`; `MainVMRefreshLifecycleTest.kt:71,:132,:172`; `UpdatePromptVMTest.kt:48,:61`; `DetailsPrefetcherTest.kt:33` | every constructor of the changed classes at base (`git grep` finds no other) |
| `StartupSplashHostTest.kt:52`; `UpdatePromptOverlayFocusTest.kt:118`, `:144` | pattern test; splash caller; AC-12 guard |

## Build order

Task 1 adds the class and binding before any consumer; Tasks 3-5 change each constructor together with its test fixtures; Task 2 resolves a binding Task 1 made. No task leaves the build red.

## Notes (non-blocking)

| # | Note |
|---|---|
| 1 | AC-10 text names "existing epoch tests in `HomeVMTest`"; they live in `HomeVMIncrementalPublishTest` (`:265`, `:562`), which the test design and Task 6 already use. |
| 2 | AC-11 P0 row names the perf track, not an automated test; it is a device measurement by nature. |
| 3 | The plan file is untracked in the worktree; Task 1's commit or the conductor must add it. |

Open questions: none (state.md Decisions answer Q1-Q4).
