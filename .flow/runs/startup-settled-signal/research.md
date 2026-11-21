# Research: startup-settled-signal

## Request

After startup-splash-preload shipped (merged at 8c6fc926): a single "startup settled" signal (after splash reveal) that defers MainVM.reportDeviceInformation, TvHomeSyncCoordinator.requestRefresh(immediate = true), the UpdatePromptVM auto check, DetailsPrefetcher and the watch-state sync (replacing its 5 s StartupSyncDelay), staggered; coalesce Home section publishes (HomeVM.publishSections) that arrive close together into one publish. Split out of startup-splash-preload (P1).

## Understanding

The splash now covers the app until Home is ready, but the moment it lifts, five pieces of background work that started at Main/App composition (or are about to) compete with the first post-reveal frames and the user's first keys: the device-info report, the TV home channel refresh, the update check, focus-driven details prefetch and (5 s later, by a fixed guess) the watch-state history walk. The owner wants one process-wide "startup has settled" point, reached after the reveal, that these wait for and then start one after another; and Home should stop re-mapping and republishing the whole screen once per arriving section when several arrive within a short window.

## Current state in code

Paths relative to `app/src/main/java/com/kino/puber/`.

**Reveal machinery (from startup-splash-preload)**
- `core/startup/StartupReveal.kt:10-51`: process singleton over `RevealGate`; `covered: StateFlow<Boolean>`, `begin()`, `markReady()`, `revealNow()`, `onCap()`. `onActivityCreated(restored)` reveals at once for a restored activity (`:35`); an opening update prompt reveals at once (`:45-47`).
- `core/startup/RevealGate.kt:85-86,144-150`: `covered` starts `false` and becomes `true` only at `begin()`; once uncovered it latches (`revealedOnce`) until the next `begin()`. So "`covered == false`" is also true before `begin()` (e.g. in `Application.onCreate`); it is not on its own a "revealed" signal. Cap 2.5 s (`:76`).
- `MainActivity.kt:76-88`: `begin()` in `onCreate`, cap effect, `markRevealed()` telemetry mark when `covered` turns false (fade start).
- `ui/feature/root/component/StartupSplashHost.kt:18,27-38`: `REVEAL_FADE_MS = 250` fade; `splashGone` drops the splash from composition when alpha reaches 0. No callback leaves this composable.
- `core/startup/AppStartup.kt:25-68`: process-start domain init and Hot/ContinueWatching warm-up on `AppCoroutineScope` (`core/coroutine/AppCoroutineScope.kt:14-17`, bound in `domain/di/modules.kt:80`).

**Work the request defers**

| Work | Where it starts today | Trigger and scope |
|------|------------------------|-------------------|
| Device-info report | `ui/feature/main/vm/MainVM.kt:52,95-101` | `onStart`, `viewModelScope`; one network call |
| Watch-state sync | `MainVM.kt:53,79-89`; `StartupSyncDelay = 5.seconds` `:190` | `onStart` and `MainAction.Resumed` (`ui/feature/main/component/MainScreenComponent.kt:78`); first run waits 5 s once (`startupSyncDelayServed`); interactor drops overlapping non-forced runs (`domain/interactor/watchstate/WatchStateSyncInteractor.kt:96`) |
| TV home refresh | `MainVM.kt:54` -> `core/tvhome/TvHomeSyncCoordinator.kt:21-27` | `immediate = true` skips the 2 s debounce; own `CoroutineScope(SupervisorJob() + Dispatchers.IO)` (`PuberApp.kt:54`); each call cancels the previous pending one |
| Update auto check | `ui/feature/update/vm/UpdatePromptVM.kt:37-57` | `onStart` of a VM composed by `UpdatePromptHost` (`ui/feature/root/component/App.kt:135-152,180-185`), i.e. at the first App frame, under the splash; an `Available` result opens the prompt and forces the reveal (`StartupReveal.kt:45-47`) |
| Details prefetch | `domain/interactor/prefetch/DetailsPrefetcher.kt:33-41,75-91` | focus-driven, 250/750 ms dwell; Home's hero claim lands under the splash, so warms can start before reveal. `warm()` already gates on `foreground.awaitForeground()` (`core/lifecycle/AppForegroundState.kt:35-37`), the pattern a settled gate can copy |

