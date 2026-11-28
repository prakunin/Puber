# Research: gradle-build-cache

## Request

gradle.properties has no org.gradle.caching. Worktrees share GRADLE_USER_HOME=~/.gradle-agents (tools/agentw), so enabling the local build cache lets worktrees reuse task outputs; now each new worktree compiles from scratch. Scope: enable org.gradle.caching=true and confirm cacheability does not break builds (kapt/ksp, baseline profile, lint). CI cache (setup-gradle push trigger) is out of scope here unless trivial; check overlap with run ci-workflow-hardening.

## Understanding

Every Orca worktree builds with `GRADLE_USER_HOME=~/.gradle-agents`, but the build cache is off, so a fresh worktree recompiles every task even when another worktree already built identical inputs. Turning on the local build cache stores task outputs in `~/.gradle-agents/caches/build-cache-1` and lets the next worktree load them. The work is one property plus proof that no cacheable task returns a stale or wrong result: KSP/Room, lint, detekt, unit tests and the baseline-profile flow.

## Current state in code

- `gradle.properties:1-7`: jvmargs, AndroidX, nonTransitiveRClass, three `puber.*` ports; no `org.gradle.caching`, no configuration cache.
- `~/.gradle/gradle.properties` and `~/.gradle-agents/gradle.properties`: only `org.gradle.daemon.idletimeout` and `kotlin.daemon.jvmargs`; no init scripts in either home; no `caches/build-cache-1` exists yet.
- `tools/agentw:44-46`: worktrees export `GRADLE_USER_HOME=${AGENT_GRADLE_HOME:-$HOME/.gradle-agents}`; `:48-57` optional flock serialisation; main checkout keeps `~/.gradle` (`:59-61`). Main and worktrees therefore never share a cache.
- Versions: Gradle 9.9.1 (`gradle/wrapper/gradle-wrapper.properties`), AGP 9.5.1, Kotlin 2.4.21, KSP 2.3.12, Room 3.0.3, detekt 2.0.0-alpha.6, kover 0.9.11, baselineprofile 1.5.0 (`gradle/libs.versions.toml:2-36`).
- Annotation processing: KSP only, Room compiler (`app/build.gradle.kts:11,423`); no kapt anywhere in the build files. Room schemas go through the Room Gradle plugin `room3 { schemaDirectory(...) }` (`app/build.gradle.kts:16,254-257`), not a `room.schemaLocation` KSP arg.
- BuildConfig inputs differ per checkout: `CLIENT_SECRET`, `TMDB_READ_ACCESS_TOKEN`, `API_DOMAIN_OVERRIDE` come from `.env` / `local.properties` / env (`app/build.gradle.kts:28-41,58-61`). Worktrees have none, so all worktrees share the same (empty) values and can hit each other's entries; main differs.
- Prod `versionCode` is wall-clock based (`buildSrc/src/main/kotlin/Versions.kt:23-46`, used at `app/build.gradle.kts:233`): prod variant tasks downstream of the manifest change every minute and miss. Dev/instrumentation use the constant `DebugVersionCode` (`Versions.kt:22`).
- Release signing writes `app/release.jks` at configuration time from `RELEASE_KEYSTORE_BASE64` (`app/build.gradle.kts:156-160`); a configuration side effect, not a task output.
- Lint: `abortOnError = true`, no baseline, `AndroidGradlePluginVersion` among disabled checks (`app/build.gradle.kts:74-111`).
- Detekt: variant tasks get their classpath rebuilt from the Kotlin compile's `libraries`, boot classpath and javac output (`app/build.gradle.kts:325-340`); `detektAll` aggregates (`:356-360`).
- Baseline profile: `saveInSrc = true`, `automaticGenerationDuringBuild = false`, `mergeIntoMain = true` (`app/build.gradle.kts:453-466`); generation runs on a connected device (`baselineprofile/build.gradle.kts:37-39`, `tools/generate-baseline-profile.sh`).
- `buildSrc/src/main/kotlin/com/osome/utils/GitHookInit.kt`: `@CacheableTask` writing into `.git/hooks`; not registered by any build script (grep of `*.kts` finds no use).
- Unit tests that read project files Gradle does not track as Test inputs, so a cached or up-to-date Test result survives edits to them:

