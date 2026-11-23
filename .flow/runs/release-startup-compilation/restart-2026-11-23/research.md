# Research: release-startup-compilation

## 1. Request

"Refresh the baseline profile, narrow R8 rules and compile after in-app updates." Audit items 7 and 8 from the static perf audit handed to run startup-splash-preload: the baseline profile is stale since 1.0.0 (no details/player rules, new startup classes missing); broad R8 -keep rules, unused coil-video and AppCompat dependencies; after an in-app update the app runs JIT until background dexopt. Related runs/ideas: perf-deferred P5, startup-benchmark-entry.

## 2. Understanding

Release builds ship a baseline profile recorded at 1.0.0 against a UI that no longer exists, and the generator that would refresh it drives the old top-tab layout and cannot run under the repo's device rules. Even a fresh profile does not help users right after an update: the app updates itself through the system installer with only the APK, so ART compiles nothing at install and the app runs interpreted and JIT until idle background dexopt. On the owner's TV box this is the difference between a first key of about 3.6 s and 50 ms or less (startup-first-input-on-tv). Separately, the R8 config keeps whole libraries and every app constructor, and three or four declared dependencies are unused. These are three goals that can ship separately.

## 3. Current state in code

Baseline profile and generator

| Fact | Where |
|------|-------|
| Profiles last written by `88cdf071` "Release 1.0.0" (2026-06-28), 634 commits before `e4c3b5aa` | `app/src/main/generated/baselineProfiles/baseline-prof.txt`, `startup-prof.txt` |
| App coverage: about 114 top-level `com.kino.puber` classes (root, auth, main/toptabs, home, data/api, navigation). Nothing from `ui/feature/details`, `player`, `search`, `showall` and the rest, and none of the new startup classes (`StartupSplashHost`, reveal and key filter). Several entries name the removed `main/toptabs` package | `baseline-prof.txt` (grep of `Lcom/kino/puber/`) |
| Generator CUJs assume "TopTabs: focus starts on TabRow" and tab order Home→Movies→Series→Collections. Navigation became a single side menu in `1674417d` (2026-08-25); `TabType` is now Home, Search, Favourites, ... | `baselineprofile/.../BaselineProfileGenerator.kt:44-121`, `domain/model/TabType.kt:16-24` |
| Generator sends D-pad keys right after `startActivityAndWait()` + `waitForIdle()`. Since startup-splash-preload, D-pad keys are dropped while the splash covers the tree, so the first keys may be lost. The app never calls `reportFullyDrawn`/`ReportDrawn*` | `BaselineProfileGenerator.kt:39-46`; grep for `ReportDrawn` in `app/src/main` is empty |
| Plugin config: `useConnectedDevices = true` (every connected device), `saveInSrc`, `mergeIntoMain`, `automaticGenerationDuringBuild = false` | `baselineprofile/build.gradle.kts:36-38`, `app/build.gradle.kts:439-446` |
| Generation script: `./gradlew :app:installDevNonMinifiedRelease` (implicit install), bare `adb shell` with no `-s`, then `./gradlew :app:generateBaselineProfile`; tokens cached in `.todo/baseline-profile-auth.env` and passed as instrumentation args | `tools/generate-baseline-profile.sh:52,101,103-104,115-134` |
| `make itest` already has the compliant pattern: private adb server with one serial, `leaveApksInstalledAfterRun=true` | `Makefile:128-136` |
| `StartupBenchmarks` documents only `./gradlew :baselineprofile:connected...AndroidTest`, which the hook refuses | `baselineprofile/.../StartupBenchmarks.kt:19-20` |
| Release doc describes the refresh but not as a step of the release procedure. It asks for approval before regenerating | `docs/release.md:15-41` (procedure), `:94-140` (refresh) |
| `profileinstaller` 1.4.1 and `baselineProfile(project(":baselineprofile"))` are wired. `benchmarkPlugin`/macro version 1.5.0, AGP 9.5.1 | `app/build.gradle.kts:435-436`, `gradle/libs.versions.toml:2,28-29` |
| Build types: `benchmarkRelease` and `nonMinifiedRelease` = `initWith(release)`, debug-signed; dev flavor shares `DebugVersionCode` across build types | `app/build.gradle.kts:54,212-219,224-230` |

In-app update path

