# Gate - startup-splash-preload - round 1

**Decision:** CONCERNS
**Head:** 2b1e9f74   **Base:** 8428c946 (rebased; original base a22c03bc)

## Done as asked

The diff (`git diff 8428c946...HEAD`, 28 main files) implements spec option B plus Architect delta 1: `MIN_VISIBLE_MS` and the launcher splash gone (`LauncherScreen.kt`), domain init and token read off main (`AppStartup.kt`, `LauncherVM.kt`), Hot and ContinueWatching warm-up on `AppCoroutineScope`, one shared domain walk (`ApiDomainInteractor.kt`), `RevealGate` with ready, images in flight, cap, latch and restore (`RevealGate.kt`, `StartupReveal.kt`, `StartupImageActivity.kt`), `StartupSplashHost` 250 ms fade and removal, `CoveredKeyFilter` (D-pad/OK only, Back passes), `revealed` mark. Nothing from Out of scope (P1 deferrals, baseline profile, R8, TTFD) is in it. `bitmapFactoryMaxParallelism(2)` (owner Delta Q2) is not in it: superseded by the delegated waiver decision (state.md Decisions).

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `LauncherVMTest.start_opensMainScreenWithoutASplashAction`, M1 | qa round 1; code: dwell and launcher `SplashContent` removed | PASS |
| AC-2 | `LauncherVMTest.start_readsTheAccessTokenOnTheIoDispatcher`, `start_waitsForDomainInitBeforeReadingTheToken`, `AppStartupTest.start_appliesTheSavedDomainOffTheMainThread` | qa round 1; tests present at head | PASS |
| AC-3 | `AppStartupTest.warmUpAndHomeShareOneRequestPerEndpoint`, `ApiDomainInteractorTest`; device M7 | unit PASS; no device request count | PASS (unit) / NOT CHECKED (device M7) |
| AC-4 (amended) | `HomeVMTest`, `HomeLaunchFocusSettledTest`, `RevealGateTest.readyWaitsForImagesInFlight`; M1 screenrecord | qa round 2 (1 start), round 2b (4 of 9 starts images drawn; 1 unexplained skeleton reveal, Concern 3) | PASS (Concern 3) |
| AC-5 | `RevealGateTest.cap_uncoversWithoutReady`, `remainingCapMs_countsFromBegin`; M2 | unit PASS; emulator network could not be cut | PASS (unit) / NOT CHECKED (device) |
| AC-6 (amended) | `PuberVMRevealTest` (4); M3 | qa round 2: /items/1000 revealed 3061, /items/1 3036 after images; round 2b 4693 ms unanalysed (Concern 4) | PASS (Concern 4) |
| AC-7 | `StartupKeyFilterTest` incl. `upOfAKeyPressedWhileCoveredIsDroppedAfterReveal`, `DispatchWithProbeTest`; M1, M4 | qa round 1 | PASS |
| AC-8 | `StartupSplashHostTest.splashLeavesCompositionAfterTheFade` (instrumented) | qa round 1, 4/4 on emulator | PASS |
| AC-9 | `StartupMarksTest`, `TelemetryEncoderTest`; M10 | 24/24 cold starts one line with `revealed` | PASS |
| AC-10 | `StartupSplashHostTest.splashIsDrawnInTheFirstFrame`, `RevealGateTest.begin_coversAgainAfterAPreviousReveal`; M5 | owner judges the look; T1 recreation PASS (rounds 2, 2b) | NOT CHECKED (manual 1) |
| AC-11 | `AppStartupTest.aWarmUpLandingAfterAStoreWipeIsNotServedToHome` + guards | qa round 1 | PASS |
| AC-12 (amended) | `UpdatePromptOverlayFocusTest.updatePromptOpenedUnderTheSplashHasFocusOnUpdateAfterReveal`, `StartupRevealTest.onUpdatePromptOpenChanged_openRevealsAtOnceWithoutContentOrImages`; M8 | instrumented 8/8 round 1; no newer release offered | PASS (tests) / NOT CHECKED (device, manual 3) |
| AC-13 | Perf A/B, devBenchmarkRelease | qa round 2: revealed median 2789 vs target 2292; old-anchor key_lat p95 112 vs 193 | FAIL (waived by delegated decision, Concern 1) |
| AC-14 | check.full | judge run at 2b1e9f74: 2215 tests, 1 failed `HistoryVMDeletionSchedulingTest.deleteSelectionDuringRefresh_isQueuedOnceAndRetainsSemanticFocus` at load 40 (known flake, run `unit-suite-flaky`, class untouched); class re-run green, detektAll and lintDevDebug green; implementer green at e5a579a1 | PASS |
| AC-15 | Fixed-anchor interleaved A/B | qa round 2: p95 919 vs 1133 met; share >= 199 ms 14/17 vs 16/36 not met | FAIL (waived by delegated decision, Concern 1) |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| T1 restored activity covered until cap | blind F1, edge-1 F1 | fixed | 5ee1bd54; `StartupRevealTest.onActivityCreated_restored*`; device T1 PASS |
| T2 + T6 NotFound not shared; delay-based test | codex-1, edge-1 F2, tests-1 F2 | fixed | c4b131cc |
| T3 walk overwrites a user domain | codex-1 | fixed | 28f288f0 |
| T4 ready before image requests exist | codex-1 | fixed | b4448e0d (two-frame wait) |
| T5 update-prompt reveal untested | tests-1 F1 | fixed | 29dd34c9 |
| T7 Coil listener wiring untested | tests-1 F3 | fixed | 2118f95c; device rounds 2, 2b |
| T8 joiner spin / ANR (blocker) | edge-2 F1 | fixed | c2acd654; `autoResolve_joinerWalksItselfWhenTheOwnerFailsAndAThirdCallWalksAgain` |
| T9 Looper in JVM tests (blocker) | tests-2 F1 | fixed | 313a0434 |
| T10 retry path untested | tests-2 F2 | fixed | c2acd654 |
| A1 version check not atomic with write | edge-2 F2, codex-2 | accepted minor | window of ms, not a regression |
| A2 version bump tested via saveCustomDomain only | tests-2 F3 | accepted minor | real, small |
| A3 frame wait and listener wiring lack unit test | tests-2 F4 | accepted minor | device evidence rounds 2, 2b |
| U1-U6, R1-R4 | blind, edge, tests unproven | rejected | spot-checked U1 (launcher routing waits only on domain init + token read; qa round 1 routes far below the cap) and U5 (`isDroppedWhileCovered` matches spec Q3 list): accepted |
| qa 2b defect 1: skeleton reveal before the apparent cap | qa-report Round 2b | not triaged | Concern 3 |
| qa 2b defect 2: content-link reveal 4693 ms | qa-report Round 2b | not triaged | Concern 4 |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Emulator: first_content median 1015 vs 1992, old-anchor key_lat p95 better; revealed later than target and first post-reveal key slow (RenderThread shader compile on swiftshader, round 2b trace). TV unmeasured (Concern 1). `AppStartup` resolves on main with providers only; Coil listener is one synchronized set op per event. |
| Reliability | Domain init failure completes the deferred in `finally`; cap bounds every wait; latch prevents re-cover; restore reveals at once; walk cancel/retry tested. Cap and `revealed` run on the main looper, so a render stall delays both (round 2b 4693 ms). |
| Maintainability | AGENTS.md conventions kept (Koin DSL, pure composables, JUnit 5, `resetForTests`). `domain/di/modules.kt` builds a second `HomeInteractor` with the same 7 arguments as Home's screen module: duplicated wiring to keep in sync. No dead code left (`LauncherAction` deleted, `SplashContent` still used). |
| Security / privacy | No secrets or account data in diff; `tools/mobile-evidence-audit.sh docs/superpowers/evidence` passed (55 files); no recordings stored. Installs were same-signer `install-preserve` (M9 each time), none owner-authorised beyond that; nothing uninstalled or cleared. |