| Test | File read |
|---|---|
| `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt:12-17` | `src/main/generated/baselineProfiles/{baseline,startup}-prof.txt` |
| `app/src/test/kotlin/com/kino/puber/core/telemetry/ProguardRulesTest.kt:10` | `proguard-rules.pro` |
| `app/src/test/kotlin/com/kino/puber/core/telemetry/UpdateFilePathsXmlTest.kt:11-12` | `src/main/<res xml>` |
| `app/src/test/kotlin/com/kino/puber/ui/feature/history/component/HistoryContextMenuTest.kt:105-112` | `src/main/res/values/strings.xml` |
| `app/src/test/kotlin/com/kino/puber/data/repository/EpisodeSchedulePrivacyTest.kt:26-32`, `SkipSegmentPrivacyTest.kt:117` | `src/main/java/...` sources (usually also change the compiled classpath) |

- `tasks.withType<Test>` blocks (`app/build.gradle.kts:468-471`, `player-test-fixtures/build.gradle.kts:31-33`) declare no extra inputs.
- `.flow/config.yaml` `check.fast` / `check.full` run through `./tools/agentw`; nothing in `Makefile`, `tools/` or `.claude/` passes `--build-cache`, `--no-build-cache` or `--rerun-tasks`.
- CI (`.github/workflows/pr-checks.yml`, `release.yml`): `gradle/actions/setup-gradle@v5.0.2` with defaults; PR checks run only on `pull_request`, release on tags and `workflow_dispatch`. No workflow runs on a push to `master`.

## Related work

| Run / doc | Relation |
|---|---|
| `ci-workflow-hardening` (done, merged eb505d6d) | Pinned setup-gradle by SHA, narrowed permissions; did not touch Gradle caching or triggers. No overlap with this property; a CI cache write path would be a follow-up to it. |
| `unit-suite-flaky` (idea) | Cached Test results would mask flaky tests on unchanged inputs; sequencing matters only for diagnosing that run. |
| `perf-deferred`, `cache-layer-rework` | Runtime caches in the app; same word, unrelated. |
| `hermetic-baseline-profiles` (idea) and specs `docs/superpowers/specs/2026-11-26-hermetic-baseline-generation.md` | Baseline generation flow this run must not break; no caching content. |

Git: `git log -S"org.gradle.caching" --all` returns nothing; the property was never set. Duplicate: none.

## External findings

| Finding | Source |
|---|---|
| Build cache is off by default; `org.gradle.caching=true` in `gradle.properties` enables it for all builds, `--no-build-cache` disables per run. Local cache lives in Gradle User Home. | https://docs.gradle.org/current/userguide/build_cache.html (Gradle 9.9.3 docs) |
| Unused local entries are removed after 7 days by default; retention changes via an init script in `<GRADLE_USER_HOME>/init.d` (`buildCache.setRemoveUnusedEntriesAfterDays`). | https://docs.gradle.org/current/userguide/directory_layout.html (9.9.3) |
| Inputs without declared path sensitivity default to `ABSOLUTE` and are non-relocatable; cache misses come from unstable inputs such as timestamps. Different worktree paths therefore only hit for relocatable tasks. | https://docs.gradle.org/current/userguide/build_cache_concepts.html (9.9.3) |
| `AndroidUnitTest` tasks are cacheable; instrumented test tasks are not. Page is dated (cites AGP 3.3). | https://docs.gradle.org/current/userguide/caching_android_projects.html (9.9.3) |
| `AndroidLintAnalysisTask` is cacheable since AGP 7.1.0 with `org.gradle.caching=true`. | https://developer.android.com/build/releases/agp-7-1-0-release-notes |
| A third-party project on AGP 8.13.2 saw cached lint partial results carry paths of another checkout sharing the same local cache; it failed their own outside-repo guard. No upstream AGP issue linked; behaviour on AGP 9.5.1 unverified. | https://github.com/Apdelrahman1911/parlor/issues/271 (closed 2026-10-28) |
| Room Gradle plugin makes schema outputs cacheable for javac/KAPT/KSP, replacing `room.schemaLocation`. Documented for `androidx.room`; same for `androidx.room3` unverified. | https://developer.android.com/jetpack/androidx/releases/room |
| Kotlin compile tasks use the build cache; `-Dkotlin.caching.enabled=false` opts them out. | https://kotlinlang.org/docs/gradle-compilation-and-caches.html |
| KSP docs cover incremental processing only, nothing on the build cache. | https://kotlinlang.org/docs/ksp-incremental.html |
| android-cache-fix plugin supports AGP 7.0+; its `CompileLibraryResourcesTask` absolute-path issue is fixed in AGP 9.2.0+ (Puber is on 9.5.1); Room misses: use the Room plugin. | https://github.com/gradle/android-cache-fix-gradle-plugin |
| setup-gradle caches `caches/build-cache-1` with the rest of Gradle User Home; by default only default-branch jobs write, other branches, PRs and tags restore read-only. | https://github.com/gradle/actions/blob/main/docs/setup-gradle.md |

