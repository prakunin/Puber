# Update library dependencies to the latest stable versions - plan

**Spec:** `docs/superpowers/specs/2026-11-12-dependency-update-stable.md`
**Goal:** Move the catalog and the Gradle wrapper to the spec's Target versions in four commits, C1 to C4, each green on its own.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- One task = green fast gate + green tests of the touched code + one commit,
  message per `commit_style` (one imperative English sentence, no prefixes), never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the
  files cited below, nothing else.
- Run every command from `/home/user1/orca/workspaces/Puber/dependency-update-stable`. Never `./gradlew` there; always `./tools/agentw` with `AGENT_GRADLE_SERIAL=1`.
- Versions are exactly the spec's Target versions table. If a bump cannot be made green by a fix to a test, lint or detekt finding, stop and report a question with the recommended fallback from the spec's Fallbacks table (design D5). Do not pick another version.
- A fix the bump forces goes into that bump's commit. No lint baseline, no detekt baseline entry (`config/detekt/detekt-baseline.xml`), no new `@Suppress`; production code changes only if lint or detekt requires them, and mockk fallout is fixed in tests only.
- Not edited: `gradle/libs.versions.toml:13` (`detekt`), `gradle/libs.versions.toml:75` (`detekt-compose-rules` 1.4.0), `buildSrc/src/main/kotlin/Versions.kt`, `app/schemas/`, `app/src/main/generated/baselineProfiles/`.
- Device checks (AC-6 instrumented row, AC-7 to AC-10, M1 to M6) belong to `qa-device` after Task 4. No task installs, launches or leases a device.
- Logs for AC-2 go to the run folder `/mnt/data/projects/PetProjets/Puber/.flow/runs/dependency-update-stable/` (outside the worktree).

## Coverage

| AC | Task | Test-design row |
|---|---|---|
| AC-1 | 4 (final diff) | `git diff 4db9c0d4 -- gradle/libs.versions.toml gradle/wrapper/gradle-wrapper.properties` |
| AC-2 | 1 (base log), 4 (diff) | `--warning-mode all` log diff (D8) |
| AC-3 | 3 (unit suite), 4 (full gate, baseline and `@Suppress` diff) | full gate + `git diff --stat` / `-G@Suppress` |
| AC-4 | 2 | `KinoPubApiClientAuthProbeTest` (2 tests), `KinoPubApiClientFailureTest`, `HistorySerializationTest`, empty test diff; `markAsRefreshTokenRequest` grep |
| AC-5 | 4 | `:app:assembleProdRelease`, APK present |
| AC-6 | 1, 4 (build); qa-device (instrumented `WatchStateDaoTest`, `CachedPayloadDaoTest`) | `_Impl` listing after clean; `git status --porcelain app/schemas` |
| AC-7 to AC-10 | qa-device on the Task 4 APK | M1 to M5 (M6 conditional, AC-5 P2) |

The five expected KSP files (AC-6), under `app/build/generated/ksp/<variant>/kotlin/com/kino/puber/data/db/`: `CachedPayloadDao_Impl.kt`, `PuberDatabase_AutoMigration_2_3_Impl.kt`, `PuberDatabase_Impl.kt`, `WatchStateDao_Impl.kt`, `WatchStateSyncDao_Impl.kt`. Listing command (`<variant>` = `devDebug` or `prodRelease`):

```
find app/build/generated/ksp/<variant>/kotlin/com/kino/puber/data/db -name '*_Impl.kt' -printf '%f\n' | sort
```

---

## Tasks

### Task 1: Bump KSP, Room and SQLite (C1 persistence)

**Covers:** AC-2 (base log), AC-6
**Files:**
- Modify: `gradle/libs.versions.toml:4` `ksp` 2.3.11 -> 2.3.12; `:33` `room` 3.0.1 -> 3.0.3 (also drives the `androidx.room3` plugin, `:49`); `:34` `sqlite` 2.7.0 -> 2.7.1

