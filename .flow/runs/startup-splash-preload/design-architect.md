# Design (architect): startup-splash-preload

Paths are relative to `app/src/main/java/com/kino/puber/` unless they start with `app/`. ACs: `brief.md#acceptance-criteria`. Fade duration and easing come from the ux-designer delta.

## Decisions

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

## Changes by layer

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

## Alternatives rejected

- Gate keys with `ContentFocusGate`: it also disables launch focus claims (`FocusOnLaunchRequester.kt:39-48`).
- Warm-up inside `HomeVM` or `MainVM`: they exist only after Main composes, which AC-3 rules out.
- A Home-specific readiness API on the generic path (checking `HomeViewState.Content.heroSettled` in `PuberVM`): the focus outcome lives in composition, not in state.
- Awaiting domain init inside `KinoPubApiClient`: wider change for the same guarantee the launcher gate gives.
- `core-splashscreen`: excluded by Q5.

## Risks

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

## Open questions

1. Clear the window background (`app/src/main/res/values/themes.xml:6`) after reveal to drop its overdraw? Recommended: no, not asked by AC-8 and risks a black flash on transitions. Blocks: no.
2. Move `TvHomeSyncCoordinator` (`PuberApp.kt:51`) onto `AppCoroutineScope`? Recommended: no in this run; it runs on `Dispatchers.IO` and belongs to `startup-settled-signal`. Blocks: no.
