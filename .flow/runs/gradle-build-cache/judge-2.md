# Gate - gradle-build-cache - round 2

**Decision:** PASS
**Head:** ccdebdf8   **Base:** 8c3117ea

## Done as asked

The diff turns the cache on (`gradle.properties:8`). It declares `proguard-rules.pro`, `app/src/main`, `app/src/instrumentation` and `baselineprofile/src/main/assets/baseline` as inputs of every Test task (`app/build.gradle.kts:471-477`), and adds the AGENTS.md bullet. A grep of `app/src/test`, `app/src/testInstrumentation` and `player-test-fixtures/src/test` for `File(`, `Path.of`, `readText(`, `"src/` and `assets/` found no project-file read outside these inputs. `HistorySerializationTest.kt:210-213` reads a classpath resource, and `CurlLogFormatterTest.kt:388-390` reads process output. No production source, CI or retention change is in the diff (brief Q2, Q3).

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `AC1-clean-rebuild` | brief.md Implementation record: ksp and compile FROM-CACHE after clean; red run with `--no-build-cache` | PASS |
| AC-2 | `AC2-second-worktree` | evidence file, first table: copy at another path, ksp and compile FROM-CACHE | PASS |
| AC-3 | `AC3-check-full-twice` | evidence file: run after `clean` exits 0 with 68 from cache; my run on ccdebdf8 exits 0 (117 tasks, 114 up-to-date), both tooling scripts PASS | PASS |
| AC-4 | `AC4-edit-proguard`, `AC4-edit-strings`, `AC4-edit-others` | evidence file: every declared file is stale before the declaration, executes on edit after it and is FROM-CACHE on revert, including both instrumentation files | PASS |
| AC-5 | `AC5-lint-path-scan` | evidence file: lint FROM-CACHE in the copy, original path absent | PASS |
| AC-6 | `AC6-agents-bullet` | `AGENTS.md:72-75` | PASS |

## Findings

Round-1 dispositions: [judge-1.md#findings](judge-1.md).

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Two `testInstrumentation` tests read undeclared files (major) | judge-1 Must fix 1 | fixed | ccdebdf8, evidence "Judge round 1 fix" |
| Instrumentation inputs also invalidate the dev/prod unit-test caches | codex-4 (rejected) | rejected, accepted | Causes a cache miss, never a stale result |
| AC-2 and AC-5 ran on a copy, not an Orca worktree | tests-1 F4, triage-1 #6 | rejected, accepted | `.git` and `local.properties` are not compile, ksp or lint inputs; the path is what AC-2 tests |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | A `src/main` or instrumentation edit re-runs all unit-test tasks. It costs cache hits only. |
| Reliability | Flaky tests can be replayed from the cache. AGENTS.md documents `--no-build-cache` for this. |
| Maintainability | One input block, no dead code. A new test that reads a file outside the declared dirs needs a new input (AGENTS.md bullet). |
| Security / privacy | `tools/mobile-evidence-audit.sh docs/superpowers/evidence` passed on ccdebdf8. Main-checkout cache entries can embed secrets. That risk is accepted in brief.md Risks. |

## Manual checks for the owner

None.

## Must fix

None.

## Concerns for the owner

None.