**Consumes:** `PuberDatabase.create` (`app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt:31`, `.fallbackToDestructiveMigration` at `:38`) loads the generated `_Impl` at runtime; a missing class is not a compile error.
**Produces:** catalog on Room 3.0.3 / KSP 2.3.12.

- [x] Before any edit, on base: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin --warning-mode all 2>&1 | tee /mnt/data/projects/PetProjets/Puber/.flow/runs/dependency-update-stable/warnings-base.log` (D8).
- [x] Edit the three lines.
- [x] `rm -rf app/build/generated/ksp`, then the fast gate.
- [x] Run the listing for `devDebug`: exactly the five files. `git status --porcelain app/schemas` prints nothing.
- [x] Commit, e.g. `Update KSP to 2.3.12, Room to 3.0.3 and SQLite to 2.7.1`.

**Done when:** fast gate green; listing equals the five names; `git status --porcelain app/schemas` empty; one commit touching only `gradle/libs.versions.toml`.

---

### Task 2: Bump OkHttp, Ktor and kotlinx-serialization (C2 network)

**Covers:** AC-4
**Files:**
- Modify: `gradle/libs.versions.toml:14` `okhttp` 5.4.0 -> 5.5.1; `:15` `ktor` 3.5.2 -> 3.6.1; `:16` `kotlinxSerialization` 1.11.0 -> 1.13.0
- Test (run, not edited): `app/src/test/kotlin/com/kino/puber/data/api/KinoPubApiClientAuthProbeTest.kt` (`sessionExpiredFailureRecordsAuthErrThenThrowsUnauthorized` `:76`, `nothingIsRecordedWhenTheLogIsOff` `:88`), `KinoPubApiClientFailureTest.kt`, `models/HistorySerializationTest.kt`

**Consumes:** refresh request `app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:197-200` (unmarked), bearer `sendWithoutRequest` `:150` with `shouldSendKinoPubToken` `:1187-1190`, `Json` config `:125-129`; OkHttp client with DoH in `app/src/main/java/com/kino/puber/data/di/modules.kt:57-61`.
**Produces:** catalog on Ktor 3.6.1 / OkHttp 5.5.1 / serialization 1.13.0.

- [x] Edit the three lines.
- [x] Fast gate.
- [x] `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests 'com.kino.puber.data.api.*'` passes.
- [x] `git diff 4db9c0d4 -- app/src/test/kotlin/com/kino/puber/data/api` is empty (a red test here is a stop-and-report, not a test edit).
- [x] `git grep -n markAsRefreshTokenRequest -- app/src/main` prints nothing.
- [x] Commit, e.g. `Update OkHttp to 5.5.1, Ktor to 3.6.1 and kotlinx-serialization to 1.13.0`.

**Done when:** fast gate and the `data.api` tests green; the test diff and the grep are empty; one commit.

---

### Task 3: Bump AndroidX runtime, Compose BOM, Media3 and mockk (C3)

**Covers:** AC-3 (unit suite part)
**Files:**
- Modify: `gradle/libs.versions.toml:5` `coreKtx` 1.19.0 -> 1.19.1; `:6` `appcompat` 1.7.1 -> 1.8.0; `:7` `composeBom` 2026.08.00 -> 2026.09.00; `:21` `mockk` 1.14.11 -> 1.14.12; `:27` `media3` 1.11.0 -> 1.11.1
- Test: any file under `app/src/test/` that mockk 1.14.12 makes red (verification counts, suspend recording), fixed in the test only

**Consumes:** nothing from Tasks 1-2 beyond a green tree.
**Produces:** catalog on the new AndroidX, Compose and Media3 versions.

- [x] Edit the five lines.
- [x] Fast gate.
- [x] `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest` passes. If a test needs a production change to pass, stop and report (fallback: mockk 1.14.11).
- [x] Commit, e.g. `Update core-ktx, appcompat, the Compose BOM, Media3 and mockk`.

**Done when:** fast gate and the full unit suite green; no file under `app/src/main` changed; one commit.

---

### Task 4: Bump the Gradle wrapper, AGP, Kotlin, Coil and the build plugins (C4 toolchain)

**Covers:** AC-1, AC-2, AC-3, AC-5, AC-6
**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties:3` 9.7.0 -> 9.9.1, plus `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat` as the `wrapper` task regenerates them
- Modify: `gradle/libs.versions.toml:2` `agp` 9.3.1 -> 9.5.1 (also `android-test`, `:47`); `:25` `coil` 3.5.0 -> 3.6.3; `:28` `benchmarkMacroJunit4` 1.5.0-alpha05 -> 1.5.0 (also the `androidx.baselineprofile` plugin, `:48`); `:30` `uiautomator` 2.3.0 -> 2.4.1; `:3` `kotlin` 2.4.10 -> 2.4.21; `:36` `kover` 0.9.9 -> 0.9.11
- Possibly modify: `app/build.gradle.kts` detekt classpath block `:307-322` (relies on `KotlinCompile.libraries` and `compile<Variant>JavaWithJavac`) or sources flagged by new lint checks (`lint { abortOnError = true }` `:74-75`), only as fixes, never as baselines

