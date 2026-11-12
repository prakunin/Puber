# Brief: dependency-update-stable

Base 4db9c0d4. Analyst, 2026-11-12. Builds on `research.md` (cited as research.md: section). No brief template exists in the plugin templates; this follows the analyst job layout.

kind: improvement. size: M. tracks: none. Device verification: **yes** (networking, auth, playback, persistence, build variants; research.md: 7. Risks, last line).

Size reason: one goal and two files of version data, but it touches the toolchain, networking and auth (Ktor, OkHttp), playback (Media3), persistence (Room3, KSP) and the release R8 build, all risk areas in `.flow/roles/analyst.md`.

## Request
Update the library dependencies in `gradle/libs.versions.toml` and the Gradle wrapper to the latest stable versions (research.md: 1. Request).

## Decided inputs (owner, standing instruction: recommended answers)
| ID | Decision |
|----|----------|
| Q1 | KSP **2.3.12**, not 2.3.13 (2.3.13 flagged defective upstream, google/ksp#3286; research.md: 5, KSP row). |
| Q2 | `detekt-compose-rules` stays at `ru.kode:detekt-rules-compose:1.4.0` (`gradle/libs.versions.toml:75`). The swap to `detekt-rules-compose-detekt2` is a separate follow-up run. |
| Q3 | AGP 9.5.1 and Gradle 9.9.1 are in this run; coil 3.6.3 lands in the same commit as AGP (research.md: 5, Coil row). |

## Target versions
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
| `media3` | 27 | 1.11.0 | 1.11.1 |
| `benchmarkMacroJunit4` (also the `androidx.baselineprofile` plugin, `:48`) | 28 | 1.5.0-alpha05 | 1.5.0 |
| `uiautomator` | 30 | 2.3.0 | 2.4.1 |
| `room` | 33 | 3.0.1 | 3.0.3 |
| `sqlite` | 34 | 2.7.0 | 2.7.1 |
| `kover` | 36 | 0.9.9 | 0.9.11 |
| `distributionUrl` (`gradle/wrapper/gradle-wrapper.properties:3`) | 3 | 9.7.0 | 9.9.1 |

Every other catalog entry stays as it is (research.md: 1. Request, "everything else already latest stable"); `detekt` stays `2.0.0-alpha.6` (`:13`).

## Scope
In: the version changes above; the wrapper files the Gradle `wrapper` task regenerates; fixes to tests, lint or detekt findings the bump surfaces, without baselining.
Out: the compose-rules swap (Q2); `dependency-prune` and `coil-shared-http` runs (research.md: 4); `CompileSdk`/`TargetSdk` in `buildSrc/src/main/kotlin/Versions.kt:12`.

## Acceptance criteria
- AC-1. Given the worktree after the change, When `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties` are read, Then every version equals the To column of Target versions, and `detekt-compose-rules` is still 1.4.0 (diff inspection).
- AC-2. Given the worktree, When `./tools/agentw :app:compileDevDebugKotlin` runs, Then it succeeds and the build output prints no new deprecation warning from Gradle 9.9 or AGP 9.5 that names this project's build scripts (log inspection against a base run).
- AC-3. Given the worktree, When `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` runs, Then all three pass with no new lint baseline, no detekt baseline entries and no `@Suppress` added for findings the bump introduced (a JVM crash in `detektAll` is retried, not counted as a pass; research.md: 7, detekt row).
- AC-4. Given the unit suite, When `KinoPubApiClientAuthProbeTest` runs, Then it passes unchanged, including the failing-refresh case (`KinoPubApiClientAuthProbeTest.kt:76-132`; guards the Ktor 3.6 bearer change KTOR-9827).
- AC-5. Given the worktree, When `./tools/agentw :app:assembleProdRelease` runs, Then it succeeds with R8 minify and resource shrinking on (`app/build.gradle.kts:183-185`), and the APK is produced under `app/build/outputs/apk/prod/release/` (the worktree signs with the debug fallback, `app/build.gradle.kts:171-174`).
- AC-6. Given the Room schema exports in `app/schemas/`, When the build runs KSP 2.3.12, Then the generated `_Impl` classes exist for the database and every DAO in `app/src/main/java/com/kino/puber/data/db/`, and no schema JSON changes (build output inspection and `git status app/schemas`).
- AC-7. Given an emulator with an existing logged-in Puber dev install, When the worktree's dev debug APK is installed with `tools/android-apk-install-preserve` and the app is launched, Then Home opens without the login screen (session survives the update) (device).
- AC-8. Given AC-7, When the user scrolls Home rows with the D-pad, Then posters load in the cards (Coil 3.6.3 + Ktor 3.6.1 network path) (device, screenshot).
- AC-9. Given a title's Details, When the user starts playback, seeks forward with the D-pad and lets it play, Then HLS video starts, resumes after the seek and keeps playing for at least 30 s (Media3 1.11.1) (device).
- AC-10. Given AC-9 left a watch position, When the app is force-stopped and launched again, Then the same title shows the saved progress (Room3 DB opens and the watch state persisted) and logcat for the app package shows no Room/SQLite exception (device; filtered logcat only, not stored raw).

## Risks
Covered in research.md: 7. Risks; the ones that decide the plan:
- KSP drops generated code silently: a missing Room `_Impl` is a runtime crash, not a compile error (AC-6, AC-10).
- New AGP 9.5 lint checks with `abortOnError = true` and no baseline (`app/build.gradle.kts:74-110`): fix, do not baseline (AC-3).
- Detekt alpha.6 is untested on AGP 9.5.1; the custom classpath at `app/build.gradle.kts:307-322` breaking is a blocker, not a skip (AC-3).
- mockk 1.14.12 suspend-call recording (#1580) may change verification counts: fix tests, keep production code (AC-3).
- The worktree build has no `.env`: a lost session cannot be restored by logging in. Install only with install-preserve; a failed install blocks the run (AGENTS.md).

## Open questions
N/A: Q1-Q3 decided by the owner (Decided inputs).
