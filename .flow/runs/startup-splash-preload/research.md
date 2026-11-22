# Research: startup-splash-preload

## Request

Cold-start sequencing and responsiveness (release builds on Android TV). Today the splash is idle time: LauncherScreen waits a fixed 500 ms (MIN_VISIBLE_MS), LauncherVM decrypts the access token through AndroidKeyStore on the main thread, and Home data requests start only after Main/Home are composed, i.e. after the splash is gone; the user then sees Home assembling for 1-3 s while D-pad input lags (key_lat up to ~1 s under load). Everything starts at once with no priority: 8 HomeVM section collectors each republish and recompose the whole Home (publishSections), MainVM.reportDeviceInformation, TvHomeSyncCoordinator.requestRefresh(immediate = true), UpdatePromptVM auto update check, image loads. SplashContent in MainActivity stays composed under App() forever (full-screen overdraw every frame). Goal: (P0) use the splash as loading time - drop the fixed dwell, move token check off main, start Home data (disk cache + network for hero/Hot and ContinueWatching) at process start, compose Main+Home under the splash and reveal it with a fade once the first visible content is ready (hero data, first row, focus claimed) with a hard cap (~2.5 s) so a bad network never hangs the splash; swallow D-pad keys while the splash covers the tree; remove SplashContent from the tree after reveal. (P1) a single "startup settled" signal that defers device info report, TV home sync, update check, details prefetch and the watch-state sync (instead of its 5 s heuristic) until after reveal, staggered; coalesce Home section publishes that arrive close together into one publish. Out of scope: baseline profile / R8 / post-update compilation (separate run). Measure before/after with telemetry startup marks (first_content), key_lat and frames on the emulator; owner only uses release builds, so measure a release-type build (devBenchmarkRelease or equivalent) as well. No physical TV without explicit authorization. Prior numbers: docs/superpowers/evidence/2026-10-15-telemetry-core-device.md, 2026-10-16-telemetry-frames-keys-memory-device.md.

## Understanding

The cold start runs strictly in series: wordmark, a fixed 500 ms wait, a synchronous token decrypt, then Main and Home are built and only then does Home ask for data, behind a domain reachability probe; once data arrives, eight collectors each republish the whole screen while device-info, TV-home sync, the update check and image loads compete for the same main thread. The owner wants the wordmark period to do that work instead: build Main and Home behind an opaque splash, start Home's data at process start, reveal with a fade when Home is ready to use (capped), and push the non-visible background work until after the reveal, with fewer Home publishes.

## Current state in code

Paths are relative to `app/src/main/java/com/kino/puber/` unless they start with `app/` or `docs/`.

**Splash and launch**
- `MainActivity.kt:65` composes `SplashContent()` first, then `App()` one frame later (`:67-72`, `markAppComposed`). The splash is never removed; `App()` paints an opaque `Surface` over it (`ui/feature/root/component/App.kt:157-161`), so it is overdraw for the life of the activity.
- `app/src/main/res/values/themes.xml:6` sets `windowBackground` to `splash_background`. No `androidx.core:core-splashscreen` dependency (`gradle/libs.versions.toml`).
- `ui/feature/root/component/LauncherScreen.kt:19,40-45`: `MIN_VISIBLE_MS = 500`, `delay` then `LauncherAction.SplashShown`; draws a second `SplashContent()`. The KDoc says the dwell exists so a warm start does not blink.
- `ui/feature/root/vm/LauncherVM.kt:35`: `getAccessToken()` runs inside `handleAction` on the main thread; then `router.newRootScreen(main())`, `newRootScreens(main, target)` for a pending content link, or `auth()`.
- `data/repository/CryptoPreferenceRepository.kt:74-85,107-122,134`: every read is SharedPreferences + Base64 + AES-GCM with an AndroidKeyStore key, `KeyStore.load` per call inside `synchronized(KEY_LOCK)`.
- `PuberApp.kt:70-92`: `onCreate` installs telemetry, starts Koin and calls `ApiDomainInteractor.initialize()` (reads the saved domain, i.e. one more keystore decrypt, on main). No app-level coroutine scope exists; `TvHomeSyncCoordinator` gets its own `CoroutineScope(SupervisorJob() + Dispatchers.IO)` (`PuberApp.kt:51`).
- `core/ui/navigation/component/TabComponent.kt:110,156-165`: Main first shows `LoadingTab` (full-screen spinner) until `MainVM.onStart` opens Home (`ui/feature/main/vm/MainVM.kt:51`); evidence logs `Launcher -> Main` then `LoadingTab -> Home` 120 ms later (`docs/superpowers/evidence/2026-10-16-telemetry-frames-keys-memory-device.md:257`).

