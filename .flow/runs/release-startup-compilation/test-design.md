# Test design: release-startup-compilation

Base e4c3b5aa. ACs: `brief.md#acceptance-criteria` (AC-1..AC-7) and AC-8 (`design-architect.md#open-questions`, accepted in `state.md` Decisions). Decisions D1-D13 and risks R1-R9: `design-architect.md`. Levels: `.flow/roles/test-designer.md`.

## Risk profile

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

## Traceability

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
| AC-4 | unit | P0 guard | `BaselineProfileContentTest.baselineProfileMethodRulesAreHot` | every line of `baseline-prof.txt` containing `->` starts with `H` (plan delta, fix round 1); fails on a regeneration that skips the Makefile rewrite |
| AC-5 | unit | P0 red | `TelemetryRuntimeTest.coldStartStartupLineCarriesProfileStatusFromSource` | recording on, fake source delivers 1, marks reach ready: exactly one `startup` line, `prof` = 1; fails if `start` never registers the source or the source's value stops reaching `emit`; the `install` wiring of `awaitProfileStatus` is guarded only by M6 |
| AC-5 | unit | P0 guard | `TelemetryRuntimeTest.offStartNeverAsksProfileStatus` | no `KEY_ENABLED_AT`: fake source `onStatus` count 0; fails if registration moves above the switch check |
| AC-5 | unit | P0 red | `StartupMarksTest.readyLineWaitsForProfileStatusWhenAwaited` | `awaitProfileStatus = true`: first content, reveal, `markReady(0)`, `runQueued(0)` writes nothing; `markProfileStatus(2)` then `runQueued(0)` writes one line with `profileStatus` 2; fails if the D11 condition is dropped |
| AC-5 | unit | P0 red | `StartupMarksTest.boundEmitsWithoutProfileStatusWhenAwaited` | ready with no status, `runQueued(LINE_WAIT_AFTER_REVEAL_WITH_PROFILE_MS)` writes one line with `profileStatus` null; fails if the bound path also waits for `prof` (R5) |
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

## Bugs

N/A: kind improvement; the red rows above are written first.

## Manual and device checks

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
| M1 | AC-2 generation (emulator-5554, implementer, plan Task 5) | 1. `<main>/.todo/baseline-profile-auth.env` exists (D6, Q-D3) and `adb -s emulator-5554 shell pm path com.kino.puber` output saved. 2. In `wt`: `AGENT_GRADLE_SERIAL=1 DEVICE=emulator-5554 PUBER_BASELINE_AUTH_CACHE=<main>/.todo/baseline-profile-auth.env ./tools/generate-baseline-profile.sh` with the `.env` variables of Q-D2 in that command's environment only. 3. Log line after "sees:" lists only `emulator-5554`; Gradle task list holds `DevNonMinifiedRelease` producer tasks only. 4. Exit 0. 5. `playbackProfile` and `railNavigationProfile` pass (a missing `player` tag fails the run). 6. `pm path com.kino.puber` unchanged from step 1. 7. `git -C <wt> diff --stat` lists both `baseline-prof.txt` and `startup-prof.txt`; `git -C <wt> status --short` shows no new file with secrets | One serial; both files rewritten; `BaselineProfileContentTest` green on the new files | guard-hook refusal; a `Prod` task; `com.kino.puber` installed or changed; any adb call reaching the box; credentials written into `wt` |
| M2 | AC-6 packaged profile | 1. `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleProdRelease :app:assembleDevBenchmarkRelease`. 2. `unzip -l` each APK. 3. `check.full` | `assets/dexopt/baseline.prof` and `assets/dexopt/baseline.profm` in both APKs; `check.full` exit 0 | an APK without either asset; a failing unit, detekt or lint task |
| M3 | AC-7 release doc | 1. Read `docs/release.md` Refresh Baseline Profiles. 2. `rg -n 'connected.*AndroidTest' docs/release.md` | the D13 rule (startup, Home, Details, player, every minor release) and the one command `DEVICE=emulator-5554 ./tools/generate-baseline-profile.sh`; `rg` returns nothing | a second generation command; `./gradlew` as the worktree route |
| M4 | AC-1 baseline (base build, box) | 1. Install base `devBenchmarkRelease` with install-preserve. 2. States in order S0, S2, S1 (`state.md` Decisions), 15 starts each. 3. Record per state: first-key median and p95, per-start `startup` line, `dumpsys` line, load average | three complete state blocks in one evidence file, same day | a state with under 15 starts; a state without its `dumpsys` line; uninstall or data clear |
| M5 | AC-4 gate (new build, box, same day as M4) | 1. Install the new `devBenchmarkRelease` over the base with install-preserve. 2. S1, 15 starts. 3. S0, 15 starts (reported). 4. Table: base S0/S1/S2 next to new S1/S0 | new S1 first-key p95 <= 199 ms | the gate read from S2 or from a state whose `dumpsys` line does not match its definition; a different day from M4 |
| M6 | AC-5 on the box | from M5: `prof` of every `startup` line in new S0 and new S1 | each line has `prof`; S1 lines carry 1; S0 codes recorded as seen | a cold start with two `startup` lines; a line lost on a start that reached reveal |
| M7 | AC-8 update path | 1. Base build installed and launched once (any state). 2. Install the new build with install-preserve (`install -r`), no compile. 3. Cold start, wait 10 s, read the `startup` line. 4. Open a Details card, press OK on Play, wait 5 s, BACK twice. 5. Conductor: `cmd package bg-dexopt-job com.kino.puber.stage`. 6. `dumpsys` line. 7. Cold start, read the `startup` line | step 3: `prof` 2 or 1; step 4: player starts and BACK returns to Details then Home; step 6: `[status=speed-profile] [reason=bg-dexopt]`; step 7: `prof` 1 | data loss after `install -r` (login screen); `prof` absent; this state quoted as S1 |
| M8 | script needs a serial | `DEVICE= ./tools/generate-baseline-profile.sh` in `wt` | non-zero exit with a message naming `DEVICE`, before any adb or Gradle output | an adb or Gradle command runs |

## Perf

| Item | Value |
|---|---|
| Metric | first-key latency per cold start (`key_lat.lat_ms`, <= 50 ms when only a `key` line exists); secondary, not gated: `startup` `revealed` and `first_content` medians |
| Method | M4/M5 method above, box 192.168.1.110:5555, `devBenchmarkRelease`, 15 starts per state |
| Baseline (before implementation) | M4: base build in S0, S2, S1, one day |
| Pass threshold | new build in S1: p95 <= 199 ms (AC-4, Q5); new S0 reported next to base S0, not gated |

## UNTESTABLE

None.
