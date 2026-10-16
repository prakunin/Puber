LENS: tests  ROUND: 2  BASE: cd16eb47

The full check (`./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`) is green on HEAD 5eba2142. Unit tests: 1981 passed, 0 failed, 0 errors, 0 skipped. That is 10 more than round 1, and they match the new tests: KeyProbeGateTest 2, NavigatorChangedByTest 3, TelemetryRuntimeProbesTest +2, NavTelemetryTest +2, TelemetryActivityCallbacksTest +1. Gradle reused the test results (UP-TO-DATE) because the worktree is clean and the inputs match. I worked through the mutations by reasoning and did not run them. Detekt and lint are clean.

What I checked, and each test fails if its behaviour breaks:
- **#6 `KeyProbeGateTest`:** it goes red if the `repeat == 0` check, the ACTION_DOWN check or the `handled` pass-through is dropped. The fast first frame frees the slot, so every later slow frame would produce a KeyLat.
- **#10 `KeyProbeOffTest`:** the recorder is now installed before the off feed, and the feed ends on an open DOWN. If the off branch reached the probe, `flush` or the matcher would record something, so the test can now fail.
- **#7, #8, #9:** each test goes red if its line or field is deleted or swapped.
- **#3 `switchingOffFlushesTheProbesOnMainBeforeTheSinkStops`:** under the old order the detach was only posted, so `sinkEnabledAtDetach` would stay null and the test fails. The off-main test fails if `isMainThread` is ignored.
- **Renamed L7:** it now guards the flush when the log is switched off (2 frames lines; the old code wrote 1). It also still checks that nothing is written after the switch-off.

### F1 [minor] FinishFlow from a child flow with more than one screen is not tested
- Where: app/src/test/kotlin/com/kino/puber/core/ui/navigation/component/NavigatorChangedByTest.kt:48-51; app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt:317
- Problem: the only FinishFlow case uses a child with one screen. For that child, `navigatorToPop(child)` already returns the parent, so the test cannot tell the FinishFlow branch apart from the Back branch.
- Failure scenario: someone changes the FinishFlow branch to `navigatorToPop(navigator) ?: navigator`. All tests stay green. Then, on a device, FinishFlow from a flow with two or more screens pops the parent, but the nav line and `Telemetry.screen` keep the child's unchanged top screen. That is the #1 mislabelling again.
- Fix: in `backWithRoomInTheRunnerChangesTheRunner`, after `child.push(HomeScreen())`, add `assertSame(parent, navigatorChangedBy(Command.FinishFlow, child))`.

### F2 [minor] The #1 fix is checked against a copy of the pop logic, not against the pop that runs
- Where: app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt:228-229,272,315-322; NavigatorChangedByTest.kt:60-70
- Problem: `navigatorChangedBy`/`navigatorToPop` re-implement the recursion in `onBackWithNavigator`. `backThatPopsTheParentRecordsTheParentTop` calls `changed.pop()` itself instead of using the real pop routine, so it repeats the implementation step for step. No test checks that the navigator it returns is the one `onBackWithNavigator` actually pops. The runner wiring (`navChanged` used for both `navFrom` and `top`) is not tested either.
- Failure scenario: `onBackWithNavigator` or `canPop` changes, for example to skip a parent. The copy drifts and the tests stay green. Or line 272 goes back to `navigator.lastItemOrNull`, and every Back that pops a parent records the screen just left. All tests stay green.
- Fix: make `onBackWithNavigator` internal. In the test, run it on `child`, and assert that `navigatorChangedBy(Command.Back, child)` returned the navigator whose stack shrank, then call `recordNavCommand` with that navigator's top. The runner wiring needs to be stated as part of the qa-device AC-12 check.

### F3 [minor] Tab host (#2): neither the `from` side nor the TabComponent assignment is tested
- Where: app/src/main/java/com/kino/puber/core/ui/navigation/component/NavTelemetry.kt:35; app/src/main/java/com/kino/puber/core/ui/navigation/component/TabComponent.kt:111; app/src/test/kotlin/com/kino/puber/core/ui/navigation/NavTelemetryTest.kt:99-111
- Problem: both new tests set `TabHostTelemetry.content` by hand and only check `to` and the screen holder. Nothing checks `nav.from` when it is `MainScreen` (for example a push from the tab host). Nothing checks that `TabCommand.Open` writes `contentScreenClass`.
- Failure scenario: `navClassOf(from)` goes back to `telemetryClassOf(from)`, so pushes from the host log `from=MainScreen`. Or the TabComponent line is deleted, so after a root Back the holder is `MainScreen` again. Either way the tests stay green.
- Fix: in `tabHostIsReportedAsItsCurrentTabContent`, add `recordNavCommand(NavigateTo(other), MainScreen(), other)` and assert `from == HomeScreen::class.java`. List the TabComponent assignment under the qa-device check for #2: after a tab switch and a root Back, the nav `to` must be the tab's content.
