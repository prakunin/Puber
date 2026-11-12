# Design (architect): dependency-update-stable

Base 4db9c0d4. Target versions and ACs: `brief.md#target-versions`, `brief.md#acceptance-criteria`. Version facts: `research.md#5-external-findings`.

## Decisions

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

## Changes by layer

The change touches no layer listed in the role addendum: API/repository, ViewModel, screen/DI, Compose and resources are all unchanged. Everything happens in build configuration.

| Commit | Files | Catalog keys and versions |
|---|---|---|
| C1 Persistence | `gradle/libs.versions.toml:4,33,34` | `ksp` 2.3.12, `room` 3.0.3, `sqlite` 2.7.1 |
| C2 Network | `gradle/libs.versions.toml:14,15,16` | `okhttp` 5.5.1, `ktor` 3.6.1, `kotlinxSerialization` 1.13.0 |
| C3 AndroidX runtime and test | `gradle/libs.versions.toml:5,6,7,21,27` | `coreKtx` 1.19.1, `appcompat` 1.8.0, `composeBom` 2026.09.00, `mockk` 1.14.12, `media3` 1.11.1 |
| C4 Build toolchain | `gradle/wrapper/gradle-wrapper.properties:3`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`, `gradle/libs.versions.toml:2,3,25,28,30,36` | wrapper 9.9.1, `agp` 9.5.1, `kotlin` 2.4.21, `coil` 3.6.3, `benchmarkMacroJunit4` 1.5.0, `uiautomator` 2.4.1, `kover` 0.9.11 |

Not edited: `detekt-compose-rules` (`gradle/libs.versions.toml:75`, Q2), `detekt` (`:13`), `buildSrc/src/main/kotlin/Versions.kt:12`.

Checks for each commit. `check.fast` (`./tools/agentw :app:compileDevDebugKotlin`) passes for every commit.

| Commit | Extra check before committing |
|---|---|
| C1 | The D6 `_Impl` listing for `devDebug`; `git status --porcelain app/schemas` is empty. |
| C2 | `./tools/agentw testDevDebugUnitTest --tests 'com.kino.puber.data.api.*'` passes, which includes `KinoPubApiClientAuthProbeTest` (AC-4). `grep -rn markAsRefreshTokenRequest app/src/main` prints nothing. |
| C3 | `./tools/agentw testDevDebugUnitTest` passes (mockk #1580, #1577). |
| C4 | Within the working tree, edit in this order and run `check.fast` after each step: wrapper; then AGP + coil + baselineprofile/benchmark + uiautomator; then Kotlin; then kover. Then the D8 diff, then `check.full`, then `./tools/agentw :app:assembleProdRelease` (AC-5), then the D6 listing for `devDebug` and `prodRelease`. |

## Fallbacks

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

## Alternatives rejected

- One commit for everything: a single bad bump could not be reverted on its own.
- One commit per catalog key (17): over the owner's 5-commit limit, and couplings like `ktor`→`okhttp` and `agp`→`coil` cannot be split.
- Toolchain first: a library failure would then show up on an unproven toolchain, and a toolchain fallback would block the libraries.
- Adding a test that asserts the refresh request's headers: the source diff shows Puber's path is unchanged, so the test would guard behaviour the bump does not touch.
- Adding `markAsRefreshTokenRequest()` to the refresh request (`KinoPubApiClient.kt:197`): it changes production auth, which no AC asks for.

## Risks

1. **Session loss on the device (network, auth).** No `.env` in the worktree means no fresh login is possible. The refresh request at `KinoPubApiClient.kt:197-200` is unmarked and goes to the OAuth host. The bearer is attached through `sendWithoutRequest` (`:150`, `:1187-1190`) on both versions. In Ktor 3.6.1, `BearerAuthProvider.addRequestHeaders` returns early only for `AuthCircuitBreaker` requests. In 3.5.2 it removed their `Authorization` header. That is KTOR-9827. Auth-related transport changes from OkHttp 5.5 also land here: the hostname verifier and DoH (`data/di/modules.kt:57-61`). Guards: D7, AC-7, and qa runs `make DEVICE=<serial> auth-save` before the install.
2. **KSP output dropped silently (lifecycle).** `PuberDatabase.create` looks up `_Impl` by reflection (`data/db/PuberDatabase.kt:31`), so a missing class crashes at runtime. A missing `PuberDatabase_AutoMigration_2_3_Impl` is worse: combined with `fallbackToDestructiveMigration` (`:38`), it would silently drop a v2 database instead of crashing. The D6 listing must show exactly: `CachedPayloadDao_Impl`, `PuberDatabase_AutoMigration_2_3_Impl`, `PuberDatabase_Impl`, `WatchStateDao_Impl`, `WatchStateSyncDao_Impl` (under `app/build/generated/ksp/<variant>/kotlin/com/kino/puber/data/db/`). AC-10 covers the runtime.
3. **AGP 9.5.1 breaks the static gate (rollout).** detekt 2.0.0-alpha.6 has not been tested on AGP 9.5. The task-wide classpath override at `app/build.gradle.kts:307-322` depends on `KotlinCompile.libraries` and `compile<Variant>JavaWithJavac`. New lint checks fail with `abortOnError` (`:75`). Both are fixed in source, without baselines (AC-3). A JVM crash in `detektAll` is retried.
4. **Release R8 build (rollout).** Release has `isMinifyEnabled` and `isShrinkResources` on (`app/build.gradle.kts:183-184`), and AGP ≥ 9.4 needs coil 3.6.3. That is why they share C4. AC-5.
5. **Room transaction semantics (concurrency).** The Room 3.0.2/3.0.3 transaction and cancellation fixes run through `withWriteTransaction` (`data/db/DatabaseTransaction.kt:22`), which `WatchStateRepository` uses. AC-10 covers it on the device.
6. **mockk 1.14.12 verification counts (correctness, tests only).** This affects the unit suite in C3. Fix the tests; production code stays as it is.
7. **kotlinx-serialization 1.13.0 decoding (data shape).** The client's `Json` uses `ignoreUnknownKeys` and `isLenient` (`KinoPubApiClient.kt:125-129`). Whether 1.13.0 keeps the 1.12 nesting-depth limit is unverified. The C2 `data.api` tests and AC-8 cover it.
8. **Compose 1.12.1 focus and Media3 1.11.1 HLS (performance, playback).** No focus-related entry is listed upstream. Covered by AC-8 (D-pad scroll) and AC-9 (seek and resume).
9. **Baseline profile staleness (performance).** The rules in `app/src/main/generated/baselineProfiles/` that target renamed library methods stop matching. The cost is slower startup, not breakage. `baselineProfile.warnings.maxAgpVersion = false` (`app/build.gradle.kts:439`) hides the AGP-version warning. See Open question 1.

## Open questions

1. Should the baseline profile be regenerated (`tools/generate-baseline-profile.sh`) for the new library versions in this run? Recommended: no, do it in the next release run. Blocks: no.