| Fact | Where |
|------|-------|
| Install is `Intent.ACTION_VIEW` on a FileProvider URI to the system installer, with the APK only. There is no `PackageInstaller` session | `data/repository/AppUpdateInstaller.kt:32-53` |
| Update source: GitHub release, first `.apk` asset plus `<apk>.sha256` | `data/repository/AppUpdateRepository.kt:38-66` |
| Release workflow uploads only `puber-<tag>.apk` and `.sha256` | `.github/workflows/release.yml:99-124` |
| `minSdk 24`, `targetSdk 36`. Owner's box KM6 runs Android 10 (armeabi-v7a); the emulator `puber_tv_36` runs API 36 | `buildSrc/.../Versions.kt:6-7`; evidence `2026-11-22-startup-first-input-on-tv-*-device.md` |

R8 and dependencies (`app/proguard-rules.pro`)

| Rule | Line | Note |
|------|------|------|
| `-keep class io.ktor.** { *; }` + `okhttp` engine | 49-51 | whole library; engine is explicit `HttpClient(OkHttp)` everywhere (`KinoPubApiClient.kt:137`, `TmdbApiClient.kt:88`, `IntroDb*ApiClient.kt:31`) |
| `-keep class okhttp3.internal.** { *; }` | 62 | |
| `-keep class org.koin.** { *; }` | 70 | |
| `-keepclassmembers class com.kino.puber.** { public <init>(...); }` | 75-77 | every app constructor; Koin DSL uses constructor references, not reflection (unverified for every module) |
| `-keep class cafe.adriel.voyager.** { *; }` | 83 | |
| `-keep class * implements android.os.Parcelable { *; }` | 110 | whole classes, beyond `CREATOR` (107-109) |
| `-keep class androidx.media3.** { *; }` | 117 | |
| `-keep class coil3.** { *; }` | 124 | |
| `-keep class kotlin.Metadata { *; }` | 150 | |
| `-keep class androidx.security.crypto.** { *; }` | 163 | library unused (`CryptoPreferenceRepository.kt:3-17` uses raw KeyStore) |
| `-keepnames` for PuberScreen, PuberVM, UIAction, Tab, `ui.**State` | 90-101 | required: telemetry names and `PuberScreen.key` (telemetry-core AC-8) |

Unused dependencies: `coil-video` (`app/build.gradle.kts:374`, no `VideoFrameDecoder`), `security-crypto` (`:367`), `ktor-client-logging` (`:386`, no import). `appcompat` (`:353`) is used only as the parent of `Theme.Puber` (`res/values/themes.xml:3`); `MainActivity` is a `ComponentActivity` (`MainActivity.kt:36`).

## 4. Related work

| Run / doc | Relation |
|-----------|----------|
| startup-first-input-on-tv (done) | Key evidence. On the box, as installed (`speed-profile`, reason=install): first key `lat_ms` median 3654 to 4068 ms. After `cmd package compile -m speed-profile -f` (profile collected since install): 14 of 15 keys at 50 ms or less, first_content median 830 ms. Judge-2 caveat: API health also differed between the rounds. Handed the first-key target to this run (`.flow/runs/startup-first-input-on-tv/state.md`, `qa-report.md`, `judge-2.md:19,45`) |
| startup-splash-preload (done) | Source of the audit (`perf-audit-2026-11-21.md:12-13`). The owner split this work out (decision 2026-11-21) |
| dependency-prune (idea, S) | Overlaps: drop `ktor-client-logging`, `coil-video`, `security-crypto`, review `appcompat` (BP-4, `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md:121`). Needs a release build with R8 |
| perf-deferred (idea) P5 | "R8 keep rules and largeHeap, narrowed step by step with a release build and a device run after each step" (`docs/superpowers/plans/2026-09-08-memory-and-performance-fixes.md:1107`) |
| startup-benchmark-entry (idea) | The same blocker as profile generation: instrumented macrobenchmark needs a make target with a private adb server and kept APKs |
| dependency-update-stable (done) | Q4: profile deliberately not regenerated (state.md:43,48) |
| telemetry-core (done) | Introduced the `-keepnames` rules and the `devBenchmarkRelease` minified-check convention (state.md:27,34) |

Probable duplicate: none. dependency-prune covers part of goal 3 only.

## 5. External findings

