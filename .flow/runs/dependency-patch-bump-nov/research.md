# Research: dependency-patch-bump-nov

## 1. Request

> update dependencies — Compose BOM 2026.09.00 -> 2026.11.01; AGP 9.5.1 -> 9.5.3; Gradle wrapper 9.9.1 -> 9.9.3; KSP 2.3.12 -> 2.3.14; Ktor 3.6.1 -> 3.6.2; Lifecycle 2.11.0 -> 2.11.1.

## 2. Understanding

Five of the six items are true patch releases with no API change for the way Puber uses them, and KSP 2.3.14 is the fixed release the previous run was waiting for. The Compose BOM item is not a patch: 2026.11.01 is the first BOM after 2026.09.00 and moves Compose UI/foundation/runtime 1.12.1 -> 1.13.0 and material3 1.4.0 -> 1.5.0. Compose 1.13 AARs declare `minCompileSdk=37` with `minCompileMinorSdk=1`, so every module must compile against API 37.1 (today 37, and only `platforms;android-37.0` is installed in the box). Compose 1.13 also changes the default input mode of Compose UI tests to Touch, which touches the focus instrumented suites.

## 3. Current state in code

- `gradle/libs.versions.toml:2` `agp = "9.5.1"`, `:3` `kotlin = "2.4.21"`, `:4` `ksp = "2.3.12"`, `:8` `composeBom = "2026.09.00"`, `:12` `lifecycleRuntimeKtx = "2.11.0"`, `:16` `ktor = "3.6.1"`. `lifecycleRuntimeKtx` drives only `androidx-lifecycle-runtime-ktx` (`app/build.gradle.kts:375`); `lifecycle-runtime-compose`, `viewmodel` arrive transitively.
- `gradle/wrapper/gradle-wrapper.properties:3`: 9.9.1; no `distributionSha256Sum`. `gradle-wrapper.jar` sha256 `621008af...daf8`.
- `buildSrc/src/main/kotlin/Versions.kt:8-12`: `CompileSdk = 37` (comment cites Compose 1.12 `minCompileSdk=37`); `TargetSdk = 36`, `MinSdk = 24`. Used by `app/build.gradle.kts:48`, `baselineprofile/build.gradle.kts:10`, `player-test-fixtures/build.gradle.kts:7` as `compileSdk = Versions.CompileSdk`. No `compileSdkMinor` / `minorApiLevel` anywhere.
- `/opt/android-sdk/platforms`: only `android-37.0`; SDK dir owned by `dev`, licences present; build-tools 36.0.0.
- `app/build.gradle.kts:171-178`: release signing fallback with explicit debug credentials (from dependency-update-stable; AGP 9.4+ rejects the passwordless form). Must stay as is.
- `app/build.gradle.kts:423`: `ksp(libs.androidx.room.compiler)`, the only KSP consumer (Room3).
- `app/build.gradle.kts:86`: lint `warningsAsErrors = false`; `config/detekt/detekt.yml:441-442` `Deprecation: active: false`. New Compose deprecations do not fail the gate.
- `app/build.gradle.kts:453-466`: baseline profiles generated only from `instrumentationRelease`, `saveInSrc`, `automaticGenerationDuringBuild = false`, `warnings.maxAgpVersion = false`. Profiles last regenerated `367bc6d2` (2026-11-27) against Compose 1.12.1; `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt:20-45` guards app-class rules only.
- Compose API surface in `app/src/main`: material3 `MaterialTheme`, `Text`, `Card`, `Button`, `Switch`, `Snackbar*`, `Circular/LinearProgressIndicator`, `ColorScheme`, `LocalMinimumInteractiveComponentSize`; no `Slider`, `ModalBottomSheet`, `Modifier.blur`, `LazyListPrefetchStrategy`. `BasicTextField` value/onValueChange overload at `ui/feature/search/content/SearchScreenContent.kt:159` and `core/ui/uikit/component/ApiDomainDialog.kt:210` (deprecated in Compose 1.13, warning only).
- `androidx.tv:tv-material` 1.1.0 (latest stable; AAR `minCompileSdk=35`); `com.eygraber:compose-placeholder-material3` 1.0.12.
- Compose UI tests: 37 files under `app/src/androidTest*` use `createComposeRule` / `createAndroidComposeRule` / `runComposeUiTest`, 30 drive D-pad keys; none sets `InputMode` or `ComposeUiTestConfig` (grep).
- Lifecycle: `ui/feature/device/settings/DeviceSettingsContent.kt:213-214` calls `lifecycle.currentStateFlow.first {...}`, the property 2.11.1 changes (same instance per call).
- Ktor in `app/src/main`: `HttpClient(OkHttp)`, `Auth`/`bearer`, `ContentNegotiation`, `HttpTimeout`, `HttpRequestRetry`, `DefaultRequest`, `createClientPlugin`. `data/api/KtorPlugins.kt:106` `channel.readRemaining(max)` is the deprecated call owned by `ktor-readremaining-deprecation`.

