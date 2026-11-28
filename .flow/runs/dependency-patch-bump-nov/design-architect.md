# Design (architect): dependency-patch-bump-nov

Base b1b3c51a. `git diff --stat a546f5a5 b1b3c51a -- gradle/ gradlew gradlew.bat buildSrc app/build.gradle.kts` is empty, so the brief's ACs read against a546f5a5 hold unchanged on this base. Versions and release facts: `research.md#5-external-findings`.

## Decisions

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

## Changes by layer

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

## Fallbacks

| Key | Fails how | Recommended fallback |
|---|---|---|
| `ksp` | Build error or `_Impl` set differs | Stay on 2.3.12 |
| `ktor` | `data.api` tests red or broken images on the box | Stay on 3.6.1 |
| `lifecycleRuntimeKtx` | AAR metadata or compile failure | Stay on 2.11.0 |
| `agp` | detekt classpath override (`app/build.gradle.kts:318-336`) or lint (`abortOnError`, `:75`) breaks with no source fix | 9.5.2, else stay on 9.5.1; keep wrapper 9.9.3 |
| wrapper | Fails with AGP 9.5.3 | 9.9.2 (same security fixes), else fall back on `agp` |

## Alternatives rejected

- One commit: a bad bump could not be reverted alone.
- One commit per key (five): wrapper and AGP are one toolchain risk; Ktor and Lifecycle each carry one fix and share one check.
- Toolchain first: a library failure would surface on an unproven toolchain.
- Adding `distributionSha256Sum`: answered no (state.md Q3).

## Risks

1. **Room `_Impl` dropped (lifecycle, persistence).** KSP 2.3.14 keeps 2.3.13's classloader cache and no-clear-between-rounds changes. `PuberDatabase.create` (`data/db/PuberDatabase.kt:36-49`) needs `PuberDatabase_Impl` at runtime; a missing `AutoMigration_{2_3,3_4,4_5}` (`:19-23`) with `fallbackToDestructiveMigration(dropAllTables = true)` (`:48`) silently wipes the local watch-state index on upgrade instead of crashing. Guards: D2(b), D4, AC-6.
2. **Ktor 3.6.2 incomplete-body failures (networking).** Posters through `coil-network-ktor3` on the plain image client and the streamed update download now fail where they read short bodies. Behaviour is safer, but a server or CDN that closes early would show as broken posters. Guards: D5, AC-7.
3. **AGP 9.5.3 undocumented contents (rollout, build variants).** Lives in the detekt classpath override `app/build.gradle.kts:318-336`, lint `:75`, release signing fallback `:171-178`, R8 release. Guards: AC-3, AC-5.
4. **Wrapper regeneration churn (rollout).** `gradlew.bat` CRLF and an unexpected jar change. Guards: D3, AC-2.
5. **Box session lost after the worktree install (auth).** Refresh with a master build and `make DEVICE=192.168.1.110:5555 auth-save` before install (memory: worktree-build-loses-box-session).
6. **Lifecycle 2.11.1 `minCompileSdk=37` (build).** Met by `Versions.kt:12`; `check.fast` runs AAR metadata checks.

## Open questions

1. The brief cites `gradle/libs.versions.toml:2,4,12,16` and `composeBom` at `:8`; on base the keys are at `:2,4,11,15` and `composeBom` at `:7` (research.md §3 has the same offset). Recommended: the analyst corrects the line numbers in the brief; the ACs name keys, so none changes. Blocks: no.