**Home data path**
- `ui/feature/home/vm/HomeVM.kt:163-167`: `onStart` -> `loadHome()`; `:273` awaits `apiDomainInteractor.autoResolveWorkingDomain()` before any section collector starts (`:294`).
- `domain/interactor/api/ApiDomainInteractor.kt:117-129,209,219`: the probe is skipped only if `EndpointReachability` holds an answer from the last 15 min; that state is in memory (`data/api/network/EndpointReachability.kt:56-58`), so a cold start normally pays a network probe before even the disk cache is read (unverified whether another request marks it reachable first).
- `HomeVM.kt:298-323`: seven section flows plus collections, `TOTAL_SECTIONS = 8` (`:62`), each launched separately.
- `HomeVM.kt:325-336,461-514`: every `Cached.Value` calls `publishSections()`, which re-maps all loaded sections on `Dispatchers.Default` and publishes a full `HomeViewState.Content`; serialized by `publishMutex` (`:86`) and guarded by `catalogueEpoch` (`:99`). No conflation.
- `domain/interactor/home/HomeInteractor.kt:42-62` -> `data/cache/ContentCacheRepository.kt:55-90` -> `data/cache/CachedFeed.kt`: disk value emitted first, network behind it; the memory tier "doubles as the single-flight coordinator" (`CachedFeed.kt:59,290,491`), which is what a process-start warm-up would need HomeVM to join (join semantics for cold flows unverified).
- `HomeVM.kt:130-142`, `ui/feature/home/component/HomeScreenContent.kt:281,316-345`: `heroSettled` plus `ClaimHeroFocusEffect` (up to 5 frame attempts) decide the opening focus; this is the "focus claimed" readiness the request refers to.

**Startup background work**
- `MainVM.kt:52-54`: `reportDeviceInformation()`, `syncWatchState()` (waits `StartupSyncDelay = 5.seconds`, `:83,190`), `tvHomeSyncCoordinator.requestRefresh(immediate = true)` (`core/tvhome/TvHomeSyncCoordinator.kt:21-27`).
- `ui/feature/update/vm/UpdatePromptVM.kt:37-55`: the auto update check starts in `onStart` of a VM composed by `App.kt:135,179`, i.e. at the first App frame.
- `domain/interactor/prefetch/DetailsPrefetcher.kt:36-37,75-91`: focus-driven, 250 ms / 750 ms dwell; starts as soon as Home has focus.

**Focus and keys**
- `MainActivity.kt:151-154`: `dispatchKeyEvent` already wraps every key (telemetry probe); the natural place to drop keys while covered.
- `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:32-47`, `core/ui/navigation/component/FlowComponent.kt:89,159,666-680`, `App.kt:166`: `ContentFocusGate` / `screenFocusActive` (from update-dialog-focus-trap) close focus claims while the update prompt is open. Closing the gate also stops Home's launch focus claim, so it cannot double as "covered by splash" without suppressing the readiness signal.

**Telemetry**
- `core/telemetry/StartupMarks.kt:20-31`: one `startup` line: `proc_start`, `app_create`, `app_composed`, `first_content`; fallback after 60 s (`core/telemetry/TelemetryRuntime.kt:55,125`).
- `core/ui/PuberVM.kt:93-100`: `first_content` = first `StartupContentState` of any VM (`ui/feature/home/model/HomeViewState.kt:33`, also Auth, History, Bookmarks, ...). There is no reveal mark and no `reportFullyDrawn`.

**Builds and tests**
- `app/build.gradle.kts:184-221`: `benchmarkRelease` and `nonMinifiedRelease` are `release` (R8) with debug signing; `deploy` is non-minified.
- Tests: `app/src/test/kotlin/com/kino/puber/ui/feature/root/vm/LauncherVMTest.kt`, `.../home/vm/HomeVMTest.kt`, `.../home/vm/HomeVMIncrementalPublishTest.kt` (asserts per-section publishes; coalescing changes it), `.../main/vm/MainVMWatchStateSyncTest.kt`, `MainVMDeviceInfoTest.kt`, `MainVMRefreshLifecycleTest.kt`, `.../core/telemetry/StartupMarksTest.kt`; `baselineprofile/src/main/java/com/kino/puber/baselineprofile/StartupBenchmarks.kt`.

