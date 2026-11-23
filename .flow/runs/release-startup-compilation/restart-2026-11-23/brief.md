# Brief: release-startup-compilation

Kind: improvement. Size: L, split below. Tracks: perf. Device verification: yes
(baseline profile, build variants and startup are risk areas in
`.flow/roles/analyst.md`; the generation and benchmark ACs run only on a device).

## Request

Refresh the stale baseline profile with a generation target the repo rules allow,
compile the app at install time after an in-app update, and narrow the R8 keep
rules and drop unused dependencies. Findings and code facts: `research.md: 3`.
Options: `research.md: 6`.

## SPLIT

| # | Title | Size | Contents | Order |
|---|-------|------|----------|-------|
| 1 | Refresh the baseline profile through a compliant generation target | M | `make` targets for `generateBaselineProfile` and `StartupBenchmarks` (absorbs startup-benchmark-entry), CUJs rewritten for the side menu, wait for the reveal, profiles regenerated on `puber_tv_36`, release-procedure step | first; **stays in this run** |
| 2 | Compile at install after an in-app update | M | `PackageInstaller` session with `base.apk` + verified `base.dm`, APK-only fallback, release workflow publishes `.dm` + `.sha256`, `ProfileVerifier` status in the telemetry `startup` line, box measurement as-installed versus `install-dm` | after 1: the `.dm` carries the profile from 1 |
| 3 | Narrow R8 keep rules and drop unused dependencies | M | `app/proguard-rules.pro` package-wide keeps narrowed step by step with a `devBenchmarkRelease` device pass each; remove `coil-video`, `security-crypto` + its keep rule, `ktor-client-logging`; replace the AppCompat theme parent (absorbs dependency-prune and perf-deferred P5) | independent; after 1 so the profile is regenerated once on the narrowed build, or before 1 if the owner prefers |

The rest of this brief covers part 1 only.

## Part 1: code it touches

| What | Where |
|------|-------|
| Generator CUJs assume top tabs | `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt:35-121` |
| Benchmark documents a refused command | `baselineprofile/src/main/java/com/kino/puber/baselineprofile/StartupBenchmarks.kt:19-20` |
| Plugin uses every connected device | `baselineprofile/build.gradle.kts:36-38` |
| App-side plugin config | `app/build.gradle.kts:435-446` |
| Generation script: implicit install, bare `adb shell`, `./gradlew` | `tools/generate-baseline-profile.sh:52,101,103-104,134` |
| Compliant pattern to reuse; `GRADLE ?= ./gradlew` must be overridable with `./tools/agentw` | `Makefile:23,128-136` |
| Reveal point, where `ReportDrawnWhen` belongs | `app/src/main/java/com/kino/puber/MainActivity.kt:81-91` |
| Checked-in profiles | `app/src/main/generated/baselineProfiles/baseline-prof.txt`, `startup-prof.txt` |
| Release procedure and refresh section | `docs/release.md:15-41`, `:94-140` |

## Acceptance criteria (part 1)

- **AC-1** Given emulator `<serial>` leased and the dev app installed and paired,
  when `make DEVICE=<serial> baseline-profile` runs, then the instrumented run
  lists only `<serial>`, no `install*` Gradle task runs, and afterwards the
  package is still installed with its `firstInstallTime` unchanged and opens
  past login (`dumpsys package`, launch).
- **AC-2** Given the same setup, when `make DEVICE=<serial> benchmark` runs
  `StartupBenchmarks`, then it finishes on `<serial>` only, keeps the app
  installed as in AC-1, and writes its results JSON.
- **AC-3** Given a cold start under the generator, when the splash covers the
  tree, then no D-pad key is sent before the reveal: the app reports fully
  drawn at the reveal and the logcat `Fully drawn` line for the package
  precedes the first CUJ key.
- **AC-4** Given the side-menu navigation, when each CUJ runs, then it asserts
  the screen it reached (Home rows, Details, player started and exited, Search,
  Favourites) and fails the run if a screen is not reached.
- **AC-5** Given the regenerated profiles, when grepped, then `baseline-prof.txt`
  has classes under `ui/feature/details`, `player` and `search` and none under
  `main/toptabs`, and `startup-prof.txt` has `StartupSplashHost` and
  `RevealGate`.
- **AC-6** Given a `devBenchmarkRelease` APK built from the run head, when
  inspected, then `assets/dexopt/baseline.prof` is present and under 1.5 MB.
- **AC-7** Given `puber_tv_36`, when `StartupBenchmarks` runs at base `e4c3b5aa`
  and at the run head, then `timeToInitialDisplayMs` median with
  `Partial(BaselineProfileMode.Require)` is lower than with `None` at the head
  and not higher than the base's profile result.
- **AC-8** Given `docs/release.md`, when read, then the release procedure has a
  profile-refresh step that may be skipped only with a recorded reason, the
  refresh section names the `make` targets, and `generate-baseline-profile.sh`
  and `StartupBenchmarks.kt` no longer call `./gradlew` or adb without `-s`.
- **AC-9** Given the run's evidence directory, when
  `tools/mobile-evidence-audit.sh` scans it, then it reports no tokens and no
  raw logcat.

## Risks (part 1)

| Risk | Note |
|------|------|
| `generateBaselineProfile` is not `connected*AndroidTest`, so the guard hook does not refuse it | Run with plain Gradle it reaches every device and AGP may uninstall the app, dropping the box's pairing and KeyStore key. The make target is the only route; the hook should refuse the bare task too |
| Generation replaces the installed `com.kino.puber.stage` with `devNonMinifiedRelease` | Same package, signer and `DebugVersionCode` (`app/build.gradle.kts:54,212-215,224-228`), so data is kept; a worktree build has no `CLIENT_SECRET`, so a token refresh mid-run fails. Tokens must be fresh or the build must come from the main checkout |
| A stale CUJ still produces a profile file | AC-4 and AC-5 guard it |
| Benchmark noise on the emulator | AC-7 compares medians on the same emulator and day |

## Open questions

| ID | Question | Recommended | Blocks |
|----|----------|-------------|--------|
| Q1 | Split into the three runs above, with part 1 here? | Yes; create idea runs for parts 2 and 3 with `source: release-startup-compilation` | yes |
| Q2 | Authorise profile generation and benchmark runs on `emulator-5554` (`puber_tv_36`) under a lease, app kept installed, tokens from `.todo/baseline-profile-auth.env`, never the box? | Yes, emulator only (`docs/release.md:96-98` requires asking) | yes |
| Q3 | Fold startup-benchmark-entry into part 1 (AC-2, AC-7)? | Yes; the benchmark and the generator share the harness, and AC-7 needs it; close that idea as absorbed | yes |

CUJ set (AC-4) and the release-step rule (AC-8) follow `research.md: 8` Q3 and Q4 recommendations.
