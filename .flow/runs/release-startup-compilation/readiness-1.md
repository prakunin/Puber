# Readiness 1: release-startup-compilation

Verdict: **NOT READY**

Spec and plan at wt HEAD 3788b2af (base e4c3b5aa).

## Fix list

1. The spec holds two copies of `## Test design` and `## Open questions` (spec lines 138-240 and 241-348). The second copy (241-337) was not updated by the delta in 3788b2af. It still has S0/S1 built on `cmd package compile --reset`, which the box refuses (state.md, qa baseline phase 1). It also has M1 as "conductor" and the P2 row reading "after `runAll`". Delete lines 235-337 (stale `## Open questions` list and the stale Test design). Then drop the design's `### Open questions` list (lines 132-136): Q-D1..Q-D3 in the final table answer it. One Test design and one Open questions table remain.
2. AC-3 and the `baselineProfileHasNoRemovedTopTabClasses` row cite "the 10 stale classes listed in research.md", but `research.md:17` names five ("e.g."). The test checks four patterns. Put all ten classes in research.md, and make the test row name each one or a pattern that covers it. AC-3 is then checkable as written.
3. The device and manual P0 rows of Traceability (AC-1 M4, AC-2 M1, AC-4 M5, AC-5 M6, AC-6 M2, AC-8 M7) carry no `red`/`guard` mark. Mark them `guard`. Each row already names the defect it catches.

## Coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | Device verification after Task 5 (Baseline) | M4 |
| AC-2 | Task 3, Task 5 | M1, M8 |
| AC-3 | Task 2, Task 5 | `BaselineProfileContentTest` x3 |
| AC-4 | Device verification (New build) | M5 |
| AC-5 | Task 1 | `StartupMarksTest` x5, `TelemetryEncoderTest` x3, `TelemetryRuntimeTest` x3, M6 |
| AC-6 | Task 5 | M2 |
| AC-7 | Task 4 | M3 |
| AC-8 | Device verification (Update path) | M7 |

## Citations sampled (wt, base code)

| Citation | Resolves |
|---|---|
| `TelemetryEvent.kt:131-148`, `readyReqMs` `:145` | yes |
| `StartupMarks.kt:19-25` ctor, `:90` onTimeout, `:92` scheduleCheck, `:94` emitWhenComplete | yes |
| `TelemetryRuntime.kt:24` ctor, `:43`/`:50` returns, `:55` fallback schedule, `:152-159`, `:163-169` | yes |
| `TelemetrySink.kt:128` `schedule` | yes |
| `RevealGate.kt:9` `REVEAL_CAP_MS = 2_500L` | yes |
| `PlayerScreen.kt:72` `PlayerScreenContent(`; `PlayerScreenContent.kt:41` `modifier`, `:51` root `Box` | yes |
| `DetailsVM.kt:532` `openPlayer`; `DetailsScreen.kt:27`; `MainScreenComponent.kt:121` `ModalNavigationDrawer`; `HistoryScreenContent.kt:157` | yes |
| `app/build.gradle.kts:435` profileinstaller, `:436` `baselineProfile(...)`, `:439-446` block | yes |
| `Makefile:23` `GRADLE`, `:123-124` `ITEST_ADB`, `:126` `.PHONY`, `:128` `itest` | yes |
| `tools/generate-baseline-profile.sh:52,101,103,104,134` | yes |
| `ManualTelemetryExecutor.kt:21` `schedule`, `:52` `runScheduled`; `StartupMarksTest.kt:14-20`; `TelemetryRuntimeTest.kt:45`, `:246` | yes |
| `baselineprofile/build.gradle.kts:44` uiautomator; `tools/android-apk-install-preserve:295` `install -r` | yes |

## Other checks

| Check | Result |
|---|---|
| Task order and build | Task 1 and Task 2 are independent. Task 5 consumes Tasks 2 and 3. Each task ends on the fast gate. Task 5's red tests are committed only with the regenerated files. |
| P0 unit rows | Each is marked `red` with a named test and the defect it catches. |
| Out of scope | No task touches R8 rules, `coil-video`/`appcompat` or `AppUpdateInstaller`. |
| Blocking open questions | None. Q1-Q5, Q-A1, Q-A2 and Q-D1..Q-D3 are answered (spec Decisions). |