## Related work

| Item | Relation |
|------|----------|
| `telemetry-core` (done), `docs/superpowers/evidence/2026-10-15-telemetry-core-device.md:95-142,197-227` | Defined the `startup` line. Release emulator `first_content` 1487 / 1722 ms, debug 3210-3567 ms; release `am start -W` median 2297 ms under load. Baseline for this run. |
| `telemetry-frames-keys-memory` (done), evidence `2026-10-16-...:82-89,126,257` | `key_lat` 58-504 ms on emulator; release cold start 569 ms (TotalTime). Real-TV numbers were never taken (MANUAL M1, `:168`). |
| `update-dialog-focus-trap` (done, 3105e87e) | Added `screenFocusActive` / `ContentFocusGate` and the launch-race handling for the update prompt; deferring the update check moves that race and its device scenario. Deferred idea `update-gate-wiring-tests` covers the wiring this touches. |
| `favourites-launch-focus-ignores-rail` (idea) | Launch focus claims ignoring `LocalContentFocusActive`; same mechanism a splash cover would rely on. No overlap in scope. |
| `startup-benchmark-entry` (idea) | A compliant way to run `StartupBenchmarks`; would give TTID/TTFD numbers this run cannot get through `connectedAndroidTest`. Not a prerequisite. |
| `perf-deferred` (idea) | P5 R8/largeHeap is the out-of-scope baseline-profile/R8 work. No overlap. |
| `app-coroutine-scope` (idea, S) | One injected application scope; a process-start preload needs exactly this. Could be folded in. |
| Commits 1d52d9b8, 46af3266 | Introduced `publishMutex` and the epoch guard that a coalescing publish must keep. |
| Commits a8b74f38, 55f07079 | Telemetry startup wiring in `MainActivity`. |

Possible duplicate: none.

## External findings

| Finding | Source |
|---------|--------|
| `setKeepOnScreenCondition` is meant for "a small amount of data"; the condition "is evaluated before each frame is drawn, so keep it fast". Exit can be animated with `setOnExitAnimationListener`. | https://developer.android.com/develop/ui/views/launch/splash-screen (read 2026-11-21) |
| `core-splashscreen` latest stable 1.2.0 (2025-11-05). Not used by the project. | https://developer.android.com/jetpack/androidx/releases/core, https://mvnrepository.com/artifact/androidx.core/core-splashscreen (read 2026-11-21) |
| TTFD is reported only when the app calls `reportFullyDrawn`; call it once the screen is usable, after lazily loaded lists are populated. | https://developer.android.com/topic/performance/vitals/launch-time (read 2026-11-21) |

## Options

| Option | What | Trade-offs | Size |
|--------|------|------------|------|
| A. Everything in one run | P0 and P1 as written. | One measurement pass; but two shippable goals, touches startup, navigation, focus, telemetry schema, HomeVM and four background services at once; the largest regression surface in the app. | L |
| **B. P0 now, P1 as a follow-up run (recommended)** | This run: drop the dwell, token read and domain init off main, process-start warm-up of the domain probe plus Hot and ContinueWatching (joined by HomeVM through `CachedFeed` single-flight), splash as a top overlay in `MainActivity` over `App()` with Main+Home composed beneath, reveal on Home ready or cap, keys dropped in `dispatchKeyEvent` while covered, splash removed after the fade, a `revealed` startup mark. Follow-up `startup-settled-signal`: settled signal, staggered deferral, publish coalescing. | Delivers the visible win with one device story; P1 is then measured against a fixed P0 baseline. Background work still competes right after reveal until P1 lands. | M |
| C. Sequencing only, no compose-under-splash | Drop the dwell, token off main, warm-up at process start, coalesce publishes, defer background work until `first_content`; keep the current Launcher -> Main navigation and spinner. | Smallest change, no focus/overlay risk; the user still sees `LoadingTab` and Home assembling, so the stated P0 goal is only half met. | M |

## Risks

