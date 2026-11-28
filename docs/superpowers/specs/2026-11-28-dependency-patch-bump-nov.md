# Bump AGP 9.5.3, Gradle 9.9.3, KSP 2.3.14, Ktor 3.6.2, Lifecycle 2.11.1

Status: approved
Date: 2026-11-28
Slug: dependency-patch-bump-nov
Kind: improvement
Size: M
Tracks: none

## Problem / Why

The request holds two shippable goals, so it is split (research.md: Options, option A):

| # | Run | Size | Order |
|---|---|---|---|
| 1 | this run: AGP 9.5.3, Gradle 9.9.3, KSP 2.3.14, Ktor 3.6.2, Lifecycle 2.11.1 | M | first (Gradle 9.9.2 security fixes) |
| 2 | `compose-1-13-bump`: "Bump Compose BOM 2026.11.01 (Compose 1.13, material3 1.5) with compileSdk 37.1", tracks ui | M | after 1 |

Run 1 is M, not S: it touches the build variants, networking (Ktor) and persistence (Room through KSP) risk areas of `.flow/roles/analyst.md`. No production source change is expected: the edits are `gradle/libs.versions.toml:2,4,12,16` and `gradle/wrapper/gradle-wrapper.properties:3` (plus `gradlew`/`gradlew.bat` if the wrapper task rewrites them). `composeBom` (`libs.versions.toml:8`) and `Versions.CompileSdk` (`buildSrc/src/main/kotlin/Versions.kt:12`) stay as they are.

Device verification: yes (build toolchain, networking client, Room code generation). Research: `.flow/runs/dependency-patch-bump-nov/research.md`.

## Acceptance criteria

- **AC-1** Given the change, When `git diff a546f5a5 -- gradle/libs.versions.toml` is read, Then exactly `agp` 9.5.3, `ksp` 2.3.14, `lifecycleRuntimeKtx` 2.11.1 and `ktor` 3.6.2 differ, and `composeBom` is still 2026.09.00.
- **AC-2** Given the change, When `gradle-wrapper.properties` and `gradle/wrapper/gradle-wrapper.jar` are read, Then `distributionUrl` names `gradle-9.9.3-bin.zip`, the other properties are unchanged, and the jar sha256 equals services.gradle.org `gradle-9.9.3-wrapper.jar.sha256` (research.md: External findings); `git diff --stat` shows no CRLF churn in `gradlew.bat`.
- **AC-3** Given the worktree, When `./tools/agentw help --warning-mode all` runs before and after the change, Then the deprecation warnings after are a subset of those before, or each new one is listed in the evidence with its source.
- **AC-4** Given `app/build/generated/ksp` deleted, When `:app:kspDevDebugKotlin` and `:app:kspProdReleaseKotlin` run, Then every Room `*_Impl` class generated at base is generated again (same file list in both variants).
- **AC-5** Given the change, When `check.full` (`.flow/config.yaml`) and `./tools/agentw :app:assembleProdRelease` run in the worktree, Then both pass (R8 and the signing fallback at `app/build.gradle.kts:171-178` untouched).
- **AC-6** Given a lease on `192.168.1.110:5555`, When `make DEVICE=192.168.1.110:5555 itest TESTS=<each class in app/src/androidTest/kotlin/com/kino/puber/data/db/>` runs, Then all pass.
- **AC-7** Given the worktree dev APK installed with install-preserve on the box after `auth-save`, When Home is opened, posters load, a title card and its details are opened, a playback starts and Device settings is opened and left at normal pace, Then there is no crash, the session stays logged in, images and watch badges show, and the session-log toggle in Device settings still reacts after leaving and returning (`DeviceSettingsContent.kt:213-214`, `currentStateFlow`). (device observation, no scripted API calls)

## Out of scope

Compose BOM 2026.11.01 (Compose 1.13, material3 1.5) and compileSdk 37.1: run `compose-1-13-bump` (brief Q1). Baseline profile regeneration (brief Q2). `distributionSha256Sum` in the wrapper properties (brief Q3).

### Risks (analyst)

