# Bump AGP 9.5.3, Gradle 9.9.3, KSP 2.3.14, Ktor 3.6.2, Lifecycle 2.11.1 - plan

**Spec:** `docs/superpowers/specs/2026-11-28-dependency-patch-bump-nov.md`
**Goal:** Move four catalog keys and the Gradle wrapper to the spec's versions in three commits (design D1: C1 ksp, C2 ktor + lifecycle, C3 wrapper + agp), each green on its own.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :player-test-fixtures:testDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug && bash tools/tests/test-baseline-profile-tooling.sh && bash tools/tests/test-itest-tooling.sh`. Prefix every `./tools/agentw` call with `AGENT_GRADLE_SERIAL=1`.
- One task = green fast gate + green tests of the touched code + one commit,
  message per `commit_style` (one imperative English sentence, no prefixes), never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the
  files cited below, nothing else.
- Run every command from `/home/dev/orca/workspaces/Puber/dependency-patch-bump-nov`. Never `./gradlew` there (guard hook).
- Base is `b1b3c51a`; the spec's ACs name `a546f5a5`, whose build files are identical (spec Design header). Diff against `b1b3c51a`.
- Versions are exactly the spec's (Changes by layer table). If a bump cannot be made green, stop and return a question with the fallback from the spec's Fallbacks table (D8); do not pick another version.
- Not edited: `gradle/libs.versions.toml:3` (`kotlin`), `:7` (`composeBom` 2026.09.00), `buildSrc/src/main/kotlin/Versions.kt:12`, `app/build.gradle.kts` (incl. signing fallback `:171-178`), `config/detekt/`, `app/schemas/`, `app/src/`, `app/src/main/generated/baselineProfiles/`. No `distributionSha256Sum`.
- Logs and listings go to the run folder `/home/dev/Puber/.flow/runs/dependency-patch-bump-nov/` (outside the worktree). Build evidence (AC-1 to AC-5: commands, `BUILD SUCCESSFUL` lines, diffs, sha) goes in the Implementation record below.
- Device checks (AC-6, AC-7, test-design M1 to M5) belong to `qa-device` after Task 3; no task leases, installs or launches. In a worktree `make itest` must run with `GRADLE=./tools/agentw` (`Makefile:23` defaults to `./gradlew`).

## Coverage

| AC | Task | Test-design row |
|---|---|---|
| AC-1 | 3 (final diff) | `git diff b1b3c51a -- gradle/libs.versions.toml` |
| AC-2 | 3 | wrapper properties diff, jar sha256, `git diff --stat` of `gradlew`/`gradlew.bat` |
| AC-3 | 1 (base log), 3 (diff) | `help --warning-mode all` diffed against `warnings-base.log` |
| AC-4 | 1 (base listing, C1 listing), 3 (C3 listing) | `_Impl` listing vs `ksp-impl-base.txt`; `git status --porcelain app/schemas` empty |
| AC-5 | 2 (unit row), 3 (build row) | `data.api` unit tests (3 named) + empty `app/src/test` diff; `check.full` + `:app:assembleProdRelease` + empty config diff |
| AC-6 | qa-device on the Task 3 build | `make itest` per class in `app/src/androidTest/kotlin/com/kino/puber/data/db/` |
| AC-7 | qa-device on the Task 3 APK | M1 to M5 |

KSP listing script, written to `<file>` (used in Tasks 1 and 3):

```
rm -rf app/build/generated/ksp
AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:kspDevDebugKotlin :app:kspProdReleaseKotlin
for v in devDebug prodRelease; do echo "## $v"; find app/build/generated/ksp/$v -name '*_Impl.kt' -printf '%P\n' | sort; done > <file>
```

---

## Tasks

### Task 1: Record the baselines and bump KSP (C1 persistence)

**Covers:** AC-3 (base), AC-4
**Files:**
- Modify: `gradle/libs.versions.toml:4` `ksp` 2.3.12 -> 2.3.14

**Consumes:** `ksp(libs.androidx.room.compiler)` (`app/build.gradle.kts:423`), the only KSP consumer. `PuberDatabase.create` (`app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt:36`) loads the generated `PuberDatabase_Impl` at runtime; the auto-migrations at `:19-23` generate `AutoMigration_*_Impl` classes, and with `fallbackToDestructiveMigration(dropAllTables = true)` (`:48`) a missing one wipes data instead of failing.
**Produces:** `/home/dev/Puber/.flow/runs/dependency-patch-bump-nov/warnings-base.log`, `/home/dev/Puber/.flow/runs/dependency-patch-bump-nov/ksp-impl-base.txt` (read by Task 3); catalog on KSP 2.3.14.