- **Focus under an overlay.** Home's hero claim must succeed while the splash covers it, yet D-pad must not reach it. Gating with `ContentFocusGate` would block the claim (`FocusOnLaunchRequester.kt:40-47`); key dropping in `MainActivity.dispatchKeyEvent` does not, but must still pass the key to the telemetry probe and must let Back through or not (open question).
- **Landing screens other than Home.** `LauncherVM.kt:36-52` can land on Auth or on Main plus a content-link target; the readiness signal must cover them or fall back to the cap, otherwise the splash waits the full cap on every unauthenticated start.
- **Warm start** (activity recreated, process alive): no dwell means the splash can disappear within a frame, which is what `MIN_VISIBLE_MS` was added for (`LauncherScreen.kt:31-34`).
- **Preload ownership.** A warm-up outside HomeVM must be cancelled or ignored on a domain switch (`HomeVM.kt:431-443`, `cacheGeneration`) and on logout (`App.kt:91-107`); a warm-up that HomeVM does not join doubles the requests.
- **Update prompt race.** The auto check today can open the prompt over a still-loading Home; with the splash on top it may open under the splash (keys swallowed) or the deferral changes the scenario `update-dialog-focus-trap` verified.
- **Telemetry meaning.** `first_content` will fire under the splash; comparisons with the October numbers need a separate `revealed` mark, or before/after will look better or worse for the wrong reason.
- **Measurement.** Release builds are R8-obfuscated (`err` frames unreadable, evidence `2026-10-16-...:126`); emulator `key_lat` is not TV `key_lat` (no real-TV baseline exists). Device work needs a lease and `install-preserve`; release-type APK is debug-signed (`benchmarkRelease`), so installing it over a dev debug APK is a signer change unless the same debug key is used (unverified for the emulator's current install).

## Open questions

| ID | Question | Recommended answer |
|----|----------|--------------------|
| Q1 | Ship P0 and P1 in one run, or split? | Split (option B): P0 here, P1 as `startup-settled-signal`. |
| Q2 | What is "ready" for the reveal? | Home `Content` with `heroSettled` and the hero focus claim resolved (claimed or refused); any other landing screen: its first `StartupContentState`; cap 2.5 s from activity `onCreate`, revealing whatever state is there. |
| Q3 | Which keys are dropped while covered? | All D-pad and OK keys; Back passes through (exit stays possible on a hung start). |
| Q4 | Warm start behaviour without the dwell? | No minimum dwell; same readiness rule, usually met within a frame, fade still applied. |
| Q5 | Add `core-splashscreen` or keep the Compose overlay? | Keep the existing Compose `SplashContent` overlay; no new dependency. |

## Sources

- Code: `MainActivity.kt`, `PuberApp.kt`, `ui/feature/root/component/{LauncherScreen,SplashContent,App}.kt`, `ui/feature/root/vm/LauncherVM.kt`, `data/repository/CryptoPreferenceRepository.kt`, `ui/feature/home/vm/HomeVM.kt`, `ui/feature/home/component/HomeScreenContent.kt`, `domain/interactor/home/HomeInteractor.kt`, `data/cache/{ContentCacheRepository,CachedFeed}.kt`, `domain/interactor/api/ApiDomainInteractor.kt`, `data/api/network/EndpointReachability.kt`, `ui/feature/main/vm/MainVM.kt`, `core/tvhome/TvHomeSyncCoordinator.kt`, `ui/feature/update/vm/UpdatePromptVM.kt`, `domain/interactor/prefetch/DetailsPrefetcher.kt`, `core/ui/navigation/component/{FlowComponent,TabComponent}.kt`, `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt`, `core/telemetry/{StartupMarks,StartupContentState,TelemetryRuntime}.kt`, `core/ui/PuberVM.kt`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/res/values/themes.xml`.
- Docs: `AGENTS.md`, `docs/architecture/navigation.md`, `docs/superpowers/evidence/2026-10-15-telemetry-core-device.md`, `docs/superpowers/evidence/2026-10-16-telemetry-frames-keys-memory-device.md`.
- Runs: `telemetry-core`, `telemetry-frames-keys-memory`, `update-dialog-focus-trap`, `favourites-launch-focus-ignores-rail`, `startup-benchmark-entry`, `perf-deferred`, `app-coroutine-scope`.
- Commits: 1d52d9b8, 46af3266, a8b74f38, 55f07079, 3105e87e.
- URLs: listed under External findings.