## Manual checks for the owner

1. AC-10 (cold and warm look, TV, release build): force-stop, launch from the TV launcher: wordmark fades about 250 ms into Home with hero image and cards drawn, no spinner, no black frame. Then Back out to the launcher (activity finished, process alive), relaunch: same fade, no blank frame.
2. AC-5 (TV): disconnect Wi-Fi/Ethernet, cold start: splash lifts about 2.5 s after launch, the visible state answers D-pad; reconnect.
3. AC-12 (when a newer release is published): cold start; the prompt shows at once without waiting for Home, focus on Update, Back dismisses.
4. AC-13/AC-15 on TV (outstanding acceptance, idea `startup-first-input-on-tv`): release builds of master 8428c946 and this branch, 5 cold starts each, telemetry on; compare `first_content`/`revealed` and `key_lat` of D-pad presses at 2-4.5 s after launch and right after the reveal.

## Must fix

none

## Concerns for the owner

1. AC-13 and AC-15 FAIL on the emulator. The waiver was decided by the conductor with a Codex consult under your "decide yourselves"; it also set aside your earlier Delta Q2 decision (`bitmapFactoryMaxParallelism(2)` as the next fix round). The `WAIVED` status is yours to set at gate 2; TV performance acceptance remains open (manual 4).
2. AC-3 device request count (M7) was not captured though it is checkable on the emulator with the debug network log; only unit evidence.
3. Round 2b: one of 9 cold starts revealed with hero and card skeletons at 2742 ms, 8 ms after `first_content`. Unexplained: the cap counts from `onCreate`, which the evidence does not record, so a cap reveal and an early reveal (image requests issued after the two-frame wait) cannot be told apart. Not triaged.
4. Round 2b: content-link reveal at 4693 ms, 3.7 s after Details content and past the 2.5 s cap; unanalysed, likely the cap delayed by the 1.4 s first-frame render stall. Not triaged.
