# Use the splash as loading time

Status: approved
Date: 2026-11-21
Slug: startup-splash-preload
Kind: improvement
Size: M
Tracks: ui, perf

## Problem / Why

Use the splash as loading time on cold start (research.md: Request). This run is P0 only: no fixed dwell, token and domain init off main, Home data warm-up at process start, Main+Home composed under the splash and revealed with a fade when Home is ready or at a cap, D-pad dropped while covered, splash removed after reveal, a separate `revealed` startup mark. Measured on the emulator in devDebug and a release-type build.

### Owner decisions applied

| ID | Decision |
|---|---|
| Q1 | Split: P0 here, P1 (settled signal, deferral of background work, publish coalescing) in `startup-settled-signal`. |
| Q2 | Ready = Home `Content` with `heroSettled` and the hero focus claim resolved (claimed or refused); any other landing screen: its first `StartupContentState`; cap 2.5 s from activity `onCreate`, revealing whatever state is there. |
| Q3 | While covered all D-pad and OK keys are dropped; Back passes through. |
| Q4 | No minimum dwell on warm start; same readiness rule, fade still applied. |
| Q5 | Keep the Compose `SplashContent` overlay; no `core-splashscreen` dependency. |

### Current state

Paths relative to `app/src/main/java/com/kino/puber/`. Facts are in research.md: Current state in code; the ones the change rests on:

| Fact | Where |
|---|---|
| Splash composed first and never removed; `App()` paints an opaque `Surface` over it | `MainActivity.kt:65-72`, `ui/feature/root/component/App.kt:157-161` |
| Fixed 500 ms dwell and a second `SplashContent` in the launcher | `ui/feature/root/component/LauncherScreen.kt:19`, `:40-45` |
| Token decrypt on main in `openStartScreen` | `ui/feature/root/vm/LauncherVM.kt:35` |
| Saved-domain decrypt on main in `Application.onCreate` | `PuberApp.kt:91` |
| Home awaits the domain probe before any section collector | `ui/feature/home/vm/HomeVM.kt:273`, `domain/interactor/api/ApiDomainInteractor.kt:117-129` |
| Hot and ContinueWatching flows; `CachedFeed` memory tier is the single-flight coordinator | `domain/interactor/home/HomeInteractor.kt:42-62`, `data/cache/CachedFeed.kt:59`, `:290` |
| Main shows `LoadingTab` spinner until `MainVM.onStart` opens Home | `core/ui/navigation/component/TabComponent.kt:110`, `:156` |
| Hero focus claim, up to 5 frame attempts, reports claimed/refused | `ui/feature/home/component/HomeScreenContent.kt:319-345` |
| Every key already passes through one override with the telemetry probe | `MainActivity.kt:51-54` |
| `ContentFocusGate` closed also blocks launch focus claims, so it cannot mean "covered" | `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:32-47` |
| One `startup` line, emitted at `first_content` or a 60 s fallback | `core/telemetry/StartupMarks.kt:20-31`, `core/telemetry/TelemetryRuntime.kt:55` |
| `benchmarkRelease` is R8 release with debug signing; dev flavor id suffix `.stage` | `app/build.gradle.kts:216-219`, `:227` |

### Risks (analyst)

| Risk | Guard |
|---|---|
| Hero claim fails under an overlay that has no focusables, or the key filter drops keys after reveal | keys dropped in `MainActivity.dispatchKeyEvent` only, not via `ContentFocusGate`; AC-4, AC-7 |
| Warm-up not joined by HomeVM doubles requests (cold `CachedFeed` join semantics unverified, research.md: Current state) | AC-3 |
| Warm-up outlives a domain switch or logout (`HomeVM.kt:431-443`, `App.kt:91-107`) | AC-11 |
| Reveal holder keeps stale "ready" across activity recreation or a second cold start in the same process | reset per activity `onCreate`; AC-10 |
| Update prompt opens under the splash with keys dropped | AC-12 |
| `first_content` now fires under the splash; old and new numbers not comparable | `revealed` mark; AC-9, AC-13 |
| devBenchmarkRelease over the installed devDebug on the emulator: same `.stage` id and debug key expected, but signer and versionCode of the current install are unverified | Q3 |
| Emulator key_lat is not TV key_lat; no real-TV baseline | numbers labelled emulator in evidence |

## Acceptance criteria

- AC-1. Given a cold or warm start, When the launcher runs, Then no fixed delay precedes navigation (`MIN_VISIBLE_MS` and its `delay` gone) and the launcher draws no second `SplashContent` (code check; `LauncherVMTest`).
- AC-2. Given a cold start, When `PuberApp.onCreate` and `LauncherVM` read the saved domain and the access token, Then neither `CryptoPreferenceRepository` read runs on the main thread, and the start screen is still chosen as today: Main, Main plus content-link target, or Auth (`LauncherVMTest` with an injected dispatcher; test for domain init).
- AC-3. Given an authenticated cold start, When the process starts, Then the domain resolve and the Hot and ContinueWatching loads begin before Main composes, and HomeVM joins them: each of those endpoints is requested at most once per cold start (unit test with counting fakes; device: request count from the debug network log).
- AC-4. Given an authenticated cold start, When the splash fades out, Then Home is on screen with hero content and focus on the hero (or on the first row when the hero refused), and neither the `LoadingTab` spinner nor `HomeViewState.Loading` is visible after the fade (device screenrecord, frame by frame).
- AC-5. Given Home never becomes ready (network unreachable), When 2.5 s have passed since activity `onCreate`, Then the splash fades out and shows whatever Home state exists, and that state stays operable (unit test of the reveal rule with a fake clock; device with emulator network off).
- AC-6. Given a start that lands on Auth or on a content-link target, When that screen publishes its first `StartupContentState`, Then the splash reveals without waiting for the cap (unit test of the reveal rule; device for the content-link case via `am start` with a content intent).
- AC-7. Given the splash covers the tree, When D-pad or OK is pressed, Then the key does not reach the tree (focus after reveal is the AC-4 target) and is still seen by `KeyProbe`; When Back is pressed, Then it is dispatched as today (unit test of the key filter; device observation).
- AC-8. Given the reveal fade has finished, Then no `SplashContent` remains in the composition and the window shows no splash layer under `App()` (compose UI test by test tag; device: `dumpsys gfxinfo` frames compared with before).
- AC-9. Given a cold start, Then the `startup` line carries a new `revealed` mark (ms since process start, or absent if the fallback fires first) while `first_content` keeps its meaning, and exactly one line is still emitted (`StartupMarksTest`, encoder test).
- AC-10. Given a warm start (activity recreated, process alive), When it starts, Then there is no blank or black frame and the content fades in (device screenrecord) (owner judges the look).
- AC-11. Given a domain switch or a logout happens after the warm-up started, When HomeVM loads, Then no row from the previous catalogue or account is shown (`cacheGeneration` check; unit test).
- AC-12. Given the update prompt becomes `Available` while the splash covers the tree, When the splash reveals, Then focus is on the prompt's Update button and Back dismisses it, as in `update-dialog-focus-trap` AC-1 and AC-4 (instrumented test with the overlay host; device if a newer release is available).
- AC-13. Given 5 cold starts each on the emulator for devDebug and devBenchmarkRelease, before (base a22c03bc) and after, Then the evidence file reports `first_content`, `revealed`, key_lat for keys in the first 3 s after reveal, and frames, and meets the target in Q1.
- AC-14. The full check passes in the worktree.

## Out of scope

In: research.md option B (research.md: Options). Warm-up owner lives at application level (`app-coroutine-scope` idea, see Q2). Reveal state is a process-level holder written by the landing screen and read by `MainActivity`.
Out: P1 items; baseline profile, R8 rules, post-update compilation; `reportFullyDrawn`/TTFD via `StartupBenchmarks` (`startup-benchmark-entry`).

## Design

### Architect

Paths are relative to `app/src/main/java/com/kino/puber/` unless they start with `app/`. ACs: `brief.md#acceptance-criteria`. Fade duration and easing come from the ux-designer delta.

#### Decisions

