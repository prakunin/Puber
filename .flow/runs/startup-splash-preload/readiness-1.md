# Readiness 1: startup-splash-preload

Final judge, 2026-11-21. Inputs: spec and plan (worktree, plan untracked at HEAD 7ed16cdb), `test-design.md`, `state.md` Decisions. Citations read with `git show a22c03bc:<path>`.

**Verdict: READY**

## AC coverage

| AC | Plan task | Test design rows |
|---|---|---|
| AC-1 | 8 | `LauncherVMTest.start_opensMainScreenWithoutASplashAction`, M1 |
| AC-2 | 6, 8 | `LauncherVMTest.start_*`, `AppStartupTest.start_appliesTheSavedDomainOffTheMainThread` |
| AC-3 | 5, 6, 8 | `ApiDomainInteractorTest.autoResolve_concurrentCallersShareOneProbe`, `AppStartupTest.warmUp*`, M7 |
| AC-4 | 4, 7 | `HomeVMTest`, `HomeLaunchFocusSettledTest`, `StartupSplashHostTest.contentUnderTheSplashTakesLaunchFocus`, M1 |
| AC-5 | 2, 7 | `RevealGateTest.remainingCapMs_countsFromBegin`, `RevealGateTest.cap_uncoversWithoutReady`, M2 |
| AC-6 | 3 | `PuberVMRevealTest.*`, M3 |
| AC-7 | 2, 7 | `StartupKeyFilterTest.*`, `RevealGateTest.markReady_uncoversImmediately`, M1, M4 |
| AC-8 | 7 | `StartupSplashHostTest.splashLeavesCompositionAfterTheFade`, M6 |
| AC-9 | 1 | `StartupMarksTest.*`, `TelemetryEncoderTest.*`, M10 |
| AC-10 | 2, 7 | `RevealGateTest.begin_coversAgainAfterAPreviousReveal`, M5 |
| AC-11 | 6 | `AppStartupTest.aWarmUpLandingAfterAStoreWipeIsNotServedToHome` + two guards |
| AC-12 | 7 | `UpdatePromptOverlayFocusTest.updatePromptOpenedUnderTheSplashHasFocusOnUpdateAfterReveal`, M8 |
| AC-13 | qa-device after Task 8 (plan, Global constraints) | Perf track; target in spec Decisions (revealed <= 2502 ms, key_lat p95 < 199 ms) |
| AC-14 | 8 | full gate |

Every automated name in test-design.md appears in the plan (by class or inside its class's task). The plan adds `StartupKeyFilterTest.upOfAKeyPressedWhileCoveredIsDroppedAfterReveal`, accepted by the owner (state.md, Plan Q1).

## Citations sampled (at a22c03bc)

| Citation | Resolves to |
|---|---|
| `MainActivity.kt:51-54`, `:65` | `dispatchKeyEvent` via `dispatchWithProbe`; standalone `SplashContent()` |
| `LauncherScreen.kt:19`, `:27`, `:40-45` | `MIN_VISIBLE_MS`; `viewModelOf(::LauncherVM)`; `delay` + `SplashShown` + `SplashContent()` |
| `LauncherVM.kt:11-15`, `:24-29`, `:35` | 3-param constructor; `handleAction`; `getAccessToken()` on caller thread |
| `PuberApp.kt:33`, `:91` | `ApiDomainInteractor` import; `get<ApiDomainInteractor>().initialize()` |
| `PuberVM.kt:86-90` | `updateViewState` with `Telemetry.isEnabled` checks |
| `HomeVM.kt:47-58`, `:176-199`, `:273` | constructor; `handleAction` with `else` at 197; `autoResolveWorkingDomain()` |
| `HomeScreenContent.kt:181`, `:191-203`, `:319-345` | `rowsHeldFocus` (rememberSaveable); `heroFocusResolved` + `ClaimHeroFocusEffect` call; 5-attempt claim |
| `ApiDomainInteractor.kt:53-60`, `:117-145`, `:125`, `:177` | `initialize`; `autoResolveWorkingDomain`; `isKnownReachable`; `log(error, ...)` |
| `App.kt:179` | `UpdatePromptHost(onOpenChange = ...)` |
| `KeyProbe.kt:109-119` | `dispatchWithProbe` |
| `StartupMarks.kt:20-32`, `TelemetryEvent.kt:130-138`, `TelemetryEncoder.kt:67-72` | emit CAS; `Startup` data class; `putStartup` |
| `HeroCarousel.kt:182-196` | select acts on `KeyUp` |
| `CachedFeed.kt:495-499` -> `TypedTtlCache.kt:84-101` | `reload`/`getOrPut`; flight join via `Lookup.Wait` |
| `CryptoPreferenceRepository.kt:33-35` (`data/repository/`) | lazy `sharedPreferences` |
| `domain/di/modules.kt:31`, `:71-76`; `data/di/modules.kt:62-71`, `:90`, `:101`, `:109` | `interactorModule`; `DetailsPrefetcher`; `HomeInteractor` deps are singles |
| `DetailsScreenState.kt:14`, `PlayerViewState.kt:13`, `StartupContentState.kt:8` | `Content` classes; marker interface |
| Test anchors: `PuberVMTelemetryTest.kt:87-91`, `:133-178`, `:181`; `SectionVMTest.kt:196-226`; `LauncherVMTest.kt:22`, `:29-38`, `:83-95`; `HomeVMTest.kt:446-459`; `StartupMarksTest.kt:40`; `TelemetryEncoderTest.kt:128`; `HomeVMIncrementalPublishTest.kt:265`; `CachedFeedTest.kt:726`; `UpdatePromptOverlayFocusTest.kt:46-49`, `:62-104`, `:256-258`; `FavoriteScreenContentFocusTest.kt:167-169`; `HomeFocusTraversalTest.kt:332`, `:355-364` | all as claimed |

Inexact: Task 6 fixture says "as in `HomeInteractorCacheTest.kt:30-48`" with `FakePayloadStore`; that test uses `InMemoryPayloadStore()` (`:34`). `FakePayloadStore` exists (`test/util/FakePayloadStore.kt:7`), so the plan builds.

## Build and order

Dependencies hold: T3 needs T2; T4 needs T3; T6 needs T5; T7 needs T1, T2, T4; T8 needs T6. Each task compiles and keeps its tests green; Task 1 lists every existing test that asserts emission at `first_content` (`PuberVMTelemetryTest` x3, `SectionVMTest`); `StartupMarksTest.oneLineWithAscendingMarksOnFirstContent` calls `onTimeout()` and still passes.

## Notes for implementation and review (non-blocking)

| # | Note |
|---|---|
| 1 | Between Task 1 and Task 7 no code calls `markRevealed()`, so a build from those commits emits the `startup` line only at the 60 s fallback (`TelemetryRuntime.kt:55`). Do not measure intermediate commits. |
| 2 | The `rowsHeldFocus` term the plan adds to Home's settled condition (Task 4, beyond the spec's rule) has no test; a recreated Home falls back to the cap if it is wrong. Reviewers check it; M5 observes it. |
| 3 | Device P0 rows (M1, M2, M4, M5, Perf track) carry no red/guard mark; they are manual by nature and name their expected and forbidden outcomes. |
| 4 | `autoResolve_concurrentCallersShareOneProbe`: `probeCalls` (`ApiDomainInteractorTest.kt:33`) is a plain list appended from IO threads; the red run without the `Mutex` may race. Use a synchronized list in the new test. |

## Open questions

None blocking: spec Open questions all answered; state.md Open questions empty.