**Consumes:** release build type with `isMinifyEnabled`/`isShrinkResources` (`app/build.gradle.kts:183-184`), debug signing fallback (`:171-174`); `:baselineprofile` module (`baselineprofile/build.gradle.kts`) compiles against `benchmarkMacroJunit4` and `uiautomator`.
**Produces:** the final catalog and wrapper; the APKs qa-device uses for AC-7 to AC-10 and M6.

Steps, in this order, one commit at the end; run the fast gate after each step and stop at the first step that cannot be made green:

- [x] Wrapper: `AGENT_GRADLE_SERIAL=1 ./tools/agentw wrapper --gradle-version 9.9.1`, then the same command again (the second run regenerates the jar and scripts with 9.9.1). Keep the other lines of `gradle-wrapper.properties`; add no `distributionSha256Sum`. Fast gate.
- [x] AGP + Coil + baselineprofile/benchmark + uiautomator (lines 2, 25, 28, 30). Fast gate. Then compile the `:baselineprofile` module: find its Kotlin compile tasks with `AGENT_GRADLE_SERIAL=1 ./tools/agentw :baselineprofile:tasks --all | grep -E '^compile.*Kotlin'` and run the `dev` ones.
- [x] Kotlin (line 3). Fast gate.
- [x] Kover (line 36). Fast gate.
- [x] AC-2: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin --warning-mode all 2>&1 | tee /mnt/data/projects/PetProjets/Puber/.flow/runs/dependency-update-stable/warnings-c4.log`; `diff <(grep -E 'build\.gradle\.kts|settings\.gradle\.kts|buildSrc' <run>/warnings-base.log | sort -u) <(grep -E 'build\.gradle\.kts|settings\.gradle\.kts|buildSrc' <run>/warnings-c4.log | sort -u)` shows no added line.
- [x] AC-3: full gate passes and prints `BUILD SUCCESSFUL` (a JVM crash in `detektAll` is retried, not counted). `git diff 4db9c0d4 --stat -- config/detekt` and `git diff 4db9c0d4 -G@Suppress -- app/src` are empty; no `lint-baseline.xml` or `baseline =` appears in `app/`.
- [x] AC-5 and AC-6: `rm -rf app/build/generated/ksp`; fast gate; `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleProdRelease`; an APK exists under `app/build/outputs/apk/prod/release/`; the listing for `devDebug` and for `prodRelease` each gives exactly the five names; `git status --porcelain app/schemas` is empty.
- [x] Build the dev debug APK for qa-device: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleDevDebug`.
- [x] AC-1: `git diff 4db9c0d4 -- gradle/libs.versions.toml gradle/wrapper/gradle-wrapper.properties` changes exactly the 17 catalog keys and the wrapper line of the spec's Target versions table, each to its To value; `:75` still reads `version = "1.4.0"`.
- [x] Commit, e.g. `Update Gradle to 9.9.1, AGP to 9.5.1, Kotlin to 2.4.21 and Coil to 3.6.3`.