- [x] On unchanged base (`git status --porcelain gradle` empty): `AGENT_GRADLE_SERIAL=1 ./tools/agentw help --warning-mode all 2>&1 | tee /home/dev/Puber/.flow/runs/dependency-patch-bump-nov/warnings-base.log` (D2a).
- [x] Run the KSP listing script with `<file>` = `/home/dev/Puber/.flow/runs/dependency-patch-bump-nov/ksp-impl-base.txt` (D2b). Both sections must list the database, the three DAOs (`PuberDatabase.kt:27-31`) and the three auto-migrations; if not, stop and report.
- [x] Edit `:4`.
- [x] Run the listing script with `<file>` = `/home/dev/Puber/.flow/runs/dependency-patch-bump-nov/ksp-impl-c1.txt`; `diff /home/dev/Puber/.flow/runs/dependency-patch-bump-nov/ksp-impl-base.txt /home/dev/Puber/.flow/runs/dependency-patch-bump-nov/ksp-impl-c1.txt` prints nothing; `git status --porcelain app/schemas` prints nothing (D4).
- [x] Fast gate; commit, e.g. `Update KSP to 2.3.14`.

**Done when:** both diffs above empty, fast gate green, one commit touching only `gradle/libs.versions.toml`.

---

### Task 2: Bump Ktor and Lifecycle (C2 runtime libraries)

**Covers:** AC-5 (unit row)
**Files:**
- Modify: `gradle/libs.versions.toml:11` `lifecycleRuntimeKtx` 2.11.0 -> 2.11.1; `:15` `ktor` 3.6.1 -> 3.6.2

**Consumes:** streamed readers that KTOR-6905 affects (design D5): image client `createImageHttpClient` (`app/src/main/java/com/kino/puber/data/api/network/ImageHttpClient.kt:9`, wired `data/di/modules.kt:79`, used by Coil at `PuberApp.kt:153`); update download `KinoPubApiClient.kt:1006-1018` with failure path `:1027-1029`. `kotlinx-datetime` is declared directly (`app/build.gradle.kts:405`, D6). `lifecycle.currentStateFlow` at `ui/feature/device/settings/DeviceSettingsContent.kt:213-214` (D7, device check only). All paths under `app/src/main/java/com/kino/puber/`.
**Produces:** catalog on Ktor 3.6.2 / Lifecycle 2.11.1.

- [x] Edit `:11` and `:15`.
- [x] Fast gate (runs the AAR metadata check; Lifecycle 2.11.1 needs `minCompileSdk=37`, met by `Versions.kt:12`).
- [x] `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.data.api.*'`: green, and the report under `app/build/test-results/testDevDebugUnitTest/` contains `KinoPubApiClientDownloadTest.downloadUpdateAsset_writesBytesToDiskAsTheyArrive` (`app/src/test/kotlin/com/kino/puber/data/api/KinoPubApiClientDownloadTest.kt:54`), `ImageHttpClientTest.imageRequest_carriesNoKinoPubAuthHeaderOrQuery` (`.../data/api/network/ImageHttpClientTest.kt:37`), `KinoPubApiClientAuthProbeTest.sessionExpiredFailureRecordsAuthErrThenThrowsUnauthorized` (`.../data/api/KinoPubApiClientAuthProbeTest.kt:76`) as passed.
- [x] `git diff b1b3c51a -- app/src/test` prints nothing.
- [x] Commit, e.g. `Update Ktor to 3.6.2 and Lifecycle to 2.11.1`.

**Done when:** the `data.api` run is green with the three tests passed, the test diff is empty, one commit touching only `gradle/libs.versions.toml`.

---

### Task 3: Bump the Gradle wrapper and AGP, then run the full gates (C3 toolchain)

