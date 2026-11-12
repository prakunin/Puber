# Research: dependency-update-stable

## 1. Request

> update Puber's library dependencies (gradle/libs.versions.toml, gradle wrapper) to the latest stable versions. Conductor's version scan on 2026-11-12 (Maven Central / Google Maven / Gradle Plugin Portal metadata):
> Patch: Kotlin 2.4.10→2.4.21, KSP 2.3.11→2.3.13, core-ktx 1.19.0→1.19.1, mockk 1.14.11→1.14.12, media3 1.11.0→1.11.1, room3 3.0.1→3.0.3, sqlite 2.7.0→2.7.1, kover 0.9.9→0.9.11.
> Minor: Compose BOM 2026.08.00→2026.09.00, appcompat 1.7.1→1.8.0, ktor 3.5.2→3.6.1, okhttp 5.4.0→5.5.1, kotlinx-serialization 1.11.0→1.13.0, coil 3.5.0→3.6.3, uiautomator 2.3.0→2.4.1, benchmark/baselineprofile 1.5.0-alpha05→1.5.0.
> Separate: AGP 9.3.1→9.5.1 with Gradle wrapper 9.7.0→9.9.1; detekt-rules-compose (ru.kode) 1.4.0→2.2.0 while detekt itself is dev.detekt 2.0.0-alpha.6 (latest; no stable 2.x).
> Everything else already latest stable. Skip alphas/betas.

## 2. Understanding

Raise the version catalog and the Gradle wrapper to the newest stable releases. Most bumps are patch or small minor releases with no API change for the way Puber uses them. Three items are not plain bumps. KSP 2.3.13 is marked defective upstream, so 2.3.12 is the newest safe version. Coil 3.6.3 is required by AGP 9.4+ release (R8) builds, so coil and AGP move together. The `ru.kode` compose rules have never loaded under detekt 2: the 1.4.0 jar registers against the detekt 1 API. Version 2.2.0 of the same coordinate does the same. Only the new `detekt-rules-compose-detekt2` artifact loads, and swapping to it turns on nine Compose rules that have not run since 2026-08-14.

## 3. Current state in code