| Risk | Handling |
|---|---|
| Ktor 3.6.2 now fails incomplete streamed responses (KTOR-6905) instead of reading them silently; Coil images go through Ktor (`data/api/network/ImageHttpClient.kt`, `coil-network-ktor3`) | AC-7 posters and card images on the box; a broken-image regression blocks |
| KSP 2.3.14 carries 2.3.13's caching changes; a missing Room `_Impl` fails only at runtime | AC-4, AC-6 |
| AGP 9.5.2/9.5.3 contents undocumented (research.md: External findings) | AC-3, AC-5 |
| Lifecycle 2.11.1 AARs declare `minCompileSdk=37` | Already met by `Versions.kt:12`; `checkDevDebugAarMetadata` in check.fast confirms |
| Box session lost after a worktree install | Refresh with a master build and `auth-save` first (memory: worktree-build-loses-box-session) |
| Merge overlap with `gradle-build-cache` (in review) | No shared lines; rebase on master before ship |

## Design

Base b1b3c51a. `git diff --stat a546f5a5 b1b3c51a -- gradle/ gradlew gradlew.bat buildSrc app/build.gradle.kts` is empty, so the brief's ACs read against a546f5a5 hold unchanged on this base. Versions and release facts: `research.md#5-external-findings`.

### Decisions

| Question | Decision | Why |
|---|---|---|
| D1. Commits? | Three, in this order: C1 persistence (`ksp`), C2 runtime libraries (`ktor`, `lifecycleRuntimeKtx`), C3 toolchain (wrapper, `agp`). A fix forced by a bump goes in that bump's commit. | Same order as dependency-update-stable D2: libraries land on the proven toolchain, so a failure names one group; toolchain last, as the likeliest to need a fallback. Under the owner's 5-commit limit with the docs commit. |
| D2. Baselines before any edit? | On b1b3c51a, before C1: (a) `AGENT_GRADLE_SERIAL=1 ./tools/agentw help --warning-mode all` saved to `{run}/warnings-base.log`; (b) delete `app/build/generated/ksp`, run `:app:kspDevDebugKotlin :app:kspProdReleaseKotlin`, save the sorted `find app/build/generated/ksp -name '*_Impl.kt'` per variant to `{run}/ksp-impl-base.txt`. | AC-3 and AC-4 compare against base; `app/build/generated/ksp` does not exist in the worktree today, so there is no base listing yet. |
| D3. Wrapper bump? | `./tools/agentw wrapper --gradle-version 9.9.3`, run twice (first rewrites `gradle/wrapper/gradle-wrapper.properties:3`, second regenerates jar and scripts on 9.9.3). No `distributionSha256Sum` (state.md Q3). Then `sha256sum gradle/wrapper/gradle-wrapper.jar` must print `621008af…daf8`, and `git diff --stat gradlew.bat` must be empty or LF/CRLF-clean (`.gitattributes`: `*.bat text eol=crlf`). | Previous run's procedure; `./gradlew` is blocked in a worktree. The 9.9.3 jar sha equals the committed one, so a jar diff is itself a finding. |
| D4. KSP guard? | After C1 and after C3, repeat D2(b) and `diff` against `ksp-impl-base.txt`; `git status --porcelain app/schemas` empty. | KSP #3286 class of defect drops output silently; stale outputs would hide it. |
| D5. Ktor KTOR-6905 (incomplete streamed bodies now fail)? | No production change, no new test. Guards: `./tools/agentw testDevDebugUnitTest --tests 'com.kino.puber.data.api.*'` in C2 (includes `KinoPubApiClientDownloadTest`, `network.ImageHttpClientTest`), AC-7 on the box. | The three streaming readers already treat a failure as a failure: Coil via `createImageHttpClient` (`data/api/network/ImageHttpClient.kt:9`, wired `data/di/modules.kt:79`, `PuberApp.kt:153`) shows an error image; the update download (`KinoPubApiClient.kt:1006-1018`) deletes the temp file and returns `Result.failure` (`:1027-1029`) instead of keeping a short APK; `CurlLogger` is debug-only (`KinoPubApiClient.kt:181`, reader `KtorPlugins.kt:87`). |
| D6. Ktor KTOR-9930 (no `kotlinx-datetime` compat artifact)? | No change. | The app declares `kotlinx-datetime` 0.8.0 itself (`app/build.gradle.kts:405`) and imports only `LocalDate`/`toKotlinLocalDate`; nothing used the compat `Instant`. `check.fast` confirms. |
| D7. Lifecycle 2.11.1 `currentStateFlow`? | No change. | `DeviceSettingsContent.kt:213-214` reads it twice with `first {}`; one shared instance gives the same values. AC-7 covers the toggle. |
| D8. A bump cannot be made green? | Implementer stops and returns a question with the fallback below; any other version breaks AC-1. | Owner decides version changes. |