**Done when:** every step above holds; `git log --oneline 4db9c0d4..HEAD` shows the spec commit plus four task commits; `git status --short` is clean apart from build outputs.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

- 33352773 Update KSP to 2.3.12, Room to 3.0.3 and SQLite to 2.7.1 (Task 1)
- e5064d1b Update OkHttp to 5.5.1, Ktor to 3.6.1 and kotlinx-serialization to 1.13.0 (Task 2)
- 09a930ab Update core-ktx, appcompat, the Compose BOM and mockk (Task 3, `media3` stays 1.11.0 per the owner decision)
- 83c3fad9 Record the AGP release signing decision in the dependency update spec
- f7198347 Update Gradle to 9.9.1, AGP to 9.5.1, Kotlin to 2.4.21 and Coil to 3.6.3, and give the debug-signed release fallback its credentials (Task 4)

**Red run** (P0 `red` tests on the unchanged code): n/a, all rows are `guard`.

**Deviations from the plan:**

**Files touched outside the plan:**

**For the reviewers:**

**Blocked:** none. Resolved by owner decisions: `media3` stays 1.11.0 (spec, f0d73c62); AGP 9.4.1 failed the same release signing check as 9.5.1, so the fix went into the signing fallback.

**Task 4 result (`agp` 9.5.1, final):**

- Signing fix: `app/build.gradle.kts` release fallback branch (`else ->`) adds `storePassword = "android"`, `keyAlias = "androiddebugkey"`, `keyPassword = "android"` (standard debug keystore `app/debug.jks`); AGP 9.4+ rejects a signing config without credentials. Other branches and the debug config untouched.
- Fast gate, `:baselineprofile:compileDev{Benchmark,NonMinified}ReleaseKotlin`, `assembleProdRelease` (R8) and `assembleDevDebug`: `BUILD SUCCESSFUL`.
- `apksigner verify --print-certs` on the prod release APK: `CN=Android Debug`, SHA-256 `6b29181257cb520329553691b7a48c9b1123899950105127b3dd67012c49a83e`, equal to `app/debug.jks` `androiddebugkey` (6B:29:18:12:...:A8:3E).
- AC-6: `devDebug` and `prodRelease` listings give the five `_Impl.kt` names; `app/schemas` unchanged.
- AC-3: `check.full` `BUILD SUCCESSFUL in 8m 45s`; `-G@Suppress -- app/src` and `-G'baseline' -- app/build.gradle.kts` diffs empty; `config/detekt` unchanged.
- APKs for qa-device: `/home/user1/orca/workspaces/Puber/dependency-update-stable/app/build/outputs/apk/dev/debug/app-dev-debug.apk`, `/home/user1/orca/workspaces/Puber/dependency-update-stable/app/build/outputs/apk/prod/release/app-prod-release.apk`.

## Readiness

### Readiness 1: dependency-update-stable

Plan: `docs/superpowers/plans/2026-11-12-dependency-update-stable.md` (worktree, untracked). Spec at 619fbd8d. Test design: `test-design.md`.

**Verdict: READY**

#### Coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | 4 | build P0 guard, `git diff 4db9c0d4 -- gradle/...` |
| AC-2 | 1 (base log), 4 (diff) | build P1, `--warning-mode all` diff |
| AC-3 | 3, 4 | build P0 guard, full gate + `--stat` / `-G@Suppress` diff |
| AC-4 | 2 | unit P0 guard, `KinoPubApiClientAuthProbeTest.sessionExpiredFailureRecordsAuthErrThenThrowsUnauthorized`, `...nothingIsRecordedWhenTheLogIsOff`; build P1 grep |
| AC-5 | 4 | build P0 guard, `:app:assembleProdRelease`; device P2 M6 |
| AC-6 | 1, 4; qa-device | build P0 guard, `_Impl` listing; instrumented P1 `WatchStateDaoTest`, `CachedPayloadDaoTest` |
| AC-7 to AC-10 | qa-device after Task 4 | device P0 guard, M1 to M5 |