| Finding | Source |
|---------|--------|
| Channels other than Play may not apply baseline profiles at install; "users ... don't see the benefits until background dexopt runs, which is likely overnight" | https://developer.android.com/topic/performance/baselineprofiles/overview (read 2026-11-23) |
| ProfileInstaller copies the profile to the `cur` directory, and `bg-dexopt` picks it up only "once the device is idle" | https://developer.android.com/topic/performance/baselineprofiles/manually-create-measure |
| Install-time compile for sideloads: APK and `.dm` (`primary.prof` + `primary.profm`) in one install session; the result shows `[status=speed-profile] [reason=install-dm]`. A `.dm` built by renaming `assets/dexopt/baseline.prof{,m}` works on Android 9 to 11 only. For API 31+, use `profgen extractProfile --output-dex-metadata --profile-format V0_1_5_S` | same page, sections "Manually measure", "Use install-multiple with profgen" |
| Profile formats: `v0_1_0_p` for API 28-30, `v0_1_5_s` for 31+. The binary profile must be under 1.5 MB | same page; overview page |
| The framework rejects a bad `.dm` with `INSTALL_FAILED_BAD_DEX_METADATA` | https://android.googlesource.com/platform/frameworks/base/+/3fc56c30000d9b88c2145d4297a97ec5061d60d1%5E!/ |
| Profile generation on a connected device requires root or API 33+ (Macrobenchmark 1.2.0-alpha06+) | https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile |
| Report fully drawn (`ReportDrawnWhen`) so asynchronously loaded content is included in the profile | same page, "Capture all required code paths" |
| profileinstaller 1.4.1 (2024-10-02) is the latest stable. `ProfileVerifier` (since 1.3.0) reports from inside the app whether the profile is compiled, enqueued or missing | https://developer.android.com/jetpack/androidx/releases/profileinstaller |
| Keep rules: do not use package-wide `-keep class pkg.** { *; }` long term; name members instead of `{ *; }`; rely on library consumer rules | https://developer.android.com/topic/performance/app-optimization/keep-rules-best-practices |
| Third-party TV/IPTV apps ship a `.dm` next to the APK in the release, verify its sha256, write `base.apk` + `base.dm` into a PackageInstaller session, and fall back to an APK-only install on failure. They use `.dm` on 9 to 11 and rely on ProfileInstaller on 12+. AGP output path `baselineProfiles/1` is unverified | https://github.com/victortrinh/better-iptv/pull/142, https://github.com/Sudashiii/OwnTV/pull/17 (anecdotal) |

## 6. Options