### Changes by layer

API/repository, ViewModel, screen/DI, Compose and resources: no change. Build configuration only.

| Commit | File:line | Key: from -> to |
|---|---|---|
| C1 | `gradle/libs.versions.toml:4` | `ksp` 2.3.12 -> 2.3.14 |
| C2 | `gradle/libs.versions.toml:11`, `:15` | `lifecycleRuntimeKtx` 2.11.0 -> 2.11.1, `ktor` 3.6.1 -> 3.6.2 |
| C3 | `gradle/wrapper/gradle-wrapper.properties:3`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat` (only if regenerated differently), `gradle/libs.versions.toml:2` | wrapper 9.9.1 -> 9.9.3, `agp` 9.5.1 -> 9.5.3 |

Not edited: `composeBom` (`gradle/libs.versions.toml:7`), `kotlin` (`:3`), `buildSrc/src/main/kotlin/Versions.kt:12`, `app/build.gradle.kts:171-178`.

| Commit | Checks before committing (`check.fast` = `./tools/agentw :app:compileDevDebugKotlin` on every commit) |
|---|---|
| C1 | D4 listing for both variants |
| C2 | D5 `data.api` unit tests |
| C3 | wrapper first, `check.fast`, then AGP, `check.fast`; D3 sha and CRLF checks; `help --warning-mode all` diffed against `warnings-base.log` (AC-3); `check.full`; `./tools/agentw :app:assembleProdRelease` (AC-5); D4 listing |

### Fallbacks

| Key | Fails how | Recommended fallback |
|---|---|---|
| `ksp` | Build error or `_Impl` set differs | Stay on 2.3.12 |
| `ktor` | `data.api` tests red or broken images on the box | Stay on 3.6.1 |
| `lifecycleRuntimeKtx` | AAR metadata or compile failure | Stay on 2.11.0 |
| `agp` | detekt classpath override (`app/build.gradle.kts:318-336`) or lint (`abortOnError`, `:75`) breaks with no source fix | 9.5.2, else stay on 9.5.1; keep wrapper 9.9.3 |
| wrapper | Fails with AGP 9.5.3 | 9.9.2 (same security fixes), else fall back on `agp` |

### Alternatives rejected

- One commit: a bad bump could not be reverted alone.
- One commit per key (five): wrapper and AGP are one toolchain risk; Ktor and Lifecycle each carry one fix and share one check.
- Toolchain first: a library failure would surface on an unproven toolchain.
- Adding `distributionSha256Sum`: answered no (state.md Q3).

### Risks

1. **Room `_Impl` dropped (lifecycle, persistence).** KSP 2.3.14 keeps 2.3.13's classloader cache and no-clear-between-rounds changes. `PuberDatabase.create` (`data/db/PuberDatabase.kt:36-49`) needs `PuberDatabase_Impl` at runtime; a missing `AutoMigration_{2_3,3_4,4_5}` (`:19-23`) with `fallbackToDestructiveMigration(dropAllTables = true)` (`:48`) silently wipes the local watch-state index on upgrade instead of crashing. Guards: D2(b), D4, AC-6.
2. **Ktor 3.6.2 incomplete-body failures (networking).** Posters through `coil-network-ktor3` on the plain image client and the streamed update download now fail where they read short bodies. Behaviour is safer, but a server or CDN that closes early would show as broken posters. Guards: D5, AC-7.
3. **AGP 9.5.3 undocumented contents (rollout, build variants).** Lives in the detekt classpath override `app/build.gradle.kts:318-336`, lint `:75`, release signing fallback `:171-178`, R8 release. Guards: AC-3, AC-5.
4. **Wrapper regeneration churn (rollout).** `gradlew.bat` CRLF and an unexpected jar change. Guards: D3, AC-2.
5. **Box session lost after the worktree install (auth).** Refresh with a master build and `make DEVICE=192.168.1.110:5555 auth-save` before install (memory: worktree-build-loses-box-session).
6. **Lifecycle 2.11.1 `minCompileSdk=37` (build).** Met by `Versions.kt:12`; `check.fast` runs AAR metadata checks.

### Open questions

1. The brief cites `gradle/libs.versions.toml:2,4,12,16` and `composeBom` at `:8`; on base the keys are at `:2,4,11,15` and `composeBom` at `:7` (research.md §3 has the same offset). Recommended: the analyst corrects the line numbers in the brief; the ACs name keys, so none changes. Blocks: no.

## Test design

Base b1b3c51a (brief ACs cite a546f5a5; same build files, `design-architect.md` header). ACs: `brief.md#acceptance-criteria`. Commits, baselines and per-commit checks: `design-architect.md#decisions` (D2-D5) and `#changes-by-layer`. Levels: `.flow/roles/test-designer.md` plus `build` (a Gradle task or file inspection in the worktree, output in the evidence file).

