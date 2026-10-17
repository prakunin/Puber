# Readiness 1: telemetry-tab-host-back-label

Verdict: **READY**

Read at worktree `/home/user1/orca/workspaces/Puber/telemetry-tab-host-back-label`, HEAD `8c9143b6`
(spec commit on base `bdd2c792`; the plan is untracked in the worktree). Spec status `approved`, gate 1 recorded.

## AC coverage

| AC | Plan task | Test design row | Covered |
|----|-----------|-----------------|---------|
| AC-1 | T1 (`disposedBindingKeepsTheTopForTheSameHost`), T3 fix | unit P0 | yes |
| AC-2 | T1 (`frozenBindingOfHostADoesNotLeakIntoFreshHostB`), T3 | unit P0 | yes |
| AC-3 | T1 (three ordering tests), T2 (`tabSwitchThenRootPushAndBackNamesTheNewTab`), T3 | unit P0 + instrumented P1 | yes |
| AC-4 | T2 (`backOntoTheHostRecordsNoTabReannounce`), T4 fix | instrumented P0 | yes |
| AC-5 | T2 (two tests, incl. equal-but-distinct `PuberTab`), T4 | instrumented P0 | yes |
| AC-6 | T1 (`tabLocalTopIsFrozenAtUnbind`), T2 (`tabLocalPushThenRootPushThenBackNamesTheTabLocalScreen`) | unit P1 + instrumented P0 | yes |
| AC-7 | T2 (`backFromRootPushedScreenNeverRecordsMain`) | instrumented P0 | yes |
| AC-8 | T4 full gate; T1 keeps other cases untouched | unit P0 | yes |
| AC-9 | T1 (`unbindWithTelemetryOffKeepsNoFrozenTopAndNeverReadsIt`), T3 | unit P1 | yes |
| AC-10 | handed to qa-device after T4 ("Done when" of T4) | manual M1 | yes (device, qa-device) |

## Citations sampled (all resolved in code at 8c9143b6)

1. `NavTelemetry.kt:34-36` `unbind` with `if (binding === handle) binding = null`; `:38` `topOf` with `it.host === host`; `:21-25` KDoc "cleared when it leaves"; `:47-52` `recordNav`, `:48` isEnabled guard, `:50` the only `Telemetry.screen` write. Correct.
2. `FlowComponent.kt:588-591` `DisposableEffect(tabHost, navigator)` bind/unbind; `:228` `navChanged`; `:272` `recordNavCommand(event, navFrom, navChanged.lastItemOrNull)`; `:738-746` RootPuberScreen forwarded to `rootRouter`; `:82` `FlowComponent` signature; `:659,665` `TabRootScreen.contentScreenClass`. Correct.
3. `TabComponent.kt:34` `LocalTabHostScreen`; `:77-81` signature; `:108-119` collector; `:112-116` `Open` handling with `recordNav(NavCmd.TAB, from, event.tab)`; `:126` `PuberCurrentTab`. Correct.
4. `TabRouter.kt:13` `MutableSharedFlow(replay = 1)`, `:19` `openTab`, `:24` `Dispatchers.Main.immediate`. Correct.
5. `Telemetry.kt:5-8` `TelemetryRecorder`, `:33-35` `screen`, `:37` `isEnabled`, `:46-49` `install`, `:55-62` `resetForTests` (sets `screen = null`); `TelemetryClock.kt:11` `internal object JvmTelemetryClock`; `TelemetryNames.kt:44-49` `telemetryClassOf`; `TelemetryEvent.kt:140-147` `Nav`. Correct.
6. `NavTelemetryTest.kt:25-28,36-45,47,101-103,133-138,140-146`; `tearDown` resets Telemetry before unbinding (so after T3 the off-path clears a live binding). `RecordingTelemetryRecorder.isEnabled` is a `var`. Correct.
7. `MainScreen.kt:23-24,39` (`@Parcelize internal class`, `LocalTabHostScreen provides this`); `PuberScreen.kt:12` `RootPuberScreen`; `TabHostScreen.kt:4`; `TabType` has `Home` and `History`; `MainVM.kt:51,116,125,151,165` all open a freshly built `PuberTab` (`MainUIMapper.kt:83-92`). Correct.
8. `TabFlowLifecycleTest.kt:62-63,69-84,88-101,179-187,189-218,319-325,349-355,357-372`. Correct.

Only drift found: the spec's D5 cites `PuberTab.kt:20-26`; the real declaration is `:16-22` (`@Stable @Parcelize data class PuberTab` at `:18`). The plan cites `:16-22` correctly. Cosmetic.

## Build state and order

- T1 -> T2 -> T3 -> T4 follow the dependencies: failing-first tests, then the wiring test, then the two fixes. Each task needs a green fast gate (compile).
- T1 and T2 deliberately leave two JVM tests red (`disposedBindingKeepsTheTopForTheSameHost`, `tabLocalTopIsFrozenAtUnbind`) until T3. I accept this as the bug-run failing-first convention: compilation stays green, T1's "Done when" names exactly those two failures, and the commits are squashed before ship. I checked the expected base outcome of every new T1 case against the base `unbind`. Only those two fail; the other five pass on base, as the plan states.
- T4 runs the full gate and the androidTest compile.

## Scope

The plan stays inside Out of scope: no Robolectric, no `act Resumed` change, `TabFlowLifecycleTest` is not edited (fixture copied, no shared helpers), no `FlowComponent.kt`, `TabRouter.kt` or `MainVM.kt` change. No open question blocks (Q1-Q3 answered).

## Must fix

None.

## Notes for the conductor (non-blocking)

1. Failing-first proof for the instrumented class: the test design asks to run `TabHostTelemetryWiringTest` once on the pre-fix code (expected red for AC-7, AC-4, AC-6 and AC-3, green for both AC-5 tests). The plan only says qa-device "records" it and schedules no device step between T2 and T3. Without that run, a fixture where the tab navigator never disposes would pass AC-7 on the old code too, and the top risk R1 would go unchecked. Either dispatch qa-device on the T2 commit before T3, or have qa-device record why it was skipped. AC-10 on the real app partly covers this.
2. The plan does not assign the manual checks M2 (AC-6 on device) and M3 (AC-5 on device) to anyone. Include them in the qa-device brief with M1 and M4.
3. The test design wants the AC-5 instrumented reselect to assert both the `Nav TAB` and `Telemetry.screen`. Plan T2 asserts only the `Nav TAB` for the reselect. Adding `Telemetry.screen == WiringHomeScreen` is cheap.
4. R9: frozen bindings created without `bindTop` survive `tearDown`. They stay inert only because every test uses a fresh `MainScreen()`. Reviewers should check that no new test reuses a host across tests.