**Home publishes**
- `ui/feature/home/vm/HomeVM.kt:467-525` `publishSections()`: under `publishMutex` (`:80-90`) snapshots all sections, maps on `presentationDispatcher`, drops the result if `catalogueEpoch` moved (`:93-104`), publishes a full `HomeViewState.Content`.
- Callers: each `Cached.Value` of seven section collectors (`:341`) and collections (`:379`), `markHeroSettled` (`:368`), card display changes (`:178`). A cold start with disk cache plus network produces up to ~16 publishes (disk then network per section; unverified count on device).
- Reveal depends on publishes: `heroSettled` reaches the screen only through a publish, and `HomeScreenContent.kt:224-230` sends `LaunchFocusSettled` -> `HomeVM.kt:202` -> `StartupReveal.markReady`. Coalescing that delays the Hot publish delays the reveal.

**Tests around it** (`app/src/test/kotlin/com/kino/puber/`)
- `ui/feature/main/vm/MainVMWatchStateSyncTest.kt` (five tests advance `StartupSyncDelay`, `:66,103,117`), `MainVMDeviceInfoTest.kt`, `MainVMRefreshLifecycleTest.kt`.
- `ui/feature/update/vm/UpdatePromptVMTest.kt`; `core/tvhome/TvHomeSyncCoordinatorTest.kt`; `domain/interactor/prefetch/DetailsPrefetcherTest.kt`, `domain/di/DetailsPrefetcherWiringTest.kt`.
- `ui/feature/home/vm/HomeVMIncrementalPublishTest.kt` (`theFirstSectionToArrivePublishesContentWithoutWaitingForTheRest` `:112`: the first section must publish at once, so coalescing has to be leading-edge), `HomeVMTest.kt`.
- `core/startup/{RevealGateTest,StartupRevealTest,AppStartupTest}.kt`.

## Related work

| Item | Relation |
|------|----------|
| `startup-splash-preload` (done, 8c6fc926) | Parent; P1 split here (its `state.md` Decisions, Q1). Design A-Q2 left `TvHomeSyncCoordinator` scope for this run (`design-architect.md:71`). qa Round 1c/2: post-reveal key latency comes from a decode burst and shader compile, not only background work (`qa-report.md`, `state.md` Log). AC-12 ("prompt shows at once without waiting for Home") was never device-checked (`judge-1.md:63`). |
| `startup-first-input-on-tv` (idea) | Real-TV measurement of first input after reveal; names this run as related. The settled signal's benefit can only be confirmed there. |
| `cache-targeted-invalidation` (idea, B4 of `cache-layer-rework`) | Includes "coalesced TV home refresh"; ordered after this run (`cache-layer-rework/state.md:30`). Touches the same `TvHomeSyncCoordinator`. |
| `update-gate-wiring-tests` (idea) | Tests of the update-prompt focus-gate wiring that moving the auto check affects. |
| `card-image-size-tier`, `release-startup-compilation`, `startup-main-thread-io` (ideas) | Other post-reveal cost sources (decode, JIT, main-thread I/O); not covered here. |
| Commits d051c873 (introduced `StartupSyncDelay`), 1d52d9b8, 46af3266, f306699e (publish mutex, epoch guard, mapping off main) | Constraints a coalesced publish must keep. |

Possible duplicate: none.

## External findings

External findings: off (local research only; `.flow/config.yaml` sets no `research.web`).

## Options

| Option | What | Trade-offs | Size |
|--------|------|------------|------|
| **A. Settled latch plus awaiting consumers (recommended)** | New `core/startup` latch (e.g. `StartupSettled.await(slot)`) completed once per process after the splash is gone (or at once when the activity was restored / no splash began), with a hard upper bound; each consumer awaits its slot (fixed order, fixed gaps) where it starts today: MainVM device info and watch-state (replacing `StartupSyncDelay`), TV home `immediate` refresh, UpdatePromptVM auto check, `DetailsPrefetcher.warm` (next to `awaitForeground`). HomeVM: publish requests go through a conflated trigger (first publish immediate, requests arriving while one runs or within a short window fold into one), keeping `publishMutex` and the epoch check. | Small, local edits; work stays owned and cancelled by its current scope (VM cleared, logout). Order lives in one table of slots. Five consumers plus Home publishing touched; MainVM tests change from virtual-time delay to a fake latch. | M |
| B. Central startup work queue | A process-level scheduler: consumers register tasks, the queue runs them in priority order after settled. | Order and spacing in one place, easy to measure; but VM-owned work moves into app scope, so cancellation on VM clear/logout/domain switch must be re-implemented; larger contract change. | M (upper end) |
| C. Reveal only, no stagger | Consumers await `covered` turning false after `begin()`; no slots; coalescing as in A. | Smallest; but everything still starts in one burst during the fade and the first post-reveal frames, which is where qa measured the latency. | S |

