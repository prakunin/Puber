# Test design: gradle-build-cache

Base: 8c3117ea. No production code changes, so no JUnit test fits: the behaviour is Gradle task outcomes. Level `build` = a `./tools/agentw ... --console=plain` run whose outcome lines are kept as evidence in `docs/superpowers/`. Existing unit tests are the AC-3 guard.

## Risk profile

| Risk | P | I | Score | Test |
|---|---|---|---|---|
| Test task stale (UP-TO-DATE / FROM-CACHE) after an edit to an untracked file the tests read | 3 | 3 | 9 | AC-4 |
| Warm-cache run breaks a task (detekt alpha, KSP/Room, lint) | 2 | 3 | 6 | AC-3, AC-2 |
| Cache miss across worktrees (path-sensitive input), so no benefit | 2 | 2 | 4 | AC-2 |
| Lint report embeds another checkout's absolute path | 2 | 1 | 2 | AC-5 (accepted if found, reported) |
| Cached results hide flakes | 2 | 2 | 4 | Accepted: diagnose with `--no-build-cache`, documented by AC-6 |
| Secrets in cache entries, concurrent writers | 1 | 2 | 2 | Accepted per brief |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-4 | build | P0 red | `AC4-edit-proguard`: run `testDevDebugUnitTest`, append a comment to `app/proguard-rules.pro`, rerun, expect executed (no UP-TO-DATE, no FROM-CACHE), revert, rerun, expect FROM-CACHE or UP-TO-DATE | Fails if the Test task lacks the file as input (stale-result hazard) |
| AC-4 | build | P0 red | `AC4-edit-strings`: same with `app/src/main/res/values/strings.xml` | Same, for a second input; fails if only some files were declared |
| AC-4 | build | P1 red | `AC4-edit-others`: same for `update_file_paths.xml`, one file under `src/main/generated/baselineProfiles/`, one `src/main/java` file | Fails if a directory or file in the hazard table was missed |
| AC-1 | build | P0 red | `AC1-clean-rebuild`: `:app:compileDevDebugKotlin`, `./tools/agentw clean`, again; expect `FROM-CACHE` and non-zero from-cache count | Fails if `org.gradle.caching` is absent or compile is not cacheable |
| AC-2 | build | P1 red | `AC2-second-worktree`: worktree B, same commit, warm cache from A; record outcomes of `:app:compileDevDebugKotlin` and `:app:kspDevDebugKotlin`; on a miss rerun with `-Dorg.gradle.caching.debug=true` and name the input | Fails if inputs embed checkout paths or per-checkout values |
| AC-3 | build | P0 guard | `AC3-check-full-twice`: `check.full` from `.flow/config.yaml` twice, second on a warm cache; both exit 0 | Fails if detekt, lint, KSP or tests break when loaded from cache |
| AC-5 | build | P2 | `AC5-lint-path-scan`: `lintDevDebug` in A, load in B, `grep -r` A's absolute path in `app/build/reports/lint-results-devDebug.*` | Finds leaked paths; a hit is a reported follow-up, not a failure |
| AC-6 | build | P2 | `AC6-agents-bullet`: read `AGENTS.md` "Build And Worktrees"; one bullet states the cache is on and `--no-build-cache` bypasses it | Fails if the bullet is missing or omits the bypass |

Existing tests that must stay green (AC-3 guard): `BaselineProfileContentTest`, `ProguardRulesTest`, `UpdateFilePathsXmlTest`, `HistoryContextMenuTest`, `EpisodeSchedulePrivacyTest`, `SkipSegmentPrivacyTest`.

## Failing-first

`AC4-edit-proguard` on the base commit: with only caching on (plan step 1) the edited run is FROM-CACHE or UP-TO-DATE, so it fails. It passes after plan step 2. Run it on the base plus step 1 before adding the inputs; the base alone is already UP-TO-DATE after an edit, which is the same defect. Revert every appended comment afterwards (`git diff` must show none).

## Manual checks

N/A: no device or UI behaviour; AGENTS.md says device verification is not needed.

## Perf track

N/A: perf track off. AC-1 and AC-2 only record outcomes, no threshold.

## UNTESTABLE

None.
