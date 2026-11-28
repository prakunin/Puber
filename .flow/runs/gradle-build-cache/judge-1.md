# Gate - gradle-build-cache - round 1

**Decision:** FAIL
**Head:** ed880669   **Base:** 8c3117ea

## Done as asked

The diff turns the cache on (`gradle.properties:8`), declares `proguard-rules.pro` and `app/src/main` as Test inputs (`app/build.gradle.kts:471-475`) and adds the AGENTS.md bullet. The brief's goal is that no cacheable task returns a stale result, and the new bullet says tests that read project files must declare them. Two tests that `check.full` runs still read files no Test input covers. `tasks.withType<Test>` applies to them, and `isIncludeAndroidResources` is not set:

| Test | Undeclared file read |
|---|---|
| `app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineNetworkSecurityConfigTest.kt:11-14` | `app/src/instrumentation/res/xml/network_security_config.xml` |
| `app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineFixtureContentTest.kt:39-46` | `baselineprofile/src/main/assets/baseline/*.json` |

If either file is edited, `:app:testInstrumentationDebugUnitTest` can come back UP-TO-DATE or FROM-CACHE from another worktree. That is the stale pass this run exists to prevent. The brief's hazard table and every review grepped only `app/src/test`.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `AC1-clean-rebuild` | brief.md Implementation record: ksp and compile FROM-CACHE after clean, 9 from cache; red with `--no-build-cache` | PASS |
| AC-2 | `AC2-second-worktree` | qa-report.md: run on a git-archive copy at another path, ksp and compile FROM-CACHE (triage-1 #6) | PASS |
| AC-3 | `AC3-check-full-twice` | evidence file: run after `clean` exits 0 with 68 from cache; my run on ed880669 exits 0 (117 tasks, 114 up-to-date), both tooling scripts PASS | PASS |
| AC-4 | `AC4-edit-proguard`, `AC4-edit-strings`, `AC4-edit-others` | evidence "Review round 1 fixes" table: every hazard-table file executes on edit and is FROM-CACHE on revert; red runs in brief.md | PASS |
| AC-5 | `AC5-lint-path-scan` | evidence file: lint FROM-CACHE in the copy, original path absent from its reports | PASS |
| AC-6 | `AC6-agents-bullet` | diff `AGENTS.md:72-75` | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Test inputs miss `player.xml` and `values-en/strings.xml` (major) | blind F1, codex-1 | fixed | dbbab1fd |
| Hand-kept input list drifts | blind F2, tests-1 F5 | fixed | dbbab1fd |
| P0 red runs not recorded (major) | tests-1 F1 | fixed | brief.md Review round 1 fixes, red lines |
| AC-3 never loaded from cache | tests-1 F2 | fixed | evidence: check.full after clean, 68 from cache |
| AC4-edit-others has no red run | tests-1 F3 | fixed | brief.md red lines |
| AC-2 and AC-5 ran on a copy, not a worktree | tests-1 F4 | rejected, accepted | Only the path differs, and the path is what AC-2 tests |
| Garbled evidence table (major) | tests-2 F1, edge-2 F1 | fixed | ed880669 |
| Red runs not in the evidence file | tests-3 F1 | rejected, accepted | brief.md records them |
| Round-1 table drops the `src/main/java` row (nit) | tests-3 F2 | rejected, accepted | The `PuberApp.kt` row is still in the first table |
| Two `testInstrumentation` tests read undeclared files (major) | judge | open | Must fix 1 |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Every `src/main` edit now invalidates the unit tests. That costs cache hits only, and compiling main already invalidates them. |
| Reliability | Flaky tests can be replayed from the cache. The AGENTS.md bullet documents `--no-build-cache` for this. |
| Maintainability | One input block, no dead code. It covers only the inputs `app/src/test` needs (Must fix 1). |
| Security / privacy | The evidence directory passes `tools/mobile-evidence-audit.sh`. Main-checkout cache entries can embed `CLIENT_SECRET`. That risk is accepted in brief.md Risks. |

## Manual checks for the owner

None.

## Must fix

1. In `app/build.gradle.kts` `tasks.withType<Test>`, declare `src/instrumentation` (or `src/instrumentation/res/xml/network_security_config.xml`) and `rootProject`'s `baselineprofile/src/main/assets/baseline` as Test inputs with relative path sensitivity. Then record one edit-and-revert outcome of `:app:testInstrumentationDebugUnitTest` for each file in the evidence file.

## Concerns for the owner

- AC-2 and AC-5 were verified on a git-archive copy, not an Orca worktree (triage-1 #6).