- `gradle/libs.versions.toml:1-40`: all versions. `detekt-compose-rules` is hardcoded to `version = "1.4.0"` (`:79`), the only inline version. `benchmarkMacroJunit4` also drives the `androidx.baselineprofile` plugin (`:52`).
- `gradle/wrapper/gradle-wrapper.properties:3`: Gradle 9.7.0.
- `buildSrc/src/main/kotlin/Versions.kt:12`: `CompileSdk = 37`, justified by Compose 1.12 / core 1.19 `minCompileSdk=37`; `MinSdk = 24`, `TargetSdk = 36`, toolchain JDK 21, bytecode 17.
- `app/build.gradle.kts:74-110`: Lint `abortOnError = true`, no baseline. `GradleDependency`, `NewerVersionAvailable` and `AndroidGradlePluginVersion` are disabled because "Upgrading AGP, Kotlin and the libraries is its own change with its own verification" (`:89-92`).
- `app/build.gradle.kts:183-185`: release `isMinifyEnabled = true`, `isShrinkResources = true` (R8 path matters for coil/AGP).
- `app/build.gradle.kts:236-239`: `room3 { schemaDirectory(...) }`; schemas `app/schemas/.../1-3.json`; `ksp(libs.androidx.room.compiler)` at `:405` is the only KSP consumer.
- `app/build.gradle.kts:259-265`: detekt `buildUponDefaultConfig = false`, config `config/detekt/detekt.yml`, baseline `config/detekt/detekt-baseline.xml` (empty, 145 bytes). No `maxIssues`/`failOnSeverity`, so any finding fails the gate (default detekt 2 behaviour assumed, unverified).
- `app/build.gradle.kts:307-322`: custom detekt classpath built from the variant's `KotlinCompile.libraries`, boot classpath and javac output.
- `app/build.gradle.kts:328-337`: detekt 2 standalone session lacks parcelize/serialization plugins, about 83 compiler errors, "Revisit when 2.0 ships a way to load them".
- `app/build.gradle.kts:420`: `detektPlugins(libs.detekt.compose.rules)`.
- `config/detekt/detekt.yml:881-901`: `compose:` rule set with 10 entries (9 active: `ReusedModifierInstance`, `UnnecessaryEventHandlerParameter`, `ComposableEventParameterNaming`, `ComposableParametersOrdering`, `ModifierHeightWithText`, `ModifierDefaultValue`, `MissingModifierDefaultValue`, `PublicComposablePreview`, `ComposableFunctionName`; `TopLevelComposableFunctions` off).
- **Compose rules are dead today.** `~/.gradle/caches/.../ru.kode/detekt-rules-compose/1.4.0/...jar` has `META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider` (detekt 1 API). `app/build/reports/detekt/devDebugUnitTest.sarif` (2026-11-11, detekt 2.0.0-alpha.6) lists 227 rules in sets `comments, complexity, coroutines, empty-blocks, exceptions, naming, performance, potential-bugs, style`, with no `compose`.
- `app/src/main/java/com/kino/puber/PuberApp.kt:100-125`: Coil `ImageLoader` with memory/disk cache and an `HttpsEnforcingInterceptor`. No fetcher is registered, so `coil-network-ktor3` auto-registers its own default Ktor client.
- `app/src/main/java/com/kino/puber/data/di/modules.kt:57-61`: one shared `OkHttpClient` with `DnsOverHttpsFactory`.
- `app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:132-175`: `HttpClient(OkHttp)` with `preconfigured`, an OkHttp `Cache`, `Auth { bearer { loadTokens; sendWithoutRequest; refreshTokens } }`, `HttpTimeout`, `ContentNegotiation`. Refresh at `:192-200` posts through the same client without `markAsRefreshTokenRequest()`.
- `TmdbApiClient.kt:88-92`, `TheIntroDbApiClient.kt:31-33`, `IntroDbAppApiClient.kt:31-33`: same `HttpClient(OkHttp) { engine { preconfigured } }` pattern.
- `app/src/main/java/com/kino/puber/data/db/*`: Room3 DB with 3 entities; no `@Transaction` DAO wrappers and no `close()` in `main` (grep). `androidTest/.../WatchStateDaoTest.kt:31-40` and `CachedPayloadDaoTest.kt:27-36` use `AndroidSQLiteDriver` and `close()`.
- `app/src/main/res/values/themes.xml:3`: the only `appcompat` use is the theme parent (no `AppCompatDelegate` calls; locales via `core/system/AppLocale.kt:21`).
- `baselineprofile/build.gradle.kts`: `com.android.test` + `androidx.baselineprofile`, `useConnectedDevices = true`. Sources use `BaselineProfileRule`, `MacrobenchmarkRule`, `StartupTimingMetric` only, no `TraceProcessor` or new UiAutomator APIs.
- Tests: JUnit 5, 69 test files with `relaxed = true` mocks; Ktor `MockEngine` in 2 files; `KinoPubApiClientAuthProbeTest.kt:76-132` covers a failing refresh.
- `gradle.properties`: no `org.gradle.parallel`; `tools/agentw` isolates `GRADLE_USER_HOME` and serialises with `AGENT_GRADLE_SERIAL`.
- CI `.github/workflows/pr-checks.yml`: JDK 21, `./gradlew :app:detektAll`, `:app:lintProdDebug`, `testProdDebugUnitTest`.

## 4. Related work

| Item | Relation |
|---|---|
| `03c2ca31` Release 1.7.1 (2026-08-14) | Previous bulk bump: AGP 9.1.1→9.3.1, Kotlin 2.3.21→2.4.10, KSP 2.3.6→2.3.11, Gradle 9.3.1→9.7.0, detekt `io.gitlab.arturbosch` 1.23.8 → `dev.detekt` 2.0.0-alpha.6, Room3 added, `tv-foundation` and `voyager-koin` dropped. The compose rules have been silent since this commit. |
| `ecfcd6b8` | Detekt classpath fix for `BuildConfig` (`app/build.gradle.kts:292-322`); the AGP bump must keep it working. |
| run `dependency-prune` (idea) | Drops `ktor-client-logging`, `coil-video`, `security-crypto` (audit BP-4). Overlaps the catalog lines this run bumps. |
| run `coil-shared-http` (idea) | Give Coil the shared OkHttp/DoH client (audit PERF-5). Coil 3.6 does not change the fetcher, so it stays independent. |
| run `code-quality-audit`, `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md:147-160` | `detektAll` crashed the Gradle JVM 5 times on this host under swap pressure; neighbouring worktrees reported 0 findings. Pitfall for verifying this run. |
| `docs/superpowers/plans/2026-10-15-subtitle-restore-model.md:650` | Relies on detekt `2.0.0-alpha.6` thresholds (`ComplexCondition`, `ReturnCount`). |