No new test: the change edits no production or test source. Every P0 is `guard`.

### Risk profile

| # | Risk (source) | P | I | Score | Caught by |
|---|---|---|---|---|---|
| R1 | KSP 2.3.14 (2.3.13 caching changes) drops a Room `_Impl`; a missing `AutoMigration_*_Impl` becomes a silent destructive migration (design Risk 1) | 2 | 3 | 6 | AC-4 listing vs `ksp-impl-base.txt` (build), AC-6 incl. `PuberDatabaseMigrationTest` (instrumented) |
| R2 | AGP 9.5.3 undocumented contents break R8, signing fallback, detekt classpath or lint (design Risk 3) | 2 | 3 | 6 | AC-5 (build) |
| R3 | Ktor 3.6.2 KTOR-6905 fails short streamed bodies: broken posters or failed update download (design Risk 2) | 1 | 3 | 3 | `data.api` unit tests (design D5), M3 |
| R4 | Box session lost after the worktree install (design Risk 5) | 2 | 3 | 6 | M1, M2: master refresh and `auth-save` first |
| R5 | Wrapper regeneration changes the jar or adds `gradlew.bat` CRLF churn (design Risk 4) | 2 | 2 | 4 | AC-2 (build) |
| R6 | New Gradle 9.9.3 / AGP 9.5.3 deprecation on project scripts (brief AC-3) | 1 | 1 | 1 | AC-3 log diff; accepted with a note in evidence |
| R7 | Lifecycle 2.11.1 `currentStateFlow` change stalls the session-log refresh in Device settings (design D7) | 1 | 2 | 2 | M5; accepted at device level only |
| R8 | Lifecycle 2.11.1 `minCompileSdk=37` fails AAR metadata (design Risk 6) | 1 | 2 | 2 | `check.fast` |
| R9 | A catalog key outside the brief list changes, or `composeBom` moves (brief AC-1) | 1 | 2 | 2 | AC-1 diff |

### Traceability

| AC | Level | Priority | Test | Proves (P0: `guard` or `red`) |
|---|---|---|---|---|
| AC-1 | build | P0 | `git diff b1b3c51a -- gradle/libs.versions.toml` | guard: fails if a To value differs from the brief, any other key changes, or `composeBom` leaves 2026.09.00 |
| AC-2 | build | P0 | `git diff b1b3c51a -- gradle/wrapper/gradle-wrapper.properties`; `sha256sum gradle/wrapper/gradle-wrapper.jar` against `services.gradle.org/distributions/gradle-9.9.3-wrapper.jar.sha256`; `git diff --stat b1b3c51a -- gradlew gradlew.bat` | guard: fails if more than `distributionUrl` changes, the jar sha differs from the published one, or `gradlew.bat` shows whole-file line-ending churn |
| AC-3 | build | P1 | `AGENT_GRADLE_SERIAL=1 ./tools/agentw help --warning-mode all` after C3, diffed against `warnings-base.log` (D2a) | Fails if Gradle 9.9.3 or AGP 9.5.3 adds a deprecation not recorded with its source |
| AC-4 | build | P0 | `rm -rf app/build/generated/ksp`; `./tools/agentw :app:kspDevDebugKotlin :app:kspProdReleaseKotlin`; sorted `find app/build/generated/ksp -name '*_Impl.kt'` per variant diffed against `ksp-impl-base.txt` (D2b), after C1 and C3; `git status --porcelain app/schemas` empty | guard: fails if any of the base `_Impl` files (database, three DAOs, three auto-migrations) is missing in either variant, or the schema JSON changes |
| AC-5 | build | P0 | `check.full` from `.flow/config.yaml`, then `./tools/agentw :app:assembleProdRelease`; APK under `app/build/outputs/apk/prod/release/`; `git diff b1b3c51a -- app/build.gradle.kts config/detekt app/lint*.xml` empty | guard: fails if AGP 9.5.3 breaks R8, the signing fallback, detekt or lint, or a finding is hidden by a config edit; `BUILD SUCCESSFUL` quoted for the run including `:app:detektAll` |
| AC-5 | unit | P1 | `KinoPubApiClientDownloadTest.downloadUpdateAsset_writesBytesToDiskAsTheyArrive`, `ImageHttpClientTest.imageRequest_carriesNoKinoPubAuthHeaderOrQuery`, `KinoPubApiClientAuthProbeTest.sessionExpiredFailureRecordsAuthErrThenThrowsUnauthorized` (run in C2 via `--tests 'com.kino.puber.data.api.*'`); `git diff b1b3c51a -- app/src/test` empty | Fails if Ktor 3.6.2 changes streamed download, the image client or the bearer failure path; the empty diff fails if a test was edited to pass |
| AC-6 | instrumented | P0 | `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.data.db.WatchStateDaoTest`, then the same for `CachedPayloadDaoTest`, `PersistentPayloadStoreDbTest`, `PuberDatabaseMigrationTest` | guard: fails if KSP-generated DAOs or auto-migrations change results or `PuberDatabase.create` drops watch state (`productionBuilderOpensAV3DatabaseWithoutDroppingWatchState`) |
| AC-7 | device | P0 | M1-M5 | guard: fails on a crash, a lost session, broken posters or watch badges, playback not starting, or a dead session-log toggle |

