# Brief: gradle-build-cache

Kind: improvement   Size: S   Tracks: none   Base: 6534e4e6
Research: `research.md` (findings, options, risks cited below by section).

## Goal

Turn on the local Gradle build cache so worktrees sharing `GRADLE_USER_HOME=~/.gradle-agents` (`tools/agentw:44`) reuse each other's task outputs, without any cacheable task returning a stale result. Option A of `research.md: Options`.

## Cause of the current cost

`gradle.properties:1-7` has no `org.gradle.caching`, so the cache is off (Gradle default) and every new worktree builds every task from scratch. Confirmed by reading the file; `git log -S"org.gradle.caching" --all` is empty (`research.md: Related work`).

## Correctness hazard the cache widens

Unit tests read project files that `tasks.withType<Test>` (`app/build.gradle.kts:468-471`) does not declare as inputs, so an edit to those files leaves the Test task UP-TO-DATE today and FROM-CACHE once caching is on, in any worktree. Confirmed from the code:

| Test | File read |
|---|---|
| `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt:13-14` | `src/main/generated/baselineProfiles/` |
| `app/src/test/kotlin/com/kino/puber/core/telemetry/ProguardRulesTest.kt:10` | `proguard-rules.pro` |
| `app/src/test/kotlin/com/kino/puber/core/telemetry/UpdateFilePathsXmlTest.kt:12,17` | `src/main/res/xml/update_file_paths.xml` |
| `app/src/test/kotlin/com/kino/puber/ui/feature/history/component/HistoryContextMenuTest.kt:105-109` | `src/main/res/values/strings.xml` |
| `app/src/test/kotlin/com/kino/puber/data/repository/EpisodeSchedulePrivacyTest.kt:26-30`, `SkipSegmentPrivacyTest.kt:117-120` | `src/main/java/**` source text |

Other `File(...)` uses in `app/src/test` (`UpdatePromptVMTest`, `AppUpdateInteractorTest`, `DeviceSettingsVMSessionLogTest`, `FakeTelemetryStore`) are fake paths or scratch outputs, not reads.

## Device verification

Not needed: no production source, resource or manifest changes; a cache hit replays the outputs Gradle recorded for identical inputs. Evidence is build output only.

## Acceptance criteria

- **AC-1** Given `org.gradle.caching=true` in `gradle.properties`, when one worktree runs `./tools/agentw :app:compileDevDebugKotlin`, then `./tools/agentw clean` and the same task again, then the second run reports `:app:compileDevDebugKotlin FROM-CACHE` (`--console=plain`) and a non-zero "from cache" count in the actionable-tasks summary.
- **AC-2** Given the cache warmed by worktree A, when a second worktree B on the same commit runs `./tools/agentw :app:compileDevDebugKotlin --console=plain`, then the summary shows tasks from cache and the outcome of `:app:compileDevDebugKotlin` and `:app:kspDevDebugKotlin` is recorded in the evidence; a miss on either names the differing input (`-Dorg.gradle.caching.debug=true`).
- **AC-3** Given the cache on, when `check.full` (`.flow/config.yaml`) runs twice in the worktree, the second time on a warm cache, then both runs pass.
- **AC-4** Given a warm cache with `testDevDebugUnitTest` passed, when the content of any one file in the hazard table changes (a comment line appended, then reverted), then the next `testDevDebugUnitTest` run executes (not UP-TO-DATE, not FROM-CACHE); checked for `proguard-rules.pro` and `strings.xml` at least.
- **AC-5** Given `lintDevDebug` run in worktree A and then loaded in worktree B, when B's lint report (`app/build/reports/lint-results-devDebug.*`) is searched for A's absolute path, then it is absent, or the finding is recorded as a risk follow-up and reported to the owner.
- **AC-6** Given the change, when `AGENTS.md` "Build And Worktrees" is read, then one bullet states the build cache is on and `--no-build-cache` bypasses it.

## Risks

| Risk | Handling |
|---|---|
| Lint partial results carrying another checkout's paths (`research.md: Risks`, parlor issue; unverified on AGP 9.5.1) | AC-5 observes it; if present, report paths are cosmetic (no outside-repo guard in Puber). |
| Cached Test results hide flakes on unchanged inputs (`unit-suite-flaky`) | Diagnosing that run uses `--rerun-tasks` or `--no-build-cache`. |
| Main checkout cache holds outputs embedding `CLIENT_SECRET` / TMDB token | Local disk under `~/.gradle`, same exposure as `app/build`; CI release runs on tags, read-only for setup-gradle. |
| Concurrent worktree writes to one cache; detekt alpha cacheability | `AGENT_GRADLE_SERIAL=1` exists; AC-3 covers detekt passing on a warm cache. |

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Enable in `gradle.properties` or only through `tools/agentw`? | `gradle.properties` (main, worktrees, CI). | no |
| Q2 | Add a CI cache write path (push trigger on `master`)? | No; a separate run after `ci-workflow-hardening`. | no |
| Q3 | Change the 7-day cache retention? | No. | no |

