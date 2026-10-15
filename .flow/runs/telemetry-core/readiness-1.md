# Readiness 1 - telemetry-core

Judge: final-judge, mode readiness, round 1. Date 2026-10-15.
Read against worktree `/home/user1/orca/workspaces/Puber/telemetry-core`, HEAD `e636e081` (base `39903d31`
+ spec `29b795a9` + plan). The worktree is clean. Inputs: spec (status approved), plan, `state.md`
(latest decision: minified device checks use `devBenchmarkRelease`, and `deploy` is unminified), AGENTS.md,
`.flow/roles/final-judge.md`.

## Verdict: READY

No blocking defects. Every AC maps to a plan task and to Test design rows. Every citation I sampled points
to the symbol the plan names. Each task leaves a compilable tree, and the tasks are ordered by dependency.
The plan stays inside Out of scope, and none of the open questions blocks. The notes at the end are for the
implementer and reviewers. They are not preconditions.

## 1. AC coverage (AC -> task -> tests)

| AC | Plan task(s) | Test design rows |
|---|---|---|
| AC-1 | 5 (fresh install), 9 (rows, indicator) | T01, T02, T03, T04, T05 (instr.), D2 |
| AC-1a | 5 (export via controller), 8 (paths XML), 9 (share UI) | T07, T08-T11, T12, T13, D4 |
| AC-2 | 3 (sink flag), 5 (off start), 6 (VM probes) | T15, T16, T17, T18 (P2), D1 |
| AC-3 | 1 (encoder), 2 (store), 3 (writer) | T19, T20, T21, T22, T23, D3 |
| AC-4 | 3 (drain auto-off), 5 (start auto-off) | T25-T28 |
| AC-5 | 2 (folders), 3 (caps, rotation, budget) | T29-T34 |
| AC-6 | 3 (writer thread) | T35, T36 |
| AC-7 | 1 (scrubber, names, encoder), 4 (err pipeline) | T38, T39, T40, T41, D3 audit |
| AC-8 | 1 (names), 8 (R8 rules) | T43, T44, T45, D5 on `devBenchmarkRelease` |
| AC-9 | 5 (startup marks), 6 (ready/act), 7 (nav), 2 (act folding) | T47-T53, D6 |
| AC-10 | 4 (tree, handler), 6 (coroutine path), 8 (auth probe, narrowed strip rule), 2/3 (fold, cap) | T55-T62, D7 |
| AC-10a | 5 (runtime enable/disable, prefs) | T65-T67, D8 |
| Static/regression | 6 (S1 grep, S3 doc), 9/10 (S4), 10 (S2) | S1-S4 |

The test seams from the Test design are all present as plan requirements: `TelemetryClock` with `FakeTelemetryClock`,
`TelemetryStore` with `FakeTelemetryStore` (thread names, throw, latch, unavailable), `TelemetryExecutor` with
`ManualTelemetryExecutor`, `Telemetry.install`/`resetForTests`, `TelemetryRuntime(prefs, clock, sink)` on
`FakeSharedPreferences` (Task 5 extends it with `getLong`/`putLong`), a test-local concrete `PuberVM`, and
`FakeSessionLogController`.

## 2. Citation sample (opened in the worktree)

All of these resolve to the symbol the plan claims:

- `core/ui/PuberVM.kt:30` (class, `router`-only ctor), `:46` (`started`), `:74-80` (reified overload delegating), `:82-84` (`updateViewState`), `:97-99` (`DefaultExceptionHandler`), `:135-140` (`open fun onAction`, snackbar body).
- All 19 override pairs in Task 6 (for example `HomeVM.kt:176,197`, `PlayerVM.kt:632,678`, `CollectionsVM.kt:37,38`, `DeviceSettingsVM.kt:135,175`). `grep` finds no `onAction` override in `test/` or `androidTest/`, and `PagingVM` does not override it.
- `core/logger/Logger.kt:7` (message overload) and `:11-19` (`Timber.tag(...).w(sanitizedThrowable, ...)`). The WARN-level tree therefore sees it. `CoroutinesErrorHandler.kt:10-14` (`t.log(t)`).
- `core/logger/SensitiveRequestLogPolicy.kt:40` (`internal class`), `:106` (`sanitizeText`), `:116-120` (`sanitizeThrowable`), `:257-272` (`private class SanitizedLogThrowable`).
- `app/proguard-rules.pro:15` (`InnerClasses`), `:90` (`-keepnames` PuberScreen), `:94`, `:130-132` (`LoggerKt.log(...)` strip). The Timber strip at `:123-127` covers only static `d/v/i`, so `Tree.w` survives.
- `app/build.gradle.kts:183-184` (release minify/shrink), `:202` (`deploy` `isMinifyEnabled = false`, which confirms the state.md correction), `:212-215` (`benchmarkRelease`, `initWith(release)`, debug signing), `:223` (dev `.stage` suffix shared by every build type, so the installs keep the same package), `:386` (kotlinx-serialization-json).
- `PuberApp.kt:67-72` (`onCreate`), `:77-83` (modules), `:88-92` (`initLogger`). `MainActivity.kt:54-56`.
- `FlowComponent.kt:225-226` and the `if/else` ending `:268`; `:708-709` and the end `:746`. `TabComponent.kt:107`; `TabRouter.kt:32`; `AppRouter.kt:156-168` (`Command`).
- `KinoPubApiClient.kt:194-202`, `:224-227` (`sessionExpiredFailure`).
- `res/xml/update_file_paths.xml:3-5`; `AndroidManifest.xml:66-74`.
- `DeviceSettingsContent.kt:83-90`, `:176-183`, `:185-187` (Developer filter), `:197-204`, `:244-255`, `:338-345`, `:373-384`, `:403-411`, `:465`, `:762-770`. `DeviceSettingsInformationItems.kt:147-158`. `DeviceSettingsItems.kt:80-92` (`SettingsListItem` with `role`, `enabled`, `focusableWhenDisabled`) and `:207-231` (`SettingsToggleItem` with `description`). `DeviceSettingsScreen.kt:36, 48-54`. `DeviceSettingsViewState.kt:10-15, 22-47`. `DeviceSettingsActions.kt:8-39`. `DeviceSettingsVM.kt:39-51` (internal class, so internal ctor types are legal), `:53`, `:66-69`, `:71-88`, `:99-125`. The file has 522 lines.
- `ContentSharer.kt:6-23`, `AppUpdateInstaller.kt:38-42`, `data/di/modules.kt:81`, `core/ui/uikit/model/Actions.kt:5`, `AppLocale.kt:21, 35-38, 72-73` (the plan's correction of the spec's `:307` is right: the file has 74 lines), `PuberTab.kt:19, 37`, `PuberScreen.kt:7-10`, `HomeVM.kt:71`, `HomeViewState.kt:19`, `docs/architecture/viewmodel.md:18,22,45`, `values/device_settings_strings.xml:17`, `values-en/strings.xml:99`, `TimedActionDebugTrace.kt:15-17`, `buildSrc/.../Versions.kt:6` (MinSdk 24).
- Test files: `FakeResourceProvider.kt:8-16`, `FakeSharedPreferences` init block, `KinoPubApiClientFailureTest.kt:37-54, 112-139`, `EpisodeSchedulePrivacyTest.kt:26-33`, `DeviceSettingsVMAutoTrailerTest.kt:89-103`, `DeviceSettingsContentFocusTest.kt` (612 lines, `:239`, `:348`).

## 3. Build stays green, order