### Failing-first test

N/A: no bug fix; every P0 is `guard`.

### Manual checks

TV box `192.168.1.110:5555` only, under a lease (`tools/emulator-resource-lock.sh`), every adb call with `-s 192.168.1.110:5555`, package `com.kino.puber.stage` only. Ordinary use at human pace, no scripted API calls. Evidence: screenshots and filtered logcat, scanned with `tools/mobile-evidence-audit.sh`.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | If the box session is stale, install a master dev build first and open Home; `make DEVICE=192.168.1.110:5555 auth-save`; install the worktree dev APK with `tools/android-apk-install-preserve`; compare on-device `base.apk` sha256 with the built APK | Install succeeds, hashes match | Uninstall, data clear, downgrade, replaced signer; touching `com.kino.puber`; a failed install treated as anything but a blocker |
| M2 | `adb -s 192.168.1.110:5555 shell monkey -p com.kino.puber.stage 1` | Home opens logged in | Pairing or login screen; crash |
| M3 | On Home press `KEYCODE_DPAD_DOWN` through 3 rows and `KEYCODE_DPAD_RIGHT` 5 times in one row; screenshot; `KEYCODE_DPAD_CENTER` on a card; screenshot the details | Posters and watch badges on visible cards; details show poster and backdrop | Placeholder or error images (KTOR-6905); missing badges; focus loss |
| M4 | On the details screen start playback with `KEYCODE_DPAD_CENTER`; wait 30 s; screenshot; `KEYCODE_BACK` | Video plays and position advances; back returns to details | Black screen, endless buffering, error overlay, crash |
| M5 | Open Device settings; toggle the session-log row with `KEYCODE_DPAD_CENTER`; `KEYCODE_HOME`, relaunch with monkey; return to Device settings and toggle again; screenshot each state | The toggle flips each time and its description follows after returning | Toggle stuck or description stale after resume (`DeviceSettingsContent.kt:213-214`); crash |

### Perf track

N/A: perf track off.

### UNTESTABLE

None.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Split Compose BOM 2026.11.01 into its own run? | yes | answered | Yes, `compose-1-13-bump` |
| Q2 | Regenerate the baseline profile here? | no | answered | No |
| Q3 | Add `distributionSha256Sum`? | no | answered | No |
| Q4 | Brief cites `libs.versions.toml:2,4,12,16` and `composeBom` at `:8`; on base they are `:2,4,11,15` and `:7` (architect) | no | answered | ACs name keys, not lines; the line numbers in this spec's Problem section are stale, the architect's are authoritative |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-28 | Q1-Q3 as recommended in the brief | owner (standing instruction: recommended) |
| 2026-11-28 | Base b1b3c51a (master moved after a546f5a5; build files identical) | conductor |
| 2026-11-28 | Gate 1: approved (size M, no tracks, 7 ACs, commits C1 ksp, C2 ktor + lifecycle, C3 wrapper + agp) | owner (standing instruction: recommended) |
