# Update library dependencies to the latest stable versions

Status: approved
Date: 2026-11-12
Slug: dependency-update-stable
Kind: improvement
Size: M
Tracks: none

## Problem / Why

### Request
Update the library dependencies in `gradle/libs.versions.toml` and the Gradle wrapper to the latest stable versions (research.md: 1. Request).

### Decided inputs (owner, standing instruction: recommended answers)
| ID | Decision |
|----|----------|
| Q1 | KSP **2.3.12**, not 2.3.13 (2.3.13 flagged defective upstream, google/ksp#3286; research.md: 5, KSP row). |
| Q2 | `detekt-compose-rules` stays at `ru.kode:detekt-rules-compose:1.4.0` (`gradle/libs.versions.toml:75`). The swap to `detekt-rules-compose-detekt2` is a separate follow-up run. |
| Q3 | AGP 9.5.1 and Gradle 9.9.1 are in this run; coil 3.6.3 lands in the same commit as AGP (research.md: 5, Coil row). |

### Target versions
| Catalog key (`gradle/libs.versions.toml`) | Line | From | To |
|---|---|---|---|
| `agp` | 2 | 9.3.1 | 9.5.1 |
| `kotlin` | 3 | 2.4.10 | 2.4.21 |
| `ksp` | 4 | 2.3.11 | 2.3.12 |
| `coreKtx` | 5 | 1.19.0 | 1.19.1 |
| `appcompat` | 6 | 1.7.1 | 1.8.0 |
| `composeBom` | 7 | 2026.08.00 | 2026.09.00 |
| `okhttp` | 14 | 5.4.0 | 5.5.1 |
| `ktor` | 15 | 3.5.2 | 3.6.1 |
| `kotlinxSerialization` | 16 | 1.11.0 | 1.13.0 |
| `mockk` | 21 | 1.14.11 | 1.14.12 |
| `coil` | 25 | 3.5.0 | 3.6.3 |
| `media3` | 27 | 1.11.0 | 1.11.0 (unchanged; see the 2026-11-12 gate 1 delta in Decisions) |
| `benchmarkMacroJunit4` (also the `androidx.baselineprofile` plugin, `:48`) | 28 | 1.5.0-alpha05 | 1.5.0 |
| `uiautomator` | 30 | 2.3.0 | 2.4.1 |
| `room` | 33 | 3.0.1 | 3.0.3 |
| `sqlite` | 34 | 2.7.0 | 2.7.1 |
| `kover` | 36 | 0.9.9 | 0.9.11 |
| `distributionUrl` (`gradle/wrapper/gradle-wrapper.properties:3`) | 3 | 9.7.0 | 9.9.1 |

Every other catalog entry stays as it is (research.md: 1. Request, "everything else already latest stable"); `detekt` stays `2.0.0-alpha.6` (`:13`).

### Risks
Covered in research.md: 7. Risks; the ones that decide the plan:
- KSP drops generated code silently: a missing Room `_Impl` is a runtime crash, not a compile error (AC-6, AC-10).
- New AGP 9.5 lint checks with `abortOnError = true` and no baseline (`app/build.gradle.kts:74-110`): fix, do not baseline (AC-3).
- Detekt alpha.6 is untested on AGP 9.5.1; the custom classpath at `app/build.gradle.kts:307-322` breaking is a blocker, not a skip (AC-3).
- mockk 1.14.12 suspend-call recording (#1580) may change verification counts: fix tests, keep production code (AC-3).
- The worktree build has no `.env`: a lost session cannot be restored by logging in. Install only with install-preserve; a failed install blocks the run (AGENTS.md).

## Acceptance criteria

- AC-1. Given the worktree after the change, When `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties` are read, Then every version equals the To column of Target versions, and `detekt-compose-rules` is still 1.4.0 (diff inspection).
- AC-2. Given the worktree, When `./tools/agentw :app:compileDevDebugKotlin` runs, Then it succeeds and the build output prints no new deprecation warning from Gradle 9.9 or AGP 9.5 that names this project's build scripts (log inspection against a base run).
- AC-3. Given the worktree, When `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` runs, Then all three pass with no new lint baseline, no detekt baseline entries and no `@Suppress` added for findings the bump introduced (a JVM crash in `detektAll` is retried, not counted as a pass; research.md: 7, detekt row).
- AC-4. Given the unit suite, When `KinoPubApiClientAuthProbeTest` runs, Then it passes unchanged, including the failing-refresh case (`KinoPubApiClientAuthProbeTest.kt:76-132`; guards the Ktor 3.6 bearer change KTOR-9827).
- AC-5. Given the worktree, When `./tools/agentw :app:assembleProdRelease` runs, Then it succeeds with R8 minify and resource shrinking on (`app/build.gradle.kts:183-185`), and the APK is produced under `app/build/outputs/apk/prod/release/` (the worktree signs with the debug fallback, `app/build.gradle.kts:171-174`).
- AC-6. Given the Room schema exports in `app/schemas/`, When the build runs KSP 2.3.12, Then the generated `_Impl` classes exist for the database and every DAO in `app/src/main/java/com/kino/puber/data/db/`, and no schema JSON changes (build output inspection and `git status app/schemas`).
- AC-7. Given an emulator with an existing logged-in Puber dev install, When the worktree's dev debug APK is installed with `tools/android-apk-install-preserve` and the app is launched, Then Home opens without the login screen (session survives the update) (device).
- AC-8. Given AC-7, When the user scrolls Home rows with the D-pad, Then posters load in the cards (Coil 3.6.3 + Ktor 3.6.1 network path) (device, screenshot).
- AC-9. Given a title's Details, When the user starts playback, seeks forward with the D-pad and lets it play, Then HLS video starts, resumes after the seek and keeps playing for at least 30 s (Media3 1.11.0) (device).
- AC-10. Given AC-9 left a watch position, When the app is force-stopped and launched again, Then the same title shows the saved progress (Room3 DB opens and the watch state persisted) and logcat for the app package shows no Room/SQLite exception (device; filtered logcat only, not stored raw).

## Out of scope

In: the version changes above; the wrapper files the Gradle `wrapper` task regenerates; fixes to tests, lint or detekt findings the bump surfaces, without baselining.
Out: the compose-rules swap (Q2); `dependency-prune` and `coil-shared-http` runs (research.md: 4); `CompileSdk`/`TargetSdk` in `buildSrc/src/main/kotlin/Versions.kt:12`.

## Design

### Decisions

| Question | Decision | Why |
|---|---|---|
| D1. How are the bumps grouped into commits? | Four commits, one per risk area, in the order in D2. If a bump forces a fix to a test, lint or detekt finding, the fix goes in that bump's commit. | Each commit can then be reverted alone, and the run stays under the owner's 5-commit limit without a squash that mixes areas. |
| D2. In what order? | C1 persistence, C2 network, C3 AndroidX runtime and test libraries, C4 build toolchain. | Each library group is built on the toolchain already proven on base, so a failure points at one group. The toolchain goes last because it is the most likely to need a fallback. If it does, C1 to C3 still stand and can ship. |
| D3. What does each commit contain? | See the table under Changes by layer. | Q3 puts coil with AGP. `benchmarkMacroJunit4` drives the baselineprofile plugin (`gradle/libs.versions.toml:48`), which is tied to AGP. |
| D4. How is the Gradle wrapper bumped? | `./tools/agentw wrapper --gradle-version 9.9.1`, run twice: the first run rewrites `gradle-wrapper.properties:3`, the second regenerates `gradle-wrapper.jar` and the `gradlew` scripts with 9.9.1. Keep the existing properties and add no `distributionSha256Sum`. | This is the regeneration the brief's Scope allows. `./gradlew` is blocked in a worktree. |
| D5. What happens if a bump cannot be made green? | The implementer stops and returns a question with the recommended fallback from the table under Fallbacks. Using a different version changes AC-1, so the owner decides. | Any version other than the To column breaks AC-1. |
| D6. How is the KSP silent-skip guarded? | After C1 and after C4, delete `app/build/generated/ksp`, build, and check that exactly 5 `*_Impl.kt` files exist (see Risk 2). Also check that `git status --porcelain app/schemas` is empty. | KSP #3286 drops output silently. Stale files left from an earlier build would hide a skip. |
| D7. How is the Ktor refresh-header change (KTOR-9827) guarded? | No production change and no new test. The guards are AC-4 and a grep that `markAsRefreshTokenRequest` is still absent from `app/src/main` (the C2 checks). AC-7 covers the session on a device. | I diffed the `ktor-client-auth-jvm` sources of 3.5.2 and 3.6.1. The change is limited to requests marked with `AuthCircuitBreaker` (Risk 1). Puber never marks a request, so the refresh request carries the same header on both versions. |
| D8. How is AC-2 measured? | Before the C1 edit, on base, run `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin --warning-mode all` and save the log outside the repo. Repeat after C4 and diff the lines that name `build.gradle.kts`, `settings.gradle.kts` or `buildSrc`. | Gradle and AGP deprecations are reported at configuration time, so the comparison does not depend on what was up to date. |

### Changes by layer

The change touches no layer listed in the role addendum: API/repository, ViewModel, screen/DI, Compose and resources are all unchanged. Everything happens in build configuration.

| Commit | Files | Catalog keys and versions |
|---|---|---|
| C1 Persistence | `gradle/libs.versions.toml:4,33,34` | `ksp` 2.3.12, `room` 3.0.3, `sqlite` 2.7.1 |
| C2 Network | `gradle/libs.versions.toml:14,15,16` | `okhttp` 5.5.1, `ktor` 3.6.1, `kotlinxSerialization` 1.13.0 |
| C3 AndroidX runtime and test | `gradle/libs.versions.toml:5,6,7,21,27` | `coreKtx` 1.19.1, `appcompat` 1.8.0, `composeBom` 2026.09.00, `mockk` 1.14.12, `media3` 1.11.0 |
| C4 Build toolchain | `gradle/wrapper/gradle-wrapper.properties:3`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`, `gradle/libs.versions.toml:2,3,25,28,30,36` | wrapper 9.9.1, `agp` 9.5.1, `kotlin` 2.4.21, `coil` 3.6.3, `benchmarkMacroJunit4` 1.5.0, `uiautomator` 2.4.1, `kover` 0.9.11 |

Not edited: `detekt-compose-rules` (`gradle/libs.versions.toml:75`, Q2), `detekt` (`:13`), `buildSrc/src/main/kotlin/Versions.kt:12`.

Checks for each commit. `check.fast` (`./tools/agentw :app:compileDevDebugKotlin`) passes for every commit.

| Commit | Extra check before committing |
|---|---|
| C1 | The D6 `_Impl` listing for `devDebug`; `git status --porcelain app/schemas` is empty. |
| C2 | `./tools/agentw testDevDebugUnitTest --tests 'com.kino.puber.data.api.*'` passes, which includes `KinoPubApiClientAuthProbeTest` (AC-4). `grep -rn markAsRefreshTokenRequest app/src/main` prints nothing. |
| C3 | `./tools/agentw testDevDebugUnitTest` passes (mockk #1580, #1577). |
| C4 | Within the working tree, edit in this order and run `check.fast` after each step: wrapper; then AGP + coil + baselineprofile/benchmark + uiautomator; then Kotlin; then kover. Then the D8 diff, then `check.full`, then `./tools/agentw :app:assembleProdRelease` (AC-5), then the D6 listing for `devDebug` and `prodRelease`. |

### Fallbacks

Each fallback is a recommendation for the D5 question. It is not applied without an answer.

| Key | Fails how | Recommended fallback |
|---|---|---|
| `ksp` | Build error, or the `_Impl` set differs | Stay on 2.3.11, keep Room 3.0.3 and sqlite 2.7.1 |
| `room` / `sqlite` | KSP processor error, or the schema JSON changes | 3.0.1 / 2.7.0 |
| `ktor` | `data.api` tests red | 3.5.2. `okhttp` cannot go back on its own: `ktor-client-okhttp:3.6.1` pulls 5.5.1, and Gradle resolves to the highest version. |
| `kotlinxSerialization` | Decode behaviour in tests | 1.11.0. Do not use 1.12.x: it wraps serializer exceptions in `JsonException`. |
| `mockk` | Production code would need changing to make tests pass | 1.14.11. Test-only fixes stay in C3. |
| `composeBom` / `media3` | Compile errors, or failures at AC-8/AC-9 on the device | 2026.08.00 / 1.11.0 |
| `agp` | detekt classpath (`app/build.gradle.kts:307-322`) or lint breaks in a way no source fix solves | The latest 9.4.x patch, keeping coil 3.6.3 and Gradle 9.9.1 |
| wrapper | Fails with AGP 9.5.1 | Keep 9.9.1 (it carries the security fixes) and fall back on `agp` instead |
| `kotlin` | Compose compiler or KGP failure | 2.4.20 |

### Alternatives rejected

- One commit for everything: a single bad bump could not be reverted on its own.
- One commit per catalog key (17): over the owner's 5-commit limit, and couplings like `ktor`→`okhttp` and `agp`→`coil` cannot be split.
- Toolchain first: a library failure would then show up on an unproven toolchain, and a toolchain fallback would block the libraries.
- Adding a test that asserts the refresh request's headers: the source diff shows Puber's path is unchanged, so the test would guard behaviour the bump does not touch.
- Adding `markAsRefreshTokenRequest()` to the refresh request (`KinoPubApiClient.kt:197`): it changes production auth, which no AC asks for.

### Risks

1. **Session loss on the device (network, auth).** No `.env` in the worktree means no fresh login is possible. The refresh request at `KinoPubApiClient.kt:197-200` is unmarked and goes to the OAuth host. The bearer is attached through `sendWithoutRequest` (`:150`, `:1187-1190`) on both versions. In Ktor 3.6.1, `BearerAuthProvider.addRequestHeaders` returns early only for `AuthCircuitBreaker` requests. In 3.5.2 it removed their `Authorization` header. That is KTOR-9827. Auth-related transport changes from OkHttp 5.5 also land here: the hostname verifier and DoH (`data/di/modules.kt:57-61`). Guards: D7, AC-7, and qa runs `make DEVICE=<serial> auth-save` before the install.
2. **KSP output dropped silently (lifecycle).** `PuberDatabase.create` looks up `_Impl` by reflection (`data/db/PuberDatabase.kt:31`), so a missing class crashes at runtime. A missing `PuberDatabase_AutoMigration_2_3_Impl` is worse: combined with `fallbackToDestructiveMigration` (`:38`), it would silently drop a v2 database instead of crashing. The D6 listing must show exactly: `CachedPayloadDao_Impl`, `PuberDatabase_AutoMigration_2_3_Impl`, `PuberDatabase_Impl`, `WatchStateDao_Impl`, `WatchStateSyncDao_Impl` (under `app/build/generated/ksp/<variant>/kotlin/com/kino/puber/data/db/`). AC-10 covers the runtime.
3. **AGP 9.5.1 breaks the static gate (rollout).** detekt 2.0.0-alpha.6 has not been tested on AGP 9.5. The task-wide classpath override at `app/build.gradle.kts:307-322` depends on `KotlinCompile.libraries` and `compile<Variant>JavaWithJavac`. New lint checks fail with `abortOnError` (`:75`). Both are fixed in source, without baselines (AC-3). A JVM crash in `detektAll` is retried.
4. **Release R8 build (rollout).** Release has `isMinifyEnabled` and `isShrinkResources` on (`app/build.gradle.kts:183-184`), and AGP ≥ 9.4 needs coil 3.6.3. That is why they share C4. AC-5.
5. **Room transaction semantics (concurrency).** The Room 3.0.2/3.0.3 transaction and cancellation fixes run through `withWriteTransaction` (`data/db/DatabaseTransaction.kt:22`), which `WatchStateRepository` uses. AC-10 covers it on the device.
6. **mockk 1.14.12 verification counts (correctness, tests only).** This affects the unit suite in C3. Fix the tests; production code stays as it is.
7. **kotlinx-serialization 1.13.0 decoding (data shape).** The client's `Json` uses `ignoreUnknownKeys` and `isLenient` (`KinoPubApiClient.kt:125-129`). Whether 1.13.0 keeps the 1.12 nesting-depth limit is unverified. The C2 `data.api` tests and AC-8 cover it.
8. **Compose 1.12.1 focus and Media3 1.11.0 HLS (performance, playback).** No focus-related entry is listed upstream. Covered by AC-8 (D-pad scroll) and AC-9 (seek and resume).
9. **Baseline profile staleness (performance).** The rules in `app/src/main/generated/baselineProfiles/` that target renamed library methods stop matching. The cost is slower startup, not breakage. `baselineProfile.warnings.maxAgpVersion = false` (`app/build.gradle.kts:439`) hides the AGP-version warning. See Open question 1.

## Test design

### Risk profile

| # | Risk (source) | P | I | Score | Caught by |
|---|---|---|---|---|---|
| R1 | KSP drops a Room `_Impl` silently; a missing `PuberDatabase_AutoMigration_2_3_Impl` turns into a destructive migration, not a crash (design Risk 2) | 2 | 3 | 6 | AC-6 listing after a clean of `app/build/generated/ksp` (build), AC-10 (device) |
| R2 | AGP 9.5.1 + coil < 3.6.3 or a new R8 rule breaks the release build (design Risk 4) | 2 | 3 | 6 | AC-5 (build) |
| R3 | Session lost on the device after install: Ktor bearer KTOR-9827, OkHttp 5.5 hostname verifier or DoH (design Risk 1) | 1 | 3 | 3 | AC-4 (unit), the `markAsRefreshTokenRequest` grep (build), AC-7 (device), `auth-save` before install |
| R4 | AGP 9.5 breaks `detektAll` (custom classpath, `app/build.gradle.kts:307-322`) or adds lint errors (design Risk 3) | 2 | 2 | 4 | AC-3 (build) |
| R5 | `detektAll` JVM crash read as a pass (research.md: 7) | 2 | 2 | 4 | AC-3: evidence quotes `BUILD SUCCESSFUL` for the run that includes `:app:detektAll` |
| R6 | Room 3.0.3 / sqlite 2.7.1 transaction or DAO behaviour change (design Risk 5) | 1 | 3 | 3 | Instrumented DAO suites (P1), AC-10 (device) |
| R7 | New Gradle 9.9 / AGP 9.5 deprecation on the build scripts (brief AC-2) | 2 | 1 | 2 | AC-2 log diff |
| R8 | mockk 1.14.12 changes verification counts (design Risk 6) | 2 | 1 | 2 | Full unit suite (AC-3); accepted: test-only fallout |
| R9 | kotlinx-serialization 1.13.0 decoding change (design Risk 7) | 1 | 2 | 2 | `data.api` unit tests (AC-4 run), AC-8 |
| R10 | Compose 1.12.1 focus or Media3 1.11.0 HLS regression (design Risk 8) | 1 | 2 | 2 | AC-8, AC-9 (device) |
| R11 | Release build passes but crashes at runtime under R8 (stripped reflective class) | 1 | 3 | 3 | M6 (P2, conditional) |
| R12 | Baseline profile rules go stale (design Risk 9) | 2 | 1 | 2 | Accepted: startup cost only, perf track off; design Open question 1 |
| R13 | A catalog key other than the brief's list changes, or `detekt-compose-rules` moves (brief AC-1) | 1 | 2 | 2 | AC-1 diff |

### Traceability

| AC | Level | Priority | Test | Proves (P0: `guard` or `red`) |
|---|---|---|---|---|
| AC-1 | build | P0 | `git diff 4db9c0d4 -- gradle/libs.versions.toml gradle/wrapper/gradle-wrapper.properties` | guard: Fails if any To value differs, an unlisted key changes, or `detekt-compose-rules` leaves 1.4.0 |
| AC-2 | build | P1 | `./tools/agentw :app:compileDevDebugKotlin --warning-mode all` on base and after C4, lines naming `build.gradle.kts`, `settings.gradle.kts` or `buildSrc` diffed (design D8) | Fails if Gradle 9.9 or AGP 9.5 adds a deprecation against this project's scripts |
| AC-3 | build | P0 | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` with `AGENT_GRADLE_SERIAL=1`; then `git diff 4db9c0d4 --stat -- config/detekt app/lint*.xml` and `git diff 4db9c0d4 -G@Suppress -- app/src` | guard: Fails if a bump breaks the detekt classpath, adds a lint error, or a finding is hidden by a baseline or `@Suppress`; a JVM crash is retried, not counted |
| AC-4 | unit | P0 | `KinoPubApiClientAuthProbeTest.sessionExpiredFailureRecordsAuthErrThenThrowsUnauthorized`, `KinoPubApiClientAuthProbeTest.nothingIsRecordedWhenTheLogIsOff`, `KinoPubApiClientFailureTest`, `HistorySerializationTest`; `git diff 4db9c0d4 -- app/src/test/kotlin/com/kino/puber/data/api` is empty | guard: Fails if Ktor 3.6.1 changes the failed-refresh path (no `Unauthorized`, no auth record) or serialization 1.13.0 changes decoding; the empty diff fails if the test was edited to pass |
| AC-4 | build | P1 | `git grep -n markAsRefreshTokenRequest -- app/src/main` prints nothing | Fails if the refresh request becomes marked, the one case where KTOR-9827 changes headers (design D7) |
| AC-5 | build | P0 | `./tools/agentw :app:assembleProdRelease`; APK present under `app/build/outputs/apk/prod/release/` | guard: Fails if AGP 9.5.1 + coil or any library breaks R8 minify or resource shrinking |
| AC-6 | build | P0 | `rm -rf app/build/generated/ksp`, build devDebug (after C1 and C4) and prodRelease (after C4), list `*_Impl.kt` under `app/build/generated/ksp/<variant>/kotlin/com/kino/puber/data/db/`; `git status --porcelain app/schemas` is empty | guard: Fails unless exactly CachedPayloadDao_Impl, PuberDatabase_AutoMigration_2_3_Impl, PuberDatabase_Impl, WatchStateDao_Impl, WatchStateSyncDao_Impl exist, or if the schema JSON changes |
| AC-6 | instrumented | P1 | `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=com.kino.puber.data.db.WatchStateDaoTest`, then the same with `TESTS=com.kino.puber.data.db.CachedPayloadDaoTest` | Fails if Room 3.0.3 / sqlite 2.7.1 generated DAOs change upsert, sync, prune or delete results (R6) |
| AC-7 | device | P0 | M1, M2 | guard: Fails if the install or the libraries lose the stored session |
| AC-8 | device | P0 | M3 | guard: Fails if Coil 3.6.3 / Ktor 3.6.1 stop loading posters or D-pad focus breaks in rows |
| AC-9 | device | P0 | M4 | guard: Fails if Media3 1.11.0 HLS does not start, resume after seek, or stalls within 30 s |
| AC-10 | device | P0 | M5 | guard: Fails if the Room DB does not open or watch progress does not persist across a restart |
| AC-5 | device | P2 | M6 | Fails if R8 strips a class the release build needs at startup (R11) |

### Failing-first test

N/A: no bug fix; every P0 is `guard`.

### Manual checks

Emulator only, under a lease (`tools/emulator-resource-lock.sh`), every adb call with `-s <serial>`, dev package `com.kino.puber.stage`. Evidence: screenshots and filtered logcat only (`tools/mobile-evidence-audit.sh` before sharing).

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | `make DEVICE=<serial> auth-save`; build the worktree dev debug APK; install it with `tools/android-apk-install-preserve`; compare on-device `base.apk` sha256 with the built APK | Install succeeds, hashes match | Uninstall, data clear, downgrade or `-r` without authorization; a failed install treated as anything but a blocker |
| M2 | Launch the app (`adb -s <serial> shell monkey -p com.kino.puber.stage 1`) | Home opens | Login or pairing screen; crash |
| M3 | On Home press `KEYCODE_DPAD_DOWN` through at least 3 rows and `KEYCODE_DPAD_RIGHT` 5 times in one row; screenshot | Every focused and visible card shows its poster; focus stays on a card | Placeholder-only cards; focus jump to the rail or loss of focus |
| M4 | Open a title, start playback with `KEYCODE_DPAD_CENTER`; after it plays press `KEYCODE_DPAD_RIGHT` to seek forward; wait 30 s; screenshot before and after | Video starts, resumes after the seek, position advances over 30 s | Black screen, endless buffering, error overlay, crash |
| M5 | Back out of the player; `adb -s <serial> shell am force-stop com.kino.puber.stage`; launch again; open the same title; `adb -s <serial> logcat -d --pid=<pid>` filtered for `Room\|SQLite` | The title shows the saved progress | Progress missing; any Room or SQLite exception in the filtered log |
| M6 | Only if `com.kino.puber` is not installed on the leased emulator: install the AC-5 APK with `adb -s <serial> install` and launch it | App reaches its first screen (login, as the worktree has no credentials) without a crash | Install over an existing `com.kino.puber` (replaced signer); crash on start |

### Perf track

N/A: perf track off.

### UNTESTABLE

None.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q4 | Regenerate the baseline profile for the new library versions in this run? (architect) | no | answered | No; regenerate in the next release run (recommended) |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-12 | KSP 2.3.12, not the defective 2.3.13 | owner (standing instruction: recommended) |
| 2026-11-12 | detekt Compose-rules swap is the follow-up run detekt-compose-rules-revive; 1.4.0 line untouched here | owner (standing instruction: recommended) |
| 2026-11-12 | AGP 9.5.1 + Gradle 9.9.1 in this run; coil 3.6.3 lands with AGP | owner (standing instruction: recommended) |
| 2026-11-12 | Q4: baseline profile not regenerated in this run | owner (standing instruction: recommended) |
| 2026-11-12 | Gate 1: approved (size M, no tracks, 10 ACs, four commits C1-C4) | owner (standing instruction: recommended) |
| 2026-11-12 | Gate 1 delta: media3 stays at 1.11.0 (Fallbacks table). 1.11.1 removes `DefaultMediaSourceFactory.setExperimentalEnableHagcPlayback` with no replacement; `PlaybackController.kt:498` calls it behind a user setting. AC-1's media3 target is 1.11.0. Follow-up run `media3-hagc-removal`. | owner (standing instruction: recommended) |
| 2026-11-12 | Gate 1 delta (supersedes the AGP 9.4.1 fallback): AGP stays at 9.5.1. AGP 9.4+ rejects the release signingConfig fallback (`storeFile = file("debug.jks")` with no passwords or alias, `app/build.gradle.kts:172-174`) in :app:validateSigningProdRelease; 9.4.1 fails the same way, so the 9.4.x fallback does not help. Fix in C4: the fallback sets storePassword "android", keyAlias "androiddebugkey", keyPassword "android" explicitly; app/debug.jks is the committed standard debug keystore (checked with keytool), so the produced signature is unchanged and the keystore.properties / CI paths are untouched | owner (standing instruction: recommended) |
| 2026-11-12 | AC-10 goes to the gate as NOT CHECKED, partly covered: `WatchStateDaoTest` 8/8 and `CachedPayloadDaoTest` 6/6 pass on the device with Room 3.0.3 / sqlite 2.7.1 (incl. the `@Transaction` `upsertAllFromServer`); `withWriteTransaction` through `PuberDatabase.transactions()` on a file-backed database has not run. The on-device progress save fails on base 4db9c0d4 too (`resume-progress-anomaly`) | owner (standing instruction: recommended) |