**Covers:** AC-1, AC-2, AC-3, AC-4, AC-5 (build row)
**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties:3` 9.9.1 -> 9.9.3; `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat` only as the `wrapper` task regenerates them
- Modify: `gradle/libs.versions.toml:2` `agp` 9.5.1 -> 9.5.3

**Consumes:** `/home/dev/Puber/.flow/runs/dependency-patch-bump-nov/warnings-base.log`, `/home/dev/Puber/.flow/runs/dependency-patch-bump-nov/ksp-impl-base.txt` (Task 1). Root `.gitattributes` (`*.bat text eol=crlf`, `gradlew text eol=lf`). AGP-sensitive build code that must stay untouched: lint `abortOnError` (`app/build.gradle.kts:75`), signing fallback (`:171-178`), detekt classpath override (`:318-336`).
**Produces:** the final catalog and wrapper; the dev APK and the build `qa-device` uses for AC-6 and AC-7.

- [x] Wrapper (D3): `AGENT_GRADLE_SERIAL=1 ./tools/agentw wrapper --gradle-version 9.9.3`, then the same command again. Fast gate.
- [x] AC-2: `git diff b1b3c51a -- gradle/wrapper/gradle-wrapper.properties` changes only `distributionUrl` to `gradle-9.9.3-bin.zip`; `sha256sum gradle/wrapper/gradle-wrapper.jar` prints `621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8` (the published `gradle-9.9.3-wrapper.jar.sha256`, equal to the committed jar, so `git diff --stat b1b3c51a -- gradle/wrapper/gradle-wrapper.jar` is empty); `git diff --stat b1b3c51a -- gradlew gradlew.bat` is empty or a template change, not a whole-file rewrite; `git ls-files --eol gradlew gradlew.bat` shows `i/lf` for both.
- [x] Edit `gradle/libs.versions.toml:2`. Fast gate.
- [x] AC-3: `AGENT_GRADLE_SERIAL=1 ./tools/agentw help --warning-mode all 2>&1 | tee /home/dev/Puber/.flow/runs/dependency-patch-bump-nov/warnings-after.log`; `diff <(grep -iE 'deprecat|warning' /home/dev/Puber/.flow/runs/dependency-patch-bump-nov/warnings-base.log | sort -u) <(grep -iE 'deprecat|warning' /home/dev/Puber/.flow/runs/dependency-patch-bump-nov/warnings-after.log | sort -u)`. Every line only in the after log is listed in the Implementation record with its source (plugin or script line).
- [x] AC-4: run the KSP listing script with `<file>` = `/home/dev/Puber/.flow/runs/dependency-patch-bump-nov/ksp-impl-c3.txt`; `diff /home/dev/Puber/.flow/runs/dependency-patch-bump-nov/ksp-impl-base.txt /home/dev/Puber/.flow/runs/dependency-patch-bump-nov/ksp-impl-c3.txt` and `git status --porcelain app/schemas` print nothing.
- [x] AC-5: the full gate, then `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleProdRelease`; an APK exists under `app/build/outputs/apk/prod/release/`; `git diff b1b3c51a -- app/build.gradle.kts config/detekt 'app/lint*.xml' app/src` prints nothing. Quote the `BUILD SUCCESSFUL` line of the full-gate run (it includes `:app:detektAll`).
- [x] AC-1: `git diff b1b3c51a -- gradle/libs.versions.toml` changes exactly `agp` 9.5.3, `ksp` 2.3.14, `lifecycleRuntimeKtx` 2.11.1, `ktor` 3.6.2; `:7` still reads `composeBom = "2026.09.00"`.
- [x] `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleDevDebug` for `qa-device`; record the APK path and its sha256.
- [x] Commit, e.g. `Update the Gradle wrapper to 9.9.3 and AGP to 9.5.3`.

**Done when:** every check above holds, the full gate and `:app:assembleProdRelease` are green, and the commit touches only `gradle/libs.versions.toml` and `gradle/wrapper/`, `gradlew`, `gradlew.bat`.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

- 499f0a53 Update the Gradle wrapper to 9.9.3 and AGP to 9.5.3
- 104d2455 Update Ktor to 3.6.2 and Lifecycle to 2.11.1
- 27697243 Update KSP to 2.3.14

**Red run** (P0 `red` tests on the unchanged code): n/a, no source change; all rows are `guard`.

**Evidence:**

- AC-1: catalog diff against b1b3c51a changes exactly agp 9.5.3, ksp 2.3.14, lifecycleRuntimeKtx 2.11.1, ktor 3.6.2; composeBom stays 2026.09.00.
- AC-2: properties diff changes only `distributionUrl` to gradle-9.9.3-bin.zip; jar sha256 `621008af...f73cdaf8` (unchanged, diff stat empty); `gradlew`/`gradlew.bat` diff stat empty; `git ls-files --eol`: i/lf for both.
- AC-3: `help --warning-mode all` before/after differ only in the docs URL version (9.9.1 -> 9.9.3) of the same two deprecations (`Configuration.setVisible`, Project object as dependency notation); no new warning.
- AC-4: `_Impl` listings (devDebug, prodRelease: database, 3 DAOs, 3 auto-migrations) identical to base after C1 and after C3; `git status --porcelain app/schemas` empty.
- AC-5: C2 `data.api.*` tests green, the three named tests passed, `app/src/test` diff empty. Full gate: gradle part `BUILD SUCCESSFUL in 5m 36s` (117 actionable tasks, includes `:app:detektAll`), then `test-baseline-profile-tooling.sh` and `test-itest-tooling.sh` exit 0. `:app:assembleProdRelease` green, `app/build/outputs/apk/prod/release/app-prod-release.apk`. Diff of `app/build.gradle.kts`, `config/detekt`, `app/src` empty.
- Dev APK for qa-device: `/home/dev/orca/workspaces/Puber/dependency-patch-bump-nov/app/build/outputs/apk/dev/debug/app-dev-debug.apk`, sha256 `9534f7c7fb168ab45264c0499299759ee2bf335a443c2eacde15d74930abe6f2`.
- Logs in the run folder: warnings-base.log, warnings-after.log, ksp-impl-base.txt, ksp-impl-c1.txt, ksp-impl-c3.txt, full-gate.log.

**Deviations from the plan:** none. The wrapper task was run twice as D3 says; the second run changed nothing but the properties.

**Files touched outside the plan:** none.

**For the reviewers:** AC-6 and AC-7 (Room migration itest, Ktor streaming, lifecycle device checks) are not covered here; they need qa-device on the dev APK above, with `GRADLE=./tools/agentw` for `make itest`.

**Blocked:** none.

## Readiness

Verdict: **READY**

Spec `docs/superpowers/specs/2026-11-28-dependency-patch-bump-nov.md` (commit 9a935e73), plan `docs/superpowers/plans/2026-11-28-dependency-patch-bump-nov.md` (untracked in wt), base b1b3c51a.

### Coverage

| AC | Plan task | Test design row | OK |
|---|---|---|---|
| AC-1 | 3 | catalog diff | yes |
| AC-2 | 3 | properties diff, jar sha256, `gradlew`/`gradlew.bat` stat and `ls-files --eol` | yes |
| AC-3 | 1 (base log), 3 (diff) | `help --warning-mode all` diff | yes |
| AC-4 | 1 (base, C1), 3 (C3) | `_Impl` listing vs `ksp-impl-base.txt`, `app/schemas` clean | yes |
| AC-5 | 2 (unit), 3 (build) | `data.api` tests, `check.full`, `:app:assembleProdRelease` | yes |
| AC-6 | qa-device after 3 | `make itest` per class in `data/db/` | yes |
| AC-7 | qa-device after 3 | M1-M5 | yes |

### Citations sampled (at b1b3c51a / wt HEAD)

| Citation | Resolves to | OK |
|---|---|---|
| `gradle/libs.versions.toml:2,4,7,11,15` | `agp` 9.5.1, `ksp` 2.3.12, `composeBom` 2026.09.00, `lifecycleRuntimeKtx` 2.11.0, `ktor` 3.6.1 | yes |
| `gradle-wrapper.properties:3`; jar sha | `gradle-9.9.1-bin.zip`; `621008af...cdaf8`, equals plan value | yes |
| `app/build.gradle.kts:75`, `:171-178`, `:318-336`, `:375`, `:405`, `:423` | `abortOnError = true`, debug signing fallback, detekt classpath override, lifecycle-runtime-ktx, kotlinx-datetime, `ksp(room.compiler)` | yes |
| `PuberDatabase.kt:19-23`, `:27-31`, `:36`, `:48` | three AutoMigrations, three DAOs, `create`, `fallbackToDestructiveMigration` | yes |
| `ImageHttpClient.kt:9`, `modules.kt:79`, `PuberApp.kt:153` | `createImageHttpClient`, qualifier binding, `KtorNetworkFetcherFactory` | yes |
| `KinoPubApiClient.kt:1006-1018`, `:1027-1029`, `:181`; `KtorPlugins.kt:87`, `:106` | streamed `prepareGet`, temp delete + `Result.failure`, debug `CurlLogger`, `readTextLimited`, `readRemaining` | yes |
| `DeviceSettingsContent.kt:213-214` | two `currentStateFlow.first` calls | yes |
| Test names at `KinoPubApiClientDownloadTest.kt:54`, `ImageHttpClientTest.kt:37`, `KinoPubApiClientAuthProbeTest.kt:76`, `PuberDatabaseMigrationTest.kt:126` | named functions exist | yes |
| `Makefile:23` | `GRADLE ?= ./gradlew` | yes |
| `.gitattributes` | `*.bat eol=crlf`, `gradlew eol=lf`; both `i/lf` | yes |

`git diff --stat a546f5a5 b1b3c51a -- gradle/ gradlew gradlew.bat buildSrc app/build.gradle.kts` is empty, so ACs citing a546f5a5 hold against b1b3c51a.

### Checks

| Check | Result |
|---|---|
| Build green per task, dependency order | yes: each task ends on the fast gate; Task 3 consumes Task 1's base logs; wrapper before AGP with a fast gate between |
| P0 rows: defect named, `guard`, test in backticks | yes (AC-1, 2, 4, 5, 6, 7; no source change, so no `red`) |
| Inside Out of scope | yes: `composeBom`, `Versions.kt:12`, baseline profiles, `distributionSha256Sum` listed as not edited |
| Blocking questions answered | yes: Q1 answered; Q2-Q4 non-blocking and answered |

### Notes (non-blocking)

1. Spec Problem section still cites `libs.versions.toml:2,4,12,16` and `:8`; Q4 records the architect's lines (`:2,4,11,15`, `:7`) as authoritative.
2. The plan file is not committed yet; commit it with the spec before Task 1.