## 4. Related work

| Item | Relation |
|---|---|
| run `dependency-update-stable` (done, merged `e2bccaf5`; squashed `44e5601a`, `dba6ae5f`) | Previous bump. Reusable: KSP 2.3.12 chosen over defective 2.3.13; signing fallback credentials for AGP 9.4+ (`app/build.gradle.kts:171-178`); wrapper bumped with `./tools/agentw wrapper --gradle-version` twice, `gradlew.bat` CRLF nit fixed with `.gitattributes` (triage-1 #3); wrapper jar sha256 checked against services.gradle.org (triage-1); baseline profile not regenerated (Q4); AC-10 Room write persistence left NOT CHECKED. Its ACs (catalog diff, `--warning-mode all` diff, `assembleProdRelease`, KSP `*_Impl` listing after cleaning `app/build/generated/ksp`, DAO itests) transfer directly. |
| run `ktor-readremaining-deprecation` (idea) | Owns `KtorPlugins.kt:106`. Ktor 3.6.2 adds no new deprecation (release page); stays separate. |
| run `gradle-build-cache` (stage review, branch `prakunin/gradle-build-cache`) | Touches `gradle.properties`, `app/build.gradle.kts`, `AGENTS.md`. Merge-order overlap on `app/build.gradle.kts`; no version overlap. |
| run `media3-hagc-removal`, `detekt-compose-rules-revive` (ideas) | Deferred from the previous bump; not in this request. |
| run `hermetic-baseline-profiles` and commit `367bc6d2` | Profiles now regenerate hermetically (`tools/generate-baseline-profile.sh`, emulator only per owner exception 2026-11-26). Compose 1.13 renames make library rules stale. |
| run `r8-keep-narrowing` (idea) | Whole-library keep rules incl. Ktor; no conflict. |

Duplicate: none.

## 5. External findings

| Bump | Finding relevant here | Source |
|---|---|---|
| Compose BOM 2026.11.01 | Published 2026-11-19; no BOM between 2026.09.00 and it. Maps ui/foundation/runtime/animation/material 1.13.0, material3 1.5.0, icons 1.7.8 (unchanged). | dl.google.com `compose-bom/maven-metadata.xml`, `compose-bom-2026.11.01.pom` |
| Compose UI/foundation 1.13.0 | AAR metadata (checked): `minCompileSdk=37`, `minCompileMinorSdk=1`, `minAndroidGradlePluginVersion=9.1.0`. Notes: libraries moved to compileSdk 37.1 (1.13.0-alpha03); kotlinc >= 2.2; single-line text height optimisation on by default ("a few pixel difference"); `VectorPainter` bitmap sharing; Compose test rules default to `InputMode.Touch` (alpha01), fix with `ComposeUiTestConfig(inputMode = InputMode.Keyboard)`, temporary flag `AndroidComposeUiTestFlags.isInputModeSetForDeviceTests`; value/onValueChange `BasicTextField` deprecated; ui depends on lifecycle 2.10.0. | developer.android.com/jetpack/androidx/releases/compose-ui (1.13.0, 2026-11-18); AARs `ui-android-1.13.0`, `foundation-android-1.13.0` |
| material3 1.5.0 | 2026-11-18. AAR `minCompileSdk=37`, minor 0. Source-breaking only for Slider/RangeSlider (unused). `MaterialTheme` values consolidated into one composition local; `LocalMotionScheme` deprecated (unused). No entries for Switch, Card, Snackbar. | developer.android.com/jetpack/androidx/releases/compose-material3; AAR `material3-android-1.5.0` |
| compileSdk 37.1 | `platforms;android-37.1` exists in Google's SDK repository. DSL since AGP 8.13: `compileSdk { version = release(37) { minorApiLevel = 1 } }`; older `compileSdkMinor` (AGP 8.10.1) is slated for removal in AGP 10. Whether AGP 9.5.x accepts 37.1 under "max API level 37" is not stated on the AGP page; community PRs report AGP 9.5.2/9.5.3 + 37.1 building (unverified primary). AGP auto-download of the platform in the box/CI: unverified. | dl.google.com/android/repository/repository2-3.xml; developer.android.com/reference/tools/gradle-api/8.13/com/android/build/api/dsl/CompileSdkReleaseSpec; developer.android.com/build/gradle-build-overview |
| AGP 9.5.3 | 9.5.2 and 9.5.3 exist on Google Maven; the official 9.5 notes list fixes only up to 9.5.1 (none public), so contents are unverified. 9.5 line: min Gradle 9.6.0, JDK 17, max API 37. Gradle 9.9.3 is in range. | dl.google.com `com/android/tools/build/gradle/maven-metadata.xml`; developer.android.com/build/releases/agp-9-5-0-release-notes |
| Gradle 9.9.3 | 2026-11-27. 9.9.2 fixes four High security advisories (SFTP host keys, snapshot dependency verification, TestKit temp dir, daemon registry) and three regressions; 9.9.3 fixes a 9.9.2 start failure on an ImDisk RAM disk. Wrapper task keeps existing properties. `gradle-9.9.3-wrapper.jar.sha256` = `621008af...daf8`, identical to 9.9.1 and to the committed jar; `bin.zip` sha256 `4f2135b7...1e4b`. | docs.gradle.org/9.9.3/release-notes.html; services.gradle.org/distributions/*.sha256 |
| KSP 2.3.14 | 2026-11-13, "Latest", no known issues. Fixes #3286 (library symbols disappear in concurrent executions), the defect that made 2.3.13 unusable. Adds context parameter reading, skips Analysis API setup on no-op incremental builds, `KSAnnotation` vararg/defaults fixes. Carries 2.3.13's changes (opt-in classloader cache, no cache clearing between rounds, incremental classpath scan). No Kotlin/AGP requirement listed; KSP2 is not tied to the Kotlin version, so Kotlin 2.4.21 is fine (as with 2.3.12). | github.com/google/ksp/releases |
| Ktor 3.6.2 | 2026-11-25, bugfix only, no deprecations. Client: incomplete streamed responses no longer read silently (KTOR-6905); OkHttp WebSocket send hang (unused). `ktor-serialization-kotlinx` no longer pulls `kotlinx-datetime:0.8.0-0.6.x-compat` (KTOR-9930). No bearer or ContentNegotiation entries. `ktor-client-okhttp:3.6.2` still requires okhttp 5.5.1 and coroutines 1.11.0 (matches the catalog). | github.com/ktorio/ktor/releases/tag/3.6.2; Maven Central `ktor-client-okhttp-jvm-3.6.2.module` |
| Lifecycle 2.11.1 | 2026-11-18. One fix: `LifecycleRegistry.currentStateFlow` returns the same instance per call (b/579057749). No API or dependency changes. AAR `lifecycle-runtime-compose` 2.11.1: `minCompileSdk=37`, AGP >= 9.1.0. | developer.android.com/jetpack/androidx/releases/lifecycle; AAR metadata |

## 6. Options

| # | Option | Trade-offs | Size |
|---|---|---|---|
| A **(recommended)** | This run: AGP 9.5.3, Gradle 9.9.3, KSP 2.3.14, Ktor 3.6.2, Lifecycle 2.11.1. Capture a follow-up run for Compose BOM 2026.11.01 (Compose 1.13 / M3 1.5, compileSdk 37.1, SDK platform install, test input mode, visual check). | Ships the Gradle security fixes and the KSP fix fast with the previous run's ACs; no source change expected. The Compose jump gets its own design (compileSdk DSL, 37 Compose UI test files, track ui) and its own blame window. Two device passes. | S |
| B | Everything in one run, as requested: the five bumps plus the BOM, `Versions.CompileSdk` -> 37.1 via `compileSdk { version = release(...) { minorApiLevel = 1 } }` in the three modules, platform 37.1 in the box and CI, input-mode fix in the Compose UI tests, baseline profile regeneration. | One verification pass, but it crosses build config, every UI test and visible text metrics; a focus or text regression is mixed with toolchain changes. | M |
| C | The five bumps only; keep the BOM at 2026.09.00 with no follow-up until Compose 1.13.x patches. | Smallest, but leaves Compose behind with no plan. | S |

## 7. Risks

| Risk | Where | Mitigation |
|---|---|---|
| Compose 1.13 fails `checkDebugAarMetadata` against compileSdk 37 | `Versions.kt:12`, AAR `minCompileMinorSdk=1` | Option A defers it; under B, compile against 37.1 and install `platforms;android-37.1` (box and CI). |
| Compose UI tests flip to `InputMode.Touch`; D-pad focus tests fail or pass for the wrong reason | 37 files in `app/src/androidTest*` | Under B: `ComposeUiTestConfig(inputMode = InputMode.Keyboard)` in each rule, full `make DEVICE=192.168.1.110:5555 itest`. |
| Text height shifts a few pixels (single-line optimisation) on TV cards and overlays | Compose 1.13 notes | Under B: track ui, ux-designer visual review on the box. |
| KSP 2.3.14 carries 2.3.13's caching changes; a missing Room `_Impl` surfaces only at runtime | `app/build.gradle.kts:423` | Reuse the previous AC: clean `app/build/generated/ksp`, list the five `*_Impl` files for devDebug and prodRelease; DAO itests on the box. |
| AGP 9.5.2/9.5.3 contents undocumented | AGP 9.5 notes | `--warning-mode all` diff, `check.full`, `:app:assembleProdRelease` (signing fallback, R8). |
| Baseline profile library rules stale (bigger under B: Compose 1.13) | `app/src/main/generated/baselineProfiles/` | Startup cost only; regenerate in this run (B) or the Compose run (A) on the emulator per the owner exception. |
| Box session lost after the worktree install | memory: worktree build loses box session | `make DEVICE=192.168.1.110:5555 auth-save` before install; install-preserve; on-device sha256 check. |

## 8. Open questions

| ID | Question | Recommended answer |
|----|----------|--------------------|
| Q1 | Take Compose BOM 2026.11.01 (Compose 1.13, compileSdk 37.1) in this run, or split it out? | Split (Option A): this run does the five patch bumps; capture `compose-1-13-bump` as an idea run with track ui. |
| Q2 | Regenerate the baseline profile in this run? | No for Option A (no Compose change; library rules barely move); yes in the Compose run. |
| Q3 | Add `distributionSha256Sum` to `gradle-wrapper.properties` now that 9.9.2 fixes dependency-verification issues? | No; keep the previous run's decision (D4) and verify the jar sha256 against services.gradle.org (it equals the committed jar). |

## 9. Sources

- Files: `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `buildSrc/src/main/kotlin/Versions.kt`, `app/build.gradle.kts`, `baselineprofile/build.gradle.kts`, `player-test-fixtures/build.gradle.kts`, `config/detekt/detekt.yml`, `app/src/main/java/com/kino/puber/data/api/KtorPlugins.kt`, `app/src/main/java/com/kino/puber/ui/feature/device/settings/DeviceSettingsContent.kt`, `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt`, `docs/release.md`, `.flow/config.yaml`, `/opt/android-sdk/platforms`.
- Runs: `dependency-update-stable` (state, research, triage-1), `ktor-readremaining-deprecation`, `gradle-build-cache`, `media3-hagc-removal`, `detekt-compose-rules-revive`, `hermetic-baseline-profiles`, `r8-keep-narrowing`; spec `docs/superpowers/specs/2026-11-12-dependency-update-stable.md`.
- Commits: `dba6ae5f`, `44e5601a`, `367bc6d2`, `6cd733f4`.
- URLs: https://dl.google.com/android/maven2/androidx/compose/compose-bom/2026.11.01/compose-bom-2026.11.01.pom; https://dl.google.com/android/maven2/androidx/compose/compose-bom/maven-metadata.xml; AARs under https://dl.google.com/android/maven2/androidx/ (ui-android 1.13.0, foundation-android 1.13.0, material3-android 1.5.0, lifecycle-runtime-android 2.11.1, lifecycle-runtime-compose-android 2.11.1, tv-material 1.1.0); https://developer.android.com/jetpack/androidx/releases/compose-ui; https://developer.android.com/jetpack/androidx/releases/compose-material3; https://developer.android.com/jetpack/androidx/releases/lifecycle; https://developer.android.com/build/releases/agp-9-5-0-release-notes; https://dl.google.com/android/maven2/com/android/tools/build/gradle/maven-metadata.xml; https://dl.google.com/android/repository/repository2-3.xml; https://developer.android.com/reference/tools/gradle-api/8.13/com/android/build/api/dsl/CompileSdkReleaseSpec; https://docs.gradle.org/9.9.3/release-notes.html; https://services.gradle.org/distributions/gradle-9.9.3-wrapper.jar.sha256; https://github.com/google/ksp/releases; https://github.com/ktorio/ktor/releases/tag/3.6.2; https://repo1.maven.org/maven2/io/ktor/ktor-client-okhttp-jvm/3.6.2/ktor-client-okhttp-jvm-3.6.2.module.
