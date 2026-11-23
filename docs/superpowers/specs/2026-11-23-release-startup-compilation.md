# Refresh the baseline profile and prove its effect on the TV box

Status: approved
Date: 2026-11-23
Slug: release-startup-compilation
Kind: improvement
Size: M
Tracks: perf

## Problem / Why

Base e4c3b5aa. Analyst, 2026-11-23. Builds on `research.md`; the lines I rely on (`BaselineProfileGenerator.kt:24-120`, `tools/generate-baseline-profile.sh:105-148`, `Makefile:124-136`, `docs/release.md:79-163`, `TelemetryEvent.kt:131-148`) were re-read at base and hold. Owner answers Q1-Q5 are in `state.md` (Open questions, Decisions) and are applied here as written.

kind: improvement. size: M. tracks: perf. Device verification: **yes** (startup, build variants, packaged profile; the goal is a number measured on the box).

Size reason: one goal (a current, regenerated baseline/startup profile and proof of its effect) that crosses build tooling, the `:baselineprofile` module, telemetry and the release doc, and sits in the risk areas "the baseline profile" and "startup" of `.flow/roles/analyst.md`.

research.md: Request. Scope per Q1/Q4/Q5 = research.md: Options, A.

### Measurement states
| State | How it is reached |
|---|---|
| S0 as installed | install-preserve reinstall of the same APK, then `dump-profiles` shows empty cur and ref profiles; cold starts with no compile |
| S1 profile only | install-preserve reinstall, ProfileInstaller broadcast, force-stop, `dump-profiles` shows only the embedded profile, then `cmd package compile -m speed-profile -f` before any launch |
| S2 forced after use | S0 plus several launches, then `compile -m speed-profile -f` (the startup-first-input-on-tv state) |

### Files this run touches
| File | Change |
|---|---|
| `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt:24-120` | CUJs for today's UI: startup through splash reveal; side-rail navigation replaces `tabNavigationProfile` (top tabs removed); browse into Details (`ui/feature/details/component/DetailsScreen.kt:27`); start playback (`ui/feature/player/component/PlayerScreen.kt:34`) and Back |
| `Makefile:124-136` (next to `itest`) | new target running `:app:generateBaselineProfile` through the private adb server on one `DEVICE`, leaving APKs installed; `GRADLE` overridable to `./tools/agentw` in a worktree (`Makefile:23`) |
| `tools/generate-baseline-profile.sh:52,105-148` | every adb call with `-s`; calls the make target instead of `./gradlew` |
| `app/src/main/generated/baselineProfiles/{baseline,startup}-prof.txt` | regenerated on host.docker.internal:5555 (AVD puber_tv_36, owner 2026-11-23) |
| `app/src/main/java/com/kino/puber/core/telemetry/` (`TelemetryEvent.kt:131-148`, `TelemetryEncoder.kt:43`, `TelemetryRuntime.kt:42-55`) | `ProfileVerifier` compilation status code in the telemetry stream once per cold start, read off the main thread, only while recording |
| `docs/release.md:93-163` | refresh step: when it is required and the make-target command; drop the `connected*AndroidTest` sanity check the hook refuses |
| `docs/superpowers/evidence/` | A/B and verify evidence (box) |

### Risks (analyst)
| Risk | Guard |
|---|---|
| S1 and S2 differ in JIT samples; quoting S2 as the user's state overstates the gain | AC-1 states defined above; gate is S1 (AC-4) |
| Box load or API health swings between states | all states same day, load average logged (AC-1) |
| Generation reinstalls/clears `com.kino.puber.stage` on the emulator and drops its pairing | authorised (Q3); tokens re-seeded from `.todo/baseline-profile-auth.env` (`BaselineProfileGenerator.kt` `seedAuthState`) |
| Player CUJ depends on live content and network on the emulator; a failed start records a partial profile without failing | CUJ asserts the player screen is reached; AC-3 |
| `ProfileVerifier` returns `ERROR_UNSUPPORTED_API_VERSION` on API 30 and does file I/O | code recorded as is; background thread, recording only (AC-5) |
| Background dexopt may never run on an always-on TV box, so users stay in S0 after install/update | AC-5 makes it observable; the `.dm` route leaves this run (Leaving this run) |

## Acceptance criteria

