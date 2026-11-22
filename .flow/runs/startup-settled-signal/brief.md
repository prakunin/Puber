# Brief: startup-settled-signal

Base 7bcbfd83. Analyst, 2026-11-21. Builds on `research.md`; the code references it gives for `StartupReveal`, `RevealGate`, `StartupSplashHost`, `MainActivity`, `MainVM`, `TvHomeSyncCoordinator`, `UpdatePromptVM`, `DetailsPrefetcher` and `HomeVM.publishSections` were re-read at base and hold.

kind: improvement. size: M. tracks: perf. Device verification: **yes** (startup, ViewModel state, networking; the goal is a runtime number).

Size reason: one goal (a quiet first seconds after the reveal), but it crosses startup, five consumers in VM, domain and `core/tvhome` layers, and Home ViewModel state, all risk areas in `.flow/roles/analyst.md`. The publish coalescing is independently shippable; it stays here because it serves the same measured goal and the owner filed it as one P1 item.

## Request
research.md: Request. Split out of `startup-splash-preload` (its `state.md` Decisions, Q1).

## Current state
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

## Scope
In: research.md option A (research.md: Options). A process-wide settled latch in `core/startup`; the five consumers await it at their current start point and keep their current owning scope; `StartupSyncDelay` removed. HomeVM publish requests become leading-edge conflated (first runs at once; requests arriving while a publish runs fold into one follow-up of the latest snapshot; no time window), keeping `publishMutex` and the epoch check.
Out: the `TvHomeSyncCoordinator` scope and coalesced TV home refresh (`cache-targeted-invalidation`); decode, JIT and main-thread I/O costs (`card-image-size-tier`, `release-startup-compilation`, `startup-main-thread-io`); real-TV measurement (`startup-first-input-on-tv`).

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

## Risks
| Risk | Guard |
|---|---|
| Latch fires early (`covered` false before `begin()`) or never (no activity start) | Explicit latch with a begun flag and a fallback bound; AC-1, AC-3 |
| Coalescing delays the hero publish and so the reveal | Leading-edge only, no window; AC-9, AC-11 |
| A folded publish request lost, leaving a row unshown | Follow-up reads the snapshot under `publishMutex`; AC-9 |
| Deferred work escapes its scope and runs after logout | Await in the current owning scope; AC-7 |
| Post-login Main loses the 5 s cushion for watch-state | Q2 slot gaps apply from Main start; AC-8 |
| Emulator noise hides the gain (parent AC-13/AC-15) | Count-based measures (publishes, cap reveals) beside `key_lat`; real-TV left to `startup-first-input-on-tv` |

## Open questions
| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | When is startup settled? | When the splash leaves composition after the fade, or at once for a restored activity; fallback 5 s after `begin()`. Once per process. | yes |
| Q2 | Order and gaps, and after login? | Prefetch gate opens at settled; then TV home refresh, update check, device info, watch-state sync, 500 ms apart (tunable constant). After login the same order counted from Main start. | yes |
| Q3 | What proves the gain? | AC-11 on the emulator: fewer publishes per start, no `revealed` or cap regression, `key_lat` p95 reported (not gated, emulator noise); real TV stays with `startup-first-input-on-tv`. | yes |