## Options

| Option | Change | Trade-offs | Size |
|---|---|---|---|
| A (recommended) | `org.gradle.caching=true` in `gradle.properties`; declare the files the tests in the table above read as Test inputs with `PathSensitivity.RELATIVE` in `app/build.gradle.kts:468`; verify with two worktree builds (second shows `from cache` in the `actionable tasks` summary) and a green `check.full`. | Main, worktrees and CI all get the cache; tests stop passing on stale inputs, which also fixes the existing up-to-date gap. Two files. | S |
| B | Enable only for worktrees: `tools/agentw` adds `--build-cache` (or `-Dorg.gradle.caching=true`) in the worktree branch. | Main checkout and CI unchanged; the stale-test-input hazard stays unless also fixed; caching then depends on the wrapper, not the project. | S |
| C | A plus CI: add a `push: branches: [master]` trigger (or a dedicated cache-seeding job) so setup-gradle writes caches PRs can read. | Real CI speed-up; more Actions minutes and cache storage; touches the workflows `ci-workflow-hardening` just hardened. | M |

## Risks

- Stale test verdicts: the tests in the Current state table pass from cache after edits to the files they read (proguard rules, `strings.xml` text, baseline profiles), in any worktree. Option A closes it; without it a regression can ship green.
- Lint cross-checkout paths: per the parlor issue, a lint result built in worktree X may report paths of X when loaded in Y. Puber has no outside-repo guard, so at worst report paths look wrong; unverified on AGP 9.5.1.
- Secrets in the main cache: with the property in `gradle.properties`, main checkout outputs that embed `CLIENT_SECRET` / TMDB token (BuildConfig and compiled classes) are copied into `~/.gradle/caches/build-cache-1`. Local disk only, same exposure as `app/build`. CI release runs on tags, which setup-gradle restores read-only, so no Actions cache gets them.
- Disk: `~/.gradle-agents` grows with entries for every worktree; bounded by the 7-day cleanup.
- Concurrent worktree builds writing the same cache: Gradle's locking for this is not described in the fetched docs (unverified); `AGENT_GRADLE_SERIAL=1` already exists.
- Detekt 2.0.0-alpha.6 task cacheability and relocatability with the overridden classpath: unverified.
- Baseline profile: the device generation task is an instrumented test (not cacheable); the saveInSrc copy writes into `src/`. A cache hit cannot replace a device run, but `BaselineProfileContentTest` can stay green on an old profile (first risk).

## Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Enable in `gradle.properties` (main, worktrees, CI) or only through `tools/agentw`? | `gradle.properties`: one place, also helps main and CI; Option A. |
| Q2 | Declare the undeclared Test file inputs in this run? | Yes; it is the only correctness hazard the cache widens. |
| Q3 | Add a CI cache write path (push trigger on `master`)? | No; separate run, it changes workflow triggers and Actions usage. |
| Q4 | Change the default 7-day retention of `~/.gradle-agents`? | No; default is enough for the worktree cadence. |

## Sources

- Files: `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `baselineprofile/build.gradle.kts`, `player-test-fixtures/build.gradle.kts`, `buildSrc/`, `tools/agentw`, `tools/generate-baseline-profile.sh`, `Makefile`, `.flow/config.yaml`, `.github/workflows/pr-checks.yml`, `.github/workflows/release.yml`, `~/.gradle/gradle.properties`, `~/.gradle-agents/gradle.properties`, tests listed in Current state.
- Commits: `git log -S"org.gradle.caching" --all` (none); `git log -- gradle.properties tools/agentw` (7127d530, 7d5cf63f, 6cd733f4, 71aed2b9).
- Runs: `ci-workflow-hardening`, `unit-suite-flaky`, `perf-deferred`, `cache-layer-rework`, `hermetic-baseline-profiles`, `dependency-prune`, `r8-keep-narrowing`; grep of `.flow/runs` and `docs/` for build-cache terms.
- URLs: listed in External findings (fetched 2026-11-28).