## Risks

- **Early or never firing.** `covered` is `false` before `begin()` (`RevealGate.kt:85`), so a signal derived from it fires in `Application.onCreate`; a signal that waits for `begin()` never fires if the process has no activity start. Needs an explicit latch with a timeout.
- **Reveal held by coalescing.** The hero publish feeds `LaunchFocusSettled` (`HomeScreenContent.kt:224-230`); a trailing window adds its length to every reveal and can push starts onto the 2.5 s cap.
- **Publish ordering.** The coalesced publish must still read the freshest snapshot under `publishMutex` and skip on an epoch change (`HomeVM.kt:80-104`); a dropped trailing request leaves a row unpublished.
- **Post-login start.** Main also starts after Auth (no splash); the latch is already settled, so watch-state and the rest start with Home's cold first load, which the 5 s delay used to avoid.
- **Update prompt.** Deferring the auto check changes when the prompt appears (after Home is usable instead of possibly under the splash) and the focus-gate scenario of `update-dialog-focus-trap`; `StartupReveal.onUpdatePromptOpenChanged` becomes reachable mostly from the manual check.
- **Measurement.** Benefit is post-reveal key latency and frames; emulator (swiftshader) noise dominated the parent run's AC-13/AC-15. Device verification is needed (startup, ViewModel state, networking); Room/signer state of the shared emulator must be checked before install.

## Open questions

| ID | Question | Recommended answer |
|----|----------|--------------------|
| Q1 | When is startup "settled"? | When the splash has left composition (fade end), or at once if no splash covered this activity (restored start); hard fallback 5 s after `begin()` (above the 2.5 s cap plus fade). Process-wide, fires once. |
| Q2 | Order and spacing of the deferred work? | Details prefetch gate opens at settled (user-driven); then TV home refresh, update check, device info, watch-state sync, each about 500 ms after the previous (tuned on device; values unverified). |
| Q3 | Main started after login (latch already settled): any delay? | No extra delay; the stagger order still applies, counted from Main start. |
| Q4 | Coalescing rule for Home publishes? | Leading-edge: the first request publishes at once; requests arriving while a publish runs fold into one follow-up publish of the latest snapshot. No fixed time window, so the reveal is not delayed. |
| Q5 | Acceptance measure? | Emulator devBenchmarkRelease A/B against master: publish count per cold start (log), `key_lat` p95 in the first 3 s after `revealed`, and no start lost to the cap; real-TV confirmation stays with `startup-first-input-on-tv`. |

## Sources

- Code: `MainActivity.kt`, `PuberApp.kt`, `core/startup/{StartupReveal,RevealGate,AppStartup,StartupKeyFilter}.kt`, `core/coroutine/AppCoroutineScope.kt`, `core/lifecycle/AppForegroundState.kt`, `ui/feature/root/component/{App,StartupSplashHost}.kt`, `ui/feature/main/vm/MainVM.kt`, `ui/feature/main/component/MainScreenComponent.kt`, `ui/feature/update/vm/UpdatePromptVM.kt`, `core/tvhome/TvHomeSyncCoordinator.kt`, `domain/interactor/prefetch/DetailsPrefetcher.kt`, `domain/interactor/watchstate/WatchStateSyncInteractor.kt`, `domain/di/modules.kt`, `ui/feature/home/vm/HomeVM.kt`, `ui/feature/home/component/HomeScreenContent.kt`, `app/src/main/AndroidManifest.xml`; tests listed above.
- Docs: `AGENTS.md`, `docs/architecture/viewmodel.md`, `docs/superpowers/specs/2026-11-21-startup-splash-preload.md` (listed, not re-read).
- Runs: `startup-splash-preload` (research, brief, design-architect, perf-audit-2026-11-21, judge-1, state), `startup-first-input-on-tv`, `cache-layer-rework`, `cache-targeted-invalidation`, `update-gate-wiring-tests`, `card-image-size-tier`, `startup-main-thread-io`.
- Commits: 8c6fc926, 0b5c0669, d051c873, 1d52d9b8, 46af3266, f306699e.