#### Citations sampled (worktree at 619fbd8d)

| Citation | Resolves to |
|---|---|
| `gradle/libs.versions.toml:2-36` (all Target rows), `:13`, `:47-49`, `:75` | every From value and line matches; `detekt-compose-rules` 1.4.0 at `:75` |
| `gradle/wrapper/gradle-wrapper.properties:3` | `gradle-9.7.0-bin.zip`, no `distributionSha256Sum` |
| `app/build.gradle.kts:74-75`, `:110`, `:171-174`, `:183-184`, `:307-322`, `:439` | `lint {` / `abortOnError = true`, end of `disable`, debug.jks fallback, minify/shrink, `withType<Detekt>` classpath block, `maxAgpVersion = false` |
| `KinoPubApiClient.kt:125-129`, `:150`, `:197-200`, `:1187-1190` | `Json` config, `sendWithoutRequest`, unmarked refresh `post`, `shouldSendKinoPubToken`; no `markAsRefreshTokenRequest` in `app/src/main` |
| `data/di/modules.kt:57-61` | `OkHttpClient` with `DnsOverHttpsFactory` |
| `data/db/PuberDatabase.kt:31`, `:38`; `DatabaseTransaction.kt:22` | `Room.databaseBuilder`, `fallbackToDestructiveMigration`, `withWriteTransaction` |
| `KinoPubApiClientAuthProbeTest.kt:76`, `:88` | the two named tests; refresh refused by an interceptor (500) |
| `buildSrc/src/main/kotlin/Versions.kt:12` | `CompileSdk = 37` |
| Five `_Impl.kt` names | match main checkout `app/build/generated/ksp/{devDebug,prodRelease}/.../data/db/` |
| `Makefile` `GRADLE ?=`, `itest` with `TESTS` | `:23`, `:128-135` |

#### Checks

| Check | Result |
|---|---|
| No task leaves the build red; order by dependency | yes: C1-C3 on base toolchain, each ends on fast gate (+ tests); C4 steps gated one by one with D5 stop |
| P0 rows name the defect, `guard`/`red`, named test | yes: all `guard`, build/device rows name the command or M-step; AC-4 names test methods in backticks |
| Inside Out of scope | yes: `:13`, `:75`, `Versions.kt`, `app/schemas/`, baseline profiles listed as not edited |
| Blocking open questions | none; Q1-Q4 answered |

#### Notes for the conductor (not blocking)

1. Spec AC-4 cites `KinoPubApiClientAuthProbeTest.kt:76-132`; the tests end at `:95`, the rest is the server and client helpers. The plan cites `:76`, `:88` correctly.
2. Task 4's AC-3 lint-baseline check is prose ("no `lint-baseline.xml` or `baseline =` in `app/`"), and `app/build.gradle.kts:264` already has `baseline = file(...detekt-baseline.xml)`, so a plain grep hits on base. Evidence should quote a diff instead: `git diff 4db9c0d4 -G'baseline' -- app/build.gradle.kts` empty and `ls app/lint*.xml` finds nothing.
3. Spec plus four task commits is five before evidence commits; the owner's under-five rule needs a squash before ship.

**Review round 1 fixes (docs and line endings only):**

- Triage #2: spec text aligned with the gate-1 delta, media3 target 1.11.0 (Target versions table, AC-9, C3, risk 8 text, R10, AC-9 test row); Decisions rows untouched.
- Triage #3: root `.gitattributes` (`*.bat text eol=crlf`, `gradlew text eol=lf`); `gradlew.bat` stored with LF, so `git diff 4db9c0d4 --stat -- gradlew.bat` shows the template change only (41+/11-). `git ls-files --eol`: both wrapper scripts `i/lf`; `git status` clean.