- Tasks 1 -> 2 -> 3 -> 4 -> 5 build strictly on what the earlier tasks produced. Task 6 needs `Telemetry`, `Act`, `Ready` and `StartupMarks` (Tasks 1, 3, 5). Task 7 needs only Tasks 1 and 3. Task 8 needs Tasks 1 and 3. Task 9 needs the controller (Tasks 3 and 5) and the `handleAction` rename (Task 6). Task 10 is the full gate.
- Each task ends with the fast gate plus the touched tests and one commit. Task 6 makes `onAction` final, renames all 19 overrides and runs the whole unit suite in the same commit, so the tree is never half-renamed. `onAction` stays public, so callers and tests compile unchanged.
- The new `DeviceSettingsContent`/`DeviceSettingsPane` parameters have defaults, so the existing androidTest call sites still compile. Task 9 compiles `compileDevDebugAndroidTestKotlin`.
- Unit tests have no Android stubs (no `isReturnDefaultValues`). The plan keeps `android.*` off the paths that tests reach: `JvmTelemetryClock` is the default, and `Log.WARN` is a compile-time constant.

## 4. Scope and rules

- Nothing from Out of scope (`key`, `key_lat`, `frames`, `mem`, `net*`, `play`, exit reason, summary tool, gzip) appears in the plan. The splash-frame mark is explicitly skipped, as the spec allows.
- AGENTS.md rules are respected. Builds use `./tools/agentw` and never `./gradlew`. Tests are JUnit 5 with `MainDispatcherExtension` and `FakeResourceProvider`. Strings go in `res/values*` and the VM reads them through `ResourceProvider`. Koin gets a global `telemetryModule` in `PuberApp`, and `puberViewModel`/`viewModelOf` are untouched. Commits have no prefixes and no Co-Authored-By. Device work is handed to qa-device (lease, `-s`, install-preserve), and instrumented tests run only through `make DEVICE=... itest`.
- State.md decisions are followed: the minified checks use `devBenchmarkRelease`, `deploy` is used only for the non-debug Developer section, and Q1-Q3 are respected (existing row styles only, `-keepnames` with `key` unchanged, rotate-then-zip closed files with the previous archive replaced).
- The plan's deviations from the spec are stated and justified: `telemetryStartupContentClass` is `internal` rather than `protected`, and `shareFile` takes a `File` rather than a `Uri`.

## Notes (non-blocking, for implementer and reviewers)

1. Task 5 calls `executor.schedule(60_000)` "through a sink method", but Task 3 does not list such a method in Produces. Add something like `TelemetrySink.schedule(delayMs, task)` in Task 3 or 5 and record it.
2. `HomeVM.telemetryStartupContentClass`: prefer a getter (`get() = HomeViewState.Content::class.java`) over a backing field. A subclass field is still null while `PuberVM` code runs before `HomeVM` initialisers.
3. `Unavailable` keeps the switch off while `enabled_at_wall_ms` stays set, so a tap re-calls `setEnabled(true)`. Make sure `enable` never plants a second tree or wraps a second handler. The plan's "safe to call twice" covers this, and T65/T66 should assert a single tree.
4. In minified builds `ErrorScrubber`'s prefix filter does not drop library frames that R8 has renamed, and app frames are obfuscated (no retrace). D7 should report what `frames`/`et` actually look like in `devBenchmarkRelease` rather than claim "app frames only". AC-10 evidence must state this.
5. Task 1's "Done when" grep (`else ->` in `TelemetryEncoder.kt`) is file-wide. Scope it to the event `when`, or keep the other `when`s in that file free of `else`.
6. The spec's own Decisions table (spec line ~837) still lists the superseded "AC-8 on `deploy`" row without marking it superseded, and the spec's Baseline section calls `deploy` "R8 + resource shrinking". The conductor should sync the spec text with state.md when the docs are next touched. The plan's correction is already authoritative.
7. Device evidence: installing `devBenchmarkRelease` over `devDebug` is a same-package, same-signer `install -r`. Any owner-authorised install must be named in the gate Concerns.

## Fix list

None.