| Question | Decision | Why |
|---|---|---|
| Who owns process-start work | New `core/startup/AppStartup` (Koin single) with one injected `AppCoroutineScope` (`SupervisorJob + Dispatchers.Default` + a logging `CoroutineExceptionHandler`); `PuberApp.initDi` calls `AppStartup.start()` instead of `initialize()` at `PuberApp.kt:91` | Brief Q2 folds `app-coroutine-scope` in; one owner for domain init and warm-up |
| Domain init off main | `AppStartup.start()` runs `ApiDomainInteractor.initialize()` (`ApiDomainInteractor.kt:53-60`) on `Dispatchers.IO` into a `CompletableDeferred<Unit>` completed in `finally`; `suspend fun awaitDomainInitialized()` | KinoPubConfig is read per request (`data/api/KinoPubApiClient.kt:1023`), so only ordering matters; the manifest declares no receiver or service that could request before a screen does |
| Token read off main | `LauncherVM` routes from `onStart` in `launch {}`: awaits `awaitDomainInitialized()`, then reads `getAccessToken()` in `withContext(ioDispatcher)`; `ioDispatcher` is a constructor parameter defaulting to `Dispatchers.IO` | Gating the launcher on domain init means no screen issues a request before the saved domain is applied (AC-2); the token is read fresh each start so a warm start after logout is not routed on a stale answer |
| Dwell and action | `MIN_VISIBLE_MS`, its `delay`, the launcher's `SplashContent` (`LauncherScreen.kt:19`, `:40-45`) and `LauncherAction.SplashShown` (`ui/feature/root/model/LauncherAction.kt:6`) are deleted; `LauncherScreen.Content` draws nothing | AC-1; the splash overlay in `MainActivity` already covers the launcher |
| Warm-up | `AppStartup` after domain init: if a token exists, `autoResolveWorkingDomain()`; on `Success`, collect `observeHotItems()` and `observeWatchingItems()` (`HomeInteractor.kt:42-62`) to completion in parallel, errors logged and dropped. It builds its own `HomeInteractor` from globals (Home's is screen-scoped, `ui/feature/home/component/HomeScreen.kt:31`) | Joining happens in the global `ContentCacheRepository`: a later collector on the same key either reads the fresh memory value (`CachedFeed.kt:505-512`) or joins the in-flight flight (`CachedFeed.kt:498` -> `TypedTtlCache.kt:84-99`), so HomeVM needs no change to join |
| Domain probe single-flight | `autoResolveWorkingDomain` (`ApiDomainInteractor.kt:117-145`) body runs under a `Mutex`; the second caller then answers from `isKnownReachable` (`:125`) | Without it HomeVM (`HomeVM.kt:273`) probes again while the warm-up probe is in flight (AC-3) |
| Reveal state | New `core/startup/RevealGate` (pure: injected clock, `capMs = 2_500`, `begin()`, `markReady()`, `onCap()`, `remainingCapMs()`, `covered: StateFlow<Boolean>`) held by process object `StartupReveal`; `MainActivity.onCreate` calls `StartupReveal.begin()` | Brief scope: a process-level holder written by the landing screen and read by `MainActivity`; reset per `onCreate` covers warm starts |
| Generic readiness | `PuberVM.updateViewState` (`core/ui/PuberVM.kt:86-90`) calls `StartupReveal.markReady()` when the new state `is StartupContentState` and `revealsOnContentState` (new `protected open val`, default `true`); independent of `Telemetry.isEnabled` | Q2 "any other landing screen: its first StartupContentState"; today that check runs only while recording (`PuberVM.kt:89`, `:99`) |
| Home readiness | `HomeVM` overrides `revealsOnContentState = false`; `HomeContent` sends new `HomeAction.LaunchFocusSettled` once `state.heroSettled && (state.heroItems.isEmpty() || heroFocusResolved)` (state at `ui/feature/home/component/HomeScreenContent.kt:191-203`); `HomeVM.handleAction` (`HomeVM.kt:176-199`) forwards it to a `revealSignal: () -> Unit` constructor parameter (default `StartupReveal::markReady`) | Q2 for Home; keeps the composable pure (state + `onAction`); a repeat on later returns to Home is a no-op |
| Content-link targets | `DetailsScreenState.Content` (`ui/feature/details/model/DetailsScreenState.kt:14`) and `PlayerViewState.Content` (`ui/feature/player/model/PlayerViewState.kt:13`) implement `StartupContentState` | Neither is marked today, so AC-6 would wait for the cap; this also gives those starts a real `first_content` instead of the 60 s fallback |
| Update prompt under the splash | `App`'s `onOpenChange` (`ui/feature/root/component/App.kt:179`) also calls `StartupReveal.markReady()` when open | An open prompt closes the content focus gate, so Home's hero claim is disabled (`HomeScreenContent.kt:197`) and Home would only reveal at the cap (AC-12) |
| Overlay and removal | `MainActivity` composes `App()` first and `SplashContent` on top (test tag `startup_splash`), `graphicsLayer` alpha animated 1 -> 0 when `covered` turns false, then a `splashGone` flag drops it from composition; the one-frame `appComposed` deferral (`MainActivity.kt:67-72`) stays. A `LaunchedEffect` waits `remainingCapMs()` then `onCap()` | AC-4, AC-5, AC-8, AC-10; Q5 keeps the Compose overlay |
| Key filter | `dispatchKeyEvent` (`MainActivity.kt:51-54`) passes `superDispatch = { if (StartupReveal.isCovered && isDroppedWhileCovered(keyCode)) true else super.dispatchKeyEvent(event) }` into `dispatchWithProbe`; `isDroppedWhileCovered` is a pure top-level function: DPAD up/down/left/right/center, ENTER, NUMPAD_ENTER | AC-7, Q3: the probe still sees the key, Back passes, and `ContentFocusGate` stays untouched so launch claims still run (`FocusOnLaunchRequester.kt:39-48`) |
| `revealed` mark | `StartupMarks.markRevealed()` keeps the first value; the line is emitted once both `first_content` and `revealed` are reached, or at the fallback with what exists (`StartupMarks.kt:24-31`); `TelemetryEvent.Startup` gains `revealedMs: Long?` (`TelemetryEvent.kt:130-138`), encoder writes `revealed` (`TelemetryEncoder.kt:67-72`). `MainActivity` calls it when `covered` turns false (fade start) | AC-9; `first_content` now fires under the splash and keeps its meaning |

#### Changes by layer

| Layer | File | Change |
|---|---|---|
| API / repository | `domain/interactor/api/ApiDomainInteractor.kt:117` | `Mutex` around `autoResolveWorkingDomain` |
| API / repository | `core/startup/AppStartup.kt` (new), `core/coroutine/AppCoroutineScope.kt` (new) | Domain-init deferred, token check and warm-up of Hot and ContinueWatching on the app scope |
| API / repository | `domain/di/modules.kt:31` or `PuberApp.kt:45-52` | `single { AppCoroutineScope() }`, `single { AppStartup(...) }` building its `HomeInteractor` from `get()` |
| API / repository | `PuberApp.kt:91` | `get<AppStartup>().start()` replaces `get<ApiDomainInteractor>().initialize()` |
| ViewModel | `ui/feature/root/vm/LauncherVM.kt:24-52` | Routing moves to `onStart` + `launch`; awaits domain init; token read on `ioDispatcher`; same three outcomes |
| ViewModel | `core/ui/PuberVM.kt:86-90` | `revealsOnContentState` and the unconditional `StartupReveal.markReady()` |
| ViewModel | `ui/feature/home/vm/HomeVM.kt:47-58`, `:176-199` | `revealSignal` parameter, `revealsOnContentState = false`, `LaunchFocusSettled` branch |
| ViewModel | `ui/feature/home/model/HomeAction.kt:5`; `ui/feature/root/model/LauncherAction.kt` | Add `LaunchFocusSettled`; delete `LauncherAction` |
| ViewModel | `ui/feature/details/model/DetailsScreenState.kt:14`, `ui/feature/player/model/PlayerViewState.kt:13` | Implement `StartupContentState` |
| ViewModel | `core/telemetry/StartupMarks.kt:16-31`, `TelemetryEvent.kt:130-138`, `TelemetryEncoder.kt:67-72` | `revealed` mark and joint emission |
| Screen and DI | `ui/feature/root/component/LauncherScreen.kt:19-46` | `viewModel { LauncherVM(get(), get(), get(), get()) }` (the dispatcher default is not resolvable by `viewModelOf`); dwell and `SplashContent` gone |
| Screen and DI | `ui/feature/home/component/HomeScreen.kt:34-46` | Nothing: `revealSignal` keeps its default |
| Screen and DI | `core/startup/RevealGate.kt`, `core/startup/StartupReveal.kt` (new) | Reveal rule and process holder with a test reset |
| Compose content | `MainActivity.kt:51-75` | Overlay order, fade, removal, cap effect, `begin()`, key filter, `markRevealed` |
| Compose content | `ui/feature/home/component/HomeScreenContent.kt:191-203` | `LaunchedEffect` sending `LaunchFocusSettled` |
| Compose content | `ui/feature/root/component/App.kt:179` | `markReady()` when the prompt opens |
| Resources | N/A | No strings or dimensions; fade timing is a code constant from the ux delta |

#### Alternatives rejected

- Gate keys with `ContentFocusGate`: it also disables launch focus claims (`FocusOnLaunchRequester.kt:39-48`).
- Warm-up inside `HomeVM` or `MainVM`: they exist only after Main composes, which AC-3 rules out.
- A Home-specific readiness API on the generic path (checking `HomeViewState.Content.heroSettled` in `PuberVM`): the focus outcome lives in composition, not in state.
- Awaiting domain init inside `KinoPubApiClient`: wider change for the same guarantee the launcher gate gives.
- `core-splashscreen`: excluded by Q5.

#### Risks

| Risk | Where | Guard |
|---|---|---|
| Focus: hero claim does not run under the splash, or keys stay dropped after reveal | `HomeScreenContent.kt:194-203`, `MainActivity.kt:51` | Filter reads `covered` only; claim gated only by focus state, which dropped keys leave untouched (`userDroveFocus` stays false); AC-4, AC-7 |
| Reveal never fires before the cap on a landing screen whose VM survives activity recreation (config change: no new `updateViewState`) | `PuberVM.kt:86`, `RevealGate` | Cap; Home re-sends `LaunchFocusSettled` because `heroFocusResolved` is `remember`ed |
| Double request when a list record is incomplete: `force = incomplete` (`data/cache/ContentCacheRepository.kt:63-66`) makes each collector `reload`, which displaces the other's flight (`TypedTtlCache.kt:111-123`) | warm-up + `HomeVM.kt:307-321` | Edge case only; AC-3 device count shows it |
| Domain init fails (keystore) and the launcher waits forever | `AppStartup`, `LauncherVM` | Deferred completed in `finally`; `initialize` failure logged, stock domain stays |
| Logout or domain switch while the warm-up loads | `CachedFeed.kt:188-243`, `HomeVM.kt:153`, `:454-458` | Warm-up holds no rows; the store generation drops its late write and HomeVM clears rows on a moved generation; AC-11 |
| Process started without the activity still runs the warm-up | `AppStartup.start` | Only the FileProvider exists today (`app/src/main/AndroidManifest.xml:66-74`); cost is two cached requests |
| Main-thread contention: Home composition and image loads during the fade | `MainActivity.kt` overlay | Measured by AC-13 frames; P1 run defers the rest |
| Global `StartupReveal` state leaks between unit tests | `PuberVMTelemetryTest`, `HomeVMTest` | `resetForTests()` as `Telemetry` has (`core/telemetry/Telemetry.kt:55-62`) |
| New startup classes are outside the baseline profile in release | `:baselineprofile` | Out of scope; release numbers in AC-13 carry it |

#### Open questions

1. Clear the window background (`app/src/main/res/values/themes.xml:6`) after reveal to drop its overdraw? Recommended: no, not asked by AC-8 and risks a black flash on transitions. Blocks: no.
2. Move `TvHomeSyncCoordinator` (`PuberApp.kt:51`) onto `AppCoroutineScope`? Recommended: no in this run; it runs on `Dispatchers.IO` and belongs to `startup-settled-signal`. Blocks: no.

### ux-designer

Base a22c03bc. Paths relative to `app/src/main/java/com/kino/puber/`.

#### Governing stand

No `docs/design/` stand governs this change: the stands are `catalogue`, `content` and `player-controls`; none covers the splash (`ui/feature/root/component/SplashContent.kt`) or Home (`ui/feature/home/component/HomeScreenContent.kt`). By the role's rule the conductor runs `designing-a-screen` with the owner before planning, unless the owner waives it.

The change alters no size, colour or position on either screen; its only new visual value is the reveal fade below. Recommendation: waive the stand session and accept the fade on the TV under brief.md AC-10, as the catalogue stand did for `PanelMotion` (`docs/design/catalogue/README.md`, Motion). Open question for the owner.

#### Numbers that apply

Unchanged; the overlay reuses `SplashContent` as it is.

| Element | Value | Source |
|---|---|---|
| Splash background | `#FF000000` (`splash_background`), also `windowBackground` | `res/values/colors.xml:6`, `res/values/themes.xml:6` |
| App surface under it | `OledBackground` = `Color.Black` | `core/ui/uikit/theme/Color.kt:30`, `PuberTheme.kt:49-50` |
| Wordmark | 320 dp wide, 640:169, 18 dp gap to signature | `SplashContent.kt` |
| Signature | 22 sp, letter spacing 4 sp, `#B3FFFFFF` | `SplashContent.kt`, `colors.xml:7` |
| Version | 9 sp, 32 dp from bottom | `SplashContent.kt` |

Both backgrounds are pure black, so the fade changes only the wordmark, signature and version against Home; no background tint shift may appear.

##### New, proposed (open until the owner accepts on the TV, AC-10)

| Part | Value |
|---|---|
| Reveal | whole `SplashContent` alpha 1 to 0 via `graphicsLayer`, no scale, no shift |
| Duration | 250 ms |
| Curve | `FastOutLinearInEasing`: the wordmark holds then leaves, leaving no low-alpha ghost of it over the hero |
| Warm start | same fade, no minimum dwell (Q4) |
| After the fade | `SplashContent` leaves composition; the window background stays the only layer under `App()` (AC-8) |

#### Must not be seen

| Must not appear after the fade starts | Guard |
|---|---|
| `LoadingTab` spinner (`core/ui/navigation/component/TabComponent.kt:156-165`) | readiness requires Home `Content`; AC-4 |
| `HomeViewState.Loading` spinner (`HomeScreenContent.kt:80`, `:112`) | same; only the 2.5 s cap may reveal it (AC-5) |
| Home with rows but an empty carousel, or rows reshuffling as sections land | readiness waits for `heroSettled` |
| Focus landing in a row and then jumping to the hero, which scrolls the list | readiness waits for the hero claim outcome (`HomeScreenContent.kt:319-345`) |
| Hero auto-scroll page turn during the fade | the 5 s timer (`HeroCarousel.kt:52`) runs only while the hero lacks focus; with the claim settled before reveal it does not run |
| A black or blank frame between splash and Home, cold or warm | the overlay is drawn from the first frame and removed only after alpha reaches 0 (AC-10) |
| A second wordmark jump (launcher's own `SplashContent`) | the launcher copy is removed (AC-1); under the overlay the Launcher to Main switch is not visible |

Allowed after the fade, record in evidence: the hero and card skeleton shimmer (`SkeletonAsyncImage`, Coil `crossfade(true)` in `HeroCarousel.kt:229`) when a poster is not decoded yet, because images are outside the Q2 readiness rule. If the owner sees it as a flash on the TV, it becomes a follow-up, not a reason to widen readiness here.

#### Focus

No new focusable element. The overlay must not be focusable and must not hold a `FocusRequester`; keys are dropped in `MainActivity.dispatchKeyEvent`, not through focus (brief.md Risks).

| State | Focus | Visibility |
|---|---|---|
| Covered | wherever the Home claim put it; invisible | n/a |
| From fade start | hero, or the first row when the hero refused | hero: as today (`HeroItem` has no focused border, `HeroCarousel.kt:210-213`); row card: 3 dp `colorScheme.border` ring (`docs/design/content/README.md:228-233`) |
| Update prompt open | its Update button (AC-12) | as in update-dialog-focus-trap |
| Auth or content-link landing | that screen's own launch focus | unchanged |

Open for the owner: D-pad opens at fade start (proposed: the ring is already visible through the fading wordmark, so the first press is not lost), or after the fade ends (adds 250 ms of dead input).

#### Shared constants

| Constant | Rule |
|---|---|
| `PanelMotion` (`core/ui/uikit/component/details/VideoItemGridDetails.kt:108`) | do not reuse or retune; the reveal gets its own constant next to `SplashContent` |
| `SplashContent` | stays foundation-only (no `PuberTheme`, DI or VM), so it still draws in the first frame; the fade wraps it from outside |
| `FullScreenProgressIndicator` (`core/ui/uikit/component/Loading.kt:11`) | drawn by many screens; hide the startup spinner by readiness, never by changing this component |
| `HERO_FOCUS_REQUEST_ATTEMPTS`, `HERO_AUTO_SCROLL_DELAY_MS` | unchanged |
| Coil `crossfade(false)` default (`PuberApp.kt:107`) and per-request `crossfade(true)` | unchanged |

#### Review measurements (for the visual review)

Device screenrecord at 1080p, frame by frame: fade length 250 ms +/- one frame; no frame with a spinner, an empty carousel or a row ring after alpha starts falling; the first frame after the fade has no wordmark pixels; the background stays `#000000` throughout.

### Architect delta 1 (fix round after verify round 1c)

Base: spec `docs/superpowers/specs/2026-11-21-startup-splash-preload.md` (Design, Architect). Problem and measurements: `qa-report.md` Round 1b (trace) and Round 1c (fixed anchor). Paths are relative to `app/src/main/java/com/kino/puber/`. Coil facts are from the coil-core-android 3.6.3 bytecode in `~/.gradle-agents/caches` (`javap`).

#### Decisions

| Question | Decision | Why |
|---|---|---|
| Where the post-reveal burst comes from | The requests the composed screen itself issues: Home is composed and laid out under the splash (`ui/feature/root/component/StartupSplashHost.kt:30`), so hero (`core/ui/uikit/component/HeroCarousel.kt:225-232`) and cards (`core/ui/uikit/component/moviesList/VideoItemHorizontal.kt:96-103`) already start their loads before the reveal; the reveal rule just does not wait for them | Round 1b: 3.5 s of decode CPU falls between `revealed` and key 1 |
| Fix | Readiness gains a second input: no image request that has resolved its size is in flight. `covered = begun && !forced && !revealedOnce && !(ready && !imagesBusy)`; the cap (2.5 s from `onCreate`, `core/startup/RevealGate.kt:7`) is unchanged | Option (c) generalised: the landing screen's own visible requests (hero backdrop, first-row cards, LazyRow and pager prefetch) are decoded while covered; image shaders compile under the splash with them |
| How "images in flight" is known | New `core/startup/StartupImageActivity` (a Coil `EventListener`): a request joins a synchronized identity set at `resolveSizeEnd`, leaves it at `onSuccess`/`onError`/`onCancel`; it calls `onBusyChanged(Boolean)` only on empty/non-empty edges. Installed once with `.eventListener(...)` in `PuberApp.newImageLoader` (`PuberApp.kt:100-125`) | One listener sees every request of the singleton loader; no composable changes. Counting from `resolveSizeEnd` skips requests that never get measured (a size that never resolves would otherwise hold the splash to the cap) |
| Once revealed, stays revealed | `RevealGate` latches `revealedOnce` on the first uncover; only `begin()` (`RevealGate.kt:21`) clears it, together with `ready` and `forced`; `imagesBusy` is process state and survives `begin()` | Without the latch, a card load after the reveal would put the splash back and drop keys (`MainActivity.kt:62`) |
| Update prompt opening under the splash | `App.kt:185` calls new `StartupReveal.revealNow()` (sets `forced`) instead of `markReady()` | The prompt needs the screen now (AC-12); it must not wait for Home's images |
| Thread safety | `RevealGate` recomputes `covered` under `synchronized`; Coil callbacks may arrive off main (`onCancel`) | `MutableStateFlow` writes from two threads would otherwise race the latch |
| Prefetch, option (a) | Not added | The same requests with the same keys and sizes already run under the splash; a second request path duplicates size derivation, and Coil reuses a sampled bitmap only when it is at least the requested size (`MemoryCacheService.isCacheValueValidForSize`; size enters the key only with transformations), so a mismatch decodes twice |
| Decode parallelism, option (b) | Not in this delta; fallback (Open question 2) | `bitmapFactoryMaxParallelism` (default 4, one semaphore shared by `StaticImageDecoder` and `BitmapFactoryDecoder`) at 2 halves the decode rate under the splash, which is exactly the work the new rule waits for |
| Smaller server renditions | Not available | `Posters` has `small`, `medium`, `big`, `wide` only (`data/api/models/Models.kt:198-203`); 16:9 cards and the hero need `wide` (`core/ui/model/VideoItemUIMapper.kt:32`, `:58-64`) |

#### Changes by layer

| Layer | File | Change |
|---|---|---|
| API / repository | `PuberApp.kt:100-125` | `.eventListener(StartupReveal.imageActivity)` on the `ImageLoader.Builder` |
| ViewModel | N/A | `HomeVM.kt:202` and `core/ui/PuberVM.kt:94` keep calling `markReady()`; its meaning becomes "content ready" |
| Screen and DI | `core/startup/StartupImageActivity.kt` (new) | `EventListener` subclass with the in-flight set and `onBusyChanged: (Boolean) -> Unit` |
| Screen and DI | `core/startup/RevealGate.kt:21-43` | Inputs `ready`, `forced`, `imagesBusy`, latch `revealedOnce`; `onImagesBusy(Boolean)`, `revealNow()`; `onCap()` sets `forced`; `reset()` clears all |
| Screen and DI | `core/startup/StartupReveal.kt:7-23` | `val imageActivity = StartupImageActivity(gate::onImagesBusy)`, `fun revealNow()` |
| Compose content | `ui/feature/root/component/App.kt:185` | `revealNow()` replaces `markReady()` |
| Compose content | `MainActivity.kt:76-84` | None: `begin()` and the cap effect stay |
| Resources | N/A | No strings or dimensions |

#### Alternatives rejected

- Separate prefetch of hero and first-row images during the splash: duplicates requests the composition already makes (Decisions, option (a)).
- Wait for the hero image only: leaves the 15 card decodes (3.2 s CPU, Round 1b) after the reveal.
- Per-composable "visible image" tracking through a `CompositionLocal`: touches `HeroCarousel`, both card types and `SkeletonAsyncImage` for the same signal one listener gives.
- Background-priority decoder dispatcher (`decoderCoroutineContext`): same trade-off as option (b), plus hardware-bitmap upload on a low-priority thread.
- `crossfade(true)` removal (`VideoItemHorizontal.kt:100`, `HeroCarousel.kt:229`): visual change; memory-cache hits after the reveal skip the crossfade anyway.

#### Risks

| Risk | Where | Guard |
|---|---|---|
| Splash covers again after the reveal when a new image load starts, dropping D-pad keys | `RevealGate.kt`, `MainActivity.kt:62` | `revealedOnce` latch; `RevealGateTest.imagesBusyAfterRevealDoNotCoverAgain` |
| Reveal moves later: a cold disk cache, new items or a slow image CDN hold the splash to the cap, and AC-13's `revealed` median (<= 2502 ms) can fail | `RevealGate`, `StartupImageActivity` | Cap unchanged; AC-13 and AC-15 measured together; Open question 1 |
| Unbalanced callbacks (a request with `resolveSizeEnd` and no terminal event) keep `imagesBusy` true for the process: every later warm start waits for the cap | `StartupImageActivity` | Identity set, not a counter, so a double terminal event cannot go negative; `StartupImageActivityTest`; cap |
| Cross-thread writes race the gate (`onCancel` off main against `begin()` on main) | `RevealGate` | `synchronized` recompute; `covered` stays a `StateFlow` read by `MainActivity.kt:80` |
| More keys dropped under the splash at the fixed anchor (2.0 and 2.5 s may fall before the reveal) | `MainActivity.kt:62` | By design (Q3); AC-15 counts them separately |
| Post-reveal cost not removed: hero page changes on D-pad decode a 4K source each (`HeroCarousel.kt:125`), JIT and stale baseline profile remain | Coil, ART | AC-15 measures; Open questions 2 and 3 |
| `StartupReveal` global state leaks between unit tests | `RevealGateTest`, `PuberVMRevealTest` | `resetForTests()` also clears `imagesBusy` and empties the in-flight set |

#### AC delta (gate 1)

| AC | Change |
|---|---|
| Q2 (owner decision) | Ready additionally requires that no image request of the loader is in flight (size resolved, no success, error or cancel yet); the cap and the Home and other-landing rules stay; an update prompt opening reveals at once |
| AC-4 | Add: and the hero backdrop and the card images on screen are drawn, not skeletons, in the first frame after the fade, unless the cap fired (device screenrecord, frame by frame) |
| AC-6 | "publishes its first `StartupContentState`" becomes "publishes its first `StartupContentState` and its image requests have settled" |
| AC-12 | Add: the prompt opening reveals the splash without waiting for content or images |
| AC-15 (new) | Given devBenchmarkRelease on the emulator, 3 interleaved base (a22c03bc) and head sets of 4 cold starts per build, host load <= 9, the six keys 22, 21, 22, 21, 22, 21 at 2.0, 2.5, 3.0, 3.5, 4.0 and 4.5 s after `am start` (qa-report Round 1c, fixed anchor), Then over head keys sent after `revealed` the `key_lat` p95 is <= the base p95 over all six keys of the same session, and the share of those head keys at >= 199 ms is <= the base share; the evidence lists head keys dropped under the splash and the `ImageDecoder#decodeBitmap` slices between `revealed` and the first delivered key from one trace |

#### Tests to add

| Test | Level | AC |
|---|---|---|
| `RevealGateTest.readyWaitsForImagesInFlight` | unit | AC-4, AC-6 |
| `RevealGateTest.imagesSettlingAfterReadyUncovers` | unit | AC-4, AC-6 |
| `RevealGateTest.capUncoversWhileImagesInFlight` | unit | AC-5 |
| `RevealGateTest.revealNowIgnoresContentAndImages` | unit | AC-12 |
| `RevealGateTest.imagesBusyAfterRevealDoNotCoverAgain` | unit | AC-4, AC-7 |
| `RevealGateTest.beginKeepsImagesBusyFromBeforeIt` | unit | AC-10 |
| `StartupImageActivityTest.requestIsInFlightFromResolvedSizeUntilSuccess` | unit (`mockk` `ImageRequest`) | AC-4 |
| `StartupImageActivityTest.errorAndCancelEndARequest` | unit | AC-4, AC-5 |
| `StartupImageActivityTest.cancelWithoutResolvedSizeLeavesIdle` | unit | AC-5 |
| `StartupImageActivityTest.busyIsReportedOnlyOnEdges` | unit | AC-4 |
| Existing `RevealGateTest.markReady_uncoversImmediately` | unit | AC-6: still passes with no images in flight |
| Fixed-anchor A/B and one trace (AC-15) | device | AC-15, AC-13 |

#### Open questions

1. Accept the Q2 delta (reveal waits for in-flight images within the unchanged cap) and keep AC-13's `revealed` target (<= 2502 ms median) unchanged alongside AC-15? Recommended: yes to both; if both cannot hold, report and ask. Blocks: yes.
2. If AC-15 fails with images drained, add `bitmapFactoryMaxParallelism(2)` in `PuberApp.newImageLoader` and measure again? Recommended: yes, as the next fix round. Blocks: no.
3. Record an idea run for a card-size image tier (downscaled bitmaps on disk, so a cold start never decodes 4K `wide` sources into 533x300 cards)? Recommended: yes. Blocks: no.

## Test design

Base a22c03bc. Inputs: `brief.md` (ACs, risks), `design-architect.md`, `design-ux-designer.md`. Levels from `.flow/roles/test-designer.md`: `unit` (JUnit 5, `MainDispatcherExtension`, virtual time), `instrumented` (`make DEVICE=<serial> itest TESTS=<fqcn>`), `device` (manual, evidence file). Test paths: `app/src/test/kotlin/com/kino/puber/` and `app/src/androidTest/kotlin/com/kino/puber/`. Every test that touches `StartupReveal` calls `StartupReveal.resetForTests()` in `@AfterEach`, as `PuberVMTelemetryTest` does with `Telemetry`.

#### Seams the tests need

| Seam | Used by |
|---|---|
| `AppStartup` takes its scope and an `ioDispatcher` as constructor parameters (defaults allowed) | `AppStartupTest` |
| The overlay, fade and removal live in a composable taking `covered` and a content slot (called `StartupSplashHost` below), which `MainActivity` calls | `StartupSplashHostTest`, `UpdatePromptOverlayFocusTest` |
| `isDroppedWhileCovered(keyCode)` is a top-level pure function (design-architect.md, Key filter) | `StartupKeyFilterTest` |

#### Risk profile

| # | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Hero claim does not run under the overlay, or the overlay takes focus | 2 | 3 | 6 | `StartupSplashHostTest.contentUnderTheSplashTakesLaunchFocus`, `HomeLaunchFocusSettledTest`, M1 |
| R2 | Keys stay dropped after reveal, or Back is dropped while covered | 2 | 3 | 6 | `StartupKeyFilterTest`, `RevealGateTest`, M1, M4 |
| R3 | HomeVM probes the domain again while the warm-up probe is in flight | 3 | 2 | 6 | `ApiDomainInteractorTest.autoResolve_concurrentCallersShareOneProbe` |
| R4 | Warm-up not joined by Home: Hot or ContinueWatching requested twice | 2 | 2 | 4 | `AppStartupTest.warmUpAndHomeShareOneRequestPerEndpoint`, M7 |
| R5 | Warm-up outlives a domain switch or logout and Home shows old rows | 1 | 3 | 3 | `AppStartupTest.aWarmUpLandingAfterAStoreWipeIsNotServedToHome` |
| R6 | Reveal holder keeps "ready" from a previous activity: warm start reveals Loading | 2 | 3 | 6 | `RevealGateTest.begin_coversAgainAfterAPreviousReveal`, M5 |
| R7 | Cap never fires: splash hangs on a dead network | 2 | 3 | 6 | `RevealGateTest.cap_*`, M2 |
| R8 | Early reveal: Home's generic `Content` reveals before hero settled, or a non-landing state reveals | 2 | 2 | 4 | `HomeVMTest.contentState_keepsTheSplashUntilLaunchFocusSettled`, `PuberVMRevealTest` |
| R9 | Content-link target or Auth waits the full cap (states not `StartupContentState`) | 3 | 2 | 6 | `PuberVMRevealTest.contentLinkTargetStatesRevealTheSplash`, M3 |
| R10 | Update prompt opens under the splash; reveal waits for the cap or focus misses Update | 2 | 3 | 6 | `UpdatePromptOverlayFocusTest.updatePromptOpenedUnderTheSplashHasFocusOnUpdateAfterReveal`, M8 |
| R11 | Domain init throws and the launcher waits forever | 1 | 3 | 3 | `AppStartupTest.awaitDomainInitialized_completesWhenInitializeThrows` |
| R12 | `startup` line lost or duplicated by the joint emission | 2 | 2 | 4 | `StartupMarksTest` rows under AC-9 |
| R13 | Splash stays composed after the fade (overdraw kept) | 2 | 2 | 4 | `StartupSplashHostTest.splashLeavesCompositionAfterTheFade`, M6 |
| R14 | devBenchmarkRelease install changes signer or downgrades | 2 | 3 | 6 | M9 pre-install check (brief.md Q3); stop and ask on mismatch |
| R15 | Emulator key_lat not representative of TV | 3 | 1 | 3 | Accepted: numbers labelled emulator in evidence |
| R16 | `StartupReveal` global state leaks between unit tests | 2 | 1 | 2 | Accepted: `resetForTests()` in `@AfterEach` |

#### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `LauncherVMTest.start_opensMainScreenWithoutASplashAction` | Fails if routing still waits for `SplashShown` or a dwell |
| AC-1 | unit | P1 | `LauncherVMTest.startTwice_opensStartScreenOnlyOnce` | Fails if a re-run `onStart` (recreation) routes twice |
| AC-1 | device | P1 | M1 step 3 | Fails if the launcher's second wordmark is drawn (visible jump) |
| AC-2 | unit | P0 red | `LauncherVMTest.start_readsTheAccessTokenOnTheIoDispatcher` | Fake preferences record the calling thread; fails if `getAccessToken` runs on Main |
| AC-2 | unit | P0 red | `LauncherVMTest.start_waitsForDomainInitBeforeReadingTheToken` | Domain deferred held open: no token read, no route; fails if the gate is removed |
| AC-2 | unit | P0 guard | `LauncherVMTest.start_opensMainScreenForAuthenticatedSession` | Rewritten from `splashShown_*`; fails if Main is no longer chosen with a token |
| AC-2 | unit | P0 guard | `LauncherVMTest.start_opensAuthScreenWithoutAccessToken` | Fails if Auth is no longer chosen without a token |
| AC-2 | unit | P0 guard | `LauncherVMTest.start_opensMainAndTheContentLinkTarget_whenALinkIsPending` | Fails if `newRootScreens(main, target)` is lost |
| AC-2 | unit | P0 red | `AppStartupTest.start_appliesTheSavedDomainOffTheMainThread` | Fails if `ApiDomainInteractor.initialize()` runs on the caller's thread |
| AC-2 | unit | P1 | `AppStartupTest.awaitDomainInitialized_completesWhenInitializeThrows` | Fails if the deferred is not completed in `finally` (R11) |
| AC-3 | unit | P0 red | `ApiDomainInteractorTest.autoResolve_concurrentCallersShareOneProbe` | Two concurrent callers, gated probe: one probe; fails without the `Mutex` |
| AC-3 | unit | P0 red | `AppStartupTest.warmUpStartsHotAndWatchingWithoutAnyScreen` | Mocked `KinoPubApiClient`: Hot and watching endpoints requested after `start()` alone; fails if the warm-up is not launched |
| AC-3 | unit | P0 red | `AppStartupTest.warmUpAndHomeShareOneRequestPerEndpoint` | Real `ContentCacheRepository` over `FakePayloadStore`, a second `HomeInteractor` (as Home's screen scope) collecting while the gated request is in flight: each endpoint called once; fails if the warm-up bypasses the shared cache |
| AC-3 | unit | P1 | `AppStartupTest.warmUpRequestsNothingWithoutAnAccessToken` | Fails if a logged-out start fetches Home data |
| AC-3 | device | P1 | M7 | Request count per endpoint per cold start |
| AC-4 | unit | P0 red | `HomeVMTest.launchFocusSettled_revealsTheSplash` | `revealSignal` counter called once; fails if the action is not forwarded |
| AC-4 | unit | P0 red | `HomeVMTest.contentState_keepsTheSplashUntilLaunchFocusSettled` | After `begin()`, Home `Content` published: `StartupReveal` still covered; fails if `revealsOnContentState = false` is dropped |
| AC-4 | instrumented | P0 red | `HomeLaunchFocusSettledTest.heroClaimSendsLaunchFocusSettledWithHeroFocused` | `HomeScreenContent` with settled hero: action sent and hero focused (pattern of `HomeFocusTraversalTest`); fails if the effect is missing |
| AC-4 | instrumented | P1 | `HomeLaunchFocusSettledTest.emptyHeroSendsLaunchFocusSettledWithFirstRowFocused` | Fails if a refused or empty hero never reports settled |
| AC-4 | instrumented | P1 | `HomeLaunchFocusSettledTest.unsettledHeroSendsNothing` | `heroSettled = false`: no action; fails if reveal can show an empty carousel |
| AC-4 | instrumented | P0 red | `StartupSplashHostTest.contentUnderTheSplashTakesLaunchFocus` | Covered host over a `rememberFocusRequesterOnLaunch` box: box focused; fails if the overlay is focusable or gates focus (R1) |
| AC-4 | device | P0 | M1 | Frame-by-frame: no `LoadingTab` or `HomeViewState.Loading` after fade start |
| AC-5 | unit | P0 red | `RevealGateTest.remainingCapMs_countsFromBegin` | Fake clock: 2 500 at `begin()`, 1 500 after 1 000 ms; fails if the cap is measured from elsewhere |
| AC-5 | unit | P0 red | `RevealGateTest.cap_uncoversWithoutReady` | `onCap()` with no `markReady()`: `covered` false; fails if the cap path is missing |
| AC-5 | device | P0 | M2 | Network off: reveal near 2.5 s, state operable |
| AC-6 | unit | P0 red | `PuberVMRevealTest.firstStartupContentStateRevealsTheSplash` | Fails if the generic path does not call `markReady()` |
| AC-6 | unit | P0 red | `PuberVMRevealTest.revealDoesNotDependOnTelemetryRecording` | Recorder disabled: still reveals; fails if `markReady()` sits behind `Telemetry.isEnabled` |
| AC-6 | unit | P0 red | `PuberVMRevealTest.loadingErrorAndOverlayStatesKeepTheSplash` | Fails if a non-content state reveals (R8) |
| AC-6 | unit | P0 red | `PuberVMRevealTest.contentLinkTargetStatesRevealTheSplash` | `DetailsScreenState.Content`, `PlayerViewState.Content` are `StartupContentState`; fails if either is unmarked (R9) |
| AC-6 | unit | P0 guard | `PuberVMTelemetryTest.theRealLandingStatesAreMarkedAndErrorLoadingAndOverlayStatesAreNot` | Auth `Content` stays marked |
| AC-6 | device | P1 | M3 | Content link reveals before the cap |
| AC-7 | unit | P0 red | `StartupKeyFilterTest.dpadAndOkAreDroppedWhileCovered` | Up, down, left, right, center, ENTER, NUMPAD_ENTER return true |
| AC-7 | unit | P0 red | `StartupKeyFilterTest.backAndOtherKeysPass` | BACK, MENU, media keys return false; fails if Back is swallowed |
| AC-7 | unit | P0 red | `RevealGateTest.markReady_uncoversImmediately` | Fails if keys stay dropped after ready (R2) |
| AC-7 | unit | P1 | `DispatchWithProbeTest.aKeyDroppedBySuperIsStillProbed` | Super returning true without dispatching: probe still called with `handled = true` |
| AC-7 | device | P0 | M1 step 2, M4 | D-pad while covered changes nothing; Back works |
| AC-8 | instrumented | P0 red | `StartupSplashHostTest.splashLeavesCompositionAfterTheFade` | `covered` false, clock advanced 250 ms + 1 frame: no node with tag `startup_splash`; fails if the flag is never set |
| AC-8 | instrumented | P1 | `StartupSplashHostTest.splashStaysWhileCovered` | Tag present while covered, before any reveal |
| AC-8 | device | P1 | M6 | gfxinfo compared with baseline |
| AC-9 | unit | P0 red | `StartupMarksTest.lineWaitsForRevealedAndCarriesBothMarks` | First content then revealed: one line with both; fails if the line is emitted at `first_content` only |
| AC-9 | unit | P0 red | `StartupMarksTest.oneLineWhenRevealedComesBeforeFirstContent` | Fails if order matters or two lines are emitted |
| AC-9 | unit | P0 red | `StartupMarksTest.fallbackLineHasNoRevealedWhenItNeverCame` | Fails if the fallback drops or invents `revealed` |
| AC-9 | unit | P1 | `StartupMarksTest.revealedKeepsTheFirstValue` | Warm-start reveal cannot overwrite |
| AC-9 | unit | P0 guard | `StartupMarksTest.fallbackLineAfterSixtySecondsWithMarksSoFar` | One line at fallback stays |
| AC-9 | unit | P0 red | `TelemetryEncoderTest.startupWritesRevealed` | Fails if the encoder omits `revealed` |
| AC-9 | unit | P0 guard | `TelemetryEncoderTest.startupOmitsMarksNotReached` | Null `revealedMs` written as absent |
| AC-9 | device | P1 | M10 | Line on device carries `revealed` |
| AC-10 | unit | P0 red | `RevealGateTest.begin_coversAgainAfterAPreviousReveal` | Fails if a recreated activity starts uncovered (R6) |
| AC-10 | instrumented | P1 | `StartupSplashHostTest.splashIsDrawnInTheFirstFrame` | `autoAdvance = false`: tag present at frame 0; fails if the splash waits for the content |
| AC-10 | device | P0 | M5 | Owner judges the look |
| AC-11 | unit | P0 red | `AppStartupTest.aWarmUpLandingAfterAStoreWipeIsNotServedToHome` | Gated warm-up, store wiped, gate opened: the later Home collector gets the post-wipe answer; fails if the warm-up writes outside `CachedFeed` generations |
| AC-11 | unit | P0 guard | `HomeVMIncrementalPublishTest.aCacheWipeFromAnotherScreenDropsTheRowsFromThePreviousCatalogue` | Generation guard unchanged |
| AC-11 | unit | P0 guard | `CachedFeedTest.aLoaderThatLandsAfterAWipeCannotRefillTheMemoryTier` | Store guard unchanged |
| AC-12 | instrumented | P0 red | `UpdatePromptOverlayFocusTest.updatePromptOpenedUnderTheSplashHasFocusOnUpdateAfterReveal` | `StartupSplashHost` covering prompt and content, prompt `Available` while covered, then uncovered: focus on Update, Back sends dismiss |
| AC-12 | device | P2 | M8 | Only if a newer release exists |
| AC-13 | device | P0 | Perf track below | Target met |
| AC-14 | command | P0 | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` in the worktree | Exit 0 |

`LauncherVMTest.start_keepsSplashOnScreenUntilItsAnimationHasPlayed` asserts the removed dwell and is deleted; the other `splashShown_*` tests are rewritten into the rows above.

#### Bugs

N/A: improvement; red rows above are the failing-first tests.

#### Manual checks

Emulator, lease via `tools/emulator-resource-lock.sh`, worktree APK via `tools/android-apk-install-preserve`, `adb -s <serial>` throughout. Cold start = `am force-stop` then `am start -n <pkg>/com.kino.puber.MainActivity`.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | 1. `screenrecord` running, cold start authenticated. 2. During the splash send `input keyevent DPAD_DOWN` x3 and `DPAD_CENTER`. 3. Wait for reveal, step frames | Splash fades about 250 ms into Home with hero content; focus on hero (first row if hero refused); the step-2 keys moved nothing | `LoadingTab` or Home spinner after fade start; empty carousel; focus jump row-to-hero; second wordmark; a detail screen opened by the dropped OK |
| M2 | Emulator network off (`svc wifi disable`, `svc data disable`), cold start, then D-pad around, network back on | Reveal about 2.5 s after start; visible state (error or cached rows) responds to D-pad | Splash past 3 s; dead input after reveal |
| M3 | Cold start with a content intent (`am start -a android.intent.action.VIEW -d <content uri>`) | Details target shown, splash gone before 2.5 s | Waiting the full cap; Home flashing first |
| M4 | Cold start, press `BACK` during the splash | Back handled as today (exit confirmation or app to background) | Back swallowed |
| M5 | App on Home, `am start` HOME, relaunch (warm, process alive); also rotate-equivalent recreation via `am start` after `HOME` | Content fades in, owner judges look | Black or blank frame; Home spinner revealed |
| M6 | After reveal, `dumpsys gfxinfo <pkg> reset`, idle 5 s on Home, `dumpsys gfxinfo <pkg>` | Frame stats not worse than baseline.md | Splash node in layout inspector / UI dump after fade |
| M7 | Cold start with debug network log, count requests per endpoint until reveal plus 5 s | Domain probe, Hot (movies, series) and watching each at most once | Any of them twice |
| M8 | If a newer release exists: cold start, prompt appears | After reveal focus on Update; Back dismisses | Prompt hidden under the splash with keys dropped; reveal at the cap |
| M9 | Before installing devBenchmarkRelease: compare signer (`apksigner verify --print-certs`) and versionCode (`dumpsys package`) with the installed package | Same signer, versionCode not lower | Installing on mismatch: stop and ask |
| M10 | Export telemetry after a cold start | One `startup` line with `first_content` and `revealed` | Two lines; `revealed` missing on a normal start |

#### Perf track

| Item | Value |
|---|---|
| Baseline | `/mnt/data/projects/PetProjets/Puber/.flow/runs/startup-splash-preload/baseline.md` (qa-device, base a22c03bc) |
| Devices and builds | Same emulator, devDebug and devBenchmarkRelease, 5 cold starts each, before and after |
| Metric 1 | Startup: before `first_content`, after `revealed` (and `first_content`), ms since process start, from the `startup` line |
| Metric 2 | `key_lat` of a fixed script: 6 `DPAD_RIGHT`/`DPAD_LEFT` at 500 ms spacing starting at the anchor (before: `first_content`; after: `revealed`), keys within the first 3 s |
| Metric 3 | Frames: `frame` telemetry lines (janky count, p95) for the first 5 s after the anchor |
| Pass (devBenchmarkRelease) | median after `revealed` <= median before `first_content` + 300 ms; `key_lat` p95 after < before |
| devDebug | Reported, no threshold |

#### Baseline (qa-device, mode baseline)

Mode baseline, track perf. Worktree /home/user1/orca/workspaces/Puber/startup-splash-preload, no code changes. Round 1, 2026-11-21.

##### Device and builds

- Device: emulator-5554 (AVD puber_tv_36, API 36 TV, software GPU, logged in), leased and released. The emulator was not running at
  the start; it was started with `-no-window -no-audio -no-boot-anim -no-snapshot-save -gpu swiftshader_indirect`. App data and login were intact.
- Host load (1-minute load average, 8 cores): 6.6-9.5 during the measured runs, so the host was loaded and absolute numbers are inflated
  and noisy. Compare only against a re-measurement on the same emulator under similar load.
- devDebug: `app-dev-debug.apk` sha256 ea6b5176...09d86, versionName 1.12.3-dev, installed with install-preserve (outcome installed,
  compatible_replace, destructive_action false), on-device base.apk sha256 equal.
- devBenchmarkRelease (R8, debug-signed): `app-dev-benchmarkRelease.apk` sha256 6cabdc4b...1ffeb. M9 check passed before install: signer
  6b29181257cb...a83e and versionCode 2090000000 equal to the installed package, no signer change, no downgrade; install-preserve outcome
  installed, compatible_replace, on-device sha256 equal. It is left installed.
- (A first combined `assembleDevDebug assembleDevBenchmarkRelease` call failed in 6m53s; separate calls succeeded. Not investigated, not a product finding.)
- Telemetry was enabled for the measurement by writing `enabled_at_wall_ms` to the app's `telemetry_preferences` (run-as on the debug build); it expires after 7 days and was left on.
  Telemetry-on adds probe overhead to both builds equally. Nothing was uninstalled, cleared or downgraded.

##### Method

Cold start = `am force-stop`, 3 s, `am start -W -n com.kino.puber.stage/com.kino.puber.MainActivity`. 8 cold starts per build, the first dropped, 7 reported.
Marks (ms since process start) come from the telemetry `startup` line: `app_composed`, `first_content` (no `revealed` exists at base). `Home ready` is the
`ready` Home Content `ms` (since Home start). `TotalTime` is `am start -W`.

**key_lat script (test-design.md, Perf track, Metric 2):** after `first_content` (anchor: the `startup` line appears in the session file, polled every 0.1 s
in one `adb shell` script), 6 key events at 500 ms spacing: `input keyevent 22, 21, 22, 21, 22, 21` (DPAD_RIGHT/LEFT alternating), all inside the first 3 s after the anchor.
No keys were sent before the anchor; an earlier measurement with a different script was not made. `key_lat` is the telemetry `key_lat` event, which the app
writes only when latency exceeds 50 ms (`SLOW_MS`), so a key without a line is at most 50 ms. Frames: `dumpsys gfxinfo <pkg> reset` at the anchor, dump 3 s of keys plus 2 s later (about 5 s window).

##### Raw runs

Columns: run, load before, TotalTime ms, app_composed ms, first_content ms, Home ready ms, key_lat ms (>50 only, of 6 keys), frames, janky, frame time p50/p90/p95/p99 ms.

##### devDebug
| Run | Load | TotalTime | app_composed | first_content | Home ready | key_lat | Frames | Janky | p50/p90/p95/p99 |
|---|---|---|---|---|---|---|---|---|---|
| 1 (dropped) | 9.47 | 2291 | 2589 | 6162 | 2605 | none >50 | 54 | 54 (100%) | 133/200/250/300 |
| 2 | 9.44 | 2065 | 2156 | 5120 | 2048 | 1701 | 59 | 48 (81.36%) | 93/250/400/1250 |
| 3 | 9.13 | 1903 | 1892 | 5470 | 2682 | 1932, 1203 | 61 | 49 (80.33%) | 85/450/600/1250 |
| 4 | 8.14 | 1967 | 2102 | 4325 | 1285 | 2095 | 47 | 43 (91.49%) | 89/300/700/1850 |
| 5 | 6.75 | 1826 | 1868 | 5025 | 2302 | 1767 | 54 | 53 (98.15%) | 121/400/1000/1400 |
| 6 | 6.94 | 2413 | 2546 | 5221 | 1703 | 1287, 1643 | 34 | 31 (91.18%) | 121/750/1050/1050 |
| 7 | 8.44 | 1791 | 1723 | 4242 | 1675 | none >50 | 68 | 58 (85.29%) | 81/200/400/750 |
| 8 | 7.41 | 1981 | 1936 | 4589 | 1591 | 1824 | 36 | 34 (94.44%) | 150/700/850/1250 |

##### devBenchmarkRelease
| Run | Load | TotalTime | app_composed | first_content | Home ready | key_lat | Frames | Janky | p50/p90/p95/p99 |
|---|---|---|---|---|---|---|---|---|---|
| 1 (dropped) | 7.71 | 788 | 629 | 2110 | 906 | 103, 104 | 79 | 69 (87.34%) | 73/97/101/113 |
| 2 | 8.23 | 914 | 737 | 2878 | 1554 | 110, 153 | 79 | 71 (89.87%) | 73/93/109/121 |
| 3 | 7.94 | 573 | 465 | 1777 | 750 | 90, 72 | 90 | 68 (75.56%) | 61/81/89/101 |
| 4 | 6.84 | 729 | 572 | 2672 | 1513 | 61, 271, 129, 137 | 49 | 49 (100%) | 150/200/250/350 |
| 5 | 7.57 | 765 | 703 | 2709 | 1421 | 207, 112, 162 | 60 | 55 (91.67%) | 105/150/150/200 |
| 6 | 6.63 | 593 | 505 | 2144 | 1070 | 159, 121, 199 | 55 | 54 (98.18%) | 133/150/200/200 |
| 7 | 6.84 | 848 | 658 | 2202 | 957 | 94, 107 | 73 | 64 (87.67%) | 81/109/117/129 |
| 8 | 7.95 | 751 | 661 | 2154 | 901 | 111, 106 | 69 | 62 (89.86%) | 85/125/150/200 |

##### Summary (runs 2-8, n = 7 per build)

| Metric | devDebug median (range) | devBenchmarkRelease median (range) |
|---|---|---|
| `am start -W` TotalTime ms | 1967 (1791-2413) | 751 (573-914) |
| `app_composed` ms | 1936 (1723-2546) | 658 (465-737) |
| **`first_content` ms (AC-13 anchor)** | **5025** (4242-5470) | **2202** (1777-2878) |
| Home ready ms (since Home start) | 1703 (1285-2682) | 1070 (750-1554) |
| Keys with key_lat > 50 ms, of 42 | 8/42 | 18/42 |
| key_lat of the slow keys, median / max ms | 1734 / 2095 | 116.5 / 271 |
| **key_lat p95 over all 42 keys (unlogged counted as 50) ms** | **1824** | **199** |
| key_lat p95 over the slow keys only ms | 2095 | 271 |
| Frames in the ~5 s window, median | 54 | 69 |
| Janky percent, median (software GPU: nearly all frames janky, a gross check only) | 91.18 | 89.87 |
| Frame time p50 / p90 / p95 / p99 ms, medians | 93 / 400 / 700 / 1250 | 85 / 125 / 150 / 200 |
| Host load average during runs | 6.75-9.44 | 6.63-8.23 |

Slow key_lat values, devDebug: 1701,1932,1203,2095,1767,1287,1643,1824. devBenchmarkRelease: 110,153,90,72,61,271,129,137,207,112,162,159,121,199,94,107,111,106.

##### Targets for the after measurement (brief Q1 / test-design Perf track)

- devBenchmarkRelease: median `revealed` <= 2202 + 300 = 2502 ms, and key_lat p95 (same 6-key script from `revealed`, same counting) < 199 ms.
- devDebug: reported, no threshold.

##### Caveats

- Emulator, software GPU, loaded host: spread of first_content is about 30 percent in both builds (release 1777-2878); a difference under that spread is not evidence.
- Keys are sent after `first_content`, which at base fires when Home's first content state appears, while Home is still assembling (hero focus, remaining rows, device-info and TV home sync). That is the intended "before" load for key_lat.
- key_lat is not TV key_lat (no real-TV baseline). The run's frames window is about 5 s, not a 10 s telemetry `frames` window, which closes only on a later frame.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| R-Q1..Q5 | research.md open questions | - | answered | as recommended, see Decisions |
| B-Q1..Q3 | brief.md open questions | - | answered | as recommended, see Decisions |
| UX-Q1 | No docs/design stand governs splash or Home: run designing-a-screen first? | no | answered | waived; fade value accepted on device under AC-10 |
| UX-Q2 | D-pad live from the start of the fade or from its end? | no | answered | from the start of the fade |
| A-Q1 | Clear the window background after reveal? | no | answered | no |
| A-Q2 | Move TvHomeSyncCoordinator onto AppCoroutineScope? | no | answered | not in this run (startup-settled-signal) |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-21 | Split: this run is P0 only; P1 is run startup-settled-signal | owner (standing instruction: recommended) |
| 2026-11-21 | Reveal readiness: Home Content with heroSettled and hero focus claim resolved; other landings their first StartupContentState; cap 2.5 s; D-pad/OK dropped while covered, Back passes; no dwell on warm start; keep Compose SplashContent | owner (standing instruction: recommended) |
| 2026-11-21 | AC-13 target on devBenchmarkRelease: median revealed <= 2502 ms (baseline first_content 2202 + 300); key_lat p95 after reveal < 199 ms | owner (standing instruction: recommended) |
| 2026-11-21 | app-coroutine-scope idea absorbed as the warm-up owner | owner (standing instruction: recommended) |
| 2026-11-21 | devBenchmarkRelease install over devDebug allowed with install-preserve when signer and versionCode match (they did in baseline) | owner (standing instruction: recommended) |
| 2026-11-21 | Design stand waived; UX-Q2, A-Q1, A-Q2 as recommended | owner (standing instruction: recommended) |
| 2026-11-21 | Peer static audit perf-audit-2026-11-21.md (run folder) is planner input: items needed by this run's ACs may be absorbed, the rest become idea runs | conductor |
| 2026-11-21 | Verify/review round caps lifted | owner |
| 2026-11-21 | Gate 1: approved | owner (standing instruction: recommended) |
| 2026-11-21 | Gate 1 delta: reveal also waits for image requests in flight (cap unchanged; an opening update prompt reveals at once); AC-4, AC-6, AC-12 amended and AC-15 (fixed-anchor key_lat vs interleaved base) added per architect delta 1; AC-13 revealed target kept - if both cannot hold, report and ask | owner (standing instruction: recommended) |
| 2026-11-21 | Delta Q2: if AC-15 fails with images decoded, next fix round adds bitmapFactoryMaxParallelism(2) | owner (standing instruction: recommended) |
| 2026-11-21 | Delta Q3: idea run card-image-size-tier recorded | owner (standing instruction: recommended) |
| 2026-11-21 | AC-13 and AC-15 unmet on the emulator and waived (not passed); real-TV acceptance outstanding (idea startup-first-input-on-tv); decided by conductor with a Codex consult at the owner's request | owner (delegated) |
