# Brief: startup-splash-preload

Base a22c03bc. Analyst, 2026-11-21. Builds on `research.md`; the code references it gives for the splash, launcher, `PuberApp`, HomeVM domain wait, `StartupMarks`, `dispatchKeyEvent` and build types were re-read at base and hold.

kind: improvement. size: M. tracks: ui, perf. Device verification: **yes** (startup, navigation, TV focus, D-pad input, overlay, DI, networking; the goal is a runtime number).

Size reason: one goal (P0 of the request; P1 is follow-up run `startup-settled-signal`, owner 2026-11-21), but it crosses startup, root composition, Home ViewModel, cache/networking, auth reads and telemetry, all risk areas in `.flow/roles/analyst.md`.

## Request
Use the splash as loading time on cold start (research.md: Request). This run is P0 only: no fixed dwell, token and domain init off main, Home data warm-up at process start, Main+Home composed under the splash and revealed with a fade when Home is ready or at a cap, D-pad dropped while covered, splash removed after reveal, a separate `revealed` startup mark. Measured on the emulator in devDebug and a release-type build.

## Owner decisions applied
| ID | Decision |
|---|---|
| Q1 | Split: P0 here, P1 (settled signal, deferral of background work, publish coalescing) in `startup-settled-signal`. |
| Q2 | Ready = Home `Content` with `heroSettled` and the hero focus claim resolved (claimed or refused); any other landing screen: its first `StartupContentState`; cap 2.5 s from activity `onCreate`, revealing whatever state is there. |
| Q3 | While covered all D-pad and OK keys are dropped; Back passes through. |
| Q4 | No minimum dwell on warm start; same readiness rule, fade still applied. |
| Q5 | Keep the Compose `SplashContent` overlay; no `core-splashscreen` dependency. |

## Current state
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

## Scope
In: research.md option B (research.md: Options). Warm-up owner lives at application level (`app-coroutine-scope` idea, see Q2). Reveal state is a process-level holder written by the landing screen and read by `MainActivity`.
Out: P1 items; baseline profile, R8 rules, post-update compilation; `reportFullyDrawn`/TTFD via `StartupBenchmarks` (`startup-benchmark-entry`).

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

## Risks
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

## Open questions
1. Perf target for AC-13? Recommended: on devBenchmarkRelease, median `revealed` (Home usable) no later than the before median `first_content` + 300 ms, and key_lat p95 in the first 3 s after reveal lower than before. Blocks: yes (sets AC-13).
2. Fold the `app-coroutine-scope` idea (one injected application scope) into this run as the warm-up owner? Recommended yes, close that idea as absorbed. Blocks: no.
3. Install devBenchmarkRelease over the emulator's devDebug with `tools/android-apk-install-preserve`? Recommended yes only after checking the installed package's signer and versionCode match (no signer change, no downgrade); otherwise stop and ask. Blocks: no.