- AC-1. Given the box on one day with the base APK (old profile), When S0, S1 and S2 are each measured over at least 15 cold starts, Then the evidence records first-key latency (median, p95) and the telemetry `startup` line for each state, plus `dumpsys package` compile status and box load average per state.
- AC-2. Given the new make target and `DEVICE=host.docker.internal:5555`, When generation runs, Then only that serial is seen by the run's adb server (logged as for `itest`), the guard hook is not tripped, and both profile files are rewritten.
- AC-3. Given the regenerated `baseline-prof.txt`, When it is searched, Then it has rules under `com/kino/puber/ui/feature/details` and `ui/feature/player` and for `TelemetryRuntime`, `RevealGate`, `PlayerVM`, `DetailsScreen`, and none of the 6 stale classes listed in test-design.md: Stale classes at base (scan at base e4c3b5aa; research.md's count of 10 counted nested entries loosely); `startup-prof.txt` holds startup classes only.
- AC-4. Given the new build in S1 on the box the same day as AC-1, When 15 or more cold starts are measured, Then first-key p95 <= 199 ms; S0 for the new build is reported next to it and not gated (Q5).
- AC-5. Given telemetry recording on, When a cold start completes, Then exactly one telemetry line carries the `ProfileVerifier` status code (unit test with a fake status source; box observation shows the code for S0 and S1). With recording off, `ProfileVerifier` is not called (unit test).
- AC-6. Given `assembleProdRelease` of the new build, When the APK is listed, Then `assets/dexopt/baseline.prof` and `baseline.profm` are present, and `check.full` passes.
- AC-7. Given `docs/release.md`, When a maintainer reads Refresh Baseline Profiles, Then it names when a refresh is required (Open questions, 1) and the single make-target command, with no `connected*AndroidTest` command left.
- AC-8. Given the base `devBenchmarkRelease` (old profile) installed on the box, When the new `devBenchmarkRelease` is installed over it with `tools/android-apk-install-preserve` (`install -r`, data kept) and launched once, Then the `startup` line carries `prof` = 2 (enqueued) or 1, and after `cmd package bg-dexopt-job com.kino.puber.stage` (conductor) the next cold start carries `prof` = 1 and `dumpsys package` shows `[status=speed-profile] [reason=bg-dexopt]`. Compiled code at update time stays in `update-install-dex-metadata`. (architect, design Q1; accepted by the owner)

## Out of scope

| Part | Goes to | Note |
|---|---|---|
| Narrowing broad R8 `-keep` rules (`app/proguard-rules.pro:49-163`, research.md: Current state, "Broad R8 rules") | `dependency-prune` (idea), absorbing `perf-deferred` P5 | release-only crash risk; needs its own release walk on the box |
| Dropping `coil-video` and `appcompat` (`app/build.gradle.kts:353,374`; `themes.xml:3-7` is load-bearing for the splash) | `dependency-prune` (idea) | already owns both |
| Compiled code right after an in-app update: `PackageInstaller` session with `base.apk` + `base.dm` (research.md: Options, C) | new idea run, proposed slug `update-install-dex-metadata`, `source: release-startup-compilation` | replaces `Intent.ACTION_VIEW` in `AppUpdateInstaller.kt:32-53` |

## Design

Base e4c3b5aa. ACs: `brief.md#acceptance-criteria`. Owner answers: `state.md` Decisions.

### Decisions

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D1 | Which build type is measured on the box | `devBenchmarkRelease` (`app/build.gradle.kts:216-219`): non-debuggable, R8-minified like `release` (`app/build.gradle.kts:185-193`), debug-signed, same package `com.kino.puber.stage` and versionCode (`app/build.gradle.kts:54`) as the box install | A profile is used only by a non-debuggable build; R8 is needed for startup-profile DEX layout; same signer and versionCode keep `install -r` a compatible replace. The startup-first-input-on-tv evidence used this type (`docs/superpowers/evidence/2026-11-22-startup-first-input-on-tv-baseline-device.md:8`) |
| D2 | How the worktree's own build reaches the box | `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleDevBenchmarkRelease` in `wt`, then `tools/android-apk-install-preserve install-preserve --serial 192.168.1.110:5555 --package com.kino.puber.stage --apk <wt>/app/build/outputs/apk/dev/benchmarkRelease/app-dev-benchmarkRelease.apk` (`adb -s … install -r`, `tools/android-apk-install-preserve:295-296`). The AC-1 base APK: `git archive e4c3b5aa` into the scratchpad, same task with that tree's own `tools/agentw`, same install command. Evidence records apk sha256 and on-device `base.apk` sha256 | Data preserved, no implicit Gradle install target (AGENTS.md); the base-reference recipe is the one already used (`…-baseline-device.md:10`). `auth-save` cannot run on a non-debuggable build (`…-verify-device.md:9`); install-preserve is the protection |
| D3 | Generation must not touch `com.kino.puber` | Replace the all-variant dependency `baselineProfile(project(":baselineprofile"))` (`app/build.gradle.kts:436`) with the dev-flavor one `"devBaselineProfile"(project(":baselineprofile"))`. `mergeIntoMain = true` (`app/build.gradle.kts:439-446`) still packages the files from `src/main` into every variant, prod included | With `mergeIntoMain` only `:app:generateBaselineProfile` exists (`agentw help --task :app:generateDevBaselineProfile`: "not found … candidates: generateBaselineProfile"), and it runs every producer flavor, prod targeting `com.kino.puber` (`baselineprofile/build.gradle.kts:24-27`), which Q3 does not authorise |
| D4 | Hook-compliant generation | New `Makefile` target `baseline-profile` beside `itest` (`Makefile:123-136`): same private server (`ITEST_ADB`, `Makefile:124`), prints what it sees, runs `$(GRADLE) :app:generateBaselineProfile -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true $(PROFILE_ARGS)`, kills the server, keeps the exit status. `GRADLE` overridable (`Makefile:23`) | Same isolation that makes `itest` safe; the hook refuses `connected*AndroidTest` but not a make target |
| D5 | Script shape | `tools/generate-baseline-profile.sh`: requires `DEVICE`; every adb call `adb -s "$DEVICE"` (`:52`, `:103-104`); `GRADLE` defaults to `$ROOT_DIR/tools/agentw`; generation through `make -C "$ROOT_DIR" DEVICE=… GRADLE=… PROFILE_ARGS=… baseline-profile` instead of `./gradlew` (`:134`); bootstrap builds `assembleDevNonMinifiedRelease` and installs with install-preserve instead of `installDevNonMinifiedRelease` (`:101`), and tries the token export before it prompts (`:110`) | `./gradlew` is refused in a worktree; an implicit install target may wipe data; an already paired emulator then needs no human |
| D6 | Token cache in a worktree | Default stays `$ROOT_DIR/.todo/baseline-profile-auth.env` (`:5`); a worktree run passes `PUBER_BASELINE_AUTH_CACHE=<main>/.todo/baseline-profile-auth.env`. No cache exists today (main has no such file) | A worktree has no `.todo`; the cache stays out of Git and out of the worktree |
| D7 | CUJs | `startupProfile` (`BaselineProfileGenerator.kt:24-33`) waits past the reveal, bounded by the gate cap (`REVEAL_CAP_MS`, `core/startup/RevealGate.kt:9`); `browseAndDetailsProfile` (`:35-75`) keeps its path, comments fixed to the rail UI; `tabNavigationProfile` (`:77-122`) replaced by `railNavigationProfile` (LEFT opens the drawer of `ui/feature/main/component/MainScreenComponent.kt:121`, DOWN/OK through each entry, back to content); new `playbackProfile`: Home card → Details (`ui/feature/details/component/DetailsScreen.kt:27`) → Play (`ui/feature/details/vm/DetailsVM.kt:536`) → player held about 5 s → Back twice. Only `startupProfile` keeps `includeInStartupProfile = true` | Today's UI; AC-3 coverage of details, player, `RevealGate`, `TelemetryRuntime` |
| D8 | How `playbackProfile` proves the player was reached | `PlayerScreen.kt:72` passes `modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("player")` to `PlayerScreenContent` (which takes `modifier`, `PlayerScreenContent.kt:41`); the CUJ fails unless `device.wait(Until.hasObject(By.res("player")), …)` holds | Brief risk "a failed start records a partial profile without failing"; precedent `HistoryScreenContent.kt:157`; no MediaSession exists to probe instead |
| D9 | How the compile status is read | `ProfileVerifier.getCompilationStatusAsync()` listener, result posted to the telemetry executor. The app never calls `ProfileVerifier.writeProfileVerification` | ProfileInstaller calls `writeProfileVerification(context, false)` after it writes the profile (javap of profileinstaller 1.4.1 `ProfileInstaller`, offsets 148-163, 198-200), which returns the cached `sCompilationStatus`; an earlier app call would freeze the pre-install status for the process and the cache file. The future resolves only from the library's own verification |
| D10 | Where the code goes (Q-A2) | Field `prof` (int, `CompilationStatus.getProfileInstallResultCode()`) on the `startup` line, absent when not known | One line per cold start; codes already distinguish 0/1/2/3 and errors |
| D11 | When the line waits for `prof` | Only on the ready path: `StartupMarks` built with `awaitProfileStatus = true` counts ready as complete only once `prof` is in; the reveal+5 s bound, `FORCED` and the 60 s fallback emit as today, with or without `prof` | ProfileInstaller runs 5000-6000 ms after its first frame (`ProfileInstallerInitializer.DELAY_MS = 5000` + `Random.nextInt(1000)`), often after ready; the bound keeps the line's lateness capped. Default `false` keeps existing `StartupMarksTest` cases unchanged |
| D12 | Profile in every release APK (owner, 2026-11-23) | No build change: `mergeIntoMain` + `saveInSrc` (`app/build.gradle.kts:439-446`) put `src/main/generated/baselineProfiles` into every non-debuggable variant as `assets/dexopt/baseline.prof` + `baseline.profm`; the release build packages them with no step beyond the refresh. `docs/release.md:79-92` says so, AC-6 checks `prodRelease`; the box build (D1) is checked the same way | An app update ships the new profile; ProfileInstaller enqueues it on the first launch after the update; background dexopt compiles it. Immediate compile at update time stays in `update-install-dex-metadata` (`.dm` session install) |
| D13 | Refresh rule (Q-A1) | `docs/release.md:94-142` rewritten: refresh before every release whose diff touches startup, Home, Details or the player, and at least every minor release; one command (`DEVICE=host.docker.internal:5555 ./tools/generate-baseline-profile.sh`, which runs `make baseline-profile`); `:154-163` (`connected…AndroidTest` sanity check) removed | AC-7 |

### Changes by layer

| Layer | File | Change |
|-------|------|--------|
| API / repository | N/A: no API or repository change | |
| ViewModel state and actions | N/A: no ViewModel change | |
| Screen and DI module | `app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerScreen.kt:72` | test tag on `PlayerScreenContent` (D8); no DI change |
| Compose content | N/A: no layout or visual change | |
| Resources | N/A | |
| Telemetry core | `app/src/main/java/com/kino/puber/core/telemetry/TelemetryEvent.kt:131-148` | `Startup` gains `val profileStatus: Int? = null` |
| | `core/telemetry/TelemetryEncoder.kt:67-81` | `putStartup` writes `prof` when non-null |
| | `core/telemetry/StartupMarks.kt:19-25,94-122` | constructor `awaitProfileStatus: Boolean = false`; `markProfileStatus(code: Int)` keeps the first value and calls `scheduleCheck()`; `emitWhenComplete` adds the D11 condition; `emit` passes the value |
| | new `core/telemetry/ProfileStatusSource.kt` | `fun interface ProfileStatusSource { fun onStatus(callback: (Int) -> Unit) }`; production object wraps `ProfileVerifier.getCompilationStatusAsync().addListener(…)` with a direct executor and catches `ExecutionException` |
| | `core/telemetry/TelemetryRuntime.kt:24-31,42-56,152-170` | constructor takes `profileStatus: ProfileStatusSource` (default no-op); `start`, after the marks are built (`:53-55`), registers `profileStatus.onStatus { code -> sink.schedule(0) { marks.markProfileStatus(code) } }` (`TelemetrySink.kt:128`); `install` passes the production source and builds marks with `awaitProfileStatus = true`. Recording off returns at `:43`, so the source is never touched |
| Build | `app/build.gradle.kts:436` | D3 |
| | `Makefile:37,123-136` | D4 target, listed in `help` and `.PHONY` |
| | `tools/generate-baseline-profile.sh:5,52,101-110,115-135` | D5, D6 |
| Generator module | `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt:24-122` | D7, D8 |
| Generated | `app/src/main/generated/baselineProfiles/{baseline,startup}-prof.txt` | regenerated on host.docker.internal:5555 |
| Docs | `docs/release.md:79-163` | D12, D13 |

### Alternatives rejected

- Calling `ProfileVerifier.writeProfileVerification` at startup: poisons the library's cached status (D9).
- A `meta` event for the status: Q-A2 chose the `startup` line.
- Disabling the prod variant in `baselineprofile/build.gradle.kts` via `androidComponents.beforeVariants`: also drops prod `StartupBenchmarks`; kept as fallback if D3 fails to resolve.
- `deploy` build type for the box (`app/build.gradle.kts:202-210`): not minified, so no startup-profile DEX layout; not what users get.
- Matching the player by a localized `contentDescription`: depends on locale and on auto-hidden controls.

### Risks

| # | Risk | Where it lives | Guard |
|---|------|----------------|-------|
| 1 | Correctness: any app call of `writeProfileVerification` before ProfileInstaller freezes a wrong status for the process and its cache file | `ProfileStatusSource.kt` | D9; review checks no `writeProfileVerification` in `app/src/main` |
| 2 | Rollout: `:app:generateBaselineProfile` still runs the prod flavor if the per-flavor dependency (D3) is not honoured, installing `com.kino.puber` on the emulator | `app/build.gradle.kts:436` | generation log must list only `DevNonMinifiedRelease` test tasks before the run is accepted; stop and use the fallback otherwise |
| 3 | Data shape: a worktree build has empty `CLIENT_SECRET` and API domain (`app/build.gradle.kts:58-61`); seeded `api_domain` covers the domain, but an expired access token cannot refresh, Home stays empty and the details/player CUJs record nothing | `BaselineProfileGenerator.kt` `seedAuthState` (`:124-144`), Q2 | D8 assertion fails the run; AC-3 grep |
| 4 | Correctness of S0/S1: `cmd package compile --reset` is refused for shell on the box (API 29, SecurityException), and the install broadcast starts the process, so `Application.onCreate` runs and JIT samples may be saved before the forced compile | box protocol (test design) | reset = reinstall the same APK with `tools/android-apk-install-preserve`, then `cmd package dump-profiles com.kino.puber.stage` shows empty cur and reference profiles; for S1 force-stop right after the broadcast result, `dump-profiles` shows only the embedded profile, compile before any launch; `dumpsys package` per state (AC-1) |
| 5 | Lifecycle: on the ready path the `startup` line now waits for `prof`; a process killed before reveal+5 s loses the line | `StartupMarks.kt:94-100` | bound path unchanged (`LINE_WAIT_AFTER_REVEAL_MS`, `:10`); unit test for ready-without-status then bound |
| 6 | Concurrency: the listener fires on a library thread | `TelemetryRuntime.kt:53-55` | only `sink.schedule` from the listener; marks use atomics (`StartupMarks.kt:26-38`) |
| 7 | Correctness of the update check (Q1): `bg-dexopt-job` merges JIT samples from the first launch, so "compiled from the new profile" is embedded profile plus one launch | box protocol | `dumpsys package` `[reason=bg-dexopt]` plus `prof` = 1 on the next start; reported as such, not as S1 |
| 8 | Concurrency: `make baseline-profile` and `make itest` share port 5038 | `Makefile:123` | one leasing agent at a time (AGENTS.md) |
| 9 | Performance: `getCompilationStatusAsync` is a static future, no I/O on the caller; nothing runs while recording is off | `TelemetryRuntime.kt:43` | AC-5 off-path unit test |

## Test design

Base e4c3b5aa. ACs: `brief.md#acceptance-criteria` (AC-1..AC-7) and AC-8 (`design-architect.md#open-questions`, accepted in `state.md` Decisions). Decisions D1-D13 and risks R1-R9: `design-architect.md`. Levels: `.flow/roles/test-designer.md`.

### Risk profile

P = probability, I = impact, score = P x I.

| Risk | P | I | Score | Test |
|---|---|---|---|---|
| R2: `:app:generateBaselineProfile` still runs the prod producer and installs `com.kino.puber` on the emulator (D3 not honoured) | 2 | 3 | 6 | M1 steps 3, 6 |
| R3 / brief: an expired token or a failed player start records a partial profile without failing | 2 | 3 | 6 | M1 step 5 (`playbackProfile` asserts `By.res("player")`), `baselineProfileCoversDetailsPlayerAndStartupClasses` |
| Brief: S1 and S2 differ in JIT samples, so quoting S2 overstates the gain | 3 | 2 | 6 | M5: the gate reads S1 only |
| R5: the ready path waits for `prof`, so a line is lost or never written when the status never arrives | 2 | 2 | 4 | `boundEmitsWithoutProfileStatusWhenAwaited`, `forcedRevealEmitsWithoutProfileStatusWhenAwaited` |
| R4: the install broadcast starts the process and JIT samples reach the S1 compile | 2 | 2 | 4 | M4 S1 steps (force-stop, compile before launch, `dumpsys`) |
| Brief: box load or API health swings between states | 2 | 2 | 4 | M4, M5 same day, load average per state |
| Brief: background dexopt never runs on the always-on box | 2 | 2 | 4 | M7 (`prof` on the update path) |
| Brief: generation clears `com.kino.puber.stage` on the emulator and drops its pairing | 2 | 2 | 4 | M1 step 1 (token cache present before the run) |
| `prof` written when null breaks the line's field catalogue | 1 | 3 | 3 | `everyEventTypeEmitsExactlyEnvelopePlusCatalogueFields`, `startupOmitsMarksNotReached` |
| R1: an app call of `writeProfileVerification` freezes a wrong status | 1 | 3 | 3 | review check: `rg writeProfileVerification app/src/main` returns nothing |
| R7: `bg-dexopt-job` merges first-launch JIT samples | 3 | 1 | 3 | M7 reports it as "embedded profile plus one launch", not as S1 |
| R6: the listener fires on a library thread | 1 | 2 | 2 | accepted; `profileStatusReachesMarksOnlyThroughTheTelemetryExecutor` (P2) |
| R8: `make baseline-profile` and `make itest` share port 5038 | 1 | 2 | 2 | accepted: one leasing agent at a time (AGENTS.md) |
| R9: `ProfileVerifier` touched while recording is off | 1 | 2 | 2 | `offStartNeverAsksProfileStatus` (AC-5) |
| D8 test tag changes player focus or semantics | 1 | 2 | 2 | accepted; M7 step 4 starts playback |
| Brief: `ERROR_UNSUPPORTED_API_VERSION` on API 30 | 1 | 1 | 1 | accepted: the code is recorded as is |

### Traceability

Unit tests sit in `app/src/test/kotlin/com/kino/puber/core/telemetry/` (existing classes, their fakes `FakeTelemetryClock`, `ManualTelemetryExecutor`, `FakeTelemetryStore`, `FakeSharedPreferences`, the `queuedMarks`/`runQueued` pattern of `StartupMarksTest`) and in the new `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt`, which reads `app/src/main/generated/baselineProfiles/*.txt` with the working-dir fallback of `ProguardRulesTest`. The fake `ProfileStatusSource` keeps the callback and counts `onStatus` calls. A test that does not compile on base counts as `red`; its behaviour assertion is what it proves after the change.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | device | P0 guard | M4 | baseline numbers for S0/S1/S2 exist before the new build is judged; missing state or under 15 starts fails the row |
| AC-2 | device | P0 guard | M1 | fails if the run's adb server sees a second serial, the hook refuses a command, a prod task runs, or either profile file is unchanged |
| AC-2 | manual | P1 | M8 | fails if the script reaches adb or Gradle without `DEVICE` |
| AC-3 | unit | P0 red | `BaselineProfileContentTest.baselineProfileCoversDetailsPlayerAndStartupClasses` | rules exist under `com/kino/puber/ui/feature/details/`, `ui/feature/player/` and for `TelemetryRuntime`, `RevealGate`, `PlayerVM`, `DetailsScreen`; fails on the base profile and on a regeneration whose details or player CUJ recorded nothing |
| AC-3 | unit | P0 red | `BaselineProfileContentTest.baselineProfileHasNoRemovedTopTabClasses` | no rule for any class in `Stale classes at base` below, by four prefixes: `com/kino/puber/ui/feature/main/toptabs/` (`TopTabBarKt`, `TopTabMainContentKt`, `TopTabFocusedRegion` and their lambdas), `com/kino/puber/ui/feature/main/model/TabType`, `com/kino/puber/core/model/NavigationMode`, `com/kino/puber/domain/interactor/auth/model/AuthState`; fails if the profile is not regenerated |
| AC-3 | unit | P1 guard | `BaselineProfileContentTest.startupProfileHasNoDetailsOrPlayerRules` | fails if `includeInStartupProfile = true` spreads beyond `startupProfile` (D7) |
| AC-4 | device | P0 guard | M5 | new build S1 first-key p95 <= 199 ms; fails on the threshold or on fewer than 15 starts |
| AC-5 | unit | P0 red | `TelemetryRuntimeTest.coldStartStartupLineCarriesProfileStatusFromSource` | recording on, fake source delivers 1, marks reach ready: exactly one `startup` line, `prof` = 1; fails if `start` never registers the source or `install` builds marks without the code reaching `emit` |
| AC-5 | unit | P0 red | `TelemetryRuntimeTest.offStartNeverAsksProfileStatus` | no `KEY_ENABLED_AT`: fake source `onStatus` count 0; fails if registration moves above the switch check |
| AC-5 | unit | P0 red | `StartupMarksTest.readyLineWaitsForProfileStatusWhenAwaited` | `awaitProfileStatus = true`: first content, reveal, `markReady(0)`, `runQueued(0)` writes nothing; `markProfileStatus(2)` then `runQueued(0)` writes one line with `profileStatus` 2; fails if the D11 condition is dropped |
| AC-5 | unit | P0 red | `StartupMarksTest.boundEmitsWithoutProfileStatusWhenAwaited` | ready with no status, `runQueued(LINE_WAIT_AFTER_REVEAL_MS)` writes one line with `profileStatus` null; fails if the bound path also waits for `prof` (R5) |
| AC-5 | unit | P1 red | `StartupMarksTest.forcedRevealEmitsWithoutProfileStatusWhenAwaited` | `RevealCause.FORCED` emits with no status; fails if `FORCED` waits |
| AC-5 | unit | P1 red | `StartupMarksTest.profileStatusKeepsTheFirstValue` | 2 then 1 before emit: line carries 2; a status after emit writes no second line |
| AC-5 | unit | P1 guard | `StartupMarksTest.readyLineEmitsWithoutProfileStatusByDefault` | default constructor: ready path emits on `runQueued(0)` with no status; fails if the default becomes `true` |
| AC-5 | unit | P0 red | `TelemetryEncoderTest.startupWritesProfWhenKnown` | `Startup(profileStatus = 327680)` encodes `prof` = 327680; fails if `putStartup` skips the field |
| AC-5 | unit | P1 guard | `TelemetryEncoderTest.everyEventTypeEmitsExactlyEnvelopePlusCatalogueFields`, `TelemetryEncoderTest.startupOmitsMarksNotReached` | no `prof` key when the status is null |
| AC-5 | unit | P2 | `TelemetryRuntimeTest.profileStatusReachesMarksOnlyThroughTheTelemetryExecutor` | callback fired, executor not run: the line is not yet written; after `runScheduled` it carries `prof`; fails if the listener calls marks directly (R6) |
| AC-5 | device | P0 guard | M6 | `prof` present on the box `startup` line in S0 and S1 of the new build |
| AC-6 | manual | P0 guard | M2 | fails if either profile asset is missing from `prodRelease` or the box build, or `check.full` fails |
| AC-7 | manual | P1 | M3 | fails if the refresh rule or the single command is missing, or a `connected*AndroidTest` command remains |
| AC-8 | device | P0 guard | M7 | update over the base build: `prof` 2 or 1 on first launch, 1 after `bg-dexopt-job` with `[reason=bg-dexopt]` |

### Stale classes at base

Scan: every `com/kino/puber` class in `app/src/main/generated/baselineProfiles/baseline-prof.txt` (385 outer classes) against the classes declared in `app/src/main` and `app/src/nonMinifiedRelease` at base (package and name). Generated `ComposableSingletons`, `R` and the `profile/BaselineProfileAuth*` classes (`nonMinifiedRelease`) exist. 6 outer classes are missing, 38 profile entries with their nested and lambda classes:

| Stale class | Entries | Covered by prefix |
|---|---|---|
| `com/kino/puber/ui/feature/main/toptabs/TopTabMainContentKt` | 23 | `ui/feature/main/toptabs/` |
| `com/kino/puber/ui/feature/main/toptabs/TopTabBarKt` | 8 | `ui/feature/main/toptabs/` |
| `com/kino/puber/ui/feature/main/toptabs/TopTabFocusedRegion` | 1 | `ui/feature/main/toptabs/` |
| `com/kino/puber/ui/feature/main/model/TabType` (incl. `$Creator`; the class now lives in `domain/model`) | 2 | `ui/feature/main/model/TabType` |
| `com/kino/puber/domain/interactor/auth/model/AuthState` (incl. `$Code`, `$Success`; now `domain/model`) | 3 | `domain/interactor/auth/model/AuthState` |
| `com/kino/puber/core/model/NavigationMode` | 1 | `core/model/NavigationMode` |

### Bugs

N/A: kind improvement; the red rows above are written first.

### Manual and device checks

Box checks run on 192.168.1.110:5555 under a lease, `adb -s` every call, `com.kino.puber.stage` only; `com.kino.puber` is never touched. The conductor runs every `cmd package compile`, install broadcast and `bg-dexopt-job` (`state.md` Decisions); qa-device installs, launches, injects keys and reads telemetry. Builds: `devBenchmarkRelease` per D1/D2, base built from `git archive e4c3b5aa`. Telemetry recording is on (switched on less than 7 days ago); a cold start with no `startup` line stops the check.

Cold-start and key method: the injector and anchors A/B/C of `docs/superpowers/evidence/2026-11-23-startup-first-input-on-tv-verify-5-device.md` (Device and build), 15 starts per state (5 per anchor). First-key latency per start = `key_lat.lat_ms`; a start with a `key` line and no `key_lat` line counts as <= 50 ms. p95 is nearest rank (for n < 20 it is the maximum).

Per state, recorded once: `dumpsys package com.kino.puber.stage` Dexopt state line, `cat /proc/loadavg` before and after the 15 starts, apk sha256 and on-device `base.apk` sha256.

| State | Steps (conductor, then qa) |
|---|---|
| S0 | `am force-stop`; install-preserve reinstall of the same APK, then `cmd package dump-profiles com.kino.puber.stage` shows empty cur and ref profiles; no compile; `dumpsys` shows the install-time filter, no `speed-profile` from a forced compile |
| S1 | `am force-stop`; install-preserve reinstall of the same APK; `am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE -n com.kino.puber.stage/androidx.profileinstaller.ProfileInstallReceiver` (result code logged); `am force-stop` at once; `dump-profiles` shows only the embedded profile; `cmd package compile -m speed-profile -f com.kino.puber.stage`; `dumpsys` shows `[status=speed-profile]`; only then the first launch |
| S2 | S0, then 5 cold starts with D-pad use (not measured), `am force-stop`, `compile -m speed-profile -f`, `dumpsys` |

| # | Check | Steps | Expected | Must not happen |
|---|---|---|---|---|
| M1 | AC-2 generation (host.docker.internal:5555, implementer, plan Task 5b) | 1. `<main>/.todo/baseline-profile-auth.env` exists (D6, Q-D3) and `adb -s emulator-5554 shell pm path com.kino.puber` output saved. 2. In `wt`: `AGENT_GRADLE_SERIAL=1 DEVICE=emulator-5554 PUBER_BASELINE_AUTH_CACHE=<main>/.todo/baseline-profile-auth.env ./tools/generate-baseline-profile.sh` with the `.env` variables of Q-D2 in that command's environment only. 3. Log line after "sees:" lists only `emulator-5554`; Gradle task list holds `DevNonMinifiedRelease` producer tasks only. 4. Exit 0. 5. `playbackProfile` and `railNavigationProfile` pass (a missing `player` tag fails the run). 6. `pm path com.kino.puber` unchanged from step 1. 7. `git -C <wt> diff --stat` lists both `baseline-prof.txt` and `startup-prof.txt`; `git -C <wt> status --short` shows no new file with secrets | One serial; both files rewritten; `BaselineProfileContentTest` green on the new files | guard-hook refusal; a `Prod` task; `com.kino.puber` installed or changed; any adb call reaching the box; credentials written into `wt` |
| M2 | AC-6 packaged profile | 1. `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleProdRelease :app:assembleDevBenchmarkRelease`. 2. `unzip -l` each APK. 3. `check.full` | `assets/dexopt/baseline.prof` and `assets/dexopt/baseline.profm` in both APKs; `check.full` exit 0 | an APK without either asset; a failing unit, detekt or lint task |
| M3 | AC-7 release doc | 1. Read `docs/release.md` Refresh Baseline Profiles. 2. `rg -n 'connected.*AndroidTest' docs/release.md` | the D13 rule (startup, Home, Details, player, every minor release) and the one command `DEVICE=host.docker.internal:5555 ./tools/generate-baseline-profile.sh`; `rg` returns nothing | a second generation command; `./gradlew` as the worktree route |
| M4 | AC-1 baseline (base build, box) | 1. Install base `devBenchmarkRelease` with install-preserve. 2. States in order S0, S2, S1 (`state.md` Decisions), 15 starts each. 3. Record per state: first-key median and p95, per-start `startup` line, `dumpsys` line, load average | three complete state blocks in one evidence file, same day | a state with under 15 starts; a state without its `dumpsys` line; uninstall or data clear |
| M5 | AC-4 gate (new build, box, same day as M4) | 1. Install the new `devBenchmarkRelease` over the base with install-preserve. 2. S1, 15 starts. 3. S0, 15 starts (reported). 4. Table: base S0/S1/S2 next to new S1/S0 | new S1 first-key p95 <= 199 ms | the gate read from S2 or from a state whose `dumpsys` line does not match its definition; a different day from M4 |
| M6 | AC-5 on the box | from M5: `prof` of every `startup` line in new S0 and new S1 | each line has `prof`; S1 lines carry 1; S0 codes recorded as seen | a cold start with two `startup` lines; a line lost on a start that reached reveal |
| M7 | AC-8 update path | 1. Base build installed and launched once (any state). 2. Install the new build with install-preserve (`install -r`), no compile. 3. Cold start, wait 10 s, read the `startup` line. 4. Open a Details card, press OK on Play, wait 5 s, BACK twice. 5. Conductor: `cmd package bg-dexopt-job com.kino.puber.stage`. 6. `dumpsys` line. 7. Cold start, read the `startup` line | step 3: `prof` 2 or 1; step 4: player starts and BACK returns to Details then Home; step 6: `[status=speed-profile] [reason=bg-dexopt]`; step 7: `prof` 1 | data loss after `install -r` (login screen); `prof` absent; this state quoted as S1 |
| M8 | script needs a serial | `DEVICE= ./tools/generate-baseline-profile.sh` in `wt` | non-zero exit with a message naming `DEVICE`, before any adb or Gradle output | an adb or Gradle command runs |

### Perf

| Item | Value |
|---|---|
| Metric | first-key latency per cold start (`key_lat.lat_ms`, <= 50 ms when only a `key` line exists); secondary, not gated: `startup` `revealed` and `first_content` medians |
| Method | M4/M5 method above, box 192.168.1.110:5555, `devBenchmarkRelease`, 15 starts per state |
| Baseline (before implementation) | M4: base build in S0, S2, S1, one day |
| Pass threshold | new build in S1: p95 <= 199 ms (AC-4, Q5); new S0 reported next to base S0, not gated |

### UNTESTABLE

None.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1-Q5 | research.md Open questions (split, box compile-state commands, emulator generation, post-update compile route, S1 gate) | no | answered | see Decisions |
| Q-A1 | When does docs/release.md require a profile refresh? | no | answered | before every release whose diff touches startup, Home, Details or the player, and at least every minor release |
| Q-A2 | Where does the ProfileVerifier code go? | no | answered | a field on the telemetry startup line |
| Q-D1 | Add AC-8 (update path)? | no | answered | yes, as written |
| Q-D2 | Pass .env secrets to the one generation command only? | no | answered | yes, nothing written into the worktree |
| Q-D3 | Generator tokens from emulator-5554's paired app? | no | answered | yes; ask the owner to pair only if that fails |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-23 | Q1, Q4, Q5, Q-A1, Q-A2, Q-D1..Q-D3: recommended answers | owner (standing instruction: recommended) |
| 2026-11-23 | Q2: box compile-state commands for com.kino.puber.stage authorised; Q3: emulator-5554 reinstall/clear for generation authorised | owner |
| 2026-11-23 | Profile ships inside every release APK so an app update carries it; update path proven by AC-8 | owner |
| 2026-11-23 | Gate 1: approved as drafted (size M, tracks perf, AC-1..AC-8) | owner (standing instruction: recommended) |
| 2026-11-23 | codex: required; round caps lifted; device evidence on the box only | owner (standing instructions) |
| 2026-11-23 | Spec delta after gate 1 (no AC change): `compile --reset` refused on the box (API 29), replaced by an install-preserve reinstall verified with dump-profiles; test-design runScheduled wording; M1 run by the implementer; D7 citations fixed | conductor (from the box, architect plan Q1-Q3) |
| 2026-11-23 | Readiness-1 fixes (no AC meaning change): one Test design/Open questions copy; AC-3 names the 6 stale classes in Test design "Stale classes at base" (research.md said 10); device P0 rows marked guard | conductor (readiness-1) |
| 2026-11-23 | Plan spec deltas 4-7 (Task 5 replan, readiness-3/4): AC-2, M1, M3, D13 and the Files table name host.docker.internal:5555 (owner reading of AC-2); Q3 reinstall/clear authorisation no longer used; token seeding, cache and receiver removed, so M1 steps 1-2 are superseded by plan Task 5b and "Device verification after Task 5" | conductor (owner answers in run state.md) |