No pinned version carries a recorded reason except `CompileSdk = 37` (`buildSrc/src/main/kotlin/Versions.kt:9-12`). Duplicate: none.

## 5. External findings

| Bump | Finding relevant here | Source |
|---|---|---|
| AGP 9.5.1 | Min Gradle 9.6.0, JDK 17, max API 37. 9.5.0-alpha09 fixes "Gradle 9.8.0 reports `Configuration.setVisible()` deprecations with AGP 9.4.1". 9.5.0-alpha06 fixes `optimization { enable }` not setting `isMinifyEnabled`. 9.4 adds `android.newDsl.optOut` ahead of AGP 10. No DSL removal listed. | developer.android.com/build/releases/agp-9-5-0-release-notes, agp-9-4-0-release-notes |
| AGP POM | AGP 9.3.1 and 9.5.1 both depend on KGP 2.2.10 (the project's KGP wins). | dl.google.com `gradle-9.5.1.pom` |
| Gradle 9.9.1 | 9.9.1 fixes three security issues (catalog values run as code, critical; daemon-registry trust ×2). Embedded Kotlin 2.4.21 (9.8 had 2.4.10). 9.8 deprecates configuration-time `ProcessBuilder`/`exec` (none in `app/build.gradle.kts`). 9.9: `GradleException` resolution API, test-filter semantics, the wrapper task keeps existing properties. | docs.gradle.org/9.9.1/release-notes.html, /userguide/upgrading_version_9.html |
| Kotlin 2.4.21 | Officially supports Gradle 7.6.3–9.7.0 and AGP 8.5.2–9.3.1, so 9.9.1/9.5.1 are above the tested range ("may produce deprecation warnings"). Today's 2.4.10 + Gradle 9.7.0 + AGP 9.3.1 is already above 2.4.10's range (Gradle ≤9.5.0, AGP ≤9.1.0) and works. 2.4.20 carries Compose-compiler fixes (cross-module getter/setter miscompile b/537617330, non-local return in `when` lambdas b/549552317). 2.4.21 changes are JS/Wasm/reflection. | kotlinlang.org/docs/gradle-configure-project.html; github.com/JetBrains/kotlin/releases |
| KSP 2.3.13 | **Marked defective by upstream**: issue #3286, "library symbols disappear during concurrent in-process executions". Generated code can be silently omitted. Fixed in PR #3294 (2026-11-09), not yet released. Workaround: stay on 2.3.12. 2.3.12 (2026-09-09) raises min AGP to 8.12; its new features are opt-in. KSP 2.3.x is not tied to the Kotlin version (KSP2 runs on its own analysis API). | github.com/google/ksp/releases; github.com/google/ksp/issues/3286; Maven Central metadata (2.3.13 latest, 2026-10-30) |
| detekt 2.0.0-alpha.6 | Built against Gradle 9.6.1, Kotlin 2.4.10, AGP 9.3.1. AGP 9.5 compatibility is not listed (unverified). | detekt.dev/docs/introduction/compatibility |
| detekt-rules-compose | 2.0.0 split into `ru.kode:detekt-rules-compose` (detekt 1.22–1.23.8) and `ru.kode:detekt-rules-compose-detekt2` (detekt 2.0.0-alpha.6). Rule IDs, the `compose` set id and existing config keys are unchanged through 2.2.0. New optional keys have defaults that keep earlier behaviour. 2.0.0 checks expression-bodied composables and nullable handlers (new findings possible). 2.1.0: `ComposableParametersOrdering` stops forcing slots last; `UnnecessaryEventHandlerParameter.reportConstantArguments` defaults to `true`. 2.2.0 adds 5 rules, all off by default. The 2.2.0 detekt2 jar registers `META-INF/services/dev.detekt.api.RuleSetProvider` (checked). | github.com/appKODE/detekt-rules-compose/releases; Maven Central `ru/kode/detekt-rules-compose-detekt2/2.2.0` |
| Compose BOM 2026.09.00 | Maps ui/foundation/runtime/animation 1.12.0→1.12.1. material3 stays 1.4.0, icons 1.7.8. 1.12.1 notes list no itemised fixes (commit range only); no focus/D-pad entry. | dl.google.com `compose-bom-2026.0{8,9}.00.pom`; developer.android.com/jetpack/androidx/releases/compose-ui, compose-foundation |
| Coil 3.6.0–3.6.3 | **3.6.3 fixes AGP 9.4.0 + R8 builds failing on invalid Kotlin module-name characters**, which matters with release minify on. 3.6.0 adds `allowPartialImage` (default on) and fixes `AsyncImage` inverted constraints and `GenericViewTarget` under R8 9. 3.6.2 fixes `DeDupeConcurrentRequestStrategy` hanging requests. No `coil-network-ktor3` entry; that module still requires only `ktor-client-core ≥ 3.1.0`. | github.com/coil-kt/coil/blob/main/CHANGELOG.md; Maven Central `coil-network-ktor3-android-3.6.3.module` |
| Ktor 3.6.0/3.6.1 | OkHttp engine: fewer dispatches, no body close on the main thread, duplex connection release. Bearer: "Authorization header is no longer dropped from refresh-token requests" (KTOR-9827; exact trigger unverified, ticket page unreadable). ContentNegotiation adds an opt-in to leave Accept/Content-Type alone. 3.6.1 fixes client connection leaks and `HttpStatement.execute` hangs. `ktor-client-okhttp:3.6.1` depends on okhttp 5.5.1 and coroutines 1.11.0. `ktor-serialization-kotlinx-json` declares serialization 1.11.0 (the app's direct 1.13.0 wins). | github.com/ktorio/ktor/releases; Maven Central `ktor-client-okhttp-jvm-3.6.1.module` |
| OkHttp 5.5.0/5.5.1 | 5.5.0 adds opt-in ECH and a richer DNS API (`DnsOverHttps` HTTPS records, opt-in). The default hostname verifier rejects hosts failing IP canonicalisation. A throwing proxy selector falls back to no proxy. 5.5.1 blocks 8 compromised eMudhra intermediates. | github.com/square/okhttp/blob/master/CHANGELOG.md |
| kotlinx-serialization 1.12.0/1.13.0 | Both require Kotlin 2.4.10. 1.12.0 wrapped serializer exceptions in `JsonException` (breaking); **1.13.0 (2026-11-11) reverts that**. 1.12.0 adds a JSON nesting-depth limit; whether 1.13.0 keeps it is unverified. `JsonConfiguration` setters hidden. | github.com/Kotlin/kotlinx.serialization/releases |
| Room3 3.0.2/3.0.3, sqlite 2.7.1 | Fixes: `@Transaction` wrapper deadlock and `IllegalMonitorStateException`; cancel-during-transaction state on suspending drivers. Suspending queries after `close()` now throw `IllegalStateException` (app never closes the DB in `main`). No schema/KSP option changes. | developer.android.com/jetpack/androidx/releases/room3, /sqlite |
| Media3 1.11.1 | HLS: resume offset for a not-yet-listed snap segment (#3322); a consumed `HlsMediaChunk` no longer retries on `EOFException` (#3350). ExoPlayer: `setExperimentalEnableHagcPlayback` removed (not used here). No UI/OkHttp-datasource/text entries. | developer.android.com/jetpack/androidx/releases/media3 |
| appcompat 1.8.0 | minSdk 23 (app is 24), depends on Activity 1.8.0, JSpecify nullness, config changes dispatched to the view tree. | developer.android.com/jetpack/androidx/releases/appcompat |
| core 1.19.1 | Raises `lifecycle-runtime` to 2.10.0 so bundled lint checks run on AGP 9.5.0-alpha04+ (app already has lifecycle 2.11.0). | developer.android.com/jetpack/androidx/releases/core |
| benchmark / baselineprofile 1.5.0 | Stable 2026-09-09. Since alpha05: `TraceProcessor.runServer` moved package and `requireAot` defaults to true (neither used here). Pulls uiautomator 2.4.0 (alpha05 already pulled 2.4.0-beta02, above the declared 2.3.0). | developer.android.com/jetpack/androidx/releases/benchmark; dl.google.com `benchmark-macro-1.5.0.pom` |
| uiautomator 2.4.1 | Fixes a `ShellServer` port collision; 2.4.0 adds a new API surface with the old one kept. | developer.android.com/jetpack/androidx/releases/test-uiautomator |
| mockk 1.14.12 | Includes a relaxed-mock exception fix (#1544, value-class matching), `Map` restricted by default (#1577), restriction checks on every mock path (#1579), and "suspend resumption no longer recorded as a separate call" (#1580). These may change verification counts in coroutine tests. | github.com/mockk/mockk/releases |
| kover 0.9.11 | 0.9.10 concurrency fix; 0.9.11 reverts the 0.9.10 "skip test tasks without binary report". No filter changes. | github.com/Kotlin/kotlinx-kover/releases |

## 6. Options

| # | Option | Trade-offs | Size |
|---|---|---|---|
| A **(recommended)** | One run for every version bump, AGP 9.5.1 + Gradle 9.9.1 included, with two deviations from the scan: **KSP 2.3.12** instead of 2.3.13, and coil 3.6.3 landing in the same commit as AGP. The detekt compose-rules line moves to a follow-up run that swaps to `detekt-rules-compose-detekt2:2.2.0` and clears its findings. | Pure build change: no source edits expected, one verification pass (unit, detekt, lint, release R8 build, device smoke). Keeps a Compose-code clean-up out of a dependency diff. The compose rules stay dead a little longer. | M (toolchain + network, playback and DB libraries; release R8) |
| B | Same as A, plus the compose-rules swap in this run, fixing or suppressing what the nine rules report. | Closes the silent gap now, but the finding count is unknown (rules last ran on 2026-08-14; 236 commits to `app/src/main` since). It can pull Compose UI edits and a ux review into a dependency run. | M, can grow to L |
| C | Two runs: libraries first (KSP 2.3.12, no AGP, coil ≤ AGP need), then AGP 9.5.1 + Gradle 9.9.1 + coil 3.6.3. | Smaller blast radius per run, but two device passes. Gradle 9.9.1 carries security fixes and coil 3.6.3 is needed only with AGP ≥ 9.4, so the split buys little. | S + S/M |

## 7. Risks

| Risk | Where | Mitigation |
|---|---|---|
| KSP 2.3.13 silently drops generated code (Room `_Impl` missing gives a runtime crash, not a compile error) | upstream #3286 | Use 2.3.12; revisit when a fixed release ships. |
| AGP ≥ 9.4 release build fails with coil < 3.6.3 | coil 3.6.3 changelog; `app/build.gradle.kts:183` | Same commit; verify `assembleProdRelease` (or the release-like variant) with R8. |
| Toolchain above KGP 2.4.21's tested range (Gradle 9.9.1, AGP 9.5.1) and detekt alpha.6's (AGP 9.3.1) | Kotlin compat table; detekt compat table | Precedent: the current setup is already above range and works. Treat new deprecation warnings as findings, and treat a broken `detektAll` (custom classpath at `app/build.gradle.kts:307-322`) as a blocker. |
| New bundled Lint checks in AGP 9.5 fail `lintDevDebug` (`abortOnError`, no baseline) | `app/build.gradle.kts:74-110` | Run `check.full`; fix rather than baseline (project rule). |
| Ktor bearer refresh behaviour change (KTOR-9827): the refresh POST may now carry the stale access token | `KinoPubApiClient.kt:150-200` | Unit `KinoPubApiClientAuthProbeTest`; on-device session-refresh check, or a mock test asserting the refresh request headers. |
| mockk 1.14.12 verification semantics (#1580 suspend calls, #1577 Map restriction) turn some unit tests red | 69 test files with relaxed mocks | Run the full unit suite; fix tests and keep production code as is. |
| Media3 HLS retry/resume change | playback | Device smoke: start, seek and resume an HLS title. |
| Detekt run unstable on this host (JVM crashes under swap) | audit report `:147-160` | `AGENT_GRADLE_SERIAL=1`; a crash is a host fault to retry, not a pass. |
| Overlap with `dependency-prune` (drops `coil-video`, `ktor-client-logging`, `security-crypto`) | catalog | Bump them anyway (same version refs); the prune run removes them later. |

Device verification is needed under AGENTS.md (networking, playback, persistence, build variants): login session alive, Home posters load (Coil), details/HLS playback start and seek, the DB-backed watch state persists across restart.

## 8. Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | KSP: take 2.3.13 as scanned, or 2.3.12? | 2.3.12 (2.3.13 is flagged defective upstream, fix not released). |
| Q2 | detekt-rules-compose: swap to `detekt-rules-compose-detekt2:2.2.0` in this run (option B), or in a follow-up run? | Follow-up run (`/task:new`), with the catalog line left at 1.4.0 here. Bumping the detekt-1 coordinate to 2.2.0 would change nothing, because it still does not load. |
| Q3 | AGP 9.5.1 + Gradle 9.9.1 in this run or a separate one? | This run (option A): Gradle 9.9.1 carries security fixes, and coil 3.6.3 / AGP 9.5 belong together. |

## 9. Sources

- Files: `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `baselineprofile/build.gradle.kts`, `buildSrc/` (Versions.kt, build files), `config/detekt/detekt.yml`, `config/detekt/detekt-baseline.xml`, `app/build/reports/detekt/devDebug*.sarif`, `PuberApp.kt`, `data/di/modules.kt`, `data/api/{KinoPubApiClient,TmdbApiClient,TheIntroDbApiClient,IntroDbAppApiClient}.kt`, `data/db/*`, `tools/agentw`, `.github/workflows/pr-checks.yml`, cached jar `ru.kode/detekt-rules-compose/1.4.0`.
- Commits: `03c2ca31`, `d051c873`, `ecfcd6b8`, `a957a57f`, `9930b952`, `69a95b2e`.
- Runs: `dependency-prune`, `coil-shared-http`, `code-quality-audit`, `host-android-sdk`, `upstream-sync-triage`; `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md`.
- URLs:
  - https://developer.android.com/build/releases/gradle-plugin
  - https://developer.android.com/build/releases/agp-9-4-0-release-notes
  - https://developer.android.com/build/releases/agp-9-5-0-release-notes
  - https://docs.gradle.org/9.9.1/release-notes.html
  - https://docs.gradle.org/9.9.1/userguide/upgrading_version_9.html
  - https://kotlinlang.org/docs/gradle-configure-project.html
  - https://github.com/JetBrains/kotlin/releases
  - https://github.com/google/ksp/releases
  - https://github.com/google/ksp/issues/3286
  - https://slack-chats.kotlinlang.org/t/32596901/hi-all-https-github-com-google-ksp-releases-tag-2-3-0-ksp-ve
  - https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml
  - https://detekt.dev/docs/introduction/compatibility
  - https://github.com/appKODE/detekt-rules-compose/releases
  - https://repo1.maven.org/maven2/ru/kode/detekt-rules-compose-detekt2/2.2.0/
  - https://dl.google.com/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom
  - https://developer.android.com/jetpack/androidx/releases/compose-ui
  - https://developer.android.com/jetpack/androidx/releases/compose-foundation
  - https://github.com/coil-kt/coil/blob/main/CHANGELOG.md
  - https://github.com/ktorio/ktor/releases
  - https://repo1.maven.org/maven2/io/ktor/ktor-client-okhttp-jvm/3.6.1/
  - https://github.com/square/okhttp/blob/master/CHANGELOG.md
  - https://github.com/Kotlin/kotlinx.serialization/releases
  - https://developer.android.com/jetpack/androidx/releases/room3
  - https://developer.android.com/jetpack/androidx/releases/sqlite
  - https://developer.android.com/jetpack/androidx/releases/media3
  - https://developer.android.com/jetpack/androidx/releases/appcompat
  - https://developer.android.com/jetpack/androidx/releases/core
  - https://developer.android.com/jetpack/androidx/releases/benchmark
  - https://developer.android.com/jetpack/androidx/releases/test-uiautomator
  - https://github.com/mockk/mockk/releases
  - https://github.com/Kotlin/kotlinx-kover/releases
  - https://dl.google.com/android/maven2/com/android/tools/build/gradle/9.5.1/gradle-9.5.1.pom