## Plan

1. `gradle.properties:7`: append `org.gradle.caching=true`.
2. `app/build.gradle.kts:468-471` (`tasks.withType<Test>`): declare the hazard-table files as inputs, e.g. `inputs.files(layout.projectDirectory.files("proguard-rules.pro", "src/main/res/values/strings.xml", "src/main/res/xml/update_file_paths.xml"), layout.projectDirectory.dir("src/main/generated/baselineProfiles"), layout.projectDirectory.dir("src/main/java")).withPropertyName("projectFilesReadByTests").withPathSensitivity(PathSensitivity.RELATIVE)`. `player-test-fixtures/build.gradle.kts:31-33` reads no project files and stays as is.
3. `AGENTS.md:65` "Build And Worktrees": one bullet for AC-6.
4. Verify AC-1..AC-5 in the worktree with `./tools/agentw` and `--console=plain`; keep the outcome lines (not full logs) in `docs/superpowers/` evidence for this run.

## Implementation record

Commit: 47efa4dc Enable the Gradle build cache and declare the project files unit tests read as test inputs.

Red run (caching on, inputs not yet declared):
- red: AC4-edit-proguard - after appending a line to `app/proguard-rules.pro`, `:app:testDevDebugUnitTest UP-TO-DATE` (stale).

After the change (outcome lines):
- AC-4 proguard-rules.pro edit: `testDevDebugUnitTest` executed; revert: FROM-CACHE.
- AC-4 strings.xml edit: `testDevDebugUnitTest` executed; revert: FROM-CACHE.
- AC4-edit-others (update_file_paths.xml, generated baselineProfiles, src/main/java): not run; same input declaration.
- AC-1: after `clean`, `:app:kspDevDebugKotlin FROM-CACHE`, `:app:compileDevDebugKotlin FROM-CACHE`, "9 from cache".
- AC-3: check.full run twice, both exit 0 (second: 117 actionable tasks, 3 executed, 114 up-to-date; it hit up-to-date, not FROM-CACHE).
- AC-5: partial. This worktree's lint reports contain this worktree's own paths, as expected; the cross-worktree load was not run.
- AC-6: bullet added to AGENTS.md "Build And Worktrees".

Not done: AC-2 and the cross-worktree part of AC-5 need a second worktree on the same commit; implementer may not create one. Conductor or qa to run them.

Deviations: gradle.properties had no trailing newline, so the commit adds a blank line before the property. The AGENTS.md bullet also states the declare-inputs rule.

### Review round 1 fixes

Change: test inputs are `proguard-rules.pro` and the whole `app/src/main` directory, replacing the hand-kept file list (triage #1, #2). Outcome lines are also in `docs/superpowers/evidence/2026-11-28-gradle-build-cache-device.md`.

Red runs (cache on, test inputs removed; probe edits reverted, git status clean):
- red: AC4-edit-strings - append to `values/strings.xml`: `testDevDebugUnitTest UP-TO-DATE` (stale).
- red: AC4-edit-player.xml - `values/player.xml` (read by SubtitleLabelerTest): UP-TO-DATE (stale).
- red: AC4-edit-proguard - UP-TO-DATE (stale).
- red: AC4-edit-update_file_paths - UP-TO-DATE (stale).
- red: AC4-edit-baselineProfiles - UP-TO-DATE (stale).
- AC4-edit-src/main/java: executed even without inputs, because the edit also recompiles main; the row does not discriminate here, the `src/main` input still covers it.
- red: AC1-clean-rebuild - with `--no-build-cache`, `clean` then `compileDevDebugKotlin`: ksp and compile executed, 0 from cache.

After the change (`:app:testDevDebugUnitTest`; an executed task prints no suffix; re-run in round 2, probe edits reverted, git status clean):
- strings.xml: edit: executed; revert: FROM-CACHE
- values/player.xml: edit: executed; revert: FROM-CACHE
- update_file_paths.xml: edit: executed; revert: FROM-CACHE
- generated baselineProfiles: edit: executed; revert: FROM-CACHE
- proguard-rules.pro: edit: executed; revert: FROM-CACHE
- AC-3: check.full after `clean`: exit 0, 117 actionable tasks, 42 executed, 68 from cache, 7 up-to-date; tooling scripts exit 0.
- Triage #6 rejected, no change. One warm unit-test run failed once and passed on rerun with no input change (known flake).

### Judge round 1 fixes

Must fix 1: `app/src/instrumentation` and `baselineprofile/src/main/assets/baseline` added to the Test inputs (relative path sensitivity). A grep of all JVM test source sets found no further project-file reads. Outcome lines are in the evidence file.
- red: AC4-edit-network_security_config.xml - `testInstrumentationDebugUnitTest UP-TO-DATE` (stale).
- red: AC4-edit-baseline-ok.json - UP-TO-DATE (stale).
- After: both executed on edit, FROM-CACHE on revert.
