# Gate - watch-state-batch-in-progress - round 1

**Decision:** PASS
**Head:** 6f3e7dca   **Base:** 8c3117ea

## Done as asked

`WatchStateRepository.kt:325-338` `recordInProgress` returns on an empty list, then reads `currentGeneration()` and runs the `markInProgress` loop inside one `transaction.run { }`. That is option A from the spec. The signature, DAO and SQL are unchanged, and the diff touches no other production file. Production binds `PuberDatabase.transactions()` (`data/di/modules.kt:133`, which is `withWriteTransaction`), so the unit seam matches what ships.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `recordInProgress_runsTheWholeListInOneTransaction` | check.full at 6f3e7dca exit 0 (this gate). The diff asserts `runs == 1` and that each call is logged with `active == true` | PASS |
| AC-2 | `recordInProgress_withAnEmptyListOpensNoTransactionAndTouchesNoDao` | check.full at 6f3e7dca exit 0 | PASS |
| AC-3 | `recordInProgress_marksBareItemsUnfinishedWithoutLosingProgress`, unchanged (no diff hunk touches it) | check.full at 6f3e7dca exit 0 | PASS |
| AC-4 | `markInProgressInOneTransactionEmitsNoPartialList` and the control `markInProgressInSeparateCommitsEmitsAPartialList` | evidence file, "Round 2": box itest 13/13, exit 0 | PASS |
| AC-5 | Device run on the box: play, exit, compare badges | `docs/superpowers/evidence/2026-11-28-watch-state-batch-in-progress-device.md`, "AC-5" | PASS |
| AC-6 | check.full | Re-run by this gate at 6f3e7dca: BUILD SUCCESSFUL, both tooling scripts PASS | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| The AC-4 test cannot be shown to fail | review-tests-1 F1, review-codex-1 #1, review-blind-1 F1 | fixed | cf89461f adds the control case; it is green on the box (evidence, Round 2) |
| The AC-4 command needs `GRADLE=./tools/agentw` in a worktree | review-codex-1 #2 | rejected; accepted by the gate | `Makefile:23` defaults to `./gradlew`. The working command is in the spec's Decisions (3318a62e) and in the evidence file |
| The AC-4 test does not call `recordInProgress` | review-blind-1 F1 | rejected; accepted by the gate | AC-4 is specified at the Room level. The repository batching is covered by AC-1, which fails on base |
| The control case is paced but the atomicity test is not | review-codex-2 #1 | accepted minor | Affects test strength only. A write transaction keeps uncommitted rows from readers whatever the invalidation timing, and AC-1 covers the production change |

The reviews also list items as unproven: a large list holding the write lock, a mid-list rollback, duplicate ids, and the settle timing. The spec's Risks table accepts each of these, and none is a finding.

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One commit and one `watch_state` invalidation per list instead of N. The write lock is held for one list of tens of rows |
| Reliability | The empty list is handled by an early return. A mid-list failure rolls back the whole list, and the next catch-up rewrites it (spec Risks). The `sessionSurvived` checks in both callers are unchanged (review-state-path-1) |
| Maintainability | Follows the `recordHistoryPage` pattern. The test seam is test-local, and no production seam or dead code was added |
| Security / privacy | No secrets in the diff. `tools/mobile-evidence-audit.sh docs/superpowers/evidence com.kino.puber.stage` -> `passed` |

## Manual checks for the owner

None.

## Must fix

None.

## Concerns for the owner

None.