| Option | Content | Trade-offs | Size |
|--------|---------|------------|------|
| A. Profile only | New compliant generation target (`make DEVICE=<serial> baseline-profile`: private adb server, `leaveApksInstalledAfterRun`, no implicit install), CUJs rewritten for the side menu (home rows, details, player start/exit, search), `ReportDrawnWhen(revealed)` so generation waits out the splash, profiles regenerated on `puber_tv_36`, step added to the release procedure | Small and safe. Users still run JIT after every in-app update until idle dexopt, so the box's first key stays at about 3.6 s after an update | M |
| **B. Profile + install-time compile (recommended)** | A, plus: the update installer switches from `ACTION_VIEW` to a `PackageInstaller` session writing `base.apk` and, when present and its sha256 matches, `base.dm` (API 28-30 from the APK's own profile; API 31+ only if a `v0_1_5_s` `.dm` is produced in the build); APK-only fallback on any `.dm` failure; the release workflow publishes `.dm` + `.sha256`; `ProfileVerifier` status in the telemetry `startup` line. R8 and dependency narrowing move to a separate run that absorbs dependency-prune and perf-deferred P5 | Targets the measured 3.6 s → 50 ms gap on the owner's box. Touches the update flow (user confirmation via `STATUS_PENDING_USER_ACTION`, update-prompt focus runs), the release workflow and the update metadata. More than one shippable goal | L (split: A then the install path) |
| C. Everything in one run | B plus R8 narrowing and dependency removal | One release-build device pass covers both. But R8 regressions show only at runtime (serialization, Parcelable, telemetry names) and would block the startup gain; largest review surface | L |

Recommended: B as two sequential runs. This run keeps the profile refresh and generation entry; a follow-up run takes the session install with `.dm` and `ProfileVerifier`. A third run takes R8, coil-video, AppCompat and the dependency-prune items. The `.dm` only carries the baseline profile, so the profile must be fresh first.

## 7. Risks

| Risk | Note |
|------|------|
| Generation touches every device | `useConnectedDevices` + `generateBaselineProfile` runs instrumented tests on all connected devices, including the owner's box, and by default AGP uninstalls the tested APK after the run. That uninstall drops the KinoPub pairing and its KeyStore key. Use a private adb server and kept APKs, as `make itest` does |
| Box cannot generate | KM6 is Android 10 and not rooted (unverified), so generation must run on `puber_tv_36` (API 36). Profiles are dex-level and do not depend on the ABI |
| Credentials | Tokens travel as instrumentation args on the Gradle command line. A worktree build has no `CLIENT_SECRET`, so a token refresh fails mid-run. Keep tokens out of logs and evidence |
| Stale CUJs give a silently poor profile | Keys dropped under the splash and navigation that no longer exists still produce a profile file. Assert the screens reached, for example by checking that the profile has details and player classes |
| `.dm` mismatch | A wrong format or a dex mismatch fails the session (`INSTALL_FAILED_BAD_DEX_METADATA`). An APK-only retry is mandatory |
| R8 narrowing | Failures appear only at runtime in a minified build: kotlinx.serialization, Parcelize/Voyager restore, Koin, telemetry names, Media3 renderers. Each step needs a `devBenchmarkRelease` device pass (P5 method) |
| Measuring the gain | The box comparison in startup-first-input-on-tv is confounded by API health. A same-day as-installed versus `install-dm` pair is needed. Exercising the in-app update path on the box needs a newer-version update source or a debug hook |

## 8. Open questions

| ID | Question | Recommended answer |
|----|----------|--------------------|
| Q1 | Scope: keep all three goals here, or split? | Split. This run covers profile refresh and the compliant generation target. New idea runs: install-time compile (session + `.dm` + `ProfileVerifier`) and R8 narrowing (absorbs dependency-prune and perf-deferred P5) |
| Q2 | Authorise one profile generation run on `emulator-5554` (`puber_tv_36`) under a lease, with the app kept installed and the cached tokens from `.todo/baseline-profile-auth.env`? | Yes, emulator only, never the box. `docs/release.md:96-98` requires asking first |
| Q3 | Which CUJs go into the profile? | Startup to reveal (startup profile); Home vertical and horizontal scroll; open details and go Back; start playback and exit the player; side menu to Search and Favourites |
| Q4 | Make the profile refresh a mandatory step of the release procedure? | Yes, as a checklist item that may be skipped only with a recorded reason. Generation needs a device and the owner, so it cannot be a CI gate |
| Q5 | Fold startup-benchmark-entry into this run, since the generation target and the benchmark target share the harness? | Yes. One make target family covers `generateBaselineProfile` and `StartupBenchmarks`; close that idea as absorbed |

## 9. Sources

- Files: `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/generated/baselineProfiles/*`, `baselineprofile/build.gradle.kts`, `baselineprofile/src/main/java/com/kino/puber/baselineprofile/{BaselineProfileGenerator,StartupBenchmarks}.kt`, `tools/generate-baseline-profile.sh`, `Makefile`, `docs/release.md`, `gradle/libs.versions.toml`, `buildSrc/src/main/kotlin/Versions.kt`, `app/src/main/java/com/kino/puber/data/repository/{AppUpdateInstaller,AppUpdateRepository,CryptoPreferenceRepository}.kt`, `app/src/main/java/com/kino/puber/MainActivity.kt`, `app/src/main/res/values/themes.xml`, `.github/workflows/release.yml`, `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md`, `docs/superpowers/plans/2026-09-08-memory-and-performance-fixes.md`, `docs/superpowers/evidence/2026-11-2[23]-startup-first-input-on-tv-*-device.md`
- Commits: `88cdf071` (Release 1.0.0, last profile), `03c2ca31` (1.7.1, generator module build only), `1674417d` (side-menu navigation, toptabs removed), `a8b74f38` (telemetry keep rules)
- Runs: startup-splash-preload, startup-first-input-on-tv, dependency-prune, perf-deferred, startup-benchmark-entry, dependency-update-stable, telemetry-core
- URLs: listed in section 5
