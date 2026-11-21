# Test design: startup-splash-preload

Base a22c03bc. Inputs: `brief.md` (ACs, risks), `design-architect.md`, `design-ux-designer.md`. Levels from `.flow/roles/test-designer.md`: `unit` (JUnit 5, `MainDispatcherExtension`, virtual time), `instrumented` (`make DEVICE=<serial> itest TESTS=<fqcn>`), `device` (manual, evidence file). Test paths: `app/src/test/kotlin/com/kino/puber/` and `app/src/androidTest/kotlin/com/kino/puber/`. Every test that touches `StartupReveal` calls `StartupReveal.resetForTests()` in `@AfterEach`, as `PuberVMTelemetryTest` does with `Telemetry`.

## Seams the tests need

| Seam | Used by |
|---|---|
| `AppStartup` takes its scope and an `ioDispatcher` as constructor parameters (defaults allowed) | `AppStartupTest` |
| The overlay, fade and removal live in a composable taking `covered` and a content slot (called `StartupSplashHost` below), which `MainActivity` calls | `StartupSplashHostTest`, `UpdatePromptOverlayFocusTest` |
| `isDroppedWhileCovered(keyCode)` is a top-level pure function (design-architect.md, Key filter) | `StartupKeyFilterTest` |

## Risk profile

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

## Traceability

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

## Bugs

N/A: improvement; red rows above are the failing-first tests.

## Manual checks

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

## Perf track

| Item | Value |
|---|---|
| Baseline | `/mnt/data/projects/PetProjets/Puber/.flow/runs/startup-splash-preload/baseline.md` (qa-device, base a22c03bc) |
| Devices and builds | Same emulator, devDebug and devBenchmarkRelease, 5 cold starts each, before and after |
| Metric 1 | Startup: before `first_content`, after `revealed` (and `first_content`), ms since process start, from the `startup` line |
| Metric 2 | `key_lat` of a fixed script: 6 `DPAD_RIGHT`/`DPAD_LEFT` at 500 ms spacing starting at the anchor (before: `first_content`; after: `revealed`), keys within the first 3 s |
| Metric 3 | Frames: `frame` telemetry lines (janky count, p95) for the first 5 s after the anchor |
| Pass (devBenchmarkRelease) | median after `revealed` <= median before `first_content` + 300 ms; `key_lat` p95 after < before |
| devDebug | Reported, no threshold |
